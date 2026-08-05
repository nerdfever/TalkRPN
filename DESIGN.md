# TalkRPN — design decisions

> **Constraint change, 2026-08-03.** The original "no cloud service calls, ever" rule
> is **withdrawn**. Dave's position now: audio leaving the device is fine if it works
> better; what he does not want to pay is a latency tax. This is for personal use, so
> anyone else's view of the trade does not enter into it.
>
> The `INTERNET` permission is still absent, but as an artefact of how the app was
> built rather than as a guarantee anyone is defending. Adding it is a normal option.

Decisions settled in discussion, with the reasoning. Written down because the
reasoning is the part that gets forgotten, and several of these look arbitrary
without it.

Nothing here is built yet except the speech layer.

---

## Speech engine: Android's platform recognizer

Measured against a bundled Vosk model on the real watch. See `SOURCES.md` for the
numbers. Summary:

| | Platform | Vosk |
|---|---|---|
| think latency | **83–140 ms** | 537–937 ms |
| accuracy on test phrases | all correct | mangled spoken `e` |
| compound numbers | `nine hundred eighty eight` → `988` | returns words |
| app size | **zero** | 97 MB |
| deaf window between utterances | **~200 ms** | none |

Vosk was removed but stayed in git history. The `SpeechSource` interface it forced
into existence was kept: it cost nothing, it is what made the comparison possible,
and a future engine drops in without the calculator noticing.

### The deaf window, and how it was closed

The platform recognizer closes the microphone after every result, so anything said
between utterances was lost. Two corrections to what was first written here:

- The ~2000 ms figure was **cold start**, not steady state. Measured steady state is
  ~200 ms mean.
- `EXTRA_SEGMENTED_SESSION` (API 33) is **not supported on this device**. It was the
  obvious fix and it does nothing here.

What actually fixed it was inverting the ownership. The app now owns the microphone
and the recognizer reads from it, rather than the recognizer owning the microphone
and the app waiting its turn:

    MicStream ── AudioRecord, runs continuously ──┐
                                                  ├── ParcelFileDescriptor.createPipe()
    SpeechRecognizer ── EXTRA_AUDIO_SOURCE ───────┘

`AudioRecord` runs for as long as the screen is up. Restarting a recognizer session
replays the buffered backlog into the new session instead of discarding it, so
speech that begins before the recognizer is ready survives. This is why the mic can
stay visibly "open" to the user — the earlier red/green signalling problem, where
the indicator flipped to red a few milliseconds after the user had already started
talking, no longer arises.

---

## Cloud recognition: not now, and the reason is measurement not principle

The constraint against it was withdrawn (see the note at the top), so this is a
free engineering choice. The measurement says stay local:

| `EXTRA_PREFER_OFFLINE` | Silent failures |
|---|---|
| **ON** | **0%** |
| OFF | 36% |

Counterintuitive — Google's server-side models are plainly better in general, and
the same watch dictating to Gemini performs well. The discrepancy is not explained.
The working guess is that with the flag off the recognizer waits on a network round
trip that this watch's Wi-Fi does not reliably complete, and gives up silently
rather than falling back.

Whatever the cause, building a cloud path now would mean adding the route that
measured **worse**. Revisit only if a specific token proves unrecognizable offline
after biasing — then it is a targeted fix rather than speculation.

`INTERNET` stays absent for now, as a consequence of this decision rather than as a
principle being defended.

---

## Three layers, and all the irregularity lives in the first

    recognizer text
       ↓  normalize        ← every natural-language irregularity lives HERE
    canonical token stream
       ↓  lex + parse      ← strictly regular postfix
    RPN engine

The normalizer does longest-match substitution: homophones, multi-word phrases, and
the symbol forms the recognizer emits on its own.

    "change sign"       → NEGATE
    "square root"       → SQRT
    "times ten to the"  → EXPONENT
    "/"                 → DIVIDE          (the recognizer emits this for "divide")
    "988"               → NUMBER(988)

**The recognizer rewrites what you say**, and the table of what to speak is not the
same as the table of what arrives. Both digits-as-numerals and `/`-for-divide were
observed. The parser must accept forms no human would ever utter.

