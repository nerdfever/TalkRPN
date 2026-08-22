package com.nerdfever.talkrpn

import android.os.Build
import android.util.DisplayMetrics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

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
     * The lit-segment colour: the dot font's neon orange, adopted for the
     * whole display - chosen by eye on the watch, where it reads brighter
     * than the LED red it replaced. ONE source: [Hdls1414Font.NEON_ORANGE]
     * owns the value and documents its derivation, so retuning it there
     * moves both fonts together. (The red era and its GaAsP colour
     * science are in HISTORY.md.)
     */
    val LIT = Hdls1414Font.NEON_ORANGE

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

    /** A register's whole field box - the same diagnostic cyan, one size up. */
    val FIELD_BOUNDS = CELL_BOUNDS

    /** Outlines on controls and annunciator boxes. */
    val BORDER = Color(0xFF5A5A5A)

    /** The ring marking where the round glass ends. */
    val GLASS_EDGE = Color(0xFF5A5A5A)
}

/**
 * Whether this is an emulator rather than a watch.
 *
 * The stock heuristic; there is no honest API for it. Both halves matter -
 * the Wear images report a "generic" fingerprint and an "sdk_gwear" product.
 */
val IS_EMULATOR: Boolean =
    Build.FINGERPRINT.contains("generic") || Build.PRODUCT.contains("sdk")

/** Ring line width. Thin, so it reads as a boundary rather than content. */
private val GLASS_EDGE_RING_WIDTH = 1.dp

/**
 * Marks where the round glass ends. Overlay it LAST, so it sits above content.
 *
 * The emulator window is square and shows the whole framebuffer, so without
 * this there is no way to see what a round watch cuts off - a layout can look
 * fine on the emulator and lose a digit on the wrist. Every screen shows it on
 * the emulator, per standing instruction.
 *
 * The ring is drawn just INSIDE the circle, because the round-screen mask
 * swallows anything painted outside it - on the EMULATOR as well as the
 * watch, which a pixel scan confirmed after an attempt to draw it outside
 * rendered nothing anywhere (HISTORY.md). Inside is the only place it can
 * exist - which also means the watch shows it, so screens that reach the
 * wrist must use [GlassEdgeIfEmulator], never this directly.
 */
@Composable
fun GlassEdge() {
    Canvas(modifier = Modifier.fillMaxSize()) {

        val radius = minOf(size.width, size.height) / 2f

        drawCircle(
            color = LedPalette.GLASS_EDGE,
            radius = radius - GLASS_EDGE_RING_WIDTH.toPx() / 2f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = GLASS_EDGE_RING_WIDTH.toPx()),
        )
    }
}

/** [GlassEdge] on the emulator only - for screens that are not measuring tools. */
@Composable
fun GlassEdgeIfEmulator() {
    if (IS_EMULATOR) GlassEdge()
}
