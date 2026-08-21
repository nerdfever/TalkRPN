package com.nerdfever.talkrpn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text

/*
 * The display-test RIG: [CalculatorDisplay] - the one shared display - under
 * a set of tuning knobs and sample values. It answers: does this layout fit
 * a 40 mm watch at a size anyone can read?
 *
 * Tap anywhere to show or hide the controls, so the layout can be judged
 * without buttons cluttering it. Whatever is tuned here becomes the settled
 * defaults in CalculatorDisplay.kt, which the calculator proper then shows.
 */

// ---------------------------------------------------------------------------
// Tweakables - the knobs' steps and ranges. The display geometry itself
// lives in CalculatorDisplay.kt; this file only decides how the knobs move.
//
// Lengths are in the font's cell widths unless the name says otherwise.
// Anything in device pixels carries "Px" in its name.
// ---------------------------------------------------------------------------

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
 * CalculatorDisplay turns it into per-seam row spacing.
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
 * The display grammar - radix, separators, the trailing-radix policy - lives
 * with [NumberFormatter], its single owner, where its whys are documented.
 * Aliased here so this screen's sample-dressing helpers read cleanly; an
 * alias cannot drift.
 */
private const val GROUP_DIGITS = NumberFormatter.GROUP_DIGITS
private const val GROUP_SIZE = NumberFormatter.GROUP_SIZE
private const val GROUP_SEPARATOR = NumberFormatter.GROUP_SEPARATOR
private const val RADIX = NumberFormatter.RADIX
private const val ALWAYS_SHOW_RADIX = NumberFormatter.ALWAYS_SHOW_RADIX

private val TEXT_BUTTON = 9.sp
private val GAP_SMALL = 4.dp
private val GAP_MEDIUM = 8.dp

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
    // Exponent stress: huge and tiny magnitudes, signs on mantissa and
    // exponent both, so every arm of the Eng block shows at once.
    SampleSet(
        "exp", fillsRow = false,
        mapOf(
            "T" to 123.456789e-88,
            "Z" to 123.456789e88,
            "Y" to -123.456789e-88,
            "X" to 123.456789e88,
            "LASTX" to -123.456789e88,
            "STO" to 9.876543e99,
        ).mapValues { (_, v) -> dsp(v) }
    ),
    // Exponents full of 1s - the zero-width digit - for judging how the
    // block's alignment survives them. RAW strings, not dsp(): Eng only emits
    // exponents in multiples of three, but the eventual SCI mode will show
    // any of these.
    SampleSet(
        "exp1s", fillsRow = false,
        mapOf(
            "T" to "1.23456E10",
            "Z" to "1.23456E11",
            "Y" to "1.23456E01",
            "X" to "1.23456E12",
            "LASTX" to "5.00000E-12",
            "STO" to "1.23456E18",
        )
    ),
)

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

    // The screen is round; the step floor below needs its diameter.
    val screenPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()

    // The knobs, each starting from the settled default it tunes.
    var gapUnits by remember { mutableStateOf(INITIAL_GAP_UNITS) }
    var heightFraction by remember { mutableStateOf(DEFAULT_HEIGHT_FRACTION) }
    var vgapUnits by remember { mutableStateOf(INITIAL_VGAP_UNITS) }

    // One fp per font, like the g knob: each font remembers its own tuning
    // and the knob binds to whichever is live.
    var segmentFieldPositions by remember { mutableStateOf(SEGMENT_FIELD_POSITIONS) }
    var dotFieldPositions by remember { mutableStateOf(DOT_FIELD_POSITIONS) }
    var sampleIndex by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(false) }

    // Which font draws the registers: the segment font, or the HDLS-1414 dot
    // matrix. Same field box either way, so the two are judged in one frame.
    var useDotFont by remember { mutableStateOf(false) }

    // The dot font's own gap, in blank columns - the g knob binds to this
    // while the dot font is live, and to gapUnits otherwise.
    var dotGapColumns by remember { mutableStateOf(Hdls1414Font.CHARACTER_GAP_COLUMNS) }

    // The live font's field size - what every consumer below means by fp.
    val fieldPositions = if (useDotFont) dotFieldPositions else segmentFieldPositions

    // The cyan field boxes: measuring aid, on by default, off to judge the
    // display as a display.
    var showFieldBoxes by remember { mutableStateOf(true) }

    // One press must move the layout at least one pixel's floor; below that the
    // control looks broken rather than fine-grained. The unit is X's cell
    // width in pixels, same as the display's own conversion.
    val unitPx = heightFraction * screenPx / TalkRpnFont.CELL_HEIGHT
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

        // ---- The display itself: the shared one, under this rig's knobs -----

        CalculatorDisplay(
            values = samples,
            heightFraction = heightFraction,
            gapUnits = gapUnits,
            vgapUnits = vgapUnits,
            fieldPositions = fieldPositions,
            useDotFont = useDotFont,
            dotGapColumns = dotGapColumns,
            showFieldBoxes = showFieldBoxes,
        )

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

                    // fp tunes the LIVE font's field size - how many of its
                    // cells each register's field holds. Whole cells only.
                    SplitButton("fp %d".format(fieldPositions), Modifier.weight(1f),
                        onIncrease = {
                            if (useDotFont) dotFieldPositions = (dotFieldPositions + 1)
                                .coerceAtMost(FIELD_POSITIONS_MAX)
                            else segmentFieldPositions = (segmentFieldPositions + 1)
                                .coerceAtMost(FIELD_POSITIONS_MAX)
                        },
                        onDecrease = {
                            if (useDotFont) dotFieldPositions = (dotFieldPositions - 1)
                                .coerceAtLeast(FIELD_POSITIONS_MIN)
                            else segmentFieldPositions = (segmentFieldPositions - 1)
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
 * The FINAL formatter, at this screen's default field: FIX at [places],
 * falling back to ENG, sized for the segment font's default field. The
 * output already carries its grouping and radix, so the sample pipeline's
 * dressing passes leave it untouched.
 *
 * The samples are baked once at start, which is why this call cannot follow
 * the fp knob or the live font; the engine's own calls will.
 */
private fun dsp(value: Double, places: Int = DSP_PLACES): String =
    NumberFormatter.format(
        value,
        NumberFormatter.Mode.FIX,
        places,
        NumberFormatter.FieldShape(SEGMENT_FIELD_POSITIONS, punctuationCostsCell = false),
    )

/**
 * Inserts a separator every [GROUP_SIZE] digits to the left of the radix.
 *
 * Only the integer part is grouped. Digits after the radix are not - grouping them
 * is a convention nobody uses on a calculator, and the exponent must not be touched
 * at all.
 */
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
