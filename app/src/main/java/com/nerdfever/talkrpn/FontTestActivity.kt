package com.nerdfever.talkrpn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import kotlin.math.floor

/*
 * The font, on the watch, at whatever size and weight you want to judge it at.
 *
 * Two views:
 *
 *   segments   - one cell, stepping through the 30 elements one at a time so a
 *                mislabelled or misplaced one is caught before ninety-odd glyphs
 *                have been drawn against it.
 *   characters - the whole glyph table, as many per page as the chosen size
 *                allows, each with its character underneath. A malformed glyph
 *                is often only recognisable once you know what it was meant to be.
 *
 * Tap the screen to show or hide the controls, so the font can be judged without
 * buttons over it.
 */

// ---------------------------------------------------------------------------
// Tweakables.
//
// Cell heights are millimetres, because "how big does it need to be" is a
// physical question and dp is not a physical unit. Everything else is in the
// font's own cell widths. Anything in device pixels carries "Px" in its name.
// ---------------------------------------------------------------------------

/** Cell heights offered, smallest first. Cap height, not including the descender. */
private val CELL_HEIGHTS_MM = listOf(1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 5.0f, 6.0f, 8.0f, 10.0f)

/** Which one to start on, named by its value so reordering the list cannot break it. */
private const val INITIAL_CELL_HEIGHT_MM = 3.0f
private val INITIAL_SIZE_INDEX = CELL_HEIGHTS_MM.indexOf(INITIAL_CELL_HEIGHT_MM)

/**
 * Stroke widths offered, as multiples of the font's own value, so the control
 * brackets the measurement rather than a list of absolute figures that would
 * stop meaning anything the moment the measurement changed.
 */
private val STROKE_MULTIPLES = listOf(0.5f, 0.65f, 0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f)
private val STROKE_CHOICES = STROKE_MULTIPLES.map { it * TalkRpnFont.STROKE }

/** Start on the font's own stroke - found by value, not by a hand-counted index. */
private val INITIAL_STROKE_INDEX = STROKE_MULTIPLES.indexOf(1.0f)

/**
 * The lit-segment colour: the display's reddest red.
 *
 * See DisplayTestActivity for why - briefly, the real emitters peak at 655-660 nm,
 * which is outside every display gamut, and clipping to maximum saturation is the
 * closest reachable colour by a factor of two in dE2000.
 */

/** Unlit segments, drawn faintly in step mode so the cell keeps its shape. */

/**
 * The cell boundary, for diagnosing where a glyph's ink sits inside its cell.
 *
 * Cyan rather than a shade of red so it cannot be mistaken for a segment.
 */

/** Boundary line width, in cell widths, so it scales with the glyph. */
private const val BOUNDS_STROKE = 0.02052f

private val CONTROL_BORDER = Color(0xFF5A5A5A)

/**
 * How much of the screen one cell may use, in segment view.
 *
 * Both axes are needed. Sizing from width alone overflowed: a cell is nearly
 * twice as tall as it is wide once the descender is counted, so 62% of the width
 * came to more than the whole screen height and the descender ran behind the
 * controls.
 */
private const val SEGMENT_WIDTH_FRACTION = 0.62f
private const val SEGMENT_HEIGHT_FRACTION = 0.70f

/**
 * How much of the screen the character grid may use.
 *
 * A glyph box is nearly twice as tall as it is wide because of the descender, so
 * sizing the grid from its width overflows: three rows came to about 540 px on a
 * 396 px screen and the top and bottom rows were cut off. Height binds here.
 */
private const val GRID_HEIGHT_FRACTION = 0.74f
private const val GRID_WIDTH_FRACTION = 0.94f

/** Space between glyph boxes, and under each for its character. */
private val GRID_GAP = 6.dp
private val GRID_LABEL_HEIGHT = 13.dp

private val GAP_MEDIUM = 8.dp
private val SIDE_MARGIN = 8.dp
private val TEXT_GLYPH_LABEL = 10.sp

// ---- The control overlay -------------------------------------------------
//
// Halved from where it started. Four rows of buttons plus a caption came to
// about 130 dp, which is a third of a 396 px screen and sat over the very thing
// this screen exists to show. These are deliberately small and fiddly to hit:
// it is an instrument, and the font matters more than the buttons do.

/**
 * Overlay scrim behind the controls: 20% opaque black, so the glyphs stay
 * readable straight through the panel.
 */
