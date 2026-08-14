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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import kotlin.math.ceil

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
// All physical lengths here are millimetres unless the name says otherwise.
// Anything in device pixels carries "Px" in its name; anything in the font's own
// coordinate system carries "Units". Mixing those three is the whole risk in this
// file, so each is named.
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
 * Expressed against the screen rather than in millimetres deliberately. The tuning
 * happens on an emulator that thinks it is a physically larger watch, so a value in
 * millimetres dialled in there would not carry across - a fraction of the screen
 * does, and lands on the real watch looking the same.
 */
private const val INITIAL_HEIGHT_FRACTION = 0.076f
private const val HEIGHT_FRACTION_MIN = 0.03f
private const val HEIGHT_FRACTION_MAX = 0.25f

/**
 * THE UNIT for the gap and the vpitch below: segment E/F to segment B/C - the
 * cell width - is 1.
 *
 * TalkRpnFont's header defines it; this is the same unit, used horizontally and
 * vertically alike, so a gap and a vpitch are directly comparable numbers. There
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
 * Independent of the height control: this moves spacing only. Sizing the cell to
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
 * VPITCH - vertical row-to-row distance, BASELINE TO BASELINE, in the same units.
 *
 * Baseline to baseline rather than gap-between-rows, so that it keeps meaning the
 * same thing when adjacent rows are different sizes - which they are here, since
 * every row but X is scaled by [SMALL_ROW_SCALE]. Measured always in the X row's
 * units, so a smaller row does not bring smaller units with it.
 *
 * A uniform GAP between unequal rows would put the baselines at unequal
 * distances, and the baselines are what the eye reads. Making the spacing uniform
 * means the gaps differ instead, which is the way round that looks even.
 *
 * Floor is the font's ink height, 2.62 units with the descenders - below that,
 * one row's descenders reach the row beneath.
 */
private val INITIAL_VPITCH_UNITS = TalkRpnFont.VPITCH
private const val VPITCH_UNITS_MAX = 4.5f

/**
 * The gap and the vpitch step by this, additively - they are lengths, not
 * proportions. A fortieth of a cell width, which is fine enough to tune with and
 * coarse enough that a press is always visible at a readable size.
 */
private const val SPACING_STEP_UNITS = 0.025f

/** Every proportional adjustment moves by this much per press. */
private const val ADJUST_STEP_FRACTION = 0.05f

/**
 * Slant, in degrees from vertical. The default is TalkRpnFont.SLANT_DEGREES.
 *
 * Stepped by a fixed amount rather than by a percentage: slant is the one
 * adjustment whose useful range includes zero, and a percentage step cannot move
 * away from zero or cross it. Upright is a legitimate choice here, so the control
 * has to be able to reach it and pass through.
 */
private const val SLANT_STEP_DEGREES = 0.5f
private const val SLANT_DEGREES_MIN = -6f
private const val SLANT_DEGREES_MAX = 24f

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

/**
 * The lit-segment colour: the display's reddest red, and deliberately so.
 *
 * These parts are GaAsP, and both datasheets state the peak outright - 655 nm for
 * HP's 5082-7400 bubble, 660 nm for the Siemens DL-3422. That is x = 0.728,
 * y = 0.272 in CIE 1931, which is outside EVERY display gamut: sRGB's red primary
 * falls short by 0.088 in x, Display P3 by 0.048, even Rec.2020 by 0.020.
 *
 * So there is no exact match and the question is which reachable colour is
 * closest. Clipping to maximum saturation gives this, at dE2000 7.8; desaturating
 * along the constant-dominant-wavelength line gives 0xFFFF0052, at 17.0. The
 * second is not wrong about the wavelength - it is genuinely 655 nm at 63% purity
 * - but that line leaves the gamut through the MAGENTA edge, so it lands on pink.
 *
 * Do not sample this from a photograph. Camera filters overlap, the red channel
 * clips almost at once on a lit segment, and the highlight rolls into whatever
 * green and blue were picking up - which is why photographs of these displays
 * show a white-pink core. The eye does not do that: its cones overlap too, but
 * with no hard clip and far more dynamic range, so a bright segment stays
 * saturated red. Matching a photograph reproduces the camera's failure.
 *
 * Free improvement available: this watch's OLED covers P3, so rendering in a
 * wide-gamut space would move the red primary from x = 0.64 to x = 0.68.
 */

