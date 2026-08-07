# Centreline geometry of the HP-01 cell. No stroke, no slant.
#
# This is the shape the font actually stores: a set of centrelines. Stroke width and
# shear are applied at render time and appear nowhere in these coordinates.
#
# The datum is segment a's centreline crossed with segment f's - the top-left corner
# of the centreline box - so the numbers describe the skeleton and nothing else. In
# Hp01Font.kt the same skeleton is expressed from the ink corner instead, which is
# why every coordinate there carries a STROKE/2 of 4.25 baked into it. Subtract 4.25
# from the Kotlin figures and you get these.
#
# The unit is arbitrary. It happens to be 1/100 of the *ink* cell height in the
# existing font, which is why the centreline box comes out 91.5 tall rather than a
# round number. Scale the whole set by 100/91.5 = 1.0929 if a 100-tall centreline
# box is wanted instead.

Add-Type -AssemblyName System.Drawing

$OUT = "$PSScriptRoot\cell_geometry.png"

# ---- drawing scale ----------------------------------------------------------

$SCALE = 5.0
$ORIGIN_X = 400
$ORIGIN_Y = 190
$CANVAS_W = 1220
$CANVAS_H = 1420

# ---- the skeleton ----------------------------------------------------------
#
# Datum: a's centreline is y = 0, f's centreline is x = 0.
#
# NORMALISE_TO_100 rescales so the centreline box is exactly 100 tall. Off, the
# numbers are the existing font's own units, in which the box is 91.5 tall - the
# ink box of 100 less one STROKE. On is the better frame for designing new
# geometry, since 91.5 is an artefact of a stroke width the skeleton should not
# know about.
$NORMALISE_TO_100 = $true

$K = if ($NORMALISE_TO_100) { 100.0 / 91.5 } else { 1.0 }

$Y_A = 0.0
$Y_D = 91.5 * $K       # bottom bar
$Y_G = $Y_D / 2.0      # middle bar, dead centre: b == c

$X_LEFT = 0.0
$X_RIGHT = 53.5 * $K   # right column

$HOOK_R = 7.25 * $K
$HOOK_START_X = 7.25 * $K

$Y_B_TOP = 0.0
# c runs to d's centreline, so b + c span the box exactly.
$Y_C_BOTTOM = $Y_D
$Y_F_TOP = 7.25 * $K
# e runs to where d's hook lands, mirroring f starting where a's hook lands.
$Y_E_BOTTOM = $Y_D - $HOOK_R

$DOT_AXIS_X = 26.75 * $K
$DOT_UPPER_Y = 18.75 * $K
$DOT_LOWER_Y = 73.75 * $K

$ADVANCE = 130.0 * $K
$STROKE_AT_RENDER = 8.5 * $K

# ---- canvas -----------------------------------------------------------------

function Px { param($x, $y) return @(($ORIGIN_X + $x * $SCALE), ($ORIGIN_Y + $y * $SCALE)) }

$bmp = New-Object System.Drawing.Bitmap $CANVAS_W, $CANVAS_H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$centre = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), 1.8
$centre.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
$centre.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat

$dimPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(70, 110, 170)), 1.1
$dimPen.CustomStartCap = New-Object System.Drawing.Drawing2D.AdjustableArrowCap 3.5, 4.5
$dimPen.CustomEndCap = New-Object System.Drawing.Drawing2D.AdjustableArrowCap 3.5, 4.5

$guidePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(195, 195, 195)), 0.9
$guidePen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dot

$letterFont = New-Object System.Drawing.Font "Segoe UI", 15, ([System.Drawing.FontStyle]::Bold)
$dimFont = New-Object System.Drawing.Font "Consolas", 11
$titleFont = New-Object System.Drawing.Font "Segoe UI", 15, ([System.Drawing.FontStyle]::Bold)

$black = [System.Drawing.Brushes]::Black
$dimBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(40, 80, 140))
$grey = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(90, 90, 90))
$nodeBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200, 60, 40))

