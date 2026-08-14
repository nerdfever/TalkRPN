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

# An axis-aligned bar, as the rectangle a fixed nib sweeps.
#
# END RULE, final - Dave chose the DIE policy (2026-08-11):
#
#   An end extends half a stroke ONLY when a perpendicular axis-aligned lit
#   segment shares the endpoint. Everything else ends flat at the centreline,
#   exactly as the separate dies on a real DL-3422 do.
#
# The support case is what fills every corner and L-turn: at 7's top-right, A2
# and B each overshoot half a stroke and land exactly flush with each other's
# ink edges; same at h's shoulder. Where there is no such partner the end is a
# die edge: a lone 1 really is half a stroke shorter than the 0 beside it, v's
# foot is flat with the diagonal melding into it, and lowercase tops sit dead
# on the x-height line - all as on the real hardware.
#
# Rejected on the way here, each after Dave caught its artefact: blanket
# extension (eaves hanging past diagonals, spurs on n), boundary extension
# (heels under v and w), corner patches (nubs on lone tips), slope extension of
# free diagonal tips (kinked boots).
function Add-AxisBar($path, $x1, $y1, $x2, $y2, $w, $extend1, $extend2) {

    $half = $w / 2.0

    $len = [Math]::Sqrt(($x2 - $x1) * ($x2 - $x1) + ($y2 - $y1) * ($y2 - $y1))
    if ($len -eq 0) { return }

    $ux = ($x2 - $x1) / $len
    $uy = ($y2 - $y1) / $len

    $ext1 = $SEAM_OVERLAP
    if ($extend1) { $ext1 += $half }

    $ext2 = $SEAM_OVERLAP
    if ($extend2) { $ext2 += $half }

    $x1 = $x1 - $ux * $ext1;  $y1 = $y1 - $uy * $ext1
    $x2 = $x2 + $ux * $ext2;  $y2 = $y2 + $uy * $ext2

    # The nib points across the bar's axis.
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

# A diagonal (or tail): the parallelogram a horizontal nib sweeps. Flat
# horizontal end faces exactly at the endpoints - a die, nothing added.
function Add-Diagonal($path, $x1, $y1, $x2, $y2, $w) {

    $half = $w / 2.0

    $len = [Math]::Sqrt(($x2 - $x1) * ($x2 - $x1) + ($y2 - $y1) * ($y2 - $y1))
    if ($len -eq 0) { return }

    $ux = ($x2 - $x1) / $len
    $uy = ($y2 - $y1) / $len

    $ax = $x1 - $ux * $SEAM_OVERLAP;  $ay = $y1 - $uy * $SEAM_OVERLAP
    $bx = $x2 + $ux * $SEAM_OVERLAP;  $by = $y2 + $uy * $SEAM_OVERLAP

    $pts = @(
        (New-Object System.Drawing.PointF ([float]($ax - $half), [float]$ay))
        (New-Object System.Drawing.PointF ([float]($bx - $half), [float]$by))
        (New-Object System.Drawing.PointF ([float]($bx + $half), [float]$by))
        (New-Object System.Drawing.PointF ([float]($ax + $half), [float]$ay))
    )

    Add-Wound $path $pts
}

# The two ribbon shapes and everything below are unchanged.
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

    # Every lit segment's endpoints, tagged with the segment's axis, for the
    # support and free/shared tests. H = horizontal bar, V = vertical bar,
    # D = diagonal, C = curve.
    $endpoints = @()

    foreach ($n in $names) {
        if ($SEG_LINES.Contains($n)) {
            $sl = $SEG_LINES[$n]
            $ax = "D"
            if ([Math]::Abs($sl[1] - $sl[3]) -lt 0.0005) { $ax = "H" }
            elseif ([Math]::Abs($sl[0] - $sl[2]) -lt 0.0005) { $ax = "V" }
            $endpoints += , @($sl[0], $sl[1], $ax)
            $endpoints += , @($sl[2], $sl[3], $ax)
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $a = $SEG_ARCS[$n]
            foreach ($deg in @($a[3], $a[4])) {
                $th = $deg * [Math]::PI / 180.0
                $endpoints += , @(($a[0] + $a[2] * [Math]::Cos($th)), ($a[1] + $a[2] * [Math]::Sin($th)), "C")
            }
        }
        elseif ($SEG_POLYS.Contains($n)) {
            $poly = $SEG_POLYS[$n]
            $endpoints += , @($poly[0][0], $poly[0][1], "C")
            $endpoints += , @($poly[-1][0], $poly[-1][1], "C")
        }
    }

    # Does a lit segment of the given axis (other than one instance of me) end
    # at this point?
    function Test-PartnerAxis($x, $y, $axes, $selfAxis) {
        $selfSeen = $false
        foreach ($pt in $endpoints) {
            if (([Math]::Abs($pt[0] - $x) -lt 0.001) -and ([Math]::Abs($pt[1] - $y) -lt 0.001)) {
                if ((-not $selfSeen) -and ($pt[2] -eq $selfAxis)) { $selfSeen = $true; continue }
                if ($axes -contains $pt[2]) { return $true }
            }
        }
        return $false
    }



    # The four points where a bar hands over to a corner arc: never extend into
    # an arc, it puts a bump on the hook's outer edge.
    function Test-HookPoint($x, $y) {
        $he = 0.0005
        return (([Math]::Abs($x - $HOOK_R) -lt $he) -and ([Math]::Abs($y) -lt $he)) -or
               (([Math]::Abs($x) -lt $he) -and ([Math]::Abs($y - $Y_F_TOP) -lt $he)) -or
               (([Math]::Abs($x) -lt $he) -and ([Math]::Abs($y - $Y_E_BOT) -lt $he)) -or
               (([Math]::Abs($x - $HOOK_R) -lt $he) -and ([Math]::Abs($y - $CELL_HEIGHT) -lt $he))
    }

    # Should this bar end extend? Only into a perpendicular partner - and never
    # into a hook arc, where the overshoot would sit proud of the curve.
    function Test-BarExtend($x, $y, $selfAxis, $perpAxis) {

        if (Test-HookPoint $x $y) { return $false }

        return Test-PartnerAxis $x $y @($perpAxis) $selfAxis
    }

    foreach ($n in $names) {

        if ($SEG_LINES.Contains($n)) {
            $sl = $SEG_LINES[$n]
            $ee = 0.0005

            $isH = [Math]::Abs($sl[1] - $sl[3]) -lt $ee
            $isV = [Math]::Abs($sl[0] - $sl[2]) -lt $ee

            if ($isH -or $isV) {
                $selfAx = "V"; $perpAx = "H"
                if ($isH) { $selfAx = "H"; $perpAx = "V" }

                $e1 = Test-BarExtend $sl[0] $sl[1] $selfAx $perpAx
                $e2 = Test-BarExtend $sl[2] $sl[3] $selfAx $perpAx

                Add-AxisBar $path $sl[0] $sl[1] $sl[2] $sl[3] $barWidth $e1 $e2

                # THE MITRE DIAMOND. A horizontal bar's end face is vertical and
                # a diagonal's is horizontal, so where the two share an endpoint
                # they touch only at the centre and the diagonal's shoulder pokes
                # half a stroke past the bar's flat end - the notch Dave caught
                # at the corners of Z, z, s, e, a and the top-left of &.
                #
                # Extending the bar instead brings back the eave. What a stroked
                # join would supply here is a mitre: the diamond spanning both
                # end faces. Its 45-degree upper edge chamfers the bar's corner
                # into the diagonal's shoulder; its lower half is buried under
                # the diagonal's body.
                #
                # Only this pairing needs it. A vertical bar's end face is
                # horizontal - identical to the diagonal's - so column-diagonal
                # and diagonal-diagonal junctions already meet flush, and adding
                # anything there is what earlier designs got wrong.
                if ($isH) {
                    $half = $barWidth / 2.0
                    foreach ($endPt in @(@($sl[0], $sl[1]), @($sl[2], $sl[3]))) {
                        if (Test-PartnerAxis $endPt[0] $endPt[1] @("D") "H") {
                            $mx = $endPt[0]; $my = $endPt[1]
                            $quad = @(
                                (New-Object System.Drawing.PointF ([float]$mx, [float]($my - $half)))
                                (New-Object System.Drawing.PointF ([float]($mx + $half), [float]$my))
                                (New-Object System.Drawing.PointF ([float]$mx, [float]($my + $half)))
                                (New-Object System.Drawing.PointF ([float]($mx - $half), [float]$my))
                            )
                            Add-Wound $path $quad
                        }
                    }
                }
            }
            else {
                Add-Diagonal $path $sl[0] $sl[1] $sl[2] $sl[3] $barWidth
            }
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $sl = $SEG_ARCS[$n]
            Add-Ribbon $path (New-ArcPoints $sl[0] $sl[1] $sl[2] $sl[3] $sl[4]) $barWidth
        }
        elseif ($SEG_POLYS.Contains($n)) {
            Add-Ribbon $path $SEG_POLYS[$n] $barWidth
        }
        elseif ($n -eq "COMMA") {
            Add-Diagonal $path $COMMA_TAIL[0] $COMMA_TAIL[1] $COMMA_TAIL[2] $COMMA_TAIL[3] $barWidth
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
