# Build talkrpn_font_reference.pdf:
#
#   page 1  geometry diagram (portrait)
#   page 2  centreline listing (portrait)
#   page 3  the whole character set on ONE landscape sheet, 16 per row in
#           code-point order - the traditional ASCII chart layout - each glyph
#           over its unlit ghost inside its cell bounds, with its lit segment
#           names printed underneath so corrections can be dictated as names.
#
# The glyph table here MIRRORS TalkRpnGlyphs.kt. If they disagree, the Kotlin
# wins; fix this one to match.
#
# The table is a LIST of pairs, not a hashtable: PowerShell hashtable keys are
# case-insensitive, so 'A' and 'a' would collide.

$OUT = "$PSScriptRoot\talkrpn_font_reference.pdf"

# ---- geometry, in cell widths (must match TalkRpnFont.kt) -------------------
#
# THE UNIT: segment E/F to segment B/C is 1. Every length below is in that unit,
# horizontally and vertically alike, to four significant figures.

$CELL_WIDTH = 1.0            # by definition
$CELL_HEIGHT = 1.710         # cap height, segment D to segment A
$DESCENDER_DEPTH = 0.7525    # how far the N/O bar hangs below the baseline
$TOTAL_HEIGHT = $CELL_HEIGHT + $DESCENDER_DEPTH

$STROKE = 0.1475
$SLANT_DEG = 6.0

$HOOK_R = 0.1355

# The grid every segment endpoint hangs from.
$XL = 0.0
$XM = $CELL_WIDTH / 2.0
$XR = $CELL_WIDTH

$YT = 0.0
$YM = $CELL_HEIGHT / 2.0
$YB = $CELL_HEIGHT
$YD = $TOTAL_HEIGHT

$Y_F_TOP = $HOOK_R                       # where the top hook lands on the column
$Y_E_BOT = $YB - $HOOK_R                 # where the bottom hook leaves it
$X_HOOK_END_R = $XR - $HOOK_R            # the parenthesis arcs' turning point

# The descender bar, inset from both columns - very slightly asymmetrically.
$XN = 0.06396
$XO = 0.9359

# Dots.
$COL1_Y = 0.3504
$COL2_Y = 1.378

$DP_DROP = 0.3263            # how far the decimal point sits below the baseline
$DP_Y = $CELL_HEIGHT + $DP_DROP

# From the last lit centreline of one glyph to the first of the next, and how far across it
# the decimal point sits. Both mirror TalkRpnFont.
$GAP = 0.85
$DP_GAP_FRACTION = 0.337

# Where the dot lands after a full-width cell - which is every cell on the
# reference sheet, since each glyph is drawn in a box of its own.
$DP_X = $CELL_WIDTH + $DP_GAP_FRACTION * $GAP

# What two FULL-WIDTH glyphs sit apart at that gap - the widest any pair gets.
# The app spaces proportionally and has no fixed pitch; the comparison sheets
# that set all-caps text still lay out on this.
$PITCH = $CELL_WIDTH + $GAP

$COMMA_TAIL_DROP = 0.3551
$COMMA_TAIL_LEFT = 0.1308

# ---- page setup -------------------------------------------------------------

# Portrait pages are 612 x 792 pt; the character sheet is landscape, 792 x 612.
# Bitmaps keep exactly those aspects so each page is one image, unscaled.
$PORTRAIT_W = 1700;  $PORTRAIT_H = 2200
$LANDSCAPE_W = 2200; $LANDSCAPE_H = 1700

$MARGIN = 50.0
$HEADER_H = 60.0

# The traditional chart: 16 columns, code-point order, 0x20 to 0x7F - exactly
# six full rows of sixteen.
$COLUMNS = 16

# One glyph box in cell widths: wide enough for the sheared cell plus the decimal
# point hanging off to the right, tall enough for the descender.
$GLYPH_W_UNITS = 1.659
$GLYPH_H_UNITS = 2.651

