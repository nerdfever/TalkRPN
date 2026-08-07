# TalkRPN cell - centreline skeleton, dimensioned.
#
# The reference drawing for TalkRpnFont.kt: no stroke width, no slant, every
# segment as the hairline it is defined as. cell_geometry.png is the same thing
# for the HP-01's seven segments and is left alone; this supersedes it.
#
# The segment table below is the single source for both the drawing and the
# printed listing, so the two cannot drift apart. If it disagrees with
# TalkRpnFont.kt, the Kotlin wins.

param(
    # "both" keeps the drawing and the listing on one sheet, as before.
    # "diagram" and "listing" emit them separately, which is what the reference
    # PDF wants so the diagram can have a whole page to itself.
    [ValidateSet("both", "diagram", "listing")]
    [string] $Section = "both",

    [string] $OutputName = "talkrpn_geometry.png"
)

$OUT = "$PSScriptRoot\$OutputName"

# ---- geometry, in cell units (must match TalkRpnFont.kt) -------------------

$CELL_HEIGHT  = 100.0
$CELL_WIDTH   = 58.47
$TOTAL_HEIGHT = 144.0
$STROKE       = 9.29
$SLANT_DEG    = 7.5
$HOOK_R       = 7.92
$ADVANCE      = 142.08

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

# ---- page ------------------------------------------------------------------

$SCALE    = 5.2
$ORIGIN_X = 330.0
$ORIGIN_Y = 190.0
$CANVAS_W = 1240

# Height depends on what is on the sheet. The listing runs to about fifty lines
# at 15 px each; the drawing ends near y = 1460.
$DRAWING_BOTTOM = 1500
$LISTING_LINE_H = 15
$LISTING_LINES = 52

$CANVAS_H = switch ($Section) {
    "diagram" { $DRAWING_BOTTOM }
    "listing" { 120 + $LISTING_LINES * $LISTING_LINE_H }
    default   { 2320 }
}

Add-Type -AssemblyName System.Drawing

$bmp = New-Object System.Drawing.Bitmap $CANVAS_W, $CANVAS_H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::White)

$centrePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), 1.8
$altPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(30, 90, 200)), 1.8
$dimPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(70, 110, 170)), 1.1
$dimPen.EndCap = [System.Drawing.Drawing2D.LineCap]::ArrowAnchor
$dimPen.StartCap = [System.Drawing.Drawing2D.LineCap]::ArrowAnchor
$guidePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(200, 200, 200)), 0.9

$titleFont  = New-Object System.Drawing.Font "Segoe UI", 16, ([System.Drawing.FontStyle]::Bold)
$noteFont   = New-Object System.Drawing.Font "Consolas", 11
$letterFont = New-Object System.Drawing.Font "Segoe UI", 12, ([System.Drawing.FontStyle]::Bold)
$altFont    = New-Object System.Drawing.Font "Segoe UI", 12, ([System.Drawing.FontStyle]::Bold)

$black     = [System.Drawing.Brushes]::Black
$blueBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(30, 90, 200))
$dimBrush  = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(70, 110, 170))
$redBrush  = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200, 40, 30))
$greyBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(110, 110, 110))

function P($x, $y) {
    New-Object System.Drawing.PointF (($ORIGIN_X + $x * $SCALE), ($ORIGIN_Y + $y * $SCALE))
}

# ---- the segment table -----------------------------------------------------
#
# Kind is Line, Arc or Dot. Alt marks the two hook arcs, which are alternatives
# to A4 and D4 rather than additions - never lit together with them.

