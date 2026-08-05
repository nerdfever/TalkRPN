package com.nerdfever.talkrpn

/*
 * Hp01Font â€” a reconstruction of the display font of the Hewlett-Packard HP-01
 * wrist instrument (1977), for drawing on a Compose Canvas.
 *
 * Original author: Claude Opus 5, 2026-08-02
 *
 * ---------------------------------------------------------------------------
 * Provenance
 * ---------------------------------------------------------------------------
 * Reconstructed by pixel measurement of photographs of physical HP-01 units,
 * cross-checked against the December 1977 Hewlett-Packard Journal and the
 * Panamatik HP-01 repair kit manual. The display module was HP Optoelectronics
 * Division project "Bugseye": monolithic LED digits immersed in epoxy on a
 * five-layer ceramic substrate, nine digit positions of nine elements each
 * (segments a-g, plus "col" upper dot and "dp" lower dot).
 *
 * Geometry is expressed in cell units with a cell height of 100. Confidence is
 * high on segment extents, slant and dot positions (multiple photographs in
 * agreement); lower on the hook radius and on segment a's free right end, each
 * of which only one digit in the available photographs can show.
 *
 * ---------------------------------------------------------------------------
 * Distinguishing features of this font
 * ---------------------------------------------------------------------------
 *  - Segments a and d turn through 90 degrees at their LEFT ends, so the left
 *    corners of the cell are formed by the horizontal bars curling into the
 *    vertical column rather than by the verticals meeting them. A 7 therefore
 *    carries a downward flag at its top left, and a 0 is asymmetric: rounded
 *    top-left and bottom-left, square-ish top-right and bottom-right.
 *  - b runs taller than f, and c runs lower than e, so the right side of a 4
 *    is visibly taller than its left.
 *  - b and c meet at the vertical midline, as do f and e, so a 1 and the left
 *    side of a 0 are unbroken strokes with no pinch. This is why the vertical
 *    segments use butt caps: a round cap at the midline would create a waist.
 *  - Two round dot elements per cell on the cell axis, symmetric about the
 *    middle bar. Lit together they read as a colon; the lower one alone is the
 *    decimal point. On the real HP-01 a dot occupies an entire digit position,
 *    which is why 3.141593 fills eight of the nine positions.
 *  - Everything is sheared 7.8 degrees to the right. The dots are positioned
 *    by the shear but NOT shaped by it: they are true circles. Shearing the
 *    circles turns them into ellipses, which is visibly wrong.
 */

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.sqrt
import kotlin.math.tan

object Hp01Font {

    // ---- Cell metrics, in cell units (cell height == 100) -------------------

    const val CELL_HEIGHT = 100f
    const val CELL_WIDTH = 62f
    const val STROKE = 8.5f
    const val SLANT_DEGREES = 7.8f

    /** Dot diameter is exactly twice the stroke width. */
    const val DOT_RADIUS = STROKE

    /**
     * Horizontal distance between successive cell origins on the real HP-01.
     * The original spacing is wide because every character, including a bare
     * decimal point, occupies a full digit position. Override this if you are
     * kerning.
     */
    const val ADVANCE = 130f

    /**
     * Advance for punctuation, as a fraction of [ADVANCE].
     *
     * Spans the whole range of reasonable choices with one number:
     *
     *   1.0   a whole digit position, as the HP-01 did
     *   0.35  narrow, the way a calculator LCD sets a comma or point
     *   0.0   drawn inside the preceding gap, costing nothing
     *
     * At 0 the mark must fit in the inter-digit gap, which is (ADVANCE - CELL_WIDTH)
     * and shrinks as the pitch tightens - so 0 and a tight pitch do not combine.
     */
    const val PUNCTUATION_ADVANCE = 0.35f

    /** Hook centreline radius. The tip extends exactly this far past the far
     *  edge of the bar it grows from. */
    private const val HOOK_R = 7.25f

    private val SHEAR = tan(Math.toRadians(SLANT_DEGREES.toDouble())).toFloat()

    /** Added to every x so that a sheared cell still starts at x = 0. */
    private val SHEAR_OFFSET = SHEAR * CELL_HEIGHT

