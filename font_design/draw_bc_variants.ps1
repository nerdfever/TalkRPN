# Three candidates for where the middle bar sits.
#
# Only Y_G changes between them. Everything else - hooks, width, terminations,
# stroke - is held identical, so the comparison isolates the one variable.
#
# Units: centreline box is 100 tall, as in cell_geometry.png. No slant; it is the
# same shear on all three and would only add noise to the comparison.

Add-Type -AssemblyName System.Drawing

$OUT = "$PSScriptRoot\bc_variants.png"

# ---- the three candidates ---------------------------------------------------

$VARIANTS = @(
    @{ Name = "current";   Detail = "HP-01 reconstruction"; G = 50.55 },
    @{ Name = "equal";     Detail = "b = c";                G = 50.00 },
    @{ Name = "datasheet"; Detail = "QDSP-6064, measured";  G = 46.73 }
)

# ---- geometry shared by all three ------------------------------------------

$Y_A = 0.0
$Y_D = 100.0
$X_LEFT = 0.0
$X_RIGHT = 58.47

$HOOK_R = 7.92
$HOOK_START_X = 7.92

$Y_F_TOP = 7.92          # where a's hook lands
$Y_E_BOTTOM = 92.08      # where d's hook lands
$Y_C_BOTTOM = 100.0      # c runs to d, so b + c = 100

$DOT_AXIS_X = 29.23
$DOT_LOWER_Y = 80.60
$DOT_RADIUS = 9.29 / 2 * 2      # dot diameter equals twice the stroke in this font

# Stroke for the rendered row. Not part of the geometry - see cell_geometry.png.
$STROKE_CURRENT = 9.29
$STROKE_MEASURED = 4.66  # what the QDSP-6064 figure actually measures

# ---- layout -----------------------------------------------------------------

$SCALE = 2.55
$COL_X = @(150, 480, 810)
$ROW_Y = @(150, 560, 900)
$CANVAS_W = 1010
$CANVAS_H = 1290

$bmp = New-Object System.Drawing.Bitmap $CANVAS_W, $CANVAS_H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$hair = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), 1.6
$guide = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(210, 120, 120)), 1.0
$guide.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash

$titleFont = New-Object System.Drawing.Font "Segoe UI", 15, ([System.Drawing.FontStyle]::Bold)
$headFont = New-Object System.Drawing.Font "Segoe UI", 12, ([System.Drawing.FontStyle]::Bold)
$noteFont = New-Object System.Drawing.Font "Consolas", 11
$black = [System.Drawing.Brushes]::Black
$grey = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(90, 90, 90))
$redBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200, 60, 40))

# ---- one cell ---------------------------------------------------------------

function Draw-Cell {
    param($ox, $oy, $gY, $pen, $stroke, $filled)

    function P { param($x, $y) return @(($ox + $x * $SCALE), ($oy + $y * $SCALE)) }

    if ($filled) {
        $round = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(200, 30, 20)), ($stroke * $SCALE)
        $round.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
        $round.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
        $butt = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(200, 30, 20)), ($stroke * $SCALE)
        $butt.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
        $butt.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat
    } else {
        $round = $pen; $butt = $pen
    }

    # a, with its hook
    $p1 = P $X_RIGHT $Y_A; $p2 = P $HOOK_START_X $Y_A
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddLine($p1[0], $p1[1], $p2[0], $p2[1])
    $arc = P ($HOOK_START_X - $HOOK_R) $Y_A
    $path.AddArc($arc[0], $arc[1], ($HOOK_R * 2 * $SCALE), ($HOOK_R * 2 * $SCALE), 270, -90)
    $g.DrawPath($round, $path)

    # d, with its hook
    $p1 = P $X_RIGHT $Y_D; $p2 = P $HOOK_START_X $Y_D
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddLine($p1[0], $p1[1], $p2[0], $p2[1])
    $arc = P ($HOOK_START_X - $HOOK_R) ($Y_D - $HOOK_R * 2)
    $path.AddArc($arc[0], $arc[1], ($HOOK_R * 2 * $SCALE), ($HOOK_R * 2 * $SCALE), 90, 90)
    $g.DrawPath($round, $path)

    # g
    $p1 = P $X_RIGHT $gY; $p2 = P $X_LEFT $gY
    $g.DrawLine($round, $p1[0], $p1[1], $p2[0], $p2[1])

    # b, c, f, e
    $p1 = P $X_RIGHT $Y_A;        $p2 = P $X_RIGHT $gY;          $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])
    $p1 = P $X_RIGHT $gY;         $p2 = P $X_RIGHT $Y_C_BOTTOM;  $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])
    $p1 = P $X_LEFT $Y_F_TOP;     $p2 = P $X_LEFT $gY;           $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])
    $p1 = P $X_LEFT $gY;          $p2 = P $X_LEFT $Y_E_BOTTOM;   $g.DrawLine($butt, $p1[0], $p1[1], $p2[0], $p2[1])
}

