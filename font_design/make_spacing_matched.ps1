# B against C at MATCHED line lengths.
#
# One model covers both policies. Every cell has a WIDTH and is followed by a
# GAP; a glyph's ink is centred on its cell.
#
#   C  width = the glyph's real ink width (right minus left lit centreline)
#   B  width = 1.0 for every glyph, whatever its ink
#
# So B is C with the widths clamped, and both share one knob - the gap. B's
# familiar "pitch" is just gap + 1. Writing it this way means a space, a word
# boundary and a letter boundary are defined identically in both, which is the
# only way the comparison is honest.
#
# They can therefore only differ where ink is narrower than the cell - most
# lower case, the digit 1, i j l, and punctuation. Comparing at the same gap
# would be unfair: C would always be shorter and would win on compactness
# alone. So each pair below is solved to the same total ink length, and the
# only question left is which distributes that length better.
#
# The spacings are solved numerically rather than algebraically - one bisection
# per row, which cannot go subtly wrong the way a hand-derived formula can.

$OUT_MATCHED = "$PSScriptRoot\talkrpn_spacing_matched.png"

. "$PSScriptRoot/talkrpn_render.ps1"

# ---- what to set --------------------------------------------------------------

# Mixed case is the only case that matters here: all-caps is nearly all
# full-width, where B and C agree by construction.
#
# The digit 1 is the one digit with no ink width at all, and that is where the
# policies part on figures - but only if the sample contains BOTH adjacent full
# digits and adjacent 1s. A number that merely alternates (31,415.19) hides the
# difference completely, because in B every gap is then the around-a-1 gap and
# B's tight full-digit gap never occurs. So the numbers below deliberately mix
# runs of full digits with runs of 1s.
$SAMPLES = @(
    "Hello World 2,345.67",
    "11,190.11 to 2,345.67"
)

# A space is an ordinary cell with no ink, this wide, taking a gap on each side
# like anything else. Mirrors TalkRpnFont.
$SPACE_WIDTH = 0.6

# How many matched lengths to show, and how far past the ends of the plausible
# range to reach. The range itself is measured, not guessed: it runs from C to
# B at the gap used before.
$STEPS = 5
$OVERSHOOT = 0.10

$REFERENCE_GAP = 0.7        # what both policies used in the first comparison

$SCALE = 24.0
$MARGIN = 46.0
$LABEL_W = 168.0
$ROW_GAP = 16.0
$PAIR_GAP = 28.0
$SAMPLE_GAP = 44.0

# ---- glyph extents -------------------------------------------------------------

# Lit centreline extent of a mask, ignoring DP and COMMA: those live in the gap
# between cells by design and merge into the preceding cell in both policies, so
# counting them would make a glyph look wider than it sets.
function Get-GlyphExtents($names) {

    $lo = [double]::PositiveInfinity
    $hi = [double]::NegativeInfinity

    foreach ($n in $names) {

        # The gap-dwellers live outside the glyph, and a DESCENDER tucks under
        # its neighbour rather than pushing it away - neither counts as width.
        if (("DP", "COMMA", "M", "N", "O") -contains $n) { continue }

        $xs = @()

        if ($SEG_LINES.Contains($n)) {
            $sl = $SEG_LINES[$n]
            $xs = @($sl[0], $sl[2])
        }
        elseif ($SEG_ARCS.Contains($n)) {
            $a = $SEG_ARCS[$n]
            foreach ($pt in (New-ArcPoints $a[0] $a[1] $a[2] $a[3] $a[4])) { $xs += $pt[0] }
        }
        elseif ($SEG_POLYS.Contains($n)) {
            foreach ($pt in $SEG_POLYS[$n]) { $xs += $pt[0] }
        }
        elseif ($SEG_DOTS.Contains($n)) {
            $xs = @($SEG_DOTS[$n][0])
        }

        foreach ($x in $xs) {
            if ($x -lt $lo) { $lo = $x }
            if ($x -gt $hi) { $hi = $x }
        }
    }

    if ($lo -gt $hi) { return $null }
    return @($lo, $hi)
}

