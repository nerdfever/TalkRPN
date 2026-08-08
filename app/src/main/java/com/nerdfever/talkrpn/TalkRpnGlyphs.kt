package com.nerdfever.talkrpn

import com.nerdfever.talkrpn.TalkRpnFont.Seg

/*
 * Which segments light for each character.
 *
 * Derived from the DL-3422's published character set (the readable scan supplied
 * 2026-08-07), with these deliberate departures:
 *
 *  - Digits 2 3 5 7 9 take the HP-01's hooked corners (A3/D3), and 4 takes the
 *    HP-01's short left side.
 *  - ALL DIGITS use P/Q as their right vertical instead of B/C, so a digit is a
 *    half-width character. This differs from the HP-01 and is deliberate: it is
 *    what keeps 5 and S distinguishable at a glance. Letters use the full width.
 *  - The decimal point and comma live in the character cell (DP / COMMA
 *    elements) rather than consuming a cell of their own.
 *
 * ---------------------------------------------------------------------------
 * Confidence
 * ---------------------------------------------------------------------------
 * Digits: certain (HP-01 masks + the corner rules + the P/Q decision).
 * Upper case, most symbols: read from the chart, high confidence.
 * Flagged with "LOW CONFIDENCE" below: the chart is ambiguous at these glyphs
 * and the mask is my best reading - & ' a f l t = ~ @. Review in the PDF or on
 * the watch and correct by segment name.
 *
 * ---------------------------------------------------------------------------
 * Why segment J exists
 * ---------------------------------------------------------------------------
 * Segment J (the second lower-left diagonal, crossing segment L) is what draws
 * the letter x - the two of them ARE the x. It also serves the letters e and s.
 * There is no mirror diagonal in the lower right; the letters V and v are drawn
 * vertical-plus-slash instead, which is how the DL-3422 does it.
 *
 * Convention, per Dave: in prose, "segment J" for segments, bare letters for
 * glyphs. A bare J is ambiguous.
 */

object TalkRpnGlyphs {

    // ---- Combinations, so the table below reads as shapes not bit soup -----

    private fun m(vararg s: Seg) = s.fold(0L) { acc, seg -> acc or seg.bit }

    // Dave's shorthand when dictating corrections, and what it means here:
    //
    //     A  =  A4 + A1 + A2   =  TOP
    //     D  =  D4 + D1 + D2   =  BOT
    //     F  =  F2 + F1        =  UL
    //     E  =  E1 + E2        =  LL
    //
    // A bare letter always means the whole segment including its corner piece,
    // unless a correction names the halves explicitly.

    /** Full top bar, square left corner. The default. */
    private val TOP = m(Seg.A1, Seg.A4, Seg.A2)

    /** Full top bar, hooked left corner. */
    private val TOP_HOOK = m(Seg.A1, Seg.A3, Seg.A2)

    /** Left half of the top bar only, square corner. Digits and brackets. */
    private val TOP_LEFT = m(Seg.A1, Seg.A4)

    /** Left half of the top bar, hooked. Digits 2 3 5 7 9. */
    private val TOP_LEFT_HOOK = m(Seg.A1, Seg.A3)

    private val BOT = m(Seg.D1, Seg.D4, Seg.D2)
    private val BOT_HOOK = m(Seg.D1, Seg.D3, Seg.D2)
    private val BOT_LEFT = m(Seg.D1, Seg.D4)
    private val BOT_LEFT_HOOK = m(Seg.D1, Seg.D3)

    /** Upper-left column, out to the corner. */
    private val UL = m(Seg.F1, Seg.F2)

    /** Upper-left column stopping where a hook would land - and a 4's left side. */
    private val UL_SHORT = m(Seg.F1)

    /** Lower-left column, out to the corner. */
    private val LL = m(Seg.E1, Seg.E2)

    /** Lower-left column stopping where the bottom hook lands. Only a 2 needs it. */
    private val LL_SHORT = m(Seg.E1)

    private val UR = m(Seg.B)
    private val LR = m(Seg.C)
    private val MID = m(Seg.G1, Seg.G2)
    private val STEM = m(Seg.P, Seg.Q)

    // ---- The table ----------------------------------------------------------

