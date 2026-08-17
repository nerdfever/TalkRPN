package com.nerdfever.talkrpn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/*
 * TalkRpnFont - a 32-element display cell.
 *
 * A 1970s bubble-LED look: HP-01 styling (rounded left corners) on a modified
 * DL-3422 segment set, so the display can show text as well as digits.
 * Identical to neither part. Where the numbers came from is recorded in
 * HISTORY.md.
 *
 * ---------------------------------------------------------------------------
 * Coordinate system
 * ---------------------------------------------------------------------------
 * Everything here is a CENTRELINE. Stroke width and slant are applied at render
 * time, so a segment is a zero-width hairline until it is drawn. Origin is the
 * top-left centreline corner; x runs right, y runs down.
 *
 * THE UNIT: segment E/F to segment B/C is exactly 1. That is the left column's
 * centreline to the right column's - the cell width, measured where the ink's
 * middle is, not where its edge is. Every length in this font and in everything
 * that lays it out - gap, vgap, stroke, the lot - is in that one unit,
 * horizontally and vertically alike. There is no second unit anywhere, and
 * nothing here is in pixels, dp or millimetres.
 *
 * Millimetres enter at exactly one place: whoever draws the font picks a size,
 * and that fixes the scale for everything else. So the display's shape is fully
 * specified by the numbers here, and only its SIZE depends on hardware.
 *
 * SCALE IS THE CALLER'S. Nothing here has a physical size: the drawing calls
 * take a cellHeight in pixels and everything is scaled from it, so the same
 * geometry serves a 2 mm watch row and a 300 mm reference sheet unchanged.
 *
 * ---------------------------------------------------------------------------
 * What the defaults here are for
 * ---------------------------------------------------------------------------
 * Stroke, slant and the cell proportions describe the hardware. What the app
 * finally renders is a separate question, to be settled by eye on the watch -
 * which is what the test screens' controls are for, and why they bracket these
 * values rather than replacing them.
 *
 * ---------------------------------------------------------------------------
 * Segments
 * ---------------------------------------------------------------------------
 *  A1 A2   top bar, split at the centre axis
 *  A3 A4   ALTERNATIVE top-left corners, never both. A4 runs the bar straight on
 *          to x = 0, giving a square corner. A3 instead turns it through 90
 *          degrees down into the left column, giving a rounded one - which is
 *          why a 7 carries a downward flag at its top left.
 *  B  C    right column, upper and lower
 *  D1 D2   bottom bar, split
 *  D3 D4   alternative bottom-left corners, mirroring A3/A4
 *  F1 F2   upper-left column. F1 is the main run, stopping where A3 lands. F2 is
 *          the short stub above it, carrying the column to y = 0 - lit when the
 *          corner is square, dark when it is hooked, and dark for a 4 so that its
 *          left side sits lower than its right.
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
 *  RPAR    the whole right parenthesis as ONE element - bar stub, corner arc,
 *          column, corner arc, bar stub, drawn as a single continuous figure
 *  COL1    upper colon dot; also dots a lower-case i
 *  COL2    lower colon dot, used only by the colon
 *  COL2_TAIL  the semicolon's tail, hung off COL2 on the cell axis
 *  DP      decimal point, out in the gap after the cell
 *  COMMA   comma, a dot with a tail, at the decimal point's position
 *
 * Thirty-two in all, which is why the mask is a Long - 32 would exactly fill an
 * Int with no headroom.
 *
 * ---------------------------------------------------------------------------
 * No gaps
 * ---------------------------------------------------------------------------
 * Adjacent segments meet FLUSH, where real hardware leaves a dark line between
 * them. Every endpoint below is shared with its neighbours, so a lit run reads
 * as one continuous stroke.
 */

object TalkRpnFont {

    // ---- Tweakables, in cell units -------------------------------------------
    //
    // Every value someone might plausibly want to turn, in one place. The rest
    // of this file either derives from these or describes the segments'
    // geometry. Each is explained in the block after the values, in this order.

    const val CELL_WIDTH = 1f           // by definition; all other measures are relative to this
    const val CELL_HEIGHT = 1.710f      // of the top 7 segments, baseline to top
    const val DESCENDER_DEPTH = 0.625f  // how far the descender hangs below the baseline
    const val STROKE = 0.1475f          // pen stroke width
    const val SLANT_DEGREES = 6.0f      // rightward lean, in degrees
    const val DEFAULT_GAP = 0.67f       // from the last lit centreline of one glyph to the first of the next
    const val SPACE_WIDTH = 0.6f        // width of blank space (0x20)
    const val VGAP = -0.33f             // vertical space between rows: one row's descender-bar centreline down to the next row's cap centreline
    const val DP_GAP_FRACTION = 0.337f  // how far across the gap the decimal point and comma sit, as a fraction of that gap

