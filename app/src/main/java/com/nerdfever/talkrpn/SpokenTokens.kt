package com.nerdfever.talkrpn

import com.nerdfever.talkrpn.RpnEngine.Token

/*
 * SpokenTokens - one spoken utterance into engine tokens, per the parser
 * decisions in DESIGN.md ("Confirming what was heard", "Number entry",
 * "Constraint on aliases").
 *
 * THE WHOLE UTTERANCE PARSES OR NONE OF IT APPLIES. Parsing runs to
 * completion before anything reaches the engine, so an unknown word
 * rejects the utterance atomically - the rollback reading of DESIGN's
 * open question, chosen because a garbled tail is evidence the whole
 * utterance is suspect, and because it falls out of parse-then-apply
 * for free.
 *
 * The recognizer rewrites speech - digits arrive as numerals ("988",
 * "2.5") and operators as symbols ("/") - so the vocabulary accepts
 * forms no human would utter, alongside the spoken ones.
 *
 * THE SIGN RULE, sharpened from DESIGN's draft: a sign word signs the
 * EXPONENT field only ("five e minus three" is 5e-3). Before a mantissa
 * it reads as SUBTRACT - otherwise "five minus three" would be ambiguous
 * between 5-3 and 5, -3. Mantissa negation is "change sign", exactly the
 * HP keyboard's CHS.
 *
 * "e" means EEX only while number entry is in progress; anywhere else it
 * is rejected (the constant e is not in the engine yet). "exponent" is
 * its acoustically strong alias - the fallback DESIGN wants for e's
 * weakness, chosen over "times ten to the", which the no-proper-prefix
 * rule forbids ("times" is a token).
 *
 * Pure Kotlin, shared with the :repl process, JVM-tested.
 */

object SpokenTokens {

    /** Parsed in full, or rejected in full by its first unknown word. */
    sealed interface Result {
        data class Parsed(val tokens: List<Token>) : Result
        data class Rejected(val word: String) : Result

        /** The whole utterance was an undo word: restore the last mark. */
        data object Undo : Result
    }

    // ---- The vocabulary -----------------------------------------------------
    //
    // Command phrases, longest match first at each position. Multi-word
    // phrases are fine; what is forbidden is one token being a proper
    // PREFIX of another (see the init check below) - "clear" cannot exist
    // beside "clear x", which is why the stack wipe is "clear all".

    private val PHRASES: Map<List<String>, Token> = mapOf(
        listOf("enter") to Token.Enter,

        // The bare verbs are the REAL RPN forms - "six enter two divide" -
        // and the first thing a live wrist test asked for. The infix-
        // flavoured aliases stay for how people actually talk. (Word-wise
        // the prefix rule is safe: "divide" is not a prefix of
        // "divided by", because "divide" and "divided" are different
        // words.)
        listOf("plus") to Token.Add,
        listOf("add") to Token.Add,
        listOf("+") to Token.Add,
        listOf("minus") to Token.Subtract,
        listOf("subtract") to Token.Subtract,
        listOf("-") to Token.Subtract,
        listOf("times") to Token.Multiply,
        listOf("multiply") to Token.Multiply,
        listOf("multiplied", "by") to Token.Multiply,
        listOf("*") to Token.Multiply,
        listOf("divide") to Token.Divide,
        listOf("divided", "by") to Token.Divide,
        listOf("over") to Token.Divide,
        listOf("/") to Token.Divide,

        listOf("square", "root") to Token.Sqrt,
        listOf("reciprocal") to Token.Reciprocal,
        listOf("change", "sign") to Token.Chs,

        listOf("clear", "x") to Token.ClearX,
        listOf("clear", "all") to Token.ClearStack,
        listOf("swap") to Token.SwapXY,
        listOf("exchange") to Token.SwapXY,
        listOf("roll", "down") to Token.RollDown,
        listOf("roll", "up") to Token.RollUp,
        listOf("last", "x") to Token.LastX,
        listOf("pi") to Token.Pi,

        listOf("store") to Token.Sto,
        listOf("recall") to Token.Rcl,
    )

    /** Spoken digits; numerals like "988" and "2.5" are handled apart. */
    private val DIGIT_WORDS = mapOf(
        "zero" to '0', "one" to '1', "two" to '2', "three" to '3',
        "four" to '4', "five" to '5', "six" to '6', "seven" to '7',
        "eight" to '8', "nine" to '9',
    )

    /** The radix, spoken. */
    private const val POINT_WORD = "point"

    /** EEX during number entry: the weak form and its strong fallback. */
    private val EEX_WORDS = setOf("e", "exponent")

