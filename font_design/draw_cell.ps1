# Dimensioned drawing of the current HP-01 cell geometry, slant removed.
#
# Every number here is read from Hp01Font.kt in cell units, where the cell height
# is 100. Drawn unsheared: the shear is an affine transform applied at render time
# and does not change any of these figures.

Add-Type -AssemblyName System.Drawing

# ---- tweakables -------------------------------------------------------------

$OUT = "$PSScriptRoot\cell_geometry.png"

# Device pixels per cell unit.
$SCALE = 4.2

# Where the cell's top-left ink corner sits on the canvas.
$ORIGIN_X = 270
$ORIGIN_Y = 90

$CANVAS_W = 760
$CANVAS_H = 640

# ---- geometry, in cell units, from Hp01Font.kt -----------------------------

$CELL_HEIGHT = 100.0
$CELL_WIDTH = 62.0
$STROKE = 8.5
$DOT_RADIUS = 8.5
$HOOK_R = 7.25

$Y_A = 4.25          # centreline of the top bar
$Y_G = 50.5          # centreline of the middle bar
$Y_D = 95.75         # centreline of the bottom bar

$X_LEFT = 4.25       # centreline of the left column
$X_RIGHT = 57.75     # centreline of the right column

$HOOK_START_X = 11.5 # where each horizontal bar's straight run ends

$Y_B_TOP = 4.25
$Y_C_BOTTOM = 95.0
$Y_F_TOP = 11.5
$Y_E_BOTTOM = 89.0

$DOT_AXIS_X = 31.0
$DOT_UPPER_Y = 23.0
$DOT_LOWER_Y = 78.0

# ---- helpers ----------------------------------------------------------------

function Px { param($x, $y) return @(($ORIGIN_X + $x * $SCALE), ($ORIGIN_Y + $y * $SCALE)) }

$bmp = New-Object System.Drawing.Bitmap $CANVAS_W, $CANVAS_H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$ink = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), ($STROKE * $SCALE)
$ink.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$ink.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

$butt = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), ($STROKE * $SCALE)
$butt.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
$butt.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat

$dimPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(90, 90, 90)), 1.2
$dimPen.CustomStartCap = New-Object System.Drawing.Drawing2D.AdjustableArrowCap 4, 5
$dimPen.CustomEndCap = New-Object System.Drawing.Drawing2D.AdjustableArrowCap 4, 5

$thinPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(160, 160, 160)), 1.0
$thinPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash

$labelFont = New-Object System.Drawing.Font "Segoe UI", 17, ([System.Drawing.FontStyle]::Bold)
$dimFont = New-Object System.Drawing.Font "Segoe UI", 12
$black = [System.Drawing.Brushes]::Black
$grey = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(70, 70, 70))

# ---- the seven segments -----------------------------------------------------

# a: straight run, then a quarter turn down into the left column.
$pathA = New-Object System.Drawing.Drawing2D.GraphicsPath
$p1 = Px $X_RIGHT $Y_A; $p2 = Px $HOOK_START_X $Y_A
$pathA.AddLine($p1[0], $p1[1], $p2[0], $p2[1])
$arcA = Px ($HOOK_START_X - $HOOK_R) ($Y_A)
$pathA.AddArc($arcA[0], $arcA[1], ($HOOK_R * 2 * $SCALE), ($HOOK_R * 2 * $SCALE), 270, -90)
$g.DrawPath($ink, $pathA)

# d: the same turn, upward.
$pathD = New-Object System.Drawing.Drawing2D.GraphicsPath
$p1 = Px $X_RIGHT $Y_D; $p2 = Px $HOOK_START_X $Y_D
$pathD.AddLine($p1[0], $p1[1], $p2[0], $p2[1])
$arcD = Px ($HOOK_START_X - $HOOK_R) ($Y_D - $HOOK_R * 2)
$pathD.AddArc($arcD[0], $arcD[1], ($HOOK_R * 2 * $SCALE), ($HOOK_R * 2 * $SCALE), 90, 90)
$g.DrawPath($ink, $pathD)

# g: plain bar, round caps at both ends.
$p1 = Px $X_RIGHT $Y_G; $p2 = Px $X_LEFT $Y_G
$g.DrawLine($ink, $p1[0], $p1[1], $p2[0], $p2[1])

# The four verticals, butt-capped so they meet at the midline without a waist.
$p1 = Px $X_RIGHT $Y_B_TOP;    $p2 = Px $X_RIGHT $Y_G;        $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])
$p1 = Px $X_RIGHT $Y_G;        $p2 = Px $X_RIGHT $Y_C_BOTTOM; $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])
$p1 = Px $X_LEFT $Y_F_TOP;     $p2 = Px $X_LEFT $Y_G;         $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])
$p1 = Px $X_LEFT $Y_G;         $p2 = Px $X_LEFT $Y_E_BOTTOM;  $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])