# ---- helpers ----------------------------------------------------------------

function Line { param($x1, $y1, $x2, $y2, $pen)
    $a = Px $x1 $y1; $b = Px $x2 $y2
    $g.DrawLine($pen, $a[0], $a[1], $b[0], $b[1])
}

function Node { param($x, $y)
    $p = Px $x $y
    $g.FillEllipse($nodeBrush, ($p[0] - 3.5), ($p[1] - 3.5), 7, 7)
}

function DimH { param($x1, $x2, $y, $text, $dy = -18)
    $a = Px $x1 $y; $b = Px $x2 $y
    $g.DrawLine($dimPen, $a[0], $a[1], $b[0], $b[1])
    $mid = Px (($x1 + $x2) / 2) $y
    $sz = $g.MeasureString($text, $dimFont)
    $g.DrawString($text, $dimFont, $dimBrush, ($mid[0] - $sz.Width / 2), ($mid[1] + $dy))
}

function DimV { param($y1, $y2, $x, $text, $dx = 6)
    $a = Px $x $y1; $b = Px $x $y2
    $g.DrawLine($dimPen, $a[0], $a[1], $b[0], $b[1])
    $mid = Px $x (($y1 + $y2) / 2)
    $g.DrawString($text, $dimFont, $dimBrush, ($mid[0] + $dx), ($mid[1] - 8))
}

# ---- the centreline skeleton ------------------------------------------------

Line $X_RIGHT $Y_A $HOOK_START_X $Y_A $centre
$arcA = Px ($HOOK_START_X - $HOOK_R) ($Y_A)
$g.DrawArc($centre, $arcA[0], $arcA[1], ($HOOK_R * 2 * $SCALE), ($HOOK_R * 2 * $SCALE), 270, -90)

Line $X_RIGHT $Y_D $HOOK_START_X $Y_D $centre
$arcD = Px ($HOOK_START_X - $HOOK_R) ($Y_D - $HOOK_R * 2)
$g.DrawArc($centre, $arcD[0], $arcD[1], ($HOOK_R * 2 * $SCALE), ($HOOK_R * 2 * $SCALE), 90, 90)

Line $X_RIGHT $Y_G $X_LEFT $Y_G $centre
Line $X_RIGHT $Y_B_TOP $X_RIGHT $Y_G $centre
Line $X_RIGHT $Y_G $X_RIGHT $Y_C_BOTTOM $centre
Line $X_LEFT $Y_F_TOP $X_LEFT $Y_G $centre
Line $X_LEFT $Y_G $X_LEFT $Y_E_BOTTOM $centre

Node $X_RIGHT $Y_A; Node $X_RIGHT $Y_D; Node $X_RIGHT $Y_G; Node $X_LEFT $Y_G
Node $X_LEFT ($Y_A + $HOOK_R); Node $X_LEFT ($Y_D - $HOOK_R)
Node $X_RIGHT $Y_C_BOTTOM; Node $X_LEFT $Y_E_BOTTOM; Node $X_LEFT $Y_F_TOP
Node $DOT_AXIS_X $DOT_UPPER_Y; Node $DOT_AXIS_X $DOT_LOWER_Y

foreach ($d in @(@($DOT_UPPER_Y, "col"), @($DOT_LOWER_Y, "dp"))) {
    $lp = Px ($DOT_AXIS_X + 3) ($d[0] - 4)
    $g.DrawString($d[1], $dimFont, $grey, $lp[0], $lp[1])
}

# ---- segment letters --------------------------------------------------------

foreach ($l in @(@("a", 30, -6), @("b", 58, 22), @("c", 58, 70), @("d", 30, 98),
                 @("e", -5, 70), @("f", -5, 22), @("g", 42, 41))) {
    $p = Px $l[1] $l[2]
    $g.DrawString($l[0], $letterFont, $black, ($p[0] - 6), ($p[1] - 12))
}

# ---- dimensions -------------------------------------------------------------

