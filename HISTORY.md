# TalkRPN — where the numbers came from

This file is the **provenance record**. Nothing here is authoritative and nothing
here is required reading to work on the code.

The split is deliberate:

| | says |
|---|---|
| the code | what a value **is**, what it does, what its range and floor are |
| `DESIGN.md` | what was **decided**, and what is still open |
| this file | where a value **came from**, what it used to be, and what was rejected |

Provenance was moved out of the source comments and out of `DESIGN.md` on
2026-08-13, because it was burying the one line that said what a knob actually
did — and because it goes stale the moment a value is superseded. `PITCH` was the
case that prompted it: the layout stopped using it, the constant stayed with a
comment explaining what it meant, and the file went on reading as though the font
had a pitch.

Expect entries here to describe a state of the world that no longer exists. That
is the point. **When this disagrees with the code, the code is right.**

---

## The unit, and the grid underneath it

The font's unit is **segment E/F to segment B/C = 1** — the left column's
centreline to the right column's.

It got there in three steps:

1. **`Hp01Font`'s reconstruction figures**, which are in *outer ink box* terms:
   cell 62 × 100, stroke 8.5, advance 130. Assembled from photographs, the
   December 1977 *HP Journal*, and the Panamatik HP-01 repair kit manual.
2. **A working grid where the cap height was 100.** Centrelines drop half a stroke
   at each end, so the HP-01's centreline box is 53.5 × 91.5; scaling by
   `100 / 91.5 = 1.09290` gives a cell width of **58.47** and an advance of
   **142.08**. All the glyph geometry was drawn and reviewed on that grid.
3. **Division by 58.47**, which is the current unit.

### Why the constants are written as divisions

`100f / GRID_CELL_WIDTH` rather than `1.71028f`, so the grid figure stays visible
and no digit is invented in the conversion.

### The vertex-moving incident (2026-08-10)

When the unit changed to E/F-to-B/C, the first attempt re-derived the numbers
afresh from the HP-01's own 53.5 — which is arithmetically cleaner and produces
rounder figures. Dave caught it: *"You moved the verticies by < 1% to make them
come out round? Please undo that."*

<!-- cspell:ignore verticies -->

He was right. The glyphs had been corrected one by one against the Litronix chart
on the old grid, so re-deriving them shifts reviewed vertices by up to 0.04% for
cosmetic reasons. Reverted to a pure rescale — every figure is its old grid value
over 58.47 — and checked with a script: maximum discrepancy 2.8e-14.

---

## `PITCH` = 2.43031 — deleted 2026-08-13

The HP-01's own advance of **130**, rescaled: `130 × 100/91.5 = 142.08`, then
`142.08 / 58.47 = 2.43031`.

Done exactly it is `130 / 53.5 = 2.42991`; the last digit of 2.43031 is an
artefact of rounding 142.08 and 58.47 to two places before dividing.

It was very wide because on the real instrument every character occupied a full
digit position — over half the pitch is empty and the space between digits
exceeds the digits.

**Its provenance was always weaker than it looked.** Unlike the stroke, nobody
checked 130 against a physical part; it is a reconstruction figure, and DESIGN.md
flagged both it and the 62 cell width as *"reconstruction figures with no
corroboration, and both look wrong in the same direction."*

It survived the move to proportional spacing only as the source of
`GAP = PITCH − CELL_WIDTH = 1.43031`, then was deleted when `GAP` became a stated
value.

---

## `STROKE` = 0.14747

**16 px against 108.5 px centre-to-centre**, measured by Dave in GIMP off a
microscope photograph of a real HP-55 (`PXL_20260811_140126781.jpg`).

This corrected an earlier **0.0795**, which came from two things that both looked
sound at the time:

- a note claiming 4.45% of digit height on HP's own part — whatever that
  measured, the photographs do not support it;
- a threshold measurement of a second, sharper photograph, reading 5 px on a
  49 px cell (about 0.10). The threshold was cutting *inside* the stroke: run on
  the microscope frame, the same method reads 21–26 px where the edge is visibly
  at 16.