**Constraint on aliases:** no phrase alias may be a *suffix* of another. `to the`
cannot be an alias for `raise` while `times ten to the` exists, or
"five times ten to the three" acquires two valid parses that differ by thirty orders
of magnitude. Prefixes are safe under longest-match; suffixes are not. Worth
enforcing at startup so it fails loudly rather than silently preferring one reading.

---

## Parsing words resolve against a vocabulary, not a flag

Some tokens consume the following token from the input stream rather than taking
operands from the stack:

    log base <n>        antilog base <n>
    store <name>        recall <name>
    define <name>       forget <name>

The first design had a per-token "takes an argument" flag. Better: **the next token
is resolved against a different vocabulary.**

    after store / recall / define / forget  → NAMES
    after log base / antilog base           → NUMBERS
    otherwise                               → COMMANDS

This is Chuck Moore's trick from cmForth, where a separate `COMPILER` wordlist
replaced the `IMMEDIATE` flag — and took `STATE` with it. Nothing is "suppressed";
you simply look in a different table. `store clear` isn't "the clear command got
overridden", it's "`clear` was resolved in the names space, where it is just a name."

Keep the set of parsing words **small, fixed and declared**. Forth's hardest corner
is what happens when it isn't. In particular, user-defined programs must not
themselves become parsing words.

Related decisions:

- **Name arguments consume the rest of the utterance**, so multi-word names work.
  Number arguments consume exactly one token.
- **Store the name as the recognizer heard it.** If "foo" comes back as `pho`, store
  `pho` — because "recall foo" will produce `pho` again next time. Recognition
  consistency matters more than spelling.
- **Recall of an unknown name must be a visible error**, never zero. A silently
  wrong zero is the one failure a calculator cannot afford.
- A `catalog` command to hear what is defined.

---

## Number entry

A small state machine — mantissa digits, optional `point`, fraction digits, optional
exponent marker, optional sign, exponent digits — terminating the moment a token
arrives that is not valid in the current field. That termination rule is what makes
"five minus three" unambiguous while "five e minus three" also works.

**Sign rule:** a sign word at the *start of a number field* sets that field's sign;
anywhere else it acts on X.

    one point five  e  minus  six
    └── mantissa ──┘  └sign┘└digit┘

This is what HP calculators already do — EEX then CHS negates the exponent, not the
mantissa. Not a special case; the standard numeric-literal lexing rule.

**`e` carries three meanings, disambiguated by position:**

| Context | Meaning | Resolved by |
|---|---|---|
| after `log base` / `antilog base` | base e | normalizer (whole phrase is one token) |
| after digits in X | EEX | number lexer |
| anywhere else | 2.71828… | default |

Two of the three never reach the parser, so at parse time it is a single test: is
number entry in progress.

**Risk worth remembering:** `e` is the acoustically weakest token in the vocabulary
carrying the most meaning, and a dropped `e` turns 2.5e6 into 2.56 silently.

### What the platform engine actually does with `e` (measured 2026-08-04)

| Spoken | Result |
|---|---|
| `e` alone | **nothing comes back** |
| `e` … pause … `f` | `e f` — both words, emitted only once `f` arrives |
| `e to the x` | recognized |
| `log base e` | recognized, most of the time |

The pattern is not that `e` is misheard. It is that **the engine will not emit a
result for an utterance this short on its own** — it needs neighbouring speech
before it will commit. Given a neighbour, the `e` is there and correct.

This is better news than it first appears, because in real use `e` is almost never
alone. `2.5 e 6` surrounds it with digits on both sides, which is exactly the
condition under which it works. The failing case — `e` as an entire utterance — is
one a calculator rarely produces.

Two consequences:

- **Do not judge `e` by speaking it in isolation.** That tests the endpointer, not
  the vocabulary.
- **Keep a long spoken form for EEX.** `times ten to the` already exists as an alias
  and carries enough acoustic weight to stand alone. It is the fallback when `e`
  does fail.

Biasing has **not** been tried yet, and this is the token most likely to benefit
from it.

---

## Display modes

Three, each with a settable number of digits after the radix, defaulting to **3**:

