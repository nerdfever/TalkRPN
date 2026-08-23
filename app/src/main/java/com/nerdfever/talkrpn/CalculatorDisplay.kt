package com.nerdfever.talkrpn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kotlin.math.ceil

/*
 * THE calculator display: every register visible at once - T, Z, Y above
 * X, then LASTX and STO below it, two annunciator regions at the bottom.
 * X is the widest row and sits across the middle of the screen, where a
 * round display is at its widest; the other registers carry the same
 * number of cells at a smaller size, which makes them narrower too -
 * conveniently, since they sit on the narrower chords.
 *
 * ONE display, TWO callers: [CalcActivity], the calculator proper, takes
 * every default below - the values settled on the watch; the display-test
 * rig ([DisplayTestActivity]) passes its knobs into the same parameters.
 * Whatever the rig tunes, the calculator shows, with no second copy of the
 * layout to drift.
 *
 * The register labels are drawn in the system font, not the segment font,
 * because seven segments have no letters. That is precisely the gap that a
 * 14- or 16-segment cell would close.
 */

// ---------------------------------------------------------------------------
// Tweakables - the settled display geometry.
//
// Lengths are in the font's cell widths unless the name says otherwise.
// Anything in device pixels carries "Px" in its name. Mixing the two is the
// whole risk in this file, so each is named.
// ---------------------------------------------------------------------------

/**
 * How much smaller the other registers are than X, as a fraction of its cell height.
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
 * Settled by eye on the watch.
 */
internal const val DEFAULT_HEIGHT_FRACTION = 0.090f

/**
 * THE FIELD: every register shows its value in this many digit positions.
 *
 * A position is one full-width cell plus one gap. The field's right edge goes as
 * far right as the glass allows; the mantissa is left-justified from position 1,
 * and an exponent occupies just its own characters at the right end - see
 * [EXPONENT_DIGITS] for the zero-width blank.
 *
 * One per font. The dot font affords an extra position because its cells are
 * narrower and its radix pays for a cell of its own.
 */
internal const val SEGMENT_FIELD_POSITIONS = 9
internal const val DOT_FIELD_POSITIONS = 10

/**
 * The exponent's digits, at the field's end with no marker. The block is
 * ALWAYS [EXPONENT_DIGITS] + 1 positions: a lead position holding a blank -
 * or the MINUS, when the exponent is negative, which spends the separator's
 * seat exactly as the HP hardware did - then the two digits, zero-padded, in
 * the same two positions regardless of sign.
 *
 * The block is placed BY POSITION - each character at its position's
 * boundary - not right-justified by measured ink. Ink-justification made the
 * separation depend on which digits the exponent held: a 1 is zero-width in
 * the segment font, so "21" left more dark than "24" would, and a full
 * mantissa against "24" closed to less than the ordinary inter-digit gap.
 */
internal const val EXPONENT_DIGITS = 2

/**
 * The cyan field box's clearance: how far its line's INNER edge sits outside
 * the field's ink envelope, in pixels. Just enough that the hairline never
 * touches a glyph's outermost ink; any more and the box stops honestly
 * marking where the field is.
 */
private const val FIELD_BOX_CLEARANCE_PX = 2f

/** The field box hairline's width, in pixels. */
private const val FIELD_BOX_STROKE_PX = 1f

/**
 * Slack for the does-it-fit comparison, in cell units.
 *
 * A full field of full-width digits measures EXACTLY the field's width, so the
 * comparison sits on an equality that floating point tips either way - which
 * showed as the digit count flickering between 14 and 15 as the gap moved.
 * Far below anything visible; far above any rounding error.
 */
private const val FIT_SLACK_UNITS = 0.001f

/** Registers above X, in drawing order (T at the top, as HP draws it). */
private val UPPER_REGISTERS = listOf("T", "Z", "Y")

/** Registers below X, in drawing order. */
private val LOWER_REGISTERS = listOf("LASTX", "STO")

/** Breathing room at each end of a register row. */
private val SIDE_MARGIN = 6.dp

/** Space between the register block and the annunciators. */
private val ANNUNCIATOR_GAP = 8.dp

/** Space between the two annunciators. */
private val GAP_SMALL = 4.dp

private val TEXT_REGISTER_LABEL = 10.8.sp

/** Air between a label's right end and the field it names. */
private val LABEL_FIELD_CLEARANCE = 4.dp

/**
 * The label whose width sets the small rows' centring shift - a single letter,
 * the common case, so the field earns most of the glass.
 */
private const val SHIFT_REFERENCE_LABEL = "T"

