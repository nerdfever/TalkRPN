package com.nerdfever.talkrpn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * TalkRpnFont - the 28-element display cell.
 *
 * Styled after the HP-01 (1977), but with the segment count of a DL-3422 rather
 * than the HP-01's seven, so the display can show text as well as digits. Drawn
 * from Dave's dimensioned sketch, "TalkRPN font (not to scale).pdf".
 *
 * ---------------------------------------------------------------------------
 * Coordinate system
 * ---------------------------------------------------------------------------
 * Everything here is a CENTRELINE. Stroke width and slant are applied at render
 * time, so a segment is a zero-width hairline until it is drawn. Origin is the
 * top-left centreline corner; x runs right, y runs down.
 *
 * The units are the HP-01's own centreline geometry - a 53.5 x 91.5 box -
 * rescaled so the cap height is exactly 100:
 *
 *     K = 100 / 91.5 = 1.0929
 *     53.5 * K = 58.47   the cell width
 *     26.75 * K = 29.235 the centre axis
 *     18.75 * K = 20.49  the upper colon dot
 *     73.75 * K = 80.60  the lower colon dot
 *     7.25 * K = 7.92    the hook radius
 *     8.5 * K = 9.29     the stroke
 *
 * Note this differs from Hp01Font, whose coordinates are ink-box based and so
 * carry an extra STROKE/2 on every axis. The mapping is
 *
 *     talkRpn = (hp01 - STROKE/2) * K
 *
 * ---------------------------------------------------------------------------
 * Segments
 * ---------------------------------------------------------------------------
 *  A1 A2   top bar, split at the centre axis
 *  A3 A4   ALTERNATIVE top-left corners, never both. A4 runs the bar straight on
 *          to x = 0, giving a square corner. A3 instead turns it through 90
 *          degrees down into the left column - the HP-01's signature, which is
 *          why a 7 carries a downward flag at its top left.
 *  B  C    right column, upper and lower
 *  D1 D2   bottom bar, split
 *  D3 D4   alternative bottom-left corners, mirroring A3/A4
 *  F1 F2   upper-left column. F1 is the main run, stopping where A3 lands. F2 is
 *          the short stub above it, carrying the column to y = 0 - lit when the
 *          corner is square, dark when it is hooked, and dark for a 4 so that its
 *          left side sits lower than its right, as on the HP-01.
 *  E1 E2   lower-left column, mirroring F1/F2. E2 is dark for a 2, the one hooked
 *          digit that also lights E.
 *  G1 G2   middle bar, split
 *  H  I    upper diagonals, left and right
 *  J       lower-left "\" diagonal. Deliberately without a mirror on the right -
 *          no ASCII glyph needs one there.
 *  K  L    the lower halves of the two through-diagonals
 *  M       centre descender
 *  N  O    descender bar, split
 *  P  Q    centre column, upper and lower
 *  COL1    upper colon dot; also dots a lower-case i
 *  COL2    lower colon dot, used only by the colon
 *  DP      decimal point
 *  COMMA   comma, a dot with a tail, at the decimal point's position
 *
 * ---------------------------------------------------------------------------
 * No gaps
 * ---------------------------------------------------------------------------
 * Unlike the sketch and unlike a real DL-3422, adjacent segments meet flush.
 * Every endpoint below is shared with its neighbours, so a lit run reads as one
 * continuous stroke.
 */

object TalkRpnFont {

    // ---- Cell metrics, in cell units ----------------------------------------

    /** Top centreline to baseline centreline. */
    const val CELL_HEIGHT = 100f

    /** Left column to right column, centre to centre. */
    const val CELL_WIDTH = 58.47f

    /** Including the descender: baseline to the N/O bar. */
    const val TOTAL_HEIGHT = 144f

    /** Rendered stroke width. The HP-01's 8.5 rescaled by 100/91.5. */
    const val STROKE = 9.29f

    /** Rightward lean, in degrees. */
    const val SLANT_DEGREES = 7.5f

