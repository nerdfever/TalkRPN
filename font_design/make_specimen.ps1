# Render sample text in the TalkRPN font - lit segments on black, the way the
# display will actually look. All caps, because that is how the calculator will
# actually be driven: error words, register names and units read fine in caps,
# and caps tolerate a much tighter pitch than lower case does.
#
# Reuses the geometry and glyph tables from make_font_reference.ps1 by executing
# that file's top half (everything before its drawing section), so this cannot
# drift from the mirror - and the mirror answers to the Kotlin.

$OUT_SPECIMEN = "$PSScriptRoot\talkrpn_specimen.png"

# ---- borrow the tables -------------------------------------------------------

$refLines = Get-Content "$PSScriptRoot\make_font_reference.ps1"
$cut = 0..($refLines.Count - 1) | Where-Object { $refLines[$_] -match '^# ---- drawing' } | Select-Object -First 1
if ($null -eq $cut) { throw "could not find the drawing marker in make_font_reference.ps1" }
Invoke-Expression (($refLines[0..($cut - 1)]) -join "`n")

# Char -> segment list, case-sensitively.
$MAP = New-Object 'System.Collections.Generic.Dictionary[char, object]'
foreach ($e in $GLYPHS) { $MAP[[char]$e.C] = $e.S }

# ---- what this renderer adds to the borrowed geometry ------------------------
#
# EVERY length here is in cell widths: segment E/F to segment B/C is 1, and that
# same unit measures pitch and vpitch, horizontally and vertically alike. The one
# exception is $SCALE, which is the px-per-unit conversion, and it says so.
#
# $CELL_WIDTH, $PITCH, $DP_X and $DP_GAP_FRACTION all come from the borrowed
# tables above - this file no longer restates them, so there is one mirror rather
# than two.

# A space is half a pitch wide, which is a display-layer decision rather than a
# font one: full-width spaces read as chasms in running text.
$SPACE_PITCH_FRACTION = 0.5

# Vertical pitch: baseline to baseline between specimen lines. Generous here
# because each line carries a label and wants air around it - the display itself
# will run nearer the 2.62 floor.
$VPITCH = 3.5

# ---- what to render ----------------------------------------------------------

# The pitch bracket: ONE string at every pitch, so the only thing varying
# between lines is the pitch itself.
$PITCH_SAMPLE = "QUICK BROWN FOX 0123456789 42.9565"

# In cell widths. Bracketed close around 1.45-1.85, which is where caps look
# right, with the HP-01's own 2.43 and a deliberately-too-tight 1.35 as anchors.
# The cell is exactly 1 wide, so the figure minus 1 IS the clearance between
# neighbours: 1.35 leaves 0.35, and the ink only meets at 1.16.
$PITCHES = @($PITCH, 2.05, 1.90, 1.80, 1.70, 1.65, 1.55, 1.45, 1.35)

$LINES = @()
foreach ($p in $PITCHES) {
    $LINES += @{ T = $PITCH_SAMPLE; P = $p; Label = ("{0:F2}" -f $p) }
}

# Then running text, calculator state and code, at the pitches in contention.
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; P = 1.83; Label = "TEXT 1.83" }
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; P = 1.57; Label = "TEXT 1.57" }
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; P = 1.45; Label = "TEXT 1.45" }
$LINES += @{ T = "988 ENTER 23 DIVIDE = 42.9565 (DEG) [SI] LASTX"; P = 1.71; Label = "RPN 1.71" }
$LINES += @{ T = "X: 1,234,567.89  Y: -6.02E23  Z: 0.0  T: 3.14159"; P = 1.71; Label = "STACK 1.71" }
$LINES += @{ T = "FOR (I = 0; I < 10; I++) { X[I] = A*B + C/D; }"; P = 1.71; Label = "CODE 1.71" }
$LINES += @{ T = 'PRINTF("%6.2F\N", &VALS[J] | MASK ^ 0X7E);'; P = 1.71; Label = "CODE 1.71" }