    /*
     * CELL_WIDTH - segment E/F to segment B/C: the left column to the right
     * column, centre to centre. This is the unit's definition, so it is 1 by
     * construction and must never be anything else.
     *
     * CELL_HEIGHT - the CAP HEIGHT: baseline to the top of a flat cap, which
     * here is segment D to segment A, centreline to centreline. In a segment
     * font every capital is exactly this tall, so it doubles as the cell's
     * height above the baseline. Two other heights are NOT this one: the
     * x-height, which is half of it (segment G to segment D), and TOTAL_HEIGHT,
     * which adds the descender below the baseline.
     *
     * DESCENDER_DEPTH - how far the descender bar hangs below the baseline.
     * The single number controlling how deep g q y j reach: everything that
     * depends on it - TOTAL_HEIGHT, the descender segments, the slant's lean,
     * the row spacing - derives from it, so one change propagates. Every
     * drawing and measuring call also takes a descender parameter DEFAULTING
     * to this, which is how the display test screen previews other depths
     * without changing anything outside itself.
     *
     * STROKE - rendered stroke width. If this is ever re-measured off a
     * photograph, measure the denominator CENTRE TO CENTRE, never as the outer
     * ink width: outer width already contains one stroke, so using it
     * understates the ratio. Centre-to-centre also makes the measurement
     * robust, since bloom pushes outer edges out and inner edges in by the
     * same amount and cancels exactly.
     *
     * SLANT_DEGREES - rightward lean, in degrees.
     *
     * DEFAULT_GAP - from the LAST lit centreline of one glyph to the FIRST lit
     * centreline of the next. The layout's one horizontal knob, and a
     * parameter on every call that lays out text - this is only its default.
     * Named DEFAULT_GAP rather than GAP so it cannot be mistaken for the `gap`
     * parameter it seeds: PowerShell, which mirrors this font, treats $GAP and
     * $gap as the SAME variable.
     *
     *   Both ends are centrelines, like every other length in this font. That
     *   is NOT the dark space a reader sees: each glyph's ink overhangs its
     *   own centreline by half a stroke, so the visible dark band is
     *   gap - STROKE. At the default, 0.67 - 0.1475 = 0.52.
     *
     *   Measuring centre to centre is what makes the floor fall out directly:
     *   the two inks touch when the gap equals one stroke, so anything above
     *   STROKE is physically legal. The slant makes it LOOK tight well before
     *   that, because one glyph's top-right passes close to the next one's
     *   bottom-left - but those are at different heights and never actually
     *   touch.
     *
     *   Mixed-case text reads well from about 0.76 to 0.92; all caps takes
     *   rather less. Still to be judged on the watch.
     *
     * SPACE_WIDTH - how wide a space is. A space is an ordinary cell that
     * happens to have no ink, so it takes a gap on each side like any other.
     * Nothing about it is special-cased and there is no separate notion of a
     * word space.
     *
     * VGAP - the vertical space between rows: one row's descender-bar
     * centreline down to the next row's cap centreline. The vertical partner
     * of DEFAULT_GAP, measured the same way - centreline to centreline - so
     * the two inks touch when it equals STROKE, exactly as glyphs do
     * horizontally, and anything below that overlaps. Because it is the space
     * BETWEEN the rows' ink, changing the descender depth moves the rows
     * apart or together by itself, keeping this clearance as tuned.
     *
     *   NEGATIVE on purpose: the rows interleave. One row's descender band
     *   reaches into the cap band of the next, visibly - a tail's tip ends
     *   ABOVE the top of a neighbouring ascender. Ink survives on horizontal
     *   offset alone, so text that stacks tall letters directly under tails
     *   will collide. Still to be settled by eye on the watch.
     *
     * DP_GAP_FRACTION - how far across the gap the decimal point and comma
     * sit, as a fraction of that gap. They belong to the GAP, not to the cell,
     * which is why this is a fraction rather than an x. Give it a fixed x
     * instead and tightening the gap leaves the dot standing inside the next
     * character.
     */

    // ---- Derived from the tweakables -----------------------------------------

    /** Segment A down to the N/O bar - the cap plus the descender. */
    fun totalHeight(descender: Float = DESCENDER_DEPTH): Float = CELL_HEIGHT + descender

    /** [totalHeight] at the font's own descender. */
    const val TOTAL_HEIGHT = CELL_HEIGHT + DESCENDER_DEPTH

    /** Top of segment A's ink to the bottom of the descender bar's ink. */
    const val INK_HEIGHT = TOTAL_HEIGHT + STROKE

    /**
     * VPITCH - vertical distance between successive rows, baseline to baseline,
     * in cell widths: a row's own centreline span plus the gap to the next -
     * the vertical twin of the horizontal pen advance, w/2 + gap + w/2.
     *
     * Baseline to baseline - segment D of one row to segment D of the next -
     * rather than gap-between-rows, so that it means the same thing when two
     * adjacent rows are different sizes. It is always measured in the units of
     * the REFERENCE row, so a half-size row does not carry half-size units.
     */
    const val VPITCH = TOTAL_HEIGHT + VGAP

    // ---- The grid the segments hang from ------------------------------------

    /** Radius of the two hooks, measured on their centreline. */
    const val HOOK_R = 0.1355f

    private const val X_LEFT = 0f
    private const val X_MID = CELL_WIDTH / 2f
    private const val X_RIGHT = CELL_WIDTH

    private const val Y_TOP = 0f
    private const val Y_MID = CELL_HEIGHT / 2f
    private const val Y_BASE = CELL_HEIGHT
    private const val Y_DESC = TOTAL_HEIGHT

    /** Where the top hook lands on the left column, and where the bottom one leaves it. */
    private const val Y_F_TOP = HOOK_R
    private const val Y_E_BOTTOM = Y_BASE - HOOK_R

    /** Where each horizontal bar's straight run ends and its hook begins. */
    private const val X_HOOK_START = HOOK_R

    /** The mirror point on the right, where the parenthesis arcs turn. */
    private const val X_HOOK_END_R = CELL_WIDTH - HOOK_R

    /**
     * Control-point offset for a quarter-circle as one cubic Bezier:
     * (4/3)tan(pi/8) x radius. Exact to within 0.02%.
     */
    private val HOOK_K = (4.0 / 3.0 * tan(Math.PI / 8.0)).toFloat() * HOOK_R

    /**
     * The descender bar is inset from the columns, symmetrically. The two figures
     * differ by 0.0002 - measurement rounding, and below what anything here is
     * measured well enough to justify correcting.
     */
    private const val X_N_LEFT = 0.06396f
    private const val X_O_RIGHT = 0.9359f

    // ---- Dots ---------------------------------------------------------------