### Two of my mistakes on the way, both instructive

**FWHM was invalid on the sharper frame**, where the core is clipped. It assumes
an unsaturated profile, so the half-max point sits out in the halo. It read high
at 0.062 of cap height, which looked like evidence and was noise.

**I argued the measurement down as "bloomed" and was wrong.** Dave: *"I don't see
much if any bloom... it's not so overexposed that there's any real bloom."* An
edge profile across the microscope frame's left column settles it — the plateau
is 190 of a ~210 range, so nothing is railed, and the top is flat over ~9 px.
Not an overexposed image. The 90% width is 12 px and the half-max width ~23, so a
visual reading of 16 sits sensibly between them. What softness there is comes
from the epoxy bubble lens, and being symmetric it does not bias fat.

So the HP-01's own 9.29 — 0.159 in this unit — was close to right all along, and
the claim that it was "twice what it should be" was simply wrong.

### The denominator has to be centre-to-centre

The first attempt paired a 16 px stroke with a 124.5 px *outer* width, giving
0.128. That double-counts: outer width already contains one stroke.

`outer − stroke = centre-to-centre` is the identity, and it has the useful
property that **bloom cancels in it exactly** — bloom pushes outer edges out and
inner edges in by the same amount. Which is why the *widths* from two photographs
at 2.2× different magnification agree to within 0.1% while their *strokes*
disagree by 45%.

---

## `SLANT_DEGREES` = 6.0

A compromise Dave chose between HP's datasheet **5.0** and the **7.5** the font
first used. `Hp01Font` still carries 7.5, itself chosen by eye against a
reconstruction reading of 7.8.

---

## `DOT_SIDE` = 2 × STROKE, and square

Magnifying the HP-55 photograph shows the decimal point is a **square die**, and
every segment is **beaded out of small rectangular dies** strung end to end.
Round dots were the earlier guess and were wrong.

Tied to `STROKE` because that is the HP-01's own relation — an observation, not a
rule the font has to obey.

The same photograph is why **the pen does not rotate**: a real segment is a die on
a rectangular grid, so a fixed-orientation nib is the honest sweep.

---

## `DP_GAP_FRACTION` = 0.337

Originally `DP_X = 86.64 / 58.47 = 1.48179`, an HP datasheet position on the old
grid, with the fraction derived as `(DP_X − CELL_WIDTH) / (PITCH − CELL_WIDTH)`.

Both `DP_X` and that derivation were deleted on 2026-08-13. The derivation was
actively dangerous once `GAP` became an independent knob: written against `GAP`
it evaluated to **0.567** at gap 0.85, which would have shoved every decimal
point most of the way into the next character.

---

## `DESCENDER_FRACTION` = 0.44

144 against 100 on the old grid — exactly 0.44 there too, so naming it moved
nothing. Still open: it is deep, against an x-height of half the cap.

Later became the length `DESCENDER_DEPTH` = 0.7525 when the reference unit was
unified. Settled on 2026-08-17 at **0.625**, chosen by eye on the display test
screen's dd knob against the lowercase sample — the measured depth plunged
tails nearly as deep as their bowls are tall. The knob was removed the same
day; the value lives in the font.

---

## `VGAP` = −0.33 was tuned against a spacing bug

Dave noticed (2026-08-17) that at VGAP −0.33 the tails and the caps of
adjacent equal-size rows showed clear space where the numbers said they should
interleave by 0.48 units. The cause was in `rowGapPx`: the below row's
top-to-baseline span was computed as `cellHeight − (baseline-to-ink-bottom)`,
treating the cap height as if it were the full ink height. Every row gap was
inflated by descender-plus-stroke in the below row's units — about 0.77 — plus
the canvas rounding padding. (A correct `baselineFromTopPx` helper had existed
and was deleted as "dead" earlier the same week; it was dead because this
formula had wrongly absorbed its job.) Fixed by reintroducing it and charging
the above row for its canvas padding too, so baselines now land exactly
`vpitch` apart. Consequence: every vg value tuned before the fix, −0.33
included, describes a look about 0.77 units tighter than it produced; vg needs
re-tuning by eye.

