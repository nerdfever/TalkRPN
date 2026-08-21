package com.nerdfever.talkrpn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/*
 * The display-tuning knobs and their on-screen panel, shared by the
 * display-test rig and the calculator itself - so the settled geometry can
 * be nudged while looking at either fixed samples or a live calculation,
 * with ONE panel implementation to keep honest.
 *
 * [DisplayKnobs] holds the adjustable state, every knob starting from the
 * settled default it tunes; [DisplayTuningPanel] is the translucent panel
 * of controls, which callers overlay on a tap. Each button carries its own
 * value, named as the code names it - the reading IS the value to copy
 * back into the source when a tuning session settles something.
 */

// ---------------------------------------------------------------------------
// Tweakables - the knobs' steps and ranges.
//
// Lengths are in the font's cell widths unless the name says otherwise.
// Anything in device pixels carries "Px" in its name.
// ---------------------------------------------------------------------------

private const val HEIGHT_FRACTION_MIN = 0.03f
private const val HEIGHT_FRACTION_MAX = 0.25f

/**
 * The gap knob's floor is physical, not chosen: at a gap of one stroke the
 * neighbouring ink touches, so there is nothing below it worth showing.
 */
private val GAP_UNITS_MIN = TalkRpnFont.STROKE
private const val GAP_UNITS_MAX = 3.0f

/**
 * The g knob's step and range when the DOT font is live, in blank columns -
 * that font's own gap unit. Half a column per press: coarse enough to tune
 * on the wrist, where a fingertip press is the whole instrument.
 * Zero means adjacent cells - the lattices touch, ink one dot apart.
 */
private const val DOT_GAP_STEP_COLUMNS = 0.5f
private const val DOT_GAP_COLUMNS_MIN = 0f
private const val DOT_GAP_COLUMNS_MAX = 8f

/**
 * The vgap control's range, deliberately far past both touching points: ink
 * meets ink at one stroke, like the horizontal gap, and on a measuring
 * instrument seeing the overlap is more useful than being protected from it.
 * The floor puts every baseline of equal-size neighbours in the same place
 * (vpitch exactly zero).
 */
private val VGAP_UNITS_MIN = -TalkRpnFont.TOTAL_HEIGHT
private const val VGAP_UNITS_MAX = 2.0f

/**
 * The gap and vgap knobs step by this, additively - they are lengths, not
 * proportions. Sized for tuning ON THE WATCH, where pressing precisely is
 * hard, so each press should visibly earn its effort. Kept to a multiple
 * of 0.001 so the three-decimal button readouts stay exact.
 */
private const val SPACING_STEP_UNITS = 0.012f

/** Every proportional adjustment (hf) moves by this much per press. */
private const val ADJUST_STEP_FRACTION = 0.025f

/**
 * The floor on how little a press may move the layout, in pixels. At these
 * sizes a cell unit is a few pixels, so without a floor a step could sit far
 * below the display's resolution and spend many clicks crossing one pixel.
 *
 * A QUARTER pixel, by choice: for final tuning, resolution matters more than
 * every single press being visible, and the readout on the button moves every
 * press even when the pixels have not caught up yet.
 *
 * Units are the right thing to STORE - they transfer to the watch, where a pixel
 * figure tuned on the emulator would not - but the wrong thing to step by blindly.
 * So the step is whichever is larger: the nominal step above, or this floor's
 * worth of units.
 */
private const val MIN_STEP_PX = 0.25f

/**
 * The fp knob's range: the floor is the exponent's digits, its possible
 * minus, and one mantissa position; the ceiling is past anything the glass
 * could hold at a readable size.
 */
private const val FIELD_POSITIONS_MIN = EXPONENT_DIGITS + 2
private const val FIELD_POSITIONS_MAX = 20

/**
 * The slant knob, in degrees, one per press. Zero is upright; the ceiling
 * is far past anything that still reads as a digit rather than italics
 * falling over.
 */
private const val SLANT_STEP_DEGREES = 1.0f
private const val SLANT_DEGREES_MIN = 0f
private const val SLANT_DEGREES_MAX = 15f

/**
 * The stroke knob, in cell widths like every other length. The settled
 * 0.1475 was measured off an HP-55 photograph; the range runs from
 * hairline to almost touching neighbours within a glyph.
 */
