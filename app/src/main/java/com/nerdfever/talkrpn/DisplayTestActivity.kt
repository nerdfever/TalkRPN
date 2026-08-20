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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/*
 * The whole calculator display, laid out for fit.
 *
 * Every register visible at once, per the sketch: T, Z, Y above X, then LASTX and
 * STO below it, with two annunciator regions at the bottom. X is the widest row and
 * sits across the middle of the screen, where a round display is at its widest. The
 * other registers carry the same number of cells at a smaller size, which makes them
 * narrower too - conveniently, since they sit on the narrower chords.
 *
 * This is a measuring instrument. It answers: does this layout fit a 40 mm watch at
 * a size anyone can read?
 *
 * Tap anywhere to show or hide the controls, so the layout can be judged without
 * buttons cluttering it.
 *
 * The register labels are drawn in the system font, not the HP-01 font, because the
 * HP-01 font has seven segments and therefore no letters at all. That is precisely
 * the gap that a 14- or 16-segment cell would close.
 */

// ---------------------------------------------------------------------------
// Tweakables.
//
// Lengths are in the font's cell widths unless the name says otherwise.
// Anything in device pixels carries "Px" in its name. Mixing the two is the
// whole risk in this file, so each is named.
// ---------------------------------------------------------------------------

/**
 * How much smaller the other registers are than X, as a fraction of its cell height.
 *
 * Not on a button: the screen has room for two adjustments per row, and gap and
 * height are the two that matter. Change it here.
 */
private const val SMALL_ROW_SCALE = 0.70f

/**
 * Digit height, as a fraction of the screen's diameter.
 *
 * A screen fraction rather than millimetres deliberately: the tuning happens on
 * an emulator that thinks it is a physically larger watch, so a value in
 * millimetres dialled in there would not carry across - a fraction of the screen
 * does, and lands on the real watch looking the same.
 *
 * The default is settled by eye. It replaced an earlier derive-to-fit rule
 * (the tallest digits whose full field spans the widest chord), which made
 * sense while the field size was fixed; with fp on a knob, the height is its
 * own decision.
 */
private const val INITIAL_HEIGHT_FRACTION = 0.092f
private const val HEIGHT_FRACTION_MIN = 0.03f
private const val HEIGHT_FRACTION_MAX = 0.25f

/**
 * THE UNIT for the gap and the vgap below: segment E/F to segment B/C - the
 * cell width - is 1.
 *
 * TalkRpnFont's header defines it; this is the same unit, used horizontally and
 * vertically alike, so a gap and a vgap are directly comparable numbers. There
 * is no conversion between this screen and the font - both are in cell widths.
 *
 * GAP - from the LAST lit centreline of one glyph to the FIRST of the next, in
 * cell widths. Both ends are centrelines, so the visible dark band between the
 * ink is one stroke narrower than this.
 *
 * Spacing is PROPORTIONAL, so this is the only horizontal control there is: each
 * glyph takes the width of its own ink and every gap is this wide. Two full-width
 * glyphs sit 1 + gap apart; the narrow ones (1, i, l, most lower case) come in
 * closer than that.
 *
 * Starts at the font's own default so the two cannot drift apart. The range
 * brackets it generously - this screen exists to find the value.
 *
 * Independent of the hf control: this moves spacing only. Sizing the cell to
 * make a fixed cell count fill the row would let a tighter gap buy taller digits,
 * which is convenient and means neither control does one thing.
 */
private val INITIAL_GAP_UNITS = TalkRpnFont.DEFAULT_GAP

/**
 * The floor is physical, not chosen: at a gap of one stroke the neighbouring ink
 * touches, so there is nothing below it worth showing.
 */
private val GAP_UNITS_MIN = TalkRpnFont.STROKE
private const val GAP_UNITS_MAX = 3.0f

/**
 * The g knob's step and range when the DOT font is live, in blank columns -
 * that font's own gap unit. A quarter of a column per press: fine enough to
 * tune with, and at register sizes roughly a pixel, so every press shows.
 * Zero means adjacent cells - the lattices touch, ink one dot apart.
 */
private const val DOT_GAP_STEP_COLUMNS = 0.25f
private const val DOT_GAP_COLUMNS_MIN = 0f
private const val DOT_GAP_COLUMNS_MAX = 8f

/**
 * VGAP - the vertical space between rows, in the same units. The knob mirrors
 * the font's tweakable of the same name: one row's descender-bar centreline
 * down to the next row's cap centreline, negative meaning the bands interleave.
 *
 * The screen spaces rows baseline to baseline from it, per seam: each row
 * contributes its own half - descender plus half a vgap from the row above,
 * half a vgap plus cap height from the row below - each half at its own row's
 * scale. Between equal rows that is exactly the font's derived VPITCH =
 * TOTAL_HEIGHT + VGAP; between unequal rows the smaller row brings its
 * smaller units, so spacing scales with the rows it separates. See
 * seamPitchPx in [DisplayTestScreen].
 *
 * Baseline to baseline rather than a uniform gap, because a uniform GAP
 * between unequal rows puts the baselines at unequal distances, and the
 * baselines are what the eye reads.
 */
private val INITIAL_VGAP_UNITS = TalkRpnFont.VGAP

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
 * proportions. Deliberately fine: the knobs are now used for FINAL tuning, so
 * resolution beats per-press visibility. Kept to a multiple of 0.001 so the
 * three-decimal button readouts stay exact.
 */
private const val SPACING_STEP_UNITS = 0.006f

/** Every proportional adjustment (hf) moves by this much per press. */
private const val ADJUST_STEP_FRACTION = 0.0125f

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

/** The annunciator box outline. */
private val ANNUNCIATOR_BORDER = Color(0xFF4A4A4A)

/**
 * One sample set: the name the sample button shows while it is up, whether its
 * rows grow to fill the field, and what each register shows.
 *
 * fillsRow: the realistic and lowercase sets are fixed text, because their
 * point is to look like something; the fill sets exist to fill the field, so
 * they follow it.
 */
private class SampleSet(val name: String, val fillsRow: Boolean, val values: Map<String, String>)

/**
 * The all-eights set is the one that matters for legibility: every segment lit
 * is the worst case, because adjacent digits then have the least dark space
 * between them.
 */