    private val GLYPHS: Map<Char, Long> = mapOf(

        ' ' to 0L,

        // ---- Digits ---------------------------------------------------------
        //
        // Half-width: right vertical is P/Q, bars are the left halves only.
        // Hooked corners on 0 2 3 5 7 8 9; short left side on 4; 6 stays square.

        '0' to (TOP_LEFT_HOOK or UL_SHORT or LL_SHORT or BOT_LEFT_HOOK or STEM),
        '1' to STEM,
        '2' to (TOP_LEFT_HOOK or m(Seg.P, Seg.G1) or LL_SHORT or BOT_LEFT_HOOK),
        '3' to (TOP_LEFT_HOOK or m(Seg.P, Seg.G1, Seg.Q) or BOT_LEFT_HOOK),
        '4' to (UL_SHORT or m(Seg.G1) or STEM),
        '5' to (TOP_LEFT_HOOK or UL_SHORT or m(Seg.G1, Seg.Q) or BOT_LEFT_HOOK),
        '6' to (TOP_LEFT or UL or m(Seg.G1) or LL or m(Seg.Q) or BOT_LEFT),
        '7' to (TOP_LEFT_HOOK or STEM),
        // Hooked at both corners, so F2 and E2 go dark - same rule as 2 3 5 9.
        '8' to (TOP_LEFT_HOOK or UL_SHORT or m(Seg.G1) or LL_SHORT or STEM or BOT_LEFT_HOOK),
        '9' to (TOP_LEFT_HOOK or UL_SHORT or m(Seg.G1) or STEM or BOT_LEFT_HOOK),

        // ---- Upper case -----------------------------------------------------
        //
        // Full width, right vertical B/C, square corners throughout.

        'A' to (TOP_HOOK or UL_SHORT or UR or MID or LL or LR),
        'B' to (TOP or UR or LR or BOT or m(Seg.G2) or STEM),
        'C' to (TOP_HOOK or UL_SHORT or LL_SHORT or BOT_HOOK),
        'D' to (TOP or UR or LR or BOT or STEM),
        'E' to (TOP or UL or m(Seg.G1) or LL or BOT),
        'F' to (TOP or UL or m(Seg.G1) or LL),
        'G' to (TOP_HOOK or UL_SHORT or LL_SHORT or BOT_HOOK or LR or m(Seg.G2)),
        'H' to (UL or UR or MID or LL or LR),
        'I' to (TOP or STEM or BOT),
        'J' to (UR or LR or LL_SHORT or BOT_HOOK),
        'K' to (UL or LL or m(Seg.G1) or m(Seg.I) or m(Seg.K)),
        'L' to (UL or LL or BOT),
        'M' to (UL or m(Seg.H) or m(Seg.I) or UR or LL or LR),
        'N' to (UL or m(Seg.H) or UR or m(Seg.K) or LL or LR),
        'O' to (TOP_HOOK or UL_SHORT or UR or LL_SHORT or LR or BOT_HOOK),
        'P' to (TOP or UL or UR or MID or LL),
        'Q' to (TOP_HOOK or UL_SHORT or UR or LL_SHORT or BOT_HOOK or LR or m(Seg.K)),
        'R' to (TOP or UL or UR or MID or LL or m(Seg.K)),
        'S' to (TOP_HOOK or UL_SHORT or MID or LR or BOT),
        'T' to (TOP or STEM),
        // D3 rather than D4, so the bottom-left corner rounds. E2 goes dark with
        // it, per the standing hook rule - left lit it would spike past the arc
        // down to y = 100.
        'U' to (UL or UR or LL_SHORT or LR or BOT_LEFT_HOOK or m(Seg.D2)),

        // Left column plus the full "/" - the two meet at the bottom left. This
        // is how the DL-3422 draws it, there being no lower-right "/" diagonal.
        'V' to (UL or LL or m(Seg.I, Seg.L)),

        // The mirror of M: the two lower diagonals peak at the centre.
        'W' to (UL or UR or LL or LR or m(Seg.L) or m(Seg.K)),

        'X' to m(Seg.H, Seg.I, Seg.L, Seg.K),
        'Y' to m(Seg.H, Seg.I, Seg.Q),
        // No E2 bridge needed since segment L reaches the corner itself.
        'Z' to (TOP or m(Seg.I, Seg.L) or BOT),

        // ---- Lower case -----------------------------------------------------
        //
        // The x-height is the lower half of the cell (G at the top, D at the
        // bottom); ascenders use the upper half and descenders use M/N/O below
        // the baseline. Narrow bowls close with Q, exactly like the digits.

        // Segment L, not segment J: it runs the way the slash in '/' does. D4
        // rather than D3 since the corner move - segment L lands on the exact
        // corner, which is where the square bar ends and the arc does not.
        'a' to m(Seg.G1, Seg.Q, Seg.D4, Seg.D1, Seg.L),

        // b c d e all round their bottom-left corner: D3 in place of E2, so the
        // bowl curls into the foot instead of meeting it square.
        'b' to (UL or LL_SHORT or m(Seg.G1, Seg.D3, Seg.D1, Seg.Q)),
        'c' to (LL_SHORT or m(Seg.G1, Seg.D3, Seg.D1)),
        'd' to (LL_SHORT or m(Seg.G1, Seg.D3, Seg.D1) or STEM),
        // Square foot like a, and E2 lit again: with no arc landing to stop at,
        // the left column runs to the corner where segment L and D4 meet.
        'e' to (LL or m(Seg.G1, Seg.D4, Seg.D1, Seg.L)),

        // LOW CONFIDENCE: flag right (A2), full crossbar, centre stem.
        'f' to (m(Seg.A2) or STEM or MID),

        // Hooked foot, same as b c d e: E2 out, D3 in.
        'g' to (LL_SHORT or m(Seg.G1, Seg.D3, Seg.D1, Seg.Q, Seg.M, Seg.N)),

        'h' to (UL or LL or m(Seg.G1, Seg.Q)),
        'i' to m(Seg.Q, Seg.COL1),
        'j' to m(Seg.Q, Seg.M, Seg.N, Seg.COL1),

        // J, not L: J descends left-to-right from the top of E, which is the
        // leg of a k. L descends the other way and would point back inward.
        'k' to (UL or LL or m(Seg.G1, Seg.J)),

        // Centre column, not the right one: B/C sit against the cell edge and
        // read as off-centre. Note this makes l and | identical.
        'l' to STEM,

        'm' to (MID or LL or LR or m(Seg.Q)),
        'n' to (LL or m(Seg.J, Seg.Q)),
        'o' to (LL_SHORT or m(Seg.G1, Seg.D3, Seg.D1, Seg.Q)),
                // Right-half bowl, deliberately: p went to the left half briefly, but
        // with the descender only possible on the centre column that form was a
        // tailless q. The bowl sitting RIGHT of the stem is what makes it a p,
        // and that outweighs it being the one lowercase bowl off the left side.
        'p' to (LR or m(Seg.G2, Seg.D2, Seg.Q, Seg.M)),

        // g and q share a bowl and differ only in which way the tail curls:
        // g curls left (N), q curls right (O).
        'q' to (LL_SHORT or m(Seg.G1, Seg.D3, Seg.D1, Seg.Q, Seg.M, Seg.O)),

        'r' to (LL or m(Seg.G1)),

        // s and z are mirror zigzags: s uses J, z uses L.
        's' to m(Seg.G1, Seg.J, Seg.D4, Seg.D1),

        // LOW CONFIDENCE: centre stem with full crossbar and a right foot.
        //
        // Reverted from a left-column form with a hooked foot: that only made
        // sense on the assumption that t sat flush left in the cell, which it
        // does not. The hook is on the left column and the stem is in the
        // centre, so there is nothing here for it to attach to.
        't' to (STEM or MID or m(Seg.D2)),

        // Experimental, matching upper-case U: D3 rounds the bottom-left corner,
        // and E2 goes dark with it or it would spike past the arc.
        'u' to (LL_SHORT or m(Seg.D3, Seg.D1, Seg.Q)),

        // Lower-left leg plus the rising diagonal. E2 lit since the corner move:
        // the two meet at the corner itself now, so the leg must reach it.
        'v' to (LL or m(Seg.L)),

        // L and K peak at the centre, columns outside - the lower-case mirror
        // of what makes M.
        'w' to (LL or LR or m(Seg.L, Seg.K)),

        // The crossing pair in the lower-left quadrant IS the x.
        'x' to m(Seg.J, Seg.L),

        'y' to (LL or m(Seg.D4, Seg.D1, Seg.Q, Seg.M, Seg.N)),

        'z' to m(Seg.G1, Seg.L, Seg.D4, Seg.D1),

        // ---- Punctuation and symbols ----------------------------------------

        '.' to m(Seg.DP),
        ',' to m(Seg.COMMA),
        ':' to m(Seg.COL1, Seg.COL2),

        // A colon whose lower dot has grown a tail - which is what a semicolon
        // is. Previously COL1 + COMMA, which put the dot on the cell axis and
        // the comma outside the cell to the right: two marks that belonged to
        // different characters.
        ';' to m(Seg.COL1, Seg.COL2, Seg.COL2_TAIL),
        '-' to MID,
        '+' to (MID or STEM),
        '*' to m(Seg.H, Seg.I, Seg.L, Seg.K, Seg.P, Seg.Q, Seg.G1, Seg.G2),
        '/' to m(Seg.I, Seg.L),
        '\\' to m(Seg.H, Seg.K),

        // Half-width bars, with D4 carrying the lower one out to the corner so
        // the two are the same length.
        '=' to m(Seg.G1, Seg.D4, Seg.D1),

        '_' to BOT,
        // Full height with the descender, per the DL-3422 - and no longer
        // identical to 1, which is P/Q alone. (l remains identical to 1.)
        '|' to (STEM or m(Seg.M)),

        // Upper centre stroke over the colon's lower dot.
        '!' to m(Seg.P, Seg.COL2),

        '?' to (TOP or UR or m(Seg.G2) or m(Seg.Q)),

        // A single slanted mark in the upper right.
        '\'' to m(Seg.I),

        // Two upper verticals of equal length: F and P.
        '"' to (UL or m(Seg.P)),

        // Parentheses, both properly curved - the pair that pushed the mask
        // past an Int.
        //
        // '(' is the left column with both corners hooked: round where '[' is
        // square, which is the real typographic difference between them. ')'
        // could not mirror it with what existed - the right side has no hooks -
        // so A5 and D5 were added, each half of the right parenthesis in one
        // piece. A mismatched pair had already been tried and looked broken.
        '(' to m(Seg.A1, Seg.A3, Seg.F1, Seg.E1, Seg.D3, Seg.D1),
        ')' to m(Seg.A5, Seg.D5),

        // Brackets and braces run the full cell, descender included, as on the
        // DL-3422. Only the centre column reaches the descender zone (segment
        // M), so all four live on the centre spine with their bars pointing the
        // way they open - which also keeps square-tall [ apart from curved (.
        // Braces differ from brackets by the mid tick alone.
        '[' to m(Seg.A2, Seg.P, Seg.Q, Seg.M, Seg.O),
        ']' to m(Seg.A1, Seg.P, Seg.Q, Seg.M, Seg.N),
        '{' to m(Seg.A2, Seg.P, Seg.G1, Seg.Q, Seg.M, Seg.O),
        '}' to m(Seg.A1, Seg.P, Seg.G2, Seg.Q, Seg.M, Seg.N),

        '<' to m(Seg.I, Seg.K),
        '>' to m(Seg.H, Seg.L),

        // Vertex at the centre, arms descending - the true up-pointing caret.
        // (H+I meet at the BOTTOM of the upper half, which reads as a v.)
        '^' to m(Seg.L, Seg.K),

        // Three horizontals - top, middle, descender - threaded by the whole
        // centre column. The one symbol that hangs below the baseline.
        '#' to (TOP or MID or m(Seg.N, Seg.O) or STEM or m(Seg.M)),

        // An S with the centre column through it, carried into the descender.
        '$' to (TOP_HOOK or UL_SHORT or MID or LR or BOT or STEM or m(Seg.M)),

        // Two closed boxes on the diagonal with the through-slash between them.
        '%' to (
            (TOP_LEFT_HOOK or UL_SHORT or m(Seg.G1, Seg.P)) or
                m(Seg.I, Seg.L) or
                (m(Seg.G2) or LR or m(Seg.D2, Seg.Q))
            ),

        // Square top corner (A4): segment H runs to the exact corner now, so the
        // diagonal springs from where the square bar ends. The hooked corner was
        // tried twice here and lost both times - first alongside A4, then alone.
        '&' to (TOP_LEFT or m(Seg.P, Seg.H, Seg.G1) or LL_SHORT or
            m(Seg.D3, Seg.D1, Seg.D2) or LR or m(Seg.K)),

        '@' to (TOP_HOOK or UR or LR or BOT_HOOK or m(Seg.E1, Seg.G1, Seg.Q)),

        '`' to m(Seg.H),

        // A squared-off wave, in the TOP half of the cell: up F, right along A,
        // down P, right along G2, up B - with the left crest ROUNDED, segment A3
        // curling the F-to-A corner. F2 dark per the hook rule. The other two
        // bends have no arcs available, so the wave rounds only where it can.
        '~' to (UL_SHORT or TOP_LEFT_HOOK or m(Seg.P, Seg.G2) or UR),
    )

    /** Segment mask for a character, or null if this font has no glyph for it. */
    fun maskFor(ch: Char): Long? = GLYPHS[ch]

    /** True if every character in [text] can be rendered. */
    fun canRender(text: String) = text.all { GLYPHS.containsKey(it) }

    /** Every character the font knows, in code-point order - the set to review. */
    val CHARACTERS: List<Char> = GLYPHS.keys.sorted()

    /** The segment names lit for [ch], for review listings. */
    fun segmentNames(ch: Char): List<String> {
        val mask = GLYPHS[ch] ?: return emptyList()
        return Seg.entries.filter { mask and it.bit != 0L }.map { it.name }
    }
}













