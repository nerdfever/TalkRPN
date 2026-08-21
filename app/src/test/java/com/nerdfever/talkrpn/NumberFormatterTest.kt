package com.nerdfever.talkrpn

import com.nerdfever.talkrpn.NumberFormatter.FieldShape
import com.nerdfever.talkrpn.NumberFormatter.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * The final formatter, held against its spec (DESIGN.md, "The number
 * formatter"). Field shapes match the display test screen's defaults:
 * segment fp 9 with free punctuation, dot fp 10 paying a cell for it.
 */

class NumberFormatterTest {

    // The two real fields, and a deliberately cramped one for the clipping cases.
    private val segment = FieldShape(9, punctuationCostsCell = false)
    private val dot = FieldShape(10, punctuationCostsCell = true)
    private val cramped = FieldShape(7, punctuationCostsCell = false)

    private fun fix(value: Double, dsp: Int = 3, field: FieldShape = segment) =
        NumberFormatter.format(value, Mode.FIX, dsp, field)

    private fun sci(value: Double, dsp: Int = 3, field: FieldShape = segment) =
        NumberFormatter.format(value, Mode.SCI, dsp, field)

    private fun eng(value: Double, dsp: Int = 3, field: FieldShape = segment) =
        NumberFormatter.format(value, Mode.ENG, dsp, field)

    // ---- FIX: the plain cases ------------------------------------------------

    @Test fun fixZero() = assertEquals("0.000", fix(0.0))

    @Test fun fixNegativeZeroLosesItsSign() = assertEquals("0.000", fix(-0.0))

    @Test fun fixRounds() = assertEquals("3.142", fix(3.1415927))

    @Test fun fixInteger() = assertEquals("12.000", fix(12.0))

    @Test fun fixKeepsTheLeadingZero() = assertEquals("0.500", fix(0.5))

    @Test fun fixNegative() = assertEquals("-0.500", fix(-0.5))

    @Test fun fixGroupsThousands() = assertEquals("12,345.678", fix(12345.678))

    @Test fun fixAtDspZeroKeepsTheRadix() = assertEquals("42.", fix(42.0, dsp = 0))

    // ---- FIX: rounding changes the digit count ---------------------------------

    @Test fun fixCarryGrowsADigit() = assertEquals("10.0", fix(9.96, dsp = 1))

    // ---- FIX: the fallbacks ------------------------------------------------------

    @Test fun fixTooBigFallsToEng() =
        assertEquals("602.000E21", fix(6.02e23))

    @Test fun fixTooSmallFallsToEng() =
        assertEquals("400.000E-06", fix(0.0004))

    @Test fun fixCarryCanTriggerTheFallback() =
        // 999.96 rounds to 1000.0, five positions in a four-position field.
        assertEquals("1.E03", fix(999.96, dsp = 1, field = FieldShape(4, false)))

    // ---- The fonts disagree about punctuation ------------------------------------

    @Test fun segmentFieldHoldsNineDigitsFree() =
        assertEquals("123,456.789", fix(123456.789))

    @Test fun dotFieldChargesForPunctuationAndOverflows() =
        assertEquals("123.457E03", fix(123456.789, field = dot))

    // ---- SCI ---------------------------------------------------------------------

    @Test fun sciBasic() = assertEquals("6.020E23", sci(6.02e23))

    @Test fun sciNegativeExponent() = assertEquals("5.000E-12", sci(5e-12))

    @Test fun sciZero() = assertEquals("0.000E00", sci(0.0))

    @Test fun sciNegativeMantissa() = assertEquals("-5.000E-12", sci(-5e-12))

    @Test fun sciRenormalisesAfterCarry() =
        // 9.9999 at three places rounds to 10.000: one power up.
        assertEquals("1.000E01", sci(9.9999))

    @Test fun sciClipsPlacesToTheField() =
        // dsp 5 asks for more than a seven-position field affords.
        assertEquals("1.235E12", sci(1.23456789e12, dsp = 5, field = cramped))

    // ---- ENG ---------------------------------------------------------------------

    @Test fun engExponentIsAMultipleOfThree() =
        assertEquals("12.346E-87", eng(123.456789e-88))

    @Test fun engExactPowerStaysPut() = assertEquals("1.235E90", eng(123.456789e88))

    @Test fun engRenormalisesAfterCarry() =
        // 999.97 at one place rounds to 1000.0: three powers up.
        assertEquals("1.0E03", eng(999.97, dsp = 1))

    @Test fun engDotFieldPaysForItsRadix() =
        assertEquals("123.457E03", eng(123456.789, field = dot))

    // ---- The edges of the world -----------------------------------------------------

    @Test fun overflowGetsAWord() = assertEquals("Overflow", sci(1e120))

    @Test fun underflowShowsZero() = assertEquals("0.000E00", sci(1e-120))

    @Test fun fixOverflowsThroughTheFallbackToo() = assertEquals("Overflow", fix(1e120))

    @Test fun nanGetsItsName() =
        assertEquals("NaN", NumberFormatter.format(Double.NaN, Mode.FIX, 3, segment))

    @Test fun infinityGetsItsName() =
        assertEquals("Inf", NumberFormatter.format(Double.POSITIVE_INFINITY, Mode.FIX, 3, segment))

    @Test fun negativeInfinityKeepsItsSign() =
        assertEquals("-Inf", NumberFormatter.format(Double.NEGATIVE_INFINITY, Mode.FIX, 3, segment))

    // ---- The overflow mode is configurable --------------------------------------------

    @Test fun fixCanFallBackToSciInstead() =
        assertEquals("6.020E23", NumberFormatter.format(6.02e23, Mode.FIX, 3, segment, Mode.SCI))
}
