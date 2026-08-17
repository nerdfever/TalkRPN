# Build talkrpn_dot_reference.png AND talkrpn_dot_reference.pdf: the whole
# HDLS-1414 dot-matrix character set on one sheet, 16 per row in code-point
# order 0x00-0x7F - the same layout as the part's datasheet chart, so the two
# can be compared side by side.
#
# The glyphs are PARSED OUT OF Hdls1414Glyphs.kt at run time, not copied here.
# The Kotlin file is the single source of truth; this sheet cannot drift from
# it, only fail to build.
#
# One parse and one layout produce BOTH outputs: the layout pass fills lists of
# squares, grid segments and text items, and two emitters - GDI+ for the PNG, a
# hand-assembled PDF 1.4 for the PDF - each walk the same lists. The PDF is
# vector: every dot is a plain `re f` rectangle, text is the base-14 fonts.
#
# THE UNIT: one column pitch - dot centre to dot centre, across - is 1, exactly
# as in Hdls1414Font.kt. Every length below is in that unit unless its name says
# PX or PT; pixels and points appear only at the render boundaries, via
# $PITCH_PX and $PT_PER_PX.

# ---- files ------------------------------------------------------------------

$GLYPH_SOURCE = "$PSScriptRoot\..\app\src\main\java\com\nerdfever\talkrpn\Hdls1414Glyphs.kt"
$PNG_OUT = "$PSScriptRoot\talkrpn_dot_reference.png"
$PDF_OUT = "$PSScriptRoot\talkrpn_dot_reference.pdf"

# ---- the character set, as the hardware defines it ---------------------------

# 128 codes, no gaps - the part decodes 0x00-0x7F and shows something for each.
$GLYPH_COUNT = 128

# A cell is 5 x 7 dots; the glyph table is written to that shape.
$DOT_COLUMNS = 5
$DOT_ROWS = 7

# ---- cell geometry (must match Hdls1414Font.kt) -------------------------------

$COLUMN_PITCH = 1.0          # by definition; the unit itself
$ROW_PITCH = 1.098           # dot centre to dot centre, down
$DOT_SIDE = 0.7033           # side of one lit dot's square, centred on its lattice point

# Centre-to-centre span of a cell, and its ink box with the dots' overhang.
$CELL_SPAN_W = ($DOT_COLUMNS - 1) * $COLUMN_PITCH
$CELL_SPAN_H = ($DOT_ROWS - 1) * $ROW_PITCH
$INK_W = $CELL_SPAN_W + $DOT_SIDE
$INK_H = $CELL_SPAN_H + $DOT_SIDE

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

$PAGE_NUMBER_INSET_PX = 34   # from the bottom-right corner

# ---- text styles ------------------------------------------------------------------
#
# One table for both emitters: the point size and colour are shared, the face
# is named twice because the bitmap uses installed system fonts while the PDF
# is restricted to the base-14 it can use without embedding. The subtitle is
# the sheet's body text; the page number is one point smaller, per house rule.

$SUB_PT = 10

$TEXT_STYLES = @{
    title = @{ Pt = 20;           Gdi = "Segoe UI"; Bold = $true;  Pdf = "Helvetica-Bold"; Rgb = $TEXT_RGB }
    sub   = @{ Pt = $SUB_PT;      Gdi = "Segoe UI"; Bold = $false; Pdf = "Helvetica";      Rgb = $DIM_TEXT_RGB }
    hex   = @{ Pt = 13;           Gdi = "Consolas"; Bold = $true;  Pdf = "Courier-Bold";   Rgb = $TEXT_RGB }
    label = @{ Pt = 10;           Gdi = "Consolas"; Bold = $false; Pdf = "Courier";        Rgb = $TEXT_RGB }
    page  = @{ Pt = $SUB_PT - 1;  Gdi = "Consolas"; Bold = $false; Pdf = "Courier";        Rgb = $DIM_TEXT_RGB }
}

