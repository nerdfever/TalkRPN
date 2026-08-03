package com.nerdfever.talkrpn

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/*
 * Speech recognition that runs entirely on the watch.
 *
 * Vosk is Kaldi with a Java wrapper. The model is bundled in the APK, the inference
 * is native code on this CPU, and there is no network code anywhere in the library -
 * so "offline" here is a property of the build, not a request to a service that may
 * or may not honour it.
 *
 * Two capabilities matter to a calculator and are the reason for switching:
 *
 *   Streaming  - partial hypotheses arrive while the user is still speaking, so the
 *                display can follow along rather than waiting for a full stop.
 *   Grammar    - the recognizer can be constrained to a fixed word list, so it must
 *                choose among the calculator's vocabulary instead of all of English.
 *                Digits come back as words, ungrouped, leaving the aggregation
 *                decision where it belongs: with the calculator.
 */

private const val LOG_TAG = "TalkRPN"

/** Vosk models are trained at 16 kHz; feeding anything else degrades accuracy badly. */
private const val SAMPLE_RATE = 16000.0f

/** Folder inside the merged assets holding the unpacked model. */
private const val MODEL_ASSET_DIR = "model-en-us"

/** How many competing interpretations to ask for, to match the platform probe. */
private const val MAX_ALTERNATIVES = 5

/**
 * Offline recognition backed by a bundled Vosk model.
 *
 * Model loading is slow the first time - the assets have to be unpacked to internal
 * storage - so it is kicked off as soon as this object is constructed rather than
 * being made the user's problem on first press.
 */
class VoskSpeechSource(private val context: Context) : SpeechSource, RecognitionListener {

    override val label = "Vosk (bundled model)"

    /** True, and unlike the platform recognizer this is a guarantee rather than a hope. */
    override val guaranteedOffline = true

    override val streams = true

    override var state by mutableStateOf(TrialState.Idle)
        private set

    override var partial by mutableStateOf("")
        private set

    override var partialUpdates by mutableStateOf(0)
        private set

    override var continuous = false

    /** Words from utterances already closed out. */
    private var committedTokens by mutableStateOf<List<TokenArrival>>(emptyList())

    /** Words in the utterance currently being spoken, still subject to revision. */
    private var inFlightTokens by mutableStateOf<List<TokenArrival>>(emptyList())

    override val tokens: List<TokenArrival>
        get() = committedTokens + inFlightTokens

    override var results by mutableStateOf<List<Candidate>>(emptyList())
        private set

    /** Vosk owns the audio capture and exposes no level, so this stays at zero. */
    override val soundLevel = 0f

    /**
     * Null: there is no gap.
     *
     * Vosk keeps one audio stream open across utterance boundaries, so unlike the
     * platform recognizer it never stops hearing. That is a real advantage and worth
     * showing next to the latency figures, which favour the other engine.
     */
    override val deafWindowMs: Long? = null

    override var message by mutableStateOf<String?>("Loading model…")
        private set

    override var totalMs by mutableStateOf<Long?>(null)
        private set

    override var processingMs by mutableStateOf<Long?>(null)
        private set

    /** How long unpacking and loading the model took, reported once. */
    var modelLoadMs by mutableStateOf<Long?>(null)
        private set

    /** Whether the model finished loading. Nothing can start until it has. */
    var modelReady by mutableStateOf(false)
        private set

    private var model: Model? = null
    private var speechService: SpeechService? = null

    private var startedAt = 0L
    private var speechEndedAt = 0L

    init {
        loadModel()
    }

