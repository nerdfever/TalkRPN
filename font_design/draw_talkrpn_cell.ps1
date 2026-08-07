# Render the TalkRpnFont cell exactly as TalkRpnFont.kt defines it, so the
# geometry can be checked without a round trip through the watch.
#
# Coordinates here must stay identical to the Kotlin. This is a mirror, not a
# second source of truth - if they disagree, the Kotlin wins.

param(
    # Stroke to render at, as a fraction of the font's nominal 9.29. All 26 bars
    # lit at full width nearly closes the dark gaps, so a thinner setting is the
    # only way to see the geometry underneath.
    [double] $StrokeFraction = 1.0,

    [string] $OutputName = "talkrpn_cell.png"
)

$OUT = "$PSScriptRoot\$OutputName"

# ---- geometry, in cell units (must match TalkRpnFont.kt) -------------------

$CELL_HEIGHT  = 100.0
$CELL_WIDTH   = 58.47
$TOTAL_HEIGHT = 144.0
$STROKE       = 9.29
$SLANT_DEG    = 7.5
$HOOK_R       = 7.92

$X_LEFT  = 0.0
$X_MID   = $CELL_WIDTH / 2.0
$X_RIGHT = $CELL_WIDTH

$Y_TOP  = 0.0
$Y_MID  = $CELL_HEIGHT / 2.0
$Y_BASE = $CELL_HEIGHT
$Y_DESC = $TOTAL_HEIGHT

$Y_F_TOP      = $HOOK_R
$Y_E_BOTTOM   = $Y_BASE - $HOOK_R
$X_HOOK_START = $HOOK_R

$X_N_LEFT  = 3.74
$X_O_RIGHT = 54.72

$COL1_Y = 20.49
$COL2_Y = 80.60
$DP_X   = 86.64
$DP_Y   = 119.08
$COMMA_TAIL_DROP = 20.76
$COMMA_TAIL_LEFT = 7.65

# ---- rendering ------------------------------------------------------------

$SCALE  = 7.0
$MARGIN = 90.0

$SHEAR = [Math]::Tan($SLANT_DEG * [Math]::PI / 180.0)
$SHEAR_OFFSET = $SHEAR * $TOTAL_HEIGHT

# Width has to hold the decimal point, which sits well outside the cell.
$CANVAS_W = [int](($DP_X + $SHEAR_OFFSET + $STROKE) * $SCALE + 2 * $MARGIN)
$CANVAS_H = [int](($TOTAL_HEIGHT + $STROKE + $COMMA_TAIL_DROP) * $SCALE + 2 * $MARGIN)

Add-Type -AssemblyName System.Drawing

$bmp = New-Object System.Drawing.Bitmap $CANVAS_W, $CANVAS_H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Black)

$STROKE_RENDER = $STROKE * $StrokeFraction

$lit = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(232, 24, 16)), ($STROKE_RENDER * $SCALE)
$lit.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$lit.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$litBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(232, 24, 16))

$guide = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(60, 60, 70)), 1.0
$labelFont = New-Object System.Drawing.Font "Consolas", 13
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(150, 190, 150))

# Cell coordinate -> canvas point, with slant applied.
function P($x, $y) {
    $sx = $x - $SHEAR * $y + $SHEAR_OFFSET
    New-Object System.Drawing.PointF (($sx * $SCALE + $MARGIN), ($y * $SCALE + $MARGIN))
}

function Seg($name, $x1, $y1, $x2, $y2) {
    $a = P $x1 $y1
    $b = P $x2 $y2
    $g.DrawLine($lit, $a, $b)

    # Label just off the segment's midpoint.
    $mx = ($a.X + $b.X) / 2.0 + 10
    $my = ($a.Y + $b.Y) / 2.0 - 8
    $g.DrawString($name, $labelFont, $labelBrush, $mx, $my)
}

