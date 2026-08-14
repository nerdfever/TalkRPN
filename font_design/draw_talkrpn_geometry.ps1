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

# ---- geometry, in cell widths (must match TalkRpnFont.kt) -------------------
#
# THE UNIT: segment E/F to segment B/C is 1. Every figure below is in that unit,
# horizontally and vertically alike, to four significant figures.

$CELL_WIDTH   = 1.0
$CELL_HEIGHT  = 1.710      # cap height, segment D to segment A
$DESCENDER_DEPTH = 0.7525  # how far the N/O bar hangs below the baseline
$TOTAL_HEIGHT = $CELL_HEIGHT + $DESCENDER_DEPTH
$STROKE       = 0.1475
$SLANT_DEG    = 6.0
$HOOK_R       = 0.1355

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
$X_HOOK_END_R = $X_RIGHT - $HOOK_R

# The descender bar, inset from both columns - very slightly asymmetrically.
$X_N_LEFT  = 0.06396
$X_O_RIGHT = 0.9359

$COL1_Y = 0.3504
$COL2_Y = 1.378

# From the last lit centreline of one glyph to the first of the next, and how far across
# it the decimal point sits. Both mirror TalkRpnFont.
$GAP = 0.85
$DP_GAP_FRACTION = 0.337

$DP_DROP = 0.3263          # how far the decimal point sits below the baseline
$DP_X   = $CELL_WIDTH + $DP_GAP_FRACTION * $GAP
$DP_Y   = $CELL_HEIGHT + $DP_DROP

# What two FULL-WIDTH glyphs sit apart at that gap - the widest any pair gets.
$PITCH = $CELL_WIDTH + $GAP

$COMMA_TAIL_DROP = 0.3551
$COMMA_TAIL_LEFT = 0.1308
$COMMA_TAIL_TIP_X = $DP_X - $COMMA_TAIL_LEFT
$COMMA_TAIL_TIP_Y = $DP_Y + $COMMA_TAIL_DROP

# COL2_TAIL is the same tail hung off the lower colon dot instead, which is what
# turns a colon into a semicolon.
$COL2_TAIL_TIP_X = $X_MID - $COMMA_TAIL_LEFT
$COL2_TAIL_TIP_Y = $COL2_Y + $COMMA_TAIL_DROP

# ---- page ------------------------------------------------------------------

# Px per cell width - the diagram's own rendering scale, nothing to do with the
# font. Sized so the cell fills the page width left of the dimension column.
$SCALE    = 304.0
$ORIGIN_X = 330.0

# Below the header block, which explains what this is before it draws anything.
$ORIGIN_Y = 290.0

# The drawing needs width for its dimension stacks; the listing is only as wide
# as its longest line. Sizing them separately matters because each sheet is
# scaled to fit the page - so a canvas wider than its content shrinks the text
# for no reason, which is what made the listing render at half the size it could.
#
# The listing width is set by its longest line. $noteFont is Consolas 11 POINTS,
# which at 96 dpi is 14.7 px tall and about 8.1 px per character - so a 108-column
# line needs ~880 px plus the left inset. Measured by rendering, not assumed:
# 820 clipped "the whole right paren" off the RPAR row.
$CANVAS_W_DRAWING = 1460
$CANVAS_W_LISTING = 960

$CANVAS_W = switch ($Section) {
    "listing" { $CANVAS_W_LISTING }
    default   { $CANVAS_W_DRAWING }
}

# Where the header starts and how tall a line of it is - the drawing and the
# listing both have to clear it, so it is stated once.
$HEADER_TOP = 56
$HEADER_LINE_H = 16