private val TEXT_ANNUNCIATOR = 10.sp
private val ANNUNCIATOR_PAD_H = 6.dp
private val ANNUNCIATOR_PAD_V = 2.dp
private val ANNUNCIATOR_CORNER = 3.dp

/** The annunciator box outline. */
private val ANNUNCIATOR_BORDER = Color(0xFF4A4A4A)

/** Marks the start of an exponent, which never takes a radix of its own. */
private const val EXPONENT_MARKERS = "eE"

/**
 * The round-glass rescue shift's search bounds: how far the whole layout
 * may be dragged to bring clipped elements onto the glass, as a fraction
 * of the diameter, and the search grid's pitch in pixels. The bound keeps
 * a pathological layout from being dragged into absurdity; the pitch is
 * finer than anything the eye tracks at these sizes.
 */
private const val UNCLIP_MAX_SHIFT_FRACTION = 0.15f
private const val UNCLIP_STEP_PX = 2f

/**
 * How far inside the nominal pixel circle the ink must stay, as a
 * fraction of the diameter. The physical bezel and curved cover glass
 * hide the outermost ring of pixels that the framebuffer (and the
 * emulator's mask) still shows - the T label's corner proved it on the
 * wrist, still nibbled at 1.5%. 2% is about 9 px on the watch, and close
 * to the ceiling: much past it the X row's full field no longer fits the
 * judged circle at all, and the honest lever becomes hf, not margin.
 */
private const val UNCLIP_GLASS_MARGIN_FRACTION = 0.02f

/**
 * Reported rectangles are rounded to this before comparing, so relayout
 * float noise cannot re-trigger the search forever.
 */
private const val RECT_QUANTUM_PX = 0.5f

// ---------------------------------------------------------------------------
// Shared geometry helpers.
// ---------------------------------------------------------------------------

/**
 * Ink width of [n] full-width digit positions, in cell units: the cells, the
 * gaps between them, the slant's lean, and the stroke overhanging both ends.
 */
private fun fieldUnits(
    n: Int, gap: Float, slantDegrees: Float, descender: Float, stroke: Float,
): Float =
    n * TalkRpnFont.CELL_WIDTH + (n - 1) * gap +
        (TalkRpnFont.shearedWidth(slantDegrees, descender) - TalkRpnFont.CELL_WIDTH) +
        stroke

/**
 * Segment G's y inside a row's canvas: half a stroke of headroom, then half the
 * cap height, at the row's scale. The optical middle of the digits - what the
 * screen centres X on, and what each label centres itself against.
 */
private fun midBarYPx(cellHeightPx: Float, stroke: Float): Float =
    (stroke / 2f + TalkRpnFont.CELL_HEIGHT / 2f) *
        (cellHeightPx / TalkRpnFont.CELL_HEIGHT)

/**
 * Where a row's field begins, in its own canvas coordinates: the field centred
 * on the SCREEN's vertical axis, labels not counted.
 */
/**
 * The field's ink width in pixels. A field position is the LIVE font's
 * cell: fp means seven of WHATEVER is showing, not seven segment positions
 * with nine narrower dot cells rattling around inside them.
 */
private fun fieldWidthPx(
    fieldPositions: Int,
    useDotFont: Boolean,
    cellHeightPx: Float,
    gapUnits: Float,
    dotGapColumns: Float,
    slantDegrees: Float,
    descenderUnits: Float,
    strokeUnits: Float,
): Float =
    if (useDotFont) Hdls1414Font.fieldWidth(fieldPositions, cellHeightPx, dotGapColumns)
    else fieldUnits(fieldPositions, gapUnits, slantDegrees, descenderUnits, strokeUnits) *
        (cellHeightPx / TalkRpnFont.CELL_HEIGHT)

private fun fieldLeftPx(
    fieldPositions: Int,
    useDotFont: Boolean,
    cellHeightPx: Float,
    gapUnits: Float,
    dotGapColumns: Float,
    slantDegrees: Float,
    descenderUnits: Float,
    strokeUnits: Float,
    screenPx: Float,
    leftInRootPx: Float,
): Float =
    screenPx / 2f -
        fieldWidthPx(
            fieldPositions, useDotFont, cellHeightPx, gapUnits, dotGapColumns,
            slantDegrees, descenderUnits, strokeUnits
        ) / 2f - leftInRootPx

/**
 * Whether a value is a NUMBER - which the radix, grouping and exponent rules
 * apply to. Text passes through untouched: "Syntax error" must keep its e and
 * gain no trailing radix.
 */
internal fun isNumeric(value: String): Boolean =
    value.isNotEmpty() && value.all {
        it.isDigit() || it == NumberFormatter.RADIX ||
            it == NumberFormatter.GROUP_SEPARATOR || it == '-' || it in EXPONENT_MARKERS
    }