private val CONTROL_PANEL_BACKGROUND = Color(0x33000000)

private val CONTROL_GAP = 2.dp
private val CONTROL_PANEL_PAD_TOP = 3.dp

/**
 * Clearance under the last row of controls.
 *
 * More than the top, because the screen is round: a panel flush with the bottom
 * of the bounding box has its last row sliced by the curve.
 */
private val CONTROL_PANEL_PAD_BOTTOM = 16.dp
private val CONTROL_CORNER = 3.dp
private val CONTROL_PAD_V = 1.dp

private val TEXT_READOUT = 9.sp
private val TEXT_BUTTON = 8.sp

/** Millimetres per inch. */

class FontTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Judging a font needs the screen to stay up while it is looked at.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AppScaffold {
                FontTestScreen()
            }
        }
    }
}

@Composable
private fun FontTestScreen() {

    var charMode by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showGhost by remember { mutableStateOf(true) }
    var showBounds by remember { mutableStateOf(true) }

    // -1 means "all segments"; 0 and up index a single one.
    var stepIndex by remember { mutableStateOf(-1) }
    var page by remember { mutableStateOf(0) }

    var strokeIndex by remember { mutableStateOf(INITIAL_STROKE_INDEX) }
    var sizeIndex by remember { mutableStateOf(INITIAL_SIZE_INDEX) }

    val stroke = STROKE_CHOICES[strokeIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LedPalette.BACKGROUND)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {

        if (charMode) {
            CharacterGrid(page, sizeIndex, stroke, showBounds)
        } else {
            SegmentView(stepIndex, stroke, showGhost, showBounds)
        }

        if (!showControls) return@Box

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CONTROL_PANEL_BACKGROUND)
                .padding(
                    start = SIDE_MARGIN,
                    end = SIDE_MARGIN,
                    top = CONTROL_PANEL_PAD_TOP,
                    bottom = CONTROL_PANEL_PAD_BOTTOM
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ---- What we are looking at ------------------------------------

            val caption =
                if (charMode) {
                    "%.1f mm   stroke %.2f".format(CELL_HEIGHTS_MM[sizeIndex], stroke)
                } else {
                    val segments = TalkRpnFont.Seg.entries
                    if (stepIndex < 0) "all ${segments.size}   stroke %.2f".format(stroke)
                    else "${segments[stepIndex].name}  ${stepIndex + 1}/${segments.size}"
                }

            Text(
                text = caption,
                color = LedPalette.LABEL,
                fontSize = TEXT_READOUT,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(CONTROL_GAP))

            // ---- Size, only where it means anything ------------------------
            //
            // The segment view sizes one cell to the screen, so there is nothing
            // for a size control to do there.

            if (charMode) {

                ControlPair(
                    leftLabel = "size -",
                    rightLabel = "size +",
                    onLeft = { if (sizeIndex > 0) { sizeIndex--; page = 0 } },
                    onRight = { if (sizeIndex < CELL_HEIGHTS_MM.lastIndex) { sizeIndex++; page = 0 } }
                )

                Spacer(Modifier.height(CONTROL_GAP))
            }

            // ---- Stroke ----------------------------------------------------

            ControlPair(
                leftLabel = "stroke -",
                rightLabel = "stroke +",
                onLeft = { if (strokeIndex > 0) strokeIndex-- },
                onRight = { if (strokeIndex < STROKE_CHOICES.lastIndex) strokeIndex++ }
            )

            Spacer(Modifier.height(CONTROL_GAP))

            CompactControl(
                if (showBounds) "bounds on" else "bounds off",
                Modifier.fillMaxWidth()
            ) { showBounds = !showBounds }

            Spacer(Modifier.height(CONTROL_GAP))

            // ---- Paging, which means different things in the two views -----

            if (charMode) {
                ControlPair(
                    leftLabel = "prev",
                    rightLabel = "next",
                    onLeft = { page-- },
                    onRight = { page++ }
                )
            } else {
                val last = TalkRpnFont.Seg.entries.lastIndex
                ControlPair(
                    leftLabel = "prev",
                    rightLabel = "next",
                    onLeft = { stepIndex = if (stepIndex <= -1) last else stepIndex - 1 },
                    onRight = { stepIndex = if (stepIndex >= last) -1 else stepIndex + 1 }
                )
            }

            Spacer(Modifier.height(CONTROL_GAP))

            // ---- Switching views -------------------------------------------

            if (charMode) {
                CompactControl("segments", Modifier.fillMaxWidth()) { charMode = false }
            } else {
                ControlPair(
                    leftLabel = if (showGhost) "ghost on" else "ghost off",
                    rightLabel = "characters",
                    onLeft = { showGhost = !showGhost },
                    onRight = { charMode = true }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Segment view.
// ---------------------------------------------------------------------------

@Composable
private fun SegmentView(stepIndex: Int, stroke: Float, showGhost: Boolean, showBounds: Boolean) {

    val segments = TalkRpnFont.Seg.entries

    val mask =
        if (stepIndex < 0) TalkRpnFont.ALL_SEGMENTS
        else segments[stepIndex].bit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SIDE_MARGIN),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        var boxWidthPx by remember { mutableStateOf(0) }

        val density = LocalDensity.current.density
        val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels.toFloat()

        // Sized so the slanted ink, plus a stroke either side, fits the allowed
        // fraction of the screen - on whichever axis binds first.
        val inkUnits = TalkRpnFont.SHEARED_WIDTH + stroke
        val tallUnits = TalkRpnFont.TOTAL_HEIGHT + stroke

        val fromWidth =
            if (boxWidthPx > 0) boxWidthPx * SEGMENT_WIDTH_FRACTION / inkUnits * TalkRpnFont.CELL_HEIGHT
            else 0f

        val fromHeight =
            screenHeightPx * SEGMENT_HEIGHT_FRACTION / tallUnits * TalkRpnFont.CELL_HEIGHT

        val cellHeightPx = minOf(fromWidth, fromHeight)

        val boxHeightPx = cellHeightPx / TalkRpnFont.CELL_HEIGHT * tallUnits

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((boxHeightPx / density).dp)
                // Captured during layout, not draw: writing state from the draw
                // phase schedules another draw, which writes it again.
                .onSizeChanged { boxWidthPx = it.width }
        ) {
            if (cellHeightPx <= 0f) return@Canvas

            val scale = cellHeightPx / TalkRpnFont.CELL_HEIGHT
            val inkWidthPx = inkUnits * scale
            val originX = (size.width - inkWidthPx) / 2f + stroke / 2f * scale
            val originY = stroke / 2f * scale

            if (showBounds) drawCellBounds(Offset(originX, originY), cellHeightPx)

            with(TalkRpnFont) {

                // Everything faint first, so a single lit segment is seen in the
                // context of the whole cell rather than floating in the dark.
                if (stepIndex >= 0 && showGhost) {
                    drawTalkRpnCell(
                        TalkRpnFont.ALL_SEGMENTS, Offset(originX, originY),
                        cellHeightPx, LedPalette.GHOST, stroke
                    )
                }

                drawTalkRpnCell(mask, Offset(originX, originY), cellHeightPx, LedPalette.LIT, stroke)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Character view.
// ---------------------------------------------------------------------------

/**
 * The glyph table at a chosen physical size, as many per page as will fit.
 *
 * The grid is not a fixed number of columns and rows: the size control decides
 * how big a glyph is, and the grid takes as many as the screen holds. That way
 * "make it bigger" never silently crops the bottom row.
 */
@Composable
private fun CharacterGrid(page: Int, sizeIndex: Int, stroke: Float, showBounds: Boolean) {

    val context = LocalContext.current
    val density = LocalDensity.current

    val metrics = context.resources.displayMetrics
    val pixelsPerMm = metrics.pixelsPerMm()

    val cellHeightPx = CELL_HEIGHTS_MM[sizeIndex] * pixelsPerMm
    val scale = cellHeightPx / TalkRpnFont.CELL_HEIGHT

    // What one glyph occupies, ink plus the stroke that overhangs it.
    val glyphWidthPx = (TalkRpnFont.SHEARED_WIDTH + stroke) * scale
    val glyphHeightPx = (TalkRpnFont.TOTAL_HEIGHT + stroke) * scale

    val gapPx = with(density) { GRID_GAP.toPx() }
    val labelPx = with(density) { GRID_LABEL_HEIGHT.toPx() }

    val availableWidthPx = metrics.widthPixels * GRID_WIDTH_FRACTION
    val availableHeightPx = metrics.heightPixels * GRID_HEIGHT_FRACTION

    val columns = floor(availableWidthPx / (glyphWidthPx + gapPx)).toInt().coerceAtLeast(1)
    val rows = floor(availableHeightPx / (glyphHeightPx + labelPx + gapPx)).toInt().coerceAtLeast(1)

    val characters = TalkRpnGlyphs.CHARACTERS
    val perPage = columns * rows
    val pageCount = (characters.size + perPage - 1) / perPage

    // Wrap rather than clamp, so "next" past the end returns to the start.
    val safePage = ((page % pageCount) + pageCount) % pageCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SIDE_MARGIN),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        for (row in 0 until rows) {

            Row(horizontalArrangement = Arrangement.Center) {

                for (col in 0 until columns) {

                    val index = safePage * perPage + row * columns + col

                    Box(
                        modifier = Modifier
                            .width((glyphWidthPx / density.density).dp)
                            .padding(horizontal = GRID_GAP / 2)
                    ) {
                        if (index < characters.size) {
                            GlyphCell(characters[index], cellHeightPx, stroke, showBounds)
                        }
                    }
                }
            }

            Spacer(Modifier.height(GRID_GAP))
        }
    }
}

/** One glyph, with the character it is meant to be printed underneath it. */
@Composable
private fun GlyphCell(ch: Char, cellHeightPx: Float, stroke: Float, showBounds: Boolean) {

    val mask = TalkRpnGlyphs.maskFor(ch) ?: 0L
    val density = LocalDensity.current.density

    val scale = cellHeightPx / TalkRpnFont.CELL_HEIGHT
    val glyphHeightPx = (TalkRpnFont.TOTAL_HEIGHT + stroke) * scale

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((glyphHeightPx / density).dp)
        ) {
            val inkWidthPx = (TalkRpnFont.SHEARED_WIDTH + stroke) * scale

            val glyphOrigin = Offset(
                (size.width - inkWidthPx) / 2f + stroke / 2f * scale,
                stroke / 2f * scale
            )

            if (showBounds) drawCellBounds(glyphOrigin, cellHeightPx)

            with(TalkRpnFont) {
                drawTalkRpnCell(
                    mask = mask,
                    origin = glyphOrigin,
                    cellHeight = cellHeightPx,
                    color = LedPalette.LIT,
                    strokeWidth = stroke
                )
            }
        }

        Text(
            text = if (ch == ' ') "sp" else ch.toString(),
            color = LedPalette.LABEL,
            fontSize = TEXT_GLYPH_LABEL
        )
    }
}

/**
 * The cell's own bounds, drawn behind a glyph.
 *
 * A parallelogram because the slant leans the whole cell. Ink overhangs it by
 * half a stroke on every side - that is expected, and seeing by how much is the
 * point of drawing it. The inner line is the baseline; the descender hangs
 * below it.
 */
private fun DrawScope.drawCellBounds(origin: Offset, cellHeightPx: Float) {

    val scale = cellHeightPx / TalkRpnFont.CELL_HEIGHT

    withTransform({
        translate(origin.x, origin.y)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawPath(TalkRpnFont.CELL_OUTLINE, LedPalette.CELL_BOUNDS, style = Stroke(width = BOUNDS_STROKE))
        drawPath(TalkRpnFont.CELL_BASELINE, LedPalette.CELL_BOUNDS, style = Stroke(width = BOUNDS_STROKE))
    }
}

// ---------------------------------------------------------------------------
// Controls.
// ---------------------------------------------------------------------------

/** Two controls side by side, which is every control on this screen. */
@Composable
private fun ControlPair(
    leftLabel: String,
    rightLabel: String,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {

        CompactControl(leftLabel, Modifier.weight(1f), onLeft)

        // width, not height: inside a Row it is the horizontal axis that needs
        // the gap.
        Spacer(Modifier.width(CONTROL_GAP))

        CompactControl(rightLabel, Modifier.weight(1f), onRight)
    }
}

/**
 * A control small enough to leave the font visible.
 *
 * Wear's own Button is built for a finger on a watch face and is roughly a
 * quarter of the screen; several of them buried the thing this screen exists to
 * show. These are harder to hit, which is the right trade for an instrument.
 */
@Composable
private fun CompactControl(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .border(1.dp, CONTROL_BORDER, RoundedCornerShape(CONTROL_CORNER))
            .clickable(onClick = onClick)
            .padding(vertical = CONTROL_PAD_V),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = LedPalette.LABEL, fontSize = TEXT_BUTTON)
    }
}