/** Behind everything. Black costs no power on OLED. */

/**
 * Marks where the round glass ends.
 *
 * The emulator window is square and shows the whole framebuffer, so without this
 * there is no way to see what a round watch cuts off - a layout can look fine on
 * the emulator and lose a digit on the wrist, which is exactly what happened to
 * the top register.
 *
 * Greying the off-glass corners would be the obvious way to show it, but the app
 * cannot paint there; see the note at the draw site.
 */
private val GLASS_EDGE = Color(0xFF5A5A5A)
private val EDGE_RING_WIDTH = 1.dp

/** Register labels and annunciator text - dimmer than the digits, and not red. */

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
    // Something a real calculation would look like.
    mapOf(
        "T" to "0",
        "Z" to "12",
        "Y" to "1.4142136",
        "X" to "3.1415927",
        "LASTX" to "2.7182818",
        "STO" to "6.02e23",
    ),
    // Worst case: every segment lit, every cell full.
    mapOf(
        "T" to "8888888888",
        "Z" to "8888888888",
        "Y" to "8888888888",
        "X" to "8888888888",
        "LASTX" to "8888888888",
        "STO" to "8888888888",
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
)

/**
 * Whether each sample set should be grown to whatever number of cells now fits.
 *
 * The realistic set is fixed text, because its point is to look like a calculation.
 * The other two exist to fill the row, so they follow the row.
 */