# Height depends on what is on the sheet. The drawing ends below the horizontal
# dimension stack; the listing runs to about seventy lines.
$DRAWING_BOTTOM = 1380
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

    @{ N = "H";  Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_TOP;   X2 = $X_MID;       Y2 = $Y_MID;      LabelDX =   4; LabelDY = -18 }
    @{ N = "I";  Kind = "Line"; X1 = $X_RIGHT;      Y1 = $Y_TOP;   X2 = $X_MID;       Y2 = $Y_MID;      LabelDX =   6; LabelDY = -18 }
    @{ N = "J";  Kind = "Line"; X1 = $X_LEFT;       Y1 = $Y_MID;   X2 = $X_MID;       Y2 = $Y_BASE;     LabelT = 0.25; LabelDX = -30; LabelDY =  -6 }
    @{ N = "L";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_MID;   X2 = $X_LEFT;      Y2 = $Y_BASE; LabelT = 0.25; LabelDX =  14; LabelDY =  -6 }
    @{ N = "K";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_MID;   X2 = $X_RIGHT;     Y2 = $Y_BASE;     LabelDX =   6; LabelDY =  -4 }

    @{ N = "P";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_TOP;   X2 = $X_MID;       Y2 = $Y_MID;      LabelDX =   6; LabelDY =  -8 }
    @{ N = "Q";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_MID;   X2 = $X_MID;       Y2 = $Y_BASE;     LabelDX =   6; LabelDY =  -8 }

    @{ N = "COL2_TAIL"; Kind = "Line"; X1 = $X_MID; Y1 = $COL2_Y
       X2 = ($X_MID - $COMMA_TAIL_LEFT); Y2 = ($COL2_Y + $COMMA_TAIL_DROP); LabelT = 0.4; LabelDX = 14; LabelDY = 8 }

    @{ N = "M";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_BASE;  X2 = $X_MID;       Y2 = $Y_DESC;     LabelDX =   8; LabelDY =  -8 }
    @{ N = "N";  Kind = "Line"; X1 = $X_N_LEFT;     Y1 = $Y_DESC;  X2 = $X_MID;       Y2 = $Y_DESC;     LabelDX =  -6; LabelDY =   6 }
    @{ N = "O";  Kind = "Line"; X1 = $X_MID;        Y1 = $Y_DESC;  X2 = $X_O_RIGHT;   Y2 = $Y_DESC;     LabelDX =  -6; LabelDY =   6 }

    @{ N = "RPAR"; Kind = "Poly"; Alt = $true; LabelX = 1.035; LabelY = 0.787
       Pts = @(, @($X_MID, $Y_TOP)) + @(0..16 | ForEach-Object {
           $t = (270 + 90 * $_ / 16.0) * [Math]::PI / 180.0
           , @(($X_HOOK_END_R + $HOOK_R * [Math]::Cos($t)), ($Y_F_TOP + $HOOK_R * [Math]::Sin($t)))
       }) + @(0..16 | ForEach-Object {
           $t = (90 * $_ / 16.0) * [Math]::PI / 180.0
           , @(($X_HOOK_END_R + $HOOK_R * [Math]::Cos($t)), ($Y_E_BOTTOM + $HOOK_R * [Math]::Sin($t)))
       }) + @(, @($X_MID, $Y_BASE)) }

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
            $g.FillRectangle($redBrush, ($c.X - 4), ($c.Y - 4), 8, 8)
            $g.DrawString($s.N, $noteFont, $greyBrush, ($c.X + $s.LabelDX), ($c.Y + $s.LabelDY))

            # The comma is a dot PLUS its tail; show the tail too.
            if ($s.N -eq "COMMA") {
                $g.DrawLine($centrePen, $c, (P $COMMA_TAIL_TIP_X $COMMA_TAIL_TIP_Y))
            }
        }
    }
}

# ---- dimensions ------------------------------------------------------------

function Dim($x1, $y1, $x2, $y2) {
    $g.DrawLine($dimPen, (P $x1 $y1), (P $x2 $y2))
}

# Every label's number is formatted FROM the value it points at, so a dimension
# can never end up quoting a figure the drawing no longer uses.
function DimLabel($value, $what) { "{0:F3}  {1}" -f $value, $what }

# How far the first dimension line stands off the drawing, and how far apart the
# stacked ones are. In cell widths, like everything else here.
#
# The stack is tight because the labels are NOT on it: each label sits beside the
# END of its own line, at the very coordinate it calls out. With ten dimensions
# that is the difference between a readable sheet and ten labels in one band.
$DIM_GAP  = 0.20
$DIM_STEP = 0.10

# ---- verticals: every y worth naming, in top-to-bottom order ----------------
#
# Stacked out to the right of EVERYTHING, decimal point included, so no dimension
# line is drawn across a segment or a dot.
#
# DY nudges a label off its natural spot, needed only where two dimensions land
# within a line's height of each other.

$VERTICALS = @(
    @{ Y = $Y_F_TOP;          What = "F1 top, where A3 lands" }
    @{ Y = $COL1_Y;           What = "COL1 dot" }
    @{ Y = $Y_MID;            What = "middle bar" }
    @{ Y = $COL2_Y;           What = "COL2 dot" }
    @{ Y = $Y_E_BOTTOM;       What = "E1 bottom, where D3 leaves" }
    @{ Y = $Y_BASE;           What = "baseline";           DY = -20 }
    @{ Y = $COL2_TAIL_TIP_Y;  What = "COL2_TAIL bottom";   DY = 2 }
    @{ Y = $DP_Y;             What = "DP and COMMA dot" }
    @{ Y = $COMMA_TAIL_TIP_Y; What = "COMMA tail bottom";  DY = -20 }
    @{ Y = $Y_DESC;           What = "descender bar, N and O" }
)