# GDI+ font sizes are points at 96 dpi, so one em of a $SUB_PT font spans this
# many layout pixels. Both emitters size text through this one conversion.
$PX_PER_POINT = 96.0 / 72.0

# A monospace glyph's advance in ems - Courier's metric, close enough for
# Consolas too. Used to right-align the page number without measuring.
$MONO_ADVANCE_EM = 0.6

# Top of a text box down to its baseline, in ems. An approximation shared by
# both emitters so their text lands in the same place; exact ascents differ
# per face by a few percent of an em, invisible at this size.
$BASELINE_EM = 1.0

# ---- PDF page ---------------------------------------------------------------------

# Landscape US letter. The layout's aspect (1942:1492) is within 1% of the
# page's, so a uniform scale fills it edge to edge with no distortion.
$PDF_PAGE_W_PT = 792.0
$PDF_PAGE_H_PT = 612.0

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

# ---- the layout pass -------------------------------------------------------------
#
# Everything on the sheet becomes an entry in one of four lists, all in layout
# pixels with y running down. The two emitters below draw the lists and nothing
# else, so the outputs cannot disagree.

$litSquares = New-Object System.Collections.Generic.List[object]     # top-left x, y; side is $dotSide_px
$unlitSquares = New-Object System.Collections.Generic.List[object]
$gridSegments = New-Object System.Collections.Generic.List[object]   # x1, y1, x2, y2
$textItems = New-Object System.Collections.Generic.List[object]      # @{ Text; X; YTop; Style }

$dotSide_px = $DOT_SIDE * $PITCH_PX

# The header.
$textItems.Add(@{ Text = "TalkRPN HDLS-1414 character set"; X = $MARGIN_PX; YTop = 8; Style = "title" })
$textItems.Add(@{
    Text = ("16 per row, code-point order 0x00-0x7F.  Row pitch {0:F3}, dot side {1:F4}, in column pitches.  Orange = lit, faint = unlit.  Parsed from Hdls1414Glyphs.kt." -f $ROW_PITCH, $DOT_SIDE)
    X = $MARGIN_PX + 480; YTop = 20; Style = "sub"
})

# The datasheet furniture: a grid, and hex indices - low nibble across the top,
# high nibble down the left gutter.
foreach ($c in 0..$CHART_COLUMNS) {
    $x = $gridLeft + $INDEX_W_PX + $c * $cellW_px
    $gridSegments.Add(@($x, $gridTop, $x, $gridBottom))
}
$gridSegments.Add(@($gridLeft, $gridTop, $gridLeft, $gridBottom))

foreach ($r in 0..$CHART_ROWS) {
    $y = $glyphTop + $r * $rowH_px
    $gridSegments.Add(@($gridLeft, $y, $gridRight, $y))
}
$gridSegments.Add(@($gridLeft, $gridTop, $gridRight, $gridTop))

foreach ($c in 0..($CHART_COLUMNS - 1)) {
    $textItems.Add(@{
        Text = ("{0:X}" -f $c); Style = "hex"
        X = $gridLeft + $INDEX_W_PX + ($c + 0.5) * $cellW_px - 8; YTop = $gridTop + 4
    })
}

foreach ($r in 0..($CHART_ROWS - 1)) {
    $textItems.Add(@{
        Text = ("{0:X}" -f $r); Style = "hex"
        X = $gridLeft + 16; YTop = $glyphTop + $r * $rowH_px + $glyphH_px / 2 - 10
    })
}

# The glyphs. In from the cell's left edge to the first dot centre: the ink is
# centred in the cell box, and the first centre sits half a dot inside the ink.
$glyphLeft_units = ($CELL_W_UNITS - $INK_W) / 2.0 + $DOT_SIDE / 2.0
$glyphTopPad_units = $CELL_PAD_V_UNITS + $DOT_SIDE / 2.0