---

## `VPITCH` = 2.75

Chosen, not measured. The floor is the ink height, 2.61027, below which
descenders reach the row beneath.

Retuned by eye on the emulator to 2.13 on 2026-08-14, expressed then as
`VPITCH_OF_TOTAL_HEIGHT` = 0.865, a multiple of the total height so the rows
would follow the descender depth. On 2026-08-16 that multiplier — the last
dimensionless knob in the font — was replaced by `VGAP` = −0.33, the
centreline clearance between one row's descender bar and the next row's cap
line, with `VPITCH` derived as total height + `VGAP`. Same rendered pitch
(2.1325 against 2.1301); better scaling (a deeper descender now preserves the
tuned clearance instead of eating 13.5% of it). Dave chose the centreline
reference over an ink-edge one (zero = inks touch) to keep one measuring
convention everywhere; the inks touch at `VGAP` = one stroke, exactly as
glyphs do horizontally.

---

## The HDLS-1414 dot font's geometry: chart against callouts

The glyphs were recovered from the datasheet's character chart, which is
vector artwork - 1,631 identical drawn squares - so the dot patterns are
exact, not read from a scan. One cell was ambiguous: 0x0E (E-acute) draws two
body rows compressed, drifting up to half a column off the lattice; it was
read as full-width bars matching the plain E, the only reading fitting the
dot count. Dave checked the result against the sheet on 2026-08-17 and
confirmed it.

The chart's page geometry and the part's physical dimension callouts
disagree, and the callouts won for layout, the chart for appearance:

| quantity | chart artwork | part callouts | adopted |
|---|---|---|---|
| row pitch / column pitch | 1.0649 | 1.098 (0.022/0.020 inch) | 1.098 |
| dot size / column pitch | 0.7033 | 0.4902 (the die) | 0.7033 |
| blank columns between characters | 2.163 | 3.75 (8.75-pitch spacing) | 3.75 |
| vertical gap between lines | 3.562 | none - single-line part | 3.562 |

The dot size keeps the chart's figure because a lit LED behind its diffuser
reads far fatter than its die - which is presumably why the datasheet's own
chart fattens it. The callouts were verified against the drawings' leader
lines, and both character-envelope equations close exactly on round inch
values; the figures themselves are NOT drawn to their callouts.

---

## The 2026-08-17 by-eye retune

With the row-spacing bug fixed and the field boxes visible, Dave settled a
round of display defaults on the emulator: segment `DEFAULT_GAP` 0.67 → 1.0,
`VGAP` −0.33 → 1.0 (the first tuning made with honest spacing; later the same
day, on the real watch, eased to 0.78 - what just fits all of register T on
the glass), dot font
`CHARACTER_GAP_COLUMNS` 3.75 → 1.0 (the part's own 3.75 is desk-display
extravagance), `FIELD_POSITIONS` 15 → 10, and the display height became a
plain `INITIAL_HEIGHT_FRACTION` = 0.08 - retiring the derive-to-fit rule,
which made sense only while the field size was fixed. The small rows' centring
shift changed from half of "LASTX" to half of "T": single-letter labels are
the common case, and charging every row for the widest label dragged the
stack left. The segment font's dots also shrank from two strokes square to
one, then settled at 1.5 (`DOT_SIDE_STROKES`) on the watch: two read heavy
beside the bars, one read thin.

---

## Why the WDB app says "ON?" on the Watch7

The app was meant to show the wireless-debugging service's REAL state, not
just the setting, after a day of adbd wedging under a live setting. Every
probe an app could use was tested on the actual watch (2026-08-17):

