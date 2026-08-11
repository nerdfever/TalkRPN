# The one place a TalkRPN cell gets drawn.
#
# Dot-sourced by every script that renders the font. It used to be copy-pasted
# into five of them, which meant every change to the pen had to be made five
# times and the reference sheets drifted from the app in between.
#
# Loads the geometry and glyph tables from make_font_reference.ps1 as well, so
# there is still exactly one mirror of TalkRpnGlyphs.kt.
#
# ---------------------------------------------------------------------------
# THE PEN DOES NOT ROTATE
# ---------------------------------------------------------------------------
# Stroking a path cuts every end square to the direction that path happens to
# run, so a diagonal gets an end sliced at 30 degrees while the bar beside it
# gets one cut flat. Put an X next to a Y and the difference is obvious, and
# where two segments meet at an angle the mitre throws a spike.
#
# A real display has neither, because a segment is a die on a rectangular grid.
# So each straight segment is built as a POLYGON here, swept by a nib of fixed
# orientation:
#
#   horizontal bars      a vertical nib   - the bar is STROKE tall, ends upright
#   everything else      a horizontal nib - the segment is STROKE wide measured
#                                           across, and its ends are cut flat
#
# One consequence worth knowing: a diagonal comes out about 14% thinner measured
# perpendicular than a bar is, because a horizontal nib meets it at an angle.
# That is what a fixed nib does, and what a die laid out on a grid looks like.
#
# The two hook arcs and the parenthesis keep a PERPENDICULAR thickness, offset
# either side of their centreline. They are corner pieces turning a horizontal
# bar into a vertical column, so a fixed nib would pinch them to nothing at one
# end and they are the one case where following the curve is right.

Add-Type -AssemblyName System.Drawing

# ---- geometry and glyph tables ------------------------------------------------

# Skipped when the caller IS make_font_reference.ps1, which has already defined
# these - otherwise dot-sourcing this from there would re-run its own top half.
if ($null -eq $SEG_LINES) {
    $refLines = Get-Content "$PSScriptRoot/make_font_reference.ps1"
    $cut = 0..($refLines.Count - 1) | Where-Object { $refLines[$_] -match '^# ---- drawing' } | Select-Object -First 1
    if ($null -eq $cut) { throw "could not find the drawing marker in make_font_reference.ps1" }
    Invoke-Expression (($refLines[0..($cut - 1)]) -join "`n")
}

$GLYPH_MAP = New-Object 'System.Collections.Generic.Dictionary[char, object]'
foreach ($e in $GLYPHS) { $GLYPH_MAP[[char]$e.C] = $e.S }

# Ink width of one slanted cell, at the default slant.
$SHEAR_DEFAULT = [Math]::Tan($SLANT_DEG * [Math]::PI / 180.0)
$SHEARED_WIDTH = $CELL_WIDTH + $SHEAR_DEFAULT * $TOTAL_HEIGHT

# How far each bar runs past its end, purely to close antialiasing seams.
$SEAM_OVERLAP = 0.0015

# ---- building one glyph's outline ---------------------------------------------

# Add a polygon, wound consistently.
#
# Winding matters because every lit segment goes into ONE path filled in Winding
# mode. A bar running right-to-left comes out wound the opposite way from one
# running left-to-right, and where two opposite-wound shapes overlap the winding
# numbers cancel and punch a HOLE. That showed up as small black notches exactly
# at the crossings in # and $.
function Add-Wound($path, $pts) {

    # Shoelace: negative means the points run the other way round.
    $area = 0.0
    for ($i = 0; $i -lt $pts.Count; $i++) {
        $j = ($i + 1) % $pts.Count
        $area += $pts[$i].X * $pts[$j].Y - $pts[$j].X * $pts[$i].Y
    }

    if ($area -lt 0) { [array]::Reverse($pts) }

    $path.AddPolygon([System.Drawing.PointF[]]$pts)
}