# A circular arc, sheared. Walked as a polyline because GDI+ cannot shear an arc
# directly without also shearing the pen, which would distort the stroke.
function Arc($name, $cx, $cy, $r, $fromDeg, $toDeg) {

    $steps = 24
    $pts = @()

    for ($i = 0; $i -le $steps; $i++) {
        $t = $fromDeg + ($toDeg - $fromDeg) * $i / $steps
        $rad = $t * [Math]::PI / 180.0
        $pts += P ($cx + $r * [Math]::Cos($rad)) ($cy + $r * [Math]::Sin($rad))
    }

    $g.DrawLines($lit, [System.Drawing.PointF[]]$pts)

    $mid = $pts[[int]($steps / 2)]
    $g.DrawString($name, $labelFont, $labelBrush, ($mid.X - 34), ($mid.Y - 10))
}

function Dot($name, $x, $y) {
    $c = P $x $y
    $r = $STROKE_RENDER * $SCALE
    $g.FillEllipse($litBrush, ($c.X - $r), ($c.Y - $r), (2 * $r), (2 * $r))
    $g.DrawString($name, $labelFont, $labelBrush, ($c.X + $r + 4), ($c.Y - 8))
}

# ---- the cell -------------------------------------------------------------

# A3/A4 and D3/D4 are alternative corners, never lit together. Both are drawn
# here because this is a geometry check, not a glyph.
Seg "A1" $X_MID $Y_TOP $X_HOOK_START $Y_TOP
Seg "A2" $X_MID $Y_TOP $X_RIGHT $Y_TOP
Seg "A4" $X_HOOK_START $Y_TOP $X_LEFT $Y_TOP
Arc "A3" $HOOK_R $HOOK_R $HOOK_R 270 180

Seg "B" $X_RIGHT $Y_TOP $X_RIGHT $Y_MID
Seg "C" $X_RIGHT $Y_MID $X_RIGHT $Y_BASE

Seg "D1" $X_MID $Y_BASE $X_HOOK_START $Y_BASE
Seg "D2" $X_MID $Y_BASE $X_RIGHT $Y_BASE
Seg "D4" $X_HOOK_START $Y_BASE $X_LEFT $Y_BASE
Arc "D3" $HOOK_R $Y_E_BOTTOM $HOOK_R 90 180

Seg "F1" $X_LEFT $Y_F_TOP $X_LEFT $Y_MID
Seg "F2" $X_LEFT $Y_TOP $X_LEFT $Y_F_TOP
Seg "E1" $X_LEFT $Y_MID $X_LEFT $Y_E_BOTTOM
Seg "E2" $X_LEFT $Y_E_BOTTOM $X_LEFT $Y_BASE

Seg "G1" $X_LEFT $Y_MID $X_MID $Y_MID
Seg "G2" $X_MID $Y_MID $X_RIGHT $Y_MID

Seg "H" $X_LEFT $Y_F_TOP $X_MID $Y_MID
Seg "I" $X_RIGHT $Y_TOP $X_MID $Y_MID
Seg "K" $X_MID $Y_MID $X_RIGHT $Y_BASE
Seg "L" $X_MID $Y_MID $X_LEFT $Y_E_BOTTOM
Seg "J" $X_LEFT $Y_MID $X_MID $Y_BASE

Seg "P" $X_MID $Y_TOP $X_MID $Y_MID
Seg "Q" $X_MID $Y_MID $X_MID $Y_BASE

Seg "M" $X_MID $Y_BASE $X_MID $Y_DESC
Seg "N" $X_N_LEFT $Y_DESC $X_MID $Y_DESC
Seg "O" $X_MID $Y_DESC $X_O_RIGHT $Y_DESC

Dot "COL1" $X_MID $COL1_Y
Dot "COL2" $X_MID $COL2_Y
Dot "DP" $DP_X $DP_Y

Seg "COMMA" $DP_X $DP_Y ($DP_X - $COMMA_TAIL_LEFT) ($DP_Y + $COMMA_TAIL_DROP)

$g.Dispose()
$bmp.Save($OUT, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "wrote $OUT  ($CANVAS_W x $CANVAS_H)"