    private const val DOT_AXIS_X = X_MID
    private const val COL1_Y = 0.3504f
    private const val COL2_Y = 1.378f

    /** How far the dot sits below the baseline. */
    private const val DP_DROP = 0.3263f

    private const val DP_Y = CELL_HEIGHT + DP_DROP

    /**
     * Where the dot goes after a glyph whose ink ends at [inkRight], at [gap].
     *
     * A third of the way into the gap, so it stays clear of both neighbours as
     * the gap changes - and so it follows a NARROW glyph in, rather than sitting
     * out at a fixed x where a full-width cell would have put it. The dot beside
     * a 1 belongs beside the 1.
     *
     * The dot is 2 x [STROKE] across, so its right edge lands
     * 0.663 x gap - STROKE/2 clear of the next glyph's ink: positive for any gap
     * above 0.11, which is tighter than the ink itself allows.
     */
    fun dpXAfter(inkRight: Float, gap: Float) = inkRight + DP_GAP_FRACTION * gap

    /** The comma's tail, relative to its dot. */
    private const val COMMA_TAIL_DROP = 0.3551f
    private const val COMMA_TAIL_LEFT = 0.1308f

    // ---- Slant --------------------------------------------------------------

    private val SHEAR = tan(Math.toRadians(SLANT_DEGREES.toDouble())).toFloat()

    /** Added to every x so a slanted cell still starts at x = 0. */
    private val SHEAR_OFFSET = SHEAR * TOTAL_HEIGHT

    /** Ink width of one slanted cell, before stroke is added. */
    val SHEARED_WIDTH = CELL_WIDTH + SHEAR_OFFSET

    /** Maps an upright cell coordinate to its slanted x. */
    private fun sx(x: Float, y: Float) = x - SHEAR * y + SHEAR_OFFSET

    // The same map as sx exists as a MATRIX too - see [shearMatrixFor] - so it
    // can be applied to a whole canvas rather than point by point. That is what
    // makes the PEN slant as well as the path: a real display's segments are
    // parallelograms, and you only get that by stroking upright and shearing
    // the RESULT. Shear the path first and stroke it after, which is what the
    // point-by-point sx does on its own, and the pen stays round or square in
    // device space with every end cut at the wrong angle.

    // ---- Segment identity ---------------------------------------------------

    /**
     * 32 elements, so the mask is a Long - 32 would exactly fill an Int with no
     * headroom.
     *
     * RPAR is the whole right parenthesis as ONE element - bar stub, corner arc,
     * column, corner arc, bar stub, drawn as a single continuous figure. It is
     * bespoke because the right side has no shortened bars or columns for a bare
     * arc to join: A2, B, C and D2 all run square into the corner, and splitting
     * them properly would cost six elements where this costs one.
     */
    enum class Seg {
        A1, A2, A3, A4,
        B, C,
        D1, D2, D3, D4,
        E1, E2, F1, F2,
        G1, G2,
        H, I, J, K, L,
        M, N, O,
        P, Q,
        RPAR,
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
        moveToSlanted(X_LEFT, Y_TOP)
        lineToSlanted(X_RIGHT, Y_TOP)
        lineToSlanted(X_RIGHT, Y_DESC)
        lineToSlanted(X_LEFT, Y_DESC)
        close()
    }

    /** Where the baseline sits inside those bounds - the descender hangs below. */
    val CELL_BASELINE: Path = path {
        moveToSlanted(X_LEFT, Y_BASE)
        lineToSlanted(X_RIGHT, Y_BASE)
    }

    // ---- Path construction --------------------------------------------------
    //
    // Segment paths are built UPRIGHT and sheared by [SHEAR_MATRIX] at draw time,
    // so the pen is sheared along with them and every segment end is cut at the
    // slant, as a real display's is.
    //
    // The two diagnostic overlays are the exception: callers draw them straight,
    // outside that transform, so they carry the shear in their own coordinates.

    private fun path(build: Path.() -> Unit) = Path().apply(build)

    private fun Path.moveToCell(x: Float, y: Float) = moveTo(x, y)
    private fun Path.lineToCell(x: Float, y: Float) = lineTo(x, y)
    private fun Path.cubicToCell(
        x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float
    ) = cubicTo(x1, y1, x2, y2, x3, y3)

    private fun Path.moveToSlanted(x: Float, y: Float) = moveTo(sx(x, y), y)
    private fun Path.lineToSlanted(x: Float, y: Float) = lineTo(sx(x, y), y)