private val SAMPLE_FILLS_ROW = listOf(false, true, true)

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

    // The one conversion from physical to pixel. xdpi is what the panel reports
    // about itself; it is approximate on some devices, which is why the readout
    // shows only one decimal place.
    val metrics = context.resources.displayMetrics
    val pixelsPerMm = metrics.pixelsPerMm()

    // The screen is round, and the layout has to know it. Taken as a circle whose
    // diameter is the narrower side.
    val screenPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    val insetPx = BEZEL_INSET.value * metrics.density

    // The two independent adjustments. Neither moves the other.
    var gapUnits by remember { mutableStateOf(INITIAL_GAP_UNITS) }
    var heightFraction by remember { mutableStateOf(INITIAL_HEIGHT_FRACTION) }

    var vpitchUnits by remember { mutableStateOf(INITIAL_VPITCH_UNITS) }
    var slantDegrees by remember { mutableStateOf(TalkRpnFont.SLANT_DEGREES) }
    var sampleIndex by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(false) }

    // Width available to a register row, captured during layout rather than inside
    // a Canvas: writing state during the draw phase schedules another draw, which
    // writes the state again.
    var rowWidthPx by remember { mutableStateOf(0) }

    // Height is now set directly, not inferred from a cell count.
    val xCellHeightPx = heightFraction * screenPx
    val smallCellHeightPx = xCellHeightPx * SMALL_ROW_SCALE

    // ---- Out of cell-width units and into pixels ------------------------------
    //
    // ONE conversion, so there is one place to look when a length is the wrong
    // size. The font's coordinates are cell widths and so are this screen's, so
    // there is nothing to convert between them.

    // Pixels per cell width, always at the X row's size - that is the reference
    // the vpitch is quoted in, so a smaller row must not redefine it.
    val unitPx = xCellHeightPx / TalkRpnFont.CELL_HEIGHT

    val vpitchPx = vpitchUnits * unitPx

    // ---- Row spacing, derived from vpitch ------------------------------------
    //
    // A Column stacks canvases and separates them with gaps, but vpitch is stated
    // baseline to baseline - so each gap is vpitch minus the ink already lying
    // between those two baselines: the tail of the row above, plus the whole of
    // the row below down to its own baseline.
    //
    // Three junctions, because X is a different size from its neighbours. This is
    // the part a single shared gap got wrong: equal gaps between unequal rows put
    // the baselines at unequal distances, which is what the eye actually reads.

    val gapSmallToSmallPx = rowGapPx(vpitchPx, smallCellHeightPx, smallCellHeightPx)
    val gapSmallToXPx = rowGapPx(vpitchPx, smallCellHeightPx, xCellHeightPx)
    val gapXToSmallPx = rowGapPx(vpitchPx, xCellHeightPx, smallCellHeightPx)

    // The tightest vpitch that still leaves every gap non-negative. Binding case
    // is a small row above X, since that is where the row below is tallest.
    val minVpitchUnits = minVpitchPx(smallCellHeightPx, xCellHeightPx) / unitPx

    // One press must move the layout at least one pixel; below that the control
    // looks broken rather than fine-grained.
    val spacingStepUnits = maxOf(SPACING_STEP_UNITS, MIN_STEP_PX / unitPx)

    // With both free, the cell count becomes the *result* rather than the input -
    // which is the more useful reading anyway, since the question was how many
    // digits fit at a size that can be read.
    //
    // A full-width glyph advances by its own cell plus one gap. That is an
    // ESTIMATE now that spacing is proportional - a row of 1s fits far more - but
    // the samples this sizes are all digits, where every glyph but 1 is full
    // width, and the draw step trims anything that still overruns.
    val scale = xCellHeightPx / TalkRpnFont.CELL_HEIGHT
    val fullCellAdvanceUnits = TalkRpnFont.CELL_WIDTH + gapUnits
    val cellsAcross =
        if (rowWidthPx <= 0 || scale <= 0f) 0
        else ((rowWidthPx / scale - TalkRpnFont.shearedWidth(slantDegrees)) / fullCellAdvanceUnits).toInt() + 1

    // Fit the digits first, then punctuate: the radix and the separators are all
    // narrower than a cell, so counting them as cells would under-fill the row.
    // The draw step trims whatever still does not fit.
    val samples = SAMPLE_SETS[sampleIndex].mapValues { (_, text) ->

        val fitted = when {
            cellsAcross <= 0 -> ""
            SAMPLE_FILLS_ROW[sampleIndex] ->
                buildString { while (length < cellsAcross) append(text) }.take(cellsAcross)
            // Right-aligned, so an over-long value loses its left end.
            text.length > cellsAcross -> text.takeLast(cellsAcross)
            else -> text
        }

        groupDigits(ensureRadix(fitted))
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
            verticalArrangement = Arrangement.Center
        ) {

            for ((index, name) in UPPER_REGISTERS.withIndex()) {

                RegisterRow(
                    name, samples[name].orEmpty(), smallCellHeightPx, gapUnits,
                    LedPalette.LIT, metrics.density, screenPx, insetPx, slantDegrees
                )

                // The last of these sits above X, which is taller, so it needs a
                // smaller gap to land on the same baseline-to-baseline distance.
                val gapPx =
                    if (index == UPPER_REGISTERS.lastIndex) gapSmallToXPx else gapSmallToSmallPx

                Spacer(Modifier.height(pxToDp(gapPx.coerceAtLeast(0f), metrics.density)))
            }

            // X carries no label: it is the largest row, it spans the full width,
            // and the whole point of "across the middle" is that it gets everything.
            // This is also the row whose width defines every other row's size, so it
            // is the one that measures itself.
            //
            // Sitting at the vertical centre, X is on the longest chord, so the
            // round-screen limit is never what binds it - but it is applied anyway
            // rather than assumed, since "X is centred" is a layout fact that could
            // stop being true.
            var xTopInRootPx by remember { mutableStateOf(0f) }
            var xLeftInRootPx by remember { mutableStateOf(0f) }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeightDp(xCellHeightPx, metrics.density))
                    .onSizeChanged { rowWidthPx = it.width }
                    .onGloballyPositioned {
                        xTopInRootPx = it.positionInRoot().y
                        xLeftInRootPx = it.positionInRoot().x
                    }
            ) {
                val limit =
                    chordRightEdgePx(xTopInRootPx, inkHeightPx(xCellHeightPx), screenPx, insetPx) - xLeftInRootPx

                drawRegister(
                    samples["X"].orEmpty(), xCellHeightPx, gapUnits, LedPalette.LIT,
                    limit.coerceAtMost(size.width), slantDegrees
                )
            }

            // X down to the first of the lower registers: the row below is now the
            // smaller one, so this gap is the wider of the three.
            Spacer(Modifier.height(pxToDp(gapXToSmallPx.coerceAtLeast(0f), metrics.density)))

            for (name in LOWER_REGISTERS) {

                RegisterRow(
                    name, samples[name].orEmpty(), smallCellHeightPx, gapUnits,
                    LedPalette.LIT, metrics.density, screenPx, insetPx, slantDegrees
                )

                // The trailing one has no row beneath it - it is just the padding
                // ahead of the annunciators, and matching the others keeps it even.
                Spacer(Modifier.height(pxToDp(gapSmallToSmallPx.coerceAtLeast(0f), metrics.density)))
            }

            Spacer(Modifier.height(ANNUNCIATOR_GAP))

            // ---- Annunciators ------------------------------------------------
            //
            // Not real segments, as on an HP-42S - just a reserved screen region
            // that draws one word or the other. Cheating, deliberately.
            Row(horizontalArrangement = Arrangement.Center) {
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
                    // The ADVANCE for a full-width pair - what two full-width glyphs
                    // sit apart at this gap, which is the widest any pair gets.
                    // Narrow glyphs advance by less, so it is an upper bound not a grid.
                    text = "%.1f mm  %d cells  adv %.2f".format(
                        xCellHeightPx / pixelsPerMm,
                        cellsAcross,
                        TalkRpnFont.CELL_WIDTH + gapUnits
                    ),
                    color = LedPalette.LABEL,
                    fontSize = TEXT_READOUT,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    // Gap and vpitch in cell-width units, so they read against
                    // each other and against the font's own numbers. Height stays
                    // a screen fraction: it is the one quantity that has to be
                    // physical.
                    text = "g%.2f v%.2f h%.1f%% s%.1f".format(
                        gapUnits,
                        vpitchUnits,
                        heightFraction * 100f,
                        slantDegrees
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

                    SplitButton("height", Modifier.weight(1f),
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

                    SplitButton("vpitch", Modifier.weight(1f),
                        onIncrease = {
                            vpitchUnits = (vpitchUnits + spacingStepUnits)
                                .coerceAtMost(VPITCH_UNITS_MAX)
                        },
                        onDecrease = {
                            // Stops where the rows would start to touch, rather
                            // than at a guessed constant.
                            vpitchUnits = (vpitchUnits - spacingStepUnits)
                                .coerceAtLeast(minVpitchUnits)
                        }
                    )

                    Spacer(Modifier.width(GAP_SMALL))

                    SplitButton("slant", Modifier.weight(1f),
                        onIncrease = {
                            slantDegrees = (slantDegrees + SLANT_STEP_DEGREES)
                                .coerceAtMost(SLANT_DEGREES_MAX)
                        },
                        onDecrease = {
                            slantDegrees = (slantDegrees - SLANT_STEP_DEGREES)
                                .coerceAtLeast(SLANT_DEGREES_MIN)
                        }
                    )
                }

                Spacer(Modifier.height(GAP_SMALL))

                CompactButton("sample", Modifier.fillMaxWidth()) {
                    sampleIndex = (sampleIndex + 1) % SAMPLE_SETS.size
                }
            }
        }

        // ---- Where the round glass ends -------------------------------------
        //
        // Drawn last, over everything, so it marks controls as well as digits.
        Canvas(modifier = Modifier.fillMaxSize()) {

            val radius = minOf(size.width, size.height) / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)

            // No attempt to fill the off-glass corners: the app cannot paint
            // there. The platform's round-screen mask composites above app
            // content, so anything drawn out there comes back black. The
            // boundary is marked from the inside instead.

            // One ring, at the glass edge, drawn just inside it so the mask does
            // not eat it. Only this line is worth showing - content outside it
            // does not exist on the watch. A second ring at the bezel inset reads
            // as an edge with something extra outside it rather than as a
            // boundary and its margin; the inset is a layout detail.
            drawCircle(
                color = GLASS_EDGE,
                radius = radius - EDGE_RING_WIDTH.toPx() / 2f,
                center = centre,
                style = Stroke(width = EDGE_RING_WIDTH.toPx())
            )
        }
    }
}

