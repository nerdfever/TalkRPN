package com.nerdfever.talkrpn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/*
 * Hdls1414Font - a 5 x 7 dot-matrix display cell.
 *
 * The alternate to TalkRpnFont: where that one draws segments, this one draws
 * dots. It records the HDLS-1414 character set; details in HISTORY.md.
 *
 * ---------------------------------------------------------------------------
 * Coordinate system
 * ---------------------------------------------------------------------------
 * Everything here is a DOT CENTRE. Dots are drawn as squares centred on those
 * points, so a cell is a lattice of 35 points until it is drawn. Origin is the
 * centre of the top-left dot; x runs right, y runs down.
 *
 * THE UNIT: one column pitch - dot centre to dot centre, across - is exactly 1.
 * Every length in this file is in that one unit, horizontally and vertically
 * alike. There is no second unit anywhere, and nothing here is in pixels, dp or
 * millimetres.
 *
 * SCALE IS THE CALLER'S. The drawing calls take a cellHeight in pixels and
 * everything is scaled from it, so the same lattice serves a 2 mm watch row and
 * a 300 mm reference sheet unchanged.
 *
 * COLOUR: the caller's to override, but unlike the scale it has a default -
 * [NEON_ORANGE], this font's own look, next to the other tweakables.
 *
 * ---------------------------------------------------------------------------
 * No descender row
 * ---------------------------------------------------------------------------
 * Seven rows, and the baseline is the last of them. The lower case in
 * [Hdls1414Glyphs] is therefore drawn RAISED - g p q y sit entirely above the
 * baseline, in short forms fitted inside the grid - which is the character of
 * this font rather than a compromise to be corrected. TalkRpnFont, which does
 * have a descender, is the place to go for tails.
 *
 * ---------------------------------------------------------------------------
 * Fixed pitch
 * ---------------------------------------------------------------------------
 * Unlike TalkRpnFont, which spaces by ink, this font is FIXED PITCH: every
 * character claims the same five columns whether it fills them or not. That is
 * what the hardware does - each cell is a physical block of 35 LEDs - and on a
 * lattice this coarse there is no ink extent worth measuring anyway. A 1 really
 * does sit as far from its neighbour as a W does.
 */

object Hdls1414Font {

    // ---- Fixed by the hardware -----------------------------------------------
    //
    // Not tweakables. The part is a 5 x 7 matrix and the glyph table in
    // [Hdls1414Glyphs] is written to that shape; changing either number here
    // would invalidate every entry in it.

    const val DOT_COLUMNS = 5
    const val DOT_ROWS = 7

    /** Dots in one cell - and so the number of bits in a glyph mask. */
    const val DOTS_PER_CELL = DOT_COLUMNS * DOT_ROWS

    // ---- Tweakables, in column pitches ---------------------------------------
    //
    // Every value someone might plausibly want to turn, in one place. The rest
    // of this file derives from these. Each is explained in the block after the
    // values, in this order.

    const val COLUMN_PITCH = 1f            // by definition; all other measures are relative to this
    const val ROW_PITCH = 1.098f           // dot centre to dot centre, down
    const val DOT_SIDE = 0.7033f           // side of one lit dot's square
    const val CHARACTER_GAP_COLUMNS = 1.0f // blank columns between one character's cell and the next
    const val VGAP = 3.562f                // vertical space between lines: bottom dot row to next line's top dot row
    val NEON_ORANGE = Color(0xFFFF5F1F)    // the default ink colour