    /** Dot diameter is twice the stroke, as on the HP-01. */
    const val DOT_RADIUS = STROKE

    /**
     * Distance between successive cell origins.
     *
     * The HP-01's own 130, rescaled. It is very wide, because on the real
     * instrument every character occupied a full digit position. Expect to
     * tighten this - but see the note on DP_X below before going far.
     */
    const val ADVANCE = 142.08f

    /** Radius of the two hooks, measured on their centreline. */
    const val HOOK_R = 7.92f

    // ---- The grid the segments hang from ------------------------------------

    private const val X_LEFT = 0f
    private const val X_MID = CELL_WIDTH / 2f          // 29.235
    private const val X_RIGHT = CELL_WIDTH             // 58.47

    private const val Y_TOP = 0f
    private const val Y_MID = CELL_HEIGHT / 2f         // 50
    private const val Y_BASE = CELL_HEIGHT             // 100
    private const val Y_DESC = TOTAL_HEIGHT            // 144

    /** Where the top hook lands on the left column, and where the bottom one leaves it. */
    private const val Y_F_TOP = HOOK_R                 // 7.92
    private const val Y_E_BOTTOM = Y_BASE - HOOK_R     // 92.08

    /** Where each horizontal bar's straight run ends and its hook begins. */
    private const val X_HOOK_START = HOOK_R            // 7.92

    /** The mirror point on the right, where the parenthesis arcs turn. */
    private const val X_HOOK_END_R = CELL_WIDTH - HOOK_R   // 50.55

    /**
     * Control-point offset for a quarter-circle as one cubic Bezier:
     * (4/3)tan(pi/8) x radius. Exact to within 0.02%.
     */
    private val HOOK_K = (4.0 / 3.0 * tan(Math.PI / 8.0)).toFloat() * HOOK_R

    /** The descender bar is inset from the columns, symmetrically. */
    private const val X_N_LEFT = 3.74f
    private const val X_O_RIGHT = 54.72f

    // ---- Dots ---------------------------------------------------------------

    private const val DOT_AXIS_X = X_MID               // 29.235
    private const val COL1_Y = 20.49f
    private const val COL2_Y = 80.60f

    /**
     * The decimal point and comma sit outside the cell, to the right.
     *
     * Taken from an HP datasheet and expected to move. Note the constraint: this
     * x is 28.17 past the right column, so it lands in the gap between cells. It
     * only stays clear of the next glyph while ADVANCE exceeds roughly
     * DP_X + DOT_RADIUS. At the authentic 142.08 there is room to spare; tighten
     * the pitch far and the decimal point will collide.
     */
    private const val DP_X = 86.64f
    private const val DP_Y = 119.08f

    /** The comma's tail, relative to its dot. Carried over from the HP-01 font. */
    private const val COMMA_TAIL_DROP = 20.76f         // 19 * K
    private const val COMMA_TAIL_LEFT = 7.65f          // 7 * K

    // ---- Slant --------------------------------------------------------------

    private val SHEAR = tan(Math.toRadians(SLANT_DEGREES.toDouble())).toFloat()

    /** Added to every x so a slanted cell still starts at x = 0. */
    private val SHEAR_OFFSET = SHEAR * TOTAL_HEIGHT

    /** Ink width of one slanted cell, before stroke is added. */
    val SHEARED_WIDTH = CELL_WIDTH + SHEAR_OFFSET

    /** Maps an upright cell coordinate to its slanted x. */
    private fun sx(x: Float, y: Float) = x - SHEAR * y + SHEAR_OFFSET

    // ---- Segment identity ---------------------------------------------------

    /**
     * 33 elements, so the mask is a Long. It was an Int until the parentheses:
     * A5 and D5 were elements 32 and 33, and crossing that line was a deliberate
     * decision (see DESIGN.md), not drift.
     *
     * A5 and D5 are the right-hand parenthesis halves. Each bundles a bar stub,
     * the corner arc and a column stub into ONE element, because the right side
     * has no shortened bars or columns for a bare arc to join - A2, B, C and D2
     * all run square into the corner. Splitting them properly would cost six
     * elements; bundling costs two, and loses nothing while ')' is the only
     * user.
     */
    enum class Seg {
        A1, A2, A3, A4, A5,
        B, C,
        D1, D2, D3, D4, D5,
        E1, E2, F1, F2,
        G1, G2,
        H, I, J, K, L,
        M, N, O,
        P, Q,
        COL1, COL2, COL2_TAIL, DP, COMMA;

