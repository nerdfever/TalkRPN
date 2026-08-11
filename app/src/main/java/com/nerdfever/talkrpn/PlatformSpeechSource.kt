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
 * A session that ends sooner than this, without having heard anything, did not
 * really run at all.
 *
 * The discriminator for a recognizer that is refusing rather than idling. A
 * healthy silent session lasts seconds - it waits out the speech timeout before
 * reporting ERROR_NO_MATCH - so nothing legitimate comes back this fast.
 *
 * Error codes are deliberately NOT used for this. The failure that prompted it -
 * no recognition service installed, seen on the emulator - arrives as an ordinary
 * ERROR_CLIENT, indistinguishable from faults worth retrying. How long the
 * attempt survived tells you what the code does not.
 */
private const val FAST_FAILURE_MS = 400L

/**
 * How fast the restart delay grows while it keeps failing, and the point at
 * which waiting longer stops being worth it.
 *
 * Ten seconds is already generous: recognition runs ON the watch, so nothing
 * here is waiting on a network. If ten seconds of backing off has not produced
 * one session that survives, the thing is not busy, it is broken.
 *
 * There is deliberately no second "give up after N tries" constant. The cap IS
 * the give-up: once the next wait would exceed it, we stop. One knob, and the
 * two cannot drift into disagreeing about how long we persist - which with 80 ms
 * doubling is seven retries and about ten seconds in total.
 */
private const val BACKOFF_FACTOR = 2
private const val RESTART_DELAY_MAX_MS = 10_000L

/**
 * Silence that ends one segment, in milliseconds.
 *
 * Only a boundary between results — not the end of listening — so it can be short
 * without costing anything. Long enough to ride over the pause between "two point
 * five" and "e six"; short enough that a finished command is reported promptly.
 */
private const val SEGMENT_SILENCE_MS = 1200L

/** How many competing interpretations to ask for. More costs nothing to request. */
private const val MAX_ALTERNATIVES = 5

/**
 * Range of onRmsChanged, for normalising the level meter to 0..1.
 *
 * The platform documents this only as "roughly" -2 dB silence to +10 dB loud, so the
 * result is clamped rather than trusted.
 */