# A straight segment, as the parallelogram a fixed nib sweeps.
#
# END RULE, fourth design. The one-line version:
#
#   Bars extend half a stroke at every end except the hook handovers; diagonal
#   tips on the cell's outer edge are extruded vertically to the ink box;
#   curves get nothing.
#
# Why each clause is there:
#
#   Bars extend EVERYWHERE, not just at the outer boundary, because an interior
#   L-turn - the G bar turning into a stem at the x-height line, as in h, a, ?
#   and most of lower case - is notched at its outside corner when neither piece
#   extends. Extended, the two overshoots land exactly flush with each other's
#   ink edges: the notch fills and nothing pokes out. At colinear joints the
#   overshoot is buried. This is the old square cap, which got this right.
#
#   EXCEPT at the four hook handovers, where the bar meets an arc mid-run: an
#   extension there pokes past the arc's outer edge and puts a bump on the hook.
#
#   Diagonal tips landing on the top edge, baseline or descender line are
#   extruded VERTICALLY - the flat end face pushed out to the ink box - so a V's
#   foot and a W's vees reach the same line the columns do, instead of stopping
#   half a stroke short. Not extended along their own direction: that is the
#   overshoot that put a stub through M's apex. Interior diagonal ends still get
#   nothing, which is what keeps that apex a clean butt joint.
function Add-Bar($path, $x1, $y1, $x2, $y2, $w) {

    $half = $w / 2.0
    $e = 0.0005

    $len = [Math]::Sqrt(($x2 - $x1) * ($x2 - $x1) + ($y2 - $y1) * ($y2 - $y1))
    if ($len -eq 0) { return }

    $ux = ($x2 - $x1) / $len
    $uy = ($y2 - $y1) / $len

    $isHorizontal = [Math]::Abs($y2 - $y1) -lt $e
    $isVertical = [Math]::Abs($x2 - $x1) -lt $e

    # The four points where a bar hands over to a corner arc. An extension here
    # pokes past the arc's outer edge; the seam overlap alone joins them.
    function Test-HookPoint($x, $y) {
        return (([Math]::Abs($x - $HOOK_R) -lt $e) -and ([Math]::Abs($y) -lt $e)) -or
               (([Math]::Abs($x) -lt $e) -and ([Math]::Abs($y - $Y_F_TOP) -lt $e)) -or
               (([Math]::Abs($x) -lt $e) -and ([Math]::Abs($y - $Y_E_BOT) -lt $e)) -or
               (([Math]::Abs($x - $HOOK_R) -lt $e) -and ([Math]::Abs($y - $CELL_HEIGHT) -lt $e))
    }

    if ($isHorizontal -or $isVertical) {

        $ext1 = $SEAM_OVERLAP
        if (-not (Test-HookPoint $x1 $y1)) { $ext1 += $half }

        $ext2 = $SEAM_OVERLAP
        if (-not (Test-HookPoint $x2 $y2)) { $ext2 += $half }

        $x1 = $x1 - $ux * $ext1;  $y1 = $y1 - $uy * $ext1
        $x2 = $x2 + $ux * $ext2;  $y2 = $y2 + $uy * $ext2
    }
    else {
        # Diagonals and tails: seam overlap only, along the run...
        $x1 = $x1 - $ux * $SEAM_OVERLAP;  $y1 = $y1 - $uy * $SEAM_OVERLAP
        $x2 = $x2 + $ux * $SEAM_OVERLAP;  $y2 = $y2 + $uy * $SEAM_OVERLAP

        # ...plus the vertical extrusion at outer-edge tips. The rectangle runs
        # from just inside the tip to half a stroke past the boundary, the same
        # x-span as the tip's flat face.
        foreach ($end in @(@($x1, $y1), @($x2, $y2))) {

            $tx = $end[0]; $ty = $end[1]

            $outTop = [Math]::Abs($ty) -lt 0.01
            $outBase = [Math]::Abs($ty - $CELL_HEIGHT) -lt 0.01
            $outDesc = [Math]::Abs($ty - $TOTAL_HEIGHT) -lt 0.01

            if ($outTop) { $yFrom = -$half; $yTo = $SEAM_OVERLAP }
            elseif ($outBase -or $outDesc) { $yFrom = $ty - $SEAM_OVERLAP; $yTo = $ty + $half }
            else { continue }

            $pts = @(
                (New-Object System.Drawing.PointF ([float]($tx - $half), [float]$yFrom))
                (New-Object System.Drawing.PointF ([float]($tx + $half), [float]$yFrom))
                (New-Object System.Drawing.PointF ([float]($tx + $half), [float]$yTo))
                (New-Object System.Drawing.PointF ([float]($tx - $half), [float]$yTo))
            )
            Add-Wound $path $pts
        }
    }

    # Which way does the nib point? Across the segment's dominant axis, so a
    # horizontal bar gets height and everything else gets width.
    if ([Math]::Abs($x2 - $x1) -gt [Math]::Abs($y2 - $y1)) {
        $dx = 0.0; $dy = $half
    } else {
        $dx = $half; $dy = 0.0
    }

    $pts = @(
        (New-Object System.Drawing.PointF ([float]($x1 - $dx), [float]($y1 - $dy)))
        (New-Object System.Drawing.PointF ([float]($x2 - $dx), [float]($y2 - $dy)))
        (New-Object System.Drawing.PointF ([float]($x2 + $dx), [float]($y2 + $dy)))
        (New-Object System.Drawing.PointF ([float]($x1 + $dx), [float]($y1 + $dy)))
    )

    Add-Wound $path $pts
}

# A curved run, as a ribbon of constant PERPENDICULAR thickness either side of
# its centreline. Used only for the hooks and the parenthesis.
function Add-Ribbon($path, $pts, $w) {

    $half = $w / 2.0
    $left = @()
    $right = @()

    for ($i = 0; $i -lt $pts.Count; $i++) {

        # Local direction: the neighbouring points, or the one that exists at an end.
        $a = $pts[[Math]::Max(0, $i - 1)]
        $b = $pts[[Math]::Min($pts.Count - 1, $i + 1)]

        $dx = $b[0] - $a[0]
        $dy = $b[1] - $a[1]
        $len = [Math]::Sqrt($dx * $dx + $dy * $dy)
        if ($len -eq 0) { continue }

        # Normal, unit length.
        $nx = -$dy / $len
        $ny = $dx / $len

        $left += , @(($pts[$i][0] + $nx * $half), ($pts[$i][1] + $ny * $half))
        $right += , @(($pts[$i][0] - $nx * $half), ($pts[$i][1] - $ny * $half))
    }

    # Down one side and back the other.
    $outline = @()
    foreach ($p in $left) { $outline += New-Object System.Drawing.PointF ([float]$p[0], [float]$p[1]) }
    for ($i = $right.Count - 1; $i -ge 0; $i--) {
        $outline += New-Object System.Drawing.PointF ([float]$right[$i][0], [float]$right[$i][1])
    }

    Add-Wound $path $outline
}

