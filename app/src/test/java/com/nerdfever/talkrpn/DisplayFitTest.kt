package com.nerdfever.talkrpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/*
 * The round-glass rescue shift, held to its promises: a fitting layout is
 * left alone, a rescuable one is rescued completely, and a hopeless
 * element does not stop the others being saved.
 */

class DisplayFitTest {

    // The watch's screen, and a search matching the display's own calls.
    private val diameter = 454f
    private val maxShift = diameter * 0.15f
    private val step = 2f

    private fun shiftFor(rects: List<FitRect>) =
        unclipShift(rects, diameter, maxShift, step)

    /** True when every corner of [rect], shifted, lies inside the circle. */
    private fun fits(rect: FitRect, shift: FitShift): Boolean {

        val radius = diameter / 2f

        for (x in listOf(rect.left, rect.right)) {
            for (y in listOf(rect.top, rect.bottom)) {
                val cx = x + shift.dx - radius
                val cy = y + shift.dy - radius
                if (sqrt(cx * cx + cy * cy) > radius) return false
            }
        }

        return true
    }

    @Test fun aFittingLayoutIsLeftExactlyAlone() {
        // Comfortably inside: no shift, not even a small one.
        val rects = listOf(FitRect(150f, 150f, 300f, 300f))
        assertEquals(FitShift(0f, 0f), shiftFor(rects))
    }

    @Test fun theTLabelCaseIsRescued() {
        // The live case: a label off the glass at the top-left, and a wide
        // X row near the diameter constraining how far anything may move.
        val label = FitRect(62f, 48f, 73f, 69f)
        val xRow = FitRect(11f, 197f, 442f, 257f)

        val shift = shiftFor(listOf(label, xRow))

        assertTrue("label rescued", fits(label, shift))
        assertTrue("X row kept", fits(xRow, shift))
    }

    @Test fun aHopelessElementDoesNotStopTheRescue() {
        // Wider than the glass: nothing fits it. The rescuable label must
        // still come inside.
        val hopeless = FitRect(-40f, 200f, diameter + 40f, 260f)
        val label = FitRect(62f, 48f, 73f, 69f)

        val shift = shiftFor(listOf(hopeless, label))

        assertTrue("label rescued regardless", fits(label, shift))
    }

    @Test fun nothingToFitMeansNoShift() {
        assertEquals(FitShift(0f, 0f), shiftFor(emptyList()))
    }
}