/**
 * How far right ink may go, for a row occupying [topPx] to [topPx] + [heightPx].
 *
 * The screen is a circle, so the usable width depends on how far the row sits from
 * the vertical centre - a row near the top or bottom is on a short chord, and
 * ignoring that slices digits off the outer registers while the middle ones look
 * perfect.
 *
 * The binding point is whichever of the row's two edges is further from centre,
 * since that is where the circle has closed in most.
 */
private fun chordRightEdgePx(topPx: Float, heightPx: Float, screenPx: Float, insetPx: Float): Float {

    val radius = screenPx / 2f
    val centre = radius

    val worstOffset = maxOf(
        kotlin.math.abs(topPx - centre),
        kotlin.math.abs(topPx + heightPx - centre)
    )

    // Off the circle entirely: nothing is drawable, so give back the centre line.
    val halfChordSquared = radius * radius - worstOffset * worstOffset
    if (halfChordSquared <= 0f) return centre

    return centre + kotlin.math.sqrt(halfChordSquared) - insetPx
}

/** The mirror of [chordRightEdgePx]: how far left ink may go on the same row. */
private fun chordLeftEdgePx(topPx: Float, heightPx: Float, screenPx: Float, insetPx: Float): Float {

    val radius = screenPx / 2f
    val centre = radius

    return centre - (chordRightEdgePx(topPx, heightPx, screenPx, insetPx) - centre)
}