$SEGMENTS = @(
    @{ N = "A1"; Kind = "Line"; X1 = $X_MID;        Y1 = $Y_TOP;  X2 = $X_HOOK_START; Y2 = $Y_TOP;      LabelDX =  -6; LabelDY = -20 }
    @{ N = "A2"; Kind = "Line"; X1 = $X_MID;        Y1 = $Y_TOP;  X2 = $X_RIGHT;      Y2 = $Y_TOP;      LabelDX =  -6; LabelDY = -20 }
    @{ N = "A4"; Kind = "Line"; X1 = $X_HOOK_START; Y1 = $Y_TOP;  X2 = $X_LEFT;       Y2 = $Y_TOP;      LabelDX = -14; LabelDY = -32 }
    @{ N = "A3"; Kind = "Arc";  CX = $HOOK_R; CY = $HOOK_R;      R = $HOOK_R; From = 270; To = 180;     LabelDX = -46; LabelDY = -34; Alt = $true }

    @{ N = "B";  Kind = "Line"; X1 = $X_RIGHT;      Y1 = $Y_TOP;  X2 = $X_RIGHT;      Y2 = $Y_MID;      LabelDX =   8; LabelDY =  -8 }
    @{ N = "C";  Kind = "Line"; X1 = $X_RIGHT;      Y1 = $Y_MID;  X2 = $X_RIGHT;      Y2 = $Y_BASE;     LabelDX =   8; LabelDY =  -8 }

    @{ N = "D1"; Kind = "Line"; X1 = $X_MID;        Y1 = $Y_BASE; X2 = $X_HOOK_START; Y2 = $Y_BASE;     LabelDX =  -6; LabelDY =   6 }
    @{ N = "D2"; Kind = "Line"; X1 = $X_MID;        Y1 = $Y_BASE; X2 = $X_RIGHT;      Y2 = $Y_BASE;     LabelDX =  -6; LabelDY =   6 }
    @{ N = "D4"; Kind = "Line"; X1 = $X_HOOK_START; Y1 = $Y_BASE; X2 = $X_LEFT;       Y2 = $Y_BASE;     LabelDX = -14; LabelDY =  16 }
    @{ N = "D3"; Kind = "Arc";  CX = $HOOK_R; CY = $Y_E_BOTTOM;  R = $HOOK_R; From = 90;  To = 180;     LabelDX = -46; LabelDY =  16; Alt = $true }

    @{ N = "F2"; Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_TOP;    X2 = $X_LEFT;     Y2 = $Y_F_TOP;    LabelDX = -30; LabelDY = -14 }
    @{ N = "F1"; Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_F_TOP;  X2 = $X_LEFT;     Y2 = $Y_MID;      LabelDX = -22; LabelDY =  -8 }
    @{ N = "E1"; Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_MID;    X2 = $X_LEFT;     Y2 = $Y_E_BOTTOM; LabelDX = -22; LabelDY =  -8 }
    @{ N = "E2"; Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_E_BOTTOM; X2 = $X_LEFT;   Y2 = $Y_BASE;     LabelDX = -30; LabelDY =  -4 }

    @{ N = "G1"; Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_MID;  X2 = $X_MID;        Y2 = $Y_MID;      LabelDX =  -6; LabelDY = -20 }
    @{ N = "G2"; Kind = "Line"; X1 = $X_MID;        Y1 = $Y_MID;  X2 = $X_RIGHT;      Y2 = $Y_MID;      LabelDX =  -6; LabelDY = -20 }

    @{ N = "H";  Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_F_TOP; X2 = $X_MID;       Y2 = $Y_MID;      LabelDX =   4; LabelDY = -18 }
    @{ N = "I";  Kind = "Line"; X1 = $X_RIGHT;      Y1 = $Y_TOP;   X2 = $X_MID;       Y2 = $Y_MID;      LabelDX =   6; LabelDY = -18 }
    @{ N = "J";  Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_MID;   X2 = $X_MID;       Y2 = $Y_BASE;     LabelT = 0.25; LabelDX = -30; LabelDY =  -6 }
    @{ N = "L";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_MID;   X2 = $X_LEFT;      Y2 = $Y_E_BOTTOM; LabelT = 0.25; LabelDX =  14; LabelDY =  -6 }
    @{ N = "K";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_MID;   X2 = $X_RIGHT;     Y2 = $Y_BASE;     LabelDX =   6; LabelDY =  -4 }

    @{ N = "P";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_TOP;   X2 = $X_MID;       Y2 = $Y_MID;      LabelDX =   6; LabelDY =  -8 }
    @{ N = "Q";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_MID;   X2 = $X_MID;       Y2 = $Y_BASE;     LabelDX =   6; LabelDY =  -8 }

    @{ N = "M";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_BASE;  X2 = $X_MID;       Y2 = $Y_DESC;     LabelDX =   8; LabelDY =  -8 }
    @{ N = "N";  Kind = "Line"; X1 = $X_N_LEFT;     Y1 = $Y_DESC;  X2 = $X_MID;       Y2 = $Y_DESC;     LabelDX =  -6; LabelDY =   6 }
    @{ N = "O";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_DESC;  X2 = $X_O_RIGHT;   Y2 = $Y_DESC;     LabelDX =  -6; LabelDY =   6 }

    @{ N = "A5"; Kind = "Poly"; Alt = $true; LabelX = 54.0; LabelY = 2.0
       Pts = @(, @(29.235, 0.0)) + @(0..16 | ForEach-Object {
           $t = (270 + 90 * $_ / 16.0) * [Math]::PI / 180.0
           , @((50.55 + 7.92 * [Math]::Cos($t)), (7.92 + 7.92 * [Math]::Sin($t)))
       }) + @(, @(58.47, 50.0)) }
    @{ N = "D5"; Kind = "Poly"; Alt = $true; LabelX = 54.0; LabelY = 88.0
       Pts = @(, @(58.47, 50.0)) + @(0..16 | ForEach-Object {
           $t = (90 * $_ / 16.0) * [Math]::PI / 180.0
           , @((50.55 + 7.92 * [Math]::Cos($t)), (92.08 + 7.92 * [Math]::Sin($t)))
       }) + @(, @(29.235, 100.0)) }

    @{ N = "COL1";  Kind = "Dot"; X1 = $X_MID; Y1 = $COL1_Y; LabelDX = 10; LabelDY = -9 }
    @{ N = "COL2";  Kind = "Dot"; X1 = $X_MID; Y1 = $COL2_Y; LabelDX = 10; LabelDY = -9 }
    @{ N = "DP";    Kind = "Dot"; X1 = $DP_X;  Y1 = $DP_Y;   LabelDX = 10; LabelDY = -9 }
    @{ N = "COMMA"; Kind = "Dot"; X1 = $DP_X;  Y1 = $DP_Y;   LabelDX = 10; LabelDY =  8 }
)

