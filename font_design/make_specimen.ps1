# Render sample text in the TalkRPN font - lit segments on black, the way the
# display will actually look. Pangrams, digits, and code, because the font will
# be asked to show error words, register names and units, not just numbers.
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

# ---- specimen setup ----------------------------------------------------------

# Cell pitch in cell units. TalkRpnFont.ADVANCE is 142.08, the HP-01's
# authentic (and very airy) spacing; most lines here use a tighter factor to
# see what text reads like.
$ADVANCE = 142.08
$PITCH_FACTOR = 0.75

# A space is half a cell wide, which is a display-layer decision rather than a
# font one: full-cell spaces read as chasms in running text.
$SPACE_FACTOR = 0.5

$LINES = @(
    @{ T = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG 0123456789"; F = 0.75 }
    @{ T = "The Quick Brown Fox Jumps Over The Lazy Dog 0123456789"; F = 0.60 }
    @{ T = "the quick brown fox jumps over the lazy dog 0123456789"; F = 0.50 }
    @{ T = "988 ENTER 23 DIVIDE = 42.9565 (DEG) [SI] LASTX"; F = 0.60 }
    @{ T = "for (i = 0; i < 10; i++) { x[i] = a*b + c/d; }"; F = 0.60 }
    @{ T = 'printf("%6.2f\n", &vals[j] | mask ^ 0x7e);'; F = 0.50 }
    @{ T = "3.14159265 -1.5e-6 6.02e23 42.9565"; F = 0.60 }
    @{ T = "3.14159265 -1.5e-6 6.02e23 42.9565"; F = 0.50 }
)

$SCALE = 0.62                # px per cell unit
$MARGIN = 40.0
$LINE_GAP = 26.0
$CAPTION_H = 0.0

# ---- render -------------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$LIT = [System.Drawing.Color]::FromArgb(232, 24, 16)

# Width from the longest line at its own pitch.
$maxUnits = 0.0
foreach ($l in $LINES) {
    $u = $l.T.Length * $ADVANCE * $l.F + 100
    if ($u -gt $maxUnits) { $maxUnits = $u }
}
$W = [int]($maxUnits * $SCALE + 2 * $MARGIN)
$lineUnits = ($TOTAL_HEIGHT + $STROKE * 2)
$H = [int]($LINES.Count * ($lineUnits * $SCALE + $LINE_GAP) + 2 * $MARGIN)

$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Black)

# One glyph, one stroked path - same approach as everywhere else.
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
    $x = $MARGIN
    $prevX = $null

    foreach ($ch in $l.T.ToCharArray()) {

        if ($ch -eq ' ') { $x += $adv * $SPACE_FACTOR; $prevX = $null; continue }
        if (-not $MAP.ContainsKey($ch)) { $x += $adv; continue }

        # The decimal point and comma live IN the preceding character's cell -
        # that is the whole point of the DP/COMMA elements - so they merge
        # backward instead of taking a cell of their own.
        if (($ch -eq '.' -or $ch -eq ',') -and $null -ne $prevX) {
            Draw-Cell $MAP[$ch] $prevX $y $SCALE
            continue
        }

        Draw-Cell $MAP[$ch] $x $y $SCALE
        $prevX = $x
        $x += $adv
    }

    $y += $lineUnits * $SCALE + $LINE_GAP
}

$g.Dispose()
$bmp.Save($OUT_SPECIMEN, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "wrote $OUT_SPECIMEN  ($W x $H)"