# Vertical space under each glyph for its character and segment listing. Pixels,
# not units - it holds text set in a system font, which has no cell to scale to.
$LABEL_H = 40.0

# The hex indices, as on the Litronix datasheet: low nibble 0-F across the top,
# high nibble 2-7 down the left. The grid shrinks slightly to make room.
$INDEX_W = 50.0
$HEX_HEADER_H = 30.0

# Glyphs whose masks are a best guess at an ambiguous chart.
$LOW_CONFIDENCE = @("'", "f", "t")

# ---- segment geometry (must match TalkRpnFont.kt) ---------------------------

$SEG_LINES = @{
    "A1" = @($XM, $YT, $HOOK_R, $YT);        "A2" = @($XM, $YT, $XR, $YT)
    "A4" = @($HOOK_R, $YT, $XL, $YT)
    "B"  = @($XR, $YT, $XR, $YM);            "C"  = @($XR, $YM, $XR, $YB)
    "D1" = @($XM, $YB, $HOOK_R, $YB);        "D2" = @($XM, $YB, $XR, $YB)
    "D4" = @($HOOK_R, $YB, $XL, $YB)
    "F2" = @($XL, $YT, $XL, $Y_F_TOP);       "F1" = @($XL, $Y_F_TOP, $XL, $YM)
    "E1" = @($XL, $YM, $XL, $Y_E_BOT);       "E2" = @($XL, $Y_E_BOT, $XL, $YB)
    "G1" = @($XL, $YM, $XM, $YM);            "G2" = @($XM, $YM, $XR, $YM)
    "H"  = @($XL, $YT, $XM, $YM);            "I"  = @($XR, $YT, $XM, $YM)
    "J"  = @($XL, $YM, $XM, $YB);            "L"  = @($XM, $YM, $XL, $YB)
    "K"  = @($XM, $YM, $XR, $YB)
    "P"  = @($XM, $YT, $XM, $YM);            "Q"  = @($XM, $YM, $XM, $YB)
    "COL2_TAIL" = @($XM, $COL2_Y, ($XM - $COMMA_TAIL_LEFT), ($COL2_Y + $COMMA_TAIL_DROP))
    "M"  = @($XM, $YB, $XM, $YD)
    "N"  = @($XN, $YD, $XM, $YD);            "O"  = @($XM, $YD, $XO, $YD)
}

# Arcs: centre x, centre y, radius, from-degrees, to-degrees.
$SEG_ARCS = @{
    "A3" = @($HOOK_R, $Y_F_TOP, $HOOK_R, 270, 180)
    "D3" = @($HOOK_R, $Y_E_BOT, $HOOK_R, 90, 180)
}

# RPAR, the whole right parenthesis as one polyline: bar stub, arc, column,
# arc, bar stub. Sampled here; the Kotlin uses exact Beziers.
function New-ArcPoints($cx, $cy, $r, $fromDeg, $toDeg) {
    $pts = @()
    for ($i = 0; $i -le 16; $i++) {
        $t = ($fromDeg + ($toDeg - $fromDeg) * $i / 16.0) * [Math]::PI / 180.0
        $pts += , @(($cx + $r * [Math]::Cos($t)), ($cy + $r * [Math]::Sin($t)))
    }
    return $pts
}

$SEG_POLYS = @{
    "RPAR" = @(, @($XM, $YT)) + (New-ArcPoints $X_HOOK_END_R $Y_F_TOP $HOOK_R 270 360) +
             (New-ArcPoints $X_HOOK_END_R $Y_E_BOT $HOOK_R 0 90) + @(, @($XM, $YB))
}

# Dots: centre x, centre y. The comma is its dot plus the tail below.
$SEG_DOTS = @{
    "COL1" = @($XM, $COL1_Y);  "COL2" = @($XM, $COL2_Y)
    "DP" = @($DP_X, $DP_Y);    "COMMA" = @($DP_X, $DP_Y)
}
$COMMA_TAIL = @($DP_X, $DP_Y, ($DP_X - $COMMA_TAIL_LEFT), ($DP_Y + $COMMA_TAIL_DROP))