# ---- draw ------------------------------------------------------------------

if ($Section -ne "listing") {
foreach ($s in $SEGMENTS) {

    $pen = if ($s.Alt) { $altPen } else { $centrePen }
    $brush = if ($s.Alt) { $blueBrush } else { $black }

    switch ($s.Kind) {

        "Line" {
            $a = P $s.X1 $s.Y1
            $b = P $s.X2 $s.Y2
            $g.DrawLine($pen, $a, $b)
            $labelT = if ($null -ne $s.LabelT) { $s.LabelT } else { 0.5 }
            $mx = $a.X + ($b.X - $a.X) * $labelT + $s.LabelDX
            $my = $a.Y + ($b.Y - $a.Y) * $labelT + $s.LabelDY
            $g.DrawString($s.N, $letterFont, $brush, $mx, $my)
        }

        "Arc" {
            $pts = @()
            for ($i = 0; $i -le 24; $i++) {
                $t = ($s.From + ($s.To - $s.From) * $i / 24.0) * [Math]::PI / 180.0
                $pts += P ($s.CX + $s.R * [Math]::Cos($t)) ($s.CY + $s.R * [Math]::Sin($t))
            }
            $g.DrawLines($pen, [System.Drawing.PointF[]]$pts)
            $mid = $pts[12]
            $g.DrawString($s.N, $altFont, $brush, ($mid.X + $s.LabelDX), ($mid.Y + $s.LabelDY))
        }

        "Poly" {
            $pts = @()
            foreach ($q in $s.Pts) { $pts += P $q[0] $q[1] }
            $g.DrawLines($pen, [System.Drawing.PointF[]]$pts)
            $lp = P $s.LabelX $s.LabelY
            $g.DrawString($s.N, $altFont, $brush, $lp.X, $lp.Y)
        }

        "Dot" {
            $c = P $s.X1 $s.Y1
            $g.FillEllipse($redBrush, ($c.X - 4), ($c.Y - 4), 8, 8)
            $g.DrawString($s.N, $noteFont, $greyBrush, ($c.X + $s.LabelDX), ($c.Y + $s.LabelDY))
        }
    }
}

# ---- dimensions ------------------------------------------------------------

function Dim($x1, $y1, $x2, $y2, $text, $tdx, $tdy) {
    $a = P $x1 $y1
    $b = P $x2 $y2
    $g.DrawLine($dimPen, $a, $b)
    $g.DrawString($text, $noteFont, $dimBrush, (($a.X + $b.X) / 2 + $tdx), (($a.Y + $b.Y) / 2 + $tdy))
}

# Verticals, stacked out to the right of the cell.
Dim ($X_RIGHT + 12) $Y_TOP ($X_RIGHT + 12) $Y_MID  "50.00" 6 -8
Dim ($X_RIGHT + 26) $Y_TOP ($X_RIGHT + 26) $Y_BASE "100.00  baseline" 6 -8
Dim ($X_RIGHT + 40) $Y_TOP ($X_RIGHT + 40) $Y_DESC "144.00  descender" 6 -8
Dim ($X_RIGHT + 54) $Y_TOP ($X_RIGHT + 54) $COL1_Y "20.49  COL1" 6 -8
Dim ($X_RIGHT + 68) $Y_TOP ($X_RIGHT + 68) $COL2_Y "80.60  COL2" 6 -8

# Horizontals, stacked below.
Dim $X_LEFT ($Y_DESC + 14) $X_RIGHT ($Y_DESC + 14) "58.47  cell width" -40 6
Dim $X_LEFT ($Y_DESC + 28) $X_MID   ($Y_DESC + 28) "29.235  centre axis" -50 6
Dim $X_LEFT ($Y_DESC + 42) $X_HOOK_START ($Y_DESC + 42) "7.92  hook R" -30 6
Dim $X_LEFT ($Y_DESC + 56) $X_N_LEFT ($Y_DESC + 56) "3.74  N left" -20 6
Dim $X_LEFT ($Y_DESC + 70) $X_O_RIGHT ($Y_DESC + 70) "54.72  O right" -40 6
Dim $X_LEFT ($Y_DESC + 84) $DP_X ($Y_DESC + 84) "86.64  DP axis" -40 6

# Guides from the cell out to the dimension stacks.
foreach ($y in @($Y_MID, $Y_BASE, $Y_DESC, $COL1_Y, $COL2_Y)) {
    $a = P $X_LEFT $y
    $b = P ($X_RIGHT + 70) $y
    $g.DrawLine($guidePen, $a, $b)
}
}
# ---- headings --------------------------------------------------------------

