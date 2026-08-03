package com.nerdfever.talkrpn

/*
 * The words the calculator will eventually need to hear.
 *
 * Nothing here drives any logic yet — at this stage the list exists purely to be
 * handed to the recognizer as a *bias*, so the probe tests recognition of the
 * vocabulary we actually care about rather than of open-ended English.
 *
 * Biasing is not the same as a grammar. A grammar restricts the recognizer to a
 * fixed word set and refuses everything else; biasing only tilts the odds. Android's
 * platform recognizer offers biasing, not grammars. If accuracy on this list turns
 * out to be poor, a bundled engine with a real grammar constraint is the fallback —
 * and that decision is exactly what this probe exists to inform.
 */

/**
 * Whether to point either engine at [CALCULATOR_VOCABULARY].
 *
 * Currently **off**, so the two are compared unaided and on equal terms. Left as a
 * single switch rather than deleted, because the two engines use the vocabulary very
 * differently and both are worth re-testing later:
 *
 *   Vosk     - a hard grammar. Nothing outside the list can be returned at all.
 *   Platform - a soft bias. Odds are tilted; anything may still be returned.
 *
 * Turning it on distorts exactly the behaviour worth measuring first - how each engine
 * handles compound numbers like "six hundred seventy three" - because a list of bare
 * digits pulls the result towards bare digits.
 */
const val BIAS_TO_VOCABULARY = false

/**
 * A candidate command vocabulary.
 *
 * Kept deliberately under the ~50 word target: a short list is what makes constrained
 * recognition plausible on a watch, so it is a design constraint rather than a
 * starting point to grow from.
 */
val CALCULATOR_VOCABULARY: List<String> = listOf(

    // Digits, and the decimal point.
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "point",

    // The one piece of RPN grammar that is a word rather than an operator.
    "enter",

    // Arithmetic.
    "plus", "minus", "times", "divided by", "percent",

    // Stack manipulation — the operations that make RPN worth having.
    "swap", "drop", "clear", "last x", "undo",

    // Correcting entry.
    //
    // "backspace" was tried and rejected - the Vosk small en-us model has no such word
    // in its lexicon, and it logged "Ignoring word missing in vocabulary". A grammar
    // can only contain words the acoustic model knows, which is a real constraint on
    // naming commands: invented or compound words are simply unavailable.
    "delete", "back",

    // Sign and powers.
    "change sign", "squared", "square root", "inverse", "exponent",

    // Transcendental functions.
    "sine", "cosine", "tangent", "log", "natural log", "pi",

    // Storage registers.
    "store", "recall",
)