$ALL_SEGMENTS = @($SEG_LINES.Keys) + @($SEG_ARCS.Keys) + @($SEG_POLYS.Keys) + @($SEG_DOTS.Keys)

# ---- the glyph table (mirrors TalkRpnGlyphs.kt) ------------------------------

# Shorthand, matching the Kotlin combinations.
$TOP = @("A4", "A1", "A2");  $TOP_HOOK = @("A3", "A1", "A2")
$TOP_LEFT = @("A4", "A1");   $TOP_LEFT_HOOK = @("A3", "A1")
$BOT = @("D4", "D1", "D2");  $BOT_HOOK = @("D3", "D1", "D2")
$UL = @("F2", "F1");         $LL = @("E1", "E2")
$MID = @("G1", "G2");        $STEM = @("P", "Q")

$GLYPHS = @(
    @{ C = ' ';  S = @() }
    @{ C = '!';  S = @("P", "COL2") }
    @{ C = '"';  S = $UL + @("P") }
    @{ C = '#';  S = $TOP + $MID + @("N", "O") + $STEM + @("M") }
    @{ C = '$';  S = @("A3", "A1", "A2", "F1") + $MID + @("C") + $BOT + $STEM + @("M") }
    @{ C = '%';  S = $TOP_LEFT_HOOK + @("F1", "G1", "P") + @("I", "L") + @("G2", "C", "D2", "Q") }
    @{ C = '&';  S = $TOP_LEFT + @("P", "H", "G1", "E1") + @("D3", "D1", "D2") + @("C", "K") }
    @{ C = "'";  S = @("I") }
    @{ C = '(';  S = @("A1", "A3", "F1", "E1", "D3", "D1") }
    @{ C = ')';  S = @("RPAR") }
    @{ C = '*';  S = @("H", "I", "L", "K", "P", "Q", "G1", "G2") }
    @{ C = '+';  S = $MID + $STEM }
    @{ C = ',';  S = @("COMMA") }
    @{ C = '-';  S = $MID }
    @{ C = '.';  S = @("DP") }
    @{ C = '/';  S = @("I", "L") }
    @{ C = '0';  S = $TOP_HOOK + @("F1", "B", "E1", "C") + $BOT_HOOK }
    @{ C = '1';  S = @("B", "C") }
    @{ C = '2';  S = $TOP_HOOK + @("B") + $MID + @("E1") + $BOT_HOOK }
    @{ C = '3';  S = $TOP_HOOK + @("B") + $MID + @("C") + $BOT_HOOK }
    @{ C = '4';  S = @("F1") + $MID + @("B", "C") }
    @{ C = '5';  S = $TOP_HOOK + @("F1") + $MID + @("C") + $BOT_HOOK }
    @{ C = '6';  S = $TOP + $UL + $MID + $LL + @("C") + $BOT }
    @{ C = '7';  S = $TOP_HOOK + @("B", "C") }
    @{ C = '8';  S = $TOP_HOOK + @("F1", "B") + $MID + @("E1", "C") + $BOT_HOOK }
    @{ C = '9';  S = $TOP_HOOK + @("F1", "B") + $MID + @("C") + $BOT_HOOK }
    @{ C = ':';  S = @("COL1", "COL2") }
    @{ C = ';';  S = @("COL1", "COL2", "COL2_TAIL") }
    @{ C = '<';  S = @("I", "K") }
    @{ C = '=';  S = @("G1", "D4", "D1") }
    @{ C = '>';  S = @("H", "L") }
    @{ C = '?';  S = $TOP + @("B", "G2", "Q") }
    @{ C = '@';  S = @("A3", "A1", "A2") + @("B", "C") + @("D3", "D1", "D2") + @("E1", "G1", "Q") }
    @{ C = 'A';  S = @("A3", "A1", "A2", "F1") + @("B") + $MID + $LL + @("C") }
    @{ C = 'B';  S = $TOP + @("B", "C") + $BOT + @("G2") + $STEM }
    @{ C = 'C';  S = @("A3", "A1", "A2", "F1", "E1", "D3", "D1", "D2") }
    @{ C = 'D';  S = $TOP + @("B", "C") + $BOT + $STEM }
    @{ C = 'E';  S = $TOP + $UL + @("G1") + $LL + $BOT }
    @{ C = 'F';  S = $TOP + $UL + @("G1") + $LL }
    @{ C = 'G';  S = @("A3", "A1", "A2", "F1", "E1", "D3", "D1", "D2", "C", "G2") }
    @{ C = 'H';  S = $UL + @("B") + $MID + $LL + @("C") }
    @{ C = 'I';  S = $TOP + $STEM + $BOT }
    @{ C = 'J';  S = @("B", "C", "E1", "D3", "D1", "D2") }
    @{ C = 'K';  S = $UL + $LL + @("G1", "I", "K") }
    @{ C = 'L';  S = $UL + $LL + $BOT }
    @{ C = 'M';  S = $UL + @("H", "I", "B") + $LL + @("C") }
    @{ C = 'N';  S = $UL + @("H", "B", "K") + $LL + @("C") }
    @{ C = 'O';  S = $TOP + $UL + @("B") + $LL + @("C") + $BOT }
    @{ C = 'P';  S = $TOP + $UL + @("B") + $MID + $LL }
    @{ C = 'Q';  S = @("A3", "A1", "A2", "F1") + @("B", "E1", "C", "D3", "D1", "D2", "K") }
    @{ C = 'R';  S = $TOP + $UL + @("B") + $MID + $LL + @("K") }
    @{ C = 'S';  S = @("A3", "A1", "A2", "F1") + $MID + @("C") + $BOT }
    @{ C = 'T';  S = $TOP + $STEM }
    @{ C = 'U';  S = $UL + @("B", "E1", "C", "D3", "D1", "D2") }
    @{ C = 'V';  S = $UL + $LL + @("I", "L") }
    @{ C = 'W';  S = $UL + @("B") + $LL + @("C", "L", "K") }
    @{ C = 'X';  S = @("H", "I", "L", "K") }
    @{ C = 'Y';  S = @("H", "I", "Q") }
    @{ C = 'Z';  S = $TOP + @("I", "L") + $BOT }
    @{ C = '[';  S = @("A2", "P", "Q", "M", "O") }
    @{ C = '\';  S = @("H", "K") }
    @{ C = ']';  S = @("A1", "P", "Q", "M", "N") }
    @{ C = '^';  S = @("L", "K") }
    @{ C = '_';  S = $BOT }
    @{ C = '`';  S = @("H") }
    @{ C = 'a';  S = @("G1", "Q", "D4", "D1", "L") }
    @{ C = 'b';  S = $UL + @("E1", "G1", "D3", "D1", "Q") }
    @{ C = 'c';  S = @("E1", "G1", "D3", "D1") }
    @{ C = 'd';  S = @("E1", "G1", "D3", "D1") + $STEM }
    @{ C = 'e';  S = $LL + @("G1", "D4", "D1", "L") }
    @{ C = 'f';  S = @("A2") + $STEM + $MID }
    @{ C = 'g';  S = @("E1", "G1", "D3", "D1", "Q", "M", "N") }
    @{ C = 'h';  S = $UL + $LL + @("G1", "Q") }
    @{ C = 'i';  S = @("Q", "COL1") }
    @{ C = 'j';  S = @("Q", "M", "N", "COL1") }
    @{ C = 'k';  S = $UL + $LL + @("G1", "J") }
    @{ C = 'l';  S = $STEM }
    @{ C = 'm';  S = $MID + $LL + @("C", "Q") }
    @{ C = 'n';  S = $LL + @("J", "Q") }
    @{ C = 'o';  S = @("E1", "G1", "D3", "D1", "Q") }
    @{ C = 'p';  S = @("C", "G2", "D2", "Q", "M") }
    @{ C = 'q';  S = @("E1", "G1", "D3", "D1", "Q", "M", "O") }
    @{ C = 'r';  S = $LL + @("G1") }
    @{ C = 's';  S = @("G1", "J", "D4", "D1") }
    @{ C = 't';  S = $STEM + $MID + @("D2") }
    @{ C = 'u';  S = @("E1", "D3", "D1", "Q") }
    @{ C = 'v';  S = $LL + @("L") }
    @{ C = 'w';  S = $LL + @("C", "L", "K") }
    @{ C = 'x';  S = @("J", "L") }
    @{ C = 'y';  S = $LL + @("D4", "D1", "Q", "M", "N") }
    @{ C = 'z';  S = @("G1", "L", "D4", "D1") }
    @{ C = '{';  S = @("A2", "P", "G1", "Q", "M", "O") }
    @{ C = '|';  S = $STEM + @("M") }
    @{ C = '}';  S = @("A1", "P", "G2", "Q", "M", "N") }
    @{ C = '~';  S = @("F1", "A3", "A1", "P", "G2", "B") }

    # DEL lights every segment - the display self-test pattern, and the worst
    # case for legibility. Taken from $ALL_SEGMENTS rather than listed, so it
    # cannot fall behind the segment tables above.
    @{ C = [char]0x7F;  S = $ALL_SEGMENTS }
)

# ---- drawing ----------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$SHEAR = [Math]::Tan($SLANT_DEG * [Math]::PI / 180.0)
$SHEAR_OFFSET = $SHEAR * $TOTAL_HEIGHT

$LIT = [System.Drawing.Color]::FromArgb(210, 30, 20)
$GHOST = [System.Drawing.Color]::FromArgb(228, 228, 228)
$BOUNDS = [System.Drawing.Color]::FromArgb(120, 185, 200)
$BOUNDS_WIDTH = 1.1
$BLACK = [System.Drawing.Brushes]::Black
$GREY = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(120, 120, 120))
$ORANGE = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(190, 110, 0))

