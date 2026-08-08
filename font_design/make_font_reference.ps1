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

# ---- geometry, in cell units (must match TalkRpnFont.kt) -------------------

$STROKE = 5.5        # the review weight; the nominal 9.29 is too heavy at this bar count
$SLANT_DEG = 7.5
$TOTAL_HEIGHT = 144.0

# ---- page setup -------------------------------------------------------------

# Portrait pages are 612 x 792 pt; the character sheet is landscape, 792 x 612.
# Bitmaps keep exactly those aspects so each page is one image, unscaled.
$PORTRAIT_W = 1700;  $PORTRAIT_H = 2200
$LANDSCAPE_W = 2200; $LANDSCAPE_H = 1700

$MARGIN = 50.0
$HEADER_H = 60.0

# The traditional chart: 16 columns, code-point order, 0x20 to 0x7E.
$COLUMNS = 16

# One glyph box in cell units: wide enough for the sheared cell plus the decimal
# point hanging off to the right, tall enough for the descender.
$GLYPH_W_UNITS = 97.0
$GLYPH_H_UNITS = 155.0

# Vertical space under each glyph for its character and segment listing.
$LABEL_H = 40.0

# The hex indices, as on the Litronix datasheet: low nibble 0-F across the top,
# high nibble 2-7 down the left. The grid shrinks slightly to make room.
$INDEX_W = 50.0
$HEX_HEADER_H = 30.0

# Glyphs whose masks are a best guess at an ambiguous chart.
$LOW_CONFIDENCE = @("'", "f", "t")

# ---- segment geometry (must match TalkRpnFont.kt) ---------------------------

$SEG_LINES = @{
    "A1" = @(29.235, 0, 7.92, 0);      "A2" = @(29.235, 0, 58.47, 0)
    "A4" = @(7.92, 0, 0, 0)
    "B"  = @(58.47, 0, 58.47, 50);     "C"  = @(58.47, 50, 58.47, 100)
    "D1" = @(29.235, 100, 7.92, 100);  "D2" = @(29.235, 100, 58.47, 100)
    "D4" = @(7.92, 100, 0, 100)
    "F2" = @(0, 0, 0, 7.92);           "F1" = @(0, 7.92, 0, 50)
    "E1" = @(0, 50, 0, 92.08);         "E2" = @(0, 92.08, 0, 100)
    "G1" = @(0, 50, 29.235, 50);       "G2" = @(29.235, 50, 58.47, 50)
    "H"  = @(0, 7.92, 29.235, 50);     "I"  = @(58.47, 0, 29.235, 50)
    "J"  = @(0, 50, 29.235, 100);      "L"  = @(29.235, 50, 0, 92.08)
    "K"  = @(29.235, 50, 58.47, 100)
    "P"  = @(29.235, 0, 29.235, 50);   "Q"  = @(29.235, 50, 29.235, 100)
    "COL2_TAIL" = @(29.235, 80.60, 21.585, 101.36)
    "M"  = @(29.235, 100, 29.235, 144)
    "N"  = @(3.74, 144, 29.235, 144);  "O"  = @(29.235, 144, 54.72, 144)
}

# Arcs: centre x, centre y, radius, from-degrees, to-degrees.
$SEG_ARCS = @{
    "A3" = @(7.92, 7.92, 7.92, 270, 180)
    "D3" = @(7.92, 92.08, 7.92, 90, 180)
}

# A5 and D5, the right-hand parenthesis halves: bar stub, corner arc, column
# stub, as one polyline each. Sampled here; the Kotlin uses exact Beziers.
function New-ArcPoints($cx, $cy, $r, $fromDeg, $toDeg) {
    $pts = @()
    for ($i = 0; $i -le 16; $i++) {
        $t = ($fromDeg + ($toDeg - $fromDeg) * $i / 16.0) * [Math]::PI / 180.0
        $pts += , @(($cx + $r * [Math]::Cos($t)), ($cy + $r * [Math]::Sin($t)))
    }
    return $pts
}

$SEG_POLYS = @{
    "A5" = @(, @(29.235, 0.0)) + (New-ArcPoints 50.55 7.92 7.92 270 360) + @(, @(58.47, 50.0))
    "D5" = @(, @(58.47, 50.0)) + (New-ArcPoints 50.55 92.08 7.92 0 90) + @(, @(29.235, 100.0))
}

# Dots: centre x, centre y. The comma is its dot plus the tail below.
$SEG_DOTS = @{
    "COL1" = @(29.235, 20.49); "COL2" = @(29.235, 80.60)
    "DP" = @(86.64, 119.08);   "COMMA" = @(86.64, 119.08)
}
$COMMA_TAIL = @(86.64, 119.08, 78.99, 139.84)

