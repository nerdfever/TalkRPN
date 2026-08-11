# Compare candidate LED reds side by side, as flat swatches AND as lit segments,
# because a colour that looks right as a block does not always look right as a
# thin stroke on black - which is the only way this font is ever seen.
#
# The candidates come from the datasheet peak of 655-660 nm, which is outside
# every display gamut, so each is a different answer to "closest reachable".
#
# Reuses the geometry and glyph tables from make_font_reference.ps1 the same way
# make_specimen.ps1 does, so the glyphs here cannot drift from the font.

$OUT_COMPARISON = "$PSScriptRoot\talkrpn_led_colours.png"

# ---- borrow the tables -------------------------------------------------------

$refLines = Get-Content "$PSScriptRoot\make_font_reference.ps1"
$cut = 0..($refLines.Count - 1) | Where-Object { $refLines[$_] -match '^# ---- drawing' } | Select-Object -First 1
if ($null -eq $cut) { throw "could not find the drawing marker in make_font_reference.ps1" }
Invoke-Expression (($refLines[0..($cut - 1)]) -join "`n")

$MAP = New-Object 'System.Collections.Generic.Dictionary[char, object]'
foreach ($e in $GLYPHS) { $MAP[[char]$e.C] = $e.S }

# ---- the candidates ----------------------------------------------------------

# Named so the picture explains itself without the commit message next to it.
$CANDIDATES = @(
    @{ Hex = "FF0000"; R = 255; G =   0; B =  0
       Name = "#FF0000   clip to maximum saturation"
       Note = "deltaE2000 7.8 from the true 655 nm - the closer of the two" }

    @{ Hex = "FF0052"; R = 255; G =   0; B = 82
       Name = "#FF0052   desaturate along constant dominant wavelength"
       Note = "deltaE2000 17.0 - genuinely 655 nm, but at 63% purity" }

    @{ Hex = "E81810"; R = 232; G =  24; B = 16
       Name = "#E81810   what the code uses today"
       Note = "a little green and blue mixed in, so slightly duller and browner" }
)

$SAMPLE = "42.9565"

# ---- layout, in pixels --------------------------------------------------------

$SCALE = 26.0                  # px per cell width
$MARGIN = 46.0
$ROW_GAP = 40.0
$SWATCH_W = 150.0
$SWATCH_GAP = 26.0
$LABEL_DROP = 18.0             # from the top of the row to the caption baseline
$TITLE_H = 76.0

$VPITCH = 3.4                  # between candidate rows, in cell widths

Add-Type -AssemblyName System.Drawing

$titleFont = New-Object System.Drawing.Font "Consolas", 15
$labelFont = New-Object System.Drawing.Font "Consolas", 13
$noteFont = New-Object System.Drawing.Font "Consolas", 11

$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(210, 210, 210))
$noteBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(130, 130, 130))

$TITLE = "655-660 nm is outside every display gamut. These are the candidates for closest."

# Width has to clear the widest thing on the sheet, and that is not always the
# artwork - the title and the captions are longer than the digits here. Measure
# them rather than guessing and cropping the end off.
$measurer = [System.Drawing.Graphics]::FromImage((New-Object System.Drawing.Bitmap 1, 1))

$textW = ($measurer.MeasureString($TITLE, $titleFont)).Width

foreach ($c in $CANDIDATES) {
    $textW = [Math]::Max($textW, ($measurer.MeasureString($c.Name, $labelFont)).Width)
    $textW = [Math]::Max($textW, ($measurer.MeasureString($c.Note, $noteFont)).Width)
}

$measurer.Dispose()

$rowH = $VPITCH * $SCALE + $ROW_GAP
$artW = $SWATCH_W + $SWATCH_GAP + ($SAMPLE.Length + 1) * $PITCH * $SCALE

$W = [int]($MARGIN * 2 + [Math]::Max($artW, $textW))
$H = [int]($TITLE_H + $CANDIDATES.Count * $rowH + $MARGIN)

$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

# Black, because that is what the display is and saturated reds read completely
# differently against white.
$g.Clear([System.Drawing.Color]::Black)

$SHEAR = [Math]::Tan($SLANT_DEG * [Math]::PI / 180.0)
$SHEAR_OFFSET = $SHEAR * $TOTAL_HEIGHT

# One glyph, one stroked path, so overlapping segments do not blend twice.
function Draw-Cell($names, $ox, $oy, $k, $colour) {

    $pen = New-Object System.Drawing.Pen $colour, ($STROKE * $k)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $brush = New-Object System.Drawing.SolidBrush $colour

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

# ---- draw ---------------------------------------------------------------------

$g.DrawString($TITLE, $titleFont, $labelBrush, $MARGIN, 22)

$y = $TITLE_H

foreach ($c in $CANDIDATES) {

    $colour = [System.Drawing.Color]::FromArgb($c.R, $c.G, $c.B)

    # The flat swatch, for judging the colour itself.
    $swatchBrush = New-Object System.Drawing.SolidBrush $colour
    $g.FillRectangle($swatchBrush, $MARGIN, $y, $SWATCH_W, ($CELL_HEIGHT * $SCALE))
    $swatchBrush.Dispose()

    # The same colour as lit segments, which is how it will actually be seen.
    $x = $MARGIN + $SWATCH_W + $SWATCH_GAP
    $prevX = $null

    $gapUnits = $PITCH - $CELL_WIDTH
    $dpShift = ($CELL_WIDTH + $DP_GAP_FRACTION * $gapUnits - $DP_X) * $SCALE

    foreach ($ch in $SAMPLE.ToCharArray()) {

        if (-not $MAP.ContainsKey($ch)) { $x += $PITCH * $SCALE; $prevX = $null; continue }

        if (($ch -eq '.' -or $ch -eq ',') -and $null -ne $prevX) {
            Draw-Cell $MAP[$ch] ($prevX + $dpShift) $y $SCALE $colour
            continue
        }

        Draw-Cell $MAP[$ch] $x $y $SCALE $colour
        $prevX = $x
        $x += $PITCH * $SCALE
    }

    # Captions under the swatch.
    $g.DrawString($c.Name, $labelFont, $labelBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE + $LABEL_DROP))
    $g.DrawString($c.Note, $noteFont, $noteBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE + $LABEL_DROP + 20))

    $y += $rowH
}

$g.Dispose()

try {
    $bmp.Save($OUT_COMPARISON, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OUT_COMPARISON  ($W x $H)"
}
catch {
    Write-Output "COULD NOT WRITE $OUT_COMPARISON - it is probably open in another program"
}

$bmp.Dispose()
