package com.nerdfever.talkrpn

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/*
 * NumberFormatter - the FINAL formatter. DESIGN.md carries the spec; the
 * display test screen's dsp() sketch now just delegates here.
 *
 * Three modes and one dsp setting - digits right of the radix:
 *
 *   FIX  no exponent; falls back to the overflow mode (default ENG) when the
 *        rounded value cannot be honestly shown - the digits overflow the
 *        field, or every shown place would read zero.
 *   SCI  always an exponent; mantissa in [1, 10).
 *   ENG  SCI with the exponent a multiple of three; mantissa in [1, 1000).
 *
 * Everything here is a pure function of (value, mode, dsp, field) - no state
 * and no Android, so the whole thing unit-tests on the JVM.
 *
 * The output is a plain string in the display grammar the renderer already
 * speaks: digits, the radix, group separators, and an exponent introduced by
 * [EXPONENT_MARKER] - which never reaches the screen; the renderer splits on
 * it and lays the exponent into its fixed block.
 */

object NumberFormatter {

    // ---- The display grammar ---------------------------------------------------
    //
    // Public, because the renderer parses what this file emits - one owner,
    // so the two can never disagree. Which character separates and which is
    // the radix swaps with the radix-comma user setting one day; both live
    // here for the same reason.

    const val RADIX = '.'
    const val GROUP_SEPARATOR = ','
    const val GROUP_SIZE = 3
    const val GROUP_DIGITS = true

    /**
     * Show the radix even when no digits follow it, so an integer reads "5."
     * not "5".
     *
     * This is what HP calculators do, and it is not decoration. A trailing
     * point signals that you are looking at the whole value rather than the
     * leading digits of something truncated to fit - which matters on a
     * display this narrow, where truncation is routine. It also tells a
     * displayed number from a label or an error word at a glance.
     */
    const val ALWAYS_SHOW_RADIX = true

    /** Introduces the exponent in the emitted string. Never displayed. */
    const val EXPONENT_MARKER = 'E'

    // ---- The out-of-range faces --------------------------------------------------
    //
    // PROVISIONAL wording, awaiting the real choice. Overflow gets a word
    // rather than the classic HP's flashing nines; underflow quietly shows
    // zero, which is also the HP way.

    const val OVERFLOW_TEXT = "Overflow"
    const val NAN_TEXT = "NaN"
    const val INFINITY_TEXT = "Inf"

    /**
     * Exponents stay TWO digits, classic HP: any result whose exponent would
     * need three overflows (or underflows to zero) instead. Doubles reach
     * 10^+-308, so this is a real ceiling, not a formality. Public because
     * the engine sizes its exponent ENTRY field from it, so the two can
     * never disagree.
     */
    const val EXPONENT_LIMIT = 99

    /** The exponent block's positions: the blank-or-minus seat, two digits. */
    private const val EXPONENT_BLOCK_POSITIONS = 3

    enum class Mode { FIX, SCI, ENG }

    /**
     * What the formatter must know about the field it formats for.
     *
     * [positions] - how many cells the field holds (the fp knob's value).
     *
     * [punctuationCostsCell] - whether the radix and the group separators
     * occupy cells of their own: true for the fixed-pitch dot font, false
     * for the segment font, whose punctuation lives in the gaps.
     */
    class FieldShape(
        val positions: Int,
        val punctuationCostsCell: Boolean,
    )

    /**
     * [value] rendered for [field] in [mode] at [dsp] places.
     *
     * [dsp] is a CEILING, not a promise: when the field cannot afford that
     * many places - small fp, or the dot font paying for its radix - the
     * places clip down rather than the value overflowing. At the default
     * field sizes the two never differ.
     */
    fun format(
        value: Double,
        mode: Mode,
        dsp: Int,
        field: FieldShape,
        overflowMode: Mode = Mode.ENG,
    ): String {

        // The non-numbers wear words instead.
        if (value.isNaN()) return NAN_TEXT
        if (value.isInfinite()) return if (value > 0) INFINITY_TEXT else "-$INFINITY_TEXT"

        return when (mode) {
            Mode.FIX -> fix(value, dsp, field, overflowMode)
            Mode.SCI -> exponential(value, dsp, field, engineering = false)
            Mode.ENG -> exponential(value, dsp, field, engineering = true)
        }
    }

    // ---- FIX ---------------------------------------------------------------------

    private fun fix(value: Double, dsp: Int, field: FieldShape, overflowMode: Mode): String {

        // Zero is always honest in fixed form. (-0.0 == 0.0, so the sign of
        // a negative zero never reaches the screen.)
        if (value == 0.0) return grouped(plain(0.0, dsp))

        val text = grouped(plain(value, dsp))

        // Too small: the ROUNDED text shows no live digit at all, though the
        // value is not zero. Judged on the text rather than on a magnitude
        // threshold so that rounding is already accounted for.
        val allZero = text.none { it in '1'..'9' }
        if (allZero) return exponential(value, dsp, field, overflowMode == Mode.ENG)

        // Too big: the digits (and, in the dot font, the punctuation)
        // overflow the field.
        if (positionsOf(text, field) > field.positions) {
            return exponential(value, dsp, field, overflowMode == Mode.ENG)
        }

        return text
    }

