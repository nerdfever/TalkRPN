package com.nerdfever.talkrpn

import kotlin.math.sqrt

/*
 * RpnEngine - the classical four-level RPN machine, exactly as settled in
 * the design notes (DESIGN.md, "The RPN engine").
 *
 * The whole state is: the stack X Y Z T, LAST X, the single storage
 * register (as on the HP-21, so STO and RCL take no argument), the
 * digit-entry buffer, and ONE boolean - noLift, HP's stack-lift-enable flag
 * with inverted polarity: true means THE NEXT DIGIT MUST NOT LIFT. That one
 * bit suffices because every non-digit token clears the buffer, which
 * collapses "mid-entry" and "post-ENTER, buffer empty" into a single code
 * path: don't lift, append to the buffer, and appending to an empty buffer
 * is starting fresh.
 *
 * The rules:
 *
 *   1  A digit when noLift is false: lift (T<-Z<-Y<-X), set noLift, start
 *      the buffer.
 *   2  A digit when noLift is true: append to the buffer, copy its value
 *      into X. No lift.
 *   3  Every non-digit clears the buffer. (One exception: CHS mid-entry
 *      edits the buffer instead - see [Token.Chs].)
 *   4  Every non-digit then sets noLift per its DISPOSITION - see
 *      [dispositionOf]: DISABLING sets it, ENABLING clears it, NEUTRAL
 *      leaves it alone.
 *
 * VALUE PRODUCERS (RCL, LAST X, pi) put a value in X without consuming one:
 * they consult noLift first - lift when it is false, overwrite X when it is
 * true (which is what makes 2 ENTER RCL 1 x multiply the recalled value by
 * 2) - and are ENABLING afterwards, so a following digit lifts and keeps
 * the produced value.
 *
 * ERRORS (divide by zero, root of a negative, ...) leave the stack and
 * LAST X untouched and raise [error]; the display shows a word instead of
 * X. The next token clears the flag and executes normally. Provisional -
 * the error UX is undesigned.
 *
 * Everything here is pure Kotlin with no Android in it, tested on the JVM
 * like the formatter.
 */

class RpnEngine {

    // ---- The machine's whole state --------------------------------------------

    var x = 0.0; private set
    var y = 0.0; private set
    var z = 0.0; private set
    var t = 0.0; private set

    var lastX = 0.0; private set

    /** THE storage register - one, as on the HP-21, so STO takes no argument. */
    var storage = 0.0; private set

    /** Digit entry in progress, exactly as spoken/typed so far. */
    private var buffer = ""

    /** THE bit: true = the next digit must not lift. */
    private var noLift = false

    /** Raised by a failed operation; cleared by the next token. */
    var error = false; private set

    /** DSP - shown decimal places. Engine state so DSP is a real (neutral) token. */
    var dspPlaces = DEFAULT_DSP_PLACES; private set

    // ---- The tokens ---------------------------------------------------------------

    sealed interface Token {

        /** '0'-'9', or the radix. What the digit path consumes. */
        data class Digit(val character: Char) : Token

        // Stack and entry control.
        data object Enter : Token
        data object ClearX : Token
        data object ClearStack : Token
        data object Chs : Token
        data object SwapXY : Token
        data object RollDown : Token
        data object RollUp : Token

        // Two-number operations.
        data object Add : Token
        data object Subtract : Token
        data object Multiply : Token
        data object Divide : Token

        // One-number operations.
        data object Sqrt : Token
        data object Reciprocal : Token

        // Value producers.
        data object LastX : Token
        data object Pi : Token
        data object Rcl : Token

        // Storage and modes. STO takes no argument - one register, as on
        // the HP-21. DSP's argument is resolved by the token parser's
        // one-token lookahead, so it arrives here complete; the machine
        // never holds a half-token mode.
        data object Sto : Token
        data class Dsp(val places: Int) : Token
    }

    /**
     * What a non-digit token does to the noLift bit AFTER its own work -
     * the three-disposition table from the design notes, as data.
     *
     * DISABLING - the token has already put X where the user wants it, and
     * the next digit must overwrite rather than push: ENTER (it did its own
     * lift), CLx and CLEAR (the zero is a placeholder), and STO - the
     * HP-35 reading, where 2.55 STO then digits starts a fresh number. THE
     * OPEN QUESTION: the HP-41/42S made STO enabling instead; one line here
     * flips it when the Free42 oracle rules.
     *
     * NEUTRAL - display and mode commands, which should be invisible to
     * entry: 2 ENTER DSP 4 3 x must still multiply by 2.
     *
     * ENABLING - everything else: the operation ended number entry, and the
     * next digit starts a new number on a lifted stack.
     */
    private enum class Disposition { ENABLING, DISABLING, NEUTRAL }

    private fun dispositionOf(token: Token): Disposition = when (token) {
        Token.Enter, Token.ClearX, Token.ClearStack, Token.Sto -> Disposition.DISABLING
        is Token.Dsp -> Disposition.NEUTRAL
        else -> Disposition.ENABLING
    }

    // ---- The machine ----------------------------------------------------------------

