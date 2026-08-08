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

# Cell pitch in cell units, at the HP-01's authentic - and very airy - spacing.
# Every line below scales this by its own factor.
$ADVANCE = 142.08

# Width of the cell itself. The difference between this and the advance is the
# gap the decimal point has to live in.
$CELL_WIDTH = 58.47

# Where TalkRpnFont puts the decimal point / comma dot.
$DP_X_DESIGN = 86.64

# A space is half a cell wide, which is a display-layer decision rather than a
# font one: full-cell spaces read as chasms in running text.
$SPACE_FACTOR = 0.5

# ---- where the decimal point actually goes -----------------------------------

# The dot does not belong to the cell it follows - it sits in the GAP between
# that cell and the next. TalkRpnFont pins it at x = 86.64, which is 28.17 past
# the right column, and that is only correct at the full advance of 142.08.
# Tighten the pitch and the gap shrinks underneath a dot that does not move, so
# it walks into the following glyph.
#
# Place it by proportion instead: hold it at the same fraction of the gap at
# every pitch. At factor 1.00 this reproduces the design position exactly, so
# the fix costs nothing where the design was already right.
$DP_GAP_FRACTION = ($DP_X_DESIGN - $CELL_WIDTH) / ($ADVANCE - $CELL_WIDTH)

# ---- what to render ----------------------------------------------------------

# The pitch bracket: ONE string at every pitch, so the only thing varying
# between lines is the pitch itself.
$PITCH_SAMPLE = "QUICK BROWN FOX 0123456789 42.9565"

# Bracketed close around 0.60-0.75, which is where caps look right, with 1.00
# and 0.50 kept as anchors at either end.
$PITCHES = @(1.00, 0.85, 0.80, 0.75, 0.70, 0.65, 0.60, 0.55, 0.50)

$LINES = @()
foreach ($p in $PITCHES) {
    $LINES += @{ T = $PITCH_SAMPLE; F = $p; Label = ("{0:F2}" -f $p) }
}

# Then running text, calculator state and code, at the pitches in contention.
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; F = 0.75; Label = "TEXT .75" }
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; F = 0.65; Label = "TEXT .65" }
$LINES += @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"; F = 0.60; Label = "TEXT .60" }
$LINES += @{ T = "988 ENTER 23 DIVIDE = 42.9565 (DEG) [SI] LASTX"; F = 0.70; Label = "RPN .70" }
$LINES += @{ T = "X: 1,234,567.89  Y: -6.02E23  Z: 0.0  T: 3.14159"; F = 0.70; Label = "STACK .70" }
$LINES += @{ T = "FOR (I = 0; I < 10; I++) { X[I] = A*B + C/D; }"; F = 0.70; Label = "CODE .70" }
$LINES += @{ T = 'PRINTF("%6.2F\N", &VALS[J] | MASK ^ 0X7E);'; F = 0.70; Label = "CODE .70" }

# One lower-case line kept for contrast - it is why we settled on caps.
$LINES += @{ T = "the quick brown fox jumps over the lazy dog"; F = 0.70; Label = "lower .70" }

# ---- page ---------------------------------------------------------------------

$SCALE = 0.62                # px per cell unit
$MARGIN = 40.0
$LABEL_W = 110.0
$LINE_GAP = 26.0

# ---- render -------------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$LIT = [System.Drawing.Color]::FromArgb(232, 24, 16)
$labelFont = New-Object System.Drawing.Font "Consolas", 13
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(130, 130, 130))

# Width from the longest line at its own pitch.
$maxUnits = 0.0
foreach ($l in $LINES) {
    $u = $l.T.Length * $ADVANCE * $l.F + 100
    if ($u -gt $maxUnits) { $maxUnits = $u }
}
$W = [int]($maxUnits * $SCALE + 2 * $MARGIN + $LABEL_W)
$lineUnits = ($TOTAL_HEIGHT + $STROKE * 2)
$H = [int]($LINES.Count * ($lineUnits * $SCALE + $LINE_GAP) + 2 * $MARGIN)

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

$y = $MARGIN
foreach ($l in $LINES) {

    $adv = $ADVANCE * $l.F * $SCALE

    # How far the decimal point has to move left at this pitch to stay the same
    # fraction of the way across a gap that has shrunk. Zero at factor 1.00.
    $gapUnits = $ADVANCE * $l.F - $CELL_WIDTH
    $dpShift = ($CELL_WIDTH + $DP_GAP_FRACTION * $gapUnits - $DP_X_DESIGN) * $SCALE

    $x = $MARGIN + $LABEL_W
    $prevX = $null

    foreach ($ch in $l.T.ToCharArray()) {

        # A space breaks the run: nothing for a following dot to attach to.
        if ($ch -eq ' ') {
            $x += $adv * $SPACE_FACTOR
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

    if ($l.Label) {
        $g.DrawString($l.Label, $labelFont, $labelBrush, $MARGIN, ($y + 30))
    }

    $y += $lineUnits * $SCALE + $LINE_GAP
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