# Far enough right to clear the decimal point, which is the rightmost ink.
$VERTICAL_STACK_LEFT = $DP_X + $DIM_GAP

# Labels go in one column past the WHOLE stack, not beside their own line. Put
# each beside its own line and the leftmost label is printed across the nine
# lines to its right, which is what the first draft did.
$VERTICAL_LABEL_X = $VERTICAL_STACK_LEFT + $VERTICALS.Count * $DIM_STEP

$vx = $VERTICAL_STACK_LEFT

foreach ($d in $VERTICALS) {

    Dim $vx $Y_TOP $vx $d.Y

    $end = P $VERTICAL_LABEL_X $d.Y
    $dy = if ($d.ContainsKey("DY")) { $d.DY } else { -8 }

    $g.DrawString((DimLabel $d.Y $d.What), $noteFont, $dimBrush, $end.X, ($end.Y + $dy))

    $vx += $DIM_STEP
}

# ---- horizontals: every x worth naming, left-to-right ------------------------
#
# Each gets its own row below the drawing, so the labels can sit at the end of
# their own line without any chance of collision.

$HORIZONTALS = @(
    @{ X = $X_N_LEFT;         What = "segment N, left end" }
    @{ X = $X_HOOK_START;     What = "hook radius, and where each bar's straight run ends" }
    @{ X = $COL2_TAIL_TIP_X;  What = "COL2_TAIL left end" }
    @{ X = $X_MID;            What = "centre axis" }
    @{ X = $X_HOOK_END_R;     What = "RPAR, where its arcs turn" }
    @{ X = $X_O_RIGHT;        What = "segment O, right end" }
    @{ X = $X_RIGHT;          What = "cell width, the unit" }
    @{ X = $COMMA_TAIL_TIP_X; What = "COMMA tail left end" }
    @{ X = $DP_X;             What = "DP and COMMA axis" }
)

$hy = $Y_DESC + $DIM_STEP

foreach ($d in $HORIZONTALS) {

    Dim $X_LEFT $hy $d.X $hy

    $end = P $d.X $hy
    $g.DrawString((DimLabel $d.X $d.What), $noteFont, $dimBrush, ($end.X + 8), ($end.Y - 9))

    $hy += $DIM_STEP
}

# Guides from the drawing out to the vertical stack, so each dimension line can be
# traced back to the feature it measures.
foreach ($d in $VERTICALS) {
    $a = P $X_LEFT $d.Y
    $b = P $VERTICAL_LABEL_X $d.Y
    $g.DrawLine($guidePen, $a, $b)
}
}
# ---- headings --------------------------------------------------------------

$title =
    if ($Section -eq "listing") { "TalkRPN cell - centreline listing" }
    else { "TalkRPN cell - centreline skeleton" }

$g.DrawString($title, $titleFont, $black, 24, 22)

# The explanatory block belongs on the drawing only. When the two sections are
# separate sheets it used to be printed on both, which is just the same three
# paragraphs read twice.
$header = if ($Section -eq "listing") { @() } else { @(
    "A 1970s bubble-LED look: HP-01 styling (rounded left corners) on a modified DL-3422 segment set, so the display can show text as well as digits.",
    "Identical to neither part. Segments meet flush where real hardware leaves gaps, and the decimal point sits in the gap after the cell rather than",
    "taking a cell of its own.",
    "",
    "THE UNIT: segment E/F to segment B/C is 1 - the left column to the right column, centre to centre. Every length here is in that unit, across and",
    "down alike, to four significant figures, so the centre axis is exactly 0.5 and the gap reads directly as the clearance between neighbours.",
    "",
    "No stroke width, no slant: these are centrelines, and both are applied at render time. Origin is the top-left centreline corner; x right, y down.",
    "32 elements, so the mask is a Long.  Blue: A3/D3 are ALTERNATIVES to A4/D4, never both lit; RPAR is the whole right paren as one element.",
    "TalkRpnFont.kt is the source of truth - this sheet mirrors it."
) }

$y = $HEADER_TOP
foreach ($line in $header) {
    $g.DrawString($line, $noteFont, $greyBrush, 24, $y)
    $y += $HEADER_LINE_H
}

# Where the header actually ended, so anything below it clears it by measurement
# rather than by a guess that goes stale the next time a line is added.
$headerBottom = $y + $HEADER_LINE_H

# ---- printed listing -------------------------------------------------------