private val SAMPLE_SETS = listOf(
    // Something a real calculation would look like, formatted at the DSP default.
    SampleSet(
        "calc", fillsRow = false,
        mapOf(
            "T" to 0.0,
            "Z" to 12.0,
            "Y" to 1.4142136,
            "X" to 3.1415927,
            "LASTX" to 2.7182818,
            "STO" to 6.02e23,
        ).mapValues { (_, v) -> dsp(v) }
    ),
    // Worst case: every segment lit, every cell full.
    SampleSet(
        "eights", fillsRow = true,
        mapOf(
            "T" to "8",
            "Z" to "8",
            "Y" to "8",
            "X" to "8",
            "LASTX" to "8",
            "STO" to "8",
        )
    ),
    // Every digit, so none hides behind another.
    SampleSet(
        "digits", fillsRow = true,
        mapOf(
            "T" to "1234567890",
            "Z" to "1234567890",
            "Y" to "1234567890",
            "X" to "1234567890",
            "LASTX" to "1234567890",
            "STO" to "1234567890",
        )
    ),
    // Lower case, heavy on descenders, so g j p q y can be judged - especially
    // whether one row's tails collide with the row beneath at the current
    // vgap, which sits below the descender-clearance point by design.
    SampleSet(
        "lower", fillsRow = false,
        mapOf(
            "T" to "jaggy pyjamas",
            "Z" to "happy pygmy jog",
            "Y" to "Syntax error",
            "X" to "quick jazzy pig",
            "LASTX" to "grumpy dog",
            "STO" to "type gyp quay",
        )
    ),
)

/**
 * THE FIELD: every register shows its value in this many digit positions.
 *
 * A position is one full-width cell plus one gap. The field's right edge goes as
 * far right as the glass allows; the mantissa is left-justified from position 1,
 * and an exponent occupies just its own characters at the right end - see
 * [EXPONENT_DIGITS] for the zero-width blank.
 *
 * This is the fp knob's DEFAULT - the field width is being fitted by eye, and
 * the initial height derivation below also sizes against it.
 */
private const val FIELD_POSITIONS = 9

/**
 * The exponent's digits, at the field's end with no marker, ONE reserved
 * blank position ahead of them (plus a third position for the minus when
 * negative).
 *
 * The block is placed BY POSITION - its ink starts at its first position's
 * boundary - not right-justified by measured ink. Ink-justification made the
 * separation depend on which digits the exponent held: a 1 is zero-width in
 * the segment font, so "21" left more dark than "24" would, and a full
 * mantissa against "24" closed to less than the ordinary inter-digit gap.
 * Position placement makes the dark span constant: one blank cell and its
 * two gaps, in whichever font is live.
 */
private const val EXPONENT_DIGITS = 2

/**
 * The fp knob's range: the floor is the exponent's digits, its possible
 * minus, and one mantissa position; the ceiling is past anything the glass
 * could hold at a readable size.
 */
private const val FIELD_POSITIONS_MIN = EXPONENT_DIGITS + 2
private const val FIELD_POSITIONS_MAX = 20

/** Places shown after the radix - the DSP mode. DSP 3 is the default. */
private const val DSP_PLACES = 3

/**
 * The cyan field box's clearance: how far its line's INNER edge sits outside
 * the field's ink envelope, in pixels. Just enough that the hairline never
 * touches a glyph's outermost ink; any more and the box stops honestly
 * marking where the field is.
 */
private const val FIELD_BOX_CLEARANCE_PX = 2f

/** The field box hairline's width, in pixels. */
private const val FIELD_BOX_STROKE_PX = 1f

/**
 * Slack for the does-it-fit comparison, in cell units.
 *
 * A full field of full-width digits measures EXACTLY the field's width, so the
 * comparison sits on an equality that floating point tips either way - which
 * showed as the digit count flickering between 14 and 15 as the gap moved.
 * Far below anything visible; far above any rounding error.
 */
private const val FIT_SLACK_UNITS = 0.001f

/**
 * Ink width of [n] full-width digit positions, in cell units: the cells, the
 * gaps between them, the slant's lean, and the stroke overhanging both ends.
 */
private fun fieldUnits(n: Int, gap: Float, slantDegrees: Float, descender: Float): Float =
    n * TalkRpnFont.CELL_WIDTH + (n - 1) * gap +
        (TalkRpnFont.shearedWidth(slantDegrees, descender) - TalkRpnFont.CELL_WIDTH) +
        TalkRpnFont.STROKE

/**
 * Segment G's y inside a row's canvas: half a stroke of headroom, then half the
 * cap height, at the row's scale. The optical middle of the digits - what the
 * screen centres X on, and what each label centres itself against.
 */
private fun midBarYPx(cellHeightPx: Float): Float =
    (TalkRpnFont.STROKE / 2f + TalkRpnFont.CELL_HEIGHT / 2f) *
        (cellHeightPx / TalkRpnFont.CELL_HEIGHT)

/**
 * Where a row's field begins, in its own canvas coordinates: the field centred
 * on the SCREEN's vertical axis, labels not counted.
 */
private fun fieldLeftPx(
    fieldPositions: Int,
    useDotFont: Boolean,
    cellHeightPx: Float,
    gapUnits: Float,
    dotGapColumns: Float,
    slantDegrees: Float,
    descenderUnits: Float,
    screenPx: Float,
    leftInRootPx: Float,
): Float {

    // A field position is the LIVE font's cell: fp means seven of WHATEVER is
    // showing, not seven segment positions with nine narrower dot cells
    // rattling around inside them.
    val fieldWidthPx =
        if (useDotFont) Hdls1414Font.fieldWidth(fieldPositions, cellHeightPx, dotGapColumns)
        else fieldUnits(fieldPositions, gapUnits, slantDegrees, descenderUnits) *
            (cellHeightPx / TalkRpnFont.CELL_HEIGHT)

    return screenPx / 2f - fieldWidthPx / 2f - leftInRootPx
}

/**
 * Whether to separate groups of three digits left of the radix.
 *
 * Grouping is a display concern, not a font one - the font just draws a ',' if it
 * is handed one. Which character separates and which is the radix swaps with the
 * radix-comma / radix-dot setting, so both live here rather than in the renderer.
 */
private const val GROUP_DIGITS = true
private const val GROUP_SIZE = 3
private const val GROUP_SEPARATOR = ','
private const val RADIX = '.'

/**
 * Show the radix even when no digits follow it, so an integer reads "5." not "5".
 *
 * This is what HP calculators do, and it is not decoration. A trailing point is the
 * signal that you are looking at the whole value rather than at the leading digits
 * of something that has been truncated to fit - which matters on a display this
 * narrow, where truncation is routine. It also distinguishes a displayed number
 * from a register label or an error word at a glance.
 */