# The cell's own boundary, with the baseline inside it. A parallelogram because
# the slant leans the whole cell; ink overhangs it by half a stroke all round.
function Draw-CellBounds($g, $ox, $oy, $k) {

    $pen = New-Object System.Drawing.Pen $BOUNDS, $BOUNDS_WIDTH

    function BP($x, $y) {
        New-Object System.Drawing.PointF (
            ($ox + ($x - $SHEAR * $y + $SHEAR_OFFSET) * $k), ($oy + $y * $k))
    }

    $corners = @(
        (BP $XL $YT), (BP $XR $YT), (BP $XR $YD), (BP $XL $YD)
    )
    $g.DrawPolygon($pen, [System.Drawing.PointF[]]$corners)
    $g.DrawLine($pen, (BP $XL $YB), (BP $XR $YB))

    $pen.Dispose()
}

. "$PSScriptRoot/talkrpn_render.ps1"

# Kept as a thin name so the call sites below do not all have to change; the
# drawing itself lives in talkrpn_render.ps1, shared with every other script.
function Draw-Glyph($g, $names, $ox, $oy, $k, $color) {
    Draw-TalkRpnCell $g $names $ox $oy $k $color
}

# ---- the character sheet: one landscape page, traditional chart layout -------

$titleFont = New-Object System.Drawing.Font "Segoe UI", 20, ([System.Drawing.FontStyle]::Bold)
$subFont = New-Object System.Drawing.Font "Segoe UI", 10
$charFont = New-Object System.Drawing.Font "Consolas", 14, ([System.Drawing.FontStyle]::Bold)
$segFont = New-Object System.Drawing.Font "Consolas", 6.5