    /** DSP as a parsing word: "fix" consumes exactly one number token. */
    private const val FIX_WORD = "fix"

    /**
     * Reserved in EVERY vocabulary, per DESIGN - the escape hatch must
     * stay reachable once names exist. Alone, any of these IS the undo
     * utterance; buried inside a longer utterance they still reject,
     * because "five undo plus" is nobody's intent.
     */
    private val UNDO_WORDS = setOf("undo", "cancel", "escape")

    /** A numeral as the recognizer writes one: digits, one optional radix. */
    private val NUMERAL = Regex("""\d+(\.\d+)?|\.\d+""")

    init {
        // The table fails loudly the first time someone adds a phrase that
        // is a proper prefix of another - the rule DESIGN wants enforced
        // at startup. Single-word tokens are included via the full list.
        val phrases = PHRASES.keys +
            DIGIT_WORDS.keys.map { listOf(it) } +
            listOf(listOf(POINT_WORD), listOf(FIX_WORD)) +
            EEX_WORDS.map { listOf(it) }

        assertNoProperPrefixes(phrases)
    }

    /** Throws when any phrase is a proper word-wise prefix of another. */
    internal fun assertNoProperPrefixes(phrases: Collection<List<String>>) {
        for (a in phrases) for (b in phrases) {
            check(a == b || a.size >= b.size || a != b.take(a.size)) {
                "vocabulary: '${a.joinToString(" ")}' is a proper prefix " +
                    "of '${b.joinToString(" ")}'"
            }
        }
    }

    // ---- The parser ----------------------------------------------------------

    fun parse(utterance: String): Result {

        val words = utterance.trim().lowercase()
            .split(Regex("""\s+""")).filter { it.isNotEmpty() }

        // An undo word as the WHOLE utterance is the undo itself.
        if (words.size == 1 && words[0] in UNDO_WORDS) return Result.Undo

        val out = mutableListOf<Token>()

        // The number lexer's whole state: is entry in progress, has EEX
        // opened the exponent, and is the NEXT word the exponent's first -
        // the one place a sign word signs instead of subtracting.
        var inNumber = false
        var atExponentStart = false

        var at = 0
        while (at < words.size) {

            val word = words[at]

            // Numerals, as the recognizer compounds them: each character
            // is one digit press, the radix included.
            if (NUMERAL.matches(word)) {
                for (character in word) out += Token.Digit(character)
                inNumber = true
                atExponentStart = false
                at++
                continue
            }

            // Spoken digits and the radix.
            val digit = DIGIT_WORDS[word]
            if (digit != null) {
                out += Token.Digit(digit)
                inNumber = true
                atExponentStart = false
                at++
                continue
            }
            if (word == POINT_WORD) {
                out += Token.Digit(NumberFormatter.RADIX)
                inNumber = true
                atExponentStart = false
                at++
                continue
            }

            // EEX, only while a number is in progress - "e" anywhere else
            // is rejected until the constant exists in the engine.
            if (word in EEX_WORDS && inNumber) {
                out += Token.Eex
                atExponentStart = true
                at++
                continue
            }

            // THE SIGN RULE: minus immediately after the exponent marker
            // signs the exponent (the engine's CHS negates the exponent
            // mid-entry); everywhere else minus is SUBTRACT, matched from
            // the phrase table below.
            if (atExponentStart && (word == "minus" || word == "negative")) {
                out += Token.Chs
                atExponentStart = false
                at++
                continue
            }

            // FIX is a parsing word: the next token is resolved as a
            // NUMBER, DESIGN's separate-vocabulary trick, and the pair
            // becomes one Dsp token.
            if (word == FIX_WORD) {
                val places = words.getOrNull(at + 1)
                    ?.let { DIGIT_WORDS[it]?.digitToInt() ?: it.toIntOrNull() }
                    ?: return Result.Rejected(word)
                out += Token.Dsp(places)
                inNumber = false
                atExponentStart = false
                at += 2
                continue
            }

            // An undo word inside a longer utterance rejects - visibly.
            if (word in UNDO_WORDS) return Result.Rejected(word)

            // Commands: the longest phrase that matches at this position.
            val match = PHRASES.entries
                .filter { (phrase, _) -> phrase == words.subList(at, minOf(at + phrase.size, words.size)) }
                .maxByOrNull { (phrase, _) -> phrase.size }
                ?: return Result.Rejected(word)

            out += match.value
            inNumber = false
            atExponentStart = false
            at += match.key.size
        }

        return Result.Parsed(out)
    }
}