# On its own sheet the listing sits just under the header; sharing a sheet with
# the drawing it has to clear the horizontal dimension stack as well.
$textTop =
    if ($Section -eq "listing") { $headerBottom }
    else { $ORIGIN_Y + ($Y_DESC + $HORIZONTALS.Count * $DIM_STEP + $DIM_STEP) * $SCALE }

$lines = @()
$lines += "SEGMENT CENTRELINES              from                 to"

foreach ($s in $SEGMENTS) {
    switch ($s.Kind) {
        "Line" {
            $lines += ("  {0,-5} {1,-10} {2,8:F3},{3,8:F3}  ->  {4,8:F3},{5,8:F3}" -f `
                $s.N, "line", $s.X1, $s.Y1, $s.X2, $s.Y2)
        }
        "Arc" {
            $a0 = $s.From * [Math]::PI / 180.0
            $a1 = $s.To * [Math]::PI / 180.0
            $lines += ("  {0,-5} {1,-10} {2,8:F3},{3,8:F3}  ->  {4,8:F3},{5,8:F3}   arc R{6:F3} centre {7:F3},{8:F3}" -f `
                $s.N, "arc", ($s.CX + $s.R * [Math]::Cos($a0)), ($s.CY + $s.R * [Math]::Sin($a0)), `
                ($s.CX + $s.R * [Math]::Cos($a1)), ($s.CY + $s.R * [Math]::Sin($a1)), $s.R, $s.CX, $s.CY)
        }
    }
}

$lines += "  RPAR  compound     {0:F3},{1:F3} -> arc -> {2:F3}, {3:F3}..{4:F3} -> arc -> {0:F3},{5:F3}   the whole right paren" -f `
    $X_MID, $Y_TOP, $X_RIGHT, $Y_F_TOP, $Y_E_BOTTOM, $Y_BASE
$lines += ""
$lines += "DOT CENTRES     COL1    {0:F3}, {1:F3}      upper colon dot; also dots i and j" -f $X_MID, $COL1_Y
$lines += "                COL2    {0:F3}, {1:F3}      lower colon dot, colon only" -f $X_MID, $COL2_Y
$lines += "                DP      {0:F3}, {1:F3}     decimal point, outside the cell to the right" -f $DP_X, $DP_Y
$lines += "                COMMA   {0:F3}, {1:F3}     same centre, plus a tail down and left" -f $DP_X, $DP_Y
$lines += "                SQUARE, side = 2 x STROKE = {0:F3}" -f (2 * $STROKE)
$lines += ""
$lines += "CENTRELINE BOX  {0:F3} wide x {1:F3} tall    aspect {2:F3}" -f $CELL_WIDTH, $CELL_HEIGHT, ($CELL_WIDTH / $CELL_HEIGHT)
$lines += "                {0:F3} tall including the descender" -f $TOTAL_HEIGHT
$lines += "GAP             {0:F3}   last centreline of one glyph to the first of the next; the ink meets at one stroke" -f $GAP
$lines += "VPITCH          floor is {0:F3}, the ink height - below that descenders reach the next row" -f ($TOTAL_HEIGHT + $STROKE)
$lines += "SLANT           {0:F1} degrees, applied at render" -f $SLANT_DEG
$lines += ""
$lines += "RELATIONSHIPS"
$lines += "  hook radius = hook start x = {0:F3}, so each arc is tangent to both bar and column" -f $HOOK_R
$lines += "  F1 top = {0:F3} = where A3 lands.  E1 bottom = {1:F3} = where D3 lands." -f $Y_F_TOP, $Y_E_BOTTOM
$lines += "  F2 and E2 are the stubs that carry the left column out to the corner when it is square."
$lines += "  B meets C at y = {0:F3}: the right column is undivided at the corners, which is what makes a 4 lopsided." -f $Y_MID
$lines += "  N and O are inset {0:F4} and {1:F4} from the columns - symmetric to within measurement." -f $X_N_LEFT, ($CELL_WIDTH - $X_O_RIGHT)
$lines += "  COL2_TAIL and the COMMA tail are the SAME tail, {0:F3} down and {1:F3} left, hung off two different dots." -f $COMMA_TAIL_DROP, $COMMA_TAIL_LEFT
$lines += "  That is what makes a semicolon a colon whose lower dot grew a tail, at no cost in new geometry."
$lines += ""
$lines += "THE THREE LEFT-COLUMN ENDINGS"
$lines += "  square   A4 + F2      most letters"
$lines += "  hooked   A3 + F1      0 2 3 5 7 8 9, A C G O Q S, ( & and @   (F2 dark)"
$lines += "  short    F1 alone     digit 4, no A at all - left side sits {0:F3} lower than the right" -f $HOOK_R

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


















