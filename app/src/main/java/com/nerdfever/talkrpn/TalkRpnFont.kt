package com.nerdfever.talkrpn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
 * THE UNIT: segment E/F to segment B/C is exactly 1. That is the left column's
 * centreline to the right column's - the cell width, measured where the ink's
 * middle is, not where its edge is. Every length in this font and in everything
 * that lays it out - pitch, vpitch, stroke, the lot - is in that one unit,
 * horizontally and vertically alike. There is no second unit anywhere, and
 * nothing here is in pixels, dp or millimetres.
 *
 * Millimetres enter at exactly one place: whoever draws the font picks a size,
 * and that fixes the scale for everything else. So the display's shape is fully
 * specified by the numbers here, and only its SIZE depends on hardware.
 *
 * The geometry was drawn on a working grid where the cap height was 100 - the
 * HP-01's centreline box, a 53.5 x 91.5, scaled by 100/91.5. Every figure here
 * is its value on that grid divided by 58.47, the cell width there. That is a
 * pure rescale, so no vertex has moved and the glyphs are the reviewed ones.
 *
 *     58.47 / 58.47 = 1        the cell width, by definition
 *     29.235 / 58.47 = 0.5     the centre axis, exactly
 *     100 / 58.47 = 1.71028    the cap height
 *     20.49 / 58.47 = 0.35044  the upper colon dot
 *     80.60 / 58.47 = 1.37848  the lower colon dot
 *     7.92 / 58.47 = 0.13545   the hook radius
 *     16 / 108.5 = 0.14747     the stroke, measured off the real part - see STROKE
 *     142.08 / 58.47 = 2.43031 the pitch
 *
 * The payoff for reading it: since the cell is 1 wide, pitch minus 1 IS the
 * clearance between neighbouring cells, and the ink meets at pitch = 1 + STROKE.
 *
 * Note this differs from Hp01Font, whose coordinates are ink-box based and so
 * carry an extra STROKE/2 on every axis. The mapping is
 *
 *     talkRpn = (hp01 - STROKE/2) / 53.5
 *
 * ---------------------------------------------------------------------------
 * What the defaults here are for
 * ---------------------------------------------------------------------------
 * Every default in this file records the REAL PART as measured - stroke, slant,
 * dot shape, proportions. It is a description of 1970s bubble LEDs, not a set of
 * choices about how the calculator should look.
 *
 * What the app finally renders is a separate question, to be settled by eye on
 * the watch. That is what the test screens' controls are for, and why they
 * bracket these values rather than replacing them. If a tuned value ends up
 * differing, this file still says what the hardware did.
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

    /**
     * The cell width in the working grid this geometry was drawn on - the grid
     * where the cap height was 100. Dividing by it is the whole of the unit
     * change, so it appears exactly once, here.
     *
     * Every figure below is its value on THAT grid over this. That is a pure
     * rescale: not one vertex moves, and the glyphs that were reviewed and
     * corrected one by one are the same glyphs. Deriving them afresh from the
     * HP-01's own 53.5 would have been prettier by a few digits and would have
     * shifted points by up to 0.04% - which is not a licence this file has.
     */
    private const val GRID_CELL_WIDTH = 58.47f

    /**
     * Segment E/F to segment B/C - the left column to the right column, centre to
     * centre. This is the unit's definition, so it is 1 by construction and must
     * never be anything else.
     */
    const val CELL_WIDTH = 1f

    /**
     * How many of this font's own coordinates make one display unit.
     *
     * Trivially 1 here, because this font defines the unit. It exists so layout
     * code can be written against any font's E/F-to-B/C span rather than against
     * its cell width - the two are NOT the same in Hp01Font, whose coordinates
     * are ink-box based and whose E/F-to-B/C span is 53.5 of its own 62.
     */
    const val UNIT_SPAN = CELL_WIDTH

    /** Segment D to segment A, centreline to centreline. */
    const val CELL_HEIGHT = 100f / GRID_CELL_WIDTH         // 1.71028

    /**
     * How far the descender bar hangs below the baseline, as a fraction of the
     * cap height. This is the one number behind "segment M is too tall".
     *
     * Exactly 0.44 on the old grid too - 144 against 100 - so naming it moves
     * nothing.
     */
    private const val DESCENDER_FRACTION = 0.44f

    /** Including the descender: segment A down to the N/O bar. */
    const val TOTAL_HEIGHT = CELL_HEIGHT * (1f + DESCENDER_FRACTION)   // 2.46280

    /**
     * Rendered stroke width, measured off the real part.
     *
     * Dave measured a microscope photograph of an HP-55 bubble display in GIMP:
     * a 16 px stroke against 108.5 px from the left column's centre to the
     * right's. Written as that division so the two measurements stay visible.
     *
     * This CORRECTS an earlier value of 0.0795, which was half the HP-01's own
     * 9.29 and too thin. That came from a note claiming 4.45% of digit height on
     * HP's own part, and from my own threshold measurement of a second, sharper
     * photograph - both of which the microscope shot contradicts. The threshold
     * was cutting inside the stroke: the same method reads 21-26 px on the frame
     * where the edge is visibly at 16.
     *
     * So the HP-01's 9.29 was close to right all along - 0.159 against the 0.147
     * measured here, an 8% difference - and the claim that it was "twice what it
     * should be" was wrong.
     *
     * Bloom still argues the truth is at or below this rather than above it.
     */
    const val STROKE = 16f / 108.5f                        // 0.14747

    /** Rightward lean, in degrees. */
    /** Dave's compromise between HP's datasheet 5.0 and the 7.5 first used. */
    const val SLANT_DEGREES = 6.0f

    /**
     * Side of the square dots - the decimal point, the colon dots and the comma's
     * head - at twice the stroke.
     *
     * SQUARE, because that is what the real part has: a macro photograph of an
     * HP-55 shows the decimal point as a distinct square die, and the same
     * photograph shows every segment beaded out of small rectangular dies. Round
     * was the earlier guess and it was wrong.
     *
     * Tied to [STROKE], so a change there moves the dots with it. That is the
     * HP-01's own relation, but it is an observation rather than a rule this font
     * has to obey - if the dots read wrong on the watch, giving this its own
     * constant is a one-line change.
     */
    const val DOT_SIDE = 2f * STROKE

    /**
     * PITCH - horizontal distance between successive cell origins, in cell widths.
     *
     * The HP-01's own 130. It is very wide, because on the real instrument every
     * character occupied a full digit position: at 2.43 cell widths, well over
     * half the pitch is empty and the space between digits exceeds the digits.
     * Expect to tighten it - all caps read well from about 1.45 to 1.80.
     *
     * Reading it is now direct: pitch minus 1 IS the clearance between cells,
     * since the cell is exactly 1 wide. The floor is set by ink, not by taste -
     * neighbours clear each other while that clearance exceeds one stroke, so
     * pitch >= 1.14747. The slant makes it LOOK tight well before then, because
     * one cell's top-right passes close to the next cell's bottom-left, but those
     * are at different heights and never actually touch.
     */
    const val PITCH = 142.08f / GRID_CELL_WIDTH            // 2.43031

    /**
     * VPITCH - vertical distance between successive rows, baseline to baseline,
     * in the same cell widths as [PITCH].
     *
     * Baseline to baseline - segment D of one row to segment D of the next -
     * rather than gap-between-rows, so that it means the same thing when two
     * adjacent rows are different sizes. It is always measured in the units of
     * the REFERENCE row, so a half-size row does not carry half-size units.
     *
     * The floor here is the descender: ink runs from STROKE/2 above segment A to
     * STROKE/2 below the descender bar, which is [INK_HEIGHT] = 2.61 tall, so
     * anything under that overlaps the row beneath. 2.75 leaves a little air. A
     * digits-only display could go far tighter - a seven-segment font has no
     * descenders at all - but this font has them and letters will use them.
     */
    const val VPITCH = 2.75f

    /** Top of segment A's ink to the bottom of the descender bar's ink. */
    const val INK_HEIGHT = TOTAL_HEIGHT + STROKE           // 2.61027

    /** Radius of the two hooks, measured on their centreline. */
    const val HOOK_R = 7.92f / GRID_CELL_WIDTH             // 0.13545

    // ---- The grid the segments hang from ------------------------------------

    private const val X_LEFT = 0f
    private const val X_MID = CELL_WIDTH / 2f          // 0.5 exactly
    private const val X_RIGHT = CELL_WIDTH             // 1 exactly

    private const val Y_TOP = 0f
    private const val Y_MID = CELL_HEIGHT / 2f         // 0.85514
    private const val Y_BASE = CELL_HEIGHT             // 1.71028
    private const val Y_DESC = TOTAL_HEIGHT            // 2.46280

    /** Where the top hook lands on the left column, and where the bottom one leaves it. */
    private const val Y_F_TOP = HOOK_R                 // 0.13545
    private const val Y_E_BOTTOM = Y_BASE - HOOK_R     // 1.57483

    /** Where each horizontal bar's straight run ends and its hook begins. */
    private const val X_HOOK_START = HOOK_R            // 0.13545

    /** The mirror point on the right, where the parenthesis arcs turn. */
    private const val X_HOOK_END_R = CELL_WIDTH - HOOK_R   // 0.86455

    /**
     * Control-point offset for a quarter-circle as one cubic Bezier:
     * (4/3)tan(pi/8) x radius. Exact to within 0.02%.
     */
    private val HOOK_K = (4.0 / 3.0 * tan(Math.PI / 8.0)).toFloat() * HOOK_R

    /**
     * The descender bar is inset from the columns, symmetrically.
     *
     * The two figures differ by 0.0002, which is the rounding left over from the
     * grid they were drawn on. It is not worth "correcting": doing so would move
     * a vertex, and nothing here is measured well enough to justify that.
     */
    private const val X_N_LEFT = 3.74f / GRID_CELL_WIDTH    // 0.06396
    private const val X_O_RIGHT = 54.72f / GRID_CELL_WIDTH  // 0.93586

    // ---- Dots ---------------------------------------------------------------

    private const val DOT_AXIS_X = X_MID                    // 0.5 exactly
    private const val COL1_Y = 20.49f / GRID_CELL_WIDTH     // 0.35044
    private const val COL2_Y = 80.60f / GRID_CELL_WIDTH     // 1.37848

    /**
     * The decimal point and comma sit outside the cell, in the gap to its right.
     *
     * Taken from an HP datasheet, at the authentic pitch. Note what that means:
     * the dot does not belong to its cell, it belongs to the GAP, so a fixed x is
     * only right at one pitch. Tighten the pitch and the gap shrinks underneath a
     * dot that has not moved, until it is standing inside the next character.
     */
    private const val DP_X = 86.64f / GRID_CELL_WIDTH       // 1.48179

    /** How far the dot sits below the baseline, as a fraction of the cap height. */
    private const val DP_DROP_FRACTION = 0.1908f            // 119.08 / 100, exactly

    private const val DP_Y = CELL_HEIGHT * (1f + DP_DROP_FRACTION)   // 2.03660

    /**
     * Where the decimal point sits across the gap between two cells, as a
     * fraction of that gap.
     *
     * Derived from [DP_X] rather than stated, so it carries no rounding of its
     * own: [dpXAt] reproduces the datasheet position exactly at [PITCH] and stays
     * sensible either side of it.
     */
    const val DP_GAP_FRACTION = (DP_X - CELL_WIDTH) / (PITCH - CELL_WIDTH)   // 0.33692

    /**
     * The decimal point's x at a given pitch. Callers rendering at anything other
     * than [PITCH] should shift the DP and COMMA elements by dpXAt(pitch) - DP_X.
     */
    fun dpXAt(pitch: Float) = CELL_WIDTH + DP_GAP_FRACTION * (pitch - CELL_WIDTH)

    /** The comma's tail, relative to its dot. */
    private const val COMMA_TAIL_DROP = 20.76f / GRID_CELL_WIDTH   // 0.35505
    private const val COMMA_TAIL_LEFT = 7.65f / GRID_CELL_WIDTH    // 0.13084

    // ---- Slant --------------------------------------------------------------

    /** Past this ratio a mitre is cut off, so acute diagonals cannot spike. */
    private const val MITRE_LIMIT = 2.5f

    private val SHEAR = tan(Math.toRadians(SLANT_DEGREES.toDouble())).toFloat()

    /** Added to every x so a slanted cell still starts at x = 0. */
    private val SHEAR_OFFSET = SHEAR * TOTAL_HEIGHT

    /** Ink width of one slanted cell, before stroke is added. */
    val SHEARED_WIDTH = CELL_WIDTH + SHEAR_OFFSET

    /** Maps an upright cell coordinate to its slanted x. */
    private fun sx(x: Float, y: Float) = x - SHEAR * y + SHEAR_OFFSET

    /**
     * The same map as [sx], as a matrix, so it can be applied to a whole canvas
     * rather than point by point.
     *
     * This is what makes the PEN slant as well as the path. A real display's
     * segments are parallelograms - a vertical bar's ends are horizontal, a
     * horizontal bar's ends are slanted - and you only get that by stroking
     * upright and shearing the RESULT. Shearing the path first and stroking it
     * after, which is what the point-by-point [sx] did on its own, leaves a
     * round or square pen in device space and every end cut at the wrong angle.
     *
     * Column-major, so index 4 is the x-from-y skew and 12 the x translation.
     */
    private val SHEAR_MATRIX = Matrix().apply {
        // Set by NAME rather than by [row, column]. Compose indexes that operator
        // row-major into a column-major array, so the obvious-looking [0, 1] and
        // [0, 3] land on SkewY and a perspective term instead - which renders as
        // a cell that is sheared the wrong way and shifted out of its own bounds.
        values[Matrix.SkewX] = -SHEAR              // x picks up -SHEAR per unit of y
        values[Matrix.TranslateX] = SHEAR_OFFSET   // shifted so the cell starts at 0
    }

    // ---- Segment identity ---------------------------------------------------

    /**
     * 32 elements. The mask is a Long: 32 would exactly fill an Int with no
     * headroom, so it stays wide.
     *
     * RPAR is the whole right parenthesis as ONE element - bar stub, corner
     * arc, column, corner arc, bar stub, drawn as a single continuous figure.
     * It began life as two halves (A5 and D5), but nothing ever lit one without
     * the other, so they merged. It is bespoke because the right side has no
     * shortened bars or columns for bare arcs to join - A2, B, C and D2 all run
     * square into the corner, and splitting them properly would cost six
     * elements where this costs one.
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

    /** Add one straight segment, as the parallelogram a fixed nib sweeps. */
    private fun Path.addBar(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {

        val half = w / 2f
        val len = hypot(x2 - x1, y2 - y1)
        if (len == 0f) return

        // Half a stroke past each end, along the segment's own direction.
        // Without it the corners notch: a bar ending at x = 1 stops dead there
        // while the column beside it starts at y = 0, leaving the square outside
        // both empty. This is the old square cap restored - the difference being
        // that the end FACE stays axis-aligned instead of turning with the bar.
        val ux = (x2 - x1) / len * half
        val uy = (y2 - y1) / len * half

        val ax = x1 - ux; val ay = y1 - uy
        val bx = x2 + ux; val by = y2 + uy

        // The nib points across the segment's dominant axis.
        val dx: Float; val dy: Float
        if (abs(bx - ax) > abs(by - ay)) { dx = 0f; dy = half } else { dx = half; dy = 0f }

        moveTo(ax - dx, ay - dy)
        lineTo(bx - dx, by - dy)
        lineTo(bx + dx, by + dy)
        lineTo(ax + dx, ay + dy)
        close()
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

        moveTo(left[0], left[1])
        for (i in 1 until n) lineTo(left[i * 2], left[i * 2 + 1])
        for (i in n - 1 downTo 0) lineTo(right[i * 2], right[i * 2 + 1])
        close()
    }

    /** Two points is a bar; more is a curve. */
    private fun Path.addCentreline(pts: FloatArray, w: Float) {
        if (pts.size == 4) addBar(pts[0], pts[1], pts[2], pts[3], w)
        else addRibbon(pts, w)
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

    private val CENTRELINES: Map<Seg, FloatArray> = mapOf(

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
        // H and L run to the EXACT corners (changed 2026-08-08). They used to
        // stop at the hook landings, (0, 7.92) and (0, 92.08), which suited the
        // glyphs that join a diagonal to an arc - but it left every slash short
        // of its corner. The full slash and backslash are corner-to-corner now,
        // and the glyphs that used the old junctions (& a e) went back to square
        // corners instead.
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
        Seg.COL1 to Offset(DOT_AXIS_X, COL1_Y),
        Seg.COL2 to Offset(DOT_AXIS_X, COL2_Y),
        Seg.DP to Offset(DP_X, DP_Y),
        Seg.COMMA to Offset(DP_X, DP_Y),
    )

    /** The comma's tail: a bar from the dot down and to the left. */
    private val COMMA_TAIL: FloatArray = floatArrayOf(
        DP_X, DP_Y,
        DP_X - COMMA_TAIL_LEFT, DP_Y + COMMA_TAIL_DROP
    )

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

            for ((seg, pts) in CENTRELINES) {
                if (mask and seg.bit == 0L) continue
                lit.addCentreline(pts, strokeWidth)
            }

            if (mask and Seg.COMMA.bit != 0L) lit.addCentreline(COMMA_TAIL, strokeWidth)

            // Dots are squares of side twice the stroke - a macro photograph of a
            // real HP-55 shows the decimal point as a distinct square die, and the
            // same photograph shows every segment beaded out of small rectangular
            // dies. Sheared with everything else, so they lean rather than sitting
            // upright among leaning bars.
            for ((seg, centre) in DOT_CENTRES) {

                if (mask and seg.bit == 0L) continue

                lit.moveTo(centre.x - strokeWidth, centre.y - strokeWidth)
                lit.lineTo(centre.x + strokeWidth, centre.y - strokeWidth)
                lit.lineTo(centre.x + strokeWidth, centre.y + strokeWidth)
                lit.lineTo(centre.x - strokeWidth, centre.y + strokeWidth)
                lit.close()
            }

            if (lit.isEmpty) return@withTransform

            drawIntoCanvas { canvas ->

                val paint = Paint().apply {
                    isAntiAlias = true
                    this.color = color
                    style = PaintingStyle.Fill
                }

                // Filled INSIDE the shear, so the whole outline leans together.
                canvas.save()
                canvas.concat(SHEAR_MATRIX)
                canvas.drawPath(lit, paint)
                canvas.restore()
            }
        }
    }
}