- `service.adb.tls.port` - AOSP adbd publishes its live TLS port here. The
  Watch7 never sets it: empty even from the adb shell while a session was
  demonstrably connected. Enumerating `getprop | grep adb` shows Samsung
  publishes no wireless-adb property at all.
- `dumpsys adb` - contains `connected_to_adb`, a genuine host-attached flag,
  and works from the shell. But the service manager hides the `adb` service
  from app uids ("Can't find service: adb"), even with DUMP granted via pm.

So on this hardware the app cannot know, and says `ON?`. The remaining
untried route is NsdManager self-discovery of `_adb-tls-connect._tcp` (needs
INTERNET on the wdbtile app); parked unless the uncertainty starts to hurt.

Also learned: the PC-side adb server's mdns cache goes stale - after the
watch cycles wireless debugging, `adb kill-server` is what makes the fresh
advertisement visible. Two "watch unreachable" verdicts today were actually
cache staleness.

---

## The decimal point's three homes in one day

2026-08-18. The dot started at `DP_GAP_FRACTION` = 0.337 of the gap (itself a
datasheet-derived accident) and 0.3263 below the baseline. Dave suspected the
fraction was a patch; the morning's replacement was `DP_ADVANCE`, a fixed
0.2 past the glyph's ink, argued from hardware (a DP die is bolted to its
digit). It lived about an hour: Dave then proposed the third model - the dot
as the BOUNDARY'S mark, dead-centred in the gap (`inkRight + gap/2`) and
raised onto the baseline, level with segment D. That one won because it is
not a constant at all: no knob, symmetric clearance by construction, scales
with the gap naturally. Two tweakables (`DP_ADVANCE`, `DP_DROP`) died with
it, and `DP_GAP_FRACTION` never reached a release. The vertical move also
returned 0.33 units of clearance to the row below.

---

## The LED colour

Both datasheets state the peak outright — no reconstruction needed. HP 5082-7400
(the HP-35/HP-01 family bubble) at **655 nm**; Siemens DL-3422 at **660 nm**.
Both GaAsP, both at the far red end of the spectral locus, around
CIE 1931 x = 0.73, y = 0.27.

**No consumer display can show that hue**: sRGB's red primary falls short by
0.088 in x, Display P3 by 0.048, even Rec.2020 by 0.020.

| mapping | result | ΔE2000 |
|---|---|---|
| clip the negative components — maximum saturation | `#FF0000` | 7.8 |
| desaturate toward white, preserving dominant wavelength | `#FF0052` | 17.0 |

`#FF0052` is not a mistake — it genuinely has 655 nm as its dominant wavelength
at 63% purity — but the line from 655 nm to the white point exits the gamut
through the **magenta** edge, so it lands on pink.

`LED_RED` was `#E81810` before this, which mixed in a little green and blue and
read duller and browner than the emitter did.

**Do not sample this from a photograph.** Camera filters overlap, the red channel
clips almost at once on a lit segment, and the highlight rolls into whatever green
and blue were picking up — which is why photographs of these displays show a
white-pink core. The eye does not do that.

---

## Spacing: fixed pitch → proportional (2026-08-13)

The font records fixed-pitch hardware and was laid out on a fixed pitch until
this. Three policies were compared:

- **A** — fixed pitch, glyphs wherever the segment table puts them (what shipped)
- **B** — fixed pitch, each glyph's ink centred in its cell
- **C** — proportional: advance is half this glyph's ink width, plus a gap, plus
  half the next's

**C was chosen.** B is C with every width clamped to 1.0, which is the clearest
way to state the difference.

### Two false starts, both mine

**I claimed the fixed pitch left "holes around the `1`". It does not.** At matched
line length the gap beside a `1` is 0.980 against proportional's 0.920 — a 6%
difference, invisible. Dave caught it: *"It looks like 1 is spaced the same in B
as in C."* The real defect is the **inconsistency** (0.480 / 0.980 / 1.480
depending on neighbours) not the size of any one gap.