private const val ALWAYS_SHOW_RADIX = true

/** Marks the start of an exponent, which never takes a radix of its own. */
private const val EXPONENT_MARKERS = "eE"

/**
 * Whether a sample value is a NUMBER - which the radix, grouping and exponent
 * rules apply to. Text passes through untouched: "Syntax error" must keep its
 * e and gain no trailing radix.
 */
private fun isNumeric(value: String): Boolean =
    value.isNotEmpty() && value.all {
        it.isDigit() || it == RADIX || it == GROUP_SEPARATOR || it == '-' || it in EXPONENT_MARKERS
    }

/**
 * Where [value]'s exponent starts, or -1 when it has none.
 *
 * A marker only counts when what follows it is an optional minus and digits,
 * and what precedes it is numeric - so "6.020E23" gives up its E while
 * "Syntax error" keeps every e it has.
 */
private fun exponentMarkerAt(value: String): Int {

    val at = value.indexOfFirst { it in EXPONENT_MARKERS }
    if (at <= 0) return -1

    if (!isNumeric(value.take(at))) return -1

    val tail = value.substring(at + 1).removePrefix("-")
    if (tail.isEmpty() || !tail.all { it.isDigit() }) return -1

    return at
}

/** Registers above X, in drawing order (T at the top, as HP draws it). */
private val UPPER_REGISTERS = listOf("T", "Z", "Y")

/** Registers below X, in drawing order. */
private val LOWER_REGISTERS = listOf("LASTX", "STO")

/** Millimetres per inch. */

/** Breathing room at each end of a register row. */
private val SIDE_MARGIN = 6.dp

/** Vertical space between register rows. */

/** Space between the register block and the annunciators. */
private val ANNUNCIATOR_GAP = 8.dp

private val GAP_SMALL = 4.dp
private val GAP_MEDIUM = 8.dp

private val TEXT_REGISTER_LABEL = 9.sp

/** Air between a label's right end and the field it names. */
private val LABEL_FIELD_CLEARANCE = 4.dp

/**
 * The label whose width sets the small rows' centring shift - a single letter,
 * the common case, so the field earns most of the glass.
 */
private const val SHIFT_REFERENCE_LABEL = "T"
private val TEXT_ANNUNCIATOR = 10.sp
private val TEXT_BUTTON = 9.sp

private val ANNUNCIATOR_PAD_H = 6.dp
private val ANNUNCIATOR_PAD_V = 2.dp
private val ANNUNCIATOR_CORNER = 3.dp

private val CONTROL_BORDER = Color(0xFF5A5A5A)
private val CONTROL_CORNER = 4.dp

/** Panel width, as a fraction of the screen. Narrow enough to see past. */
private const val CONTROL_PANEL_WIDTH_FRACTION = 0.5f

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

class DisplayTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Judging legibility needs the screen to stay up while you look at it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // And at full brightness: this overrides the user's dimmer (and
        // auto-brightness resting below maximum) for THIS window only, and
        // reverts on leaving. It cannot reach the panel's sunlight-boost
        // nits - that headroom is sensor-driven and system-owned - but it
        // guarantees everything the slider can give.
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        setContent {
            AppScaffold {
                DisplayTestScreen()
            }
        }
    }
}

