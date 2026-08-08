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

# ---- geometry that this renderer needs (mirrors TalkRpnFont.kt) --------------
#
# EVERY length here is in D-to-A units: segment D to segment A is 100, and that
# same unit measures pitch and vpitch, horizontally and vertically alike. The one
# exception is $SCALE, which is the px-per-unit conversion, and it says so.

# The HP-01's authentic - and very airy - horizontal pitch. Each line below picks
# its own; this is only the reference.
$PITCH_DESIGN = 142.08

# Width of the cell itself. The difference between this and the pitch is the gap
# the decimal point has to live in.
$CELL_WIDTH = 58.47

# Where TalkRpnFont puts the decimal point / comma dot, at the design pitch.
$DP_X_DESIGN = 86.64

# A space is half a pitch wide, which is a display-layer decision rather than a
# font one: full-width spaces read as chasms in running text.
$SPACE_PITCH_FRACTION = 0.5

# Vertical pitch: baseline to baseline between specimen lines. Generous here
# because each line carries a label and wants air around it - the display itself
# will run much tighter.
$VPITCH = 205.0

# ---- where the decimal point actually goes -----------------------------------

# The dot does not belong to the cell it follows - it sits in the GAP between
# that cell and the next. TalkRpnFont pins it at x = 86.64, which is 28.17 past
# the right column, and that is only correct at the full advance of 142.08.
# Tighten the pitch and the gap shrinks underneath a dot that does not move, so
# it walks into the following glyph.
#
# Place it by proportion instead: hold it at the same fraction of the gap at
# every pitch. At the design pitch this reproduces the HP-01 position exactly, so
# the fix costs nothing where the design was already right.
$DP_GAP_FRACTION = ($DP_X_DESIGN - $CELL_WIDTH) / ($PITCH_DESIGN - $CELL_WIDTH)

# ---- what to render ----------------------------------------------------------

# The pitch bracket: ONE string at every pitch, so the only thing varying
# between lines is the pitch itself.
$PITCH_SAMPLE = "QUICK BROWN FOX 0123456789 42.9565"

# In D-to-A units. Bracketed close around 85-107, which is where caps look right,
# with the design pitch and a deliberately-too-tight 80 as anchors at either end.
# The cell is 58.47 wide, so the figure minus 58.47 is the clearance between
# neighbours: 80 leaves 21.5, and the ink only meets at 67.8.
$PITCHES = @(142.08, 120, 110, 105, 100, 95, 90, 85, 80)

$LINES = @()
foreach ($p in $PITCHES) {
    $LINES += @{ T = $PITCH_SAMPLE; P = $p; Label = ("{0:F0}" -f $p) }
}

# Then running text, calculator state and code, at the pitches in contention.
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; P = 107; Label = "TEXT 107" }
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; P = 92;  Label = "TEXT 92" }
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; P = 85;  Label = "TEXT 85" }
$LINES += @{ T = "988 ENTER 23 DIVIDE = 42.9565 (DEG) [SI] LASTX"; P = 100; Label = "RPN 100" }
$LINES += @{ T = "X: 1,234,567.89  Y: -6.02E23  Z: 0.0  T: 3.14159"; P = 100; Label = "STACK 100" }
$LINES += @{ T = "FOR (I = 0; I < 10; I++) { X[I] = A*B + C/D; }"; P = 100; Label = "CODE 100" }
$LINES += @{ T = 'PRINTF("%6.2F\N", &VALS[J] | MASK ^ 0X7E);'; P = 100; Label = "CODE 100" }

# One lower-case line kept for contrast - it is why we settled on caps.
$LINES += @{ T = "the quick brown fox jumps over the lazy dog"; P = 100; Label = "lower 100" }

# ---- page ---------------------------------------------------------------------

# The ONLY place a physical length enters: how big a unit is drawn. Everything
# else on this page is in units and follows from it.
$SCALE = 0.62                # px per D-to-A unit

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

    $pen = New-Object System.Drawing.Pen $LIT, ($STROKE * $k)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $brush = New-Object System.Drawing.SolidBrush $LIT

    function PT($x, $y) {
        New-Object System.Drawing.PointF (
            ($ox + ($x - $SHEAR * $y + $SHEAR_OFFSET) * $k), ($oy + $y * $k))
    }

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath

    foreach ($n in $names) {
        if ($SEG_LINES.Contains($n)) {
            $s = $SEG_LINES[$n]
            $path.StartFigure()
            $path.AddLine((PT $s[0] $s[1]), (PT $s[2] $s[3]))
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $s = $SEG_ARCS[$n]
            $pts = @()
            foreach ($p in (New-ArcPoints $s[0] $s[1] $s[2] $s[3] $s[4])) { $pts += PT $p[0] $p[1] }
            $path.StartFigure()
            $path.AddLines([System.Drawing.PointF[]]$pts)
        }
        elseif ($SEG_POLYS.Contains($n)) {
            $pts = @()
            foreach ($p in $SEG_POLYS[$n]) { $pts += PT $p[0] $p[1] }
            $path.StartFigure()
            $path.AddLines([System.Drawing.PointF[]]$pts)
        }
        elseif ($n -eq "COMMA") {
            $path.StartFigure()
            $path.AddLine((PT $COMMA_TAIL[0] $COMMA_TAIL[1]), (PT $COMMA_TAIL[2] $COMMA_TAIL[3]))
        }
    }

    if ($path.PointCount -gt 0) { $g.DrawPath($pen, $path) }
    $path.Dispose()

    foreach ($n in $names) {
        if ($SEG_DOTS.Contains($n)) {
            $s = $SEG_DOTS[$n]
            $c = PT $s[0] $s[1]
            $r = $STROKE * $k
            $g.FillEllipse($brush, ($c.X - $r), ($c.Y - $r), (2 * $r), (2 * $r))
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
    $dpShift = ($CELL_WIDTH + $DP_GAP_FRACTION * $gapUnits - $DP_X_DESIGN) * $SCALE

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