| Mode | Behaviour |
|---|---|
| **Fixed** | Ordinary positional notation. **Overflows to Scientific** when the value will not fit. |
| **Scientific** | One digit before the radix, exponent free. |
| **Engineering** | Exponent constrained to a multiple of 3, so one to three digits sit before the radix. |

**Note a change from `speech_tokens.xlsx`.** The sheet says Fixed overflows to
*Engineering*; the decision here is *Scientific*. Worth reconciling — Engineering
would keep an overflowed value in the same 10³ family as the units the rest of the
calculation is in, which is an argument for the sheet's version.

### The radix is always shown

An integer displays as `5.`, never `5` — as HP calculators do, and including when
the digit count after the radix is zero.

Not decoration. On a display this narrow, truncating a value to fit is routine, so
a reader needs to know whether they are looking at a whole number or at the leading
digits of something longer. The trailing point is that signal. It also separates a
displayed number from a register label or an error word at a glance.

On a value carrying an exponent the radix belongs to the mantissa: `6e23` displays
as `6.e23`, not `6e23.`.

Cost is one narrow slot per integer — about a third of a digit position at the
current `PUNCTUATION_ADVANCE`.

### Digit grouping

Groups of three left of the radix, separated by commas.

Grouping is a **display** concern, not a font one - the renderer draws a `,` if it
is handed one and has no opinion about where. This matters because the
`radix comma` / `radix dot` commands swap the two characters, so the separator and
the radix have to be decided together, in one place.

Only the integer part is grouped. Never the fraction, and never the exponent.

Grouping applies in Fixed mode only in practice: Scientific has a single digit
before the radix and Engineering at most three, so neither ever reaches a group
boundary.

**Separators are narrower than a digit cell.** On the real HP-01 every character
consumed a whole digit position, which is why `3.141593` filled eight of its nine.
Authentic and unaffordable: grouping `1,234,567` would spend three of ten positions
on punctuation. `Hp01Font.PUNCTUATION_ADVANCE` sets the width as a fraction of a
full advance - 1.0 is the original behaviour, 0.35 is the current setting and looks
like a calculator LCD, 0 draws the mark inside the preceding gap for nothing. Note
that 0 and a tight pitch do not combine, since the gap is what shrinks.

The comma is drawn as the decimal point's own dot plus one extra tail stroke below
it, so it costs the cell no new geometry.

---

## Naming scheme for logs and powers

One rule rather than six names — bare means ten, `natural` means e, `base N` states
it:

| | log | antilog |
|---|---|---|
| bare (base 10) | `log` | `antilog` |
| base e | `natural log` | `natural antilog` |
| explicit | `log base two` | `antilog base two` |

Variable-base forms are deliberately absent: `n^x` is already `raise`, and a
variable-base log is `ln ÷ ln`.

- **Not `exponentiate`.** It shares its entire stem with `exponential`, which is the
  correct name for the *other* function. Worst possible pair for a recognizer.
- **`raise` for y^x**, because "to the power" said *after* the exponent is wrong-order
  English in postfix.
- **`antilog` is acoustically weak** and is the root of half the table. Expect
  "auto log" / "and a log" and accept them as aliases.

---

## Vocabulary: what is still unsettled

`speech_tokens.xlsx` is the working list — roughly 60 tokens and 90 spoken forms.
It is explicitly not final. Reading it against the decisions above, these need
resolving before biasing is worth measuring:

| Open question | Where |
|---|---|
| **Bare `log` and `antilog` are missing.** The naming scheme says bare means base 10, but neither has a row — and base 10 is the common case. | — |
| **`drop` means two things.** It is an alias for `roll`, and separately its own token `DROP`. The note on the `roll` row ("drop, copy T to Z") describes DROP, not a four-register roll-down. So: is there a true R↓ at all, or only drop? | rows 23, 63 |
| **`swap` appears twice**, same token both times. | rows 21, 39 |
| **No roll *up*.** HP-21 has R↓ only, so this may be deliberate. | — |
| **`sine` and `sign` are homophones** — and `change sign` contains the word. No recognizer will separate these reliably; the parser needs an explicit rule. | rows 15, 31 |