/**
 * Where [value]'s exponent starts, or -1 when it has none.
 *
 * A marker only counts when what follows it is an optional minus and digits,
 * and what precedes it is numeric - so "6.020E23" gives up its E while
 * "Syntax error" keeps every e it has.
 */
internal fun exponentMarkerAt(value: String): Int {

    val at = value.indexOfFirst { it in EXPONENT_MARKERS }
    if (at <= 0) return -1

    if (!isNumeric(value.take(at))) return -1

    val tail = value.substring(at + 1).removePrefix("-")
    if (tail.isEmpty() || !tail.all { it.isDigit() }) return -1

    return at
}

/** Device pixels to Dp. Compose lays out in Dp; the font works in pixels. */
private fun pxToDp(px: Float, density: Float) = (px / density).dp

/** The full ink height of a row's canvas, for chord limits and canvas sizing. */
private fun inkHeightPx(cellHeightPx: Float, descenderUnits: Float, strokeUnits: Float) =
    cellHeightPx * (TalkRpnFont.totalHeight(descenderUnits) + strokeUnits) / TalkRpnFont.CELL_HEIGHT

/**
 * A row canvas's exact laid-out height in pixels: its ink rounded up, plus the
 * safety pixel - the same arithmetic [canvasHeightDp] hands to Compose, kept in
 * one place so spacing and sizing can never disagree about it.
 */
private fun canvasPx(cellHeightPx: Float, descenderUnits: Float, strokeUnits: Float) =
    ceil(inkHeightPx(cellHeightPx, descenderUnits, strokeUnits)) + 1f

/**
 * How far a row's baseline sits below the TOP of its own canvas: half a stroke
 * of headroom above the cap line, then the cap height. Rows draw their ink box
 * from the canvas top, so this needs no descender term.
 */
private fun baselineFromTopPx(cellHeightPx: Float, strokeUnits: Float) =
    cellHeightPx * (strokeUnits / 2f + TalkRpnFont.CELL_HEIGHT) /
        TalkRpnFont.CELL_HEIGHT

/**
 * The Column gap that leaves two stacked rows exactly [vpitchPx] apart, baseline
 * to baseline: the pitch, less everything already lying between the two
 * baselines - the ABOVE row from its baseline down to its canvas bottom
 * (descender, half stroke, and the canvas's rounding padding), and the BELOW
 * row from its canvas top down to its baseline. Depends on both rows, because
 * they may be different sizes.
 */
private fun rowGapPx(
    vpitchPx: Float, abovePx: Float, belowPx: Float, descenderUnits: Float, strokeUnits: Float,
) =
    vpitchPx - (canvasPx(abovePx, descenderUnits, strokeUnits) - baselineFromTopPx(abovePx, strokeUnits)) -
        baselineFromTopPx(belowPx, strokeUnits)

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
private fun canvasHeightDp(px: Float, density: Float, descenderUnits: Float, strokeUnits: Float) =
    (canvasPx(px, descenderUnits, strokeUnits) / density).dp

// ---------------------------------------------------------------------------
// The display.
// ---------------------------------------------------------------------------

/**
 * The whole register stack plus annunciators, laid out for a round screen.
 *
 * [values] maps register names ([UPPER_REGISTERS], "X", [LOWER_REGISTERS]) to
 * their display text. Every other parameter defaults to the settled geometry;
 * only the rig passes anything else.
 */
/** The display under a [DisplayKnobs]: every parameter from the knobs' readings. */
@Composable
fun CalculatorDisplay(
    values: Map<String, String>,
    knobs: DisplayKnobs,
    modifier: Modifier = Modifier,
    angleAnnunciator: String = "DEG",
) = CalculatorDisplay(
    values = values,
    modifier = modifier,
    angleAnnunciator = angleAnnunciator,
    heightFraction = knobs.heightFraction,
    gapUnits = knobs.gapUnits,
    vgapUnits = knobs.vgapUnits,
    fieldPositions = knobs.fieldPositions,
    useDotFont = knobs.useDotFont,
    dotGapColumns = knobs.dotGapColumns,
    showFieldBoxes = knobs.showFieldBoxes,
    slantDegrees = knobs.slantDegrees,
    strokeUnits = knobs.strokeUnits,
)

