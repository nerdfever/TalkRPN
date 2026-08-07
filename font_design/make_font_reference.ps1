# Build talkrpn_font_reference.pdf: the geometry reference on page 1, then the
# whole character set, every glyph drawn with its unlit segments ghosted and its
# lit segment NAMES printed underneath - so a wrong glyph can be corrected by
# dictating segment names, without a trip to the emulator.
#
# The glyph table here MIRRORS TalkRpnGlyphs.kt. If they disagree, the Kotlin
# wins; fix this one to match.
#
# The table is a LIST of pairs, not a hashtable: PowerShell hashtable keys are
# case-insensitive, so 'A' and 'a' would collide.

$OUT = "$PSScriptRoot\talkrpn_font_reference.pdf"
$GEOMETRY_PNG = "$PSScriptRoot\talkrpn_geometry.png"

# ---- geometry, in cell units (must match TalkRpnFont.kt) -------------------

$STROKE = 5.5        # the review weight; the nominal 9.29 is too heavy at 26 bars
$SLANT_DEG = 7.5
$TOTAL_HEIGHT = 144.0

# ---- page setup -------------------------------------------------------------

# Letter is 612 x 792 pt; bitmaps at this size keep exactly that aspect, so each
# page is one image with no scaling arithmetic in the PDF itself.
$PAGE_W = 1700
$PAGE_H = 2200

$MARGIN = 70.0
$HEADER_H = 100.0

$COLUMNS = 9
$ROWS = 6

# One glyph box in cell units: wide enough for the sheared cell plus the decimal
# point hanging off to the right, tall enough for the descender.
$GLYPH_W_UNITS = 97.0
$GLYPH_H_UNITS = 155.0

# Vertical space under each glyph for its character and segment listing.
$LABEL_H = 52.0

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

# Dots: centre x, centre y. The comma is its dot plus the tail below.
$SEG_DOTS = @{
    "COL1" = @(29.235, 20.49); "COL2" = @(29.235, 80.60)
    "DP" = @(86.64, 119.08);   "COMMA" = @(86.64, 119.08)
}
$COMMA_TAIL = @(86.64, 119.08, 78.99, 139.84)