**Biasing is gated on this.** `EXTRA_BIASING_STRINGS` is not unbounded, so the list
may have to be tokens only rather than every alias — and biasing measured against a
vocabulary that then changes has to be measured again. Settle the sheet, then bias,
then measure against a fixed script so before/after is real rather than
impressionistic.

---

## Sign handling

    negative  →  set sign negative   (idempotent)
    negate    →  flip sign           (also: change sign, c h s)

Better than HP's single CHS, and specifically better *for voice*: on a keyboard you
can see the sign before pressing. Speaking, you often can't — so an idempotent "make
it negative" is safe to say without looking, and a toggle isn't.

---

## Stack naming: HP's convention is kept, inconsistency and all

HP calls the fourth register **T**, glossed as "top", and draws it at the top of the
display. In the ordinary stack sense it is the *bottom*: push and pop happen at X,
so X is top-of-stack and T is the far end.

HP's own metaphor is not push/pop but **stack lift** — their term. Values enter at
the bottom and the column rises toward T, which makes the naming self-consistent
within that model. What is unusual is only that the *active* register sits at the
bottom, which is backwards from most stacks anyone meets.

T is not quite a normal bottom either: **on a drop it replicates itself** rather
than emptying, which is what makes `2 ENTER × × ×` keep squaring against a
constant.

**Decision: keep HP's naming and drawing order** — T, Z, Y, X down the screen. The
alternative considered was Forth order, with Y, Z and T *under* X so that X reads
visibly as top-of-stack. Rejected: every HP manual and forty years of muscle memory
agree with each other, and being locally more correct at the cost of disagreeing
with all of that is a bad trade for a calculator meant to be used.

**But the convention must not leak into the engine.** The stack is held in one
unambiguous order — index 0 = X = top-of-stack, ascending into the machine — and
the display layer reverses it when drawing. Naming registers by screen position
would bake the ambiguity in permanently, and something would eventually be indexed
the wrong way round.

---

## Undo

Snapshot the whole machine — four stack registers, LastX, the STO registers, mode
flags. A ring buffer of snapshots gives multi-level undo for negligible memory. The
state is small enough that nothing cleverer is warranted.

---

## Units (deferred, but decide the value type now)

Plus42-style units are a natural fit for voice — saying "five point two kilometres"
is far easier than keying a unit. The open-vocabulary engine makes the extra
vocabulary free.

**Do not build it yet.** But make the value type carry an optional unit from day one
— `Value(magnitude, unit?)` rather than a bare `Double` — so units can be added
later without rewriting the stack, display and parser.

### Unit mode

An explicit mode state — **SI, US, Imperial** — with a command to set each.

Keeping US and Imperial apart is not pedantry. Since 1959 both share the foot and
the pound, so length and mass are identical; **volume is not**. A US gallon is
3.785 L against the imperial 4.546 L, and a US pint is 16 fl oz against 20. Merging
them would silently corrupt exactly the calculations someone reaches for a
calculator to do.

The mode governs two things and no others:

1. What unit a **bare number** is assumed to be.
2. What unit **results are displayed** in.

It does not govern what a value *is*. A value carries its own unit, so an explicit
`five feet` is five feet regardless of mode.

### Switching modes does not convert what is already stored

Entered values keep the unit they were entered in — feet stay feet in SI mode.
Changing mode changes the defaults for what comes next, nothing more. Converting
everything the instant the mode changes is the more surprising behaviour
mid-calculation, and surprise is expensive on a device with no undo button in
reach.

To convert the machine, **say the mode twice in a row**: `SI SI` converts every
register and store into SI. Two adjacent mode tokens is unambiguous in the token
stream, and it echoes the double-press idiom HP users already have in their fingers.

Undo covers the accident case, since a snapshot is taken per operation — a stray
repeat is recoverable rather than catastrophic.

**Repeats survive recognition.** This was raised as a worry — the idiom depends on
two identical tokens arriving as two — and the evidence says it is not one. No
collapsing has been observed in use, and a deliberate test of three `arcsin`
followed by three `arcsine` returned all six as separate results.

The lone `e` failure is a different mechanism and does not generalise here: that is
an utterance too short to commit to in isolation, a minimum-context problem. `SI SI`
is four syllables of continuous speech.

