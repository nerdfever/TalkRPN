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
 * "e" carries the sheet's row-14 rule verbatim: after digits it is EEX,
 * anywhere else the base of the natural logs. "exponent" is EEX's
 * acoustically strong alias, and "times ten to the" (with its symbol
 * costumes) is EEX spoken long - legal here because this parser sees
 * only finished utterances, where longest-match defuses the prefix rule.
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

        /** The whole utterance was a redo word: replay the last undo. */
        data object Redo : Result
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
        listOf("root") to Token.Sqrt,
        listOf("reciprocal") to Token.Reciprocal,
        listOf("inverse") to Token.Reciprocal,
        listOf("invert") to Token.Reciprocal,
        listOf("change", "sign") to Token.Chs,
        listOf("negate") to Token.Chs,
        listOf("c", "h", "s") to Token.Chs,

        // The sheet's function tier.
        listOf("raise") to Token.Power,
        listOf("power") to Token.Power,
        listOf("squared") to Token.Squared,
        listOf("natural", "log") to Token.Ln,
        listOf("l", "n") to Token.Ln,
        listOf("natural", "antilog") to Token.Exp,
        listOf("sine") to Token.Sin,
        listOf("cosine") to Token.Cos,
        listOf("tangent") to Token.Tan,
        listOf("arcsine") to Token.Asin,
        listOf("arc", "sine") to Token.Asin,
        listOf("arccosine") to Token.Acos,
        listOf("arc", "cosine") to Token.Acos,
        listOf("arctangent") to Token.Atan,
        listOf("arc", "tangent") to Token.Atan,
        listOf("archangent") to Token.Atan, // recognizer mash, from the diary
        listOf("rectangular") to Token.ToRectangular,
        listOf("polar") to Token.ToPolar,
        listOf("positive") to Token.Abs,
        listOf("abs") to Token.Abs,
        listOf("absolute") to Token.Abs,
        listOf("degrees") to Token.Degrees,
        listOf("radians") to Token.Radians,
        listOf("radiance") to Token.Radians, // homophone, from the diary

        listOf("clear", "x") to Token.ClearX,
        listOf("clearlex") to Token.ClearX, // the recognizer's mash of "clear x"
        listOf("clear", "all") to Token.ClearStack,
        listOf("swap") to Token.SwapXY,
        listOf("exchange") to Token.SwapXY,
        listOf("roll", "down") to Token.RollDown,
        listOf("roll") to Token.RollDown,   // a finished-utterance exemption - see the init check
        listOf("drop") to Token.RollDown,
        listOf("roll", "up") to Token.RollUp,
        listOf("last", "x") to Token.LastX,
        listOf("pi") to Token.Pi,
        listOf("pie") to Token.Pi, // the recognizer's spelling of pi - first live homophone

        listOf("store") to Token.Sto,
        listOf("recall") to Token.Rcl,
    )

    /** Spoken digits; numerals like "988" and "2.5" are handled apart. */
    private val DIGIT_WORDS = mapOf(
        "zero" to '0', "one" to '1', "two" to '2', "three" to '3',
        "four" to '4', "five" to '5', "six" to '6', "seven" to '7',
        "eight" to '8', "nine" to '9',

        // The aviation forms, per the sheet - and "oh" for zero.
        "oh" to '0', "fife" to '5', "niner" to '9',
    )

    /** The radix, spoken - "point", or the sheet's "dot". */
    private val POINT_WORDS = setOf("point", "dot")

    /** EEX during number entry: the weak form and its strong fallback. */
    private val EEX_WORDS = setOf("e", "exponent")

    /**
     * EEX spoken the long way: "times ten to the". FORBIDDEN by the
     * prefix rule for real-time parsing ("times" is a token) - but this
     * parser only ever sees FINISHED utterances, where longest-match
     * resolves it safely, so the natural phrase gets to exist. Checked
     * before the phrase table, so it outranks the bare "times". Must be
     * revisited if streaming (partial-result) parsing ever arrives.
     */
    private val EEX_PHRASES: List<List<String>> = buildList {
        // Every costume the recognizer has for the one phrase: spoken or
        // starred times, worded or numeral ten, "to the" or the caret -
        // the full cross product, so no mixed form ever rejects.
        for (times in listOf("times", "*")) {
            for (ten in listOf("ten", "10")) {
                for (tail in listOf(listOf("to", "the"), listOf("^"))) {
                    add(listOf(times, ten) + tail)
                }
            }
        }
    }

    /**
     * A number in the recognizer's clothing: times ("3:55"), feet-inches
     * ("5'6"), ranges ("7-8"). The punctuation is its dressing, not the
     * speaker's - dropped, the digits glue: 355, 56, 78. (Fractions are
     * NOT here: "7/8" means the division, handled apart.)
     */
    private val DRESSED_DIGITS = Regex("""(\d+)[:'-](\d+)""")

    /** A fraction as the recognizer writes one: "7/8" means 7 over 8. */
    private val FRACTION = Regex("""(\d+)/(\d+)""")

    /** DSP as a parsing word: "fix" consumes exactly one number token. */
    private val FIX_WORDS = setOf("fix", "fixed")
    private val SCI_WORDS = setOf("scientific")
    private val ENG_WORDS = setOf("engineering")

    /**
     * The bases a log/antilog parsing word accepts by name; numerals
     * ("10", "2") come through the ordinary number reading.
     */
    private val BASE_WORDS = mapOf("e" to Math.E, "ten" to 10.0, "two" to 2.0)

    /**
     * Reserved in EVERY vocabulary, per DESIGN - the escape hatch must
     * stay reachable once names exist. Alone (or in the two-word forms,
     * which carry more acoustic weight - the recognizer often refuses to
     * finalise a lone short word), these ARE the undo utterance; buried
     * inside longer utterances the words still reject.
     */
    private val UNDO_WORDS =
        setOf("undo", "cancel", "escape", "delete", "backspace", "back")
    private val UNDO_UTTERANCES = setOf(
        listOf("undo"), listOf("cancel"), listOf("escape"),
        listOf("delete"), listOf("backspace"), listOf("back"),
        listOf("undo", "that"), listOf("undo", "it"),
    )

    /** Redo, same shape: the redo stack lives in the engine. */
    private val REDO_UTTERANCES = setOf(
        listOf("redo"), listOf("redo", "that"), listOf("redo", "it"),
        listOf("we", "do"), // the recognizer's spelling of redo, from the diary
    )

    /** A numeral as the recognizer writes one: digits, one optional radix. */
    private val NUMERAL = Regex("""\d+(\.\d+)?|\.\d+""")

    /**
     * Scientific notation glued into one token - "23e17" - another wrist
     * find. Groups: mantissa, optional sign, exponent digits.
     */
    private val SCI_NUMERAL = Regex("""(\d+(?:\.\d+)?)e(-?)(\d+)""")

    init {
        // The table fails loudly the first time someone adds a phrase that
        // is a proper prefix of another - the rule DESIGN wants enforced
        // at startup. Single-word tokens are included via the full list.
        // "roll" beside "roll down"/"roll up" LOOKS like a violation, but
        // the rule guards real-time commitment and this parser sees only
        // finished utterances - longest match keeps them apart, the same
        // exemption "times ten to the" rides on. The check therefore runs
        // with the known finished-utterance exemptions removed.
        val exempt = setOf(listOf("roll"))

        val phrases = PHRASES.keys - exempt +
            DIGIT_WORDS.keys.map { listOf(it) } +
            POINT_WORDS.map { listOf(it) } +
            (FIX_WORDS + SCI_WORDS + ENG_WORDS).map { listOf(it) } +
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

    /**
     * Command phrases as the TRAIL abbreviates them - symbols where a
     * standard one exists, Dave's ask after reading spelled-out words
     * down the column. Longest phrase matched first, like the parser.
     */
    private val TRAIL_SYMBOLS: List<Pair<List<String>, String>> = listOf(
        listOf("times", "ten", "to", "the") to "E",
        listOf("times", "10", "to", "the") to "E",
        listOf("*", "10", "^") to "E",
        listOf("times", "10", "^") to "E",
        listOf("multiplied", "by") to "\u00D7",
        listOf("divided", "by") to "\u00F7",
        listOf("square", "root") to "\u221Ax",
        listOf("change", "sign") to "\u00B1",
        listOf("clear", "x") to "CLx",
        listOf("clear", "all") to "CLR",
        listOf("roll", "down") to "R\u2193",
        listOf("roll", "up") to "R\u2191",
        listOf("last", "x") to "LASTX",
        listOf("plus") to "+",
        listOf("add") to "+",
        listOf("subtract") to "\u2212",
        listOf("-") to "\u2212",
        listOf("times") to "\u00D7",
        listOf("multiply") to "\u00D7",
        listOf("*") to "\u00D7",
        listOf("divide") to "\u00F7",
        listOf("over") to "\u00F7",
        listOf("/") to "\u00F7",
        listOf("enter") to "\u21B5",
        listOf("reciprocal") to "1/x",
        listOf("swap") to "x\u2194y",
        listOf("exchange") to "x\u2194y",
        listOf("pi") to "\u03C0",
        listOf("pie") to "\u03C0",
        listOf("raise") to "y^x",
        listOf("power") to "y^x",
        listOf("squared") to "x\u00B2",
        listOf("natural", "log") to "LN",
        listOf("l", "n") to "LN",
        listOf("natural", "antilog") to "e^x",
        listOf("sine") to "sin",
        listOf("cosine") to "cos",
        listOf("tangent") to "tan",
        listOf("arcsine") to "sin\u207B\u00B9",
        listOf("arc", "sine") to "sin\u207B\u00B9",
        listOf("arccosine") to "cos\u207B\u00B9",
        listOf("arc", "cosine") to "cos\u207B\u00B9",
        listOf("arctangent") to "tan\u207B\u00B9",
        listOf("arc", "tangent") to "tan\u207B\u00B9",
        listOf("rectangular") to "\u2192R",
        listOf("polar") to "\u2192P",
        listOf("roll") to "R\u2193",
        listOf("drop") to "R\u2193",
        listOf("negate") to "\u00B1",
        listOf("c", "h", "s") to "\u00B1",
        listOf("root") to "\u221Ax",
        listOf("inverse") to "1/x",
        listOf("invert") to "1/x",
        listOf("positive") to "|x|",
        listOf("abs") to "|x|",
        listOf("absolute") to "|x|",
        listOf("degrees") to "DEG",
        listOf("radians") to "RAD",
        listOf("log", "base") to "LOG",
        listOf("long", "base") to "LOG",
        listOf("antilog", "base") to "ALOG",
        listOf("radiance") to "RAD",
        listOf("clearlex") to "CLx",
        listOf("archangent") to "tan⁻¹",
        listOf("scientific") to "SCI",
        listOf("engineering") to "ENG",
        listOf("fixed") to "FIX",
        listOf("store") to "STO",
        listOf("recall") to "RCL",
        listOf("fix") to "FIX",
    )

    /**
     * The utterance as the undo TRAIL shows it: digits and the radix
     * compact into numerals ("two three" reads "23"), EEX forms glue an
     * E into the number ("6.5 times ten to the 16" reads "6.5E16"), and
     * commands wear their symbols ("plus" reads "+") - because the
     * trail's column is narrow. A minus right after the E signs it, the
     * parser's own sign rule; anywhere else it is the subtract symbol.
     * Unknown words pass through as spoken. The diagnostic log keeps
     * the verbatim utterance; only the glass gets the shorthand.
     */
    fun trailLabel(utterance: String): String {

        val words = utterance.trim().lowercase()
            .split(Regex("""\s+""")).filter { it.isNotEmpty() }

        // A lone "-" ACTED as force-negative (see parse), so it reads
        // "negative" - the minus sign is taken, it means subtract.
        if (words == listOf("-")) return "negative"

        val pieces = mutableListOf<String>()
        var run: StringBuilder? = null

        fun closeRun() {
            run?.let { pieces += it.toString() }
            run = null
        }

        var at = 0
        while (at < words.size) {

            val word = words[at]

            // What glues INTO a number run: digits in any costume, the
            // radix, an EEX form as E, and the exponent's sign.
            val sci = SCI_NUMERAL.matchEntire(word)
            val glue = when {
                NUMERAL.matches(word) -> word
                sci != null ->
                    sci.groupValues[1] + "E" + sci.groupValues[2] + sci.groupValues[3]
                DRESSED_DIGITS.matches(word) ->
                    word.filter { it.isDigit() }
                DIGIT_WORDS.containsKey(word) -> DIGIT_WORDS[word].toString()
                word in POINT_WORDS -> NumberFormatter.RADIX.toString()
                word in EEX_WORDS && run != null -> "E"
                (word == "minus" || word == "negative") &&
                    run?.endsWith("E") == true -> "-"
                else -> null
            }

            if (glue != null) {
                run = (run ?: StringBuilder()).append(glue)
                at++
                continue
            }

            // The EEX phrases glue an E too, and open a run if none.
            val eexPhrase = EEX_PHRASES.firstOrNull {
                it == words.subList(at, minOf(at + it.size, words.size))
            }
            if (eexPhrase != null) {
                run = (run ?: StringBuilder()).append("E")
                at += eexPhrase.size
                continue
            }

            // A command wears its symbol; longest phrase wins.
            val symbol = TRAIL_SYMBOLS.firstOrNull { (phrase, _) ->
                phrase == words.subList(at, minOf(at + phrase.size, words.size))
            }

            closeRun()

            if (symbol != null) {
                pieces += symbol.second
                at += symbol.first.size
            } else {
                pieces += word
                at++
            }
        }

        closeRun()

        return pieces.joinToString(" ")
    }

    // ---- The parser ----------------------------------------------------------

    fun parse(utterance: String, entryOpen: Boolean = false): Result {

        val words = utterance.trim().lowercase()
            .split(Regex("""\s+""")).filter { it.isNotEmpty() }

        // An undo or redo utterance IS the action itself.
        if (words in UNDO_UTTERANCES) return Result.Undo
        if (words in REDO_UTTERANCES) return Result.Redo

        // A LONE "-" is how the recognizer writes a spoken "negative"
        // (diary, 2026-08-23) - so alone it sets the sign; inside an
        // utterance it stays SUBTRACT, keeping postfix "6 enter 2 -".
        if (words == listOf("-")) {
            return Result.Parsed(listOf(Token.ForceNegative))
        }

        val out = mutableListOf<Token>()

        // The number lexer's whole state: is entry in progress, has EEX
        // opened the exponent, and is the NEXT word the exponent's first -
        // the one place a sign word signs instead of subtracting.
        //
        // [entryOpen] seeds the first: the endpointer can split "6.5 ...
        // e 16" into two utterances, and the second must know the engine
        // is still mid-entry or its "e" would be rejected.
        var inNumber = entryOpen
        var atExponentStart = false

        var at = 0
        while (at < words.size) {

            val word = words[at]

            // Glued scientific notation: "23e17" is 23 EEX 17, sign and
            // all - the number lexer's fields, packed into one token.
            val sci = SCI_NUMERAL.matchEntire(word)
            if (sci != null) {
                for (character in sci.groupValues[1]) out += Token.Digit(character)
                out += Token.Eex
                if (sci.groupValues[2] == "-") out += Token.Chs
                for (character in sci.groupValues[3]) out += Token.Digit(character)
                inNumber = true
                atExponentStart = false
                at++
                continue
            }

            // A dressed number - time, feet-inches, range: the
            // punctuation drops and the digits glue.
            val dressed = DRESSED_DIGITS.matchEntire(word)
            if (dressed != null) {
                for (character in dressed.groupValues[1] + dressed.groupValues[2]) {
                    out += Token.Digit(character)
                }
                inNumber = true
                atExponentStart = false
                at++
                continue
            }

            // A fraction, as the recognizer rewrites one: "7/8" becomes
            // 7 ENTER 8 DIVIDE - what the notation means. (Saying digits
            // "seven eight" CAN arrive compounded this way; if 78 was
            // meant, "seventy eight" survives as "78".)
            val fraction = FRACTION.matchEntire(word)
            if (fraction != null) {
                for (character in fraction.groupValues[1]) out += Token.Digit(character)
                out += Token.Enter
                for (character in fraction.groupValues[2]) out += Token.Digit(character)
                out += Token.Divide
                inNumber = false
                atExponentStart = false
                at++
                continue
            }

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
            if (word in POINT_WORDS) {
                out += Token.Digit(NumberFormatter.RADIX)
                inNumber = true
                atExponentStart = false
                at++
                continue
            }

            // The sheet's row-14 rule, verbatim: "e" after digits is EEX;
            // anywhere else it is the base of the natural logs. Only the
            // bare "e" carries the constant meaning - "exponent" stays
            // EEX-only.
            if (word in EEX_WORDS && inNumber) {
                out += Token.Eex
                atExponentStart = true
                at++
                continue
            }
            if (word == "e") {
                out += Token.EConst
                inNumber = false
                atExponentStart = false
                at++
                continue
            }

            // EEX the long way. Works with no number in progress too: the
            // engine supplies the implicit mantissa 1, so a bare "times
            // ten to the six" is 1e6, HP-21 style.
            val eexPhrase = EEX_PHRASES.firstOrNull {
                it == words.subList(at, minOf(at + it.size, words.size))
            }
            if (eexPhrase != null) {
                out += Token.Eex
                inNumber = true
                atExponentStart = true
                at += eexPhrase.size
                continue
            }

            // THE SIGN RULE: minus immediately after the exponent marker
            // signs the exponent (the engine's CHS negates the exponent
            // mid-entry); everywhere else minus is SUBTRACT, matched from
            // the phrase table below - and "negative" outside that spot
            // is the sheet's force-negative on X.
            if (atExponentStart && (word == "minus" || word == "negative")) {
                out += Token.Chs
                atExponentStart = false
                at++
                continue
            }
            if (word == "negative") {
                out += Token.ForceNegative
                inNumber = false
                at++
                continue
            }

            // The display parsing words - fix/scientific/engineering N:
            // the next token resolves as a NUMBER, DESIGN's separate-
            // vocabulary trick, and the pair becomes one mode token.
            if (word in FIX_WORDS || word in SCI_WORDS || word in ENG_WORDS) {
                val places = words.getOrNull(at + 1)
                    ?.let { DIGIT_WORDS[it]?.digitToInt() ?: it.toIntOrNull() }
                    ?: return Result.Rejected(word)
                out += when (word) {
                    in SCI_WORDS -> Token.SciMode(places)
                    in ENG_WORDS -> Token.EngMode(places)
                    else -> Token.Dsp(places)
                }
                inNumber = false
                atExponentStart = false
                at += 2
                continue
            }

            // log base N / antilog base N - the same trick, the argument
            // resolved against NUMBERS ("e", "ten", "two", or a numeral).
            if ((word == "log" || word == "antilog" || word == "long") &&
                words.getOrNull(at + 1) == "base"
            ) {
                val argument = words.getOrNull(at + 2)
                val base = argument?.let {
                    BASE_WORDS[it]
                        ?: DIGIT_WORDS[it]?.digitToInt()?.toDouble()
                        ?: it.toDoubleOrNull()
                } ?: return Result.Rejected(word)

                out += if (word == "antilog") Token.AntilogBase(base)
                else Token.LogBase(base)
                inNumber = false
                atExponentStart = false
                at += 3
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