    /** Total advance-independent ink width of one sheared cell. */
    val SHEARED_WIDTH = CELL_WIDTH + SHEAR_OFFSET

    /** Maps an unsheared cell coordinate to its sheared x. */
    private fun sx(x: Float, y: Float) = x - SHEAR * y + SHEAR_OFFSET

    // ---- Segment identity ---------------------------------------------------

    enum class Seg(val bit: Int) {
        A(1), B(2), C(4), D(8), E(16), F(32), G(64),
        DOT_UPPER(128), DOT_LOWER(256), COMMA_TAIL(512);
    }

    // Not const: an enum's property is not a compile-time constant, so this is
    // computed once at class-init instead.
    private val CAP_ROUND_MASK = Seg.A.bit or Seg.D.bit or Seg.G.bit

    // ---- Path construction --------------------------------------------------

    /**
     * A 90-degree circular arc is exact as a single cubic Bezier to within
     * 0.02%, and an affine shear maps a cubic to a cubic, so the sheared hook
     * is exact rather than approximated.
     */
    private const val KAPPA = 0.5522848f

    private fun path(build: Path.() -> Unit) = Path().apply(build)

    private fun Path.moveToCell(x: Float, y: Float) = moveTo(sx(x, y), y)
    private fun Path.lineToCell(x: Float, y: Float) = lineTo(sx(x, y), y)
    private fun Path.cubicToCell(
        x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float
    ) = cubicTo(sx(x1, y1), y1, sx(x2, y2), y2, sx(x3, y3), y3)

    // Hook control-point offset along each tangent.
    private val HOOK_K = KAPPA * HOOK_R      // 4.004

    // Centreline y of the three horizontal bars.
    private const val Y_A = STROKE / 2f              // 4.25
    private const val Y_G = 50.5f
    private const val Y_D = CELL_HEIGHT - STROKE / 2f // 95.75

    // Centreline x of the two vertical columns.
    private const val X_LEFT = STROKE / 2f                    // 4.25
    private const val X_RIGHT = CELL_WIDTH - STROKE / 2f      // 57.75

    // Where each horizontal bar's straight run ends and its hook begins.
    private val HOOK_START_X = X_LEFT + HOOK_R                // 11.5

    private val PATH_A = path {
        moveToCell(X_RIGHT, Y_A)
        lineToCell(HOOK_START_X, Y_A)
        cubicToCell(
            HOOK_START_X - HOOK_K, Y_A,
            X_LEFT, Y_A + HOOK_R - HOOK_K,
            X_LEFT, Y_A + HOOK_R
        )
    }

    private val PATH_D = path {
        moveToCell(X_RIGHT, Y_D)
        lineToCell(HOOK_START_X, Y_D)
        cubicToCell(
            HOOK_START_X - HOOK_K, Y_D,
            X_LEFT, Y_D - HOOK_R + HOOK_K,
            X_LEFT, Y_D - HOOK_R
        )
    }

    private val PATH_G = path {
        moveToCell(X_RIGHT, Y_G)
        lineToCell(X_LEFT, Y_G)
    }

    private val PATH_B = path { moveToCell(X_RIGHT, 4.25f); lineToCell(X_RIGHT, Y_G) }
    private val PATH_C = path { moveToCell(X_RIGHT, Y_G); lineToCell(X_RIGHT, 95f) }
    private val PATH_F = path { moveToCell(X_LEFT, 11.5f); lineToCell(X_LEFT, Y_G) }
    private val PATH_E = path { moveToCell(X_LEFT, Y_G); lineToCell(X_LEFT, 89f) }

    private val PATHS = mapOf(
        Seg.A to PATH_A, Seg.B to PATH_B, Seg.C to PATH_C, Seg.D to PATH_D,
        Seg.E to PATH_E, Seg.F to PATH_F, Seg.G to PATH_G
    )

    // Dot centres: sheared in POSITION, drawn as true circles.
    private const val DOT_AXIS_X = CELL_WIDTH / 2f   // 31
    private const val DOT_UPPER_Y = 23f
    private const val DOT_LOWER_Y = 78f

