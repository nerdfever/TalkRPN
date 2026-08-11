package com.nerdfever.talkrpn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import kotlin.math.ceil

/*
 * Four rows of the same digits, so the two open font questions can be judged
 * against each other rather than one at a time.
 *
 * Down the page: the HP-01 reconstruction's bar position, then the QDSP-6064
 * datasheet's, each drawn twice - once at the stroke the font has been using and
 * once at the stroke the datasheet actually measures, which is half of it.
 *
 * Everything else is held identical, including digit height, so the only things
 * that differ between rows are the two being compared.
 */

// ---------------------------------------------------------------------------
// Tweakables.
// ---------------------------------------------------------------------------

/** What every row shows. Ten cells, so it fills the width at a readable size. */
private const val SAMPLE = "1234567890"

/** Digit height as a fraction of screen diameter, same for every row. */
private const val HEIGHT_FRACTION = 0.075f

/** Pitch, as a multiple of the font's own advance. */
private const val PITCH_FACTOR = 0.62f

/** Slant, held constant across all four so it cannot confound the comparison. */
private const val SLANT_DEGREES = 7.5f

/**
 * The stroke the font has used so far, and the one measured off HP's QDSP-6064
 * font drawing - 4.45% of ink height against the current 8.5%.
 */
private const val STROKE_CURRENT = 8.5f
private const val STROKE_MEASURED = 4.26f

/**
 * The lit-segment colour: the display's reddest red.
 *
 * See DisplayTestActivity for why - briefly, the real emitters peak at 655-660 nm,
 * which is outside every display gamut, and clipping to maximum saturation is the
 * closest reachable colour by a factor of two in dE2000.
 */
private val LED_RED = Color(0xFFFF0000)
private val LABEL = Color(0xFF8A8A8A)
private val BACKGROUND = Color(0xFF000000)

/**
 * Wide, because all four rows share one inset and the outer two sit on much
 * shorter chords than the middle. Sizing to the worst row keeps every row the
 * same width, which is what makes them comparable.
 */
private val SIDE_MARGIN = 30.dp
private val ROW_GAP = 10.dp
private val LABEL_SIZE = 9.sp

/** One row: a caption and the digits drawn with one combination of settings. */
private data class Row(val caption: String, val gFraction: Float, val stroke: Float)

private val ROWS = listOf(
    Row("HP-01  b>c   stroke 8.5", Hp01Font.G_FRACTION_HP01, STROKE_CURRENT),
    Row("HP-01  b>c   stroke 4.26", Hp01Font.G_FRACTION_HP01, STROKE_MEASURED),
    Row("QDSP   c>b   stroke 8.5", Hp01Font.G_FRACTION_QDSP, STROKE_CURRENT),
    Row("QDSP   c>b   stroke 4.26", Hp01Font.G_FRACTION_QDSP, STROKE_MEASURED),
)

class FontCompareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Comparing two nearly identical things needs the screen to stay up.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AppScaffold {
                FontCompareScreen()
            }
        }
    }
}

@Composable
private fun FontCompareScreen() {

    val metrics = LocalContext.current.resources.displayMetrics
    val screenPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()

    val cellHeightPx = HEIGHT_FRACTION * screenPx
    val advanceUnits = Hp01Font.ADVANCE * PITCH_FACTOR

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BACKGROUND)
            .padding(horizontal = SIDE_MARGIN),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        for (row in ROWS) {

            Text(row.caption, color = LABEL, fontSize = LABEL_SIZE)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((ceil(cellHeightPx) + 1f) / metrics.density).dp)
            ) {
                val inkWidth = Hp01Font.measureWidth(
                    SAMPLE, cellHeightPx, advanceUnits,
                    Hp01Font.PUNCTUATION_ADVANCE, SLANT_DEGREES
                )

                with(Hp01Font) {
                    drawHp01Text(
                        text = SAMPLE,
                        origin = Offset(size.width - inkWidth, 0f),
                        cellHeight = cellHeightPx,
                        color = LED_RED,
                        advance = advanceUnits,
                        punctuationAdvance = Hp01Font.PUNCTUATION_ADVANCE,
                        slantDegrees = SLANT_DEGREES,
                        stroke = row.stroke,
                        gFraction = row.gFraction
                    )
                }
            }

            Spacer(Modifier.height(ROW_GAP))
        }
    }

    // The glass edge, so nothing is judged on ink a round screen would cut.
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) / 2f
            drawCircle(
                color = Color(0xFF3A3A3A),
                radius = radius - 1f,
                center = Offset(size.width / 2f, size.height / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
        }
    }
}