@Composable
private fun DisplayTestScreen() {

    val context = LocalContext.current

    val metrics = context.resources.displayMetrics

    // The screen is round, and the layout has to know it. Taken as a circle whose
    // diameter is the narrower side.
    val screenPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()

    // The two independent adjustments. Neither moves the other once running.
    var gapUnits by remember { mutableStateOf(INITIAL_GAP_UNITS) }
    var heightFraction by remember { mutableStateOf(INITIAL_HEIGHT_FRACTION) }

    var vgapUnits by remember { mutableStateOf(INITIAL_VGAP_UNITS) }
    var fieldPositions by remember { mutableStateOf(FIELD_POSITIONS) }
    var sampleIndex by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(false) }

    // Which font draws the registers: the segment font, or the HDLS-1414 dot
    // matrix. Same field box either way, so the two are judged in one frame.
    var useDotFont by remember { mutableStateOf(false) }

    // The dot font's own gap, in blank columns - the g knob binds to this
    // while the dot font is live, and to gapUnits otherwise.
    var dotGapColumns by remember { mutableStateOf(Hdls1414Font.CHARACTER_GAP_COLUMNS) }

    // The cyan field boxes: measuring aid, on by default, off to judge the
    // display as a display.
    var showFieldBoxes by remember { mutableStateOf(true) }

    // Slant (6.0 degrees) and descender depth (0.625) are settled in the font;
    // their knobs are gone. Both still travel as plain values so a future knob
    // could return without re-threading anything.
    val slantDegrees = TalkRpnFont.SLANT_DEGREES
    val descenderUnits = TalkRpnFont.DESCENDER_DEPTH


    // Height is now set directly, not inferred from a cell count.
    val xCellHeightPx = heightFraction * screenPx
    val smallCellHeightPx = xCellHeightPx * SMALL_ROW_SCALE

    // ---- Out of cell-width units and into pixels ------------------------------
    //
    // ONE conversion, so there is one place to look when a length is the wrong
    // size. The font's coordinates are cell widths and so are this screen's, so
    // there is nothing to convert between them.

    // Pixels per cell width, at each row's own size. X's is the reference unit
    // the knobs are quoted in; the small rows carry their own smaller unit, so
    // their spacing scales down with them.
    val unitPx = xCellHeightPx / TalkRpnFont.CELL_HEIGHT
    val smallUnitPx = smallCellHeightPx / TalkRpnFont.CELL_HEIGHT

    // ---- Row spacing: each row owns half the vgap, in its own units -----------
    //
    // Every row is a box reaching from half a vgap above its cap line to half a
    // vgap below its descender bar, all at the row's own scale, and the boxes
    // stack touching. Between equal rows a seam's pitch is then exactly the
    // font's VPITCH = TOTAL_HEIGHT + VGAP; between unequal rows each side
    // contributes its half at its own size, so the small rows close up by
    // [SMALL_ROW_SCALE] instead of floating in reference-size air.
    //
    // The descender depth rides along: deeper tails push the rows apart by
    // themselves, holding the tuned vgap clearance.
    fun seamPitchPx(aboveUnitPx: Float, belowUnitPx: Float) =
        (descenderUnits + vgapUnits / 2f) * aboveUnitPx +
            (vgapUnits / 2f + TalkRpnFont.CELL_HEIGHT) * belowUnitPx

    // A Column stacks canvases and separates them with gaps, but pitch is stated
    // baseline to baseline - so each gap is its seam's pitch minus the ink
    // already lying between those two baselines: the tail of the row above,
    // plus the whole of the row below down to its own baseline.
    //
    // Three junctions, because X is a different size from its neighbours.

    val gapSmallToSmallPx =
        rowGapPx(seamPitchPx(smallUnitPx, smallUnitPx), smallCellHeightPx, smallCellHeightPx, descenderUnits)
    val gapSmallToXPx =
        rowGapPx(seamPitchPx(smallUnitPx, unitPx), smallCellHeightPx, xCellHeightPx, descenderUnits)
    val gapXToSmallPx =
        rowGapPx(seamPitchPx(unitPx, smallUnitPx), xCellHeightPx, smallCellHeightPx, descenderUnits)

    // ---- Put X's MIDDLE BAR on the screen's diameter --------------------------
    //
    // The widest chord of a round display passes through its centre, so the
    // widest row earns the most width when the OPTICAL middle of its digits -
    // segment G - sits exactly there. Centring the stack as a block put X
    // wherever the labels' and annunciators' heights happened to leave it.
    //
    // The stack therefore hangs from a computed top spacer: the distance from
    // the screen centre up to X's canvas top, less everything stacked above X.

    val xMidBarInCanvasPx = midBarYPx(xCellHeightPx)

    // What sits above X's canvas: the upper rows' canvases and the gaps between.
    // RAW gaps, negative included - a clamped spacer's shortfall comes back as
    // an upward offset on the rows below it, so the sum is what counts.
    val smallCanvasPx = canvasPx(smallCellHeightPx, descenderUnits)
    val aboveXPx = UPPER_REGISTERS.size * smallCanvasPx +
        (UPPER_REGISTERS.size - 1) * gapSmallToSmallPx +
        gapSmallToXPx

    val topSpacerPx = (screenPx / 2f - xMidBarInCanvasPx - aboveXPx).coerceAtLeast(0f)

    // The small rows centre "label + field" as one unit, so a labelled register
    // reads as the centred thing - X, having no label, centres its field alone.
    // ONE shift for every small row, so the fields all stay aligned with each
    // other - and it is half of a SINGLE-LETTER label's block, not the widest:
    // T Z Y are the common case, and charging every row for LASTX dragged the
    // whole stack left of where the eye wants it. The two long labels simply
    // overhang further into the left margin.
    val textMeasurer = rememberTextMeasurer()
    val smallFieldShiftPx = remember(metrics.density) {
        val referenceLabelPx =
            textMeasurer.measure(SHIFT_REFERENCE_LABEL, TextStyle(fontSize = TEXT_REGISTER_LABEL)).size.width
        (referenceLabelPx + LABEL_FIELD_CLEARANCE.value * metrics.density) / 2f
    }

    // One press must move the layout at least one pixel; below that the control
    // looks broken rather than fine-grained.
    val spacingStepUnits = maxOf(SPACING_STEP_UNITS, MIN_STEP_PX / unitPx)

    // Fit the digits to the field, then punctuate: the radix and the separators
    // are narrower than a digit position, so counting them would under-fill it.
    // The draw step trims by measurement whatever still overruns.
    val samples = SAMPLE_SETS[sampleIndex].values.mapValues { (_, text) ->

        val fitted = when {
            SAMPLE_SETS[sampleIndex].fillsRow ->
                buildString { while (length < fieldPositions) append(text) }.take(fieldPositions)
            // Fixed TEXT is trimmed by characters, left-justified, losing its
            // right end. NUMBERS are not: drawRegister trims them by
            // measurement and owns the exponent block, and a character count
            // here would eat an exponent's last digit - "602.0E21" is eight
            // characters but only seven positions, since the marker, radix
            // and separators never reach the screen.
            !isNumeric(text) && text.length > fieldPositions -> text.take(fieldPositions)
            else -> text
        }

        // Numbers get the radix, grouping and exponent treatment; text does not.
        if (isNumeric(fitted)) groupDigits(ensureRadix(fitted)) else fitted
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LedPalette.BACKGROUND)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {

        // ---- The display itself ---------------------------------------------

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SIDE_MARGIN),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // Hangs the stack so X's segment G lands on the diameter; see the
            // derivation of topSpacerPx above.
            Spacer(Modifier.height(pxToDp(topSpacerPx, metrics.density)))

            // A Compose spacer cannot be negative, so when the vpitch drops
            // below the rows-touch point each clamped gap's shortfall is
            // accumulated here and paid back as an upward offset on everything
            // below it. That is what lets the control show OVERLAP rather than
            // silently stopping at the touch point.
            var overlapPx = 0f

            for ((index, name) in UPPER_REGISTERS.withIndex()) {

                RegisterRow(
                    name, samples[name].orEmpty(), fieldPositions, useDotFont, showFieldBoxes,
                    smallCellHeightPx, gapUnits, dotGapColumns,
                    LedPalette.LIT, metrics.density, screenPx, smallFieldShiftPx, slantDegrees,
                    descenderUnits, Modifier.offset(y = pxToDp(overlapPx, metrics.density))
                )

                // The last of these sits above X, which is taller, so it needs a
                // smaller gap to land on the same baseline-to-baseline distance.
                val gapPx =
                    if (index == UPPER_REGISTERS.lastIndex) gapSmallToXPx else gapSmallToSmallPx

                Spacer(Modifier.height(pxToDp(gapPx.coerceAtLeast(0f), metrics.density)))
                overlapPx += minOf(gapPx, 0f)
            }

            // X carries no label: it is the largest row, it spans the full width,
            // and the whole point of "across the middle" is that it gets
            // everything. Like every register, its field is centred on the
            // screen's vertical axis.
            var xLeftInRootPx by remember { mutableStateOf(0f) }

            Canvas(
                modifier = Modifier
                    .offset(y = pxToDp(overlapPx, metrics.density))
                    .fillMaxWidth()
                    .height(canvasHeightDp(xCellHeightPx, metrics.density, descenderUnits))
                    .onGloballyPositioned { xLeftInRootPx = it.positionInRoot().x }
            ) {
                drawRegister(
                    samples["X"].orEmpty(), fieldPositions, useDotFont, showFieldBoxes,
                    xCellHeightPx, gapUnits, dotGapColumns, LedPalette.LIT,
                    fieldLeftPx(
                        fieldPositions, useDotFont, xCellHeightPx, gapUnits, dotGapColumns,
                        slantDegrees, descenderUnits, screenPx, xLeftInRootPx
                    ),
                    slantDegrees, descenderUnits
                )
            }

            // X down to the first of the lower registers: the row below is now the
            // smaller one, so this gap is the wider of the three.
            Spacer(Modifier.height(pxToDp(gapXToSmallPx.coerceAtLeast(0f), metrics.density)))
            overlapPx += minOf(gapXToSmallPx, 0f)

            for (name in LOWER_REGISTERS) {

                RegisterRow(
                    name, samples[name].orEmpty(), fieldPositions, useDotFont, showFieldBoxes,
                    smallCellHeightPx, gapUnits, dotGapColumns,
                    LedPalette.LIT, metrics.density, screenPx, smallFieldShiftPx, slantDegrees,
                    descenderUnits, Modifier.offset(y = pxToDp(overlapPx, metrics.density))
                )

                // The trailing one has no row beneath it - it is just the padding
                // ahead of the annunciators, and matching the others keeps it even.
                Spacer(Modifier.height(pxToDp(gapSmallToSmallPx.coerceAtLeast(0f), metrics.density)))
                overlapPx += minOf(gapSmallToSmallPx, 0f)
            }

            Spacer(Modifier.height(ANNUNCIATOR_GAP))

            // ---- Annunciators ------------------------------------------------
            //
            // Not real segments, as on an HP-42S - just a reserved screen region
            // that draws one word or the other. Cheating, deliberately.
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.offset(y = pxToDp(overlapPx, metrics.density)),
            ) {
                Annunciator("DEG")
                Spacer(Modifier.width(GAP_SMALL))
                Annunciator("SI")
            }
        }

        // ---- Controls, on top, only when asked for --------------------------

        if (showControls) {

            // Centred rather than sitting at the bottom. On a round screen the
            // bottom is where the glass has almost run out, and a panel down
            // there loses its outer buttons to the curve.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    // Two controls fit side by side, and the display stays
                    // visible around it.
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
                    .padding(horizontal = CONTROL_PANEL_MARGIN, vertical = GAP_MEDIUM),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Each button carries its own value, named as the code names
                // it: g DEFAULT_GAP and vg VGAP in cell widths, copying
                // straight back into TalkRpnFont - and hf heightFraction, the
                // screen's own knob: a fraction of the diameter. All to three
                // decimals, so no knob's readout is coarser than another's
                // steps. No separate readout to cross-reference.
                Row(modifier = Modifier.fillMaxWidth()) {

                    // One knob, two tweakables: g binds to whichever font is
                    // live - the segment font's DEFAULT_GAP in cell widths, or
                    // the dot font's CHARACTER_GAP_COLUMNS in blank columns.
                    // The value shown is always the live one.
                    SplitButton(
                        if (useDotFont) "g %.2f".format(dotGapColumns)
                        else "g %.3f".format(gapUnits),
                        Modifier.weight(1f),
                        onIncrease = {
                            if (useDotFont) dotGapColumns = (dotGapColumns + DOT_GAP_STEP_COLUMNS)
                                .coerceAtMost(DOT_GAP_COLUMNS_MAX)
                            else gapUnits = (gapUnits + spacingStepUnits)
                                .coerceAtMost(GAP_UNITS_MAX)
                        },
                        onDecrease = {
                            if (useDotFont) dotGapColumns = (dotGapColumns - DOT_GAP_STEP_COLUMNS)
                                .coerceAtLeast(DOT_GAP_COLUMNS_MIN)
                            else gapUnits = (gapUnits - spacingStepUnits)
                                .coerceAtLeast(GAP_UNITS_MIN)
                        }
                    )

                    // width, not height: inside a Row it is the horizontal axis
                    // that needs the gap.
                    Spacer(Modifier.width(GAP_SMALL))

                    // hf tunes heightFraction, the one knob that is not font
                    // geometry: how large the whole display renders, as X's cap
                    // height in fractions of the screen diameter.
                    SplitButton("hf %.3f".format(heightFraction), Modifier.weight(1f),
                        onIncrease = {
                            heightFraction = (heightFraction * (1f + ADJUST_STEP_FRACTION))
                                .coerceAtMost(HEIGHT_FRACTION_MAX)
                        },
                        onDecrease = {
                            heightFraction = (heightFraction / (1f + ADJUST_STEP_FRACTION))
                                .coerceAtLeast(HEIGHT_FRACTION_MIN)
                        }
                    )
                }

                Spacer(Modifier.height(GAP_SMALL))

                Row(modifier = Modifier.fillMaxWidth()) {

                    SplitButton("vg %.3f".format(vgapUnits), Modifier.weight(1f),
                        onIncrease = {
                            vgapUnits = (vgapUnits + spacingStepUnits)
                                .coerceAtMost(VGAP_UNITS_MAX)
                        },
                        onDecrease = {
                            vgapUnits = (vgapUnits - spacingStepUnits)
                                .coerceAtLeast(VGAP_UNITS_MIN)
                        }
                    )

                    Spacer(Modifier.width(GAP_SMALL))

                    // fp tunes fieldPositions - how many digit positions each
                    // register's field holds. An integer: whole cells only.
                    SplitButton("fp %d".format(fieldPositions), Modifier.weight(1f),
                        onIncrease = {
                            fieldPositions = (fieldPositions + 1)
                                .coerceAtMost(FIELD_POSITIONS_MAX)
                        },
                        onDecrease = {
                            fieldPositions = (fieldPositions - 1)
                                .coerceAtLeast(FIELD_POSITIONS_MIN)
                        }
                    )
                }

                Spacer(Modifier.height(GAP_SMALL))

                Row(modifier = Modifier.fillMaxWidth()) {

                    // Named for the set it is SHOWING, not the one a tap brings.
                    CompactButton("sample: " + SAMPLE_SETS[sampleIndex].name, Modifier.weight(1f)) {
                        sampleIndex = (sampleIndex + 1) % SAMPLE_SETS.size
                    }

                    Spacer(Modifier.width(GAP_SMALL))

                    // Likewise: the font on screen now, not the one a tap brings.
                    CompactButton(if (useDotFont) "font: dot" else "font: seg", Modifier.weight(1f)) {
                        useDotFont = !useDotFont
                    }
                }

                Spacer(Modifier.height(GAP_SMALL))

                // Named for what is showing, like sample and font.
                CompactButton(if (showFieldBoxes) "boxes: on" else "boxes: off", Modifier.fillMaxWidth()) {
                    showFieldBoxes = !showFieldBoxes
                }
            }
        }

        // Where the round glass ends. Drawn last, over everything, so it marks
        // controls as well as digits - and EMULATOR ONLY: the ring can only
        // exist inside the glass, where a real watch would show it as a lit
        // circle. On the wrist the glass edge marks itself.
        GlassEdgeIfEmulator()
    }
}