$ALL_SEGMENTS = @($SEG_LINES.Keys) + @($SEG_ARCS.Keys) + @($SEG_DOTS.Keys)

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
    @{ C = '$';  S = $TOP + $UL + $MID + @("C") + $BOT + $STEM + @("M") }
    @{ C = '%';  S = $TOP_LEFT + $UL + @("G1", "P") + @("I", "L") + @("G2", "C", "D2", "Q") }
    @{ C = '&';  S = $TOP_LEFT + @("A3", "P", "H", "G1", "E1") + @("D3", "D1", "D2") + @("C", "K") }
    @{ C = "'";  S = @("I") }
    @{ C = '(';  S = @("A2", "P", "Q", "D2") }
    @{ C = ')';  S = @("A1", "P", "Q", "D1") }
    @{ C = '*';  S = @("H", "I", "L", "K", "P", "Q", "G1", "G2") }
    @{ C = '+';  S = $MID + $STEM }
    @{ C = ',';  S = @("COMMA") }
    @{ C = '-';  S = $MID }
    @{ C = '.';  S = @("DP") }
    @{ C = '/';  S = @("I", "L") }
    @{ C = '0';  S = $TOP_LEFT + $UL + $LL + $BOT_LEFT + $STEM }
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
    @{ C = '@';  S = $TOP + @("B", "C") + $BOT + @("E1", "G1", "Q") }
    @{ C = 'A';  S = $TOP + $UL + @("B") + $MID + $LL + @("C") }
    @{ C = 'B';  S = $TOP + @("B", "C") + $BOT + @("G2") + $STEM }
    @{ C = 'C';  S = $TOP + $UL + $LL + $BOT }
    @{ C = 'D';  S = $TOP + @("B", "C") + $BOT + $STEM }
    @{ C = 'E';  S = $TOP + $UL + @("G1") + $LL + $BOT }
    @{ C = 'F';  S = $TOP + $UL + @("G1") + $LL }
    @{ C = 'G';  S = $TOP + $UL + $LL + $BOT + @("C", "G2") }
    @{ C = 'H';  S = $UL + @("B") + $MID + $LL + @("C") }
    @{ C = 'I';  S = $TOP + $STEM + $BOT }
    @{ C = 'J';  S = @("B", "C") + $LL + $BOT }
    @{ C = 'K';  S = $UL + $LL + @("G1", "I", "K") }
    @{ C = 'L';  S = $UL + $LL + $BOT }
    @{ C = 'M';  S = $UL + @("H", "I", "B") + $LL + @("C") }
    @{ C = 'N';  S = $UL + @("H", "B", "K") + $LL + @("C") }
    @{ C = 'O';  S = $TOP + $UL + @("B") + $LL + @("C") + $BOT }
    @{ C = 'P';  S = $TOP + $UL + @("B") + $MID + $LL }
    @{ C = 'Q';  S = $TOP + $UL + @("B") + $LL + @("C") + $BOT + @("K") }
    @{ C = 'R';  S = $TOP + $UL + @("B") + $MID + $LL + @("K") }
    @{ C = 'S';  S = $TOP + $UL + $MID + @("C") + $BOT }
    @{ C = 'T';  S = $TOP + $STEM }
    @{ C = 'U';  S = $UL + @("B", "E1", "C", "D3", "D1", "D2") }
    @{ C = 'V';  S = $UL + $LL + @("I", "L") }
    @{ C = 'W';  S = $UL + @("B") + $LL + @("C", "L", "K") }
    @{ C = 'X';  S = @("H", "I", "L", "K") }
    @{ C = 'Y';  S = @("H", "I", "Q") }
    @{ C = 'Z';  S = $TOP + @("I", "L", "E2") + $BOT }
    @{ C = '[';  S = $TOP_LEFT + $UL + $LL + $BOT_LEFT }
    @{ C = '\';  S = @("H", "K") }
    @{ C = ']';  S = @("A2", "B", "C", "D2") }
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
    @{ C = '{';  S = @("A2", "P", "G1", "Q", "D2") }
    @{ C = '|';  S = $STEM }
    @{ C = '}';  S = @("A1", "P", "G2", "Q", "D1") }
    @{ C = '~';  S = $UL + $TOP_LEFT + @("P", "G2", "B") }
)

# ---- drawing ----------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$SHEAR = [Math]::Tan($SLANT_DEG * [Math]::PI / 180.0)
$SHEAR_OFFSET = $SHEAR * $TOTAL_HEIGHT

$LIT = [System.Drawing.Color]::FromArgb(210, 30, 20)

# The cell boundary, for diagnosing where ink sits inside its fixed pitch. Cyan
# so it cannot be mistaken for a segment.
$BOUNDS = [System.Drawing.Color]::FromArgb(120, 185, 200)
$BOUNDS_WIDTH = 1.1
$GHOST = [System.Drawing.Color]::FromArgb(225, 225, 225)
$BLACK = [System.Drawing.Brushes]::Black
$GREY = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(120, 120, 120))
$ORANGE = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(190, 110, 0))

# The cell's own boundary, with the baseline inside it.
#
# A parallelogram, not a rectangle: the slant leans the whole cell. Drawn from
# the centreline corners, so ink overhangs it by half a stroke all round - that
# is expected, and seeing by how much is the point.
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

    # The baseline: everything below it is descender.
    $g.DrawLine($pen, (BP 0 100), (BP 58.47 100))

    $pen.Dispose()
}