$ALL_SEGMENTS = @($SEG_LINES.Keys) + @($SEG_ARCS.Keys) + @($SEG_POLYS.Keys) + @($SEG_DOTS.Keys)

# ---- the glyph table (mirrors TalkRpnGlyphs.kt) ------------------------------

# Shorthand, matching the Kotlin combinations.
$TOP = @("A4", "A1", "A2")
$TOP_LEFT = @("A4", "A1");   $TOP_LEFT_HOOK = @("A3", "A1")
$BOT = @("D4", "D1", "D2")
$BOT_LEFT = @("D4", "D1");   $BOT_LEFT_HOOK = @("D3", "D1")
$UL = @("F2", "F1");         $LL = @("E1", "E2")
$MID = @("G1", "G2");        $STEM = @("P", "Q")

$GLYPHS = @(
    @{ C = ' ';  S = @() }
    @{ C = '!';  S = @("P", "COL2") }
    @{ C = '"';  S = $UL + @("P") }
    @{ C = '#';  S = $TOP + $MID + @("N", "O") + $STEM + @("M") }
    @{ C = '$';  S = @("A3", "A1", "A2", "F1") + $MID + @("C") + $BOT + $STEM + @("M") }
    @{ C = '%';  S = $TOP_LEFT + $UL + @("G1", "P") + @("I", "L") + @("G2", "C", "D2", "Q") }
    @{ C = '&';  S = @("A3", "A1", "P", "H", "G1", "E1") + @("D3", "D1", "D2") + @("C", "K") }
    @{ C = "'";  S = @("I") }
    @{ C = '(';  S = @("A1", "A3", "F1", "E1", "D3", "D1") }
    @{ C = ')';  S = @("A5", "D5") }
    @{ C = '*';  S = @("H", "I", "L", "K", "P", "Q", "G1", "G2") }
    @{ C = '+';  S = $MID + $STEM }
    @{ C = ',';  S = @("COMMA") }
    @{ C = '-';  S = $MID }
    @{ C = '.';  S = @("DP") }
    @{ C = '/';  S = @("I", "L") }
    @{ C = '0';  S = $TOP_LEFT_HOOK + @("F1", "E1") + $BOT_LEFT_HOOK + $STEM }
    @{ C = '1';  S = $STEM }
    @{ C = '2';  S = $TOP_LEFT_HOOK + @("P", "G1", "E1") + $BOT_LEFT_HOOK }
    @{ C = '3';  S = $TOP_LEFT_HOOK + @("P", "G1", "Q") + $BOT_LEFT_HOOK }
    @{ C = '4';  S = @("F1", "G1") + $STEM }
    @{ C = '5';  S = $TOP_LEFT_HOOK + @("F1", "G1", "Q") + $BOT_LEFT_HOOK }
    @{ C = '6';  S = $TOP_LEFT + $UL + @("G1") + $LL + @("Q") + $BOT_LEFT }
    @{ C = '7';  S = $TOP_LEFT_HOOK + $STEM }
    @{ C = '8';  S = $TOP_LEFT_HOOK + @("F1", "G1", "E1") + $STEM + $BOT_LEFT_HOOK }
    @{ C = '9';  S = $TOP_LEFT_HOOK + @("F1", "G1") + $STEM + $BOT_LEFT_HOOK }
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
    @{ C = 'O';  S = @("A3", "A1", "A2", "F1", "B", "E1", "C", "D3", "D1", "D2") }
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
    @{ C = 'Z';  S = $TOP + @("I", "L", "E2") + $BOT }
    @{ C = '[';  S = @("A2", "P", "Q", "M", "O") }
    @{ C = '\';  S = @("H", "K") }
    @{ C = ']';  S = @("A1", "P", "Q", "M", "N") }
    @{ C = '^';  S = @("L", "K") }
    @{ C = '_';  S = $BOT }
    @{ C = '`';  S = @("H") }
    @{ C = 'a';  S = @("G1", "Q", "D3", "D1", "L") }
    @{ C = 'b';  S = $UL + @("E1", "G1", "D3", "D1", "Q") }
    @{ C = 'c';  S = @("E1", "G1", "D3", "D1") }
    @{ C = 'd';  S = @("E1", "G1", "D3", "D1") + $STEM }
    @{ C = 'e';  S = @("E1", "G1", "D3", "D1", "L") }
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
    @{ C = 'v';  S = @("E1", "L") }
    @{ C = 'w';  S = $LL + @("C", "L", "K") }
    @{ C = 'x';  S = @("J", "L") }
    @{ C = 'y';  S = $LL + @("D4", "D1", "Q", "M", "N") }
    @{ C = 'z';  S = @("G1", "L", "E2", "D4", "D1") }
    @{ C = '{';  S = @("A2", "P", "G1", "Q", "M", "O") }
    @{ C = '|';  S = $STEM + @("M") }
    @{ C = '}';  S = @("A1", "P", "G2", "Q", "M", "N") }
    @{ C = '~';  S = $UL + $TOP_LEFT + @("P", "G2", "B") }
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
        (BP 0 0), (BP 58.47 0), (BP 58.47 $TOTAL_HEIGHT), (BP 0 $TOTAL_HEIGHT)
    )
    $g.DrawPolygon($pen, [System.Drawing.PointF[]]$corners)
    $g.DrawLine($pen, (BP 0 100), (BP 58.47 100))

    $pen.Dispose()
}