$cellW = ($LANDSCAPE_W - 2 * $MARGIN - $INDEX_W) / $COLUMNS
$k = $cellW / $GLYPH_W_UNITS
$rowH = $GLYPH_H_UNITS * $k + $LABEL_H

$bmp = New-Object System.Drawing.Bitmap $LANDSCAPE_W, $LANDSCAPE_H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::White)

$g.DrawString("TalkRPN character set", $titleFont, $BLACK, $MARGIN, 8)
$g.DrawString(
    ("16 per row, code-point order 0x20-0x7F.  Stroke {0:F3}, slant {1:F1} deg.  Red = lit, grey = unlit, cyan = cell bounds and baseline.  Orange ? = low confidence: ' f t" -f $STROKE, $SLANT_DEG),
    $subFont, $GREY, ($MARGIN + 330), 18)

# The datasheet furniture: a light grid, hex column labels 0-F along the top,
# hex row labels 2-7 down the left gutter.
$hexFont = New-Object System.Drawing.Font "Consolas", 13, ([System.Drawing.FontStyle]::Bold)
$hexBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(60, 60, 60))
$gridPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(215, 215, 215)), 1.0

$gridTop = $MARGIN + $HEADER_H
$gridLeft = $MARGIN
$glyphTop = $gridTop + $HEX_HEADER_H
$gridBottom = $glyphTop + 6 * $rowH
$gridRight = $gridLeft + $INDEX_W + $COLUMNS * $cellW

