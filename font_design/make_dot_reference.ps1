# Build talkrpn_dot_reference.png: the whole HDLS-1414 dot-matrix character set
# on one sheet, 16 per row in code-point order 0x00-0x7F - the same layout as
# the part's datasheet chart, so the two can be compared side by side.
#
# The glyphs are PARSED OUT OF Hdls1414Glyphs.kt at run time, not copied here.
# The Kotlin file is the single source of truth; this sheet cannot drift from
# it, only fail to build.
#
# THE UNIT: one column pitch - dot centre to dot centre, across - is 1, exactly
# as in Hdls1414Font.kt. Every length below is in that unit unless its name says
# PX; pixels appear only at the render boundary, via $PITCH_PX.

# ---- files ------------------------------------------------------------------

$GLYPH_SOURCE = "$PSScriptRoot\..\app\src\main\java\com\nerdfever\talkrpn\Hdls1414Glyphs.kt"
$OUT = "$PSScriptRoot\talkrpn_dot_reference.png"

# ---- the character set, as the hardware defines it ---------------------------

# 128 codes, no gaps - the part decodes 0x00-0x7F and shows something for each.
$GLYPH_COUNT = 128

# A cell is 5 x 7 dots; the glyph table is written to that shape.
$DOT_COLUMNS = 5
$DOT_ROWS = 7

# ---- cell geometry (must match Hdls1414Font.kt) -------------------------------

$COLUMN_PITCH = 1.0          # by definition; the unit itself
$ROW_PITCH = 1.065           # dot centre to dot centre, down
$DOT_DIAMETER = 0.70         # width of one lit dot

# Centre-to-centre span of a cell, and its ink box with the dots' overhang.
$CELL_SPAN_W = ($DOT_COLUMNS - 1) * $COLUMN_PITCH
$CELL_SPAN_H = ($DOT_ROWS - 1) * $ROW_PITCH
$INK_W = $CELL_SPAN_W + $DOT_DIAMETER
$INK_H = $CELL_SPAN_H + $DOT_DIAMETER

# ---- colours ------------------------------------------------------------------

# The font's default lit colour; mirrors NEON_ORANGE in Hdls1414Font.kt.
$LIT_RGB = @(0xFF, 0x5F, 0x1F)

# Unlit dots, just visible - the dark dies an unpowered LED matrix still shows.
$UNLIT_RGB = @(42, 24, 14)

$TEXT_RGB = @(200, 200, 200)         # titles and per-cell labels
$DIM_TEXT_RGB = @(130, 130, 130)     # the subtitle and the page number
$GRID_RGB = @(60, 60, 60)            # cell grid lines

# ---- page layout ----------------------------------------------------------------

# The scale: pixels per column pitch. Everything on the page hangs off this one
# number; at 16 a lit dot is 11 px across, comfortably legible in print.
$PITCH_PX = 16.0

# One glyph box in pitches: five dot columns with a blank column either side,
# and breathing room above and below the ink.
$CELL_W_UNITS = 7.0
$CELL_PAD_V_UNITS = 0.6

# The traditional chart: 16 columns, code-point order - eight full rows.
# Named CHART_*, NOT bare $ROWS: PowerShell variables are case-insensitive, so
# a bare $ROWS would be the same variable as any lower-case $rows elsewhere.
$CHART_COLUMNS = 16
$CHART_ROWS = $GLYPH_COUNT / $CHART_COLUMNS

# Page furniture, in pixels - it holds text set in a system font, which has no
# lattice to scale to.
$MARGIN_PX = 50.0
$HEADER_H_PX = 60.0
$LABEL_H_PX = 30.0           # under each glyph, for its hex code and character

# The hex indices, as on the datasheet: low nibble 0-F across the top, high
# nibble 0-7 down the left gutter.
$INDEX_W_PX = 50.0
$HEX_HEADER_H_PX = 30.0

# Label typefaces. The subtitle is the sheet's body text; the page number is
# set one point smaller than it, per house rule.
$TITLE_FONT_NAME = "Segoe UI";  $TITLE_PT = 20
$SUB_FONT_NAME = "Segoe UI";    $SUB_PT = 10
$LABEL_FONT_NAME = "Consolas";  $LABEL_PT = 10
$HEX_FONT_NAME = "Consolas";    $HEX_PT = 13
$PAGE_NUMBER_PT = $SUB_PT - 1

$PAGE_NUMBER_INSET_PX = 34   # from the bottom-right corner

# ---- parse the Kotlin glyph table ----------------------------------------------

# Comments go first: an entry like 0x29's carries its own character in a
# trailing comment - a literal ')' - which would otherwise close the dots(...)
# body early.
$source = [System.IO.File]::ReadAllText($GLYPH_SOURCE) -replace '(?m)//.*$', ''

# Each entry is `0xNN to dots(` and seven binary row literals, top row first,
# leftmost dot as the most significant bit - so a row string IS the row.
$entryRegex = [regex]'0x(?<code>[0-9A-Fa-f]{2})\s+to\s+dots\((?<body>[^)]*)\)'
$rowRegex = [regex]'0b(?<bits>[01]{1,5})'