### Automatic mode switching: maybe, and narrowly

Speaking a unit from another system could switch the mode — `meters` selects SI,
`feet` selects US, `imperial gallons` selects Imperial.

Attractive, and low risk *only because of the rule above*: since switching does not
convert anything, a wrong guess costs nothing already on the stack. It changes what
the next bare number means, and that is all.

Left as MAYBE. The thing to watch is whether it surprises in practice — saying
`five feet` once in an otherwise metric session would quietly make the following
bare number a US measure.

---

## Programs, if they happen

Dictating a formula does **not** need an LLM, and must not need the network. Two
offline routes:

1. **Learn mode**, HP-style: `define hypotenuse`, perform the operations, `end`.
   Recording tokens that are already parsed. Perhaps a day's work.
2. **Algebraic parsing**: "a squared plus b squared root" via shunting-yard,
   ~150 lines of ordinary Kotlin.

An LLM would only add tolerance for sloppy phrasing, which is worth much less given
a fixed token vocabulary.

---

## Display: HP-01 as inspiration, not as emulation

`HP01font.kt` reconstructs the HP-01's 1977 LED font by measurement from
photographs, cross-checked against the December 1977 *HP Journal* and the Panamatik
repair-kit manual. It draws segments as vector paths on a Compose `Canvas`.

**Explicit divergences from the original**, decided rather than inherited:

- **A `.` or `:` will not consume a whole digit cell.** On the real HP-01 it does,
  which is why `3.141593` fills eight of nine positions. That is authentic and
  wasteful, and this is not an emulator.
- **No unlit-segment ghosting.** (The idea: draw `8` in a dim colour first, then the
  lit glyph over it, so dark segments stay faintly visible — the way an LCD looks.
  It was suggested in the file's own notes as reading well on OLED. The HP-01 was
  LED and showed no such thing, and it is not wanted.)

What is being kept is the *look*: the 7.8° shear, the stroke weight, segments `a`
and `d` hooking through 90° at their left ends, `b` taller than `f`, and butt caps
so a `1` has no waist at the midline.

### The open question: more segments, and a real font

The display should be able to show **text**, not just digits — which the HP-01's
seven segments cannot do. So: extend the cell to a larger segment count (14- or
16-segment starburst), keeping the HP-01 geometry rules, and pretend the hardware
had more segments than it did.

That decision is separate from the delivery mechanism:

| | Vector paths (today) | Built TTF/OTF |
|---|---|---|
| Glyph design work | same | same |
| Layout | hand-rolled; `measureWidth` exists | Compose `Text` handles it |
| Per-segment control | yes | no |
| Toolchain | none | FontForge/fonttools, plus a build step |
| Small sizes | explicit stroke width, consistent | hinting can distort thin segments |
| Reuse outside the app | no | yes |

The hard part — deciding the geometry of ~95 glyphs — is identical either way, and
its output is a **segment mask table plus path data**. That table is the asset; the
renderer is swappable. So this is not a permanent fork: design the masks first, and
a TTF can be generated from the same data later if the layout convenience turns out
to be worth the toolchain.

For a single right-aligned line on a fixed cell grid, Compose's text layout buys
little — a segment display *is* a fixed grid, which is what the vector renderer does
natively and what a proportional text engine has to be argued out of.

Housekeeping: `HP01font.kt` still declares `package com.example.hp01`.

---

## Physical constraints measured on the watch

- **Continuous listening costs roughly 1 W** — screen forced on, recognition
  running, Wi-Fi awake. Against a 1.15 Wh battery that is about an hour from full,
  and about seven minutes from 10%.
- **Charging throttles hard when warm.** At ~40 °C the watch accepted only 79 mA
  (~290 mW) — less than a third of what a test session draws, so a long session on
  the charger still ends in a flat battery.
- Below ~10% the fuel gauge reads pessimistically under load (voltage sag) and the
  firmware shuts down early. Treat 10% as empty.

**This vindicates the rule that the app listens only while actively calculating.** A
real calculation is thirty seconds, which costs nothing. An always-listening
calculator would be unusable.
