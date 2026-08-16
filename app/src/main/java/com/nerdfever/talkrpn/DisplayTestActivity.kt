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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * There is no INITIAL constant: the starting height is DERIVED as the tallest
 * digits at which the full field still fits the widest chord, so the field's
 * size and the gap decide it. See the derivation in [DisplayTestScreen].
 */
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
 * Independent of the cw control: this moves spacing only. Sizing the cell to
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
 * VGAP - the vertical space between rows, in the same units. The knob mirrors
 * the font's tweakable of the same name: one row's descender-bar centreline
 * down to the next row's cap centreline, negative meaning the bands interleave.
 *
 * The screen spaces rows by the derived vpitch, baseline to baseline:
 * totalHeight(dd) + vg. Baseline to baseline rather than gap-between-rows, so
 * that it keeps meaning the same thing when adjacent rows are different sizes -
 * which they are here, since every row but X is scaled by [SMALL_ROW_SCALE].
 * Measured always in the X row's units, so a smaller row does not bring
 * smaller units with it.
 *
 * A uniform GAP between unequal rows would put the baselines at unequal
 * distances, and the baselines are what the eye reads. Making the spacing
 * uniform means the gaps differ instead, which is the way round that looks even.
 */
private val INITIAL_VGAP_UNITS = TalkRpnFont.VGAP

/**
 * The vgap control's range, deliberately far past both touching points: ink
 * meets ink at one stroke, like the horizontal gap, and on a measuring
 * instrument seeing the overlap is more useful than being protected from it.
 * The floor puts every baseline in nearly the same place (vpitch about zero,
 * exactly zero at the font's own descender depth).
 */
private val VGAP_UNITS_MIN = -TalkRpnFont.TOTAL_HEIGHT
private const val VGAP_UNITS_MAX = 2.0f

/**
 * The gap, vgap and dd knobs step by this, additively - they are lengths, not
 * proportions. A fortieth of a cell width, which is fine enough to tune with and
 * coarse enough that a press is always visible at a readable size.
 */
private const val SPACING_STEP_UNITS = 0.025f

/** Every proportional adjustment moves by this much per press. */
private const val ADJUST_STEP_FRACTION = 0.05f

/**
 * The descender knob's range, in cell widths. Wide on purpose: from a stub
 * shallower than the stroke up to well past the authentic depth, so the whole
 * question can be answered by eye.
 */
private const val DESCENDER_UNITS_MIN = 0.2f
private const val DESCENDER_UNITS_MAX = 1.2f

/**
 * A press must never move the layout by less than one pixel. A step that lands
 * below the display's resolution spends several clicks crossing each pixel
 * boundary, and reads as a control that is broken or laggy.
 *
 * Units are the right thing to STORE - they transfer to the watch, where a pixel
 * figure tuned on the emulator would not - but the wrong thing to step by blindly,
 * because a unit is smaller than a pixel at these sizes. So the step is whichever
 * is larger: the nominal step above, or one pixel's worth of units.
 */
private const val MIN_STEP_PX = 1f

/** The annunciator box outline. */
private val ANNUNCIATOR_BORDER = Color(0xFF4A4A4A)

/**
 * What each register shows, per sample set.
 *
 * The all-eights set is the one that matters for legibility: every segment lit is
 * the worst case, because adjacent digits then have the least dark space between
 * them.
 */
private val SAMPLE_SETS = listOf(
    // Something a real calculation would look like, formatted at the DSP default.
    mapOf(
        "T" to 0.0,
        "Z" to 12.0,
        "Y" to 1.4142136,
        "X" to 3.1415927,
        "LASTX" to 2.7182818,
        "STO" to 6.02e23,
    ).mapValues { (_, v) -> dsp(v) },
    // Worst case: every segment lit, every cell full.
    mapOf(
        "T" to "8",
        "Z" to "8",
        "Y" to "8",
        "X" to "8",
        "LASTX" to "8",
        "STO" to "8",
    ),
    // Every glyph, so no digit hides behind another.
    mapOf(
        "T" to "1234567890",
        "Z" to "1234567890",
        "Y" to "1234567890",
        "X" to "1234567890",
        "LASTX" to "1234567890",
        "STO" to "1234567890",
    ),
    // Lower case, heavy on descenders, so g j p q y can be judged - especially
    // whether one row's tails collide with the row beneath at the current
    // vpitch, which sits below the descender-clearance point by design.
    mapOf(
        "T" to "jaggy pyjamas",
        "Z" to "happy pygmy jog",
        "Y" to "Syntax error",
        "X" to "quick jazzy pig",
        "LASTX" to "grumpy dog",
        "STO" to "type gyp quay",
    ),
)