# The text as cells: @(names, extents) with '.' and ',' merged backward, and
# spaces as @($null, $null).
function Get-Cells($text) {

    $cells = @()

    foreach ($ch in $text.ToCharArray()) {

        if ($ch -eq ' ') { $cells += , @($null, $null); continue }

        if ((($ch -eq '.') -or ($ch -eq ',')) -and ($cells.Count -gt 0) -and ($null -ne $cells[-1][0])) {
            $dot = "DP"; if ($ch -eq ',') { $dot = "COMMA" }
            $cells[-1][0] = $cells[-1][0] + @($dot)
            continue
        }

        if (-not $GLYPH_MAP.ContainsKey($ch)) { $cells += , @($null, $null); continue }

        $names = $GLYPH_MAP[$ch]
        $cells += , @($names, (Get-GlyphExtents $names))
    }

    return $cells
}

# ---- the layout ------------------------------------------------------------------
#
# One function for both policies: $clampWidths turns C into B. Returns
# @(placements, inkLength) where a placement is @(names, originX) in CELL units,
# already shifted so the leftmost ink sits at zero. Ink length is left ink edge
# to right ink edge, which is what "the same line length" has to mean; cell
# origins are an internal detail that differs between the policies.

function Get-Layout($cells, $gap, $clampWidths) {

    $out = @()
    $lo = [double]::PositiveInfinity
    $hi = [double]::NegativeInfinity

    # A pen walks the line, landing on the centre of each cell. Between cells it
    # moves half of each width plus the gap.
    $pen = 0.0
    $prevHalf = $null

    foreach ($cell in $cells) {

        # A space is a cell with no ink, so it needs no case of its own.
        $isSpace = ($null -eq $cell[0])

        # Ink extent, and the cell width that carries it.
        $inkW = 0.0
        $inkMid = 0.5

        if ((-not $isSpace) -and ($null -ne $cell[1])) {
            $inkW = $cell[1][1] - $cell[1][0]
            $inkMid = ($cell[1][0] + $cell[1][1]) / 2.0
        }

        $cellW = $inkW
        if ($isSpace) { $cellW = $SPACE_WIDTH }
        elseif ($clampWidths) { $cellW = 1.0 }

        if ($null -ne $prevHalf) { $pen += $prevHalf + $gap + $cellW / 2.0 }

        if (-not $isSpace) {

            # Place the glyph so its ink centre lands on the pen.
            $out += , @($cell[0], ($pen - $inkMid))

            if (($pen - $inkW / 2.0) -lt $lo) { $lo = $pen - $inkW / 2.0 }
            if (($pen + $inkW / 2.0) -gt $hi) { $hi = $pen + $inkW / 2.0 }
        }

        $prevHalf = $cellW / 2.0
    }

    if ($lo -gt $hi) { return @($out, 0.0) }

    # Normalise so every row starts on the same left ink edge.
    $shifted = @()
    foreach ($p in $out) { $shifted += , @($p[0], ($p[1] - $lo)) }

    return @($shifted, ($hi - $lo))
}

# Solve the gap for a target ink length. Length rises monotonically with the
# gap in both policies, so bisection is safe and needs no algebra.
function Solve-Gap($cells, $target, $clampWidths) {

    $lo = 0.0
    $hi = 6.0

    for ($i = 0; $i -lt 60; $i++) {

        $mid = ($lo + $hi) / 2.0

        if ((Get-Layout $cells $mid $clampWidths)[1] -lt $target) { $lo = $mid } else { $hi = $mid }
    }

    return ($lo + $hi) / 2.0
}

# ---- work out each sample's range -------------------------------------------------

# Per sample: the cells, the list of target lengths, and the widest target seen
# anywhere (which sizes the canvas).
$plans = @()
$widest = 0.0