# ---- draw all three, three ways ---------------------------------------------

$g.DrawString("Where should the middle bar sit?", $titleFont, $black, 22, 20)
$g.DrawString("Only g moves. Hooks, width, terminations and stroke are identical across all three.", $noteFont, $grey, 22, 48)
$g.DrawString("Units: centreline box 100 tall. No slant - it is the same on all three.", $noteFont, $grey, 22, 66)

for ($i = 0; $i -lt 3; $i++) {

    $v = $VARIANTS[$i]
    $ox = $COL_X[$i]
    $gY = $v.G

    $g.DrawString($v.Name, $headFont, $black, ($ox - 6), 104)
    $g.DrawString($v.Detail, $noteFont, $grey, ($ox - 6), 124)

    # Row 1: the skeleton, with g's position marked.
    Draw-Cell $ox $ROW_Y[0] $gY $hair 0 $false
    $a = $ox; $b = $ox + $X_RIGHT * $SCALE
    $g.DrawLine($guide, ($a - 30), ($ROW_Y[0] + $gY * $SCALE), ($b + 10), ($ROW_Y[0] + $gY * $SCALE))
    $g.FillEllipse($redBrush, ($ox + $X_RIGHT * $SCALE - 4), ($ROW_Y[0] + $gY * $SCALE - 4), 8, 8)

    $g.DrawString(("b {0:N2}" -f $gY), $noteFont, $grey, ($ox - 4), ($ROW_Y[0] + $gY * $SCALE / 2 - 8))
    $g.DrawString(("c {0:N2}" -f (100 - $gY)), $noteFont, $grey, ($ox - 4), ($ROW_Y[0] + ($gY + 100) * $SCALE / 2 - 8))
    $g.DrawString(("c/b {0:N3}" -f ((100 - $gY) / $gY)), $noteFont, $grey, ($ox - 6), ($ROW_Y[0] + 108 * $SCALE))

    # Row 2: rendered at the stroke we have been looking at.
    Draw-Cell $ox $ROW_Y[1] $gY $null $STROKE_CURRENT $true

    # Row 3: rendered at the stroke the datasheet actually measures.
    Draw-Cell $ox $ROW_Y[2] $gY $null $STROKE_MEASURED $true
}

$g.DrawString("skeleton", $noteFont, $grey, 22, ($ROW_Y[0] + 40 * $SCALE))
$g.DrawString(("stroke {0:N2}" -f $STROKE_CURRENT), $noteFont, $grey, 22, ($ROW_Y[1] + 40 * $SCALE))
$g.DrawString("(current)", $noteFont, $grey, 22, ($ROW_Y[1] + 40 * $SCALE + 17))
$g.DrawString(("stroke {0:N2}" -f $STROKE_MEASURED), $noteFont, $grey, 22, ($ROW_Y[2] + 40 * $SCALE))
$g.DrawString("(datasheet)", $noteFont, $grey, 22, ($ROW_Y[2] + 40 * $SCALE + 17))

$g.DrawString("The datasheet's g sits ABOVE centre, making c longer than b - the opposite of the reconstruction.", $noteFont, $grey, 22, ($CANVAS_H - 44))
$g.DrawString("Bottom row also shows the measured stroke, which is half what the font currently uses.", $noteFont, $grey, 22, ($CANVAS_H - 26))

$g.Dispose()
$bmp.Save($OUT, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "wrote $OUT"