        val bit: Long get() = 1L shl ordinal
    }

    /** Every segment lit - the display self-test, and what to draw to check geometry. */
    val ALL_SEGMENTS: Long = Seg.entries.fold(0L) { acc, s -> acc or s.bit }

    /**
     * The cell's own boundary, for diagnosing layout.
     *
     * A parallelogram, not a rectangle: the slant leans the whole cell, so the
     * bounds lean with it. Drawn from the centreline corners, which means ink
     * overhangs it by STROKE/2 all round - that is expected, and seeing by how
     * much is part of the point.
     */
    val CELL_OUTLINE: Path = path {
        moveToCell(X_LEFT, Y_TOP)
        lineToCell(X_RIGHT, Y_TOP)
        lineToCell(X_RIGHT, Y_DESC)
        lineToCell(X_LEFT, Y_DESC)
        close()
    }

    /** Where the baseline sits inside those bounds - the descender hangs below. */
    val CELL_BASELINE: Path = path {
        moveToCell(X_LEFT, Y_BASE)
        lineToCell(X_RIGHT, Y_BASE)
    }

    // ---- Path construction --------------------------------------------------

    private fun path(build: Path.() -> Unit) = Path().apply(build)

    private fun Path.moveToCell(x: Float, y: Float) = moveTo(sx(x, y), y)
    private fun Path.lineToCell(x: Float, y: Float) = lineTo(sx(x, y), y)
    private fun Path.cubicToCell(
        x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float
    ) = cubicTo(sx(x1, y1), y1, sx(x2, y2), y2, sx(x3, y3), y3)

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) = path {
        moveToCell(x1, y1)
        lineToCell(x2, y2)
    }

    /**
     * A circular arc as a single cubic Bezier.
     *
     * Exact to within 0.02% for a quarter turn and far better for the 45-degree
     * pieces used here. An affine shear maps a cubic to a cubic, so slanting the
     * result is exact rather than approximated - which is why the arc is built in
     * cell coordinates and sheared point by point, not drawn and then transformed.
     *
     * Angles are in radians, measured from the positive x axis, y downward.
     */
    private fun arc(
        centreX: Float, centreY: Float, radius: Float,
        fromAngle: Float, toAngle: Float
    ) = path {

        val sweep = toAngle - fromAngle

        // Control-point offset along each tangent for a circular arc of this sweep.
        val k = 4f / 3f * tan(sweep / 4f) * radius

        val x0 = centreX + radius * cos(fromAngle)
        val y0 = centreY + radius * sin(fromAngle)
        val x3 = centreX + radius * cos(toAngle)
        val y3 = centreY + radius * sin(toAngle)

        // Tangent direction at each end, rotated 90 degrees from the radius.
        val x1 = x0 - k * sin(fromAngle)
        val y1 = y0 + k * cos(fromAngle)
        val x2 = x3 + k * sin(toAngle)
        val y2 = y3 - k * cos(toAngle)

        moveToCell(x0, y0)
        cubicToCell(x1, y1, x2, y2, x3, y3)
    }

    // ---- The corners --------------------------------------------------------
    //
    // A3 and A4 are two ways to finish the same corner, and are never lit
    // together. A4 continues the top bar flat to x = 0. A3 instead sweeps a
    // quarter circle centred at (HOOK_R, HOOK_R), leaving the bar at (HOOK_R, 0)
    // and arriving at the left column at (0, HOOK_R).
    //
    // Angles: -90 degrees points up to the top bar, +90 down to the bottom bar,
    // and 180 points left to the column.

    private const val ANGLE_UP = (-Math.PI / 2).toFloat()
    private const val ANGLE_DOWN = (Math.PI / 2).toFloat()
    private const val ANGLE_LEFT = Math.PI.toFloat()

    private val PATHS: Map<Seg, Path> = mapOf(

        // Top bar, and the two ways to finish its left corner.
        Seg.A1 to line(X_MID, Y_TOP, X_HOOK_START, Y_TOP),
        Seg.A2 to line(X_MID, Y_TOP, X_RIGHT, Y_TOP),
        Seg.A4 to line(X_HOOK_START, Y_TOP, X_LEFT, Y_TOP),
        Seg.A3 to arc(HOOK_R, HOOK_R, HOOK_R, ANGLE_UP, -ANGLE_LEFT),

        // Right column. Full height on both halves - it is the left side that
        // gets shortened, which is what makes a 4 lopsided.
        Seg.B to line(X_RIGHT, Y_TOP, X_RIGHT, Y_MID),
        Seg.C to line(X_RIGHT, Y_MID, X_RIGHT, Y_BASE),

        // Bottom bar, and the two ways to finish its left corner.
        Seg.D1 to line(X_MID, Y_BASE, X_HOOK_START, Y_BASE),
        Seg.D2 to line(X_MID, Y_BASE, X_RIGHT, Y_BASE),
        Seg.D4 to line(X_HOOK_START, Y_BASE, X_LEFT, Y_BASE),
        Seg.D3 to arc(HOOK_R, Y_E_BOTTOM, HOOK_R, ANGLE_DOWN, ANGLE_LEFT),

        // Left column, each half with a stub that carries it out to the corner
        // when the corner is square rather than hooked.
        Seg.F1 to line(X_LEFT, Y_F_TOP, X_LEFT, Y_MID),
        Seg.F2 to line(X_LEFT, Y_TOP, X_LEFT, Y_F_TOP),
        Seg.E1 to line(X_LEFT, Y_MID, X_LEFT, Y_E_BOTTOM),
        Seg.E2 to line(X_LEFT, Y_E_BOTTOM, X_LEFT, Y_BASE),

        // Middle bar.
        Seg.G1 to line(X_LEFT, Y_MID, X_MID, Y_MID),
        Seg.G2 to line(X_MID, Y_MID, X_RIGHT, Y_MID),

        // Diagonals. H+K make one through-line, I+L the other; J is the extra,
        // with no mirror on the right because no ASCII glyph needs one.
        Seg.H to line(X_LEFT, Y_F_TOP, X_MID, Y_MID),
        Seg.I to line(X_RIGHT, Y_TOP, X_MID, Y_MID),
        Seg.K to line(X_MID, Y_MID, X_RIGHT, Y_BASE),
        Seg.L to line(X_MID, Y_MID, X_LEFT, Y_E_BOTTOM),
        Seg.J to line(X_LEFT, Y_MID, X_MID, Y_BASE),

        // Centre column.
        Seg.P to line(X_MID, Y_TOP, X_MID, Y_MID),
        Seg.Q to line(X_MID, Y_MID, X_MID, Y_BASE),

        // The right-hand parenthesis halves: bar stub from the centre, corner
        // arc, column stub to the middle. One continuous figure each, so the
        // curve joins its straights without seams.
        Seg.A5 to path {
            moveToCell(X_MID, Y_TOP)
            lineToCell(X_HOOK_END_R, Y_TOP)
            cubicToCell(
                X_HOOK_END_R + HOOK_K, Y_TOP,
                X_RIGHT, Y_F_TOP - HOOK_K,
                X_RIGHT, Y_F_TOP
            )
            lineToCell(X_RIGHT, Y_MID)
        },
        Seg.D5 to path {
            moveToCell(X_RIGHT, Y_MID)
            lineToCell(X_RIGHT, Y_E_BOTTOM)
            cubicToCell(
                X_RIGHT, Y_E_BOTTOM + HOOK_K,
                X_HOOK_END_R + HOOK_K, Y_BASE,
                X_HOOK_END_R, Y_BASE
            )
            lineToCell(X_MID, Y_BASE)
        },

        // The semicolon's tail.
        //
        // A semicolon is a colon whose lower dot grew a tail, so that is exactly
        // what this is: the comma's tail shape, hung off COL2 instead of off the
        // decimal point. It has to be its own element rather than reusing COMMA
        // because the two live in different places - the comma sits outside the
        // cell to the right, where a thousands separator belongs, while this
        // belongs on the cell axis under the upper dot.
        Seg.COL2_TAIL to line(
            DOT_AXIS_X, COL2_Y,
            DOT_AXIS_X - COMMA_TAIL_LEFT, COL2_Y + COMMA_TAIL_DROP
        ),

        // Descender.
        Seg.M to line(X_MID, Y_BASE, X_MID, Y_DESC),
        Seg.N to line(X_N_LEFT, Y_DESC, X_MID, Y_DESC),
        Seg.O to line(X_MID, Y_DESC, X_O_RIGHT, Y_DESC),
    )

    /**
     * Dot centres.
     *
     * Positioned by the slant but NOT shaped by it: these are true circles.
     * Shearing a circle turns it into an ellipse, which is visibly wrong.
     */
    private val DOT_CENTRES: Map<Seg, Offset> = mapOf(
        Seg.COL1 to Offset(sx(DOT_AXIS_X, COL1_Y), COL1_Y),
        Seg.COL2 to Offset(sx(DOT_AXIS_X, COL2_Y), COL2_Y),
        Seg.DP to Offset(sx(DP_X, DP_Y), DP_Y),
        Seg.COMMA to Offset(sx(DP_X, DP_Y), DP_Y),
    )

    /** The comma's tail: a taper from the dot down and to the left. */
    private val COMMA_TAIL: Path = path {
        val tipX = DP_X - COMMA_TAIL_LEFT
        val tipY = DP_Y + COMMA_TAIL_DROP

        moveToCell(DP_X, DP_Y)
        lineToCell(tipX, tipY)
    }

    // ---- Drawing ------------------------------------------------------------

    /**
     * Draws one cell's worth of segments with the cell's top-left centreline
     * corner at [origin].
     *
     * [cellHeight] is the rendered distance from the top centreline to the
     * baseline centreline, in pixels; everything else scales from it. Note that
     * ink extends STROKE/2 beyond the cell on every side, and a further
     * TOTAL_HEIGHT - CELL_HEIGHT below it for the descender.
     */
    fun DrawScope.drawTalkRpnCell(
        mask: Long,
        origin: Offset,
        cellHeight: Float,
        color: Color,
        strokeWidth: Float = STROKE
    ) {
        if (mask == 0L) return

        val scale = cellHeight / CELL_HEIGHT

        withTransform({
            translate(origin.x, origin.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {

            // Every lit bar goes into ONE path, stroked once.
            //
            // Drawing them one at a time looked right at heavy strokes and wrong
            // at light ones: where two segments overlap, the second stroke's
            // antialiased edge blends over the first, and the doubled coverage
            // reads as a brighter line. A real display has no such seam. Unioned
            // into a single path, Skia strokes the outline and fills it once, so
            // an overlap is painted exactly as often as anything else.
            val lit = Path()

            for ((seg, p) in PATHS) {
                if (mask and seg.bit == 0L) continue
                lit.addPath(p)
            }

            // The comma's tail is a bar like any other, so it joins the union.
            if (mask and Seg.COMMA.bit != 0L) lit.addPath(COMMA_TAIL)

            if (!lit.isEmpty) {

                // Round caps everywhere: with segments meeting flush, a butt cap
                // leaves a notch wherever a diagonal meets a bar at an angle.
                drawPath(lit, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }

            // Dots are filled, not stroked, and never overlap a bar, so they are
            // safe to draw separately.
            for ((seg, centre) in DOT_CENTRES) {
                if (mask and seg.bit == 0L) continue
                drawCircle(color, strokeWidth, centre)
            }
        }
    }
}