    /*
     * COLUMN_PITCH - dot centre to dot centre across a row. This is the unit's
     * definition, so it is 1 by construction and must never be anything else.
     *
     * ROW_PITCH - dot centre to dot centre down a column. NOT equal to the
     * column pitch: the part's dimension callouts put a dot column 0.020 inch
     * apart and a dot row 0.022 - a ratio of exactly 1.10, stated here to the
     * millimetre callouts' precision. Set it to 1 and the whole font squats.
     *
     * DOT_SIDE - the side of one lit dot's square, as a fraction of the column
     * pitch, since that is the unit. The value is what the datasheet's own
     * character chart draws; the physical die is smaller (0.4902 of the
     * pitch), but a lit LED reads far fatter than its die, which is surely why
     * the chart fattens it. Below 1 the dots stand separate, which is what a
     * dot matrix looks like; nothing stops it going above 1 - a heavy,
     * blooming look, worth trying on the watch where small text needs the
     * weight.
     *
     * CHARACTER_GAP_COLUMNS - blank columns between one character's five and
     * the next character's five. In COLUMNS rather than in the unit directly,
     * because that is how the eye reads it on a lattice. Tuned by eye for the
     * watch: the real part spaces its characters far wider (3.75 blank
     * columns - HISTORY.md), an extravagance a 4-character desk display can
     * afford and a 10-position watch field cannot. A fraction is legal and
     * changes only the spacing, never the lattice inside a cell.
     *
     * VGAP - the vertical space between stacked lines of characters: one
     * line's bottom dot-centre row down to the next line's top dot-centre row.
     * The part itself is a single line, so no physical spacing exists; this is
     * the spacing the datasheet's chart puts between its rows of characters,
     * the only vertical rhythm the sheet exhibits. Nothing consumes it yet -
     * it waits for the multi-line calculator display.
     *
     * NEON_ORANGE - the colour the font draws in unless told otherwise. Neon
     * (fluorescent) orange proper lies outside what any screen can show; this
     * is the nearest sRGB can get at full brightness - red at maximum, green
     * carrying the orange, a whisper of blue for the fluorescent glare that
     * pure FF6600 lacks.
     */

    // ---- Derived from the tweakables -----------------------------------------

    /** Left dot centre to right dot centre - four pitches, not five. */
    const val CELL_WIDTH = (DOT_COLUMNS - 1) * COLUMN_PITCH

    /** Top dot centre to bottom dot centre - six pitches, not seven. */
    const val CELL_HEIGHT = (DOT_ROWS - 1) * ROW_PITCH

    /** Ink box of one cell: the centres, plus half a dot overhanging each side. */
    const val INK_WIDTH = CELL_WIDTH + DOT_SIDE
    const val INK_HEIGHT = CELL_HEIGHT + DOT_SIDE

    /**
     * One character's origin to the next character's origin.
     *
     * Counted in COLUMNS - five for the cell plus the gap - not from
     * [CELL_WIDTH], which is one pitch shorter because it measures centre to
     * centre. Deriving the advance from the centre span is the easy mistake
     * here, and it laps the characters over each other by exactly one column.
     */
    const val CHARACTER_ADVANCE = (DOT_COLUMNS + CHARACTER_GAP_COLUMNS) * COLUMN_PITCH

    // ---- Dot identity --------------------------------------------------------
    //
    // THE BIT ORDER LIVES HERE, in [dotBit], and nowhere else. [packRows] builds
    // on it and [Hdls1414Glyphs] builds on [packRows], so the whole font has one
    // statement of which bit is which dot.

    /**
     * The bit standing for the dot at [row] (0 = top) and [column] (0 = left).
     *
     * Dots are numbered left to right along a row, then row by row down the
     * cell, and laid into the mask MOST SIGNIFICANT FIRST - so the top-left dot
     * is bit 34 and the bottom-right dot is bit 0.
     *
     * That order is chosen so a mask printed in binary reads as the picture:
     * seven groups of five, top row first, left dot first. Thirty-five bits is
     * why the mask is a Long.
     */
    fun dotBit(row: Int, column: Int): Long =
        1L shl (DOTS_PER_CELL - 1 - (row * DOT_COLUMNS + column))

    /**
     * A glyph mask from seven five-bit row patterns, top row first.
     *
     * Each pattern is meant to be written as a binary literal - 0b01110 - so the
     * source shows the shape. Within a pattern the LEFTMOST dot is the most
     * significant bit, which is what makes the literal look like the row.
     */
    fun packRows(vararg rowPatterns: Int): Long {

        require(rowPatterns.size == DOT_ROWS) {
            "a glyph needs $DOT_ROWS rows, got ${rowPatterns.size}"
        }

        var mask = 0L

        for ((row, pattern) in rowPatterns.withIndex()) {

            require(pattern shr DOT_COLUMNS == 0) {
                "row $row is wider than $DOT_COLUMNS dots"
            }

            // Leftmost dot first, which is the pattern's high bit.
            for (column in 0 until DOT_COLUMNS) {
                val bit = pattern shr (DOT_COLUMNS - 1 - column) and 1
                if (bit == 1) mask = mask or dotBit(row, column)
            }
        }

        return mask
    }