for ($code = 0; $code -lt $GLYPH_COUNT; $code++) {

    $col = $code % $CHART_COLUMNS
    $row = [Math]::Floor($code / $CHART_COLUMNS)

    # The top-left dot centre of this cell, in layout pixels.
    $ox = $gridLeft + $INDEX_W_PX + $col * $cellW_px + $glyphLeft_units * $PITCH_PX
    $oy = $glyphTop + $row * $rowH_px + $glyphTopPad_units * $PITCH_PX

    # Every dot as a square centred on its lattice point: the unlit dimly, the
    # lit in the ink colour. Leftmost dot is the row string's first character.
    $rowPatterns = $glyphs[$code]

    for ($r = 0; $r -lt $DOT_ROWS; $r++) {
        for ($c = 0; $c -lt $DOT_COLUMNS; $c++) {

            $sx = $ox + $c * $COLUMN_PITCH * $PITCH_PX - $dotSide_px / 2.0
            $sy = $oy + $r * $ROW_PITCH * $PITCH_PX - $dotSide_px / 2.0

            if ($rowPatterns[$r][$c] -eq '1') { $litSquares.Add(@($sx, $sy)) }
            else { $unlitSquares.Add(@($sx, $sy)) }
        }
    }

    # The label: hex code, and the character itself where there is one to
    # print. 0x00-0x1F are the international bank and 0x7F the test block -
    # nothing this label font could show - so they carry only their hex.
    $label = "{0:X2}" -f $code
    if ($code -ge 0x21 -and $code -le 0x7E) { $label += "  " + [char]$code }
    elseif ($code -eq 0x20) { $label += "  sp" }

    $textItems.Add(@{
        Text = $label; Style = "label"
        X = $gridLeft + $INDEX_W_PX + $col * $cellW_px + 6
        YTop = $glyphTop + $row * $rowH_px + $glyphH_px
    })
}

# The page number: "n/m" at the bottom right, one point smaller than the body
# text - the house rule for every sheet, even a one-page one. Right-aligned by
# the monospace advance rather than by measuring, so both emitters agree.
$pageText = "1/1"
$pageNumberEm_px = $TEXT_STYLES["page"].Pt * $PX_PER_POINT

$textItems.Add(@{
    Text = $pageText; Style = "page"
    X = $pageW - $PAGE_NUMBER_INSET_PX - $pageText.Length * $MONO_ADVANCE_EM * $pageNumberEm_px
    YTop = $pageH - $PAGE_NUMBER_INSET_PX - $BASELINE_EM * $pageNumberEm_px
})

# ---- emit the PNG ------------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

function New-Rgb($rgb) { [System.Drawing.Color]::FromArgb($rgb[0], $rgb[1], $rgb[2]) }

$litBrush = New-Object System.Drawing.SolidBrush (New-Rgb $LIT_RGB)
$unlitBrush = New-Object System.Drawing.SolidBrush (New-Rgb $UNLIT_RGB)
$gridPen = New-Object System.Drawing.Pen (New-Rgb $GRID_RGB), 1.0

$bmp = New-Object System.Drawing.Bitmap $pageW, $pageH
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

# Plain antialiasing, not ClearType: subpixel fringes glow on a black ground.
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$g.Clear([System.Drawing.Color]::Black)

# The grid first, so the dots sit over it.
foreach ($seg in $gridSegments) {
    $g.DrawLine($gridPen, [float]$seg[0], [float]$seg[1], [float]$seg[2], [float]$seg[3])
}

# The dots: every unlit square dimly, then the lit ones.
foreach ($sq in $unlitSquares) {
    $g.FillRectangle($unlitBrush, [float]$sq[0], [float]$sq[1], [float]$dotSide_px, [float]$dotSide_px)
}
foreach ($sq in $litSquares) {
    $g.FillRectangle($litBrush, [float]$sq[0], [float]$sq[1], [float]$dotSide_px, [float]$dotSide_px)
}

# The text, each item in its style's face and colour.
foreach ($item in $textItems) {

    $style = $TEXT_STYLES[$item.Style]
    $gdiStyle = if ($style.Bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }

    $font = New-Object System.Drawing.Font $style.Gdi, $style.Pt, $gdiStyle
    $brush = New-Object System.Drawing.SolidBrush (New-Rgb $style.Rgb)

    $g.DrawString($item.Text, $font, $brush, [float]$item.X, [float]$item.YTop)

    $font.Dispose()
    $brush.Dispose()
}