/** One of the smaller registers: a name at the left, the value in its field. */
@Composable
private fun RegisterRow(
    name: String,
    value: String,
    fieldPositions: Int,
    useDotFont: Boolean,
    showFieldBox: Boolean,
    cellHeightPx: Float,
    gapUnits: Float,
    dotGapColumns: Float,
    color: Color,
    density: Float,
    screenPx: Float,
    fieldShiftPx: Float,
    slantDegrees: Float,
    descenderUnits: Float,
    modifier: Modifier = Modifier,
) {
    // Where this row sits horizontally, so the screen's axis can be found in
    // its own coordinates.
    var leftInRootPx by remember { mutableStateOf(0f) }

    // The field centred on the screen's axis, then shifted right by half the
    // widest label block, so LABEL PLUS FIELD is the centred unit. The same
    // shift for every small row keeps their fields aligned with each other.
    val rowFieldLeftPx =
        fieldLeftPx(
            fieldPositions, useDotFont, cellHeightPx, gapUnits, dotGapColumns,
            slantDegrees, descenderUnits, screenPx, leftInRootPx
        ) + fieldShiftPx

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { leftInRootPx = it.positionInRoot().x }
    ) {

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeightDp(cellHeightPx, density, descenderUnits))
        ) {
            drawRegister(
                value, fieldPositions, useDotFont, showFieldBox, cellHeightPx, gapUnits, dotGapColumns,
                color, rowFieldLeftPx, slantDegrees, descenderUnits
            )
        }

        // The label sits just LEFT of the field, right-justified against the
        // mantissa's starting edge, so every label ends at the same x and reads
        // as a column of names beside a column of values. Done by giving the
        // label a box that ends where the field begins, less a little clearance,
        // and letting the text right-align inside it - no text measuring needed.
        //
        // Vertically it centres on SEGMENT G - the optical middle of the digits
        // - by way of a box exactly twice segment G's depth, whose centre is
        // therefore segment G, whatever height the text turns out to be.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(pxToDp(rowFieldLeftPx, density) - LABEL_FIELD_CLEARANCE)
                .height(pxToDp(2f * midBarYPx(cellHeightPx), density)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = name,
                color = LedPalette.LABEL,
                fontSize = TEXT_REGISTER_LABEL,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * A control small enough to leave the display visible.
 *
 * Wear's own Button is built for a finger on a watch face and is roughly a quarter
 * of the screen; four of them buried the layout this screen exists to show. These
 * are harder to hit, which is the right trade for a measuring instrument.
 */
@Composable
private fun CompactButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
 * One control, two halves: press the left to increase, the right to decrease.
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
                    .clickable(onClick = onIncrease)
                    .padding(horizontal = CONTROL_PAD_H, vertical = CONTROL_PAD_V),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("+", color = LedPalette.LABEL, fontSize = TEXT_BUTTON)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onDecrease)
                    .padding(horizontal = CONTROL_PAD_H, vertical = CONTROL_PAD_V),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("-", color = LedPalette.LABEL, fontSize = TEXT_BUTTON)
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

