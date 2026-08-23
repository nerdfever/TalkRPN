package com.nerdfever.talkrpn

import com.nerdfever.talkrpn.RpnEngine.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The engine, held against the design notes' rules and traces - and against
 * the rpn83p bug catalogue, which is the list of mistakes every fresh RPN
 * implementation makes.
 */

class RpnEngineTest {

    private val engine = RpnEngine()

    // Feeding helpers: digits one at a time, exactly as the speech layer will.
    private fun digits(text: String) =
        text.forEach { engine.press(Token.Digit(it)) }

    private fun press(vararg tokens: Token) = tokens.forEach { engine.press(it) }

    private fun assertStack(x: Double, y: Double, z: Double = 0.0, t: Double = 0.0) {
        assertEquals("X", x, engine.x, 0.0)
        assertEquals("Y", y, engine.y, 0.0)
        assertEquals("Z", z, engine.z, 0.0)
        assertEquals("T", t, engine.t, 0.0)
    }

    // ---- The notes' own traces -----------------------------------------------

    @Test fun theCanonicalTrace() {
        // 2 ENTER 3 + 4 x = 20, with the intermediate states as noted.
        digits("2")
        press(Token.Enter)
        digits("3")
        assertStack(x = 3.0, y = 2.0)

        press(Token.Add)
        assertEquals(5.0, engine.x, 0.0)

        digits("4")
        assertStack(x = 4.0, y = 5.0)

        press(Token.Multiply)
        assertEquals(20.0, engine.x, 0.0)
    }

    @Test fun tripleEnterFillsTheStack() {
        digits("2")
        press(Token.Enter, Token.Enter, Token.Enter)
        assertStack(x = 2.0, y = 2.0, z = 2.0, t = 2.0)
    }

    @Test fun dspIsNeutral() {
        // 2 ENTER DSP 4 3 x must still multiply by 2.
        digits("2")
        press(Token.Enter, Token.Dsp(4))
        digits("3")
        press(Token.Multiply)
        assertEquals(6.0, engine.x, 0.0)
        assertEquals(4, engine.dspPlaces)
    }

    // ---- Digit entry ------------------------------------------------------------

    @Test fun multiDigitEntryWithRadix() {
        digits("12.34")
        assertEquals(12.34, engine.x, 0.0)
    }

    @Test fun aSecondRadixIsRefused() {
        digits("1.2.3")
        assertEquals(1.23, engine.x, 0.0)
    }

    @Test fun aLeadingRadixMeansZeroPoint() {
        digits(".5")
        assertEquals(0.5, engine.x, 0.0)
    }

    @Test fun entryIsExposedVerbatimWhileTypingAndGoneAfter() {
        // The display shows [entry] while it is non-empty - the HP way.
        digits("12.")
        assertEquals("12.", engine.entry)

        press(Token.Enter)
        assertEquals("", engine.entry)
    }

    @Test fun entryStopsWhenTheFieldIsFull() {
        // The HP-55 rule: the display is the limit. The default field is
        // nine positions, so the tenth and eleventh digits are ignored.
        digits("12345678987")
        assertEquals(123456789.0, engine.x, 0.0)
    }

    @Test fun theRadixDoesNotCountAgainstTheLimit() {
        // Nine digits AND a radix: the radix rides in a gap, so all fit.
        digits("1.23456789")
        assertEquals(1.23456789, engine.x, 0.0)
    }

    @Test fun chsStillWorksWithAFullMantissa() {
        // The limit ignores DIGITS only: sign editing continues to work
        // on a full mantissa, and further digits stay ignored after it.
        digits("123456789")
        press(Token.Chs)
        assertEquals(-123456789.0, engine.x, 0.0)

        digits("5")
        assertEquals(-123456789.0, engine.x, 0.0)
    }

    @Test fun exponentEntryStillWorksWithAFullMantissa() {
        // Exponent digits have their own field; a full mantissa does not
        // block them.
        digits("123456789")
        press(Token.Eex)
        digits("5")
        assertEquals(1.23456789e13, engine.x, 0.0)
    }

    @Test fun entryAfterClearXOverwritesTheZero() {
        digits("5")
        press(Token.ClearX)
        digits("3")
        assertStack(x = 3.0, y = 0.0)
    }

    // ---- CHS: the buffer's one editor ----------------------------------------------