private const val RMS_QUIET_DB = -2f
private const val RMS_LOUD_DB = 10f

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

    override var preferOffline = true

    override var onPartial: ((String, Long, List<TokenArrival>) -> Unit)? = null

    /**
     * Tokens belonging to the utterance just completed.
     *
     * Kept separately because the session-wide list is what gets logged otherwise,
     * which made the tokens column of the results file grow until it described the
     * whole session rather than the row it was on.
     */
    var lastUtteranceTokens by mutableStateOf<List<TokenArrival>>(emptyList())
        private set

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

    /**
     * Our own microphone, held open for the whole session.
     *
     * The recognizer reads from a pipe rather than the microphone, so its constant
     * stopping and restarting no longer costs us any audio. See MicStream.
     */
    private val mic = MicStream()

    /** Whether the recognizer actually accepted the piped audio source. */
    var usingOwnMic by mutableStateOf(false)
        private set

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

    override var failureCount by mutableStateOf(0)
        private set

    override var lastFailure by mutableStateOf<String?>(null)
        private set

    override var lastFailurePartial by mutableStateOf<String?>(null)
        private set

    /**
     * Whether onBeginningOfSpeech fired this utterance.
     *
     * The discriminator between "you said something and it failed" and "nothing was
     * said". Without it every pause looks like a failure and the failure rate is
     * meaningless.
     */
    private var speechDetected = false

    /** Whether this attempt managed to use the offline recognizer at all. */
    var usedOnDeviceRecognizer by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null

    private var startedAt = 0L
    private var speechEndedAt = 0L

    /**
     * When the current utterance's recognizer was started.
     *
     * Separate from [startedAt], which is rebased mid-utterance so that partial
     * timings stay comparable. This one is not, because it answers a different
     * question: how long did this attempt survive?
     */
    private var utteranceStartedAt = 0L

    /** Consecutive sessions that died at once without hearing anything. */
    private var fastFailures = 0

    override fun start() {

        // One microphone for the whole session, opened before the first recognizer
        // and kept open across every restart.
        usingOwnMic = mic.start()

        // A fresh session: clear the accumulated token history and reset the clock
        // that all arrival times are measured against.
        sessionStartedAt = SystemClock.elapsedRealtime()
        committedTokens = emptyList()
        inFlightTokens = emptyList()
        partialUpdates = 0

        // Asking again is the user overruling an earlier give-up, so the count that
        // produced it starts over.
        fastFailures = 0

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
        utteranceStartedAt = startedAt
        speechEndedAt = 0L
        speechDetected = false

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
        mic.stop()
        usingOwnMic = false

        state = TrialState.Idle
        soundLevel = 0f
    }

    override fun dispose() {
        continuous = false
        mainHandler.removeCallbacksAndMessages(null)

        releaseRecognizer()
        mic.stop()
        usingOwnMic = false
    }

    private fun buildTrialIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

        // ------------------------------------------------------------------
        // Feed the recognizer from our own microphone rather than letting it open
        // one. This is what makes continuous listening possible: the recognizer may
        // stop and restart as often as it likes, and capture never pauses.
        //
        // If it refuses the piped source it falls back to opening the microphone
        // itself, and behaves exactly as before.
        // ------------------------------------------------------------------
        if (usingOwnMic) {
            mic.openPipe()?.let { readEnd ->
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MIC_SAMPLE_RATE)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, MIC_ENCODING)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, MIC_CHANNELS)
            }
        }

        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, TRIAL_LANGUAGE_TAG)

        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_ALTERNATIVES)

        // ------------------------------------------------------------------
        // Segmented session: one recognition session spanning many utterances.
        //
        // Without it the recognizer stops dead on every result and has to be rebuilt,
        // and the microphone is shut for the ~200 ms that takes. Worse, if it decides
        // an utterance ended while you are mid-phrase, the rest of the phrase falls
        // into that gap and is simply lost.
        //
        // In segmented mode the microphone stays open across boundaries: results come
        // back per segment via onSegmentResults, and the session only ends at
        // onEndOfSegmentedSession. A premature cut then splits the text instead of
        // eating your words.
        //
        // The value is the KEY of whichever extra bounds a segment — here, the silence
        // length below. If the recognizer does not support segmented mode it ignores
        // the extra and falls back to plain onResults, which is still handled.
        // ------------------------------------------------------------------
        putExtra(
            RecognizerIntent.EXTRA_SEGMENTED_SESSION,
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
        )

        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SEGMENT_SILENCE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SEGMENT_SILENCE_MS)

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

        speechDetected = true
    }

    override fun onRmsChanged(rmsdB: Float) {
        soundLevel = ((rmsdB - RMS_QUIET_DB) / (RMS_LOUD_DB - RMS_QUIET_DB)).coerceIn(0f, 1f)
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

            // Which tokens are new relative to the previous hypothesis. The API gives
            // no delta - each partial is a full rewrite - so the difference has to be
            // computed here, and it is the input to any "safe to execute yet" rule.
            val previous = inFlightTokens

            // Stamped against the session clock, not the utterance clock, so that
            // timings stay comparable across an auto-restart.
            inFlightTokens = stampNewTokens(previous, text, now - sessionStartedAt)

            val fresh = inFlightTokens.drop(
                inFlightTokens.zip(previous).takeWhile { (a, b) -> a.token == b.token }.size
            )

            Log.d(LOG_TAG, "platform partial #$partialUpdates at ${now - startedAt}ms: \"$text\"")

            onPartial?.invoke(text, now - startedAt, fresh)
        }
    }

    /**
     * One segment finished, but the session — and the microphone — continue.
     *
     * This is the callback that makes continuous listening viable: no teardown, no
     * restart, no deaf window. Only fires if the recognizer supports segmented mode;
     * otherwise everything arrives through onResults as before.
     */
    override fun onSegmentResults(segmentResults: Bundle) {

        Log.d(LOG_TAG, "platform: segment result (session still open)")

        deliverResults(segmentResults, endOfSession = false)
    }

    /** The segmented session itself has ended; now a restart is needed. */
    override fun onEndOfSegmentedSession() {

        Log.d(LOG_TAG, "platform: segmented session ended")

        releaseRecognizer()
        restartIfContinuous()
    }

    override fun onResults(results: Bundle?) {
        deliverResults(results, endOfSession = true)
    }

    /**
     * Shared handling for both result callbacks.
     *
     * [endOfSession] distinguishes them: a plain result means the recognizer has
     * stopped and must be rebuilt, whereas a segment result means it is still
     * listening and rebuilding would throw away a perfectly good open microphone.
     */
    private fun deliverResults(results: Bundle?, endOfSession: Boolean) {

        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        this.results = texts.mapIndexed { index, text ->
            Candidate(text, scores?.getOrNull(index))
        }

        val now = SystemClock.elapsedRealtime()
        totalMs = now - startedAt
        processingMs = if (speechEndedAt > 0L) now - speechEndedAt else null

        soundLevel = 0f

        // Capture before committing — afterwards the in-flight list is empty.
        val utterance = inFlightTokens.joinToString(" ") { "${it.token}@${it.atMs}" }

        // Words from this segment are settled; move them into the session history so
        // the next one starts with a clean in-flight list.
        lastUtteranceTokens = inFlightTokens
        committedTokens = committedTokens + inFlightTokens
        inFlightTokens = emptyList()

        Log.d(LOG_TAG, "platform: \"${this.results.firstOrNull()?.text.orEmpty()}\" think=${processingMs}ms total=${totalMs}ms | $utterance")

        if (endOfSession) {

            state = TrialState.Finished
            message = if (this.results.isEmpty()) "no candidates" else "Finished."

            releaseRecognizer()
            restartIfContinuous()

        } else {

            // Still listening — the microphone never closed, so say so rather than
            // flashing "deaf" at someone who is mid-sentence.
            state = TrialState.Listening
            message = "listening"

            // A new segment begins now; reset the per-utterance bookkeeping without
            // touching the recognizer.
            startedAt = SystemClock.elapsedRealtime()
            speechEndedAt = 0L
            speechDetected = false
            partial = ""
        }
    }

    override fun onError(error: Int) {

        soundLevel = 0f

        // Silence is not a fault. When listening continuously these two arrive
        // constantly - every pause produces one - so reporting them as failures makes
        // a perfectly healthy idle state look broken.
        val routine = error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

        // A failure that followed actual speech is the interesting case: you said
        // something and got nothing back. A failure during silence is just a pause.
        if (speechDetected) {

            lastFailure = recognizerErrorName(error)
            lastFailurePartial = partial.ifBlank { null }
            failureCount += 1

            Log.d(LOG_TAG, "platform: FAILED after speech — ${recognizerErrorName(error)} (partial was \"$partial\")")
        }

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

        // Did this attempt actually run, or did it die on the doorstep?
        val elapsed = SystemClock.elapsedRealtime() - utteranceStartedAt
        val diedImmediately = elapsed < FAST_FAILURE_MS && !speechDetected

        if (diedImmediately) fastFailures += 1 else fastFailures = 0

        val delay = restartDelayMs()

        // Past the cap, so give up rather than retry for ever. Whatever is wrong will
        // not be fixed by asking again, and an invisible retry loop is worse than a
        // visible failure: on the emulator, with no recognition service installed,
        // this span ran hundreds of attempts a second and left the process too busy
        // to draw its own window. On a watch it would be a flat battery by lunchtime.
        if (delay > RESTART_DELAY_MAX_MS) {

            continuous = false
            state = TrialState.Failed
            message = "Recognizer failed $fastFailures times immediately - stopped trying"

            Log.w(LOG_TAG, "platform: gave up after $fastFailures immediate failures")
            return
        }

        if (diedImmediately) {
            Log.d(LOG_TAG, "platform: died in $elapsed ms (#$fastFailures), next try in $delay ms")
        }

        mainHandler.postDelayed({ if (continuous) beginUtterance() }, delay)
    }

    /**
     * The normal restart cadence, doubled once per consecutive immediate failure.
     *
     * Deliberately NOT clamped to the cap: the caller needs to see the overshoot,
     * because overshooting is what tells it to stop. Clamping here would hand back
     * exactly the cap for ever and the loop would never end.
     *
     * Returns to the normal cadence the moment a session runs for a sensible
     * length of time, so an isolated hiccup costs nothing.
     */
    private fun restartDelayMs(): Long {

        var delay = RESTART_DELAY_MS

        repeat(fastFailures) {

            delay *= BACKOFF_FACTOR

            // Stop multiplying once it is already past the cap, so a long run of
            // failures cannot overflow the Long.
            if (delay > RESTART_DELAY_MAX_MS) return delay
        }

        return delay
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Reserved by the platform.
    }
}
