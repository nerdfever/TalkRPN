package com.nerdfever.talkrpn

/*
 * The boundary between "how words are heard" and "what the calculator does with them".
 *
 * Two implementations exist, and the point of the interface is that the calculator
 * will never know which one it is talking to:
 *
 *   PlatformSpeechSource - Android's built-in recognizer. Free, no download, good
 *                          accuracy, but batch-only, no confidence scores, and no
 *                          way to *guarantee* it stays off the network.
 *   VoskSpeechSource     - a bundled Kaldi model. Genuinely offline, streams word
 *                          by word, gives per-word confidence, and can be
 *                          constrained to a fixed vocabulary.
 *
 * Keeping this seam clean is what lets the engine be swapped later without the
 * calculator noticing - which matters, because the platform recognizer may well
 * improve, and the choice made today should not be permanent.
 */

/** How far along a recognition attempt is. */
enum class TrialState {
    Idle,
    Starting,
    Listening,
    Hearing,
    Finished,
    Failed,
}

/**
 * A single word, and when it first became available to the app.
 *
 * This is the latency that actually matters to a calculator: not "how long after I
 * stopped talking did I get the sentence", but "how long after I said a word could I
 * act on it". A calculator wants to push a digit the moment it is heard, and must not
 * be made to wait for the utterance to end.
 *
 * [atMs] is measured from the start of the listening session, so consecutive gaps show
 * whether tokens really arrive independently or are batched at the end.
 */
data class TokenArrival(
    val token: String,
    val atMs: Long,
)

/**
 * Work out which words in a new partial are new, and stamp them with the time.
 *
 * Shared by both engines rather than written twice - the timestamping rule has to be
 * identical or the two are not comparable, which is the entire point of measuring.
 *
 * Revision is handled by truncation: if the engine changes its mind about an earlier
 * word, every token from that position on is re-stamped, because they have in effect
 * only just arrived. That makes a retraction visible as a jump in the timings rather
 * than silently crediting the engine with an early delivery it took back.
 */
fun stampNewTokens(
    seen: List<TokenArrival>,
    partialText: String,
    atMs: Long,
): List<TokenArrival> {

    val words = partialText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    // Keep the leading run that still agrees with what we already had.
    val agreed = words
        .zip(seen)
        .takeWhile { (word, previous) -> word == previous.token }
        .map { (_, previous) -> previous }

    // Everything past that point counts as arriving now.
    val fresh = words.drop(agreed.size).map { TokenArrival(it, atMs) }

    return agreed + fresh
}

/**
 * One interpretation of what was heard, with how sure the engine is about it.
 *
 * Confidence is nullable because not every engine supplies it - Android's returns
 * the field and leaves it at zero, which is worse than omitting it, since a naive
 * reader would take that as "certainly wrong".
 */
data class Candidate(
    val text: String,
    val confidence: Float?,
)

/**
 * Something that turns speech into text.
 *
 * Every property is Compose state in both implementations, so reading one inside a
 * composable makes that composable follow it. The interface deliberately exposes
 * the diagnostics too - not just the answer - because comparing the two engines
 * fairly is the whole reason both exist right now.
 */
interface SpeechSource {

    /** Short name for the report. */
    val label: String

    /**
     * Whether this engine can be *guaranteed* not to reach the network.
     *
     * Note this is about the guarantee, not the observed behaviour: the platform
     * recognizer demonstrably works with the radio off, but offers no way to
     * require it, so it answers false.
     */
    val guaranteedOffline: Boolean

    /** Whether the engine can report a live guess while the user is still speaking. */
    val streams: Boolean

    /**
     * Keep listening after each result instead of stopping.
     *
     * Set before [start]. Continuous operation is what the calculator will actually
     * want - you should be able to keep talking - and it is also the only way to
     * measure per-token latency across a natural run of speech.
     */
    var continuous: Boolean

    /**
     * Ask the engine to avoid the network.
     *
     * A request, not a guarantee — the platform recognizer decides for itself and
     * never reports which path it took. Exposed so the two settings can be compared,
     * but the only *proof* of local operation is having no network at all.
     */
    var preferOffline: Boolean

    val state: TrialState
    val partial: String
    val partialUpdates: Int

    /**
     * Number of recognitions that failed *after speech was detected*.
     *
     * Counted separately from silence timeouts, which happen constantly while
     * listening continuously and mean nothing. This counts the case that matters:
     * you spoke, the engine heard you begin, and it returned nothing.
     *
     * Increments so a caller can observe each new failure and record it.
     */
    val failureCount: Int

    /** Name of the most recent such failure. */
    val lastFailure: String?

    /** What the engine had heard, if anything, when it gave up. */
    val lastFailurePartial: String?

    /** Every word heard this session, with the moment it became available. */
    val tokens: List<TokenArrival>

    val results: List<Candidate>

    /**
     * Increments once per delivered result, so a consumer can observe
     * every utterance - [results] alone cannot distinguish saying the
     * same thing twice, and the calculator must not drop the second
     * "five plus" because it equals the first.
     */
    val resultCount: Int

    val soundLevel: Float
    val message: String?

    /** Milliseconds from pressing the button to the final result. */
    val totalMs: Long?

    /** Milliseconds from the end of speech to the final result - the inference cost. */
    val processingMs: Long?

    /**
     * Milliseconds the microphone was shut between utterances, or null if it never is.
     *
     * Anything spoken during this window is lost outright. The platform recognizer
     * closes the microphone after every result and has to be restarted, so it has a
     * real gap; Vosk holds a single continuous audio stream and has none.
     */
    val deafWindowMs: Long?

    /**
     * Called for every partial hypothesis, before any of the state above updates.
     *
     * Exists because partials are where the useful timing lives and they are far too
     * frequent to observe reliably through Compose state — several arrive between
     * recompositions, so a UI-driven observer would miss most of them.
     *
     * Arguments: the full hypothesis text, milliseconds since the utterance began,
     * and the tokens that are new relative to the previous hypothesis.
     */
    var onPartial: ((String, Long, List<TokenArrival>) -> Unit)?

    /** Begin listening. Safe to call repeatedly; any previous attempt is torn down. */
    fun start()

    /** Abandon the current attempt. */
    fun cancel()

    /** Release platform resources. Call from the Activity's teardown. */
    fun dispose()
}
