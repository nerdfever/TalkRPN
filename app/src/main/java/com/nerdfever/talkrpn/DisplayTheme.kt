package com.nerdfever.talkrpn

import android.util.DisplayMetrics
import androidx.compose.ui.graphics.Color

/*
 * What every screen that draws the LED display shares.
 *
 * Here rather than repeated per screen because a colour declared three times is
 * a colour that will eventually be three different colours - which had already
 * happened to LABEL before this file existed.
 */

/** Millimetres per inch. */
private const val MM_PER_INCH = 25.4f

/**
 * Device pixels per millimetre, from what the panel reports about itself.
 *
 * `xdpi` is approximate on some devices, which is why readouts derived from it
 * show only one decimal place.
 */
fun DisplayMetrics.pixelsPerMm(): Float = xdpi / MM_PER_INCH

object LedPalette {

    /**
     * The lit-segment colour: the display's reddest red, and deliberately so.
     *
     * These parts are GaAsP and their emitters peak at 655-660 nm, which is
     * x = 0.728, y = 0.272 in CIE 1931 - outside EVERY display gamut. sRGB's red
     * primary falls short by 0.088 in x, Display P3 by 0.048, even Rec.2020 by
     * 0.020.
     *
     * So there is no exact match and the question is which reachable colour is
     * closest. Clipping to maximum saturation gives this, at dE2000 7.8;
     * desaturating along the constant-dominant-wavelength line gives 0xFFFF0052,
     * at 17.0. The second is not wrong about the wavelength - it is genuinely
     * 655 nm at 63% purity - but that line leaves the gamut through the MAGENTA
     * edge, so it lands on pink.
     *
     * Do not sample this from a photograph. Camera filters overlap, the red
     * channel clips almost at once on a lit segment, and the highlight rolls into
     * whatever green and blue were picking up - which is why photographs of these
     * displays show a white-pink core. The eye does not do that: its cones
     * overlap too, but with no hard clip and far more dynamic range, so a bright
     * segment stays saturated red. Matching a photograph reproduces the camera's
     * failure.
     *
     * Free improvement available: this watch's OLED covers P3, so rendering in a
     * wide-gamut space would move the red primary from x = 0.64 to x = 0.68.
     */
    val LIT = Color(0xFFFF0000)

    /** Behind everything. Black costs no power on OLED. */
    val BACKGROUND = Color(0xFF000000)

    /** Unlit segments, drawn faintly so a cell keeps its shape while stepping. */
    val GHOST = Color(0xFF2A0A08)

    /** Labels and annunciator text - dimmer than the digits, and not red. */
    val LABEL = Color(0xFF8A8A8A)

    /**
     * The cell boundary, for diagnosing where a glyph's ink sits in its cell.
     *
     * Cyan rather than a shade of red so it cannot be mistaken for a segment.
     */
    val CELL_BOUNDS = Color(0xFF3D8B96)

    /** Outlines on controls and annunciator boxes. */
    val BORDER = Color(0xFF5A5A5A)
}