$g.Dispose()

# A PNG open in a viewer is locked for writing. Say so plainly rather than
# reporting success over a file that was never replaced.
try {
    $bmp.Save($PNG_OUT, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output ("wrote {0}  ({1} x {2} px)" -f $PNG_OUT, $bmp.Width, $bmp.Height)
}
catch [System.Runtime.InteropServices.ExternalException] {
    Write-Output "COULD NOT WRITE $PNG_OUT - it is open in another program. Close it and re-run."
    exit 1
}
finally {
    $bmp.Dispose()
}

# ---- emit the PDF: the content stream ------------------------------------------------
#
# The same lists again, as PDF drawing operators this time. PDF y runs UP from
# the bottom-left corner, so every layout y is flipped on the way through; the
# uniform scale keeps the squares square, and the sliver of slack the aspect
# difference leaves is split top and bottom.

$PT_PER_PX = [Math]::Min($PDF_PAGE_W_PT / $pageW, $PDF_PAGE_H_PT / $pageH)
$pdfSlackY = ($PDF_PAGE_H_PT - $pageH * $PT_PER_PX) / 2.0
$pdfSlackX = ($PDF_PAGE_W_PT - $pageW * $PT_PER_PX) / 2.0

# Decimal points, not whatever the locale writes - PDF numbers are ASCII.
function Fmt($v) { ([double]$v).ToString("0.###", [System.Globalization.CultureInfo]::InvariantCulture) }

# Layout pixels to page points, y flipped.
function PdfX($x) { Fmt ($pdfSlackX + $x * $PT_PER_PX) }
function PdfY($y) { Fmt ($PDF_PAGE_H_PT - $pdfSlackY - $y * $PT_PER_PX) }

# An rg/RG colour triplet from 0-255 channels.
function PdfRgb($rgb) { "{0} {1} {2}" -f (Fmt ($rgb[0] / 255.0)), (Fmt ($rgb[1] / 255.0)), (Fmt ($rgb[2] / 255.0)) }

# ( ) and \ are the string delimiters and must be escaped - and the character
# labels for 0x28, 0x29 and 0x5C contain exactly those. In a .NET replacement
# string a backslash is literal, so '\\' really is backslash-backslash.
function Escape-PdfText($s) { $s -replace '\\', '\\' -replace '\(', '\(' -replace '\)', '\)' }

$content = New-Object System.Text.StringBuilder
function Emit($op) { [void]$content.AppendLine($op) }

# The black ground, edge to edge.
Emit "0 0 0 rg"
Emit ("0 0 {0} {1} re f" -f (Fmt $PDF_PAGE_W_PT), (Fmt $PDF_PAGE_H_PT))

# The grid.
Emit ("{0} RG {1} w" -f (PdfRgb $GRID_RGB), (Fmt (1.0 * $PT_PER_PX)))
foreach ($seg in $gridSegments) {
    Emit ("{0} {1} m {2} {3} l S" -f (PdfX $seg[0]), (PdfY $seg[1]), (PdfX $seg[2]), (PdfY $seg[3]))
}

# The dots: `re` takes the LOWER-left corner, which is the square's bottom.
$dotSide_pt = Fmt ($dotSide_px * $PT_PER_PX)

foreach ($group in @(@($UNLIT_RGB, $unlitSquares), @($LIT_RGB, $litSquares))) {

    Emit ("{0} rg" -f (PdfRgb $group[0]))

    foreach ($sq in $group[1]) {
        Emit ("{0} {1} {2} {2} re f" -f (PdfX $sq[0]), (PdfY ($sq[1] + $dotSide_px)), $dotSide_pt)
    }
}

# The text. Every style maps to one of the base-14 faces declared in the page's
# resources below; the em size scales with the sheet, exactly as the bitmap's
# text does when the whole PNG is printed at page width.
$PDF_FONT_NAMES = @{ "Helvetica-Bold" = "/F1"; "Helvetica" = "/F2"; "Courier-Bold" = "/F3"; "Courier" = "/F4" }

foreach ($item in $textItems) {

    $style = $TEXT_STYLES[$item.Style]
    $em_px = $style.Pt * $PX_PER_POINT

    Emit "BT"
    Emit ("{0} {1} Tf" -f $PDF_FONT_NAMES[$style.Pdf], (Fmt ($em_px * $PT_PER_PX)))
    Emit ("{0} rg" -f (PdfRgb $style.Rgb))
    Emit ("{0} {1} Td" -f (PdfX $item.X), (PdfY ($item.YTop + $BASELINE_EM * $em_px)))
    Emit ("({0}) Tj" -f (Escape-PdfText $item.Text))
    Emit "ET"
}

# ---- emit the PDF: objects, xref, trailer ----------------------------------------------
#
# Hand-built like the sibling's, but simpler: one page, one vector content
# stream, four font dictionaries, nothing compressed.

$enc = [System.Text.Encoding]::ASCII
$contentBytes = $enc.GetBytes($content.ToString())

# A MemoryStream rather than a string: the file mixes text with the raw bytes
# of the content stream, and the xref needs exact byte offsets as it goes.
$pdfStream = New-Object System.IO.MemoryStream
$offsets = New-Object System.Collections.Generic.List[long]

function Write-Bytes([byte[]] $b) { $pdfStream.Write($b, 0, $b.Length) }
function Write-Text([string] $s) { Write-Bytes ($enc.GetBytes($s)) }
function Begin-Object([int] $num) { $offsets.Add($pdfStream.Position); Write-Text "$num 0 obj`n" }

Write-Text "%PDF-1.4`n"

Begin-Object 1
Write-Text "<< /Type /Catalog /Pages 2 0 R >>`nendobj`n"

Begin-Object 2
Write-Text "<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj`n"

Begin-Object 3
Write-Text ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PDF_PAGE_W_PT $PDF_PAGE_H_PT] " +
    "/Resources << /Font << /F1 5 0 R /F2 6 0 R /F3 7 0 R /F4 8 0 R >> >> /Contents 4 0 R >>`nendobj`n")