/** A reserved region that shows one word out of a set. */
@Composable
private fun Annunciator(text: String) {
    Box(
        modifier = Modifier
            .border(1.dp, ANNUNCIATOR_BORDER, RoundedCornerShape(ANNUNCIATOR_CORNER))
            .padding(horizontal = ANNUNCIATOR_PAD_H, vertical = ANNUNCIATOR_PAD_V)
    ) {
        Text(text, color = LedPalette.LABEL, fontSize = TEXT_ANNUNCIATOR)
    }
}

/**
 * Draws one register's value into its [fieldPositions]-position field, whose
 * position 1 begins at [fieldLeftPx] - the caller centres the field on the
 * screen's vertical axis via [fieldLeftPx] (the function of the same name).
 *
 * Mantissa left-justified from position 1; exponent, when there is one,
 * right-justified into exactly its own characters' positions, no marker, the
 * separating blank zero-width - see [EXPONENT_DIGITS].
 *
 * TWO FONTS, ONE FIELD MEANING. A field position is one cell OF THE LIVE FONT,
 * so fp = 7 means seven segment positions or seven dot cells, whichever is
 * showing - the box resizes with the font rather than leaving the narrower
 * dot cells rattling inside a segment-sized frame. The dot font draws in its
 * own default colour (its whole point is looking different) and its own fixed
 * pitch, where '.' and ',' take a whole cell rather than living in a gap -
 * which the formatter does not yet know, so a dotted number spends one more
 * dot position than dsp() planned for.
 *
 * Anything the segment font has no glyph for is dropped by its layout; the dot
 * font draws a blank cell instead, which is what fixed pitch means.
 */