    private val DOT_UPPER_CENTRE = Offset(sx(DOT_AXIS_X, DOT_UPPER_Y), DOT_UPPER_Y)
    private val DOT_LOWER_CENTRE = Offset(sx(DOT_AXIS_X, DOT_LOWER_Y), DOT_LOWER_Y)

    /**
     * The comma's tail, hanging from the lower dot.
     *
     * Not on the original instrument, which had no comma element at all - the HP-01
     * had nine positions and no digit grouping. Added because a thousands separator
     * is wanted, and a comma has to be distinguishable from a decimal point at a
     * glance or it is worse than useless.
     *
     * Drawn as the same dot plus a short stroke down and to the left. The leftward
     * lean is what reads as a comma rather than as a smudge; it runs against the
     * font's rightward shear, which makes it more distinct, not less.
     */
    private const val COMMA_TAIL_DROP = 19f
    private const val COMMA_TAIL_LEFT = 7f

    /**
     * The tail tapers rather than being a constant-width stroke.
     *
     * A stroke at STROKE width is exactly half the dot's 17-unit diameter, so a
     * plain stroked tail meets the dot at a visible step - it reads as a dot with
     * something stuck to it. Starting at the dot's full width and narrowing to a
     * stroke reads as one mark.
     *
     * That means a filled outline rather than a stroked line, since a stroke has
     * one width by definition.
     */
    private val COMMA_TAIL_START_HALF = DOT_RADIUS        // meets the dot at its full width
    private val COMMA_TAIL_END_HALF = STROKE / 2f         // ends at a segment's width

    private val COMMA_TAIL_TIP_X = DOT_AXIS_X - COMMA_TAIL_LEFT
    private val COMMA_TAIL_TIP_Y = DOT_LOWER_Y + COMMA_TAIL_DROP

    private val PATH_COMMA_TAIL = run {

        // Unit vector along the tail, and its perpendicular - the taper is applied
        // across the tail, so the outline has to be built in its own frame.
        val alongX = COMMA_TAIL_TIP_X - DOT_AXIS_X
        val alongY = COMMA_TAIL_TIP_Y - DOT_LOWER_Y
        val length = sqrt(alongX * alongX + alongY * alongY)

        val acrossX = -alongY / length
        val acrossY = alongX / length

        path {
            moveToCell(
                DOT_AXIS_X + acrossX * COMMA_TAIL_START_HALF,
                DOT_LOWER_Y + acrossY * COMMA_TAIL_START_HALF
            )
            lineToCell(
                COMMA_TAIL_TIP_X + acrossX * COMMA_TAIL_END_HALF,
                COMMA_TAIL_TIP_Y + acrossY * COMMA_TAIL_END_HALF
            )
            lineToCell(
                COMMA_TAIL_TIP_X - acrossX * COMMA_TAIL_END_HALF,
                COMMA_TAIL_TIP_Y - acrossY * COMMA_TAIL_END_HALF
            )
            lineToCell(
                DOT_AXIS_X - acrossX * COMMA_TAIL_START_HALF,
                DOT_LOWER_Y - acrossY * COMMA_TAIL_START_HALF
            )
            close()
        }
    }

    /** Rounds off the tip, so the taper does not end in a flat chop. */
    private val COMMA_TAIL_TIP_CENTRE =
        Offset(sx(COMMA_TAIL_TIP_X, COMMA_TAIL_TIP_Y), COMMA_TAIL_TIP_Y)

    // ---- Character map ------------------------------------------------------

    private val S = Seg.entries.associateBy { it }   // keeps the bit maths readable below

    private fun mask(vararg s: Seg) = s.fold(0) { acc, seg -> acc or seg.bit }