foreach ($c in 0..$COLUMNS) {
    $x = $gridLeft + $INDEX_W + $c * $cellW
    $g.DrawLine($gridPen, $x, $gridTop, $x, $gridBottom)
}
$g.DrawLine($gridPen, $gridLeft, $gridTop, $gridLeft, $gridBottom)

foreach ($r in 0..6) {
    $y = $glyphTop + $r * $rowH
    $g.DrawLine($gridPen, $gridLeft, $y, $gridRight, $y)
}
$g.DrawLine($gridPen, $gridLeft, $gridTop, $gridRight, $gridTop)

foreach ($c in 0..($COLUMNS - 1)) {
    $g.DrawString(("{0:X}" -f $c), $hexFont, $hexBrush,
        ($gridLeft + $INDEX_W + ($c + 0.5) * $cellW - 8), ($gridTop + 4))
}

foreach ($r in 0..5) {
    $g.DrawString(("{0:X}" -f ($r + 2)), $hexFont, $hexBrush,
        ($gridLeft + 16), ($glyphTop + $r * $rowH + $GLYPH_H_UNITS * $k / 2 - 10))
}

for ($i = 0; $i -lt $GLYPHS.Count; $i++) {

    $entry = $GLYPHS[$i]
    $ch = [string]$entry.C
    $col = $i % $COLUMNS
    $row = [Math]::Floor($i / $COLUMNS)

    $ox = $MARGIN + $INDEX_W + $col * $cellW
    $oy = $glyphTop + $row * $rowH + $STROKE * $k

    Draw-CellBounds $g $ox $oy $k
    Draw-Glyph $g $ALL_SEGMENTS $ox $oy $k $GHOST
    Draw-Glyph $g $entry.S $ox $oy $k $LIT

    # The two characters that cannot print themselves get a name instead.
    $labelY = $oy + $GLYPH_H_UNITS * $k - 2
    $shown = switch ([int][char]$entry.C) {
        0x20 { "sp" }
        0x7F { "DEL" }
        default { $ch }
    }
    $g.DrawString($shown, $charFont, $BLACK, ($ox - 4), $labelY)

    if ($LOW_CONFIDENCE -ccontains $ch) {
        $g.DrawString("?", $charFont, $ORANGE, ($ox + 14), $labelY)
    }

    # Listing all 32 names for DEL would overflow the box and say nothing.
    $names = if ($entry.S.Count -eq $ALL_SEGMENTS.Count) { "all segments" } else { $entry.S -join " " }
    $wrapped = [System.Drawing.RectangleF]::new(($ox + 30), ($labelY + 3), ($cellW - 32), ($LABEL_H - 4))
    $g.DrawString($names, $segFont, $GREY, $wrapped)
}

$g.Dispose()
$charsetPage = $bmp