    @Test fun chsMidEntryFlipsTheSignAndEntryContinues() {
        digits("12")
        press(Token.Chs)
        digits("3")
        assertEquals(-123.0, engine.x, 0.0)
    }

    @Test fun chsTwiceMidEntryCancels() {
        digits("7")
        press(Token.Chs, Token.Chs)
        assertEquals(7.0, engine.x, 0.0)
    }

    @Test fun chsOnACompletedNumberNegatesX() {
        digits("5")
        press(Token.Enter, Token.Chs)
        assertEquals(-5.0, engine.x, 0.0)

        // And it is ENABLING: the next digit lifts. (Z holds ENTER's copy.)
        digits("3")
        assertStack(x = 3.0, y = -5.0, z = 5.0)
    }

    // ---- EEX: exponent entry ---------------------------------------------------------

    @Test fun eexAppendsAnExponent() {
        digits("5")
        press(Token.Eex)
        digits("3")
        assertEquals(5000.0, engine.x, 0.0)
    }

    @Test fun eexOnAnEmptyEntrySuppliesTheOne() {
        // EEX 5 means 1e5, as on the HP-21.
        press(Token.Eex)
        digits("5")
        assertEquals(100000.0, engine.x, 0.0)
    }

    @Test fun eexIsNumberEntryAndLifts() {
        // 5 ENTER EEX 3 must lift... no - ENTER disabled the lift, so the
        // implicit 1e3 overwrites ENTER's duplicate, exactly like a digit.
        digits("5")
        press(Token.Enter, Token.Eex)
        digits("3")
        assertStack(x = 1000.0, y = 5.0)

        // Whereas after an operation the entry lifts as rule 1 says.
        press(Token.Add)
        press(Token.Eex)
        digits("2")
        assertStack(x = 100.0, y = 1005.0)
    }

    @Test fun chsAfterEexNegatesTheExponent() {
        digits("5")
        press(Token.Eex)
        digits("3")
        press(Token.Chs)
        assertEquals(0.005, engine.x, 1e-12)

        // And a second CHS puts it back.
        press(Token.Chs)
        assertEquals(5000.0, engine.x, 0.0)
    }

    @Test fun chsBeforeEexStillOwnsTheMantissa() {
        digits("5")
        press(Token.Chs, Token.Eex)
        digits("2")
        assertEquals(-500.0, engine.x, 0.0)
    }

    @Test fun aSecondEexIsRefused() {
        digits("5")
        press(Token.Eex, Token.Eex)
        digits("2")
        assertEquals(500.0, engine.x, 0.0)
    }

    @Test fun aThirdExponentDigitRollsTheField() {
        // 5 EEX 200: the exponent field holds two digits, so 2,0,0 rolls
        // to 00 - the last two typed - exactly as on the HP-21. Entry can
        // never build a value the display must call Overflow.
        digits("5")
        press(Token.Eex)
        digits("200")
        assertEquals("5E00", engine.entry)
        assertEquals(5.0, engine.x, 0.0)
    }

    @Test fun theRollKeepsTheExponentSign() {
        digits("5")
        press(Token.Eex, Token.Chs)
        digits("123")
        assertEquals("5E-23", engine.entry)
        assertEquals(5e-23, engine.x, 0.0)
    }

    @Test fun aRadixInTheExponentIsRefused() {
        digits("5")
        press(Token.Eex)
        digits("1.5")
        assertEquals(5e15, engine.x, 0.0)
    }

    @Test fun eexAloneIsExponentZeroSoFar() {
        digits("5")
        press(Token.Eex)
        assertEquals(5.0, engine.x, 0.0)
    }

    // ---- Undo: the whole machine, remembered -------------------------------------

    @Test fun undoRestoresTheWholeMachine() {
        // Mark, disturb everything a token can reach, undo: identical.
        digits("5")
        press(Token.Enter, Token.Sto)

        engine.mark()
        digits("3")
        press(Token.Add, Token.Dsp(6))
        engine.press(Token.Digit('9'))

        assertTrue(engine.undo())

        assertStack(x = 5.0, y = 5.0)
        assertEquals(5.0, engine.storage, 0.0)
        assertEquals(RpnEngine.DEFAULT_DSP_PLACES, engine.dspPlaces)

        // The noLift bit came back too: STO is disabling, so a digit
        // OVERWRITES rather than lifting - exactly as before the mark.
        digits("7")
        assertStack(x = 7.0, y = 5.0)
    }