**The first comparison sheet could not show the difference at all.** It used
`31,415.19` and `1,011.10`, which *alternate* full-width digit and `1`. In that
pattern every gap in the number is the around-a-`1` gap, so the two policies land
on top of each other. A test string needs both a `11` run and an adjacent pair of
full-width digits.

**I also had the `ff` collision backwards**, claiming it was C's. `f` is a
full-width glyph in this font, so `ff` is the *tightest* case under fixed pitch,
not the loosest.

### Method note

Comparing at equal *gap* is not a fair test — C is simply shorter, and wins on
compactness alone. `make_spacing_matched.ps1` solves each row's spacing knob by
bisection to hit a target ink length, so the only question left is which policy
distributes that length better.

---

## The end rule: six designs

Which segment ends extend, and by how much. Each of the first five died on an
artefact Dave caught by looking at the full character sheet:

| | failed on |
|---|---|
| rotating pen | angled ends, bumps on the hooks |
| blanket axis extension | eaves hanging past diagonals, spurs on `n`, `M`'s apex stub |
| corner patches | nubs on lone tips, unequal `"` ticks |
| boundary extension + extrusion boxes | kinked boots under `v w`, ragged x-height |
| free/shared slope extension of diagonal tips | kinked boots again |
| **die policy** — chosen 2026-08-11 | — |

Also rejected: an "ink box" alternative giving a uniform outer rectangle, which
kinked heels where diagonals meld into extended feet.

One addendum, caught on the first review of the die policy: **the mitre diamond**,
where a horizontal bar and a diagonal share an endpoint.

### The meta-lesson, which cost several rounds

I kept judging a pen change on a handful of glyphs and breaking others.
`make_pen_diagnostic.ps1` exists for this, but the real check is the **full
character sheet, after every pen change**.

---

## Element count

Started at 22, aiming at the DL-3422's segment count. Ended at 32.

- **The `Int` ceiling was crossed on 2026-08-07, deliberately, for the parens.**
  The mask is a `Long`.
- `A5`/`D5` — the right parenthesis as two halves — merged into a single `RPAR`,
  because nothing ever lit one without the other.
- `COL2_TAIL` was bought for the semicolon.
- **The cost of a choosable right corner was quoted wrong once.** "Two more hook
  pairs would take us to 32" is false: the right side has no stubs, so making it
  choosable means splitting `A2`, `B`, `D2` and `C` into main + stub — +6, not +2.
- **Going fully round would have been *cheaper*, not dearer** — drop
  `A4 D4 F2 E2`, add two arcs, and the font falls to 28. The square-or-hooked
  *choice* is what costs; the curves are nearly free.

### Font work completed, with what each replaced

| | |
|---|---|
| Horizontal placement | Was a fixed pitch with glyphs sitting wherever the segment table put them. Now proportional by ink extent; `GAP` is the only horizontal knob. |
| Diagonals to the corners | Segments H, I, K and L stopped at the hook landings, leaving every slash short of its corner. Moving H's and L's left ends to the exact corners improved about fifteen glyphs (`/ \ X * M N Y V W v`) and cost reverting `&` to `A4` and `a e` to `D4`. |
| Semicolon | Could not be drawn at all. `COL2_TAIL` bought for it. |
| Comma taper | Considered and declined; the constant-width tail is more period-correct. |
| `P`/`Q` shift | Declined 2026-08-09. They looked as though they wanted to move left a little in `B` and `D`; left alone. |
| Decimal-point position | Was a fixed x, correct at exactly one pitch. Now `dpXAfter(inkRight, gap)`. |
| Stroke width | Was 0.0795, believed to be "twice what it should be". Measured at 0.147. |
| `l` and `\|` identical | `\|` took the descender (`P Q M`). |
| `0` and `O` identical | `O` took four square corners; `0` kept the hooks. |
| Parens vs brackets | The pair was mismatched — `(` curved, `)` square — because the right side has no shortened bars for a bare arc to join. Fixed with `RPAR` as one bespoke element. |
| Not yet wired in | `DisplayTestActivity` drew `Hp01Font`; it draws `TalkRpnFont` now. |

