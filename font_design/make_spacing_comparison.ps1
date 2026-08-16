# Three spacing policies side by side, on text chosen to hurt:
#
#   A  fixed pitch, glyphs where the segment table puts them (current)
#   B  fixed pitch, each glyph's ink CENTRED in its cell
#   C  proportional: advance = half this glyph's width + half the next's + a gap
#
# Width means the lit ink's centreline extent, right minus left, per glyph.
# The decimal point and comma are excluded from a glyph's extent - they live in
# the gap between cells by design and merge into the preceding cell either way.

$OUT_SPACING = "$PSScriptRoot\talkrpn_spacing_comparison.png"

. "$PSScriptRoot/talkrpn_render.ps1"

# ---- knobs --------------------------------------------------------------------

# The fixed rows run at the pitch Dave prefers for caps.
$FIXED_PITCH = 1.7

# The proportional gap, as a fraction of the cell width. Chosen so that two
# FULL-width glyphs side by side land at the fixed pitch: 1/2 + 1/2 + 0.7 = 1.7,
# which makes rows directly comparable - only narrow glyphs move.
$GAP = 0.7

# A space's advance: half a cell plus the gap in C, half a pitch in A and B.
$SPACE_CELLS = 0.5

$SAMPLES = @(
    "ill.i 11:11 (jilt)",
    "Hello World 1,234.56 [OK]",
    "THE QUICK BROWN FOX 42.9565"
)

$SCALE = 26.0
$MARGIN = 46.0
$LABEL_W = 210.0
$ROW_GAP = 26.0
$SECTION_GAP = 40.0

# ---- glyph extents -------------------------------------------------------------

# Lit centreline extent of a mask, ignoring DP and COMMA. Returns min,max or
# $null for no ink (space).
function Get-GlyphExtents($names) {

    $min = [double]::PositiveInfinity
    $max = [double]::NegativeInfinity

    foreach ($n in $names) {

        # The gap-dwellers live outside the glyph, and a DESCENDER tucks under
        # its neighbour rather than pushing it away - neither counts as width.
        if (("DP", "COMMA", "M", "N", "O") -contains $n) { continue }

        $xs = @()

        if ($SEG_LINES.Contains($n)) {
            $sl = $SEG_LINES[$n]
            $xs = @($sl[0], $sl[2])
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $a = $SEG_ARCS[$n]
            foreach ($pt in (New-ArcPoints $a[0] $a[1] $a[2] $a[3] $a[4])) { $xs += $pt[0] }
        }
        elseif ($SEG_POLYS.Contains($n)) {
            foreach ($pt in $SEG_POLYS[$n]) { $xs += $pt[0] }
        }
        elseif ($SEG_DOTS.Contains($n)) {
            $xs = @($SEG_DOTS[$n][0])
        }

        foreach ($x in $xs) {
            if ($x -lt $min) { $min = $x }
            if ($x -gt $max) { $max = $x }
        }
    }

    if ($min -gt $max) { return $null }
    return @($min, $max)
}

# The text as (mask, extents) cells, with '.' and ',' merged into their
# predecessors, and spaces as $null cells.
function Get-Cells($text) {

    $cells = @()

    foreach ($ch in $text.ToCharArray()) {

        if ($ch -eq ' ') { $cells += , @($null, $null); continue }

        if ((($ch -eq '.') -or ($ch -eq ',')) -and ($cells.Count -gt 0) -and ($null -ne $cells[-1][0])) {
            $dotSeg = "DP"; if ($ch -eq ',') { $dotSeg = "COMMA" }
            $cells[-1][0] = $cells[-1][0] + @($dotSeg)
            continue
        }

        if (-not $GLYPH_MAP.ContainsKey($ch)) { $cells += , @($null, $null); continue }

        $names = $GLYPH_MAP[$ch]
        $cells += , @($names, (Get-GlyphExtents $names))
    }

    return $cells
}

# ---- render --------------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$labelFont = New-Object System.Drawing.Font "Consolas", 12
$titleFont = New-Object System.Drawing.Font "Consolas", 14
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200, 200, 200))
$LIT = [System.Drawing.Color]::FromArgb(255, 0, 0)