Begin-Object 4
Write-Text "<< /Length $($contentBytes.Length) >>`nstream`n"
Write-Bytes $contentBytes
Write-Text "endstream`nendobj`n"

# The four faces, in /F1-/F4 order, matching $PDF_FONT_NAMES above.
$fontObject = 5
foreach ($face in @("Helvetica-Bold", "Helvetica", "Courier-Bold", "Courier")) {
    Begin-Object $fontObject
    Write-Text "<< /Type /Font /Subtype /Type1 /BaseFont /$face >>`nendobj`n"
    $fontObject++
}

$xrefPos = $pdfStream.Position
$totalObjects = $offsets.Count + 1

Write-Text "xref`n0 $totalObjects`n0000000000 65535 f `n"
foreach ($o in $offsets) { Write-Text ("{0:D10} 00000 n `n" -f $o) }
Write-Text "trailer`n<< /Size $totalObjects /Root 1 0 R >>`nstartxref`n$xrefPos`n%%EOF`n"

# A PDF open in a viewer is locked for writing. Say so plainly rather than
# reporting success over a file that was never replaced.
try {
    [System.IO.File]::WriteAllBytes($PDF_OUT, $pdfStream.ToArray())
    Write-Output ("wrote {0}  ({1} x {2} pt, {3:N0} bytes)" -f $PDF_OUT, $PDF_PAGE_W_PT, $PDF_PAGE_H_PT, $pdfStream.Length)
}
catch [System.IO.IOException] {
    Write-Output "COULD NOT WRITE $PDF_OUT - it is open in another program. Close it and re-run."
    exit 1
}
finally {
    $pdfStream.Dispose()
}