@Composable
fun CalculatorDisplay(
    values: Map<String, String>,
    modifier: Modifier = Modifier,
    heightFraction: Float = DEFAULT_HEIGHT_FRACTION,
    gapUnits: Float = TalkRpnFont.DEFAULT_GAP,
    vgapUnits: Float = TalkRpnFont.VGAP,
    fieldPositions: Int = SEGMENT_FIELD_POSITIONS,
    useDotFont: Boolean = false,
    dotGapColumns: Float = Hdls1414Font.CHARACTER_GAP_COLUMNS,
    showFieldBoxes: Boolean = false,
    slantDegrees: Float = TalkRpnFont.SLANT_DEGREES,
    strokeUnits: Float = TalkRpnFont.STROKE,
    angleAnnunciator: String = "DEG",
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics

    // The screen is round, and the layout has to know it. Taken as a circle whose
    // diameter is the narrower side.
    val screenPx = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()

    // Descender depth (0.625) is settled in the font with no knob; it still
    // travels as a plain value so a knob could return without re-threading.
    val descenderUnits = TalkRpnFont.DESCENDER_DEPTH

    val xCellHeightPx = heightFraction * screenPx
    val smallCellHeightPx = xCellHeightPx * SMALL_ROW_SCALE

    // ---- Out of cell-width units and into pixels ------------------------------
    //
    // ONE conversion, so there is one place to look when a length is the wrong
    // size. The font's coordinates are cell widths and so are this screen's, so
    // there is nothing to convert between them.

    // Pixels per cell width, at each row's own size. X's is the reference unit
    // the knobs are quoted in; the small rows carry their own smaller unit, so
    // their spacing scales down with them.
    val unitPx = xCellHeightPx / TalkRpnFont.CELL_HEIGHT
    val smallUnitPx = smallCellHeightPx / TalkRpnFont.CELL_HEIGHT

    // ---- Row spacing: each row owns half the vgap, in its own units -----------
    //
    // Every row is a box reaching from half a vgap above its cap line to half a
    // vgap below its descender bar, all at the row's own scale, and the boxes
    // stack touching. Between equal rows a seam's pitch is then exactly the
    // font's VPITCH = TOTAL_HEIGHT + VGAP; between unequal rows each side
    // contributes its half at its own size, so the small rows close up by
    // [SMALL_ROW_SCALE] instead of floating in reference-size air.
    //
    // The descender depth rides along: deeper tails push the rows apart by
    // themselves, holding the tuned vgap clearance.
    fun seamPitchPx(aboveUnitPx: Float, belowUnitPx: Float) =
        (descenderUnits + vgapUnits / 2f) * aboveUnitPx +
            (vgapUnits / 2f + TalkRpnFont.CELL_HEIGHT) * belowUnitPx

    // A Column stacks canvases and separates them with gaps, but pitch is stated
    // baseline to baseline - so each gap is its seam's pitch minus the ink
    // already lying between those two baselines: the tail of the row above,
    // plus the whole of the row below down to its own baseline.
    //
    // Three junctions, because X is a different size from its neighbours.

    val gapSmallToSmallPx =
        rowGapPx(seamPitchPx(smallUnitPx, smallUnitPx), smallCellHeightPx, smallCellHeightPx, descenderUnits, strokeUnits)
    val gapSmallToXPx =
        rowGapPx(seamPitchPx(smallUnitPx, unitPx), smallCellHeightPx, xCellHeightPx, descenderUnits, strokeUnits)
    val gapXToSmallPx =
        rowGapPx(seamPitchPx(unitPx, smallUnitPx), xCellHeightPx, smallCellHeightPx, descenderUnits, strokeUnits)

    // ---- Put X's MIDDLE BAR on the screen's diameter --------------------------
    //
    // The widest chord of a round display passes through its centre, so the
    // widest row earns the most width when the OPTICAL middle of its digits -
    // segment G - sits exactly there. Centring the stack as a block put X
    // wherever the labels' and annunciators' heights happened to leave it.
    //
    // The stack therefore hangs from a computed top spacer: the distance from
    // the screen centre up to X's canvas top, less everything stacked above X.

    val xMidBarInCanvasPx = midBarYPx(xCellHeightPx, strokeUnits)

    // What sits above X's canvas: the upper rows' canvases and the gaps between.
    // RAW gaps, negative included - a clamped spacer's shortfall comes back as
    // an upward offset on the rows below it, so the sum is what counts.
    val smallCanvasPx = canvasPx(smallCellHeightPx, descenderUnits, strokeUnits)
    val aboveXPx = UPPER_REGISTERS.size * smallCanvasPx +
        (UPPER_REGISTERS.size - 1) * gapSmallToSmallPx +
        gapSmallToXPx

    val topSpacerPx = (screenPx / 2f - xMidBarInCanvasPx - aboveXPx).coerceAtLeast(0f)

    // The small rows centre "label + field" as one unit, so a labelled register
    // reads as the centred thing - X, having no label, centres its field alone.
    // ONE shift for every small row, so the fields all stay aligned with each
    // other - and it is half of a SINGLE-LETTER label's block, not the widest:
    // T Z Y are the common case, and charging every row for LASTX dragged the
    // whole stack left of where the eye wants it. The two long labels simply
    // overhang further into the left margin.
    val textMeasurer = rememberTextMeasurer()
    val smallFieldShiftPx = remember(metrics.density) {
        val referenceLabelPx =
            textMeasurer.measure(SHIFT_REFERENCE_LABEL, TextStyle(fontSize = TEXT_REGISTER_LABEL)).size.width
        (referenceLabelPx + LABEL_FIELD_CLEARANCE.value * metrics.density) / 2f
    }

    // ---- The round-glass rescue shift ---------------------------------------
    //
    // A rectangular layout on a round screen clips its corners - the T
    // label was the live case. Every inked element reports the rectangle
    // it landed on, the applied shift is backed out so the map holds
    // SHIFT-FREE geometry (which is what makes the loop converge), and
    // [unclipShift] finds the one translation that brings as much as
    // possible inside the circle - a layout that already fits gets (0,0)
    // and is left exactly alone.
    //
    // Applied as a plain y-offset on the whole column, but as a +dx on
    // every FIELD ANCHOR horizontally: the rows re-centre their fields on
    // the screen axis through positionInRoot, so a plain x-offset would
    // self-cancel.
    val intrinsicRects = remember { mutableStateMapOf<String, FitRect>() }

    // On a RECTANGULAR screen (a phone - adb installs there work fine)
    // there is no circle to escape, so the rescue holds still and the
    // layout simply uses the frame.
    val isRound = LocalContext.current.resources.configuration.isScreenRound

    val shift by remember {
        derivedStateOf {
            if (!isRound) FitShift(0f, 0f)
            else unclipShift(
                intrinsicRects.values.toList(), screenPx,
                screenPx * UNCLIP_GLASS_MARGIN_FRACTION,
                screenPx * UNCLIP_MAX_SHIFT_FRACTION, UNCLIP_STEP_PX,
            )
        }
    }

    fun reportRect(key: String, asLaid: FitRect) {

        fun quantized(v: Float) = kotlin.math.round(v / RECT_QUANTUM_PX) * RECT_QUANTUM_PX

        val intrinsic = FitRect(
            quantized(asLaid.left - shift.dx), quantized(asLaid.top - shift.dy),
            quantized(asLaid.right - shift.dx), quantized(asLaid.bottom - shift.dy),
        )

        if (intrinsicRects[key] != intrinsic) intrinsicRects[key] = intrinsic
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .offset(y = pxToDp(shift.dy, metrics.density))
            .padding(horizontal = SIDE_MARGIN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // Hangs the stack so X's segment G lands on the diameter; see the
        // derivation of topSpacerPx above.
        Spacer(Modifier.height(pxToDp(topSpacerPx, metrics.density)))

        // A Compose spacer cannot be negative, so when the vpitch drops
        // below the rows-touch point each clamped gap's shortfall is
        // accumulated here and paid back as an upward offset on everything
        // below it. That is what lets the rig's control show OVERLAP rather
        // than silently stopping at the touch point.
        var overlapPx = 0f

        for ((index, name) in UPPER_REGISTERS.withIndex()) {

            RegisterRow(
                name, values[name].orEmpty(), fieldPositions, useDotFont, showFieldBoxes,
                smallCellHeightPx, gapUnits, dotGapColumns,
                LedPalette.LIT, metrics.density, screenPx, smallFieldShiftPx, slantDegrees,
                descenderUnits, strokeUnits, shift.dx, { reportRect(name, it) },
                Modifier.offset(y = pxToDp(overlapPx, metrics.density))
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

        // The field is screen-centred, so its root-coordinate edges fall
        // out of its width alone - which is what X reports to the rescue.
        val xFieldWidthPx = fieldWidthPx(
            fieldPositions, useDotFont, xCellHeightPx, gapUnits, dotGapColumns,
            slantDegrees, descenderUnits, strokeUnits
        )

        Canvas(
            modifier = Modifier
                .offset(y = pxToDp(overlapPx, metrics.density))
                .fillMaxWidth()
                .height(canvasHeightDp(xCellHeightPx, metrics.density, descenderUnits, strokeUnits))
                .onGloballyPositioned {
                    xLeftInRootPx = it.positionInRoot().x

                    // Cap-band bottom, like the rows: X's alphabet has
                    // no descenders either.
                    val inkLeft = screenPx / 2f - xFieldWidthPx / 2f + shift.dx
                    reportRect(
                        "X",
                        FitRect(
                            inkLeft, it.positionInRoot().y,
                            inkLeft + xFieldWidthPx,
                            it.positionInRoot().y + xCellHeightPx +
                                strokeUnits * (xCellHeightPx / TalkRpnFont.CELL_HEIGHT),
                        )
                    )
                }
        ) {
            drawRegister(
                values["X"].orEmpty(), fieldPositions, useDotFont, showFieldBoxes,
                xCellHeightPx, gapUnits, dotGapColumns, LedPalette.LIT,
                fieldLeftPx(
                    fieldPositions, useDotFont, xCellHeightPx, gapUnits, dotGapColumns,
                    slantDegrees, descenderUnits, strokeUnits, screenPx, xLeftInRootPx
                ) + shift.dx,
                slantDegrees, descenderUnits, strokeUnits
            )
        }

        // X down to the first of the lower registers: the row below is now the
        // smaller one, so this gap is the wider of the three.
        Spacer(Modifier.height(pxToDp(gapXToSmallPx.coerceAtLeast(0f), metrics.density)))
        overlapPx += minOf(gapXToSmallPx, 0f)

        for (name in LOWER_REGISTERS) {

            RegisterRow(
                name, values[name].orEmpty(), fieldPositions, useDotFont, showFieldBoxes,
                smallCellHeightPx, gapUnits, dotGapColumns,
                LedPalette.LIT, metrics.density, screenPx, smallFieldShiftPx, slantDegrees,
                descenderUnits, strokeUnits, shift.dx, { reportRect(name, it) },
                Modifier.offset(y = pxToDp(overlapPx, metrics.density))
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
            modifier = Modifier
                .offset(
                    x = pxToDp(shift.dx, metrics.density),
                    y = pxToDp(overlapPx, metrics.density),
                )
                .onGloballyPositioned {
                    reportRect(
                        "annunciators",
                        FitRect(
                            it.positionInRoot().x, it.positionInRoot().y,
                            it.positionInRoot().x + it.size.width,
                            it.positionInRoot().y + it.size.height,
                        )
                    )
                },
        ) {
            // The angle annunciator is LIVE - the engine's mode. SI
            // remains the units placeholder.
            Annunciator(angleAnnunciator)
            Spacer(Modifier.width(GAP_SMALL))
            Annunciator("SI")
        }
    }
}

/** One of the smaller registers: a name at the left, the value in its field. */
@Composable
private fun RegisterRow(
    name: String,
    value: String,
    fieldPositions: Int,
    useDotFont: Boolean,
    showFieldBox: Boolean,
    cellHeightPx: Float,
    gapUnits: Float,
    dotGapColumns: Float,
    color: Color,
    density: Float,
    screenPx: Float,
    fieldShiftPx: Float,
    slantDegrees: Float,
    descenderUnits: Float,
    strokeUnits: Float,
    rescueShiftXPx: Float,
    reportRect: (FitRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Where this row sits horizontally, so the screen's axis can be found in
    // its own coordinates.
    var leftInRootPx by remember { mutableStateOf(0f) }

    // The field centred on the screen's axis, then shifted right by half the
    // widest label block, so LABEL PLUS FIELD is the centred unit. The same
    // shift for every small row keeps their fields aligned with each other.
    // The rescue shift rides the same anchor.
    val rowFieldLeftPx =
        fieldLeftPx(
            fieldPositions, useDotFont, cellHeightPx, gapUnits, dotGapColumns,
            slantDegrees, descenderUnits, strokeUnits, screenPx, leftInRootPx
        ) + fieldShiftPx + rescueShiftXPx

    // What this row reports to the rescue: label through field, one rect.
    // The field is screen-centred, so its root-coordinate left falls out of
    // its width and the shifts alone.
    val textMeasurer = rememberTextMeasurer()
    val labelWidthPx = remember(name) {
        textMeasurer.measure(name, TextStyle(fontSize = TEXT_REGISTER_LABEL)).size.width.toFloat()
    }
    val rowFieldWidthPx = fieldWidthPx(
        fieldPositions, useDotFont, cellHeightPx, gapUnits, dotGapColumns,
        slantDegrees, descenderUnits, strokeUnits
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                leftInRootPx = it.positionInRoot().x

                // The reported ink stops at the BASELINE bar, not the
                // descender band: a register only ever shows the
                // formatter's alphabet - digits, sign, radix, E and the
                // error words - and the labels are capitals; none of it
                // descends. (The rig's lowercase samples can dip below;
                // a measuring screen wears that.)
                val fieldLeftRootPx =
                    screenPx / 2f - rowFieldWidthPx / 2f + fieldShiftPx + rescueShiftXPx
                reportRect(
                    FitRect(
                        fieldLeftRootPx - LABEL_FIELD_CLEARANCE.value * density - labelWidthPx,
                        it.positionInRoot().y,
                        fieldLeftRootPx + rowFieldWidthPx,
                        it.positionInRoot().y + cellHeightPx +
                            strokeUnits * (cellHeightPx / TalkRpnFont.CELL_HEIGHT),
                    )
                )
            }
    ) {

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeightDp(cellHeightPx, density, descenderUnits, strokeUnits))
        ) {
            drawRegister(
                value, fieldPositions, useDotFont, showFieldBox, cellHeightPx, gapUnits, dotGapColumns,
                color, rowFieldLeftPx, slantDegrees, descenderUnits, strokeUnits
            )
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
                .height(pxToDp(2f * midBarYPx(cellHeightPx, strokeUnits), density)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = name,
                color = LedPalette.LABEL,
                fontSize = TEXT_REGISTER_LABEL,
                maxLines = 1,
                softWrap = false,
                // Measured UNBOUNDED and end-aligned, so a label longer
                // than the box (LASTX) overhangs into the left margin
                // rather than losing its right end to the box's clip.
                modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true),
            )
        }
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
 * Draws one register's value into its [fieldPositions]-position field, whose
 * position 1 begins at [fieldLeftPx] - the caller centres the field on the
 * screen's vertical axis via [fieldLeftPx] (the function of the same name).
 *
 * Mantissa left-justified from position 1; exponent, when there is one,
 * right-justified into exactly its own characters' positions, no marker, the
 * separating blank zero-width - see [EXPONENT_DIGITS].
 *
 * TWO FONTS, ONE FIELD MEANING. A field position is one cell OF THE LIVE FONT,
 * so fp = 7 means seven segment positions or seven dot cells, whichever is
 * showing - the box resizes with the font rather than leaving the narrower
 * dot cells rattling inside a segment-sized frame. The dot font draws in its
 * own default colour (its whole point is looking different) and its own fixed
 * pitch, where '.' and ',' take a whole cell rather than living in a gap -
 * which the formatter does not yet know, so a dotted number spends one more
 * dot position than dsp() planned for.
 *
 * Anything the segment font has no glyph for is dropped by its layout; the dot
 * font draws a blank cell instead, which is what fixed pitch means.
 */
private fun DrawScope.drawRegister(
    value: String,
    fieldPositions: Int,
    useDotFont: Boolean,
    showFieldBox: Boolean,
    cellHeightPx: Float,
    gapUnits: Float,
    dotGapColumns: Float,
    color: Color,
    fieldLeftPx: Float,
    slantDegrees: Float,
    descenderUnits: Float,
    strokeUnits: Float,
) {
    if (value.isEmpty() || cellHeightPx <= 0f) return

    val scale = cellHeightPx / TalkRpnFont.CELL_HEIGHT

    // The ink width of a run of n full-width digit positions, at this row's size.
    fun positionsPx(n: Int): Float = fieldUnits(n, gapUnits, slantDegrees, descenderUnits, strokeUnits) * scale

    // THE FIELD: position 1 at [fieldLeftPx], the caller having centred it -
    // and its width in the LIVE font's positions, matching fieldLeftPx.
    val fieldRightPx = fieldLeftPx +
        if (useDotFont) Hdls1414Font.fieldWidth(fieldPositions, cellHeightPx, dotGapColumns)
        else positionsPx(fieldPositions)

    // The field box made visible, for fitting work: a hairline around the
    // whole register's display area, in the diagnostic cyan so it cannot be
    // mistaken for lit ink. On the rig's boxes button.
    //
    // The rectangle fieldLeft..fieldRight x 0..inkHeight IS the ink envelope -
    // fieldUnits carries the stroke's overhang at both ends, and the row draws
    // its ink from the canvas top - so the box inflates OUTWARD from there by
    // its clearance, plus half its own line so the line's inner edge is what
    // sits at the clearance.
    if (showFieldBox) {

        val boxOutsetPx = FIELD_BOX_CLEARANCE_PX + FIELD_BOX_STROKE_PX / 2f

        // The box hugs the ACTIVE font's ink. The dot font has no descender -
        // its ink stops half a dot below the bottom lattice row - where the
        // segment font's runs on down through the descender band.
        val boxInkHeightPx =
            if (useDotFont) cellHeightPx * Hdls1414Font.INK_HEIGHT / Hdls1414Font.CELL_HEIGHT
            else inkHeightPx(cellHeightPx, descenderUnits, strokeUnits)

        drawRect(
            color = LedPalette.FIELD_BOUNDS,
            topLeft = Offset(fieldLeftPx - boxOutsetPx, -boxOutsetPx),
            size = Size(
                fieldRightPx - fieldLeftPx + 2f * boxOutsetPx,
                boxInkHeightPx + 2f * boxOutsetPx,
            ),
            style = Stroke(width = FIELD_BOX_STROKE_PX),
        )
    }

    // Split off the exponent. The marker never reaches the screen: the mantissa
    // is left-justified from position 1, the exponent right-justified into the
    // field's last positions, and the inter-cell gap between the two blocks
    // is all the separating the eye needs - the blank is zero-width.
    val markerAt = exponentMarkerAt(value)
    var mantissa = if (markerAt >= 0) value.take(markerAt) else value
    val exponent = if (markerAt >= 0) value.substring(markerAt + 1) else ""

    // The mantissa may not enter the exponent block, which is a constant
    // EXPONENT_DIGITS + 1 positions whatever the sign - the minus, when
    // there is one, sits in the blank's seat. Overflow loses its RIGHT end -
    // it is the left-justified block.
    val mantissaPositions =
        if (exponent.isEmpty()) fieldPositions
        else fieldPositions - EXPONENT_DIGITS - 1

    // Where the exponent's ink starts, as a position index: a negative
    // exponent's minus takes the blank's seat, a positive one leaves it dark.
    val exponentStartPosition =
        if (exponent.startsWith("-")) mantissaPositions else mantissaPositions + 1

    if (useDotFont) {

        // Fixed pitch makes the fit check trivial. The rig's g knob binds to
        // [dotGapColumns] while this font is live - the dot font's own gap, in
        // its own unit of blank columns.
        with(Hdls1414Font) {

            val mantissaMaxPx = fieldWidth(mantissaPositions, cellHeightPx, dotGapColumns)

            while (mantissa.isNotEmpty() &&
                measureWidth(mantissa, cellHeightPx, dotGapColumns) > mantissaMaxPx
            ) {
                mantissa = mantissa.dropLast(1)
            }

            if (mantissa.isNotEmpty()) {
                drawHdls1414Text(
                    text = mantissa,
                    inkOrigin = Offset(fieldLeftPx, 0f),
                    cellHeight = cellHeightPx,
                    gapColumns = dotGapColumns,
                )
            }

            if (exponent.isNotEmpty()) {

                // Placed by POSITION - fixed pitch makes the cell index the
                // whole story.
                val advancePx = (DOT_COLUMNS + dotGapColumns) * COLUMN_PITCH *
                    cellHeightPx / CELL_HEIGHT

                drawHdls1414Text(
                    text = exponent,
                    inkOrigin = Offset(
                        fieldLeftPx + exponentStartPosition * advancePx,
                        0f,
                    ),
                    cellHeight = cellHeightPx,
                    gapColumns = dotGapColumns,
                )
            }
        }

        return
    }

    // The check runs in the font's own UNITS, not pixels, so every row reaches
    // the same verdict for the same text - checked in pixels, the small rows'
    // different rounding could disagree with X's at the same gap. A TRAILING
    // radix or separator is not counted: it lives in the gap after its digit,
    // costs no position, and may poke past the field into the darkness.
    val mantissaMaxUnits = fieldUnits(mantissaPositions, gapUnits, slantDegrees, descenderUnits, strokeUnits)

    fun fits(text: String): Boolean {
        val counted = text.trimEnd(NumberFormatter.RADIX, NumberFormatter.GROUP_SEPARATOR)
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
                strokeWidth = strokeUnits,
                descender = descenderUnits
            )
        }

        if (exponent.isNotEmpty()) {

            // Drawn CELL BY CELL, each character in its own full-width
            // position, exactly as fixed-cell hardware holds its exponent -
            // the block's geometry must never depend on which characters it
            // holds. A position is a full cell plus its leading gap.
            val positionPitchPx = (TalkRpnFont.CELL_WIDTH + gapUnits) * scale
            val overhangPx = strokeUnits / 2f * scale

            with(TalkRpnFont) {
                for ((index, character) in exponent.withIndex()) {

                    val mask = TalkRpnGlyphs.maskFor(character) ?: continue

                    drawTalkRpnCell(
                        mask = mask,
                        origin = Offset(
                            fieldLeftPx + (exponentStartPosition + index) * positionPitchPx +
                                overhangPx,
                            overhangPx,
                        ),
                        cellHeight = cellHeightPx,
                        color = color,
                        strokeWidth = strokeUnits,
                        slantDegrees = slantDegrees,
                        descender = descenderUnits,
                    )
                }
            }
        }
    }
}