# ---- the geometry pages -------------------------------------------------------

& "$PSScriptRoot\draw_talkrpn_geometry.ps1" -Section diagram -OutputName "talkrpn_geometry_diagram.png" | Out-Null
& "$PSScriptRoot\draw_talkrpn_geometry.ps1" -Section listing -OutputName "talkrpn_geometry_listing.png" | Out-Null

$geometryPages = @()

# The factor each geometry sheet gets scaled by when placed on the page. Captured
# because the page number has to be sized against the body text AS PLACED, not as
# it was drawn - and the two sheets are different widths, so they scale
# differently.
$placedScale = @{}

foreach ($name in @("talkrpn_geometry_diagram.png", "talkrpn_geometry_listing.png")) {

    $geo = [System.Drawing.Image]::FromFile("$PSScriptRoot\$name")

    $bmp = New-Object System.Drawing.Bitmap $PORTRAIT_W, $PORTRAIT_H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.Clear([System.Drawing.Color]::White)

    $scale = [Math]::Min(($PORTRAIT_W - 2 * $MARGIN) / $geo.Width, ($PORTRAIT_H - 2 * $MARGIN) / $geo.Height)
    $w = $geo.Width * $scale
    $h = $geo.Height * $scale
    $g.DrawImage($geo, (($PORTRAIT_W - $w) / 2), $MARGIN, $w, $h)

    $placedScale[$name] = $scale

    $g.Dispose()
    $geo.Dispose()

    $geometryPages += $bmp
}

# ---- assemble the PDF -----------------------------------------------------------
#
# Hand-built: each page is one JPEG drawn over the full page. Pages may differ
# in size, so each carries its own MediaBox. Portrait bitmaps are 1700x2200 on a
# 612x792 box; the landscape sheet is 2200x1700 on 792x612.

$pages = @(
    @{ Bmp = $geometryPages[0]; WPt = 612; HPt = 792 }
    @{ Bmp = $geometryPages[1]; WPt = 612; HPt = 792 }
    @{ Bmp = $charsetPage;      WPt = 792; HPt = 612 }
)

# ---- page numbers ---------------------------------------------------------------
#
# "n/m" at the bottom right, one point smaller than the body text. The m is there
# so a printed copy shows at a glance whether a page is missing.
#
# Sizing takes two steps of care.
#
# First, GDI+ font sizes are POINTS unless told otherwise, and these scripts are
# drawing into bitmaps at 96 dpi - so $noteFont's "11" is 14.67 px, not 11. Using
# 11 here made the page number two thirds the size it should have been.
#
# Second, a "point" on the finished page only means anything after the bitmap has
# been scaled onto it. The body text is drawn at BODY_PT and then placed at that
# sheet's scale, so on the page it is that product.
#
# Which sheet's scale? The listing's. It is the page that carries running body
# text, the drawing's sheet is mostly artwork, and the two are different widths so
# they scale differently. One size on every page is the convention worth keeping -
# a page number that changed size page to page would look like a mistake.
#
# Both page sizes work out at the same pixels per point - 1700/612 and 2200/792
# are both 2.778 - so a single pixel size is correct on all three.

$BODY_PT = 11                   # matches $noteFont in draw_talkrpn_geometry.ps1
$DPI = 96.0
$PAGE_NUMBER_INSET = 34         # from the trimmed edge, in page pixels

$pxPerPoint = $PORTRAIT_W / 612.0
$bodyPxOnPage = $BODY_PT * $DPI / 72.0 * $placedScale["talkrpn_geometry_listing.png"]
$pageNumberPx = $bodyPxOnPage - $pxPerPoint

$pageNumberFont = New-Object System.Drawing.Font "Consolas", $pageNumberPx, ([System.Drawing.GraphicsUnit]::Pixel)
$pageNumberBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(110, 110, 110))