/**
 * Whether each sample set should be grown to whatever number of cells now fits.
 *
 * The realistic and lowercase sets are fixed text, because their point is to
 * look like something; the fill sets exist to fill the field, so they follow it.
 */
private val SAMPLE_FILLS_ROW = listOf(false, true, true, false)

/**
 * THE FIELD: every register shows its value in this many digit positions.
 *
 * A position is one full-width cell plus one gap. The field's right edge goes as
 * far right as the glass allows; the mantissa is left-justified from position 1,
 * and an exponent occupies the rightmost [EXPONENT_FIELD_POSITIONS].
 */
private const val FIELD_POSITIONS = 15

/**
 * The exponent's share of the field, at the right end: a blank - or the minus,
 * when the exponent is negative - then two digits. HP convention; no marker.
 */
private const val EXPONENT_FIELD_POSITIONS = 3

/** Places shown after the radix - the DSP mode. DSP 3 is the default. */
private const val DSP_PLACES = 3

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
    cellHeightPx: Float,
    gapUnits: Float,
    slantDegrees: Float,
    descenderUnits: Float,
    screenPx: Float,
    leftInRootPx: Float,
): Float {
    val fieldWidthPx = fieldUnits(FIELD_POSITIONS, gapUnits, slantDegrees, descenderUnits) *
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

/**
 * Extra inset from the glass edge, beyond what the round-screen geometry demands.
 *
 * The chord calculation says where the circle is; this says how close to it we are
 * willing to put ink. Curved glass and the bezel eat the last millimetre.
 */
private val BEZEL_INSET = 3.dp

/** Vertical space between register rows. */

/** Space between the register block and the annunciators. */
private val ANNUNCIATOR_GAP = 8.dp

private val GAP_SMALL = 4.dp
private val GAP_MEDIUM = 8.dp

private val TEXT_REGISTER_LABEL = 9.sp

/** Air between a label's right end and the field it names. */
private val LABEL_FIELD_CLEARANCE = 4.dp
private val TEXT_ANNUNCIATOR = 10.sp
private val TEXT_READOUT = 8.sp
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
    val insetPx = BEZEL_INSET.value * metrics.density

    // The two independent adjustments. Neither moves the other once running -
    // but the STARTING height is derived from the field: the tallest digits at
    // which all FIELD_POSITIONS fit the widest chord at the default gap. X sits
    // on the diameter, so its chord is the screen less the bezel insets.
    val fittedHeightFraction = remember {
        val usablePx = screenPx - 2f * insetPx
        val fieldWidthUnits =
            fieldUnits(FIELD_POSITIONS, INITIAL_GAP_UNITS, TalkRpnFont.SLANT_DEGREES, TalkRpnFont.DESCENDER_DEPTH)
        (usablePx / fieldWidthUnits * TalkRpnFont.CELL_HEIGHT / screenPx)
            .coerceIn(HEIGHT_FRACTION_MIN, HEIGHT_FRACTION_MAX)
    }

    var gapUnits by remember { mutableStateOf(INITIAL_GAP_UNITS) }
    var heightFraction by remember { mutableStateOf(fittedHeightFraction) }

    var vgapUnits by remember { mutableStateOf(INITIAL_VGAP_UNITS) }
    var descenderUnits by remember { mutableStateOf(TalkRpnFont.DESCENDER_DEPTH) }
    var sampleIndex by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(false) }

    // Slant is settled at 6.0 degrees; its knob gave way to the descender's.
    val slantDegrees = TalkRpnFont.SLANT_DEGREES


    // Height is now set directly, not inferred from a cell count.
    val xCellHeightPx = heightFraction * screenPx
    val smallCellHeightPx = xCellHeightPx * SMALL_ROW_SCALE

    // ---- Out of cell-width units and into pixels ------------------------------
    //
    // ONE conversion, so there is one place to look when a length is the wrong
    // size. The font's coordinates are cell widths and so are this screen's, so
    // there is nothing to convert between them.

    // Pixels per cell width, always at the X row's size - that is the reference
    // the vertical spacing is quoted in, so a smaller row must not redefine it.
    val unitPx = xCellHeightPx / TalkRpnFont.CELL_HEIGHT

    // The derived vertical pitch, exactly as the font derives VPITCH: the row's
    // span at the CURRENT descender, plus the tuned gap between rows. This is
    // what makes the dd knob carry the rows with it.
    val vpitchPx = (TalkRpnFont.totalHeight(descenderUnits) + vgapUnits) * unitPx

    // ---- Row spacing, from the derived vpitch ---------------------------------
    //
    // A Column stacks canvases and separates them with gaps, but vpitch is stated
    // baseline to baseline - so each gap is vpitch minus the ink already lying
    // between those two baselines: the tail of the row above, plus the whole of
    // the row below down to its own baseline.
    //
    // Three junctions, because X is a different size from its neighbours. This is
    // the part a single shared gap got wrong: equal gaps between unequal rows put
    // the baselines at unequal distances, which is what the eye actually reads.

    val gapSmallToSmallPx = rowGapPx(vpitchPx, smallCellHeightPx, smallCellHeightPx, descenderUnits)
    val gapSmallToXPx = rowGapPx(vpitchPx, smallCellHeightPx, xCellHeightPx, descenderUnits)
    val gapXToSmallPx = rowGapPx(vpitchPx, xCellHeightPx, smallCellHeightPx, descenderUnits)

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
    // an upward offset on the rows below it, so the sum is what counts. Canvas
    // heights repeat the rounding in canvasHeightDp; being a pixel off here
    // moves the chord by nothing measurable.
    val smallCanvasPx = ceil(inkHeightPx(smallCellHeightPx, descenderUnits)) + 1f
    val aboveXPx = UPPER_REGISTERS.size * smallCanvasPx +
        (UPPER_REGISTERS.size - 1) * gapSmallToSmallPx +
        gapSmallToXPx

    val topSpacerPx = (screenPx / 2f - xMidBarInCanvasPx - aboveXPx).coerceAtLeast(0f)

    // The small rows centre "label + field" as one unit, so a labelled register
    // reads as the centred thing - X, having no label, centres its field alone.
    // ONE shift for every small row, half the widest label's block, so the
    // fields all stay aligned with each other; shorter labels just leave a
    // little slack to their left.
    val textMeasurer = rememberTextMeasurer()
    val smallFieldShiftPx = remember(metrics.density) {
        val widestLabelPx = (UPPER_REGISTERS + LOWER_REGISTERS).maxOf { label ->
            textMeasurer.measure(label, TextStyle(fontSize = TEXT_REGISTER_LABEL)).size.width
        }
        (widestLabelPx + LABEL_FIELD_CLEARANCE.value * metrics.density) / 2f
    }

    // One press must move the layout at least one pixel; below that the control
    // looks broken rather than fine-grained.
    val spacingStepUnits = maxOf(SPACING_STEP_UNITS, MIN_STEP_PX / unitPx)

    // Fit the digits to the field, then punctuate: the radix and the separators
    // are narrower than a digit position, so counting them would under-fill it.
    // The draw step trims by measurement whatever still overruns.
    val samples = SAMPLE_SETS[sampleIndex].mapValues { (_, text) ->

        val fitted = when {
            SAMPLE_FILLS_ROW[sampleIndex] ->
                buildString { while (length < FIELD_POSITIONS) append(text) }.take(FIELD_POSITIONS)
            // Left-justified, so an over-long value loses its right end.
            text.length > FIELD_POSITIONS -> text.take(FIELD_POSITIONS)
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
                    name, samples[name].orEmpty(), smallCellHeightPx, gapUnits,
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
                    samples["X"].orEmpty(), xCellHeightPx, gapUnits, LedPalette.LIT,
                    fieldLeftPx(xCellHeightPx, gapUnits, slantDegrees, descenderUnits, screenPx, xLeftInRootPx),
                    slantDegrees, descenderUnits
                )
            }

            // X down to the first of the lower registers: the row below is now the
            // smaller one, so this gap is the wider of the three.
            Spacer(Modifier.height(pxToDp(gapXToSmallPx.coerceAtLeast(0f), metrics.density)))
            overlapPx += minOf(gapXToSmallPx, 0f)

            for (name in LOWER_REGISTERS) {

                RegisterRow(
                    name, samples[name].orEmpty(), smallCellHeightPx, gapUnits,
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

                // Values live here rather than on the buttons: at this size a
                // button is only wide enough for its name.
                Text(
                    // cw: what ONE CELL WIDTH - the font's unit - renders as on
                    // this device, in dp. The size readout, since everything
                    // else on both lines is measured in it. th: the cell's full
                    // height in cell widths, cap plus descender - the derived
                    // consequence of the descender knob.
                    text = "cw %.1f dp  th %.2f".format(
                        unitPx / metrics.density,
                        TalkRpnFont.totalHeight(descenderUnits)
                    ),
                    color = LedPalette.LABEL,
                    fontSize = TEXT_READOUT,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    // The knobs, all in cell widths, named as the font names
                    // them: g DEFAULT_GAP, vg VGAP, dd DESCENDER_DEPTH - the
                    // values here copy straight back into TalkRpnFont.
                    text = "g%.2f vg%.2f dd%.2f".format(
                        gapUnits,
                        vgapUnits,
                        descenderUnits
                    ),
                    color = LedPalette.LABEL,
                    fontSize = TEXT_READOUT,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(GAP_SMALL))

                Row(modifier = Modifier.fillMaxWidth()) {

                    SplitButton("gap", Modifier.weight(1f),
                        onIncrease = {
                            gapUnits = (gapUnits + spacingStepUnits)
                                .coerceAtMost(GAP_UNITS_MAX)
                        },
                        onDecrease = {
                            gapUnits = (gapUnits - spacingStepUnits)
                                .coerceAtLeast(GAP_UNITS_MIN)
                        }
                    )

                    // width, not height: inside a Row it is the horizontal axis
                    // that needs the gap.
                    Spacer(Modifier.width(GAP_SMALL))

                    SplitButton("cw", Modifier.weight(1f),
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

                    SplitButton("vgap", Modifier.weight(1f),
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

                    SplitButton("dd", Modifier.weight(1f),
                        onIncrease = {
                            descenderUnits = (descenderUnits + spacingStepUnits)
                                .coerceAtMost(DESCENDER_UNITS_MAX)
                        },
                        onDecrease = {
                            descenderUnits = (descenderUnits - spacingStepUnits)
                                .coerceAtLeast(DESCENDER_UNITS_MIN)
                        }
                    )
                }

                Spacer(Modifier.height(GAP_SMALL))

                CompactButton("sample", Modifier.fillMaxWidth()) {
                    sampleIndex = (sampleIndex + 1) % SAMPLE_SETS.size
                }
            }
        }

        // Where the round glass ends. Drawn last, over everything, so it marks
        // controls as well as digits - and unconditionally, not just on the
        // emulator, because this screen IS the measuring instrument.
        GlassEdge()
    }
}

