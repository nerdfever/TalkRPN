package com.nerdfever.talkrpn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * Recognition via Android's own SpeechRecognizer.
 *
 * Kept as a working alternative rather than deleted, because it is not bad - the
 * transcription quality on this watch was good, it costs nothing in APK size, and
 * it needs no model. What it cannot do is the four things a calculator needs:
 * guarantee it stays offline, report confidence, stream partial words, or leave
 * digit grouping alone. See SOURCES.md for the measurements.
 */

private const val LOG_TAG = "TalkRPN"

/** The language we test with. Hard-coded for now — a settings screen can come later. */
const val TRIAL_LANGUAGE_TAG = "en-US"

/**
 * Pause before relistening.
 *
 * Kept short deliberately. The recognizer is deaf from the moment a result arrives
 * until onReadyForSpeech fires on the next one, and measurements put that gap at well
 * over a second on its own - so there is no sense adding to it. Too short risks
 * ERROR_RECOGNIZER_BUSY, which is logged if it happens.
 */
private const val RESTART_DELAY_MS = 80L

/**
 * Runs a single recognition through the platform recognizer.
 *
 * Must be constructed and driven from the main thread; SpeechRecognizer requires it.
 */
class PlatformSpeechSource(private val context: Context) : SpeechSource, RecognitionListener {

    override val label = "Android platform"

    /**
     * False — and this is the crux of the whole investigation.
     *
     * The recognizer demonstrably works with the radio off, so it clearly *can* run
     * locally. But isOnDeviceRecognitionAvailable() reports false on this watch and
     * EXTRA_PREFER_OFFLINE is only a request, so there is no way to require it.
     */
    override val guaranteedOffline = false

    /** False, measured: nothing arrives until roughly a second after speech stops. */
    override val streams = false

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

    /** Session start, kept separate from per-utterance start so timings are comparable. */
    private var sessionStartedAt = 0L

    /** Used to defer the restart; see [restartIfContinuous]. */
    private val mainHandler = Handler(Looper.getMainLooper())

    override var results by mutableStateOf<List<Candidate>>(emptyList())
        private set

    override var soundLevel by mutableStateOf(0f)
        private set

    override var message by mutableStateOf<String?>(null)
        private set

    override var totalMs by mutableStateOf<Long?>(null)
        private set

    override var processingMs by mutableStateOf<Long?>(null)
        private set

    override var deafWindowMs by mutableStateOf<Long?>(null)
        private set

    /** Whether this attempt managed to use the offline recognizer at all. */
    var usedOnDeviceRecognizer by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null

    private var startedAt = 0L
    private var speechEndedAt = 0L

    override fun start() {

        // A fresh session: clear the accumulated token history and reset the clock
        // that all arrival times are measured against.
        sessionStartedAt = SystemClock.elapsedRealtime()
        committedTokens = emptyList()
        inFlightTokens = emptyList()
        partialUpdates = 0

        beginUtterance()
    }

    /**
     * Start listening for one utterance.
     *
     * Split out from [start] because the platform recognizer stops dead after every
     * result, so continuous operation means calling this again - and doing so must not
     * wipe the session's token history the way a real restart would.
     */
    private fun beginUtterance() {

        releaseRecognizer()

        partial = ""
        results = emptyList()
        soundLevel = 0f
        message = null
        processingMs = null
        totalMs = null
        state = TrialState.Starting

        startedAt = SystemClock.elapsedRealtime()
        speechEndedAt = 0L

        // Insist on the offline recognizer if the device has one.
        val onDeviceAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        usedOnDeviceRecognizer = onDeviceAvailable

        val created = try {
            if (onDeviceAvailable) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            state = TrialState.Failed
            message = "Could not create a recognizer: ${e.javaClass.simpleName}: ${e.message}"
            return
        }

        created.setRecognitionListener(this)
        recognizer = created

        created.startListening(buildTrialIntent())
    }

    override fun cancel() {

        // Clear the flag first, or the pending restart will fire after teardown and
        // silently reopen the microphone.
        continuous = false
        mainHandler.removeCallbacksAndMessages(null)

        releaseRecognizer()

        state = TrialState.Idle
        soundLevel = 0f
    }

    override fun dispose() {
        continuous = false
        mainHandler.removeCallbacksAndMessages(null)

        releaseRecognizer()
    }