    /**
     * Unpack the model from assets and load it, off the main thread.
     *
     * The first call copies ~67 MB out of the APK into internal storage, which takes
     * appreciable time on a watch; afterwards it is a cheap existence check.
     */
    private fun loadModel() {

        val began = SystemClock.elapsedRealtime()

        StorageService.unpack(
            context,
            MODEL_ASSET_DIR,
            "model",
            { loaded ->
                model = loaded
                modelReady = true
                modelLoadMs = SystemClock.elapsedRealtime() - began
                message = "Model ready in ${modelLoadMs}ms."

                Log.d(LOG_TAG, "vosk model loaded in ${modelLoadMs}ms")
            },
            { error ->
                state = TrialState.Failed
                message = "Model failed to load: ${error.message}"

                Log.e(LOG_TAG, "vosk model load failed", error)
            },
        )
    }

    /**
     * The vocabulary constraint, as Vosk wants it: a JSON array of permitted phrases.
     *
     * `[unk]` is included deliberately. Without it the recognizer must map every
     * sound onto some vocabulary word, so a cough becomes "four". With it, audio
     * that matches nothing can be reported as unknown - which for a calculator is a
     * far better outcome than a confident wrong digit.
     */
    private fun buildGrammar(): String =
        (CALCULATOR_VOCABULARY + "[unk]").joinToString(
            prefix = "[",
            separator = ",",
            postfix = "]",
        ) { "\"$it\"" }

    override fun start() {

        val loadedModel = model

        if (loadedModel == null) {
            state = TrialState.Failed
            message = "Model is still loading — wait a moment and try again."
            return
        }

        releaseService()

        partial = ""
        partialUpdates = 0
        committedTokens = emptyList()
        inFlightTokens = emptyList()
        results = emptyList()
        message = null
        processingMs = null
        totalMs = null
        state = TrialState.Starting

        startedAt = SystemClock.elapsedRealtime()
        speechEndedAt = 0L

        try {
            // With the grammar, only vocabulary words can ever come back; without it,
            // Vosk uses the model's own language model and recognises open English.
            // Unconstrained is the like-for-like comparison against the platform engine.
            val recognizer = if (BIAS_TO_VOCABULARY) {
                Recognizer(loadedModel, SAMPLE_RATE, buildGrammar())
            } else {
                Recognizer(loadedModel, SAMPLE_RATE)
            }

            recognizer.setMaxAlternatives(MAX_ALTERNATIVES)

            val service = SpeechService(recognizer, SAMPLE_RATE)
            speechService = service

            service.startListening(this)

            state = TrialState.Listening
            message = "Listening — say a command."

        } catch (e: Exception) {
            state = TrialState.Failed
            message = "Could not start Vosk: ${e.javaClass.simpleName}: ${e.message}"

            Log.e(LOG_TAG, "vosk start failed", e)
        }
    }

    override fun cancel() {

        // Clear the flag before tearing down, so a callback already in flight cannot
        // decide to keep listening on the way out.
        continuous = false

        releaseService()

        state = TrialState.Idle
    }

    override fun dispose() {
        releaseService()

        model?.close()
        model = null
    }

    /** Stop and free the recogniser and its audio thread. Idempotent. */
    private fun releaseService() {
        speechService?.let {
            it.stop()
            it.shutdown()
        }

        speechService = null
    }

    // -----------------------------------------------------------------------
    // Vosk's RecognitionListener. Hypotheses arrive as JSON strings.
    // -----------------------------------------------------------------------

    override fun onPartialResult(hypothesis: String?) {

        val text = hypothesis?.let { JSONObject(it).optString("partial") }.orEmpty()

        // Vosk re-emits an unchanged partial frequently; only count real revisions,
        // so the number means "how incremental is this" rather than "how chatty".
        if (text.isNotBlank() && text != partial) {

            val now = SystemClock.elapsedRealtime()

            partial = text
            partialUpdates += 1

            // Stamp any words that just became available. This is the measurement that
            // matters: when could the calculator have acted on this word?
            inFlightTokens = stampNewTokens(inFlightTokens, text, now - startedAt)

            state = TrialState.Hearing

            // No end-of-speech callback exists, so track the last time anything was
            // heard; the gap from here to the result is the inference cost.
            speechEndedAt = now

            Log.d(LOG_TAG, "vosk partial #$partialUpdates at ${SystemClock.elapsedRealtime() - startedAt}ms: \"$text\"")
        }
    }