/** One of the smaller registers: a name at the left, the value in its field. */
@Composable
private fun RegisterRow(
    name: String,
    value: String,
    cellHeightPx: Float,
    gapUnits: Float,
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
        fieldLeftPx(cellHeightPx, gapUnits, slantDegrees, descenderUnits, screenPx, leftInRootPx) + fieldShiftPx

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
            drawRegister(value, cellHeightPx, gapUnits, color, rowFieldLeftPx, slantDegrees, descenderUnits)
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
        Text(label, color = LedPalette.LABEL, fontSize = TEXT_BUTTON)
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
 * Draws one register's value into its [FIELD_POSITIONS]-position field, whose
 * position 1 begins at [fieldLeftPx] - the caller centres the field on the
 * screen's vertical axis via [fieldLeftPx] (the function of the same name).
 *
 * Mantissa left-justified from position 1; exponent, when there is one,
 * right-justified into the last [EXPONENT_FIELD_POSITIONS] with no marker.
 *
 * Anything the font has no glyph for is dropped by the layout, so a gap in the
 * glyph table shows as missing ink rather than crashing. Radix and comma need
 * no cell of their own: they merge into the preceding cell's DP/COMMA element.
 */
private fun DrawScope.drawRegister(
    value: String,
    cellHeightPx: Float,
    gapUnits: Float,
    color: Color,
    fieldLeftPx: Float,
    slantDegrees: Float,
    descenderUnits: Float,
) {
    if (value.isEmpty() || cellHeightPx <= 0f) return

    val scale = cellHeightPx / TalkRpnFont.CELL_HEIGHT

    // The ink width of a run of n full-width digit positions, at this row's size.
    fun positionsPx(n: Int): Float = fieldUnits(n, gapUnits, slantDegrees, descenderUnits) * scale

    // THE FIELD: position 1 at [fieldLeftPx], the caller having centred it.
    val fieldRightPx = fieldLeftPx + positionsPx(FIELD_POSITIONS)

    // Split off the exponent. The marker never reaches the screen: the mantissa
    // is left-justified from position 1, the exponent right-justified into the
    // field's last EXPONENT_FIELD_POSITIONS, and the darkness between them is
    // the HP convention's blank.
    val markerAt = exponentMarkerAt(value)
    var mantissa = if (markerAt >= 0) value.take(markerAt) else value
    val exponent = if (markerAt >= 0) value.substring(markerAt + 1) else ""

    // The mantissa may not enter the exponent's positions. Overflow loses its
    // RIGHT end - it is the left-justified block.
    //
    // The check runs in the font's own UNITS, not pixels, so every row reaches
    // the same verdict for the same text - checked in pixels, the small rows'
    // different rounding could disagree with X's at the same gap. A TRAILING
    // radix or separator is not counted: it lives in the gap after its digit,
    // costs no position, and may poke past the field into the darkness.
    val mantissaMaxUnits = fieldUnits(
        if (exponent.isEmpty()) FIELD_POSITIONS else FIELD_POSITIONS - EXPONENT_FIELD_POSITIONS,
        gapUnits, slantDegrees, descenderUnits
    )

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
            val exponentInkPx = measureWidth(exponent, cellHeightPx, gapUnits, slantDegrees, descenderUnits)
            drawTalkRpnText(
                text = exponent,
                inkOrigin = Offset(fieldRightPx - exponentInkPx, 0f),
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
 * DSP - fixed-point to [places] after the radix, scientific when fixed form
 * cannot say anything useful: when the integer part outgrows the mantissa's
 * share of the field, or when the value is so small that every shown place
 * would be zero.
 *
 * A sketch of the real formatter, good enough to feed the samples. The real one
 * inherits its edge cases: rounding that carries into a new digit, exponents of
 * three digits, and the exact fixed-to-scientific switchover.
 */
private fun dsp(value: Double, places: Int = DSP_PLACES): String {

    if (value == 0.0) return "%.${places}f".format(0.0)

    val magnitude = abs(value)

    // How many digits fixed form needs left of the radix, sign aside.
    val integerDigits = maxOf(floor(log10(magnitude)).toInt() + 1, 1)

    // Everything the mantissa's share of the field must hold in fixed form:
    // integer digits, their group separators, the radix, and the places.
    val separators = if (GROUP_DIGITS) (integerDigits - 1) / GROUP_SIZE else 0
    val sign = if (value < 0) 1 else 0
    val fixedLength = sign + integerDigits + separators + 1 + places

    val tooBig = fixedLength > FIELD_POSITIONS - EXPONENT_FIELD_POSITIONS
    val tooSmall = magnitude < 10.0.pow(-places)

    if (!tooBig && !tooSmall) return "%.${places}f".format(value)

    val exponent = floor(log10(magnitude)).toInt()
    val mantissa = value / 10.0.pow(exponent)

    return "%.${places}fE%d".format(mantissa, exponent)
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

/**
 * How far a row's baseline sits above the bottom edge of its own canvas.
 *
 * The canvas holds the full ink: half a stroke of headroom, the cap, the
 * descender, and half a stroke below the descender bar. The baseline - segment
 * D's centreline - therefore sits the descender depth plus half a stroke above
 * the bottom. Scales with the row, hence the argument.
 */
private fun baselineToBottomPx(cellHeightPx: Float, descenderUnits: Float) =
    cellHeightPx * (descenderUnits + TalkRpnFont.STROKE / 2f) /
        TalkRpnFont.CELL_HEIGHT

/** The full ink height of a row's canvas, for chord limits and canvas sizing. */
private fun inkHeightPx(cellHeightPx: Float, descenderUnits: Float) =
    cellHeightPx * (TalkRpnFont.totalHeight(descenderUnits) + TalkRpnFont.STROKE) / TalkRpnFont.CELL_HEIGHT

/**
 * The Column gap that leaves two stacked rows exactly [vpitchPx] apart, baseline
 * to baseline. Depends on both rows, because they may be different sizes.
 */
private fun rowGapPx(vpitchPx: Float, abovePx: Float, belowPx: Float, descenderUnits: Float) =
    vpitchPx - baselineToBottomPx(abovePx, descenderUnits) -
        (belowPx - baselineToBottomPx(belowPx, descenderUnits))

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
    ((ceil(inkHeightPx(px, descenderUnits)) + 1f) / density).dp