private const val STROKE_STEP_UNITS = 0.005f
private const val STROKE_UNITS_MIN = 0.04f
private const val STROKE_UNITS_MAX = 0.40f

private val TEXT_BUTTON = 11.sp
private val PANEL_GAP_SMALL = 4.dp
private val PANEL_GAP_MEDIUM = 8.dp

private val CONTROL_BORDER = Color(0xFF5A5A5A)
private val CONTROL_CORNER = 4.dp

/**
 * Panel width, as a fraction of the screen. Wide enough that the readouts
 * are comfortable to read and the halves comfortable to hit; the backing
 * is translucent, so the display still shows through.
 */
private const val CONTROL_PANEL_WIDTH_FRACTION = 0.7f

/**
 * Panel backing. Translucent so the whole display stays readable underneath -
 * the point of the panel being small is being able to see past it. Alpha 0x70 is
 * 44% opaque.
 */
private val CONTROL_PANEL_BACKGROUND = Color(0x70000000)
private val CONTROL_PAD_V = 3.dp
private val CONTROL_PAD_H = 4.dp

/** Inset for the control panel, enough to keep it inside the glass at its height. */
private val CONTROL_PANEL_MARGIN = 5.dp

/**
 * The adjustable display state, every knob starting from the settled
 * default it tunes. One instance per screen, remembered across
 * recompositions; the readings feed [CalculatorDisplay]'s parameters.
 */
class DisplayKnobs {

    var gapUnits by mutableStateOf(TalkRpnFont.DEFAULT_GAP)
    var heightFraction by mutableStateOf(DEFAULT_HEIGHT_FRACTION)
    var vgapUnits by mutableStateOf(TalkRpnFont.VGAP)

    // One fp per font, like the g knob: each font remembers its own tuning
    // and the knob binds to whichever is live.
    var segmentFieldPositions by mutableStateOf(SEGMENT_FIELD_POSITIONS)
    var dotFieldPositions by mutableStateOf(DOT_FIELD_POSITIONS)

    // Which font draws the registers: the segment font, or the HDLS-1414
    // dot matrix.
    var useDotFont by mutableStateOf(false)

    // The dot font's own gap, in blank columns - the g knob binds to this
    // while the dot font is live, and to gapUnits otherwise.
    var dotGapColumns by mutableStateOf(Hdls1414Font.CHARACTER_GAP_COLUMNS)

    // The cyan field boxes: measuring aid. The rig turns this on at start;
    // the calculator leaves it off until asked.
    var showFieldBoxes by mutableStateOf(false)

    // The glyphs' lean, in degrees, and the stroke, in cell widths - both
    // settled in the font (SLANT_DEGREES, STROKE) and adjustable here so a
    // later eye can disagree without a rebuild.
    var slantDegrees by mutableStateOf(TalkRpnFont.SLANT_DEGREES)
    var strokeUnits by mutableStateOf(TalkRpnFont.STROKE)

    /** The live font's field size - what every consumer means by fp. */
    val fieldPositions: Int
        get() = if (useDotFont) dotFieldPositions else segmentFieldPositions
}

/**
 * The tuning panel: g/hf, vg/fp, font/boxes, plus whatever [extraControls]
 * a caller appends (the rig adds its sample cycler). The caller decides
 * when it shows and where it sits - typically centred, toggled by a tap on
 * the display.
 */