Line (-34) $Y_G $X_LEFT $Y_G $guidePen
Line (-34) $Y_D $X_LEFT $Y_D $guidePen
Line (-22) $Y_F_TOP $X_LEFT $Y_F_TOP $guidePen

DimV $Y_A $Y_F_TOP (-14) ("{0:N2}   f top" -f $Y_F_TOP) (-104)
DimV $Y_A $Y_G (-24) ("{0:N2}  Y_G" -f $Y_G) (-96)
DimV $Y_A $Y_D (-34) ("{0:N2}  Y_D" -f $Y_D) (-96)

Line $X_RIGHT $Y_C_BOTTOM 76 $Y_C_BOTTOM $guidePen
Line $X_LEFT $Y_E_BOTTOM 86 $Y_E_BOTTOM $guidePen

DimV $Y_A $Y_E_BOTTOM 84 ("{0:N2}  e bottom" -f $Y_E_BOTTOM) 6
DimV $Y_A $Y_C_BOTTOM 74 ("{0:N2}  c bottom" -f $Y_C_BOTTOM) 6

Line $HOOK_START_X $Y_A $HOOK_START_X 112 $guidePen
Line $DOT_AXIS_X $DOT_LOWER_Y $DOT_AXIS_X 120 $guidePen
Line $X_RIGHT $Y_D $X_RIGHT 128 $guidePen

DimH $X_LEFT $HOOK_START_X 112 ("{0:N2}  hook start" -f $HOOK_START_X) 6
DimH $X_LEFT $DOT_AXIS_X 120 ("{0:N2}  dot axis" -f $DOT_AXIS_X) 6
DimH $X_LEFT $X_RIGHT 128 ("{0:N2}  X_RIGHT" -f $X_RIGHT) 6

Line $DOT_AXIS_X $DOT_UPPER_Y 96 $DOT_UPPER_Y $guidePen
Line $DOT_AXIS_X $DOT_LOWER_Y 106 $DOT_LOWER_Y $guidePen

DimV $Y_A $DOT_UPPER_Y 94 ("{0:N2}  col centre" -f $DOT_UPPER_Y) 6
DimV $Y_A $DOT_LOWER_Y 104 ("{0:N2}  dp centre" -f $DOT_LOWER_Y) 6

$hc = Px $HOOK_START_X $Y_A
$hr = Px ($HOOK_START_X - $HOOK_R) ($Y_A + $HOOK_R)
$g.DrawLine($dimPen, $hc[0], $hc[1], $hr[0], $hr[1])
$hp = Px 10 (-14)
$g.DrawString(("R {0:N2} hook" -f $HOOK_R), $dimFont, $dimBrush, $hp[0], $hp[1])

# ---- the numbers ------------------------------------------------------------

$hookCentreY = $HOOK_R
$hookEndAY = $HOOK_R
$hookCentreDY = $Y_D - $HOOK_R
$hookEndDY = $Y_D - $HOOK_R