/** One of the smaller registers: a name at the left, digits right-aligned. */
@Composable
private fun RegisterRow(
    name: String,
    value: String,
    cellHeightPx: Float,
    gapUnits: Float,
    color: Color,
    density: Float,
    screenPx: Float,
    insetPx: Float,
    slantDegrees: Float
) {
    // Where this row ended up on screen, so the chord at its height can be found.
    var topInRootPx by remember { mutableStateOf(0f) }
    var leftInRootPx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                topInRootPx = it.positionInRoot().y
                leftInRootPx = it.positionInRoot().x
            }
    ) {

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeightDp(cellHeightPx, density))
        ) {
            // Convert the screen-space limit into this Canvas's own coordinates.
            val limit = chordRightEdgePx(topInRootPx, inkHeightPx(cellHeightPx), screenPx, insetPx) - leftInRootPx

            drawRegister(value, cellHeightPx, gapUnits, color, limit.coerceAtMost(size.width), slantDegrees)
        }

        // The label sits over the left end of the row. A smaller register is
        // narrower than the full width by exactly the scale factor, so there is
        // always dark space there for it.
        //
        // Pushed inward by the same chord logic as the digits, on the other side:
        // the topmost register's label sat at x = 12 while the circle had already
        // closed in to x = 27, so it was invisible on a round screen while looking
        // perfectly fine on a square emulator.
        val labelIndentPx =
            (chordLeftEdgePx(topInRootPx, inkHeightPx(cellHeightPx), screenPx, insetPx) - leftInRootPx)
                .coerceAtLeast(0f)

        Text(
            text = name,
            color = LedPalette.LABEL,
            fontSize = TEXT_REGISTER_LABEL,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = pxToDp(labelIndentPx, density))
        )
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
 * Draws one register's digits, right-aligned to [widthPx].
 *
 * Right alignment is what an RPN display does, and it is also what makes the
 * decimal points line up down the stack.
 */