    @Test fun undoIsUnlimited() {
        // Three marks, three undo steps, back to the very start.
        for (digit in listOf("1", "2", "3")) {
            engine.mark()
            digits(digit)
            press(Token.Enter)
        }

        assertTrue(engine.undo())
        assertTrue(engine.undo())
        assertTrue(engine.undo())
        assertStack(x = 0.0, y = 0.0)
    }

    @Test fun undoWithNothingToUndoSaysSo() {
        assertFalse(engine.undo())
    }

    @Test fun saveAndLoadRoundTripTheWholeMachine() {
        // History, registers, and a mid-entry buffer all survive the trip.
        engine.mark()
        digits("5")
        press(Token.Enter, Token.Sto, Token.Dsp(6))
        engine.mark()
        digits("12.")

        val slept = engine.saveState()

        val woken = RpnEngine()
        assertTrue(woken.loadState(slept))

        assertEquals("12.", woken.entry)
        assertEquals(5.0, woken.y, 0.0)
        assertEquals(5.0, woken.storage, 0.0)
        assertEquals(6, woken.dspPlaces)

        // The history came along: two undo steps still work.
        assertTrue(woken.undo())
        assertTrue(woken.undo())
        assertEquals(0.0, woken.x, 0.0)
        assertFalse(woken.undo())
    }

    @Test fun aCorruptStoreLeavesTheMachineUntouched() {
        digits("7")
        assertFalse(engine.loadState("not a machine at all"))
        assertEquals(7.0, engine.x, 0.0)
    }

    // ---- The stack's mechanics --------------------------------------------------------

    @Test fun tReplicatesDownward() {
        digits("8"); press(Token.Enter)
        digits("4"); press(Token.Enter)
        digits("2"); press(Token.Enter)
        digits("1")
        press(Token.Add)
        // T=8 replicated into Z as the stack dropped.
        assertStack(x = 3.0, y = 4.0, z = 8.0, t = 8.0)
    }

    @Test fun rollDown() {
        digits("1"); press(Token.Enter)
        digits("2"); press(Token.Enter)
        digits("3"); press(Token.Enter)
        digits("4")
        press(Token.RollDown)
        assertStack(x = 3.0, y = 2.0, z = 1.0, t = 4.0)
    }

    @Test fun rollUpUndoesRollDown() {
        digits("1"); press(Token.Enter)
        digits("2"); press(Token.Enter)
        digits("3"); press(Token.Enter)
        digits("4")
        press(Token.RollDown, Token.RollUp)
        assertStack(x = 4.0, y = 3.0, z = 2.0, t = 1.0)
    }

    @Test fun swapXY() {
        digits("2"); press(Token.Enter)
        digits("7")
        press(Token.SwapXY)
        assertStack(x = 2.0, y = 7.0)
    }

    @Test fun stackOpsAreEnabling() {
        // The rpn83p bug: a digit after a swap must lift, not overwrite.
        digits("2"); press(Token.Enter)
        digits("7")
        press(Token.SwapXY)
        digits("5")
        assertStack(x = 5.0, y = 2.0, z = 7.0)
    }

    // ---- LAST X ------------------------------------------------------------------------

    @Test fun lastXHoldsTheConsumedValue() {
        digits("2"); press(Token.Enter)
        digits("3"); press(Token.Add)
        assertEquals(3.0, engine.lastX, 0.0)
    }

    @Test fun lastXRecallsWithALift() {
        digits("2"); press(Token.Enter)
        digits("3"); press(Token.Add)
        press(Token.LastX)
        assertStack(x = 3.0, y = 5.0)
        press(Token.Add)
        assertEquals(8.0, engine.x, 0.0)
    }

    @Test fun clearStackPreservesLastX() {
        // Straight from the rpn83p catalogue.
        digits("2"); press(Token.Enter)
        digits("3"); press(Token.Add)
        press(Token.ClearStack)
        assertStack(x = 0.0, y = 0.0)
        assertEquals(3.0, engine.lastX, 0.0)
    }

    // ---- Producers consult the bit ---------------------------------------------------------

