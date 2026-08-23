package com.nerdfever.talkrpn

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

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
 * The rules ("a digit" below means any number-entry token - a digit
 * proper, or EEX, which appends the exponent marker instead of a
 * character):
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
 * UNDO remembers the whole machine at every [mark] - unlimited - and
 * [saveState]/[loadState] carry the machine and its history across app
 * exits as plain text.
 *
 * Everything here is pure Kotlin with no Android in it, tested on the JVM
 * like the formatter.
 */

class RpnEngine(
    /**
     * The display field's positions, which SIZE NUMBER ENTRY - the HP-55
     * rule: once the mantissa holds this many digits, further digits are
     * ignored, exactly as if the key did nothing. Callers pass their own
     * display's field size; the default only serves tests. (The HP-42S
     * rule instead - an ellipsis in the leftmost position, entry running
     * on with only the rightmost digits visible - is noted in DESIGN.md
     * as a maybe-later.)
     */
    private val entryPositions: Int = DEFAULT_ENTRY_POSITIONS,
) {

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

    /**
     * The entry in progress, for the display: while this is non-empty the
     * display shows IT, verbatim - the HP way, where typing the first
     * digit blanks the formatted value and the digits appear as typed -
     * and goes back to formatting X when it empties.
     */
    val entry: String get() = buffer

    /** THE bit: true = the next digit must not lift. */
    private var noLift = false

    /** Raised by a failed operation; cleared by the next token. */
    var error = false; private set

    /** DSP - shown decimal places. Engine state so DSP is a real (neutral) token. */
    var dspPlaces = DEFAULT_DSP_PLACES; private set

    /** The display's notation - FIX, SCI or ENG - set by the mode tokens. */
    var dspMode = NumberFormatter.Mode.FIX; private set

    /** DEG or RAD - what the trig tokens read, and the annunciator shows. */
    enum class AngleMode { DEG, RAD }

    var angleMode = AngleMode.DEG; private set

    // ---- Undo: the whole machine, remembered ------------------------------------

    /**
     * A complete copy of the machine - every field above - plus the
     * LABEL of the input group that followed it: the utterance or pad
     * word, which is what an on-screen undo trail shows.
     */
    private data class Snapshot(
        val x: Double, val y: Double, val z: Double, val t: Double,
        val lastX: Double, val storage: Double,
        val buffer: String, val noLift: Boolean,
        val error: Boolean, val dspPlaces: Int,
        val dspMode: NumberFormatter.Mode, val angleMode: AngleMode,
        val label: String,
    )

    /**
     * UNLIMITED undo: one snapshot per mark, never trimmed. A snapshot is
     * ten scalars and a short label, so even a day of talking costs next
     * to nothing.
     */
    private val history = mutableListOf<Snapshot>()

    /**
     * Remember the machine as it stands. Callers mark once per INPUT
     * GROUP - a whole utterance, or one typed token - so undo steps by
     * what the user DID, not by internal keypresses. [label] is that
     * input, verbatim; it becomes the trail entry undo removes.
     */
    fun mark(label: String = "") {
        history += snapshotNow(label)
        redoStack.clear()
    }

    /**
     * What each un-undone input group WAS, oldest first - the undo
     * trail. One entry per mark; undo removes the newest.
     */
    val undoLabels: List<String>
        get() = history.map { it.label }

    /**
     * Rewrites the newest mark's label - for an utterance that CONTINUES
     * the previous input group ("1.515" then "35 times" gluing into one
     * number entry), which extends the group instead of starting one.
     * False when there is no mark to relabel.
     */
    fun relabelLastMark(label: String): Boolean {

        val last = history.removeLastOrNull() ?: return false
        history += last.copy(label = label)

        return true
    }

    /**
     * Undone snapshots, waiting for redo. Cleared by any new mark - a
     * fresh input invalidates the future it replaced. Session-only:
     * deliberately not persisted.
     */
    private val redoStack = mutableListOf<Snapshot>()

    /** Restore the newest mark. False when there is nothing to undo. */
    fun undo(): Boolean {

        val then = history.removeLastOrNull() ?: return false

        // Remember where we stand, labelled as the group being undone,
        // so redo can walk forward again.
        redoStack += snapshotNow(then.label)
        restore(then)

        return true
    }

    /** Replay the newest undo. False when there is nothing to redo. */
    fun redo(): Boolean {

        val next = redoStack.removeLastOrNull() ?: return false

        history += snapshotNow(next.label)
        restore(next)

        return true
    }

    private fun snapshotNow(label: String = "") =
        Snapshot(
            x, y, z, t, lastX, storage, buffer, noLift, error, dspPlaces,
            dspMode, angleMode, label,
        )

    private fun restore(then: Snapshot) {
        x = then.x; y = then.y; z = then.z; t = then.t
        lastX = then.lastX; storage = then.storage
        buffer = then.buffer; noLift = then.noLift
        error = then.error; dspPlaces = then.dspPlaces
        dspMode = then.dspMode; angleMode = then.angleMode
    }

    // ---- Persistence: the machine as text ---------------------------------------

    /**
     * The whole machine as text - the undo history oldest first, then the
     * machine as it stands on the last line - so the app can keep it
     * across exits and hand it back to [loadState] on the next launch.
     * A calculator that forgets its stack on a three-minute timeout would
     * be no calculator at all.
     */
    fun saveState(): String =
        (history + snapshotNow()).joinToString("\n") { encode(it) }

    /**
     * Restores a [saveState] text. False - and the machine untouched -
     * when the text does not parse, so a corrupt store means a clean
     * power-on rather than a garbled one.
     */
    fun loadState(text: String): Boolean {

        val snapshots = text.split('\n')
            .filter { it.isNotBlank() }
            .map { decode(it) ?: return false }

        val current = snapshots.lastOrNull() ?: return false

        history.clear()
        history += snapshots.dropLast(1)
        restore(current)

        return true
    }

    /**
     * One snapshot as one tab-separated line; [decode]'s exact inverse.
     * The label rides LAST - it never holds a tab, so no escaping. Older
     * stores load by their size: ten fields predate labels, eleven
     * predate the display and angle modes.
     */
    private fun encode(s: Snapshot): String = listOf(
        s.x, s.y, s.z, s.t, s.lastX, s.storage,
        s.buffer, s.noLift, s.error, s.dspPlaces,
        s.dspMode.name, s.angleMode.name, s.label,
    ).joinToString("\t")

    private fun decode(line: String): Snapshot? {

        val parts = line.split('\t')
        if (parts.size != 10 && parts.size != 11 && parts.size != 13) return null

        return Snapshot(
            parts[0].toDoubleOrNull() ?: return null,
            parts[1].toDoubleOrNull() ?: return null,
            parts[2].toDoubleOrNull() ?: return null,
            parts[3].toDoubleOrNull() ?: return null,
            parts[4].toDoubleOrNull() ?: return null,
            parts[5].toDoubleOrNull() ?: return null,
            parts[6],
            parts[7].toBooleanStrictOrNull() ?: return null,
            parts[8].toBooleanStrictOrNull() ?: return null,
            parts[9].toIntOrNull() ?: return null,

            if (parts.size == 13) {
                runCatching { NumberFormatter.Mode.valueOf(parts[10]) }
                    .getOrNull() ?: return null
            } else NumberFormatter.Mode.FIX,

            if (parts.size == 13) {
                runCatching { AngleMode.valueOf(parts[11]) }
                    .getOrNull() ?: return null
            } else AngleMode.DEG,

            // The label is always last, whatever the vintage.
            if (parts.size == 13) parts[12] else parts.getOrNull(10) ?: "",
        )
    }

    // ---- The tokens ---------------------------------------------------------------

    sealed interface Token {

        /** '0'-'9', or the radix. What the digit path consumes. */
        data class Digit(val character: Char) : Token

        /**
         * EEX - open the exponent field of the number being entered. A
         * number-entry token like Digit, not an operation: on an empty
         * entry it supplies the implicit mantissa 1 (EEX 5 means 1e5,
         * as on the HP-21), and it lifts or not by the digit rules.
         */
        data object Eex : Token

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
        data object Squared : Token
        data object Ln : Token
        data object Exp : Token
        data object Abs : Token
        data object ForceNegative : Token
        data object Sin : Token
        data object Cos : Token
        data object Tan : Token
        data object Asin : Token
        data object Acos : Token
        data object Atan : Token

        /** log base [base] of X; the base arrives from a parsing word. */
        data class LogBase(val base: Double) : Token

        /** [base] raised to X - the antilog. */
        data class AntilogBase(val base: Double) : Token

        // Two-number and two-register operations.
        data object Power : Token
        data object ToRectangular : Token
        data object ToPolar : Token

        // Value producers.
        data object LastX : Token
        data object Pi : Token
        data object EConst : Token
        data object Rcl : Token

        // Storage and modes. STO takes no argument - one register, as on
        // the HP-21. DSP's argument is resolved by the token parser's
        // one-token lookahead, so it arrives here complete; the machine
        // never holds a half-token mode.
        data object Sto : Token
        data class Dsp(val places: Int) : Token

        /** SCI and ENG notation, with their places - NEUTRAL like DSP. */
        data class SciMode(val places: Int) : Token
        data class EngMode(val places: Int) : Token

        /** Angle modes for the trig tokens - NEUTRAL too. */
        data object Degrees : Token
        data object Radians : Token
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
        is Token.Dsp, is Token.SciMode, is Token.EngMode,
        Token.Degrees, Token.Radians -> Disposition.NEUTRAL
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

        // EEX is number entry too - it edits the buffer and never touches
        // the rest of the machine.
        if (token is Token.Eex) {
            pressEex()
            return
        }

        // CHS mid-entry is the one non-digit that EDITS the buffer rather
        // than clearing it: it flips the sign of the number being entered -
        // of the exponent if EEX has opened one, else of the mantissa, as
        // on the HPs - and entry continues as though nothing happened.
        if (token is Token.Chs && buffer.isNotEmpty()) {
            buffer = withSignToggled(buffer)
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

            Token.Squared -> oneNumber { a -> a * a }

            Token.Ln -> oneNumber { a ->
                if (a <= 0.0) return fail() else ln(a)
            }

            Token.Exp -> oneNumber { a -> exp(a) }

            Token.Abs -> oneNumber { a -> abs(a) }
            Token.ForceNegative -> oneNumber { a -> -abs(a) }

            // Trig reads the ANGLE MODE: inputs converted going in,
            // inverse results converted coming out.
            Token.Sin -> oneNumber { a -> sin(toRadians(a)) }
            Token.Cos -> oneNumber { a -> cos(toRadians(a)) }
            Token.Tan -> oneNumber { a -> tan(toRadians(a)) }

            Token.Asin -> oneNumber { a ->
                if (abs(a) > 1.0) return fail() else fromRadians(asin(a))
            }
            Token.Acos -> oneNumber { a ->
                if (abs(a) > 1.0) return fail() else fromRadians(acos(a))
            }
            Token.Atan -> oneNumber { a -> fromRadians(atan(a)) }

            is Token.LogBase -> oneNumber { a ->
                if (a <= 0.0 || token.base <= 0.0 || token.base == 1.0) return fail()
                else ln(a) / ln(token.base)
            }
            is Token.AntilogBase -> oneNumber { a -> token.base.pow(a) }

            Token.Power -> twoNumber { a, b -> a.pow(b) }

            // The pair conversions, HP-21 style: polar holds r in X and
            // theta in Y; rectangular holds x in X and y in Y. Both axes
            // move, nothing drops, LAST X keeps the old X.
            Token.ToRectangular -> {
                val r = x
                val theta = toRadians(y)
                val newX = r * cos(theta)
                val newY = r * sin(theta)
                if (!newX.isFinite() || !newY.isFinite()) return fail()
                lastX = x
                x = newX; y = newY
            }

            Token.ToPolar -> {
                val newX = hypot(x, y)
                val newY = fromRadians(atan2(y, x))
                if (!newX.isFinite() || !newY.isFinite()) return fail()
                lastX = x
                x = newX; y = newY
            }

            Token.LastX -> produce(lastX, wasMidEntry)
            Token.Pi -> produce(Math.PI, wasMidEntry)
            Token.EConst -> produce(Math.E, wasMidEntry)
            Token.Rcl -> produce(storage, wasMidEntry)

            Token.Sto -> storage = x

            is Token.Dsp -> {
                dspMode = NumberFormatter.Mode.FIX
                dspPlaces = token.places
            }
            is Token.SciMode -> {
                dspMode = NumberFormatter.Mode.SCI
                dspPlaces = token.places
            }
            is Token.EngMode -> {
                dspMode = NumberFormatter.Mode.ENG
                dspPlaces = token.places
            }

            Token.Degrees -> angleMode = AngleMode.DEG
            Token.Radians -> angleMode = AngleMode.RAD

            is Token.Digit, Token.Eex -> Unit // handled above; here for exhaustiveness
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

    /**
     * Rules 1 and 2, shared by every number-entry token (digits and EEX) -
     * the only tokens that can lift the stack themselves.
     */
    private fun startEntryIfNeeded() {

        if (!noLift) {
            t = z; z = y; y = x
            buffer = ""
            noLift = true
        }
    }

    private fun pressDigit(character: Char) {

        startEntryIfNeeded()

        // A second radix is refused rather than corrupting the number - the
        // speech layer may well hand one over. So is a radix inside the
        // exponent, which takes whole numbers only.
        if (character == NumberFormatter.RADIX &&
            (buffer.contains(NumberFormatter.RADIX) ||
                buffer.contains(NumberFormatter.EXPONENT_MARKER))
        ) return

        // The HP-55 rule: entry is sized to the display, and once the
        // mantissa's digits fill the field further digits are IGNORED.
        // Only digits count - the radix rides in a gap - and exponent
        // digits have their own field with its own roll below.
        if (character != NumberFormatter.RADIX &&
            !buffer.contains(NumberFormatter.EXPONENT_MARKER) &&
            buffer.count { it.isDigit() } >= entryPositions
        ) return

        // The exponent field holds as many digits as the display can show:
        // one more ROLLS the field left and the oldest digit falls off, as
        // on the HP-21 - so the exponent is always the last digits typed,
        // and entry can never build a value the display must call Overflow.
        val marker = buffer.indexOf(NumberFormatter.EXPONENT_MARKER)
        if (marker >= 0) {
            val digitsStart =
                marker + 1 + if (buffer.getOrNull(marker + 1) == '-') 1 else 0
            val exponentDigits = buffer.substring(digitsStart)
            if (exponentDigits.length >= EXPONENT_ENTRY_DIGITS) {
                buffer = buffer.take(digitsStart) + exponentDigits.drop(1)
            }
        }

        buffer += character
        x = valueOf(buffer)
    }

    /**
     * EEX: open the exponent field. An empty entry gets the implicit
     * mantissa 1 first (EEX 5 is 1e5, as on the HP-21); a second EEX in
     * the same number is refused like a second radix.
     */
    private fun pressEex() {

        startEntryIfNeeded()

        if (buffer.contains(NumberFormatter.EXPONENT_MARKER)) return

        if (buffer.isEmpty()) buffer = "1"
        buffer += NumberFormatter.EXPONENT_MARKER
        x = valueOf(buffer)
    }

    /** Into radians for the trig inputs, per the angle mode. */
    private fun toRadians(angle: Double): Double =
        if (angleMode == AngleMode.DEG) Math.toRadians(angle) else angle

    /** Out of radians for the inverse-trig results, per the angle mode. */
    private fun fromRadians(angle: Double): Double =
        if (angleMode == AngleMode.DEG) Math.toDegrees(angle) else angle

    /**
     * A two-number operation: consumes X and Y, and T replicates downward.
     * A NON-FINITE result is an error, machine untouched - the HP way of
     * treating overflow and domain escapes alike.
     */
    private inline fun twoNumber(operation: (Double, Double) -> Double) {

        val result = operation(y, x)
        if (!result.isFinite()) return fail()

        lastX = x
        x = result
        y = z
        z = t
    }

    /** A one-number operation: replaces X in place. Non-finite fails. */
    private inline fun oneNumber(operation: (Double) -> Double) {

        val result = operation(x)
        if (!result.isFinite()) return fail()

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

    /**
     * CHS mid-entry: the sign that flips is the exponent's when EEX has
     * opened one, the mantissa's otherwise.
     */
    private fun withSignToggled(text: String): String {

        val marker = text.indexOf(NumberFormatter.EXPONENT_MARKER)

        if (marker >= 0) {
            val mantissa = text.take(marker + 1)
            val exponent = text.drop(marker + 1)
            return if (exponent.startsWith("-")) mantissa + exponent.drop(1)
            else mantissa + "-" + exponent
        }

        return if (text.startsWith("-")) text.drop(1) else "-$text"
    }

    /**
     * The buffer as a number: tolerant of "", "-", a leading radix, and an
     * exponent still empty of digits ("5E", "5E-" - exponent zero so far).
     */
    private fun valueOf(text: String): Double {

        val normalised = text
            .replace(NumberFormatter.RADIX, '.')
            .let { if (it == "" || it == "-") it + "0" else it }
            .let { if (it.startsWith(".")) "0$it" else it }
            .let { if (it.startsWith("-.")) "-0" + it.drop(1) else it }
            .let {
                if (it.endsWith(NumberFormatter.EXPONENT_MARKER) ||
                    it.endsWith(NumberFormatter.EXPONENT_MARKER + "-")
                ) it + "0" else it
            }

        return normalised.toDoubleOrNull() ?: 0.0
    }

    companion object {
        const val DEFAULT_DSP_PLACES = 3

        /**
         * Fallback for [entryPositions], matching the segment display's
         * settled field. Real callers wire their own display's constant;
         * only the tests rely on this.
         */
        const val DEFAULT_ENTRY_POSITIONS = 9

        /**
         * Exponent digits the entry field holds before rolling - derived
         * from the display's own limit (99 -> 2 digits), so the two can
         * never disagree.
         */
        private val EXPONENT_ENTRY_DIGITS =
            NumberFormatter.EXPONENT_LIMIT.toString().length
    }
}