$g.DrawString("TalkRPN cell - centreline skeleton", $titleFont, $black, 24, 22)

$header = @(
    "No stroke width, no slant. Origin is the top-left centreline corner; x right, y down.",
    "Units are the HP-01's centreline box (53.5 x 91.5) scaled by 100/91.5 = 1.0929, so cap height is exactly 100.",
    "29 bars + COL1 + COL2 + DP + COMMA = 33 elements; the mask is a Long.  Blue: A3/D3 are ALTERNATIVES to A4/D4; A5/D5 are the right-paren halves.",
    "Segments meet flush - no gaps, unlike the DL-3422. TalkRpnFont.kt is the source of truth."
)

$y = 56
foreach ($line in $header) {
    $g.DrawString($line, $noteFont, $greyBrush, 24, $y)
    $y += 16
}

# ---- printed listing -------------------------------------------------------

$textTop = if ($Section -eq "listing") { 140 } else { $ORIGIN_Y + ($Y_DESC + 100) * $SCALE }

$lines = @()
$lines += "SEGMENT CENTRELINES              from                 to"

foreach ($s in $SEGMENTS) {
    switch ($s.Kind) {
        "Line" {
            $lines += ("  {0,-5} {1,-10} {2,8:F2},{3,8:F2}  ->  {4,8:F2},{5,8:F2}" -f `
                $s.N, "line", $s.X1, $s.Y1, $s.X2, $s.Y2)
        }
        "Arc" {
            $a0 = $s.From * [Math]::PI / 180.0
            $a1 = $s.To * [Math]::PI / 180.0
            $lines += ("  {0,-5} {1,-10} {2,8:F2},{3,8:F2}  ->  {4,8:F2},{5,8:F2}   arc R{6:F2} centre {7:F2},{8:F2}" -f `
                $s.N, "arc", ($s.CX + $s.R * [Math]::Cos($a0)), ($s.CY + $s.R * [Math]::Sin($a0)), `
                ($s.CX + $s.R * [Math]::Cos($a1)), ($s.CY + $s.R * [Math]::Sin($a1)), $s.R, $s.CX, $s.CY)
        }
    }
}

$lines += "  A5    compound     29.24,    0.00 -> arc R7.92 centre 50.55,7.92 ->  58.47,  50.00   right paren, top half"
$lines += "  D5    compound     58.47,   50.00 -> arc R7.92 centre 50.55,92.08 -> 29.24, 100.00   right paren, bottom half"
$lines += ""
$lines += "DOT CENTRES     COL1    {0:F2}, {1:F2}      upper colon dot; also dots i and j" -f $X_MID, $COL1_Y
$lines += "                COL2    {0:F2}, {1:F2}      lower colon dot, colon only" -f $X_MID, $COL2_Y
$lines += "                DP      {0:F2}, {1:F2}     decimal point, outside the cell to the right" -f $DP_X, $DP_Y
$lines += "                COMMA   {0:F2}, {1:F2}     same centre, plus a tail down and left" -f $DP_X, $DP_Y
$lines += "                radius = STROKE = {0:F2}, so diameter {1:F2}" -f $STROKE, (2 * $STROKE)
$lines += ""
$lines += "CENTRELINE BOX  {0:F2} wide x {1:F2} tall    aspect {2:F3}" -f $CELL_WIDTH, $CELL_HEIGHT, ($CELL_WIDTH / $CELL_HEIGHT)
$lines += "                {0:F2} tall including the descender" -f $TOTAL_HEIGHT
$lines += "ADVANCE         {0:F2}   origin to origin" -f $ADVANCE
$lines += "SLANT           {0:F1} degrees, applied at render" -f $SLANT_DEG
$lines += ""
$lines += "RELATIONSHIPS"
$lines += "  hook radius = hook start x = {0:F2}, so each arc is tangent to both bar and column" -f $HOOK_R
$lines += "  F1 top = {0:F2} = where A3 lands.  E1 bottom = {1:F2} = where D3 lands." -f $Y_F_TOP, $Y_E_BOTTOM
$lines += "  F2 and E2 are the stubs that carry the left column out to the corner when it is square."
$lines += "  B = C = {0:F2}: the right column is undivided at the corners, which is what makes a 4 lopsided." -f $Y_MID
$lines += "  N and O are inset {0:F2} and {1:F2} from the columns - symmetric to within measurement." -f $X_N_LEFT, ($CELL_WIDTH - $X_O_RIGHT)
$lines += ""
$lines += "THE THREE LEFT-COLUMN ENDINGS"
$lines += "  square   A4 + F2      most letters"
$lines += "  hooked   A3 + F1      digits 2 3 5 7 9   (F2 dark)"
$lines += "  short    F1 alone     digit 4, no A at all - left side sits {0:F2} lower than the right" -f $HOOK_R
$lines += ""
$lines += "KNOWN GAP"
$lines += "  J is a lower-left backslash with no mirror in the lower right, so a textbook V - arms"
$lines += "  descending to the bottom centre from both sides - cannot be drawn. A 27th bar would fix it."

if ($Section -ne "diagram") {
$y = $textTop
foreach ($line in $lines) {
    $g.DrawString($line, $noteFont, $black, 24, $y)
    $y += 15
}
}
$g.Dispose()
$bmp.Save($OUT, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "wrote $OUT  ($CANVAS_W x $CANVAS_H)"