private fun DrawScope.drawRegister(
    value: String,
    fieldPositions: Int,
    useDotFont: Boolean,
    showFieldBox: Boolean,
    cellHeightPx: Float,
    gapUnits: Float,
    dotGapColumns: Float,
    color: Color,
    fieldLeftPx: Float,
    slantDegrees: Float,
    descenderUnits: Float,
) {
    if (value.isEmpty() || cellHeightPx <= 0f) return

    val scale = cellHeightPx / TalkRpnFont.CELL_HEIGHT

    // The ink width of a run of n full-width digit positions, at this row's size.
    fun positionsPx(n: Int): Float = fieldUnits(n, gapUnits, slantDegrees, descenderUnits) * scale

    // THE FIELD: position 1 at [fieldLeftPx], the caller having centred it -
    // and its width in the LIVE font's positions, matching fieldLeftPx.
    val fieldRightPx = fieldLeftPx +
        if (useDotFont) Hdls1414Font.fieldWidth(fieldPositions, cellHeightPx, dotGapColumns)
        else positionsPx(fieldPositions)

    // The field box made visible, for fitting work: a hairline around the
    // whole register's display area, in the diagnostic cyan so it cannot be
    // mistaken for lit ink. On the boxes button.
    //
    // The rectangle fieldLeft..fieldRight x 0..inkHeight IS the ink envelope -
    // fieldUnits carries the stroke's overhang at both ends, and the row draws
    // its ink from the canvas top - so the box inflates OUTWARD from there by
    // its clearance, plus half its own line so the line's inner edge is what
    // sits at the clearance.
    if (showFieldBox) {

        val boxOutsetPx = FIELD_BOX_CLEARANCE_PX + FIELD_BOX_STROKE_PX / 2f

        // The box hugs the ACTIVE font's ink. The dot font has no descender -
        // its ink stops half a dot below the bottom lattice row - where the
        // segment font's runs on down through the descender band.
        val boxInkHeightPx =
            if (useDotFont) cellHeightPx * Hdls1414Font.INK_HEIGHT / Hdls1414Font.CELL_HEIGHT
            else inkHeightPx(cellHeightPx, descenderUnits)

        drawRect(
            color = LedPalette.FIELD_BOUNDS,
            topLeft = Offset(fieldLeftPx - boxOutsetPx, -boxOutsetPx),
            size = Size(
                fieldRightPx - fieldLeftPx + 2f * boxOutsetPx,
                boxInkHeightPx + 2f * boxOutsetPx,
            ),
            style = Stroke(width = FIELD_BOX_STROKE_PX),
        )
    }

    // Split off the exponent. The marker never reaches the screen: the mantissa
    // is left-justified from position 1, the exponent right-justified into the
    // field's last positions, and the inter-cell gap between the two blocks
    // is all the separating the eye needs - the blank is zero-width.
    val markerAt = exponentMarkerAt(value)
    var mantissa = if (markerAt >= 0) value.take(markerAt) else value
    val exponent = if (markerAt >= 0) value.substring(markerAt + 1) else ""

    // The mantissa may not enter the exponent's positions: its characters,
    // the minus included, plus the one reserved blank. Overflow loses its
    // RIGHT end - it is the left-justified block.
    val mantissaPositions =
        if (exponent.isEmpty()) fieldPositions else fieldPositions - exponent.length - 1

    if (useDotFont) {

        // Fixed pitch makes the fit check trivial. The g knob binds to
        // [dotGapColumns] while this font is live - the dot font's own gap, in
        // its own unit of blank columns.
        with(Hdls1414Font) {

            val mantissaMaxPx = fieldWidth(mantissaPositions, cellHeightPx, dotGapColumns)

            while (mantissa.isNotEmpty() &&
                measureWidth(mantissa, cellHeightPx, dotGapColumns) > mantissaMaxPx
            ) {
                mantissa = mantissa.dropLast(1)
            }

            if (mantissa.isNotEmpty()) {
                drawHdls1414Text(
                    text = mantissa,
                    inkOrigin = Offset(fieldLeftPx, 0f),
                    cellHeight = cellHeightPx,
                    gapColumns = dotGapColumns,
                )
            }

            if (exponent.isNotEmpty()) {

                // Placed by POSITION: the block's first cell, one blank past
                // the mantissa's share - fixed pitch makes the cell index the
                // whole story.
                val advancePx = (DOT_COLUMNS + dotGapColumns) * COLUMN_PITCH *
                    cellHeightPx / CELL_HEIGHT

                drawHdls1414Text(
                    text = exponent,
                    inkOrigin = Offset(
                        fieldLeftPx + (mantissaPositions + 1) * advancePx,
                        0f,
                    ),
                    cellHeight = cellHeightPx,
                    gapColumns = dotGapColumns,
                )
            }
        }

        return
    }

    // The check runs in the font's own UNITS, not pixels, so every row reaches
    // the same verdict for the same text - checked in pixels, the small rows'
    // different rounding could disagree with X's at the same gap. A TRAILING
    // radix or separator is not counted: it lives in the gap after its digit,
    // costs no position, and may poke past the field into the darkness.
    val mantissaMaxUnits = fieldUnits(mantissaPositions, gapUnits, slantDegrees, descenderUnits)

    fun fits(text: String): Boolean {
        val counted = text.trimEnd(RADIX, GROUP_SEPARATOR)
        return TalkRpnFont.measureWidth(counted, TalkRpnFont.CELL_HEIGHT, gapUnits, slantDegrees, descenderUnits) <=
            mantissaMaxUnits + FIT_SLACK_UNITS
    }

    while (mantissa.isNotEmpty() && !fits(mantissa)) {
        mantissa = mantissa.dropLast(1)
    }

    // The font takes the ink's own top-left corner, so rows simply hang from the
    // top of their canvas - the stroke's overhang is inside the measurement.
    with(TalkRpnFont) {

        if (mantissa.isNotEmpty()) {
            drawTalkRpnText(
                text = mantissa,
                inkOrigin = Offset(fieldLeftPx, 0f),
                cellHeight = cellHeightPx,
                color = color,
                gap = gapUnits,
                slantDegrees = slantDegrees,
                descender = descenderUnits
            )
        }

        if (exponent.isNotEmpty()) {

            // Placed by POSITION: the block's ink starts at its first cell's
            // boundary, one blank position past the mantissa's share. A
            // position is a full cell plus its leading gap.
            val exponentLeftPx =
                fieldLeftPx + (mantissaPositions + 1) *
                    (TalkRpnFont.CELL_WIDTH + gapUnits) * scale

            drawTalkRpnText(
                text = exponent,
                inkOrigin = Offset(exponentLeftPx, 0f),
                cellHeight = cellHeightPx,
                color = color,
                gap = gapUnits,
                slantDegrees = slantDegrees,
                descender = descenderUnits
            )
        }
    }
}

/**
 * Inserts a separator every [GROUP_SIZE] digits to the left of the radix.
 *
 * Only the integer part is grouped. Digits after the radix are not - grouping them
 * is a convention nobody uses on a calculator, and the exponent must not be touched
 * at all.
 */
/**
 * Adds a trailing radix when the value has none, per [ALWAYS_SHOW_RADIX].
 *
 * The radix belongs to the mantissa, so on a value carrying an exponent it goes
 * before the exponent marker rather than at the end - "6E23" becomes "6.E23",
 * not "6E23.". The marker itself never reaches the screen; drawRegister splits
 * it off and right-justifies the exponent digits into the field's last
 * positions.
 */
private fun ensureRadix(value: String): String {

    if (!ALWAYS_SHOW_RADIX || value.isEmpty()) return value

    // Split the mantissa from any exponent.
    val exponentAt = exponentMarkerAt(value)
    val mantissa = if (exponentAt >= 0) value.substring(0, exponentAt) else value
    val exponent = if (exponentAt >= 0) value.substring(exponentAt) else ""

    if (mantissa.contains(RADIX)) return value

    return mantissa + RADIX + exponent
}