    private fun buildTrialIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, TRIAL_LANGUAGE_TAG)

        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

        // Hints only; this recognizer appears to ignore them.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)

        // Biasing tilts the odds towards the calculator's words without restricting
        // anything. Off by default so the engines are compared unaided — see
        // BIAS_TO_VOCABULARY.
        if (BIAS_TO_VOCABULARY) {
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_BIASING_STRINGS,
                ArrayList(CALCULATOR_VOCABULARY),
            )
        }
    }

    private fun releaseRecognizer() {
        recognizer?.let {
            it.cancel()
            it.destroy()
        }

        recognizer = null
    }

    // -----------------------------------------------------------------------
    // RecognitionListener — the platform calls these back on the main thread.
    // -----------------------------------------------------------------------

    override fun onReadyForSpeech(params: Bundle?) {

        state = TrialState.Listening
        message = "listening"

        // How long the microphone was shut between utterances. Anything said during
        // this window is simply lost, and it is the most likely explanation for
        // "it stopped hearing me for a bit" - so measure it rather than guess.
        val deaf = SystemClock.elapsedRealtime() - startedAt
        deafWindowMs = deaf

        Log.d(LOG_TAG, "platform: mic open after ${deaf}ms deaf")
    }

    override fun onBeginningOfSpeech() {
        state = TrialState.Hearing
        message = "Speech detected."
    }

    override fun onRmsChanged(rmsdB: Float) {
        soundLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio bytes; not needed.
    }

    override fun onEndOfSpeech() {
        soundLevel = 0f
        message = "Processing…"

        speechEndedAt = SystemClock.elapsedRealtime()
    }

    override fun onPartialResults(partialResults: Bundle?) {

        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

        if (text.isNotBlank() && text != partial) {

            val now = SystemClock.elapsedRealtime()

            partial = text
            partialUpdates += 1

            // Stamped against the session clock, not the utterance clock, so that
            // timings stay comparable across an auto-restart.
            inFlightTokens = stampNewTokens(inFlightTokens, text, now - sessionStartedAt)

            Log.d(LOG_TAG, "platform partial #$partialUpdates at ${now - startedAt}ms: \"$text\"")
        }
    }

    override fun onResults(results: Bundle?) {

        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        this.results = texts.mapIndexed { index, text ->
            Candidate(text, scores?.getOrNull(index))
        }

        val now = SystemClock.elapsedRealtime()
        totalMs = now - startedAt
        processingMs = if (speechEndedAt > 0L) now - speechEndedAt else null

        state = TrialState.Finished
        soundLevel = 0f

        message = if (this.results.isEmpty()) {
            "Finished, but the recognizer returned no candidates."
        } else {
            "Finished."
        }

        // Capture before committing — afterwards the in-flight list is empty.
        // Same one-line shape as the Vosk side, so a mixed run reads as a comparison.
        val utterance = inFlightTokens.joinToString(" ") { "${it.token}@${it.atMs}" }

        // Words from this utterance are settled; move them into the session history so
        // the next utterance starts with a clean in-flight list.
        committedTokens = committedTokens + inFlightTokens
        inFlightTokens = emptyList()

        Log.d(LOG_TAG, "platform: \"${this.results.firstOrNull()?.text.orEmpty()}\" think=${processingMs}ms total=${totalMs}ms | $utterance")

        releaseRecognizer()
        restartIfContinuous()
    }

    override fun onError(error: Int) {

        soundLevel = 0f

        // Silence is not a fault. When listening continuously these two arrive
        // constantly - every pause produces one - so reporting them as failures makes
        // a perfectly healthy idle state look broken.
        val routine = error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

        if (routine && continuous) {
            state = TrialState.Listening
            message = "listening"
        } else {
            state = TrialState.Failed
            message = recognizerErrorName(error)
        }

        releaseRecognizer()

        // ERROR_NO_MATCH and ERROR_SPEECH_TIMEOUT are the normal outcome of a pause,
        // not a fault, so continuous listening has to survive them - otherwise the
        // first silence ends the session.
        restartIfContinuous()
    }

    /**
     * Begin the next utterance, if we are meant to keep listening.
     *
     * Deferred rather than immediate: SpeechRecognizer is being torn down inside its
     * own callback here, and starting a new one on the same stack is unreliable.
     */
    private fun restartIfContinuous() {

        if (!continuous) return

        mainHandler.postDelayed({ if (continuous) beginUtterance() }, RESTART_DELAY_MS)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Reserved by the platform.
    }
}