# Draws one glyph with its cell origin at (ox, oy) canvas pixels, k px per unit.
function Draw-Glyph($g, $names, $ox, $oy, $k, $color) {

    $pen = New-Object System.Drawing.Pen $color, ($STROKE * $k)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $brush = New-Object System.Drawing.SolidBrush $color

    # Cell coordinate to canvas, slant applied.
    function PT($x, $y) {
        New-Object System.Drawing.PointF (
            ($ox + ($x - $SHEAR * $y + $SHEAR_OFFSET) * $k), ($oy + $y * $k))
    }

    # Every lit bar goes into ONE path, stroked once.
    #
    # Drawing them one at a time made overlaps look brighter: the second
    # stroke's antialiased edge blends over the first, so doubled coverage
    # reads as a hotter line. A real display has no such seam. Unioned into a
    # single path, the pen strokes the outline and fills it once. This mirrors
    # what drawTalkRpnCell does in TalkRpnFont.kt.
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
            for ($i = 0; $i -le 20; $i++) {
                $t = ($s[3] + ($s[4] - $s[3]) * $i / 20.0) * [Math]::PI / 180.0
                $pts += PT ($s[0] + $s[2] * [Math]::Cos($t)) ($s[1] + $s[2] * [Math]::Sin($t))
            }
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

# ---- render the character-set pages ------------------------------------------

$titleFont = New-Object System.Drawing.Font "Segoe UI", 26, ([System.Drawing.FontStyle]::Bold)
$subFont = New-Object System.Drawing.Font "Segoe UI", 13
$charFont = New-Object System.Drawing.Font "Consolas", 22, ([System.Drawing.FontStyle]::Bold)
$segFont = New-Object System.Drawing.Font "Consolas", 9.5

$cellW = ($PAGE_W - 2 * $MARGIN) / $COLUMNS
$k = $cellW / $GLYPH_W_UNITS
$rowH = $GLYPH_H_UNITS * $k + $LABEL_H

$perPage = $COLUMNS * $ROWS
$pageCount = [Math]::Ceiling($GLYPHS.Count / $perPage)

$pageBitmaps = @()

for ($p = 0; $p -lt $pageCount; $p++) {

    $bmp = New-Object System.Drawing.Bitmap $PAGE_W, $PAGE_H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $g.Clear([System.Drawing.Color]::White)

    $g.DrawString("TalkRPN character set  -  page $($p + 1) of $pageCount", $titleFont, $BLACK, $MARGIN, 26)
    $g.DrawString(
        "stroke $STROKE, slant $SLANT_DEG deg.  Red = lit, grey = unlit.  Digits half-width on P/Q.  Orange ? = low-confidence reading of the DL-3422 chart.",
        $subFont, $GREY, $MARGIN, 68)

    for ($i = 0; $i -lt $perPage; $i++) {

        $index = $p * $perPage + $i
        if ($index -ge $GLYPHS.Count) { break }

        $entry = $GLYPHS[$index]
        $ch = [string]$entry.C
        $col = $i % $COLUMNS
        $row = [Math]::Floor($i / $COLUMNS)

        $ox = $MARGIN + $col * $cellW
        $oy = $MARGIN + $HEADER_H + $row * $rowH + $STROKE * $k

        # Cell bounds first, then the ghost, then the lit mask over both.
        Draw-CellBounds $g $ox $oy $k
        Draw-Glyph $g $ALL_SEGMENTS $ox $oy $k $GHOST
        Draw-Glyph $g $entry.S $ox $oy $k $LIT

        # The character, then its segment names, wrapped to the cell width.
        $labelY = $oy + $GLYPH_H_UNITS * $k - 4
        $shown = if ($ch -eq ' ') { "sp" } else { $ch }
        $g.DrawString($shown, $charFont, $BLACK, ($ox - 6), $labelY)

        if ($LOW_CONFIDENCE -ccontains $ch) {
            $g.DrawString("?", $charFont, $ORANGE, ($ox + 28), $labelY)
        }

        $names = $entry.S -join " "
        $wrapped = [System.Drawing.RectangleF]::new(($ox + 52), ($labelY + 6), ($cellW - 56), ($LABEL_H - 6))
        $g.DrawString($names, $segFont, $GREY, $wrapped)
    }

    $g.Dispose()
    $pageBitmaps += $bmp
}

# ---- render the geometry page ------------------------------------------------

# Regenerate both halves, then give each a page. Splitting them is what lets the
# diagram be read at a useful size - sharing a sheet with fifty lines of listing
# shrank it to about half this.
& "$PSScriptRoot\draw_talkrpn_geometry.ps1" -Section diagram -OutputName "talkrpn_geometry_diagram.png" | Out-Null
& "$PSScriptRoot\draw_talkrpn_geometry.ps1" -Section listing -OutputName "talkrpn_geometry_listing.png" | Out-Null

$geometryPages = @()

foreach ($name in @("talkrpn_geometry_diagram.png", "talkrpn_geometry_listing.png")) {

    $geo = [System.Drawing.Image]::FromFile("$PSScriptRoot\$name")

    $bmp = New-Object System.Drawing.Bitmap $PAGE_W, $PAGE_H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.Clear([System.Drawing.Color]::White)

    # Fit by whichever axis binds, then sit at the top rather than centred - a
    # short listing centred on a tall page reads as a mistake.
    $scale = [Math]::Min(($PAGE_W - 2 * $MARGIN) / $geo.Width, ($PAGE_H - 2 * $MARGIN) / $geo.Height)
    $w = $geo.Width * $scale
    $h = $geo.Height * $scale
    $g.DrawImage($geo, (($PAGE_W - $w) / 2), $MARGIN, $w, $h)

    $g.Dispose()
    $geo.Dispose()

    $geometryPages += $bmp
}

# Geometry first, then the character pages.
$pageBitmaps = $geometryPages + $pageBitmaps

# ---- assemble the PDF ---------------------------------------------------------
#
# Hand-built: each page is a single JPEG image object drawn over the full page.
# JPEG because PDF accepts those streams verbatim (DCTDecode); no libraries
# needed, and nothing to install.

$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
    Where-Object { $_.MimeType -eq "image/jpeg" }
$encParams = New-Object System.Drawing.Imaging.EncoderParameters 1
$encParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter (
    [System.Drawing.Imaging.Encoder]::Quality, [long]88)

$jpegs = @()
foreach ($pb in $pageBitmaps) {
    $ms = New-Object System.IO.MemoryStream
    $pb.Save($ms, $jpegCodec, $encParams)
    $jpegs += , $ms.ToArray()
    $ms.Dispose()
    $pb.Dispose()
}

$enc = [System.Text.Encoding]::ASCII
$pdfStream = New-Object System.IO.MemoryStream
$offsets = New-Object System.Collections.Generic.List[long]

function Write-Bytes([byte[]] $b) { $pdfStream.Write($b, 0, $b.Length) }
function Write-Text([string] $s) { Write-Bytes ($enc.GetBytes($s)) }
function Begin-Object([int] $num) { $offsets.Add($pdfStream.Position); Write-Text "$num 0 obj`n" }

Write-Text "%PDF-1.4`n"

# Object numbering: 1 catalog, 2 pages, then per page k: 3+3k page, 4+3k image,
# 5+3k contents.
$n = $jpegs.Count
$kids = (0..($n - 1) | ForEach-Object { "$(3 + 3 * $_) 0 R" }) -join " "

Begin-Object 1
Write-Text "<< /Type /Catalog /Pages 2 0 R >>`nendobj`n"

Begin-Object 2
Write-Text "<< /Type /Pages /Kids [$kids] /Count $n >>`nendobj`n"

for ($p = 0; $p -lt $n; $p++) {

    $pageObj = 3 + 3 * $p
    $imgObj = 4 + 3 * $p
    $contObj = 5 + 3 * $p

    Begin-Object $pageObj
    Write-Text ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
        "/Resources << /XObject << /Im0 $imgObj 0 R >> >> /Contents $contObj 0 R >>`nendobj`n")

    Begin-Object $imgObj
    Write-Text ("<< /Type /XObject /Subtype /Image /Width $PAGE_W /Height $PAGE_H " +
        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
        "/Length $($jpegs[$p].Length) >>`nstream`n")
    Write-Bytes $jpegs[$p]
    Write-Text "`nendstream`nendobj`n"

    $content = "q 612 0 0 792 0 0 cm /Im0 Do Q"
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
    Write-Output "wrote $OUT  ($($jpegs.Count) pages)"
}
catch [System.IO.IOException] {
    Write-Output "COULD NOT WRITE $OUT - it is open in another program. Close it and re-run."
}
finally {
    $pdfStream.Dispose()
}














