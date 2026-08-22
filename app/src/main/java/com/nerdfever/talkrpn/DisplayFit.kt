package com.nerdfever.talkrpn

import kotlin.math.sqrt

/*
 * DisplayFit - the round-glass rescue shift.
 *
 * A round display clips whatever a rectangular layout puts in its corners;
 * the live case is the T register's label, which lands just left of the
 * glass. Rather than special-casing elements, the display reports the
 * rectangles it inked and this routine finds ONE (dx, dy) translation of
 * the whole layout that brings them all inside the circle - the smallest
 * such shift, so a layout that already fits is left exactly alone. When no
 * shift can fit everything, it settles for the shift that leaves the least
 * total overhang, so the elements that CAN be rescued are.
 *
 * Pure geometry, no Android in it, tested on the JVM like the engine.
 */

/** One inked rectangle, in screen pixels, y down. */
internal data class FitRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** The rescue translation, in screen pixels. */
internal data class FitShift(val dx: Float, val dy: Float)

/**
 * Scores below this far apart count as equal, so the tie goes to the
 * smaller shift instead of to float noise.
 */
private const val SCORE_TIE_PX = 0.01f

/**
 * The smallest whole-layout translation that brings every rectangle inside
 * the circle of [diameterPx] (anchored at the screen's top-left), or the
 * least-overhang shift when none can. Searched on a [stepPx] grid out to
 * +-[maxShiftPx] on both axes - the circle is convex, so a rectangle is
 * inside exactly when its four corners are.
 */
internal fun unclipShift(
    rects: List<FitRect>,
    diameterPx: Float,
    maxShiftPx: Float,
    stepPx: Float,
): FitShift {

    if (rects.isEmpty()) return FitShift(0f, 0f)

    val radius = diameterPx / 2f

    // How far a rect's worst corner pokes past the glass, zero when inside.
    fun overhang(rect: FitRect, dx: Float, dy: Float): Float {

        var worst = 0f

        for (x in floatArrayOf(rect.left + dx - radius, rect.right + dx - radius)) {
            for (y in floatArrayOf(rect.top + dy - radius, rect.bottom + dy - radius)) {
                val out = sqrt(x * x + y * y) - radius
                if (out > worst) worst = out
            }
        }

        return worst
    }

    fun totalOverhang(dx: Float, dy: Float): Float {
        var sum = 0f
        for (rect in rects) sum += overhang(rect, dx, dy)
        return sum
    }

    // The unshifted layout is the baseline - and the answer, when it
    // already fits.
    var bestDx = 0f
    var bestDy = 0f
    var bestScore = totalOverhang(0f, 0f)
    if (bestScore <= 0f) return FitShift(0f, 0f)

    // Exhaustive grid search: the counts are small and this runs once per
    // layout change, not per frame. A strictly better score wins; an equal
    // one only wins by being a SMALLER move, so (0,0) survives all ties.
    var dy = -maxShiftPx
    while (dy <= maxShiftPx) {

        var dx = -maxShiftPx
        while (dx <= maxShiftPx) {

            val score = totalOverhang(dx, dy)

            val better = score < bestScore - SCORE_TIE_PX
            val asGoodButSmaller = score < bestScore + SCORE_TIE_PX &&
                dx * dx + dy * dy < bestDx * bestDx + bestDy * bestDy

            if (better || asGoodButSmaller) {
                bestDx = dx; bestDy = dy; bestScore = score
            }

            dx += stepPx
        }

        dy += stepPx
    }

    return FitShift(bestDx, bestDy)
}
