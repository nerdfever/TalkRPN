package com.nerdfever.talkrpn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold

/*
 * The display-test RIG: [CalculatorDisplay] - the one shared display - under
 * the shared tuning panel ([DisplayTuningPanel]) and this screen's own
 * sample values. It answers: does this layout fit a 40 mm watch at a size
 * anyone can read?
 *
 * Tap anywhere to show or hide the controls, so the layout can be judged
 * without buttons cluttering it. Whatever is tuned here becomes the settled
 * defaults in CalculatorDisplay.kt, which the calculator proper then shows.
 * The calculator carries the same panel, so final tuning can also happen
 * against live values; this rig's edge is the SAMPLE sets - worst cases a
 * calculation would take ages to stumble into.
 */

// ---------------------------------------------------------------------------
// Tweakables.
// ---------------------------------------------------------------------------

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

    // The shared knobs, with the rig's one difference: the field boxes
    // start visible, because this screen exists for fitting work.
    val knobs = remember { DisplayKnobs().apply { showFieldBoxes = true } }

    var sampleIndex by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(false) }

    // Fit the digits to the field, then punctuate: the radix and the separators
    // are narrower than a digit position, so counting them would under-fill it.
    // The draw step trims by measurement whatever still overruns.
    val samples = SAMPLE_SETS[sampleIndex].values.mapValues { (_, text) ->

        val fitted = when {
            SAMPLE_SETS[sampleIndex].fillsRow ->
                buildString { while (length < knobs.fieldPositions) append(text) }
                    .take(knobs.fieldPositions)
            // Fixed TEXT is trimmed by characters, left-justified, losing its
            // right end. NUMBERS are not: drawRegister trims them by
            // measurement and owns the exponent block, and a character count
            // here would eat an exponent's last digit - "602.0E21" is eight
            // characters but only seven positions, since the marker, radix
            // and separators never reach the screen.
            !isNumeric(text) && text.length > knobs.fieldPositions -> text.take(knobs.fieldPositions)
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

        // ---- The display itself: the shared one, under the shared knobs -----

        CalculatorDisplay(values = samples, knobs = knobs)

        // ---- Controls, on top, only when asked for --------------------------
        //
        // Centred rather than sitting at the bottom. On a round screen the
        // bottom is where the glass has almost run out, and a panel down
        // there loses its outer buttons to the curve.
        if (showControls) {

            DisplayTuningPanel(knobs, Modifier.align(Alignment.Center)) {

                Spacer(Modifier.height(PANEL_ROW_GAP))

                // The rig's own extra: cycle the sample values. Named for
                // the set it is SHOWING, not the one a tap brings.
                CompactButton(
                    "sample: " + SAMPLE_SETS[sampleIndex].name, Modifier.fillMaxWidth()
                ) {
                    sampleIndex = (sampleIndex + 1) % SAMPLE_SETS.size
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
