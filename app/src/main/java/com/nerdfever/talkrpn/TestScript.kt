package com.nerdfever.talkrpn

/*
 * A scripted comparison between the two engines.
 *
 * The app shows a phrase, you say it, it records what came back and how fast, then
 * advances. When the script finishes it switches engines and runs the same phrases
 * again - so the two are measured on identical input rather than on whatever each
 * happened to be given.
 *
 * No buttons: pressing one costs a screen wake and breaks the flow of speaking.
 */

/**
 * What to say, in order.
 *
 * Chosen to probe specific things rather than to be representative:
 *
 *  1. plain digits, the commonest case
 *  2. a full RPN expression - the real target
 *  3. "hundred" is deliberately NOT in the vocabulary, so this shows what an
 *     out-of-grammar word does to each engine
 *  4. a short two-token command, to see fixed overhead without a long tail
 *  5. a single word, the floor case for latency
 */
val TEST_PHRASES: List<String> = listOf(
    "one two three",
    "nine eight eight enter two three divide",
    "nine hundred eighty eight",
    "five enter plus",
    "clear",
)

/**
 * What one engine produced for one prompt.
 *
 * [firstTokenMs] and [lastTokenMs] are measured from the start of the listening
 * session, so the gap between them shows whether words trickled in as they were
 * spoken or landed together at the end.
 */
data class PhraseResult(

    val engine: String,
    val prompt: String,
    val heard: String,

    /** When the first word of this phrase became available. */
    val firstTokenMs: Long?,

    /** When the last word became available. */
    val lastTokenMs: Long?,

    /** Largest gap between consecutive words — the worst-case per-token wait. */
    val worstGapMs: Long?,

    val tokenCount: Int,
) {

    /** Whether the transcription matches the prompt, ignoring case and spacing. */
    val correct: Boolean
        get() = normalise(heard) == normalise(prompt)

    private fun normalise(s: String) =
        s.lowercase().replace(Regex("[^a-z0-9]"), "")
}

/**
 * Summarise a run of token arrivals into the figures worth comparing.
 *
 * Kept out of the UI so the same arithmetic is applied to both engines.
 */
fun summarise(
    engine: String,
    prompt: String,
    heard: String,
    tokens: List<TokenArrival>,
): PhraseResult {

    val gaps = tokens.zipWithNext { a, b -> b.atMs - a.atMs }

    return PhraseResult(
        engine = engine,
        prompt = prompt,
        heard = heard,
        firstTokenMs = tokens.firstOrNull()?.atMs,
        lastTokenMs = tokens.lastOrNull()?.atMs,
        worstGapMs = gaps.maxOrNull(),
        tokenCount = tokens.size,
    )
}
