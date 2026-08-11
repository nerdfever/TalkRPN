# Candidate slants side by side, because a degree and a half is not something
# anyone can judge from a number.
#
# The two ends of the argument:
#
#   5.0   HP's own 5082-7400 datasheet, marked on its magnified character font
#         figure. That is the exact part in the HP-55 Dave photographed.
#   7.5   what the font used first. Never measured - eyeballed from the HP-01
#         reconstruction, which itself read 7.8 from photographs.
#
# Different parts, so both can be right about their own hardware. These are the
# steps between them.

$OUT_SLANT = "$PSScriptRoot\talkrpn_slant_comparison.png"

. "$PSScriptRoot/talkrpn_render.ps1"

# ---- what to show -------------------------------------------------------------

$CANDIDATES = @(5.5, 6.0, 6.5, 7.0)

# Digits show the lean most clearly; a word shows what it does to reading.
$SAMPLES = @("0123456789", "QUICK BROWN FOX")

# One big glyph per candidate, with a true vertical through the cell's centre
# axis, so the lean has something definitely upright to be judged against.
$BIG_GLYPH = "8"

$SCALE = 30.0                  # px per cell width, for the text rows
$BIG_SCALE = 92.0              # px per cell width, for the single glyphs
$MARGIN = 46.0
$LABEL_W = 86.0
$ROW_GAP = 26.0
$SECTION_GAP = 60.0
$TITLE_H = 92.0

$labelFont = New-Object System.Drawing.Font "Consolas", 13
$noteFont = New-Object System.Drawing.Font "Consolas", 11
$titleFont = New-Object System.Drawing.Font "Consolas", 15

$LIT = [System.Drawing.Color]::FromArgb(255, 0, 0)
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(215, 215, 215))
$noteBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(135, 135, 135))
$plumbPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(70, 140, 155)), 1.4

# ---- canvas -------------------------------------------------------------------

$rowH = $TOTAL_HEIGHT * $SCALE + $ROW_GAP
$bigH = $TOTAL_HEIGHT * $BIG_SCALE

# The widest slant sets how much room each big glyph needs.
$widestShear = [Math]::Tan(($CANDIDATES | Measure-Object -Maximum).Maximum * [Math]::PI / 180.0)
$bigCellW = ($CELL_WIDTH + $widestShear * $TOTAL_HEIGHT + 0.9) * $BIG_SCALE

$W = [int]([Math]::Max(
    $MARGIN * 2 + $LABEL_W + 16 * $PITCH * $SCALE,
    $MARGIN * 2 + $CANDIDATES.Count * $bigCellW))

$H = [int]($TITLE_H + $SAMPLES.Count * $CANDIDATES.Count * $rowH + $SECTION_GAP + $bigH + 80)

$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::Black)

# ---- draw ----------------------------------------------------------------------

$g.DrawString("Slant, between HP's datasheet 5.0 and the 7.5 first used. Same stroke, same glyphs.",
    $titleFont, $labelBrush, $MARGIN, 26)

$y = $TITLE_H

foreach ($sample in $SAMPLES) {

    foreach ($deg in $CANDIDATES) {

        $slantTan = [Math]::Tan($deg * [Math]::PI / 180.0)

        Draw-TalkRpnText $g $sample ($MARGIN + $LABEL_W) $y $SCALE $LIT $null $slantTan

        $g.DrawString(("{0:F1} deg" -f $deg), $labelFont, $labelBrush,
            $MARGIN, ($y + $CELL_HEIGHT * $SCALE * 0.35))

        $y += $rowH
    }

    $y += 14
}

$y += $SECTION_GAP - 14

$g.DrawString("the same 8, with a true vertical through each cell's centre axis",
    $noteFont, $noteBrush, $MARGIN, ($y - 28))

$x = $MARGIN

foreach ($deg in $CANDIDATES) {

    $slantTan = [Math]::Tan($deg * [Math]::PI / 180.0)
    $shearOffset = $slantTan * $TOTAL_HEIGHT

    Draw-TalkRpnCell $g $GLYPH_MAP[[char]$BIG_GLYPH] $x $y $BIG_SCALE $LIT $slantTan

    # The centre axis where it crosses the baseline, drawn straight up.
    $px = $x + (0.5 - $slantTan * $CELL_HEIGHT + $shearOffset) * $BIG_SCALE
    $g.DrawLine($plumbPen, [float]$px, [float]($y - 12), [float]$px, [float]($y + $bigH + 12))

    $g.DrawString(("{0:F1} deg" -f $deg), $labelFont, $labelBrush, $x, ($y + $bigH + 20))

    $x += $bigCellW
}

$g.Dispose()

try {
    $bmp.Save($OUT_SLANT, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OUT_SLANT  ($W x $H)"
}
catch {
    Write-Output "COULD NOT WRITE $OUT_SLANT - it is probably open in another program"
}

$bmp.Dispose()