    // ---- SCI and ENG ---------------------------------------------------------------

    private fun exponential(
        value: Double,
        dsp: Int,
        field: FieldShape,
        engineering: Boolean,
    ): String {

        val mantissaLimit = if (engineering) 1000.0 else 10.0
        val exponentStep = if (engineering) 3 else 1

        if (value == 0.0) return zeroExponential(dsp, field)

        // Normalise: exponent down to the mode's grid, mantissa into range.
        var exponent = floor(log10(abs(value))).toInt()
        if (engineering) exponent = Math.floorDiv(exponent, 3) * 3
        var mantissa = value / 10.0.pow(exponent)

        // Format, then renormalise if rounding pushed the mantissa out of
        // range (999.97 at one place becomes 1000.0) - a single step, since
        // the carried mantissa is exactly the range's floor.
        var text = plain(mantissa, mantissaPlaces(mantissa, dsp, field))

        if (integerDigitsOf(text) > integerDigitsOf(plain(mantissaLimit - 1, 0))) {
            exponent += exponentStep
            mantissa /= mantissaLimit
            text = plain(mantissa, mantissaPlaces(mantissa, dsp, field))
        }

        // The two-digit ceiling: past it, a word upward and a zero downward.
        if (exponent > EXPONENT_LIMIT) return OVERFLOW_TEXT
        if (exponent < -EXPONENT_LIMIT) return zeroExponential(dsp, field)

        val block =
            if (exponent < 0) "-%02d".format(-exponent) else "%02d".format(exponent)

        return text + EXPONENT_MARKER + block
    }

    /** Zero in exponential form: zero mantissa, zero exponent, the HP way. */
    private fun zeroExponential(dsp: Int, field: FieldShape): String =
        plain(0.0, mantissaPlaces(0.0, dsp, field)) + EXPONENT_MARKER + "00"

    /**
     * How many places the mantissa gets: [dsp], clipped to what its share of
     * the field affords after its sign, its integer digits, and - in the dot
     * font - the radix's own cell.
     */
    private fun mantissaPlaces(mantissa: Double, dsp: Int, field: FieldShape): Int {

        val integerDigits = maxOf(floor(log10(abs(mantissa))).toInt() + 1, 1)
        val sign = if (mantissa < 0) 1 else 0
        val radixCell = if (field.punctuationCostsCell) 1 else 0

        val share = field.positions - EXPONENT_BLOCK_POSITIONS

        return minOf(dsp, share - sign - integerDigits - radixCell).coerceAtLeast(0)
    }

    // ---- The grammar's mechanics ---------------------------------------------------

    /**
     * [value] at [places], radix policy applied, no grouping yet. Locale is
     * pinned to ROOT: a device set to a comma-radix locale must not change
     * what the engine emits - the radix is OUR setting, applied here.
     */
    private fun plain(value: Double, places: Int): String {

        var text = String.format(Locale.ROOT, "%.${places}f", value)

        // "-0.000" would show a sign with no live digit to own it.
        if (text.none { it in '1'..'9' }) text = text.removePrefix("-")

        if (RADIX != '.') text = text.replace('.', RADIX)

        if (ALWAYS_SHOW_RADIX && !text.contains(RADIX)) text += RADIX

        return text
    }

    /** Group separators into the integer part, per the grammar's settings. */
    private fun grouped(text: String): String {

        if (!GROUP_DIGITS) return text

        val radixAt = text.indexOf(RADIX)
        val head = if (radixAt >= 0) text.take(radixAt) else text
        val tail = if (radixAt >= 0) text.substring(radixAt) else ""

        val sign = if (head.startsWith("-")) "-" else ""
        val digits = head.removePrefix("-")

        val groupedDigits = digits.reversed()
            .chunked(GROUP_SIZE)
            .joinToString(GROUP_SEPARATOR.toString())
            .reversed()

        return sign + groupedDigits + tail
    }

    /**
     * What [text] costs the field: signs and digits always occupy positions;
     * the radix and the separators do too when the font charges for them.
     */
    private fun positionsOf(text: String, field: FieldShape): Int {

        val signsAndDigits = text.count { it.isDigit() || it == '-' }

        if (!field.punctuationCostsCell) return signsAndDigits

        return signsAndDigits + text.count { it == RADIX || it == GROUP_SEPARATOR }
    }

    /** Digits left of the radix, sign aside - for the renormalise check. */
    private fun integerDigitsOf(text: String): Int =
        text.removePrefix("-").takeWhile { it.isDigit() }.length
}
