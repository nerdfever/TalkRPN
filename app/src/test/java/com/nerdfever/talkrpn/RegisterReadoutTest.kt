package com.nerdfever.talkrpn

import com.nerdfever.talkrpn.RpnEngine.Token
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * The display rules RegisterReadout owns - above all the HP-55 habit:
 * a mantissa under entry always shows its decimal point.
 */

class RegisterReadoutTest {

    private val engine = RpnEngine()
    private val field = NumberFormatter.FieldShape(9, punctuationCostsCell = false)

    private fun digits(text: String) =
        text.forEach { engine.press(Token.Digit(it)) }

    private fun display() = RegisterReadout.displayText(engine, field)

    @Test fun entryShowsThePointAndTheCursor() {
        // The HP-55 point and the HP-42S cursor, together: "5" reads
        // "5._", and the cursor rides the end as digits accumulate.
        digits("5")
        assertEquals("5._", display())

        digits("2")
        assertEquals("52._", display())
    }

    @Test fun aTypedRadixIsNotDoubled() {
        digits("5.2")
        assertEquals("5.2_", display())
    }

    @Test fun thePointBelongsToTheMantissaNotTheExponent() {
        digits("5")
        engine.press(Token.Eex)
        digits("3")
        assertEquals("5.E3_", display())
    }

    @Test fun outsideEntryTheFormatterOwnsTheDisplay() {
        digits("5")
        engine.press(Token.Enter)
        assertEquals("5.000", display())
    }
}