$glyphs = @{}
$badEntries = @()

foreach ($entry in $entryRegex.Matches($source)) {

    $code = [Convert]::ToInt32($entry.Groups["code"].Value, 16)

    # The seven rows, each padded back out to five columns - the Kotlin writes
    # full-width literals, but a dropped leading zero must not shift the row.
    $rowPatterns = @($rowRegex.Matches($entry.Groups["body"].Value) |
        ForEach-Object { $_.Groups["bits"].Value.PadLeft($DOT_COLUMNS, '0') })

    # Anything but exactly seven rows, or a code seen twice, is a broken parse.
    if ($rowPatterns.Count -ne $DOT_ROWS -or $glyphs.ContainsKey($code)) {
        $badEntries += ("0x{0:X2} ({1} rows)" -f $code, $rowPatterns.Count)
        continue
    }

    $glyphs[$code] = $rowPatterns
}

# Fail loudly rather than print a chart with holes: the sheet's whole point is
# to be the Kotlin table, dot for dot.
if ($badEntries.Count -gt 0 -or $glyphs.Count -ne $GLYPH_COUNT) {
    Write-Output ("PARSE FAILURE: expected {0} glyphs from {1}, parsed {2}." -f
        $GLYPH_COUNT, $GLYPH_SOURCE, $glyphs.Count)
    if ($badEntries.Count -gt 0) {
        Write-Output ("Bad entries: " + ($badEntries -join ", "))
    }
    exit 1
}

# Every code 0x00-0x7F must be present exactly once - 128 entries with a
# duplicate and a gap would still pass a bare count.
$missing = @(0..($GLYPH_COUNT - 1) | Where-Object { -not $glyphs.ContainsKey($_) })

if ($missing.Count -gt 0) {
    Write-Output ("PARSE FAILURE: missing codes: " +
        (($missing | ForEach-Object { "0x{0:X2}" -f $_ }) -join ", "))
    exit 1
}

Write-Output ("parsed {0} glyphs from {1}" -f $glyphs.Count, $GLYPH_SOURCE)

# ---- derived page dimensions ----------------------------------------------------

$cellW_px = $CELL_W_UNITS * $PITCH_PX
$glyphH_px = ($CELL_PAD_V_UNITS + $INK_H + $CELL_PAD_V_UNITS) * $PITCH_PX
$rowH_px = $glyphH_px + $LABEL_H_PX

$gridLeft = $MARGIN_PX
$gridTop = $MARGIN_PX + $HEADER_H_PX
$glyphTop = $gridTop + $HEX_HEADER_H_PX
$gridRight = $gridLeft + $INDEX_W_PX + $CHART_COLUMNS * $cellW_px
$gridBottom = $glyphTop + $CHART_ROWS * $rowH_px

$pageW = [int][Math]::Ceiling($gridRight + $MARGIN_PX)
$pageH = [int][Math]::Ceiling($gridBottom + $MARGIN_PX)

# ---- drawing setup ----------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

function New-Rgb($rgb) { [System.Drawing.Color]::FromArgb($rgb[0], $rgb[1], $rgb[2]) }

$litBrush = New-Object System.Drawing.SolidBrush (New-Rgb $LIT_RGB)
$unlitBrush = New-Object System.Drawing.SolidBrush (New-Rgb $UNLIT_RGB)
$textBrush = New-Object System.Drawing.SolidBrush (New-Rgb $TEXT_RGB)
$dimBrush = New-Object System.Drawing.SolidBrush (New-Rgb $DIM_TEXT_RGB)
$gridPen = New-Object System.Drawing.Pen (New-Rgb $GRID_RGB), 1.0

$titleFont = New-Object System.Drawing.Font $TITLE_FONT_NAME, $TITLE_PT, ([System.Drawing.FontStyle]::Bold)
$subFont = New-Object System.Drawing.Font $SUB_FONT_NAME, $SUB_PT
$labelFont = New-Object System.Drawing.Font $LABEL_FONT_NAME, $LABEL_PT
$hexFont = New-Object System.Drawing.Font $HEX_FONT_NAME, $HEX_PT, ([System.Drawing.FontStyle]::Bold)
$pageNumberFont = New-Object System.Drawing.Font $LABEL_FONT_NAME, $PAGE_NUMBER_PT

$bmp = New-Object System.Drawing.Bitmap $pageW, $pageH
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

# Plain antialiasing, not ClearType: subpixel fringes glow on a black ground.
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$g.Clear([System.Drawing.Color]::Black)

# ---- one cell's dots ----------------------------------------------------------------