$rowH = $TOTAL_HEIGHT * $SCALE + $ROW_GAP

# Generous canvas; measured content never exceeds the fixed-pitch row.
$maxLen = ($SAMPLES | ForEach-Object { $_.Length } | Measure-Object -Maximum).Maximum
$W = [int]($MARGIN * 2 + $LABEL_W + $maxLen * $FIXED_PITCH * $SCALE)
$H = [int](70 + $SAMPLES.Count * (3 * $rowH + $SECTION_GAP) + $MARGIN)

$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::Black)

$g.DrawString(("A fixed pitch {0}, as authored   B same pitch, ink centred in cell   C proportional, gap {1}" -f $FIXED_PITCH, $GAP),
    $titleFont, $labelBrush, $MARGIN, 22)

$y = 64.0

foreach ($sample in $SAMPLES) {

    $cells = Get-Cells $sample

    # ---- row A: as today --------------------------------------------------
    $g.DrawString("A", $labelFont, $labelBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE * 0.4))
    $x = $MARGIN + $LABEL_W
    foreach ($cell in $cells) {
        if ($null -ne $cell[0]) { Draw-TalkRpnCell $g $cell[0] $x $y $SCALE $LIT }
        $x += $FIXED_PITCH * $SCALE * $(if ($null -eq $cell[0]) { $SPACE_CELLS } else { 1.0 })
    }
    $y += $rowH

    # ---- row B: centred in cell --------------------------------------------
    $g.DrawString("B", $labelFont, $labelBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE * 0.4))
    $x = $MARGIN + $LABEL_W
    foreach ($cell in $cells) {
        if ($null -ne $cell[0]) {
            $shift = 0.0
            if ($null -ne $cell[1]) { $shift = 0.5 - ($cell[1][0] + $cell[1][1]) / 2.0 }
            Draw-TalkRpnCell $g $cell[0] ($x + $shift * $SCALE) $y $SCALE $LIT
        }
        $x += $FIXED_PITCH * $SCALE * $(if ($null -eq $cell[0]) { $SPACE_CELLS } else { 1.0 })
    }
    $y += $rowH

    # ---- row C: proportional ------------------------------------------------
    #
    # A pen walks the baseline; each glyph's ink-centre lands ON the pen, and
    # the pen advances by half this width, the gap, and half the next width.
    $g.DrawString("C", $labelFont, $labelBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE * 0.4))

    # Pen starts so the first glyph's left ink edge aligns with the other rows.
    $pen = $MARGIN + $LABEL_W + 0.5 * $SCALE
    $prevHalf = $null

    foreach ($cell in $cells) {

        if ($null -eq $cell[0]) {
            # A space: flush the pending advance, then the space itself.
            if ($null -ne $prevHalf) { $pen += ($prevHalf + $GAP) * $SCALE; $prevHalf = $null }
            $pen += $SPACE_CELLS * $SCALE
            continue
        }

        # NOT $w - PowerShell variable names are case-insensitive, so $w IS the
        # canvas width $W. It silently overwrote it, and the only symptom was the
        # closing message reporting a 1-pixel-wide image for a correct file.
        # Fourth time this project has been caught by that rule.
        $glyphW = 0.0
        $mid = 0.5
        if ($null -ne $cell[1]) {
            $glyphW = $cell[1][1] - $cell[1][0]
            $mid = ($cell[1][0] + $cell[1][1]) / 2.0
        }

        if ($null -ne $prevHalf) { $pen += ($prevHalf + $GAP + $glyphW / 2.0) * $SCALE }

        # Place so the ink's centre sits on the pen.
        Draw-TalkRpnCell $g $cell[0] ($pen - $mid * $SCALE) $y $SCALE $LIT

        $prevHalf = $glyphW / 2.0
    }

    $y += $rowH + $SECTION_GAP
}

$g.Dispose()

try {
    $bmp.Save($OUT_SPACING, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OUT_SPACING  ($W x $H)"
}
catch {
    Write-Output "COULD NOT WRITE $OUT_SPACING - it is probably open in another program"
}

$bmp.Dispose()