    /**
     * An utterance boundary — Vosk has decided a phrase is complete.
     *
     * This, not onFinalResult, is where the recognised text actually arrives.
     * onFinalResult only returns whatever is left in the buffer after stop(), which
     * for a completed utterance is nothing at all.
     *
     * One utterance is one command, so the session ends here rather than listening
     * on: Vosk's SpeechService otherwise runs until explicitly stopped.
     */
    override fun onResult(hypothesis: String?) {

        val candidates = hypothesis?.let(::parseCandidates).orEmpty()

        // Vosk emits an empty result at a silence boundary where nothing was said.
        // Ignore those and keep listening rather than declaring an empty answer.
        if (candidates.isEmpty()) return

        finish(candidates, hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {

        // Only meaningful if the session was stopped mid-utterance; a normal command
        // has already completed via onResult.
        if (state == TrialState.Finished) return

        finish(hypothesis?.let(::parseCandidates).orEmpty(), hypothesis)
    }

    /** Record the answer and stamp the timings. */
    private fun finish(candidates: List<Candidate>, raw: String?) {

        results = candidates

        val now = SystemClock.elapsedRealtime()
        totalMs = now - startedAt

        // Vosk reports no end-of-speech event, so the last partial is the best
        // available marker for when the speaking stopped.
        processingMs = if (speechEndedAt > 0L) now - speechEndedAt else null

        // Log only this utterance's words, not the whole session.
        //
        // The previous version dumped the entire accumulated list on every result. By
        // the eightieth token that was kilobytes per utterance, and it flushed the
        // ring buffer hard enough to lose earlier entries entirely - which looked like
        // "the other engine didn't get logged" rather than like a logging bug.
        val utterance = inFlightTokens.joinToString(" ") { "${it.token}@${it.atMs}" }

        // The utterance is settled; its words move from provisional to committed and
        // the next utterance starts with a clean slate.
        committedTokens = committedTokens + inFlightTokens
        inFlightTokens = emptyList()
        partial = ""

        Log.d(LOG_TAG, "vosk: \"${candidates.firstOrNull()?.text.orEmpty()}\" think=${processingMs}ms | $utterance")

        if (continuous) {

            // Vosk's SpeechService keeps its audio thread running across utterance
            // boundaries, so there is nothing to restart - just stay in the listening
            // state and wait for the next thing said.
            state = TrialState.Listening
            message = "Listening…"

        } else {
            state = TrialState.Finished
            message = if (candidates.isEmpty()) "Nothing matched the vocabulary." else "Finished."

            releaseService()
        }
    }

    override fun onError(exception: Exception?) {
        state = TrialState.Failed
        message = "Vosk error: ${exception?.message ?: "unknown"}"

        Log.e(LOG_TAG, "vosk error", exception)

        releaseService()
    }

    override fun onTimeout() {
        state = TrialState.Finished
        message = "Timed out waiting for speech."

        releaseService()
    }

    /**
     * Turn a Vosk result document into ranked candidates.
     *
     * With alternatives enabled the payload is `{"alternatives":[{confidence,text}]}`;
     * without them it degrades to a bare `{"text":...}`. Both shapes are handled, since
     * the alternatives setting is not honoured by every model.
     */
    private fun parseCandidates(json: String): List<Candidate> {

        val document = JSONObject(json)

        document.optJSONArray("alternatives")?.let { alternatives ->

            return (0 until alternatives.length())
                .map { alternatives.getJSONObject(it) }
                .map { Candidate(it.optString("text"), it.optDouble("confidence").toFloat()) }
                .filter { it.text.isNotBlank() }
        }

        val text = document.optString("text")

        return if (text.isBlank()) emptyList() else listOf(Candidate(text, null))
    }
}