@Composable
fun DisplayTuningPanel(
    knobs: DisplayKnobs,
    modifier: Modifier = Modifier,
    extraControls: @Composable ColumnScope.() -> Unit = {},
) {
    val metrics = LocalContext.current.resources.displayMetrics
    val screenPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()

    // One press must move the layout at least the pixel floor; the unit is
    // X's cell width in pixels, same as the display's own conversion.
    val unitPx = knobs.heightFraction * screenPx / TalkRpnFont.CELL_HEIGHT
    val spacingStepUnits = maxOf(SPACING_STEP_UNITS, MIN_STEP_PX / unitPx)

    Column(
        modifier = modifier
            // Two controls fit side by side, and the display stays visible
            // around it.
            .fillMaxWidth(CONTROL_PANEL_WIDTH_FRACTION)
            .background(CONTROL_PANEL_BACKGROUND, RoundedCornerShape(CONTROL_CORNER))
            // Swallow taps that land on the panel but miss a button.
            // Without this they fall through to the root and dismiss the
            // panel, so a run of taps alternates between hitting buttons
            // and hitting the display - which made the controls look like
            // they were moving values at random.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            .padding(horizontal = CONTROL_PANEL_MARGIN, vertical = PANEL_GAP_MEDIUM),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(modifier = Modifier.fillMaxWidth()) {

            // One knob, two tweakables: g binds to whichever font is
            // live - the segment font's DEFAULT_GAP in cell widths, or
            // the dot font's CHARACTER_GAP_COLUMNS in blank columns.
            // The value shown is always the live one.
            SplitButton(
                if (knobs.useDotFont) "g %.2f".format(knobs.dotGapColumns)
                else "g %.3f".format(knobs.gapUnits),
                Modifier.weight(1f),
                onIncrease = {
                    if (knobs.useDotFont) knobs.dotGapColumns =
                        (knobs.dotGapColumns + DOT_GAP_STEP_COLUMNS)
                            .coerceAtMost(DOT_GAP_COLUMNS_MAX)
                    else knobs.gapUnits = (knobs.gapUnits + spacingStepUnits)
                        .coerceAtMost(GAP_UNITS_MAX)
                },
                onDecrease = {
                    if (knobs.useDotFont) knobs.dotGapColumns =
                        (knobs.dotGapColumns - DOT_GAP_STEP_COLUMNS)
                            .coerceAtLeast(DOT_GAP_COLUMNS_MIN)
                    else knobs.gapUnits = (knobs.gapUnits - spacingStepUnits)
                        .coerceAtLeast(GAP_UNITS_MIN)
                }
            )

            // width, not height: inside a Row it is the horizontal axis
            // that needs the gap.
            Spacer(Modifier.width(PANEL_GAP_SMALL))

            // hf tunes heightFraction, the one knob that is not font
            // geometry: how large the whole display renders, as X's cap
            // height in fractions of the screen diameter.
            SplitButton("hf %.3f".format(knobs.heightFraction), Modifier.weight(1f),
                onIncrease = {
                    knobs.heightFraction =
                        (knobs.heightFraction * (1f + ADJUST_STEP_FRACTION))
                            .coerceAtMost(HEIGHT_FRACTION_MAX)
                },
                onDecrease = {
                    knobs.heightFraction =
                        (knobs.heightFraction / (1f + ADJUST_STEP_FRACTION))
                            .coerceAtLeast(HEIGHT_FRACTION_MIN)
                }
            )
        }

        Spacer(Modifier.height(PANEL_GAP_SMALL))

        Row(modifier = Modifier.fillMaxWidth()) {

            SplitButton("vg %.3f".format(knobs.vgapUnits), Modifier.weight(1f),
                onIncrease = {
                    knobs.vgapUnits = (knobs.vgapUnits + spacingStepUnits)
                        .coerceAtMost(VGAP_UNITS_MAX)
                },
                onDecrease = {
                    knobs.vgapUnits = (knobs.vgapUnits - spacingStepUnits)
                        .coerceAtLeast(VGAP_UNITS_MIN)
                }
            )

            Spacer(Modifier.width(PANEL_GAP_SMALL))

            // fp tunes the LIVE font's field size - how many of its
            // cells each register's field holds. Whole cells only.
            SplitButton("fp %d".format(knobs.fieldPositions), Modifier.weight(1f),
                onIncrease = {
                    if (knobs.useDotFont) knobs.dotFieldPositions =
                        (knobs.dotFieldPositions + 1).coerceAtMost(FIELD_POSITIONS_MAX)
                    else knobs.segmentFieldPositions =
                        (knobs.segmentFieldPositions + 1).coerceAtMost(FIELD_POSITIONS_MAX)
                },
                onDecrease = {
                    if (knobs.useDotFont) knobs.dotFieldPositions =
                        (knobs.dotFieldPositions - 1).coerceAtLeast(FIELD_POSITIONS_MIN)
                    else knobs.segmentFieldPositions =
                        (knobs.segmentFieldPositions - 1).coerceAtLeast(FIELD_POSITIONS_MIN)
                }
            )
        }

        Spacer(Modifier.height(PANEL_GAP_SMALL))

        Row(modifier = Modifier.fillMaxWidth()) {

            // sl tunes the glyphs' lean, in degrees - the font's
            // SLANT_DEGREES.
            SplitButton("sl %.1f".format(knobs.slantDegrees), Modifier.weight(1f),
                onIncrease = {
                    knobs.slantDegrees = (knobs.slantDegrees + SLANT_STEP_DEGREES)
                        .coerceAtMost(SLANT_DEGREES_MAX)
                },
                onDecrease = {
                    knobs.slantDegrees = (knobs.slantDegrees - SLANT_STEP_DEGREES)
                        .coerceAtLeast(SLANT_DEGREES_MIN)
                }
            )

            Spacer(Modifier.width(PANEL_GAP_SMALL))

            // st tunes the stroke, in cell widths - the font's STROKE.
            // Four decimals: the settled value is 0.1475, and the readout
            // must show exactly what would be copied back.
            SplitButton("st %.4f".format(knobs.strokeUnits), Modifier.weight(1f),
                onIncrease = {
                    knobs.strokeUnits = (knobs.strokeUnits + STROKE_STEP_UNITS)
                        .coerceAtMost(STROKE_UNITS_MAX)
                },
                onDecrease = {
                    knobs.strokeUnits = (knobs.strokeUnits - STROKE_STEP_UNITS)
                        .coerceAtLeast(STROKE_UNITS_MIN)
                }
            )
        }

        Spacer(Modifier.height(PANEL_GAP_SMALL))

        Row(modifier = Modifier.fillMaxWidth()) {

            // Named for the font on screen now, not the one a tap brings.
            CompactButton(
                if (knobs.useDotFont) "font: dot" else "font: seg", Modifier.weight(1f)
            ) {
                knobs.useDotFont = !knobs.useDotFont
            }

            Spacer(Modifier.width(PANEL_GAP_SMALL))

            // Likewise: named for what is showing.
            CompactButton(
                if (knobs.showFieldBoxes) "boxes: on" else "boxes: off", Modifier.weight(1f)
            ) {
                knobs.showFieldBoxes = !knobs.showFieldBoxes
            }
        }

        extraControls()
    }
}