foreach ($sample in $SAMPLES) {

    $cells = Get-Cells $sample

    $shortest = (Get-Layout $cells $REFERENCE_GAP $false)[1]
    $longest = (Get-Layout $cells $REFERENCE_GAP $true)[1]

    $span = $longest - $shortest
    $from = $shortest - $span * $OVERSHOOT
    $to = $longest + $span * $OVERSHOOT

    $targets = @()
    for ($i = 0; $i -lt $STEPS; $i++) {
        $targets += $from + ($to - $from) * $i / ($STEPS - 1)
    }

    if ($to -gt $widest) { $widest = $to }

    $plans += , @($sample, $cells, $targets)
}

# ---- render ----------------------------------------------------------------------

Add-Type -AssemblyName System.Drawing

$labelFont = New-Object System.Drawing.Font "Consolas", 11
$titleFont = New-Object System.Drawing.Font "Consolas", 14
$labelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(205, 205, 205))
$dimBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(120, 120, 120))
$rulePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(45, 45, 45)), 1.0
$LIT = [System.Drawing.Color]::FromArgb(255, 0, 0)

$rowH = $TOTAL_HEIGHT * $SCALE + $ROW_GAP

$canvasW = [int]($MARGIN * 2 + $LABEL_W + ($widest + 1.5) * $SCALE)
$canvasH = [int](72 + $SAMPLES.Count * ($STEPS * (2 * $rowH + $PAIR_GAP) + $SAMPLE_GAP) + $MARGIN)

$bmp = New-Object System.Drawing.Bitmap $canvasW, $canvasH
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::Black)

$g.DrawString("B widths clamped to 1.0, against C real widths, at MATCHED line lengths - tightest first",
    $titleFont, $labelBrush, $MARGIN, 20)

$y = 62.0

foreach ($plan in $plans) {

    $cells = $plan[1]

    $g.DrawString(("  " + $plan[0]), $labelFont, $dimBrush, $MARGIN, $y)
    $y += 22

    foreach ($target in $plan[2]) {

        $gapB = Solve-Gap $cells $target $true
        $gapC = Solve-Gap $cells $target $false

        $layoutB = Get-Layout $cells $gapB $true
        $layoutC = Get-Layout $cells $gapC $false

        $origin = $MARGIN + $LABEL_W

        # A faint rule at each end of the pair, to make "same length" checkable
        # rather than merely asserted.
        foreach ($rx in @($origin, ($origin + $target * $SCALE))) {
            $g.DrawLine($rulePen, [float]$rx, [float]($y - 6), [float]$rx, [float]($y + 2 * $rowH + 4))
        }

        $g.DrawString(("B pitch {0:F2}" -f ($gapB + 1.0)), $labelFont, $labelBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE * 0.35))
        foreach ($p in $layoutB[0]) {
            Draw-TalkRpnCell $g $p[0] ($origin + $p[1] * $SCALE) $y $SCALE $LIT
        }
        $y += $rowH

        $g.DrawString(("C gap {0:F2}" -f $gapC), $labelFont, $labelBrush, $MARGIN, ($y + $CELL_HEIGHT * $SCALE * 0.35))
        foreach ($p in $layoutC[0]) {
            Draw-TalkRpnCell $g $p[0] ($origin + $p[1] * $SCALE) $y $SCALE $LIT
        }
        $y += $rowH + $PAIR_GAP
    }

    $y += $SAMPLE_GAP - $PAIR_GAP
}

$g.DrawString(("faint verticals mark each pair's target start and end   space width = {0}" -f $SPACE_WIDTH),
    $labelFont, $dimBrush, $MARGIN, ($y - $SAMPLE_GAP + $PAIR_GAP))

$g.Dispose()

try {
    $bmp.Save($OUT_MATCHED, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OUT_MATCHED  ($canvasW x $canvasH)"
}
catch {
    Write-Output "COULD NOT WRITE $OUT_MATCHED - it is probably open in another program"
}

$bmp.Dispose()