    /**
     * A straight segment's centreline: just its two ends.
     *
     * Two points marks it as a BAR, which gets a fixed-orientation nib. Anything
     * longer is a curve, and gets a perpendicular one. See [outlineOf].
     */
    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        floatArrayOf(x1, y1, x2, y2)

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
    ): FloatArray = run {

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

        // Sampled, because the outline is built at draw time and a ribbon needs
        // points to offset. ARC_STEPS is plenty for a 90-degree turn this small.
        val out = FloatArray((ARC_STEPS + 1) * 2)

        for (i in 0..ARC_STEPS) {
            val t = i.toFloat() / ARC_STEPS
            val u = 1f - t
            out[i * 2] = u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3
            out[i * 2 + 1] = u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3
        }

        out
    }

    /** How finely the curves are sampled. */
    private const val ARC_STEPS = 16


    /** A cubic sampled into points, excluding its first (the caller already has it). */
    private fun cubicPoints(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float
    ): FloatArray {

        val out = FloatArray(ARC_STEPS * 2)

        for (i in 1..ARC_STEPS) {
            val t = i.toFloat() / ARC_STEPS
            val u = 1f - t
            out[(i - 1) * 2] = u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3
            out[(i - 1) * 2 + 1] = u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3
        }

        return out
    }

    // ---- Turning a centreline into ink --------------------------------------
    //
    // THE PEN DOES NOT ROTATE.
    //
    // Stroking a path cuts every end square to the direction that path happens to
    // run, so a diagonal gets an end sliced at 30 degrees while the bar beside it
    // gets one cut flat - put an X next to a Y and it is obvious - and where two
    // segments meet at an angle the mitre throws a spike.
    //
    // A real display has neither, because a segment is a die on a rectangular
    // grid. So a straight segment becomes a POLYGON swept by a nib of fixed
    // orientation: horizontal bars get a vertical nib, everything else a
    // horizontal one. A diagonal ends up about 14% thinner measured perpendicular
    // than a bar is, which is what a fixed nib does.
    //
    // Curves keep a perpendicular thickness. They are corner pieces turning a bar
    // into a column, and a fixed nib would pinch them to nothing at one end.


    /** How far each bar runs past its end, purely to close antialiasing seams. */
    private const val SEAM_OVERLAP = 0.0015f

    /**
     * When two endpoint coordinates count as the same point, in cell units.
     * Generous against float noise, and far below the smallest real separation
     * anywhere in the geometry (X_N_LEFT = 0.064).
     */
    private const val COINCIDENT = 0.001f

    /**
     * Add a polygon, wound consistently.
     *
     * Winding matters because every lit segment goes into ONE path filled in
     * NonZero mode. A bar running right-to-left comes out wound the opposite way
     * from one running left-to-right, and where two opposite-wound shapes overlap
     * the winding numbers cancel and punch a HOLE - small black notches exactly
     * at the crossings in # and $.
     */
    private fun Path.addWound(pts: FloatArray) {

        val n = pts.size / 2
        if (n < 3) return

        // Shoelace: negative means the points run the other way round.
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
        }

        if (area >= 0f) {
            moveTo(pts[0], pts[1])
            for (i in 1 until n) lineTo(pts[i * 2], pts[i * 2 + 1])
        } else {
            moveTo(pts[(n - 1) * 2], pts[(n - 1) * 2 + 1])
            for (i in n - 2 downTo 0) lineTo(pts[i * 2], pts[i * 2 + 1])
        }

        close()
    }

    /**
     * An axis-aligned bar, as the rectangle a fixed nib sweeps.
     *
     * THE END RULE - the die policy:
     *
     *   An end extends half a stroke ONLY when a perpendicular axis-aligned lit
     *   segment shares the endpoint. Everything else ends flat at the
     *   centreline, exactly as the separate dies on a real display do.
     *
     * The support case fills every corner and L-turn: at 7's top-right, A2 and B
     * each overshoot half a stroke and land exactly flush with each other's ink
     * edges; same at h's shoulder. Where there is no such partner the end is a
     * die edge - a lone 1 really is half a stroke shorter than the 0 beside it,
     * v's foot is flat with the diagonal melding into it, and lowercase tops sit
     * dead on the x-height line.
     *
     * Any change here must be judged on the FULL character sheet, not on a few
     * glyphs: every end rule that has been tried looked right on the letters it
     * was designed for and broke others.
     */
    private fun Path.addAxisBar(
        x1: Float, y1: Float, x2: Float, y2: Float, w: Float,
        extend1: Boolean, extend2: Boolean
    ) {
        val half = w / 2f

        val len = hypot(x2 - x1, y2 - y1)
        if (len == 0f) return

        val ux = (x2 - x1) / len
        val uy = (y2 - y1) / len

        val ext1 = SEAM_OVERLAP + if (extend1) half else 0f
        val ext2 = SEAM_OVERLAP + if (extend2) half else 0f

        val ax = x1 - ux * ext1; val ay = y1 - uy * ext1
        val bx = x2 + ux * ext2; val by = y2 + uy * ext2

        // The nib points across the bar's axis.
        val dx: Float; val dy: Float
        if (abs(bx - ax) > abs(by - ay)) { dx = 0f; dy = half } else { dx = half; dy = 0f }

        addWound(
            floatArrayOf(
                ax - dx, ay - dy,
                bx - dx, by - dy,
                bx + dx, by + dy,
                ax + dx, ay + dy
            )
        )
    }

    /**
     * A diagonal (or tail): the parallelogram a horizontal nib sweeps. Flat
     * horizontal end faces exactly at the endpoints - a die, nothing added.
     */
    private fun Path.addDiagonal(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {

        val half = w / 2f

        val len = hypot(x2 - x1, y2 - y1)
        if (len == 0f) return

        val ux = (x2 - x1) / len
        val uy = (y2 - y1) / len

        val ax = x1 - ux * SEAM_OVERLAP; val ay = y1 - uy * SEAM_OVERLAP
        val bx = x2 + ux * SEAM_OVERLAP; val by = y2 + uy * SEAM_OVERLAP

        addWound(
            floatArrayOf(
                ax - half, ay,
                bx - half, by,
                bx + half, by,
                ax + half, ay
            )
        )
    }

    /** Add one curved run, as a ribbon of constant perpendicular thickness. */
    private fun Path.addRibbon(pts: FloatArray, w: Float) {

        val half = w / 2f
        val n = pts.size / 2
        if (n < 2) return

        val left = FloatArray(n * 2)
        val right = FloatArray(n * 2)

        for (i in 0 until n) {

            val a = maxOf(0, i - 1)
            val b = minOf(n - 1, i + 1)

            val dx = pts[b * 2] - pts[a * 2]
            val dy = pts[b * 2 + 1] - pts[a * 2 + 1]
            val len = hypot(dx, dy)
            if (len == 0f) continue

            val nx = -dy / len * half
            val ny = dx / len * half

            left[i * 2] = pts[i * 2] + nx;      left[i * 2 + 1] = pts[i * 2 + 1] + ny
            right[i * 2] = pts[i * 2] - nx;     right[i * 2 + 1] = pts[i * 2 + 1] - ny
        }

        // Down one side and back the other.
        val outline = FloatArray(n * 4)
        for (i in 0 until n) {
            outline[i * 2] = left[i * 2]
            outline[i * 2 + 1] = left[i * 2 + 1]
        }
        for (i in 0 until n) {
            val j = n - 1 - i
            outline[(n + i) * 2] = right[j * 2]
            outline[(n + i) * 2 + 1] = right[j * 2 + 1]
        }

        addWound(outline)
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

    private fun buildCentrelines(descender: Float): Map<Seg, FloatArray> = mapOf(

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
        //
        // All four run to the EXACT cell corners, so / and \ are corner-to-corner.
        // That is why the glyphs joining a diagonal to the left column (& a e)
        // take square corners rather than hooks: a diagonal arriving at the corner
        // has no arc to meet.
        Seg.H to line(X_LEFT, Y_TOP, X_MID, Y_MID),
        Seg.I to line(X_RIGHT, Y_TOP, X_MID, Y_MID),
        Seg.K to line(X_MID, Y_MID, X_RIGHT, Y_BASE),
        Seg.L to line(X_MID, Y_MID, X_LEFT, Y_BASE),
        Seg.J to line(X_LEFT, Y_MID, X_MID, Y_BASE),

        // Centre column.
        Seg.P to line(X_MID, Y_TOP, X_MID, Y_MID),
        Seg.Q to line(X_MID, Y_MID, X_MID, Y_BASE),

        // The right parenthesis, whole: bar stub, corner arc, column, corner
        // arc, bar stub - one continuous figure, so every join is seamless.
        Seg.RPAR to (
            floatArrayOf(X_MID, Y_TOP, X_HOOK_END_R, Y_TOP) +
                cubicPoints(
                    X_HOOK_END_R, Y_TOP,
                    X_HOOK_END_R + HOOK_K, Y_TOP,
                    X_RIGHT, Y_F_TOP - HOOK_K,
                    X_RIGHT, Y_F_TOP
                ) +
                floatArrayOf(X_RIGHT, Y_E_BOTTOM) +
                cubicPoints(
                    X_RIGHT, Y_E_BOTTOM,
                    X_RIGHT, Y_E_BOTTOM + HOOK_K,
                    X_HOOK_END_R + HOOK_K, Y_BASE,
                    X_HOOK_END_R, Y_BASE
                ) +
                floatArrayOf(X_MID, Y_BASE)
            ),

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

        // Descender, at the depth asked for.
        Seg.M to line(X_MID, Y_BASE, X_MID, CELL_HEIGHT + descender),
        Seg.N to line(X_N_LEFT, CELL_HEIGHT + descender, X_MID, CELL_HEIGHT + descender),
        Seg.O to line(X_MID, CELL_HEIGHT + descender, X_O_RIGHT, CELL_HEIGHT + descender),
    )

    /**
     * [buildCentrelines], cached against the descender depth it was built for,
     * so the display test screen's live descender control costs one rebuild per
     * change rather than one per cell per frame.
     */
    private var centrelinesBuiltFor = Float.NaN
    private var centrelinesCache: Map<Seg, FloatArray> = emptyMap()

    private fun centrelines(descender: Float): Map<Seg, FloatArray> {
        if (centrelinesBuiltFor != descender) {
            centrelinesCache = buildCentrelines(descender)
            centrelinesBuiltFor = descender
        }
        return centrelinesCache
    }

    /**
     * The colon dots, which sit on the cell's own axis.
     *
     * The decimal point and comma are deliberately NOT here. They live in the gap
     * after the cell rather than in it, so their x is the layout's to decide and
     * arrives as [drawTalkRpnCell]'s dpX.
     */
    private val DOT_CENTRES: Map<Seg, Offset> = mapOf(
        Seg.COL1 to Offset(DOT_AXIS_X, COL1_Y),
        Seg.COL2 to Offset(DOT_AXIS_X, COL2_Y),
    )

    /**
     * The shear matrix for a slant - one small allocation per cell per frame,
     * which is nothing next to the path work.
     */
    private fun shearMatrixFor(slantDegrees: Float, descender: Float): Matrix {

        val shear = tan(Math.toRadians(slantDegrees.toDouble())).toFloat()

        return Matrix().apply {
            // Set by NAME, not by [row, column]. Compose indexes that operator
            // row-major into a column-major array, so the obvious-looking
            // [0, 1] and [0, 3] land on SkewY and a perspective term instead -
            // which renders as a cell sheared the wrong way and shifted out of
            // its own bounds.
            values[Matrix.SkewX] = -shear
            values[Matrix.TranslateX] = shear * totalHeight(descender)
        }
    }

    /** Ink width of one slanted cell at an arbitrary slant, in cell units. */
    fun shearedWidth(
        slantDegrees: Float = SLANT_DEGREES,
        descender: Float = DESCENDER_DEPTH,
    ): Float =
        CELL_WIDTH + tan(Math.toRadians(slantDegrees.toDouble())).toFloat() * totalHeight(descender)

    // ---- Text layout ---------------------------------------------------------
    //
    // PROPORTIONAL, not fixed pitch. Cells do not sit on a grid: each glyph takes
    // the width of its own ink, and every glyph is separated from the next by the
    // same clear space, [DEFAULT_GAP].
    //
    // Why not a grid, when the hardware this font records plainly had one: a real
    // display's cells are all the same width because each is a physical digit
    // position, but its GLYPHS are not - a 1 is two verticals on the right-hand
    // edge with no width at all, and i, l and most lower case are narrower than
    // the cell. Give each of those a whole cell and the spacing swings over a 3x
    // range inside a single number: 11,190 sets its 1s three times further apart
    // than its 190. Spacing by ink instead makes every gap equal, which is what
    // the eye is actually reading.
    //
    // Two conventions the layer above needs:
    //
    //   - a '.' or ',' does NOT take a cell. It merges into the PRECEDING cell's
    //     DP or COMMA element - that is the whole point of those elements - so
    //     "42.9" is three cells, not four.
    //   - a ' ' is an ordinary cell that happens to have no ink, [SPACE_WIDTH]
    //     wide. It takes a gap on each side like anything else.
    //
    // ---------------------------------------------------------------------------
    // The rule, in full
    // ---------------------------------------------------------------------------
    //
    //     parse text into cells:
    //         '.' or ','  ->  merge into the previous cell, no cell of its own
    //         ' '         ->  a cell with no ink
    //         no glyph    ->  DROPPED, as though it had not been in the string
    //         anything else -> a cell holding that glyph's segment mask
    //
    //     for each cell:
    //         inkLeft, inkRight = leftmost and rightmost LIT CENTRELINE
    //                             (the dot and comma excluded - they live in
    //                              the gap, not in the glyph)
    //         width = if it is a space:      SPACE_WIDTH
    //                 if it has no ink:      CELL_WIDTH   (a lone leading dot)
    //                 otherwise:             inkRight - inkLeft
    //
    //     pen = width(first) / 2                       # start on the first centre
    //     for each cell after the first:
    //         pen += width(previous)/2 + gap + width(this)/2
    //
    //     place each cell so its INK CENTRE lands on the pen:
    //         cellOrigin = pen - (inkLeft + inkRight)/2
    //
    //     its dot, if any, goes at
    //         dpX = inkRight + DP_GAP_FRACTION * gap
    //
    // Two full-width glyphs therefore sit 1 + gap apart, which is the widest any
    // pair gets - all-caps text is spaced as though it were on a grid. It is only
    // the narrow glyphs that come in closer.

    /** One parsed cell: its segments, and whether it is a word space. */
    class TextCell(val mask: Long, val isSpace: Boolean)

    /** One placed cell: its segments, where its origin goes, where its dot goes. */
    class PlacedCell(val mask: Long, val originUnits: Float, val dpXUnits: Float)

    /** Left and right ends of a glyph's ink, on the centreline, in cell units. */
    class InkExtent(val left: Float, val right: Float) {
        val width get() = right - left
        val centre get() = (left + right) / 2f
    }

    /** The two elements that belong to the gap rather than to the glyph. */
    private val GAP_DWELLERS = Seg.DP.bit or Seg.COMMA.bit

    /** The segments below the baseline, which spacing must not count. */
    private val DESCENDER_SEGS = Seg.M.bit or Seg.N.bit or Seg.O.bit

    /** The text as cells, with '.' and ',' merged into their predecessors. */
    fun textCells(text: String): List<TextCell> {

        val cells = ArrayList<TextCell>(text.length)

        for (ch in text) {

            val punct = when (ch) {
                '.' -> Seg.DP.bit
                ',' -> Seg.COMMA.bit
                else -> 0L
            }

            if (punct != 0L) {
                // Into the cell before it - or a cell of its own at the start of
                // the string, exactly as a leading ".5" shows on a real display.
                if (cells.isNotEmpty() && !cells.last().isSpace) {
                    val last = cells.removeAt(cells.size - 1)
                    cells.add(TextCell(last.mask or punct, false))
                } else {
                    cells.add(TextCell(punct, false))
                }
                continue
            }

            if (ch == ' ') { cells.add(TextCell(0L, true)); continue }

            // Anything the font has no glyph for is DROPPED - no cell, no
            // advance, as though it had not been in the string. The font covers
            // 0x20 to 0x7F with no gaps, so this only ever fires on a character
            // outside that range, and there is nothing sensible to draw for one.
            val mask = TalkRpnGlyphs.maskFor(ch) ?: continue

            cells.add(TextCell(mask, false))
        }

        return cells
    }

    /**
     * How far a mask's lit ink reaches left and right, on the centreline.
     *
     * The decimal point and comma are excluded deliberately: they sit outside the
     * cell in the gap that follows it, so counting them would make every glyph
     * carrying one measure a third of a gap wider than it sets, and would push
     * the next glyph away from a dot that is supposed to nestle beside it.
     *
     * The DESCENDER segments are excluded too: a tail tucks under its neighbour
     * rather than pushing it away. Counting them opened a hole between q and u,
     * because q's tail flag reaches nearly a whole cell right of its bowl.
     *
     * Null when nothing is lit - a space, or a character with no glyph.
     */
    fun inkExtentOf(mask: Long): InkExtent? {

        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY

        // Every lit centreline's points. Bars carry two, curves carry many; the
        // extreme is always at a sampled point either way. The descender depth
        // does not matter here - only x is read, and no descender is counted.
        for ((seg, pts) in centrelines(DESCENDER_DEPTH)) {

            if (mask and seg.bit == 0L) continue
            if (seg.bit and DESCENDER_SEGS != 0L) continue

            var i = 0
            while (i < pts.size) {
                if (pts[i] < left) left = pts[i]
                if (pts[i] > right) right = pts[i]
                i += 2
            }
        }

        // The colon dots, by their centres - the same convention as the bars
        // above, which are measured on their centrelines rather than their edges.
        // The decimal point and comma are not in DOT_CENTRES at all, which is
        // what keeps them out of the glyph's width.
        for ((seg, centre) in DOT_CENTRES) {

            if (mask and seg.bit == 0L) continue

            if (centre.x < left) left = centre.x
            if (centre.x > right) right = centre.x
        }

        if (left > right) return null

        return InkExtent(left, right)
    }

    /**
     * [text] laid out at [gap], as cells ready to draw.
     *
     * Origins are in CELL UNITS, relative to the leftmost lit centreline of the
     * first glyph, which is zero. So a caller places the returned x's against the
     * left edge of the ink - not against a notional cell boundary, which under
     * proportional spacing is not a thing the eye can see anyway.
     *
     * A leading space contributes nothing, since there is no ink before it for
     * its blank to separate from.
     */
    fun layout(text: String, gap: Float = DEFAULT_GAP): List<PlacedCell> {

        val placed = ArrayList<PlacedCell>(text.length)

        var pen = 0f
        var previousHalfWidth: Float? = null

        for (cell in textCells(text)) {

            val extent = inkExtentOf(cell.mask)

            // What this cell claims of the line: its own ink, a scaled blank for
            // a space, or a whole cell for a lone leading dot with no ink at all.
            val width = when {
                cell.isSpace -> SPACE_WIDTH
                extent == null -> CELL_WIDTH
                else -> extent.width
            }

            // Walk the pen to this cell's ink centre. The first cell simply
            // starts half its own width in, which puts its left ink at zero.
            pen += if (previousHalfWidth == null) width / 2f
            else previousHalfWidth + gap + width / 2f

            previousHalfWidth = width / 2f

            if (cell.isSpace) continue

            // No ink to centre on. The only cell that can reach here is a
            // leading '.' or ',', which has nothing to merge backward into and
            // so becomes a cell of nothing but its dot - exactly as ".5" shows
            // on a real display. Give it a whole cell's worth of room and hang
            // the dot off the right of it.
            val centre = extent?.centre ?: (CELL_WIDTH / 2f)
            val inkRight = extent?.right ?: CELL_WIDTH

            placed.add(
                PlacedCell(
                    mask = cell.mask,
                    originUnits = pen - centre,
                    dpXUnits = dpXAfter(inkRight, gap)
                )
            )
        }

        return placed
    }

    /**
     * Full ink width of [text] in pixels - left ink edge to right ink edge, with
     * the stroke's overhang at both ends and the slant's lean included.
     *
     * This is what [drawTalkRpnText] draws into, so `origin.x = right - this`
     * right-aligns exactly.
     */
    fun measureWidth(
        text: String,
        cellHeight: Float,
        gap: Float = DEFAULT_GAP,
        slantDegrees: Float = SLANT_DEGREES,
        descender: Float = DESCENDER_DEPTH,
    ): Float {

        val cells = layout(text, gap)
        if (cells.isEmpty()) return 0f

        var rightmost = 0f

        for (cell in cells) {

            val extent = inkExtentOf(cell.mask)
            if (extent != null) rightmost = maxOf(rightmost, cell.originUnits + extent.right)

            // A dot pokes into the gap beyond its own glyph, and on the last
            // cell there is nothing after it to hide behind.
            if (cell.mask and GAP_DWELLERS != 0L) {
                rightmost = maxOf(rightmost, cell.originUnits + cell.dpXUnits + STROKE)
            }
        }

        // The slant leans the top of the ink rightward by the full shear offset;
        // the stroke overhangs half a width at each end.
        val units = rightmost + (shearedWidth(slantDegrees, descender) - CELL_WIDTH) + STROKE

        return units * cellHeight / CELL_HEIGHT
    }

    /**
     * Draws [text] with the TOP LEFT CORNER OF ITS INK at [inkOrigin].
     *
     * Note that this is an ink box, not a cell origin - unlike [drawTalkRpnCell],
     * which takes the cell's own coordinate origin and lets the stroke overhang
     * it. Here the caller gets a box it can measure with [measureWidth] and
     * position without knowing anything about where the centrelines fall.
     */
    fun DrawScope.drawTalkRpnText(
        text: String,
        inkOrigin: Offset,
        cellHeight: Float,
        color: Color,
        gap: Float = DEFAULT_GAP,
        slantDegrees: Float = SLANT_DEGREES,
        strokeWidth: Float = STROKE,
        descender: Float = DESCENDER_DEPTH,
    ) {
        val scale = cellHeight / CELL_HEIGHT

        // In from the ink's corner to the first centreline, on both axes.
        val overhang = strokeWidth / 2f * scale

        for (cell in layout(text, gap)) {

            drawTalkRpnCell(
                mask = cell.mask,
                origin = Offset(inkOrigin.x + overhang + cell.originUnits * scale, inkOrigin.y + overhang),
                cellHeight = cellHeight,
                color = color,
                strokeWidth = strokeWidth,
                dpX = cell.dpXUnits,
                slantDegrees = slantDegrees,
                descender = descender,
            )
        }
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
        strokeWidth: Float = STROKE,
        dpX: Float = dpXAfter(CELL_WIDTH, DEFAULT_GAP),
        slantDegrees: Float = SLANT_DEGREES,
        descender: Float = DESCENDER_DEPTH,
    ) {
        if (mask == 0L) return

        val scale = cellHeight / CELL_HEIGHT

        withTransform({
            translate(origin.x, origin.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {

            // Every lit segment goes into ONE path, filled once.
            //
            // Drawing them one at a time looked right at heavy weights and wrong
            // at light ones: where two overlap, the second shape's antialiased
            // edge blends over the first and the doubled coverage reads as a
            // brighter line. A real display has no such seam. Unioned into a
            // single path and filled once, an overlap is painted exactly as
            // often as anything else.
            //
            // NonZero winding, and every polygon wound the same way by
            // construction, so overlaps add rather than cancelling into holes.
            val lit = Path()
            lit.fillType = PathFillType.NonZero

            // Every lit segment's endpoints, tagged with the segment's axis:
            // 'H' horizontal bar, 'V' vertical bar, 'D' diagonal, 'C' curve.
            // An end extends only into a PERPENDICULAR partner.
            val ends = ArrayList<Float>()
            val axes = ArrayList<Char>()

            fun axisOf(pts: FloatArray): Char = when {
                pts.size > 4 -> 'C'
                abs(pts[1] - pts[3]) < COINCIDENT -> 'H'
                abs(pts[0] - pts[2]) < COINCIDENT -> 'V'
                else -> 'D'
            }

            for ((seg, pts) in centrelines(descender)) {
                if (mask and seg.bit == 0L) continue
                val ax = axisOf(pts)
                ends.add(pts[0]); ends.add(pts[1]); axes.add(ax)
                ends.add(pts[pts.size - 2]); ends.add(pts[pts.size - 1]); axes.add(ax)
            }

            fun hasPerpPartner(x: Float, y: Float, perp: Char, self: Char): Boolean {
                var selfSeen = false
                for (i in 0 until axes.size) {
                    if (abs(ends[i * 2] - x) < COINCIDENT && abs(ends[i * 2 + 1] - y) < COINCIDENT) {
                        if (!selfSeen && axes[i] == self) { selfSeen = true; continue }
                        if (axes[i] == perp) return true
                    }
                }
                return false
            }

            // The four points where a bar hands over to a corner arc: never
            // extend into an arc, it puts a bump on the hook's outer edge.
            fun hookPoint(x: Float, y: Float): Boolean {
                val e = COINCIDENT
                return (abs(x - X_HOOK_START) < e && abs(y) < e) ||
                    (abs(x) < e && abs(y - Y_F_TOP) < e) ||
                    (abs(x) < e && abs(y - Y_E_BOTTOM) < e) ||
                    (abs(x - X_HOOK_START) < e && abs(y - CELL_HEIGHT) < e)
            }

            fun barExtend(x: Float, y: Float, self: Char, perp: Char): Boolean =
                !hookPoint(x, y) && hasPerpPartner(x, y, perp, self)

            for ((seg, pts) in centrelines(descender)) {

                if (mask and seg.bit == 0L) continue

                when (axisOf(pts)) {
                    'C' -> lit.addRibbon(pts, strokeWidth)
                    'H' -> {
                        lit.addAxisBar(
                            pts[0], pts[1], pts[2], pts[3], strokeWidth,
                            barExtend(pts[0], pts[1], 'H', 'V'),
                            barExtend(pts[2], pts[3], 'H', 'V')
                        )

                        // THE MITRE DIAMOND. A horizontal bar's end face is
                        // vertical and a diagonal's is horizontal, so where the
                        // two share an endpoint the diagonal's shoulder pokes
                        // half a stroke past the bar's flat end - the notch at
                        // the corners of Z, z, s, e, a and the top-left of &.
                        // The diamond spanning both end faces is the mitre a
                        // stroked join would have supplied: its 45-degree upper
                        // edge chamfers the bar's corner into the shoulder, and
                        // its lower half is buried under the diagonal's body.
                        //
                        // Only this pairing needs it: a vertical bar's end face
                        // is horizontal, identical to the diagonal's, so those
                        // junctions already meet flush.
                        val half = strokeWidth / 2f

                        for (k in 0..1) {
                            val mx = pts[k * 2]; val my = pts[k * 2 + 1]
                            if (hasPerpPartner(mx, my, 'D', 'H')) {
                                lit.addWound(
                                    floatArrayOf(
                                        mx, my - half,
                                        mx + half, my,
                                        mx, my + half,
                                        mx - half, my
                                    )
                                )
                            }
                        }
                    }
                    'V' -> lit.addAxisBar(
                        pts[0], pts[1], pts[2], pts[3], strokeWidth,
                        barExtend(pts[0], pts[1], 'V', 'H'),
                        barExtend(pts[2], pts[3], 'V', 'H')
                    )
                    else -> lit.addDiagonal(pts[0], pts[1], pts[2], pts[3], strokeWidth)
                }
            }

            // The comma's tail, hanging down and to the left of its dot.
            if (mask and Seg.COMMA.bit != 0L)
                lit.addDiagonal(
                    dpX, DP_Y,
                    dpX - COMMA_TAIL_LEFT, DP_Y + COMMA_TAIL_DROP,
                    strokeWidth
                )

            // Dots are squares of side twice the stroke, matching the separate
            // rectangular dies a real display's segments are beaded out of.
            // Sheared with everything else, so they lean rather than sitting
            // upright among leaning bars.
            fun addDot(cx: Float, cy: Float) {
                lit.moveTo(cx - strokeWidth, cy - strokeWidth)
                lit.lineTo(cx + strokeWidth, cy - strokeWidth)
                lit.lineTo(cx + strokeWidth, cy + strokeWidth)
                lit.lineTo(cx - strokeWidth, cy + strokeWidth)
                lit.close()
            }

            // The colon dots, on the cell's own axis.
            for ((seg, centre) in DOT_CENTRES) {
                if (mask and seg.bit == 0L) continue
                addDot(centre.x, centre.y)
            }

            // The decimal point and comma, out in the gap where the layout put
            // them. Both draw the same dot; only the comma adds a tail.
            if (mask and GAP_DWELLERS != 0L) addDot(dpX, DP_Y)

            if (lit.isEmpty) return@withTransform

            drawIntoCanvas { canvas ->

                val paint = Paint().apply {
                    isAntiAlias = true
                    this.color = color
                    style = PaintingStyle.Fill
                }

                // Filled INSIDE the shear, so the whole outline leans together.
                canvas.save()
                canvas.concat(shearMatrixFor(slantDegrees, descender))
                canvas.drawPath(lit, paint)
                canvas.restore()
            }
        }
    }
}
