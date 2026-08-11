# Two candidate slants, side by side, because 2.5 degrees is not something anyone
# can judge from a number.
#
#   5.0   HP's own 5082-7400 datasheet, marked on the magnified character font
#         figure. That is the exact part in the HP-55 Dave photographed.
#   7.5   what this font uses now. Chosen BY EYE from the HP-01 reconstruction,
#         which itself measured 7.8 from photographs - so 7.5 was never a measured
#         figure, just a rounding of a different part's.
#
# The two are different parts, so both can be right about their own hardware. The
# question is which one this font should be.

$OUT_SLANT = "$PSScriptRoot\talkrpn_slant_comparison.png"

# ---- borrow the tables -------------------------------------------------------

$refLines = Get-Content "$PSScriptRoot\make_font_reference.ps1"
$cut = 0..($refLines.Count - 1) | Where-Object { $refLines[$_] -match '^# ---- drawing' } | Select-Object -First 1
if ($null -eq $cut) { throw "could not find the drawing marker in make_font_reference.ps1" }
Invoke-Expression (($refLines[0..($cut - 1)]) -join "`n")

$MAP = New-Object 'System.Collections.Generic.Dictionary[char, object]'
foreach ($e in $GLYPHS) { $MAP[[char]$e.C] = $e.S }

# ---- what to show -------------------------------------------------------------

$CANDIDATES = @(
    @{ Deg = 5.0; Name = "5.0 deg"; Note = "HP 5082-7400 datasheet - the HP-55's own part" }
    @{ Deg = 7.5; Name = "7.5 deg"; Note = "current, eyeballed from the HP-01 reconstruction (which measured 7.8)" }
)

# Digits show the lean most clearly; a word shows what it does to reading.
$SAMPLES = @("0123456789", "QUICK BROWN FOX")

# One big glyph each, with a true vertical drawn through it, so the lean can be
# seen against something that is definitely upright.
$BIG_GLYPH = "8"

$SCALE = 30.0                  # px per cell width, for the text rows
$BIG_SCALE = 96.0              # px per cell width, for the single glyph
$MARGIN = 46.0
$LABEL_W = 96.0
$ROW_GAP = 30.0
$SECTION_GAP = 54.0

Add-Type -AssemblyName System.Drawing

$labelFont = New-Object System.Drawing.Font "Consolas", 14
$noteFont = New-Object System.Drawing.Font "Consolas", 11
$titleFont = New-Object System.Drawing.Font "Consolas", 15

$LIT = [System.Drawing.Color]::FromArgb(255, 0, 0)
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(215, 215, 215))
$noteBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(135, 135, 135))
$plumbPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(70, 140, 155)), 1.4

# ---- geometry --------------------------------------------------------------

$rowH = $TOTAL_HEIGHT * $SCALE + $ROW_GAP
$bigH = $TOTAL_HEIGHT * $BIG_SCALE

$W = [int]($MARGIN * 2 + $LABEL_W + 16 * $PITCH * $SCALE)
$H = [int](110 + $SAMPLES.Count * $CANDIDATES.Count * $rowH + $SECTION_GAP + $bigH + $MARGIN + 40)

$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::Black)

# One glyph, at whatever slant is asked for. Same shape as everywhere else: the
# path is built upright and the shear applied as a transform, so the pen shears
# with it.
function Draw-Cell($names, $ox, $oy, $k, $shear) {

    $shearOffset = $shear * $TOTAL_HEIGHT

    $pen = New-Object System.Drawing.Pen $LIT, $STROKE
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Square
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Square
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Miter
    $pen.MiterLimit = 2.5
    $brush = New-Object System.Drawing.SolidBrush $LIT

    function UP($x, $y) { New-Object System.Drawing.PointF ([float]$x, [float]$y) }

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

        $m = New-Object System.Drawing.Drawing2D.Matrix (
            [float]$k, [float]0, [float](-$k * $shear), [float]$k,
            [float]($ox + $k * $shearOffset), [float]$oy)

        $saved = $g.Save()
        $g.Transform = $m
        $g.DrawPath($pen, $path)

        # Dots inside the transform too, so they shear with the bars.
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

    $pen.Dispose(); $brush.Dispose()
}

# A whole string at one slant.
function Draw-Line($text, $ox, $oy, $k, $shear) {

    $x = $ox

    foreach ($ch in $text.ToCharArray()) {

        if ($ch -eq ' ') { $x += $PITCH * $k * 0.5; continue }
        if (-not $MAP.ContainsKey($ch)) { $x += $PITCH * $k; continue }

        Draw-Cell $MAP[$ch] $x $oy $k $shear
        $x += $PITCH * $k
    }
}

# ---- draw --------------------------------------------------------------------

$g.DrawString("Slant: 5.0 from HP's datasheet against the 7.5 in use. Same stroke, same glyphs.",
    $titleFont, $labelBrush, $MARGIN, 26)

$y = 92.0

foreach ($sample in $SAMPLES) {
    foreach ($c in $CANDIDATES) {

        $shear = [Math]::Tan($c.Deg * [Math]::PI / 180.0)

        Draw-Line $sample ($MARGIN + $LABEL_W) $y $SCALE $shear

        $g.DrawString($c.Name, $labelFont, $labelBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE * 0.35))

        $y += $rowH
    }

    $y += 12
}

$y += $SECTION_GAP - 12

# The single glyph pair, each with a true vertical through the middle of its cell
# so the lean has something upright to be judged against.
$g.DrawString("the same 8, with a true vertical through each", $noteFont, $noteBrush, $MARGIN, ($y - 26))

$x = $MARGIN + $LABEL_W

foreach ($c in $CANDIDATES) {

    $shear = [Math]::Tan($c.Deg * [Math]::PI / 180.0)
    $shearOffset = $shear * $TOTAL_HEIGHT

    Draw-Cell $MAP[[char]$BIG_GLYPH] $x $y $BIG_SCALE $shear

    # Plumb line through the cell's centre axis at the baseline.
    $px = $x + (0.5 - $shear * $CELL_HEIGHT + $shearOffset) * $BIG_SCALE
    $g.DrawLine($plumbPen, [float]$px, [float]($y - 14), [float]$px, [float]($y + $bigH + 14))

    $g.DrawString($c.Name, $labelFont, $labelBrush, $x, ($y + $bigH + 22))

    $x += ($SHEARED_WIDTH + 2.4) * $BIG_SCALE
}

# The notes go one per line at the left margin, not under their own column: side
# by side they are far longer than the glyphs are wide and simply overprint.
$noteY = $y + $bigH + 56

foreach ($c in $CANDIDATES) {
    $g.DrawString(("{0}  -  {1}" -f $c.Name, $c.Note), $noteFont, $noteBrush, $MARGIN, $noteY)
    $noteY += 20
}

$g.Dispose()

try {
    $bmp.Save($OUT_SLANT, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OUT_SLANT  ($W x $H)"
}
catch {
    Write-Output "COULD NOT WRITE $OUT_SLANT - it is probably open in another program"
}

$bmp.Dispose()