# Draws one glyph with its cell origin at (ox, oy) canvas pixels, k px per unit.
# Every lit bar goes into ONE path stroked once, so overlaps are not brighter
# than anything else - mirrors drawTalkRpnCell in TalkRpnFont.kt.
function Draw-Glyph($g, $names, $ox, $oy, $k, $color) {

    $pen = New-Object System.Drawing.Pen $color, ($STROKE * $k)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $brush = New-Object System.Drawing.SolidBrush $color

    function PT($x, $y) {
        New-Object System.Drawing.PointF (
            ($ox + ($x - $SHEAR * $y + $SHEAR_OFFSET) * $k), ($oy + $y * $k))
    }

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath

    foreach ($n in $names) {

        if ($SEG_LINES.Contains($n)) {
            $s = $SEG_LINES[$n]
            $path.StartFigure()
            $path.AddLine((PT $s[0] $s[1]), (PT $s[2] $s[3]))
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $s = $SEG_ARCS[$n]
            $pts = @()
            foreach ($p in (New-ArcPoints $s[0] $s[1] $s[2] $s[3] $s[4])) {
                $pts += PT $p[0] $p[1]
            }
            $path.StartFigure()
            $path.AddLines([System.Drawing.PointF[]]$pts)
        }
        elseif ($SEG_POLYS.Contains($n)) {
            $pts = @()
            foreach ($p in $SEG_POLYS[$n]) { $pts += PT $p[0] $p[1] }
            $path.StartFigure()
            $path.AddLines([System.Drawing.PointF[]]$pts)
        }
        elseif ($n -eq "COMMA") {
            $path.StartFigure()
            $path.AddLine((PT $COMMA_TAIL[0] $COMMA_TAIL[1]), (PT $COMMA_TAIL[2] $COMMA_TAIL[3]))
        }
    }

    if ($path.PointCount -gt 0) { $g.DrawPath($pen, $path) }
    $path.Dispose()

    # Dots are filled, not stroked, and never overlap a bar.
    foreach ($n in $names) {
        if ($SEG_DOTS.Contains($n)) {
            $s = $SEG_DOTS[$n]
            $c = PT $s[0] $s[1]
            $r = $STROKE * $k
            $g.FillEllipse($brush, ($c.X - $r), ($c.Y - $r), (2 * $r), (2 * $r))
        }
    }

    $pen.Dispose()
    $brush.Dispose()
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
    "16 per row, code-point order 0x20-0x7E.  Stroke $STROKE, slant $SLANT_DEG deg.  Red = lit, grey = unlit, cyan = cell bounds and baseline.  Orange ? = low confidence: ' f t",
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

    $labelY = $oy + $GLYPH_H_UNITS * $k - 2
    $shown = if ($ch -eq ' ') { "sp" } else { $ch }
    $g.DrawString($shown, $charFont, $BLACK, ($ox - 4), $labelY)

    if ($LOW_CONFIDENCE -ccontains $ch) {
        $g.DrawString("?", $charFont, $ORANGE, ($ox + 14), $labelY)
    }

    $names = $entry.S -join " "
    $wrapped = [System.Drawing.RectangleF]::new(($ox + 30), ($labelY + 3), ($cellW - 32), ($LABEL_H - 4))
    $g.DrawString($names, $segFont, $GREY, $wrapped)
}

$g.Dispose()
$charsetPage = $bmp

# ---- the geometry pages -------------------------------------------------------

& "$PSScriptRoot\draw_talkrpn_geometry.ps1" -Section diagram -OutputName "talkrpn_geometry_diagram.png" | Out-Null
& "$PSScriptRoot\draw_talkrpn_geometry.ps1" -Section listing -OutputName "talkrpn_geometry_listing.png" | Out-Null

$geometryPages = @()

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