    @Test fun rclLiftsWhenLiftIsEnabled() {
        // The user's own acceptance trace: 5 ENTER RCL + ... well, without
        // the ENTER - mid-entry RCL must lift the 5 too. Both forms here.
        digits("3"); press(Token.Sto)
        digits("5")
        press(Token.Rcl, Token.Add)
        assertEquals(8.0, engine.x, 0.0)
    }

    @Test fun fiveEnterRclAdd() {
        digits("3"); press(Token.Sto)
        press(Token.ClearStack)
        digits("5")
        press(Token.Enter, Token.Rcl, Token.Add)
        assertEquals(8.0, engine.x, 0.0)
    }

    @Test fun rclOverwritesAfterEnter() {
        // The zero-argument-producer bug: after ENTER the recalled value
        // must land ON X, not push it - which is also what makes the
        // fiveEnterRclAdd trace give 8 rather than 10.
        digits("3"); press(Token.Sto)
        digits("2"); press(Token.Enter)
        press(Token.Rcl, Token.Multiply)
        assertEquals(6.0, engine.x, 0.0)
    }

    @Test fun chainedProducersLiftEachOther() {
        digits("3"); press(Token.Sto)
        press(Token.ClearStack)
        press(Token.Rcl, Token.Pi)
        assertStack(x = Math.PI, y = 3.0)
        press(Token.Add)
        assertEquals(3.0 + Math.PI, engine.x, 1e-12)
    }

    @Test fun piLiftsAndThenEnablesEntry() {
        digits("2"); press(Token.Enter)
        press(Token.Pi)
        assertEquals(Math.PI, engine.x, 0.0)
        assertEquals(2.0, engine.y, 0.0)
    }

    // ---- STO: the open question, pinned to the table as written ------------------------------

    @Test fun stoStoresWithoutMovingTheStack() {
        digits("2.55")
        press(Token.Sto)
        assertEquals(2.55, engine.storage, 0.0)
        assertEquals(2.55, engine.x, 0.0)
        assertEquals(0.0, engine.y, 0.0)
    }

    @Test fun stoIsDisablingPerTheTable() {
        // The HP-35 reading the notes settled on: digits after STO start a
        // fresh number IN PLACE of X. (The 41/42S disagree; if the Free42
        // oracle rules for them, flip STO's row in dispositionOf and this
        // test's expectation together.)
        digits("3"); press(Token.Sto)
        digits("4")
        assertStack(x = 4.0, y = 0.0)
    }

    // ---- One-number operations ----------------------------------------------------------------

    @Test fun sqrtReplacesXInPlace() {
        digits("2"); press(Token.Enter)
        digits("9"); press(Token.Sqrt)
        assertStack(x = 3.0, y = 2.0)
        assertEquals(9.0, engine.lastX, 0.0)
    }

    @Test fun reciprocal() {
        digits("4"); press(Token.Reciprocal)
        assertEquals(0.25, engine.x, 0.0)
    }

    // ---- Errors leave the machine exactly as it stood --------------------------------------------

    @Test fun divideByZeroRaisesAndDisturbsNothing() {
        digits("1"); press(Token.Enter)
        digits("3"); press(Token.Add) // lastX becomes 3
        press(Token.Enter)
        digits("0")
        press(Token.Divide)
        assertTrue(engine.error)
        assertStack(x = 0.0, y = 4.0)
        assertEquals(3.0, engine.lastX, 0.0)
    }

    @Test fun sqrtOfNegativeRaises() {
        digits("4"); press(Token.Chs, Token.Sqrt)
        assertTrue(engine.error)
        assertEquals(-4.0, engine.x, 0.0)
    }

    @Test fun theNextTokenClearsTheError() {
        digits("1"); press(Token.Enter)
        digits("0"); press(Token.Divide)
        assertTrue(engine.error)
        press(Token.ClearX)
        assertFalse(engine.error)
    }

    // ---- ENTER's exactness --------------------------------------------------------------------------

    @Test fun enterThenDigitOverwrites() {
        digits("2"); press(Token.Enter)
        digits("3")
        assertStack(x = 3.0, y = 2.0)
    }

    @Test fun twoIndependentNumbersNeedNoEnterAfterAnOperation() {
        digits("2"); press(Token.Enter)
        digits("3"); press(Token.Add)
        digits("4")
        // The 4 lifted the 5; no ENTER in between.
        assertStack(x = 4.0, y = 5.0)
    }
}