for ($i = 0; $i -lt $pages.Count; $i++) {

    $bmp = $pages[$i].Bmp
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

    $text = "{0}/{1}" -f ($i + 1), $pages.Count
    $size = $g.MeasureString($text, $pageNumberFont)

    $g.DrawString($text, $pageNumberFont, $pageNumberBrush,
        ($bmp.Width - $PAGE_NUMBER_INSET - $size.Width),
        ($bmp.Height - $PAGE_NUMBER_INSET - $size.Height))

    $g.Dispose()
}

$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
    Where-Object { $_.MimeType -eq "image/jpeg" }
$encParams = New-Object System.Drawing.Imaging.EncoderParameters 1
$encParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter (
    [System.Drawing.Imaging.Encoder]::Quality, [long]88)

foreach ($p in $pages) {
    $ms = New-Object System.IO.MemoryStream
    $p.Bmp.Save($ms, $jpegCodec, $encParams)
    $p.Jpeg = $ms.ToArray()
    $p.PxW = $p.Bmp.Width
    $p.PxH = $p.Bmp.Height
    $ms.Dispose()
    $p.Bmp.Dispose()
}

$enc = [System.Text.Encoding]::ASCII

# Named pdfStream, NOT out: PowerShell variables are case-insensitive, so $out
# would silently overwrite $OUT, the output path.
$pdfStream = New-Object System.IO.MemoryStream
$offsets = New-Object System.Collections.Generic.List[long]

function Write-Bytes([byte[]] $b) { $pdfStream.Write($b, 0, $b.Length) }
function Write-Text([string] $s) { Write-Bytes ($enc.GetBytes($s)) }
function Begin-Object([int] $num) { $offsets.Add($pdfStream.Position); Write-Text "$num 0 obj`n" }

Write-Text "%PDF-1.4`n"

$n = $pages.Count
$kids = (0..($n - 1) | ForEach-Object { "$(3 + 3 * $_) 0 R" }) -join " "

Begin-Object 1
Write-Text "<< /Type /Catalog /Pages 2 0 R >>`nendobj`n"

Begin-Object 2
Write-Text "<< /Type /Pages /Kids [$kids] /Count $n >>`nendobj`n"

for ($p = 0; $p -lt $n; $p++) {

    $page = $pages[$p]
    $pageObj = 3 + 3 * $p
    $imgObj = 4 + 3 * $p
    $contObj = 5 + 3 * $p

    Begin-Object $pageObj
    Write-Text ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $($page.WPt) $($page.HPt)] " +
        "/Resources << /XObject << /Im0 $imgObj 0 R >> >> /Contents $contObj 0 R >>`nendobj`n")

    Begin-Object $imgObj
    Write-Text ("<< /Type /XObject /Subtype /Image /Width $($page.PxW) /Height $($page.PxH) " +
        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
        "/Length $($page.Jpeg.Length) >>`nstream`n")
    Write-Bytes $page.Jpeg
    Write-Text "`nendstream`nendobj`n"

    $content = "q $($page.WPt) 0 0 $($page.HPt) 0 0 cm /Im0 Do Q"
    Begin-Object $contObj
    Write-Text "<< /Length $($content.Length) >>`nstream`n$content`nendstream`nendobj`n"
}

$xrefPos = $pdfStream.Position
$total = 2 + 3 * $n + 1

Write-Text "xref`n0 $total`n0000000000 65535 f `n"
foreach ($o in $offsets) { Write-Text ("{0:D10} 00000 n `n" -f $o) }
Write-Text "trailer`n<< /Size $total /Root 1 0 R >>`nstartxref`n$xrefPos`n%%EOF`n"

# A PDF open in a viewer is locked for writing. Say so plainly rather than
# reporting success over a file that was never replaced.
try {
    [System.IO.File]::WriteAllBytes($OUT, $pdfStream.ToArray())
    Write-Output "wrote $OUT  ($n pages)"
}
catch [System.IO.IOException] {
    Write-Output "COULD NOT WRITE $OUT - it is open in another program. Close it and re-run."
}
finally {
    $pdfStream.Dispose()
}