$rows = @(
    "SEGMENT CENTRELINES          from             to",
    ("  a  horizontal        {0,6:N2},{1,7:N2}  ->  {2,6:N2},{3,7:N2}" -f $X_RIGHT, $Y_A, $HOOK_START_X, $Y_A),
    ("       arc R{0:N2}, centre {1,6:N2},{2,7:N2}, ending {3,6:N2},{4,7:N2}" -f $HOOK_R, $HOOK_START_X, $hookCentreY, $X_LEFT, $hookEndAY),
    ("  d  horizontal        {0,6:N2},{1,7:N2}  ->  {2,6:N2},{3,7:N2}" -f $X_RIGHT, $Y_D, $HOOK_START_X, $Y_D),
    ("       arc R{0:N2}, centre {1,6:N2},{2,7:N2}, ending {3,6:N2},{4,7:N2}" -f $HOOK_R, $HOOK_START_X, $hookCentreDY, $X_LEFT, $hookEndDY),
    ("  g  horizontal        {0,6:N2},{1,7:N2}  ->  {2,6:N2},{3,7:N2}" -f $X_RIGHT, $Y_G, $X_LEFT, $Y_G),
    ("  b  vertical          {0,6:N2},{1,7:N2}  ->  {2,6:N2},{3,7:N2}" -f $X_RIGHT, $Y_B_TOP, $X_RIGHT, $Y_G),
    ("  c  vertical          {0,6:N2},{1,7:N2}  ->  {2,6:N2},{3,7:N2}" -f $X_RIGHT, $Y_G, $X_RIGHT, $Y_C_BOTTOM),
    ("  f  vertical          {0,6:N2},{1,7:N2}  ->  {2,6:N2},{3,7:N2}" -f $X_LEFT, $Y_F_TOP, $X_LEFT, $Y_G),
    ("  e  vertical          {0,6:N2},{1,7:N2}  ->  {2,6:N2},{3,7:N2}" -f $X_LEFT, $Y_G, $X_LEFT, $Y_E_BOTTOM),
    "",
    ("DOT CENTRES    col     {0,6:N2},{1,7:N2}" -f $DOT_AXIS_X, $DOT_UPPER_Y),
    ("               dp      {0,6:N2},{1,7:N2}" -f $DOT_AXIS_X, $DOT_LOWER_Y),
    ("               both on the cell axis, x {0:N2}" -f $DOT_AXIS_X),
    ("               radius = STROKE = {0:N2}, so diameter {1:N2}" -f $STROKE_AT_RENDER, (2 * $STROKE_AT_RENDER)),
    "",
    ("CENTRELINE BOX         {0:N2} wide  x  {1:N2} tall    aspect {2:N3}" -f $X_RIGHT, $Y_D, ($X_RIGHT / $Y_D)),
    ("ADVANCE                {0:N2}   origin to origin" -f $ADVANCE),
    "",
    "RELATIONSHIPS",
    ("  hook radius = hook start x = {0:N2}, so the arc is" -f $HOOK_R),
    "      tangent to both bar and column, filling the corner",
    ("  f top = {0:N2} = where a's hook lands.  Exact." -f $Y_F_TOP),
    ("  b = c = {0:N2}   g is dead centre" -f ($Y_D / 2)),
    ("  b - f = c - e = {0:N2}  ( = hook radius )" -f $HOOK_R),
    "",
    "DEPARTURES FROM THE RECONSTRUCTION",
    "  g was 50.55, putting b slightly longer than c. The",
    "  QDSP-6064 datasheet measures 46.73 the other way. The",
    "  two disagree in opposite directions and the difference",
    "  is barely visible at size, so: exactly half.",
    "  c and e also now terminate exactly; they were 0.82",
    "  short and 0.54 long, both hidden under the stroke.",
    "",
    "NOT PART OF THE GEOMETRY - applied when rendering",
    ("  STROKE  {0:N2}   ink is centreline +/- {1:N2}" -f $STROKE_AT_RENDER, ($STROKE_AT_RENDER / 2)),
    "  SLANT    7.50 deg"
)

$ty = 890
foreach ($r in $rows) {
    $g.DrawString($r, $dimFont, $grey, 22, $ty)
    $ty += 17
}

$g.DrawString("HP-01 cell - centreline skeleton", $titleFont, $black, 22, 22)
$g.DrawString("No stroke width, no slant. Datum: a's centreline x f's centreline.", $dimFont, $grey, 22, 48)
$g.DrawString(("Centreline box normalised to 100 tall.  Existing-font units x {0:N4}." -f $K), $dimFont, $grey, 22, 68)
$g.DrawString("Set NORMALISE_TO_100 = $false for the font's own units (box 91.50 tall).", $dimFont, $grey, 22, 85)
$g.DrawString("g dead centre, c and e terminate exactly. Hp01Font.kt matches.", $dimFont, $dimBrush, 22, 108)
$g.Dispose()
$bmp.Save($OUT, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "wrote $OUT"