    private val GLYPHS: Map<Char, Int> = mapOf(
        '0' to mask(Seg.A, Seg.B, Seg.C, Seg.D, Seg.E, Seg.F),
        '1' to mask(Seg.B, Seg.C),
        '2' to mask(Seg.A, Seg.B, Seg.G, Seg.E, Seg.D),
        '3' to mask(Seg.A, Seg.B, Seg.G, Seg.C, Seg.D),
        '4' to mask(Seg.F, Seg.G, Seg.B, Seg.C),
        '5' to mask(Seg.A, Seg.F, Seg.G, Seg.C, Seg.D),
        '6' to mask(Seg.A, Seg.F, Seg.G, Seg.E, Seg.C, Seg.D),
        '7' to mask(Seg.A, Seg.B, Seg.C),
        '8' to mask(Seg.A, Seg.B, Seg.C, Seg.D, Seg.E, Seg.F, Seg.G),
        '9' to mask(Seg.A, Seg.B, Seg.C, Seg.D, Seg.F, Seg.G),
        '-' to mask(Seg.G),
        '.' to mask(Seg.DOT_LOWER),
        ',' to mask(Seg.DOT_LOWER, Seg.COMMA_TAIL),
        ':' to mask(Seg.DOT_UPPER, Seg.DOT_LOWER),
        ' ' to 0
    )

    /**
     * Characters that get a narrower advance than a digit.
     *
     * On the real HP-01 these each consumed a whole digit position, which is why
     * 3.141593 filled eight of its nine. That is authentic and unaffordable here:
     * grouping 1,234,567 would spend three of ten positions on punctuation.
     *
     * How narrow is [PUNCTUATION_ADVANCE] - see there.
     */
    private val NARROW = setOf('.', ',', ':')

    /** True if [ch] takes the narrow advance rather than a full cell. */
    fun isNarrow(ch: Char) = ch in NARROW

    /** Segment mask for a character, or null if this font has no glyph for it. */
    fun maskFor(ch: Char): Int? = GLYPHS[ch]

    /** True if every character in [text] can be rendered. */
    fun canRender(text: String) = text.all { GLYPHS.containsKey(it) }

    // ---- Drawing ------------------------------------------------------------

    /**
     * Draws one character with its cell's top-left corner at [origin].
     *
     * [cellHeight] is the rendered height of the full cell in pixels; every
     * other dimension scales from it. An unknown character draws nothing.
     *
     * Marks ('.' ',' ':') are placed on the cell axis, which assumes a full-width
     * slot. If PUNCTUATION_ADVANCE is narrowing that slot, go through
     * [drawHp01Text] instead - it applies the centring correction.
     */
    fun DrawScope.drawHp01Glyph(
        ch: Char,
        origin: Offset,
        cellHeight: Float,
        color: Color
    ) {
        val m = GLYPHS[ch] ?: return
        if (m == 0) return
        val k = cellHeight / CELL_HEIGHT

        withTransform({
            translate(origin.x, origin.y)
            scale(k, k, pivot = Offset.Zero)
        }) {
            for ((seg, p) in PATHS) {
                if (m and seg.bit == 0) continue
                val cap =
                    if (seg.bit and CAP_ROUND_MASK != 0) StrokeCap.Round else StrokeCap.Butt
                drawPath(p, color, style = Stroke(width = STROKE, cap = cap))
            }
            if (m and Seg.DOT_UPPER.bit != 0) {
                drawCircle(color, DOT_RADIUS, DOT_UPPER_CENTRE)
            }
            if (m and Seg.DOT_LOWER.bit != 0) {
                drawCircle(color, DOT_RADIUS, DOT_LOWER_CENTRE)
            }
            if (m and Seg.COMMA_TAIL.bit != 0) {
                // Filled, not stroked - the outline already carries the taper.
                drawPath(PATH_COMMA_TAIL, color)
                drawCircle(color, COMMA_TAIL_END_HALF, COMMA_TAIL_TIP_CENTRE)
            }
        }
    }

    /**
     * Draws a string left to right. [advance] defaults to the original HP-01
     * cell pitch; pass something smaller once you start kerning.
     */
    fun DrawScope.drawHp01Text(
        text: String,
        origin: Offset,
        cellHeight: Float,
        color: Color,
        advance: Float = ADVANCE,
        punctuationAdvance: Float = PUNCTUATION_ADVANCE
    ) {
        val k = cellHeight / CELL_HEIGHT
        var x = origin.x

        for ((index, ch) in text.withIndex()) {

            val step = advanceFor(ch, advance, punctuationAdvance)

            // A mark is drawn at DOT_AXIS_X, the centre of a full 62-unit cell. That
            // is right only while it occupies a full slot; once its advance is
            // narrowed the same position sits hard against the following digit.
            //
            // Centre it in the whitespace it divides instead. The previous digit's
            // ink ends at (x - advance + CELL_WIDTH) and the next begins at
            // (x + step), so the midpoint works out to a shift of exactly
            // (step - advance) / 2 - independent of CELL_WIDTH, and self-correcting
            // as the pitch changes.
            //
            // Only when something precedes it: a leading mark divides nothing, and
            // shifting it left would just hang it off the end of the row.
            val shift = if (index > 0 && isNarrow(ch)) (step - advance) / 2f * k else 0f

            drawHp01Glyph(ch, Offset(x + shift, origin.y), cellHeight, color)

            x += step * k
        }
    }