### Glyph changes worth recording

- **Half-width digits on `P`/`Q`** were tried — they keep `5` and `S` apart — and
  reverted. The HP-01 width looks right.
- **`0` and `O` were briefly identical.** `O` took four square corners; `0` keeps
  the hooks.
- **`l` and `|` were identical.** `|` is `P Q M`, running the full cell including
  the descender; `l` is `P Q`.
- **Segments H and L had their left ends moved to the exact corners** on
  2026-08-08. It improved about fifteen glyphs (`/ \ X * M N Y V W v`) and cost
  reverting `&` to `A4` and `a e` to `D4`.
- **The mismatched `( )` pair shipped despite being described accurately in three
  places.** *"No hook exists on the right"* is a fact about the segment table;
  *"that pair looks broken"* is a fact about the glyph. Reasoning carefully about
  the first is not a substitute for rendering the second and looking at it.

---

## Bugs, and what caused them

The live warnings for these are in the code, where they can stop the bug
recurring. This is the record of what actually happened.

**Antialiasing seams.** Segments were drawn one stroke at a time, which looked
right at heavy strokes and wrong at light ones: where two overlap, the second
stroke's antialiased edge blends over the first and the doubled coverage reads as
a *brighter* line. Fixed by merging every lit segment into one `Path`, filled
once. The PDF renderer had the same bug and the same fix.

**Winding cancellation.** A bar running right-to-left comes out wound opposite to
one running left-to-right, and under NonZero fill the overlap cancels into a
**hole** — small black notches at the crossings in `#` and `$`. Fixed by
normalising every polygon by signed area. Fixed in the PowerShell first and left
in the Kotlin until Dave asked for the port, which is its own lesson.

**The seam overlap ate every corner patch.** `Add-Bar` reassigned its endpoint
variables before calling `Add-EndPatch`, and the corner epsilon (0.0005) was
smaller than the overlap (0.0015), so a bar ending at x = 1 was tested at 1.0015
and failed. It presented as "the RIGHT corners are broken", because left corners
are formed by arcs and take no overlap.

**Compose `Matrix` indexing.** `this[0, 1]` and `this[0, 3]` index *row-major*
into a *column-major* array, so they land on `SkewY` and a perspective term. The
cell sheared the wrong way and shifted out of its own bounds. Use
`values[Matrix.SkewX]` and `values[Matrix.TranslateX]`.

**PowerShell variable names are case-insensitive — four times.** `$stroke` *is*
`$STROKE`, so a parameter default of `$null` shadowed the script constant and
every bar came out zero-width. `$w` *is* `$W`, so a glyph width clobbered the
canvas width — and the only symptom was the closing message reporting a 1-pixel
image for a correct 1495-pixel file. Hash-table keys collide the same way.

**The emulator's font screen came up blank** — CPU starvation from the speech
recognizer's unbounded restart loop, not a rendering fault.

**The recognizer's backoff gave up in ~1.2 s instead of the promised ~10 s.**
The platform delivers ERROR twice for one failed session — visible in logcat as
paired failures at the same millisecond — and each callback scheduled its own
restart, so attempts doubled per round and the failure count rose twice per real
attempt. Found by testing the give-up path on the emulator; fixed with a
`restartScheduled` guard cleared when the next utterance actually begins.

---

## Display P3, and Dave's monitor

Diagnosed 2026-08-12, and worth not re-diagnosing. The washed-out pink desktop
with HDR enabled is **Windows' SDR-to-HDR compositing**, not the monitor and not
the calibration: the tagged-HDR thumbnail in Settings renders correctly while
everything around it does not. Running the MS HDR calibrator on Static did not
change it.

Separately: an EDID decode of the panel produced a red primary at x = 0.181,
which is nonsense for a P3 display. The decode was wrong; it is recorded here
only so nobody trusts that number if it resurfaces.