# Draws one glyph with the centre of its top-left dot at ($ox, $oy) pixels:
# every unlit dot dimly, then the lit ones over them.
function Draw-DotCell($g, $rowPatterns, $ox, $oy) {

    $radius = $DOT_DIAMETER / 2.0 * $PITCH_PX

    for ($row = 0; $row -lt $DOT_ROWS; $row++) {
        for ($col = 0; $col -lt $DOT_COLUMNS; $col++) {

            # Leftmost dot is the row string's first character.
            $lit = $rowPatterns[$row][$col] -eq '1'
            $brush = if ($lit) { $litBrush } else { $unlitBrush }

            $cx = $ox + $col * $COLUMN_PITCH * $PITCH_PX
            $cy = $oy + $row * $ROW_PITCH * $PITCH_PX

            $g.FillEllipse($brush, ($cx - $radius), ($cy - $radius),
                (2 * $radius), (2 * $radius))
        }
    }
}

# ---- header -----------------------------------------------------------------------

$g.DrawString("TalkRPN HDLS-1414 character set", $titleFont, $textBrush, $MARGIN_PX, 8)
$g.DrawString(
    ("16 per row, code-point order 0x00-0x7F.  Row pitch {0:F3}, dot diameter {1:F2}, in column pitches.  Orange = lit, faint = unlit.  Parsed from Hdls1414Glyphs.kt." -f $ROW_PITCH, $DOT_DIAMETER),
    $subFont, $dimBrush, ($MARGIN_PX + 480), 20)

# ---- the datasheet furniture: grid and hex indices -----------------------------------

foreach ($c in 0..$CHART_COLUMNS) {
    $x = $gridLeft + $INDEX_W_PX + $c * $cellW_px
    $g.DrawLine($gridPen, $x, $gridTop, $x, $gridBottom)
}
$g.DrawLine($gridPen, $gridLeft, $gridTop, $gridLeft, $gridBottom)

foreach ($r in 0..$CHART_ROWS) {
    $y = $glyphTop + $r * $rowH_px
    $g.DrawLine($gridPen, $gridLeft, $y, $gridRight, $y)
}
$g.DrawLine($gridPen, $gridLeft, $gridTop, $gridRight, $gridTop)

# Low nibble across the top, high nibble down the left gutter.
foreach ($c in 0..($CHART_COLUMNS - 1)) {
    $g.DrawString(("{0:X}" -f $c), $hexFont, $textBrush,
        ($gridLeft + $INDEX_W_PX + ($c + 0.5) * $cellW_px - 8), ($gridTop + 4))
}

foreach ($r in 0..($CHART_ROWS - 1)) {
    $g.DrawString(("{0:X}" -f $r), $hexFont, $textBrush,
        ($gridLeft + 16), ($glyphTop + $r * $rowH_px + $glyphH_px / 2 - 10))
}

# ---- the glyphs -----------------------------------------------------------------------

# In from the cell's left edge to the first dot centre: the ink is centred in
# the cell box, and the first centre sits half a dot inside the ink.
$glyphLeft_units = ($CELL_W_UNITS - $INK_W) / 2.0 + $DOT_DIAMETER / 2.0
$glyphTopPad_units = $CELL_PAD_V_UNITS + $DOT_DIAMETER / 2.0

for ($code = 0; $code -lt $GLYPH_COUNT; $code++) {

    $col = $code % $CHART_COLUMNS
    $row = [Math]::Floor($code / $CHART_COLUMNS)

    # The top-left dot centre of this cell, in page pixels.
    $ox = $gridLeft + $INDEX_W_PX + $col * $cellW_px + $glyphLeft_units * $PITCH_PX
    $oy = $glyphTop + $row * $rowH_px + $glyphTopPad_units * $PITCH_PX

    Draw-DotCell $g $glyphs[$code] $ox $oy

    # The label: hex code, and the character itself where there is one to
    # print. 0x00-0x1F are the international bank and 0x7F the test block -
    # nothing this label font could show - so they carry only their hex.
    $label = "{0:X2}" -f $code
    if ($code -ge 0x21 -and $code -le 0x7E) { $label += "  " + [char]$code }
    elseif ($code -eq 0x20) { $label += "  sp" }

    $g.DrawString($label, $labelFont, $textBrush,
        ($gridLeft + $INDEX_W_PX + $col * $cellW_px + 6),
        ($glyphTop + $row * $rowH_px + $glyphH_px))
}

# ---- page number ------------------------------------------------------------------------

# "n/m" at the bottom right, one point smaller than the body text - the house
# rule for every sheet, even a one-page one.
$pageText = "1/1"
$pageSize = $g.MeasureString($pageText, $pageNumberFont)

$g.DrawString($pageText, $pageNumberFont, $dimBrush,
    ($pageW - $PAGE_NUMBER_INSET_PX - $pageSize.Width),
    ($pageH - $PAGE_NUMBER_INSET_PX - $pageSize.Height))

# ---- write the sheet ---------------------------------------------------------------------

$g.Dispose()

# A PNG open in a viewer is locked for writing. Say so plainly rather than
# reporting success over a file that was never replaced.
try {
    $bmp.Save($OUT, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output ("wrote {0}  ({1} x {2} px)" -f $OUT, $bmp.Width, $bmp.Height)
}
catch [System.Runtime.InteropServices.ExternalException] {
    Write-Output "COULD NOT WRITE $OUT - it is open in another program. Close it and re-run."
    exit 1
}
finally {
    $bmp.Dispose()
}