private fun DrawScope.drawRegister(
    value: String,
    cellHeightPx: Float,
    gapUnits: Float,
    color: Color,
    widthPx: Float,
    slantDegrees: Float
) {
    if (value.isEmpty() || cellHeightPx <= 0f) return

    // Anything the font has no glyph for draws as a blank cell inside the text
    // helper, so a gap in the glyph table is visible rather than silently
    // swallowed. Radix and comma are legal without glyph entries of their own:
    // they merge into the preceding cell's DP/COMMA element.
    var drawable = value

    // Separators are added after the cell count is worked out, so a grouped value
    // can be a little wider than the row. Drop from the left - the display is
    // right-aligned, so that is the end that would run off the screen anyway.
    while (drawable.isNotEmpty() &&
        TalkRpnFont.measureWidth(drawable, cellHeightPx, gapUnits, slantDegrees) > widthPx
    ) {
        drawable = drawable.drop(1)
    }

    if (drawable.isEmpty()) return

    val inkWidth = TalkRpnFont.measureWidth(drawable, cellHeightPx, gapUnits, slantDegrees)

    // The font takes the ink's own top-left corner, so the row simply hangs from
    // the top of its canvas - the stroke's overhang is inside the measurement.
    with(TalkRpnFont) {
        drawTalkRpnText(
            text = drawable,
            inkOrigin = Offset(widthPx - inkWidth, 0f),
            cellHeight = cellHeightPx,
            color = color,
            gap = gapUnits,
            slantDegrees = slantDegrees
        )
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
 * before the exponent marker rather than at the end - "6e23" becomes "6.e23", not
 * "6e23.".
 */
private fun ensureRadix(value: String): String {

    if (!ALWAYS_SHOW_RADIX || value.isEmpty()) return value

    // Split the mantissa from any exponent.
    val exponentAt = value.indexOfFirst { it in EXPONENT_MARKERS }
    val mantissa = if (exponentAt >= 0) value.substring(0, exponentAt) else value
    val exponent = if (exponentAt >= 0) value.substring(exponentAt) else ""

    if (mantissa.contains(RADIX)) return value

    return mantissa + RADIX + exponent
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
private fun baselineToBottomPx(cellHeightPx: Float) =
    cellHeightPx * (TalkRpnFont.TOTAL_HEIGHT - TalkRpnFont.CELL_HEIGHT + TalkRpnFont.STROKE / 2f) /
        TalkRpnFont.CELL_HEIGHT

/** Ink from the canvas top down to the baseline: headroom plus the cap height. */
private fun baselineFromTopPx(cellHeightPx: Float) =
    cellHeightPx * (1f + TalkRpnFont.STROKE / 2f / TalkRpnFont.CELL_HEIGHT)

/** The full ink height of a row's canvas, for chord limits and canvas sizing. */
private fun inkHeightPx(cellHeightPx: Float) =
    cellHeightPx * (TalkRpnFont.TOTAL_HEIGHT + TalkRpnFont.STROKE) / TalkRpnFont.CELL_HEIGHT

/**
 * The Column gap that leaves two stacked rows exactly [vpitchPx] apart, baseline
 * to baseline. Depends on both rows, because they may be different sizes.
 */
private fun rowGapPx(vpitchPx: Float, abovePx: Float, belowPx: Float) =
    vpitchPx - baselineToBottomPx(abovePx) - (belowPx - baselineToBottomPx(belowPx))

/**
 * The tightest vpitch that keeps every gap in the column at zero or more - that
 * is, the point at which rows start to touch.
 *
 * Checked across all three junctions rather than assumed, since which one binds
 * depends on [SMALL_ROW_SCALE]. It is the small-above-X junction as things stand,
 * because that is where the row below is tallest.
 */
private fun minVpitchPx(smallPx: Float, xPx: Float) = maxOf(
    baselineToBottomPx(smallPx) + baselineFromTopPx(smallPx),
    baselineToBottomPx(smallPx) + baselineFromTopPx(xPx),
    baselineToBottomPx(xPx) + baselineFromTopPx(smallPx)
)

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
private fun canvasHeightDp(px: Float, density: Float) = ((ceil(inkHeightPx(px)) + 1f) / density).dp