    /** Every dot lit - the display self-test, and what to draw to check the lattice. */
    val ALL_DOTS: Long = (1L shl DOTS_PER_CELL) - 1L

    // ---- Text layout ---------------------------------------------------------

    /**
     * Ink width of [text] in pixels - left ink edge to right ink edge, with the
     * dots' overhang at both ends included.
     *
     * So `origin.x = right - this` right-aligns exactly. Empty text is zero
     * wide; a single character is one ink box, with no trailing gap.
     */
    fun measureWidth(
        text: String,
        cellHeight: Float,
        gapColumns: Float = CHARACTER_GAP_COLUMNS,
    ): Float {

        if (text.isEmpty()) return 0f

        val advance = (DOT_COLUMNS + gapColumns) * COLUMN_PITCH
        val units = (text.length - 1) * advance + INK_WIDTH

        return units * cellHeight / CELL_HEIGHT
    }

    /**
     * Draws [text] with the TOP LEFT CORNER OF ITS INK at [inkOrigin].
     *
     * Note that this is an ink box, not a cell origin - unlike
     * [drawHdls1414Cell], which takes the centre of the top-left dot and lets
     * the dot overhang it. Here the caller gets a box it can measure with
     * [measureWidth] and position without knowing where the lattice falls.
     *
     * Characters the font has no glyph for are drawn BLANK rather than dropped,
     * so the columns stay where the caller expects them. Fixed pitch is only
     * useful if a missing glyph still takes its cell.
     */
    fun DrawScope.drawHdls1414Text(
        text: String,
        inkOrigin: Offset,
        cellHeight: Float,
        color: Color = NEON_ORANGE,
        gapColumns: Float = CHARACTER_GAP_COLUMNS,
        dotSide: Float = DOT_SIDE,
    ) {
        val scale = cellHeight / CELL_HEIGHT

        // In from the ink's corner to the first dot centre, on both axes.
        val overhang = dotSide / 2f * scale

        val advance = (DOT_COLUMNS + gapColumns) * COLUMN_PITCH * scale

        for ((index, character) in text.withIndex()) {

            val mask = Hdls1414Glyphs.maskFor(character) ?: continue

            drawHdls1414Cell(
                mask = mask,
                origin = Offset(inkOrigin.x + overhang + index * advance, inkOrigin.y + overhang),
                cellHeight = cellHeight,
                color = color,
                dotSide = dotSide,
            )
        }
    }

    // ---- Drawing -------------------------------------------------------------

    /**
     * Draws one cell's worth of dots with the centre of its TOP-LEFT DOT at
     * [origin].
     *
     * [cellHeight] is the rendered distance from the top row of dot centres to
     * the bottom row of dot centres, in pixels; everything else scales from it.
     * Note that ink extends [dotSide] / 2 beyond the cell on every side.
     *
     * SQUARE dots, exactly as the datasheet's own character chart draws them -
     * each lit element a small filled square centred on its lattice point.
     */
    fun DrawScope.drawHdls1414Cell(
        mask: Long,
        origin: Offset,
        cellHeight: Float,
        color: Color = NEON_ORANGE,
        dotSide: Float = DOT_SIDE,
    ) {
        if (mask == 0L) return

        val scale = cellHeight / CELL_HEIGHT

        val sidePx = dotSide * scale
        val half = sidePx / 2f

        // Drawn one square at a time rather than unioned into a path, as
        // TalkRpnFont does with its segments: dots never touch unless the
        // caller asks for a side above the pitch, so there are no overlaps
        // for the antialiasing to double up on.
        for (row in 0 until DOT_ROWS) {
            for (column in 0 until DOT_COLUMNS) {

                if (mask and dotBit(row, column) == 0L) continue

                drawRect(
                    color = color,
                    topLeft = Offset(
                        origin.x + column * COLUMN_PITCH * scale - half,
                        origin.y + row * ROW_PITCH * scale - half,
                    ),
                    size = Size(sidePx, sidePx),
                )
            }
        }
    }
}