/** The panel's row spacing, for callers appending [DisplayTuningPanel]'s extras. */
val PANEL_ROW_GAP = PANEL_GAP_SMALL

/**
 * A control small enough to leave the display visible.
 *
 * Wear's own Button is built for a finger on a watch face and is roughly a quarter
 * of the screen; four of them buried the layout the panel overlays. These are
 * harder to hit, which is the right trade for a measuring instrument.
 */
@Composable
fun CompactButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .border(1.dp, CONTROL_BORDER, RoundedCornerShape(CONTROL_CORNER))
            .clickable(onClick = onClick)
            .padding(vertical = CONTROL_PAD_V),
        contentAlignment = Alignment.Center
    ) {
        // One line, always - a label that wraps inside a button this small
        // reads as two buttons.
        Text(label, color = LedPalette.LABEL, fontSize = TEXT_BUTTON, maxLines = 1, softWrap = false)
    }
}

/**
 * One control, two halves: press the LEFT to decrease, the RIGHT to
 * increase - minus on the left and plus on the right, like a number line.
 *
 * The centred label carries no click handler of its own, so taps on it fall through
 * to whichever half is underneath.
 */
@Composable
private fun SplitButton(
    label: String,
    modifier: Modifier = Modifier,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Box(
        modifier = modifier
            .border(1.dp, CONTROL_BORDER, RoundedCornerShape(CONTROL_CORNER))
    ) {

        Row(modifier = Modifier.fillMaxWidth()) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onDecrease)
                    .padding(horizontal = CONTROL_PAD_H, vertical = CONTROL_PAD_V),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("-", color = LedPalette.LABEL, fontSize = TEXT_BUTTON)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onIncrease)
                    .padding(horizontal = CONTROL_PAD_H, vertical = CONTROL_PAD_V),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("+", color = LedPalette.LABEL, fontSize = TEXT_BUTTON)
            }
        }

        Text(
            text = label,
            color = LedPalette.LABEL,
            fontSize = TEXT_BUTTON,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
        )
    }
}