# One lower-case line kept for contrast - it is why we settled on caps.
$LINES += @{ T = "the quick brown fox jumps over the lazy dog"; P = 1.71; Label = "lower 1.71" }

# ---- page ---------------------------------------------------------------------

# The ONLY place a physical length enters: how big a unit is drawn. Everything
# else on this page is in units and follows from it.
$SCALE = 36.25               # px per cell width

$MARGIN = 40.0
$LABEL_W = 110.0

# ---- render -------------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$LIT = [System.Drawing.Color]::FromArgb(232, 24, 16)
$labelFont = New-Object System.Drawing.Font "Consolas", 13
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(130, 130, 130))

# Width from the longest line at its own pitch.
$maxUnits = 0.0
foreach ($l in $LINES) {
    $u = $l.T.Length * $l.P + $CELL_WIDTH
    if ($u -gt $maxUnits) { $maxUnits = $u }
}
$W = [int]($maxUnits * $SCALE + 2 * $MARGIN + $LABEL_W)

# Height: the baselines are one vpitch apart, and the last line still needs room
# for its descenders and half a stroke of ink beyond them.
$inkHeight = $TOTAL_HEIGHT + $STROKE
$H = [int]((($LINES.Count - 1) * $VPITCH + $inkHeight) * $SCALE + 2 * $MARGIN)

$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Black)

# One glyph, one stroked path - same approach as everywhere else, so overlapping
# segments do not blend twice and come out brighter.
function Draw-Cell($names, $ox, $oy, $k) {

    # Square caps, mitre joins, and the shear applied as a TRANSFORM rather than
    # point by point - which is what shears the pen along with the path, so a
    # vertical bar's ends come out horizontal and a horizontal bar's slanted, as
    # a real rectangular die's are. Pen width is in CELL units: the transform
    # does the scaling. Mirrors drawTalkRpnCell in TalkRpnFont.kt.
    $pen = New-Object System.Drawing.Pen $LIT, $STROKE
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Square
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Square
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Miter
    $pen.MiterLimit = 2.5
    $brush = New-Object System.Drawing.SolidBrush $LIT

    # Upright cell coordinates; the transform carries them to the canvas.
    function UP($x, $y) { New-Object System.Drawing.PointF ([float]$x, [float]$y) }

    # Where a point lands once placed - for the dots, drawn outside the transform.
    function PT($x, $y) {
        New-Object System.Drawing.PointF (
            ($ox + ($x - $SHEAR * $y + $SHEAR_OFFSET) * $k), ($oy + $y * $k))
    }

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath

    foreach ($n in $names) {
        if ($SEG_LINES.Contains($n)) {
            $s = $SEG_LINES[$n]
            $path.StartFigure()
            $path.AddLine((UP $s[0] $s[1]), (UP $s[2] $s[3]))
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $s = $SEG_ARCS[$n]
            $pts = @()
            foreach ($p in (New-ArcPoints $s[0] $s[1] $s[2] $s[3] $s[4])) { $pts += UP $p[0] $p[1] }
            $path.StartFigure()
            $path.AddLines([System.Drawing.PointF[]]$pts)
        }
        elseif ($SEG_POLYS.Contains($n)) {
            $pts = @()
            foreach ($p in $SEG_POLYS[$n]) { $pts += UP $p[0] $p[1] }
            $path.StartFigure()
            $path.AddLines([System.Drawing.PointF[]]$pts)
        }
        elseif ($n -eq "COMMA") {
            $path.StartFigure()
            $path.AddLine((UP $COMMA_TAIL[0] $COMMA_TAIL[1]), (UP $COMMA_TAIL[2] $COMMA_TAIL[3]))
        }
    }

    if ($path.PointCount -gt 0) {

        # x' = k*x - k*SHEAR*y + (ox + k*SHEAR_OFFSET),  y' = k*y
        $m = New-Object System.Drawing.Drawing2D.Matrix (
            [float]$k, [float]0, [float](-$k * $SHEAR), [float]$k,
            [float]($ox + $k * $SHEAR_OFFSET), [float]$oy)

        $saved = $g.Save()
        $g.Transform = $m
        $g.DrawPath($pen, $path)

        # Dots inside the transform too, so they shear with the bars instead of
        # sitting upright among them. Upright coordinates, half-side = STROKE.
        foreach ($n in $names) {
            if ($SEG_DOTS.Contains($n)) {
                $s = $SEG_DOTS[$n]
                $g.FillRectangle($brush, ($s[0] - $STROKE), ($s[1] - $STROKE),
                    (2 * $STROKE), (2 * $STROKE))
            }
        }

        $g.Restore($saved)

        $m.Dispose()
    }

    $path.Dispose()

    foreach ($n in $names) {
        if ($SEG_DOTS.Contains($n)) {
            $s = $SEG_DOTS[$n]
            $c = PT $s[0] $s[1]
            $r = $STROKE * $k
            $g.FillRectangle($brush, ($c.X - $r), ($c.Y - $r), (2 * $r), (2 * $r))
        }
    }

    $pen.Dispose(); $brush.Dispose()
}