    /**
     * How far the pen moves after [ch].
     *
     * Punctuation is the only thing that is not a full cell. At a punctuation
     * advance of 0 it draws inside the preceding gap and costs nothing at all; at
     * 1.0 it takes a whole position, as the original did.
     */
    private fun advanceFor(ch: Char, advance: Float, punctuationAdvance: Float) =
        if (isNarrow(ch)) advance * punctuationAdvance else advance

    /** Rendered width of [text], including the ink of the final cell. */
    fun measureWidth(
        text: String,
        cellHeight: Float,
        advance: Float = ADVANCE,
        punctuationAdvance: Float = PUNCTUATION_ADVANCE
    ): Float {
        if (text.isEmpty()) return 0f

        val k = cellHeight / CELL_HEIGHT

        // Every character but the last contributes its advance; the last
        // contributes its ink instead, since nothing follows it.
        val advances = text.dropLast(1).sumOf {
            advanceFor(it, advance, punctuationAdvance).toDouble()
        }.toFloat()

        return (advances + SHEARED_WIDTH) * k
    }
}

/*
 * ---------------------------------------------------------------------------
 * Usage
 * ---------------------------------------------------------------------------
 *
 * import com.nerdfever.talkrpn.Hp01Font
 * import com.nerdfever.talkrpn.Hp01Font.drawHp01Text
 *
 * @Composable
 * fun StackDisplay(x: String, modifier: Modifier = Modifier) {
 *     val lit = Color(0xFFFF3B24)
 *     Canvas(modifier.background(Color(0xFF17120F))) {
 *         drawHp01Text(
 *             text = x,
 *             origin = Offset(8f, 8f),
 *             cellHeight = size.height - 16f,
 *             color = lit
 *         )
 *     }
 * }
 *
 * Right-alignment, which is what an RPN display wants:
 *
 *     val w = Hp01Font.measureWidth(x, cellHeight)
 *     drawHp01Text(x, Offset(size.width - w - pad, pad), cellHeight, lit)
 *
 * ---------------------------------------------------------------------------
 * Scaling
 * ---------------------------------------------------------------------------
 * Every coordinate here is in cell units against CELL_HEIGHT = 100, and drawing
 * applies one uniform scale about the cell origin. Stroke widths, dot radii, the
 * hook radius and the comma taper are all cell units, and the shear is baked into
 * the paths in cell space at init, so it scales with them. Nothing in the draw
 * path holds an absolute pixel value and nothing is rounded, so geometry is
 * exactly proportional at any size.
 *
 * ---------------------------------------------------------------------------
 * Punctuation position: use drawHp01Text, not drawHp01Glyph
 * ---------------------------------------------------------------------------
 * On the original, '.' and ':' each consumed a whole cell, and the mark sits on
 * the cell axis at DOT_AXIS_X - the centre of a 62-unit cell. That is correct
 * only while the mark occupies a full-width slot.
 *
 * PUNCTUATION_ADVANCE narrows that slot. The mark does not move, so relative to
 * its own narrower slot it drifts right and crowds the following digit. This is
 * not a scaling fault - it happens identically at every size - it is cell-relative
 * and slot-relative positioning disagreeing once the two stop being proportional.
 *
 * drawHp01Text corrects it, shifting a narrow glyph by (step - advance) / 2 so the
 * mark lands in the middle of the whitespace it divides. **drawHp01Glyph does not**,
 * because a glyph has no business knowing about advances. So draw punctuation
 * through drawHp01Text; calling drawHp01Glyph directly for '.' ',' or ':' puts the
 * mark back on the cell axis.
 */