<#
.SYNOPSIS
Draw one cell's lit segments.

.PARAMETER g       the Graphics to draw into
.PARAMETER names   segment names, as the glyph table lists them
.PARAMETER ox, oy  where the cell's origin lands, in canvas pixels
.PARAMETER k       pixels per cell width
.PARAMETER colour  the lit colour
.PARAMETER shear   tan of the slant; omit for the font's default
.PARAMETER stroke  bar width in cell widths; omit for the font's default
#>
function Draw-TalkRpnCell($g, $names, $ox, $oy, $k, $colour, $slantTan = $null, $barWidth = $null) {

    # NOT named $stroke and $shear. PowerShell variable names are case-INSENSITIVE,
    # so a parameter $stroke IS the script's $STROKE - defaulting it to $null
    # shadowed the real value, "$stroke = $STROKE" assigned null to itself, and
    # every bar came out zero-width. The glyphs simply did not appear.
    if ($null -eq $slantTan) { $slantTan = $SHEAR_DEFAULT }
    if ($null -eq $barWidth) { $barWidth = $STROKE }

    $shearOffset = $slantTan * $TOTAL_HEIGHT
    $brush = New-Object System.Drawing.SolidBrush $colour

    # Everything lit goes into ONE path, filled once. Overlaps are then painted
    # exactly as often as anything else - drawn separately, the second shape's
    # antialiased edge blends over the first and the seam reads as a bright line.
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.FillMode = [System.Drawing.Drawing2D.FillMode]::Winding

    foreach ($n in $names) {

        if ($SEG_LINES.Contains($n)) {
            $s = $SEG_LINES[$n]
            Add-Bar $path $s[0] $s[1] $s[2] $s[3] $barWidth
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $s = $SEG_ARCS[$n]
            Add-Ribbon $path (New-ArcPoints $s[0] $s[1] $s[2] $s[3] $s[4]) $barWidth
        }
        elseif ($SEG_POLYS.Contains($n)) {
            Add-Ribbon $path $SEG_POLYS[$n] $barWidth
        }
        elseif ($n -eq "COMMA") {
            Add-Bar $path $COMMA_TAIL[0] $COMMA_TAIL[1] $COMMA_TAIL[2] $COMMA_TAIL[3] $barWidth
        }

        # Dots are squares of side twice the stroke, sheared with everything else.
        if ($SEG_DOTS.Contains($n)) {
            $s = $SEG_DOTS[$n]
            $r = $barWidth
            $pts = @(
                (New-Object System.Drawing.PointF ([float]($s[0] - $r), [float]($s[1] - $r)))
                (New-Object System.Drawing.PointF ([float]($s[0] + $r), [float]($s[1] - $r)))
                (New-Object System.Drawing.PointF ([float]($s[0] + $r), [float]($s[1] + $r)))
                (New-Object System.Drawing.PointF ([float]($s[0] - $r), [float]($s[1] + $r)))
            )
            Add-Wound $path $pts
        }
    }

    if ($path.PointCount -gt 0) {

        # The shear goes on as a transform, so the whole outline leans together.
        # x' = k*x - k*shear*y + (ox + k*shearOffset),  y' = k*y
        $m = New-Object System.Drawing.Drawing2D.Matrix (
            [float]$k, [float]0, [float](-$k * $slantTan), [float]$k,
            [float]($ox + $k * $shearOffset), [float]$oy)

        $saved = $g.Save()
        $g.Transform = $m
        $g.FillPath($brush, $path)
        $g.Restore($saved)

        $m.Dispose()
    }

    $path.Dispose()
    $brush.Dispose()
}

# A whole string, at one pitch.
function Draw-TalkRpnText($g, $text, $ox, $oy, $k, $colour, $cellPitch = $null, $slantTan = $null, $barWidth = $null) {

    # $cellPitch, not $pitch, for the same case-insensitivity reason as above.
    if ($null -eq $cellPitch) { $cellPitch = $PITCH }

    $x = $ox

    foreach ($ch in $text.ToCharArray()) {

        if ($ch -eq ' ') { $x += $cellPitch * $k * 0.5; continue }
        if (-not $GLYPH_MAP.ContainsKey($ch)) { $x += $cellPitch * $k; continue }

        Draw-TalkRpnCell $g $GLYPH_MAP[$ch] $x $oy $k $colour $slantTan $barWidth
        $x += $cellPitch * $k
    }
}
