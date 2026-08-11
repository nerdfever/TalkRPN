# A few glyphs drawn large, to judge how the segment ENDS are cut.
#
# The question this exists to answer: a stroked path cuts every end square to its
# own direction, so a diagonal gets an end cut at 45 degrees while the bar beside
# it gets one cut flat. A real display has neither - every segment is a die on a
# rectangular grid, so the ends are axis-aligned whatever direction the segment
# runs.
#
# Glyphs chosen to put the two kinds of end next to each other.

$OUT_DIAG = "$PSScriptRoot\talkrpn_pen_diagnostic.png"

. "$PSScriptRoot/talkrpn_render.ps1"

# X and Y together are the case Dave pointed at: X is all diagonals, Y puts a
# diagonal directly above a vertical. # and $ carry segment M under the baseline.
$SHOW = @('&', '?', 'V', 'W', 'a', 'e', 'h', 'o', 'q', 'v', 'w', 'z')

$SCALE = 130.0
$MARGIN = 40.0
$GAP = 0.55
$LABEL_H = 34.0

$labelFont = New-Object System.Drawing.Font "Consolas", 15
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200, 200, 200))
$LIT = [System.Drawing.Color]::FromArgb(255, 0, 0)

$cellW = ($SHEARED_WIDTH + $GAP) * $SCALE
$W = [int]($MARGIN * 2 + $SHOW.Count * $cellW)
$H = [int]($MARGIN * 2 + $TOTAL_HEIGHT * $SCALE + $LABEL_H)

$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::Black)

$x = $MARGIN

foreach ($ch in $SHOW) {

    Draw-TalkRpnCell $g $GLYPH_MAP[[char]$ch] $x $MARGIN $SCALE $LIT
    $g.DrawString([string]$ch, $labelFont, $labelBrush, ($x + 20), ($MARGIN + $TOTAL_HEIGHT * $SCALE + 8))

    $x += $cellW
}

$g.Dispose()

try {
    $bmp.Save($OUT_DIAG, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OUT_DIAG  ($W x $H)"
}
catch {
    Write-Output "COULD NOT WRITE $OUT_DIAG - it is probably open in another program"
}

$bmp.Dispose()