    fun press(token: Token) {

        // Any token acknowledges a standing error; the machine underneath
        // was never disturbed, so it just carries on.
        error = false

        if (token is Token.Digit) {
            pressDigit(token.character)
            return
        }

        // CHS mid-entry is the one non-digit that EDITS the buffer rather
        // than clearing it: it flips the sign of the number being entered,
        // and entry continues as though nothing happened.
        if (token is Token.Chs && buffer.isNotEmpty()) {
            buffer = if (buffer.startsWith("-")) buffer.drop(1) else "-$buffer"
            x = valueOf(buffer)
            return
        }

        // Rule 3: number entry is over, however this token turns out. The
        // producers still need to know it WAS in progress - see [produce].
        val wasMidEntry = buffer.isNotEmpty()
        buffer = ""

        when (token) {

            // ENTER performs its own lift, duplicating X upward.
            Token.Enter -> {
                t = z; z = y; y = x
            }

            Token.ClearX -> x = 0.0

            // CLEAR empties the stack but never LAST X.
            Token.ClearStack -> {
                x = 0.0; y = 0.0; z = 0.0; t = 0.0
            }

            // CHS with no entry in progress is an operation on X.
            Token.Chs -> x = -x

            Token.SwapXY -> {
                val held = x; x = y; y = held
            }

            Token.RollDown -> {
                val held = x; x = y; y = z; z = t; t = held
            }

            Token.RollUp -> {
                val held = t; t = z; z = y; y = x; x = held
            }

            Token.Add -> twoNumber { a, b -> a + b }
            Token.Subtract -> twoNumber { a, b -> a - b }
            Token.Multiply -> twoNumber { a, b -> a * b }
            Token.Divide -> twoNumber { a, b ->
                if (b == 0.0) return fail() else a / b
            }

            Token.Sqrt -> oneNumber { a ->
                if (a < 0.0) return fail() else sqrt(a)
            }

            Token.Reciprocal -> oneNumber { a ->
                if (a == 0.0) return fail() else 1.0 / a
            }

            Token.LastX -> produce(lastX, wasMidEntry)
            Token.Pi -> produce(Math.PI, wasMidEntry)
            Token.Rcl -> produce(storage, wasMidEntry)

            Token.Sto -> storage = x

            is Token.Dsp -> dspPlaces = token.places

            is Token.Digit -> Unit // handled above; here for exhaustiveness
        }

        // Rule 4, from the table. (CHS mid-entry never reaches here - its
        // early return above IS its neutrality; with an empty buffer it was
        // an operation on X, and the table's ENABLING default applies.)
        noLift = when (dispositionOf(token)) {
            Disposition.DISABLING -> true
            Disposition.ENABLING -> false
            Disposition.NEUTRAL -> noLift
        }
    }

    // ---- The moving parts -----------------------------------------------------------

    /** Rules 1 and 2: the only tokens that can lift the stack themselves. */
    private fun pressDigit(character: Char) {

        if (!noLift) {
            t = z; z = y; y = x
            buffer = ""
            noLift = true
        }

        // A second radix is refused rather than corrupting the number - the
        // speech layer may well hand one over.
        if (character == NumberFormatter.RADIX && buffer.contains(NumberFormatter.RADIX)) return

        buffer += character
        x = valueOf(buffer)
    }

    /** A two-number operation: consumes X and Y, and T replicates downward. */
    private inline fun twoNumber(operation: (Double, Double) -> Double) {

        val result = operation(y, x)

        lastX = x
        x = result
        y = z
        z = t
    }

    /** A one-number operation: replaces X in place. */
    private inline fun oneNumber(operation: (Double) -> Double) {

        val result = operation(x)

        lastX = x
        x = result
    }

    /**
     * A value producer: lifts when lift is enabled OR when number entry was
     * in progress, and overwrites X only in the post-ENTER/CLx state - so
     * 5 RCL 1 + adds to the 5, while 2 ENTER RCL 1 x multiplies the 2.
     *
     * This is the one place the single bit is not enough by itself: mid-entry
     * and post-ENTER share a bit state but need different producer
     * behaviour. The buffer, which already exists, is the disambiguator -
     * still one bit of PERSISTENT machine state. (The overwrite half is the
     * rpn83p zero-argument-producer bug, guarded by test.)
     */
    private fun produce(value: Double, wasMidEntry: Boolean) {

        if (!noLift || wasMidEntry) {
            t = z; z = y; y = x
        }

        x = value
    }

    /**
     * The failed operation's exit: nothing moved, nothing consumed, the
     * word goes up. The token's disposition still applies on the way out -
     * press() has already run, so noLift is set below as usual... except it
     * is not: returning from inside the when skips rule 4, deliberately,
     * leaving the machine EXACTLY as it stood.
     */
    private fun fail() {
        error = true
    }

    /** The buffer as a number: tolerant of "", "-", and a leading radix. */
    private fun valueOf(text: String): Double {

        val normalised = text
            .replace(NumberFormatter.RADIX, '.')
            .let { if (it == "" || it == "-") it + "0" else it }
            .let { if (it.startsWith(".")) "0$it" else it }
            .let { if (it.startsWith("-.")) "-0" + it.drop(1) else it }

        return normalised.toDoubleOrNull() ?: 0.0
    }

    companion object {
        const val DEFAULT_DSP_PLACES = 3
    }
}