/**
 * DSP - fixed-point to [places] after the radix, falling back to ENG notation
 * when fixed form cannot say anything useful: when the digits outgrow the
 * field, or when the value is so small that every shown place would be zero.
 *
 * Fixed form owns the WHOLE field, since it shows no exponent. Eng form gives
 * the field's end to the exponent block - TWO digits, zero-padded, a minus
 * ahead when negative, the separating blank zero-width - the exponent is a
 * multiple of three, and the mantissa takes however many places still fit
 * its share.
 *
 * Only SIGNS AND DIGITS cost field positions: the radix and the group
 * separators live in the gaps between cells and cost nothing, which is
 * exactly why "1.414" fits a four-position share.
 *
 * A sketch of the real formatter, good enough to feed the samples. The real
 * one inherits its edge cases: rounding that carries into a new digit
 * (999.96 becoming 1000.0), exponents of three digits, and reformatting live
 * when the fp knob moves - this sketch bakes [FIELD_POSITIONS] in at start.
 */
private fun dsp(value: Double, places: Int = DSP_PLACES): String {

    if (value == 0.0) return "%.${places}f".format(0.0)

    val magnitude = abs(value)

    // The positions fixed form needs: the sign and the digits, nothing else.
    val integerDigits = maxOf(floor(log10(magnitude)).toInt() + 1, 1)
    val sign = if (value < 0) 1 else 0
    val fixedPositions = sign + integerDigits + places

    val tooBig = fixedPositions > FIELD_POSITIONS
    val tooSmall = magnitude < 10.0.pow(-places)

    if (!tooBig && !tooSmall) return "%.${places}f".format(value)

    // Eng: pull the exponent down to a multiple of three, leaving the
    // mantissa in [1, 1000). Two digits always, zero-padded, the minus ahead
    // of them when negative.
    val engExponent = Math.floorDiv(floor(log10(magnitude)).toInt(), 3) * 3
    val mantissa = value / 10.0.pow(engExponent)

    val exponentBlock =
        if (engExponent < 0) "-%02d".format(-engExponent) else "%02d".format(engExponent)

    // The mantissa's share is what the exponent's characters and the one
    // reserved blank position leave; spend what remains after the sign and
    // the integer digits on decimal places.
    val mantissaIntegerDigits = maxOf(floor(log10(abs(mantissa))).toInt() + 1, 1)
    val mantissaShare = FIELD_POSITIONS - exponentBlock.length - 1
    val mantissaPlaces = (mantissaShare - sign - mantissaIntegerDigits).coerceAtLeast(0)

    return "%.${mantissaPlaces}fE%s".format(mantissa, exponentBlock)
}

private fun groupDigits(value: String): String {

    if (!GROUP_DIGITS) return value

    // Split off everything from the radix onward, and any leading sign, so that
    // only the integer digits are counted.
    val radixAt = value.indexOf(RADIX)
    val head = if (radixAt >= 0) value.substring(0, radixAt) else value
    val tail = if (radixAt >= 0) value.substring(radixAt) else ""

    val signLength = if (head.startsWith("-")) 1 else 0
    val sign = head.take(signLength)
    val digits = head.drop(signLength)

    // Anything that is not a plain run of digits is left alone rather than
    // guessed at - an exponent, say.
    if (digits.isEmpty() || !digits.all { it.isDigit() }) return value

    // Group from the right, which is where the counting starts.
    val grouped = digits.reversed()
        .chunked(GROUP_SIZE)
        .joinToString(GROUP_SEPARATOR.toString())
        .reversed()

    return sign + grouped + tail
}

/** Device pixels to Dp. Compose lays out in Dp; the font works in pixels. */
private fun pxToDp(px: Float, density: Float) = (px / density).dp

/** The full ink height of a row's canvas, for chord limits and canvas sizing. */
private fun inkHeightPx(cellHeightPx: Float, descenderUnits: Float) =
    cellHeightPx * (TalkRpnFont.totalHeight(descenderUnits) + TalkRpnFont.STROKE) / TalkRpnFont.CELL_HEIGHT

/**
 * A row canvas's exact laid-out height in pixels: its ink rounded up, plus the
 * safety pixel - the same arithmetic [canvasHeightDp] hands to Compose, kept in
 * one place so spacing and sizing can never disagree about it.
 */
private fun canvasPx(cellHeightPx: Float, descenderUnits: Float) =
    ceil(inkHeightPx(cellHeightPx, descenderUnits)) + 1f

/**
 * How far a row's baseline sits below the TOP of its own canvas: half a stroke
 * of headroom above the cap line, then the cap height. Rows draw their ink box
 * from the canvas top, so this needs no descender term.
 */
private fun baselineFromTopPx(cellHeightPx: Float) =
    cellHeightPx * (TalkRpnFont.STROKE / 2f + TalkRpnFont.CELL_HEIGHT) /
        TalkRpnFont.CELL_HEIGHT

/**
 * The Column gap that leaves two stacked rows exactly [vpitchPx] apart, baseline
 * to baseline: the pitch, less everything already lying between the two
 * baselines - the ABOVE row from its baseline down to its canvas bottom
 * (descender, half stroke, and the canvas's rounding padding), and the BELOW
 * row from its canvas top down to its baseline. Depends on both rows, because
 * they may be different sizes.
 */
private fun rowGapPx(vpitchPx: Float, abovePx: Float, belowPx: Float, descenderUnits: Float) =
    vpitchPx - (canvasPx(abovePx, descenderUnits) - baselineFromTopPx(abovePx)) -
        baselineFromTopPx(belowPx)

/**
 * Dp for a Canvas that must be at least [px] tall.
 *
 * The font is scale-invariant - all its geometry is in cell units under one uniform
 * scale - but the box it draws into is not, because Compose rounds Dp back to whole
 * pixels. A cell of 20.7 px asked for as 10.35 dp comes back as 21 px on one device
 * and 20 on another, and at 20 the bottom row of the glyph is clipped: segment d
 * loses a pixel while everything above it does not. That is a distortion that
 * appears and disappears with size, which is the hardest kind to notice.
 *
 * Rounding up costs a pixel of layout and removes the failure mode entirely.
 */
private fun canvasHeightDp(px: Float, density: Float, descenderUnits: Float) =
    (canvasPx(px, descenderUnits) / density).dp