# The two dots.
foreach ($d in @(@($DOT_UPPER_Y, "col"), @($DOT_LOWER_Y, "dp"))) {
    $c = Px ($DOT_AXIS_X - $DOT_RADIUS) ($d[0] - $DOT_RADIUS)
    $g.FillEllipse($black, $c[0], $c[1], ($DOT_RADIUS * 2 * $SCALE), ($DOT_RADIUS * 2 * $SCALE))
    $lp = Px ($DOT_AXIS_X + $DOT_RADIUS + 3) ($d[0] - 6)
    $g.DrawString($d[1], $dimFont, $grey, $lp[0], $lp[1])
}

# ---- segment letters --------------------------------------------------------

$letters = @(
    @("a", 15, 12), @("b", 47, 24), @("c", 47, 70), @("d", 15, 82),
    @("e", 12, 70), @("f", 12, 24), @("g", 45, 40)
)
foreach ($l in $letters) {
    $p = Px $l[1] $l[2]
    $g.DrawString($l[0], $labelFont, $black, $p[0], $p[1])
}

# ---- dimensions -------------------------------------------------------------

function DimH {
    param($x1, $x2, $y, $text, $labelDy = -22)
    $a = Px $x1 $y; $b = Px $x2 $y
    $g.DrawLine($dimPen, $a[0], $a[1], $b[0], $b[1])
    $mid = Px (($x1 + $x2) / 2) $y
    $sz = $g.MeasureString($text, $dimFont)
    $g.DrawString($text, $dimFont, $grey, ($mid[0] - $sz.Width / 2), ($mid[1] + $labelDy))
}

function DimV {
    param($y1, $y2, $x, $text, $labelDx = 8)
    $a = Px $x $y1; $b = Px $x $y2
    $g.DrawLine($dimPen, $a[0], $a[1], $b[0], $b[1])
    $mid = Px $x (($y1 + $y2) / 2)
    $g.DrawString($text, $dimFont, $grey, ($mid[0] + $labelDx), ($mid[1] - 9))
}

function Guide {
    param($x1, $y1, $x2, $y2)
    $a = Px $x1 $y1; $b = Px $x2 $y2
    $g.DrawLine($thinPen, $a[0], $a[1], $b[0], $b[1])
}

# Overall ink box.
Guide (-4) 0 $CELL_WIDTH 0
Guide (-4) $CELL_HEIGHT $CELL_WIDTH $CELL_HEIGHT
Guide 0 (-6) 0 $CELL_HEIGHT
Guide $CELL_WIDTH (-6) $CELL_WIDTH ($CELL_HEIGHT + 4)

DimH 0 $CELL_WIDTH (-14) "62.0  cell width"
DimV 0 $CELL_HEIGHT ($CELL_WIDTH + 28) "100.0  cell height"

# Centrelines that define the frame.
Guide 0 $Y_G 72 $Y_G
DimV 0 $Y_G (-14) "50.5  g centreline" (-70)
DimV $Y_G $CELL_HEIGHT (-14) "49.5" (-34)

# Stroke, called out on segment g.
$sa = Px 68 ($Y_G - $STROKE / 2); $sb = Px 68 ($Y_G + $STROKE / 2)
$g.DrawLine($dimPen, $sa[0], $sa[1], $sb[0], $sb[1])
$sp = Px 64 ($Y_G - 30)
$g.DrawString("8.5  stroke", $dimFont, $grey, $sp[0], $sp[1])

# Hook radius.
$hc = Px $HOOK_START_X $Y_A
$hr = Px ($HOOK_START_X - $HOOK_R) ($Y_A + $HOOK_R)
$g.DrawLine($dimPen, $hc[0], $hc[1], $hr[0], $hr[1])
$hp = Px 16 (-13)
$g.DrawString("R 7.25  hook", $dimFont, $grey, $hp[0], $hp[1])

# Dot heights, dimensioned outside on the left so they clear the ink.
Guide (-30) $DOT_UPPER_Y $DOT_AXIS_X $DOT_UPPER_Y
Guide (-30) $DOT_LOWER_Y $DOT_AXIS_X $DOT_LOWER_Y
DimV 0 $DOT_UPPER_Y (-26) "23.0  col" (-78)
DimV 0 $DOT_LOWER_Y (-64) "78.0  dp" (-72)
Guide $DOT_AXIS_X 0 $DOT_AXIS_X ($CELL_HEIGHT + 8)
DimH 0 $DOT_AXIS_X ($CELL_HEIGHT + 9) "31.0  dot axis" 4

# Advance, drawn past the right edge.
Guide ($CELL_WIDTH) ($CELL_HEIGHT + 22) 130 ($CELL_HEIGHT + 22)
DimH 0 130 ($CELL_HEIGHT + 22) "130.0  advance to next cell origin" 6

# ---- title ------------------------------------------------------------------

$titleFont = New-Object System.Drawing.Font "Segoe UI", 15, ([System.Drawing.FontStyle]::Bold)
$g.DrawString("HP-01 cell, current geometry - unsheared", $titleFont, $black, 24, 20)
$g.DrawString("unit: cell units, cell height = 100.  Slant 7.5 deg applied at render.", $dimFont, $grey, 24, 46)

$g.Dispose()
$bmp.Save($OUT, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "wrote $OUT"