$SHEAR = [Math]::Tan($SLANT_DEG * [Math]::PI / 180.0)
$SHEAR_OFFSET = $SHEAR * $TOTAL_HEIGHT

# Segment A of the first line, allowing half a stroke so the top ink is not
# clipped. Every later line is one vpitch further down.
$y = $MARGIN + ($STROKE / 2.0) * $SCALE

foreach ($l in $LINES) {

    $adv = $l.P * $SCALE

    # How far the decimal point has to move left at this pitch to stay the same
    # fraction of the way across a gap that has shrunk. Zero at the design pitch.
    $gapUnits = $l.P - $CELL_WIDTH
    $dpShift = ($CELL_WIDTH + $DP_GAP_FRACTION * $gapUnits - $DP_X) * $SCALE

    $x = $MARGIN + $LABEL_W
    $prevX = $null

    foreach ($ch in $l.T.ToCharArray()) {

        # A space breaks the run: nothing for a following dot to attach to.
        if ($ch -eq ' ') {
            $x += $adv * $SPACE_PITCH_FRACTION
            $prevX = $null
            continue
        }

        # Anything the font cannot draw still consumes its cell, and likewise
        # leaves no cell behind for a dot to merge into.
        if (-not $MAP.ContainsKey($ch)) {
            $x += $adv
            $prevX = $null
            continue
        }

        # The decimal point and comma live in the gap after the preceding
        # character - that is the whole point of the DP/COMMA elements - so they
        # merge backward instead of taking a cell of their own, shifted to suit
        # the pitch.
        if (($ch -eq '.' -or $ch -eq ',') -and $null -ne $prevX) {
            Draw-Cell $MAP[$ch] ($prevX + $dpShift) $y $SCALE
            continue
        }

        # A dot with nothing before it - a leading ".5" - takes a blank cell of
        # its own, exactly as it would on the real display.
        if ($ch -eq '.' -or $ch -eq ',') {
            Draw-Cell $MAP[$ch] ($x + $dpShift) $y $SCALE
            $prevX = $x
            $x += $adv
            continue
        }

        Draw-Cell $MAP[$ch] $x $y $SCALE
        $prevX = $x
        $x += $adv
    }

    # Label sits beside the cap height, not beside the descenders.
    if ($l.Label) {
        $g.DrawString($l.Label, $labelFont, $labelBrush, $MARGIN, ($y + 30))
    }

    $y += $VPITCH * $SCALE
}

$g.Dispose()

try {
    $bmp.Save($OUT_SPECIMEN, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OUT_SPECIMEN  ($W x $H)"
}
catch {
    Write-Output "COULD NOT WRITE $OUT_SPECIMEN - it is probably open in another program"
}

$bmp.Dispose()
