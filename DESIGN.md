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

Nothing here is built yet except the speech layer and the display font.

---

## Open questions

Everything not yet decided, in one place. Each links to the section that discusses
it. Ordered roughly by what blocks what.

**Speech**

- **Does `e` survive in context?** Measured alone it produces nothing; measured with
  neighbours it works. `2.5 e 6` should be the good case but has not been tested.
- **Biasing has never been switched on.** Gated on the vocabulary settling.
- **How often does the recognizer insert words that match nothing?** Decides whether
  erroring on unknown words is free or infuriating. The existing logs may answer it.

**Vocabulary**

- `clear` is a prefix of `clear x` — use `clear all`.
- `absolute` is a prefix of `absolute value` — drop the longer alias.
- `swap` appears twice; row 55 (`cancel`/`escape`) has no action.
- `sine` and `sign` are homophones and both are needed.
- No roll *up*. Deliberate, or an omission?

**Parser**

- **Unknown words: error or ignore?** Settled as error. What is not settled is
  whether processing stops at the bad word or the whole utterance rolls back, and
  what phonetic distance counts as "obviously a homophone".
- **Read-back** — see below. The echo problem is the open part.

**Units** — deferred entirely, but one question is already live: whether an
automatic mode switch converts stored values or only changes the default.

**Display**

- **Stroke width is probably twice what it should be.** Measured 4.45% of digit
  height on HP's own part against the 8.5% in use. To be judged on the watch.
- **The 130 advance and the 62 cell width are reconstruction figures** with no
  corroboration, and both look wrong in the same direction.
- **The 22-segment design** — merging HP-01 hooks, the Woodstock in-cell decimal
  point, and DL-3422 segment count. In progress.
- **Showing an error word needs letters**, which the seven-segment font does not
  have. This couples the error policy to the font work.

**Deferred by decision, not open** — programs, units, integer/base mode.

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

### Restarting is bounded: backoff, then give up

Continuous listening restarts the recognizer after every result, because the
platform one stops dead each time. That restart had a fixed 80 ms delay and no
limit, which is fine while the recognizer works and pathological when it does
not: on the emulator, which has no recognition service installed, every attempt
failed instantly and was retried immediately — **hundreds per second**, enough
that the process could not draw its own window. The first symptom was a *font*
screen refusing to appear.

Two bounds now:

- **Backoff.** The delay doubles per consecutive immediate failure, capped at
  30 s, and drops back to 80 ms the moment a session runs for a sensible length
  of time. An isolated hiccup costs nothing.
- **Give up** after 8 consecutive immediate failures — about 20 s with the
  doubling. Long enough to ride out a recognition service restarting or a locale
  pack installing; short enough that a broken device stops burning battery.
  `start()` clears the count, so asking again overrules the give-up.

**The trigger is elapsed time, not the error code.** The failure that prompted
this arrives as an ordinary `ERROR_CLIENT`, indistinguishable from faults worth
retrying — while `ERROR_NO_MATCH` and `ERROR_SPEECH_TIMEOUT`, which *are* the
normal outcome of a pause, must not count. What separates them is that a healthy
silent session lasts seconds and a refused one returns in milliseconds. So the
test is "did this attempt survive 400 ms without hearing anything", which catches
the whole class rather than the one code that happened to show up.

---

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

### Constraint on aliases: no token may be a proper PREFIX of another

An earlier version of this note had it backwards — it said prefixes were safe under
longest-match and only suffixes were dangerous. That is true when parsing a finished
utterance. It is false in real time, which is the case that matters.

If `clear` and `clear x` are both tokens and the user says "clear" and stops, the
calculator has to choose between two bad options: act at once, in which case
`clear x` can never be spoken; or wait to see whether `x` follows, which is exactly
the timeout that has been ruled out everywhere else. There is no third option.

**Suffixes are fine.** `root` may live inside `square root`, because `square` alone
commits to nothing — so holding it costs the user nothing and feels like listening
rather than lagging. The asymmetry is entirely about whether the shorter thing is
complete and actionable on its own.

Two consequences in the current vocabulary:

- **`clear` must go.** It is a prefix of `clear x`, *and* the shorter phrase is the
  more destructive of the two, so a dropped word turns "clear one register" into
  "wipe the machine". `clear all` fixes both at once: two words each, neither a
  prefix of the other, and a dropped word yields nothing recognisable instead of
  something catastrophic.
- **`absolute` is a prefix of `absolute value`.** Harmless in effect, since both mean
  the same token, but firing on `absolute` leaves `value` behind as a stray word.
  Simplest to drop `absolute value`.

`times ten to the` has been dropped from the vocabulary entirely, which removes the
case that prompted the original rule.

Worth enforcing at startup: assert that no token is a proper prefix of another, so
the table fails loudly the first time someone adds one.

---

## Confirming what was heard

Two mechanisms, one settled and one not.

### Unknown words raise an error rather than being ignored

A word that is not an obvious homophone of any token stops processing and is shown —
`foobar?` — rather than being discarded so the rest of the utterance can run.

The alternative, ignoring strays, looks tempting because it makes `square root` work
without `square` being a token. It is the wrong trade: a dropped or invented word
then silently changes what the remainder means, and the user has no way to tell.

**This is the same principle as the naming rules: fail visibly.** It has now decided
three separate questions — `clear all` over `clear`, dropping `squared`, and this.

Open parts:

- **Partial results must not trigger it.** The engine revises as it goes; `8086`
  arrives as `80` first. An error fired on a partial would flash constantly, so
  errors wait for a stable result and are therefore a beat behind the digits.
- **Does processing stop at the bad word, or roll the whole utterance back?**
  Stopping leaves the earlier tokens standing, which were correct. Rolling back
  treats a garbled tail as evidence the whole utterance is suspect. Undo snapshots
  make either possible.
- **What counts as "obviously a homophone"** is the phonetic matcher's threshold:
  per-word phonetic code plus edit distance. `antilock` for `antilog` must pass,
  `foobar` must not. Tunable against the logs rather than guessable.

### Reading it back aloud

The calculator says what it heard as it executes, so a spoken sequence can be
confirmed without looking at the wrist.

**The problem is echo, and it is architectural.** The microphone is deliberately open
continuously — that is what closed the deaf window — so anything the watch says, it
also hears, and would then execute again. A read-back of `pi` becomes a second `pi`.

Three ways out, in increasing order of cost:

1. **Feed silence into the recognizer while speaking.** The app owns the pipe between
   `MicStream` and the recognizer, so it can substitute silence for its own voice
   without stopping `AudioRecord`. The hardware never closes, so no deaf window
   returns — but anything the user says *over* the read-back is lost.
2. **Acoustic echo cancellation.** `AcousticEchoCanceler` exists, but it expects the
   `VOICE_COMMUNICATION` audio source, which may cost recognition quality — the very
   thing being protected.
3. **Only speak when there is something to say** — an error, or an ambiguous match —
   so the channel is silent in the normal case and echo is confined to moments when
   the user has stopped talking anyway.

**Timing is the other problem.** Six tokens read back at roughly half a second each is
three seconds of speech, by which time the user has finished the whole utterance. Per-
token read-back will always lag; read-back at the end of an utterance will not.

Worth remembering that **the display already is a read-back** — silent, instant, and
echo-free. Speaking adds value only when the wrist is not being looked at, which
argues for option 3 rather than narrating everything.

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

**Why the argument follows the word rather than coming off the stack.** Pure postfix
would mean `3 fixed` and `16 word`. Two objections, and the first is decisive: a mode
setting has no business disturbing the data. Pushing 3 to set the display format
lifts the stack and shoves T off the bottom, destroying a value the user did not ask
to lose. The second is that HP did not do it that way either — `FIX 3` is a keystroke
sequence, not a stack operation.

This is also the strongest argument for keeping the stack at four levels: the whole
machine has to be visible at once, and an indefinitely deep stack cannot be.

**`undo` must be reserved in every vocabulary.** Aborting a half-finished parsing
word is done by saying `undo`, but the token after `store` is resolved against NAMES,
where `undo` would otherwise be an ordinary name. So `undo` and its aliases are
reserved words, and cannot be used as variable names. Without this the escape hatch
is unreachable in exactly the situation it exists for.

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
| **Fixed** | Ordinary positional notation. **Overflows to Engineering** when the value will not fit. |
| **Scientific** | One digit before the radix, exponent free. |
| **Engineering** | Exponent constrained to a multiple of 3, so one to three digits sit before the radix. |

Overflowing to Engineering rather than Scientific keeps an out-of-range value in the
same power-of-1000 family as the units the rest of the calculation is expressed in,
which is the more useful reading of a number that has just grown too large to show.

Each mode takes a digit count as a following parameter — `fixed 3`, `scientific 2` —
which makes all three parsing words. See below for why the count follows the mode
rather than being taken from the stack.

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

Two names, plus an explicit base. **There is no bare `log` or `antilog`, and no
`10^x`:**

| | log | antilog |
|---|---|---|
| base e | `natural log` | `natural antilog` |
| any base | `log base <n>` | `antilog base <n>` |
| base 10 | `log base ten` | `antilog base ten`, or `ten swap raise` |

An earlier version had bare meaning base ten. Dropping it removes an irregular
special case in favour of one uniform rule, at the cost of making base 10 — probably
the commonest — three words instead of one. That trade was made deliberately.

`<n>` is any real number, so `log base e` is another way to say `natural log`.

Variable-base forms are deliberately absent: `n^x` is already `raise`, and a
variable-base log is `ln ÷ ln`.

- **Not `exponentiate`.** It shares its entire stem with `exponential`, which is the
  correct name for the *other* function. Worst possible pair for a recognizer.
- **`raise` for y^x**, because "to the power" said *after* the exponent is wrong-order
  English in postfix.
- **`antilog` is acoustically weak** and is the root of half the table. Expect
  "auto log" / "and a log" and accept them as aliases.

### `squared` is removed; square root is `root`

`square` and `squared` differ by a final consonant, which is exactly what a
recognizer drops or invents. With both `squared` and `square root` in the vocabulary,
`square root` misheard as `squared root` executes x² then √, giving |x| — a plausible
wrong number rather than an error.

Removing `squared` closes it: `squared root` then has an unknown first word and
raises an error. Removing `square root` instead does not, because `squared` remains a
valid token and still fires.

Squaring becomes `enter times` — idiomatic RPN, and acoustically distant from
everything — or `two raise`. Worth listing explicitly in the vocabulary rather than
leaving users to derive it.

---

## Vocabulary: what is still unsettled

`speech_tokens.xlsx` is the working list — roughly 60 tokens and 90 spoken forms.
It is explicitly not final. Reading it against the decisions above, these need
resolving before biasing is worth measuring:

Settled since the first pass: bare `log`/`antilog` removed deliberately rather than
missing; `drop` merged into `roll` with drop semantics, so there is no true R↓ — and
that is defensible, since rotate exists mainly to inspect a stack you cannot see, and
all four registers are on screen; `times ten to the` dropped entirely.

Still open:

| Open question | Where |
|---|---|
| **`clear` is a prefix of `clear x`** — unworkable in real time, and the shorter phrase is the more destructive. Use `clear all`. | row 35 |
| **`absolute` is a prefix of `absolute value`.** Same token, so harmless in effect, but the trailing word is left stranded. Drop the longer alias. | row 50 |
| **`swap` appears twice**, same token both times. | rows 23, 38 |
| **Row 55, `cancel`/`escape`, has no action** — vestigial now that both are `undo` aliases. | row 55 |
| **`sine` and `sign` are homophones** — and `change sign` contains the word. No recognizer will separate these reliably; the parser needs an explicit rule. | rows 17, 32 |
| **No roll *up*.** HP-21 has R↓ only, so this may be deliberate. | — |

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

### Open: stroke width may be too thin on the real watch

Nothing is resampled between devices — the layout is stored as fractions of the
screen and recomputed natively, so 396 px is drawn for 396 px. But the *absolute*
stroke width falls with the screen, and the real watch is smaller than the
emulator being tuned on:

| | Emulator (454 px) | Watch 7 40 mm (396 px) |
|---|---|---|
| X register stroke | ~2.9 px | ~2.6 px |
| Smaller registers | ~2.0 px | **~1.8 px** |

A sub-two-pixel anti-aliased stroke reads as soft grey-red rather than a crisp lit
segment — the opposite of the LED look being aimed at. The emulator flatters this
by roughly 15%.

**Do not fix this blind.** It is a look-at-it-on-the-wrist question. If it does
need addressing, the levers are fewer or larger registers, a larger
`SMALL_ROW_SCALE`, or a `STROKE` that does not scale purely with cell height —
in ascending order of how much they depart from the original font.

---

## The TalkRPN font: where it stands

`TalkRpnFont.kt` is the 32-element cell — HP-01 styling, DL-3422 segment count.
`TalkRpnGlyphs.kt` maps all 95 printable ASCII characters onto it, derived from
Litronix's published set and then corrected glyph by glyph against it.

### Units: segment E/F to segment B/C is 1

One unit, used for everything, horizontally and vertically alike. It is the left
column's centreline to the right column's — the cell width, measured where the
ink's *middle* is, not where its edge is. Pitch, vpitch, stroke, cap height, the
decimal point's offset: all in that unit, all directly comparable.

The geometry was drawn on a working grid where the cap height was 100 — the
HP-01's centreline box scaled by 100/91.5. Every figure is its value on **that**
grid divided by **58.47**, the cell width there.

That matters: it is a pure rescale, so **not one vertex moves**. Re-deriving the
numbers afresh from the HP-01's own 53.5 would have been prettier by a digit or
two and would have shifted points by up to 0.04% — which is not a licence a font
already corrected glyph by glyph should be given.

| | units | from |
|---|---|---|
| cell width (the definition) | 1 | 58.47 / 58.47 |
| centre axis | 0.5 exactly | 29.235 / 58.47 |
| cap height, D to A | 1.71028 | 100 / 58.47 |
| hook radius | 0.13545 | 7.92 / 58.47 |
| stroke | 0.14747 | 16 / 108.5, measured |
| `PITCH` — cell to cell, HP-01's own | 2.43031 | 142.08 / 58.47 |
| full height including descenders | 2.46280 | cap × 1.44 |
| ink height, top of `A` to bottom of the descender bar | 2.61027 | + one stroke |
| `VPITCH` — baseline to baseline | 2.75 | chosen |

The payoff in reading it: since the cell is exactly 1 wide, **pitch minus 1 *is*
the clearance** between neighbouring cells, and the ink meets at pitch = 1 +
stroke. No arithmetic needed to see whether a pitch is viable.

The constants are written in code as the division rather than as decimals, so the
grid figure stays visible and no digit is invented in the conversion.

Millimetres enter at exactly **one** place: whoever draws the font picks a size,
and that fixes everything else. So the display's *shape* is fully specified by
the numbers above and only its *size* depends on hardware. That was not true
before — row spacing used to be a fraction of the screen diameter while pitch was
a multiplier on the advance, two incompatible units that could not be compared
and did not transfer together.

`vpitch` is baseline-to-baseline rather than gap-between-rows, so it keeps
meaning the same thing when adjacent rows are different sizes — which they are,
since every register but X is scaled down. A uniform *gap* between unequal rows
puts the baselines at unequal distances, and the baselines are what the eye
reads. Making the pitch uniform means the gaps now differ, which is the way round
that looks even.

**`Hp01Font` does not share the unit.** Its coordinates are the *outer* ink box,
so its E/F-to-B/C span is 53.5 of its own 62 — a length copied between the two
fonts unchanged is wrong. Both fonts now declare `UNIT_SPAN`, and layout code
scales by it rather than assuming.

Two floors fall out of this, both from ink rather than taste:

- **pitch ≥ 1.14747** — cell width plus one stroke, below which neighbours
  overlap outright. The slant makes it *look* tight well before that, because
  one cell's top-right passes close to the next cell's bottom-left, but those
  sit at different heights and never actually touch. All caps read well from
  about 1.45 to 1.80.
- **vpitch ≥ 2.61027** — the ink height, below which descenders reach the row
  beneath. A digits-only display could go far tighter; a seven-segment font has
  no descenders at all, which is why `DisplayTestActivity` still sits at 1.96682.

Divergences from the DL-3422, all deliberate:

- **Most of the set takes the HP-01's hooked corners** — digits `0 2 3 5 7 8 9`,
  letters `A C G O Q S`, the lowercase bowls, `( & @` — and `4` its short left
  side. `A3`/`A4` and `D3`/`D4` are alternative corner pieces, never both lit.
  (`&` briefly lit both, until the corner review settled on the hook alone.)
- **When a corner is hooked, the column stub goes dark.** `F2` with `A3`, `E2`
  with `D3` — otherwise the column spikes past the arc.
- **Digits are full-width**, on `B`/`C`. Half-width digits on `P`/`Q` were tried
  — they keep `5` and `S` apart — but reverted: the HP-01 width looks right and
  `0` and `O` were briefly identical; `O` now takes the four square corners while `0` keeps the hooks. `1` and `l` stay distinct — the right column against the centre column.
- **The decimal point and comma live inside the character cell** rather than
  consuming one of their own.

### Open font work

| | |
|---|---|
| **Horizontal placement** | Half-width glyphs sit in different halves of the cell — digits and most lower case on the left, `p` on the right, `i j l` in the centre. At a fixed pitch that reads as uneven spacing even though the pitch is exact. Needs one rule, applied throughout. |
| ~~Diagonals to the corners~~ | **Done.** Segments H, I, K and L all reach the exact cell corners now. Moving segment H's and segment L's LEFT ends there was the change that mattered — it improved about fifteen glyphs (`/ \ X * M N Y V W v`) and cost reverting `&` to `A4` and `a e` to `D4`. |
| ~~Semicolon~~ | **Done.** `COL2_TAIL`, the 31st element: the comma's tail hung off the lower colon dot, so a semicolon is a colon whose lower dot grew a tail. |
| ~~Comma taper~~ | **Decided: keep the constant-width tail.** More period-correct — though the real LED calculators drew no commas at all. |
| **Segment `M` (the descender stem) is too tall** | Not the letter M. `DESCENDER_FRACTION` is 0.44 of the cap height, against an x-height of half of it, so `g q y j` tails plunge nearly as deep as their bowls are tall. Now one number: lower `DESCENDER_FRACTION` and `TOTAL_HEIGHT`, the N/O bar and segment M's endpoint all follow. |
| ~~`P`/`Q` shift~~ | **Decided against, 2026-08-09.** They looked as though they wanted to move left a little in `B` and `D`. Leaving them where they are. |
| **Decimal-point position** | *Half done.* The dot belongs to the **gap** between cells, not to a cell, so a fixed `DP_X = 1.48179` is only right at pitch 2.43031 — tighten the pitch and the gap shrinks underneath a dot that has not moved. `DP_GAP_FRACTION` and `dpXAt(pitch)` now express it pitch-independently, and the specimen renderer uses them. **Still to do:** `TalkRpnFont` bakes `DP_X` into its paths at object-init, so `draw()` has to take a pitch before the font itself follows suit. |
| ~~Stroke width~~ | **Measured: 0.147** — see below. The test screen now brackets it from 0.5x to 1.5x, starting at the measurement. |
| ~~`l` and `\|` are identical~~ | **Fixed.** `\|` is `P Q M`, running the full cell including the descender, per DL-3422 typography; `l` is `P Q` alone. |
| ~~`0` and `O` are identical~~ | **Fixed, 2026-08-11:** `O` now takes four square corners while `0` keeps the hooks, so the two differ. `1` and `l` remain distinct — right column against centre column. |
| ~~Parens vs brackets~~ | **Done.** Both parens are now properly curved: `(` is the left column with both corners hooked; `)` is `RPAR`, the whole right parenthesis as ONE element (bar stub + arc + column + arc + bar stub — the right side has no shortened bars for a bare arc to join). It began as two halves, `A5` and `D5`, but nothing ever lit one without the other so they merged. 32 elements; the mask is a `Long`. What was rejected earlier was a *mismatched* pair, not curvature. |
| **Not yet wired in** | `DisplayTestActivity` still renders with `Hp01Font`. The calculator display needs moving onto `TalkRpnFont`. |
| **Still guessed** | `'`, `f` and `t` — the chart is ambiguous at those three. |
| **Final LED colour** | *Answered on the physics; still to be judged on the watch.* See below — the emitter is 655–660 nm and the closest sRGB is **`#FF0000`**. |

### How wide was the stroke, really?

**0.147** — a 16 px stroke against 108.5 px centre-to-centre, measured in GIMP off
a microscope photograph of a real HP-55 (`PXL_20260811_140126781.jpg`).

That corrects a value of 0.0795 recorded here earlier, and the correction is
worth keeping because the wrong number came from two things that both looked
sound:

- **A note claiming 4.45% of digit height on HP's own part.** Whatever that
  measured, the photographs do not support it.
- **My own threshold measurement of a second, sharper photograph**, which read
  5 px on a 49 px cell — about 0.10. The threshold was cutting *inside* the
  stroke: run on the microscope frame, the same method reads 21–26 px where the
  edge is visibly at 16.

Two mistakes on the way, both mine, both instructive:

- **FWHM was tried and is invalid** on the *sharper* frame, where the core is
  clipped: it assumes an unsaturated profile, so the half-max point sits out in
  the halo. It read high, at 0.062 of cap height, which looked like evidence and
  was noise.
- **I argued the measurement down as "bloomed" and was wrong.** An edge profile
  across the microscope frame's left column settles it: the plateau is 190 of a
  ~210 range, so nothing is railed, and the top is flat over ~9 px. That is not
  an overexposed image. The 90% width is 12 px and the half-max width ~23, so a
  visual reading of 16 sits sensibly between them. Whatever softness there is
  comes from the epoxy bubble lens, and being symmetric it does not bias fat.

So the HP-01's own 9.29 — 0.159 in this unit — was close to right all along, and
the claim that it was "twice what it should be" was simply wrong.

**Measure the denominator centre-to-centre.** The first attempt paired a 16 px
stroke with a 124.5 px *outer* width, which double-counts: outer width already
contains one stroke, so the ratio comes out 0.128 when the same two numbers
actually say 0.147. `outer − stroke = centre-to-centre` is the identity, and it
has the useful property that bloom cancels in it exactly — bloom pushes outer
edges out and inner edges in by the same amount. That is why the *widths* from
two photographs at 2.2× different magnification agree to within 0.1%, while
their strokes disagree by 45%.

### What the segments are actually made of

Magnifying the same photograph shows the decimal point is a **square die**, and
every segment is **beaded out of small rectangular dies** strung end to end. Two
consequences, both now in the font:

- **Dots are square**, `DOT_SIDE = 2 × STROKE`, not round.
- **The pen does not rotate.** Stroking cuts every end square to the direction
  that path happens to run, so a diagonal gets an end sliced at 30° while the bar
  beside it gets one cut flat — put an `X` next to a `Y` and it is obvious — and
  mitred joins throw spikes. Each straight segment is instead a **polygon swept
  by a fixed-orientation nib**: horizontal bars get a vertical nib, everything
  else a horizontal one. A diagonal ends up ~14% thinner measured perpendicular
  than a bar, which is what a fixed nib does. Curves keep a perpendicular
  thickness — they turn a bar into a column, and a fixed nib would pinch them to
  nothing at one end.
- **The end rule, final — the DIE policy, chosen 2026-08-11:** an end extends
  half a stroke **only when a perpendicular axis-aligned segment shares the
  endpoint**; everything else ends flat at the centreline, exactly as the
  separate dies on a real DL-3422 do; hooks never take an extension; curves get
  nothing. The support case fills every corner and L-turn — `7`'s top-right,
  `h`'s shoulder — with the two overshoots landing exactly flush. Everywhere
  else the end is a die edge: a lone `1` really is half a stroke shorter than
  the `0` beside it, `v`'s foot is flat with the diagonal melding in, lowercase
  tops sit dead on the x-height line. Chosen over the “ink box” alternative
  (uniform outer rectangle, but heel kinks where diagonals meld into extended
  feet) from a side-by-side. Five earlier designs each died on an artefact Dave
  caught: blanket extension (eaves past diagonals, spurs on `n`), boundary
  extension (heels under `v w`), corner patches (nubs on lone tips, unequal `"`
  ticks), slope-extension of free tips (kinked boots), and the original
  rotating pen (angled ends, bumped hooks).

### What colour were they, really?

Both datasheets state it outright — no reconstruction needed:

| part | peak wavelength |
|---|---|
| HP 5082-7400 (the HP-35/HP-01 family bubble) | **655 nm** |
| Siemens DL-3422 (the 22-segment part) | **660 nm** |

Both are GaAsP, the standard red LED chemistry of the period, and both sit at the
far red end of the spectral locus:

| | CIE 1931 x | y |
|---|---|---|
| 655 nm | 0.7283 | 0.2717 |
| 660 nm | 0.7300 | 0.2700 |

**No consumer display can show that hue.** It is outside every gamut in use — the
sRGB red primary falls short by 0.088 in x, Display P3 by 0.048, even Rec.2020 by
0.020. So the question is not "what is the colour" but "what is the closest
reachable one", and the two standard ways of answering disagree *visibly*:

| mapping | result | ΔE2000 |
|---|---|---|
| Clip the negative components — maximum saturation | **`#FF0000`** | **7.8** |
| Desaturate toward white, preserving dominant wavelength | `#FF0052` | 17.0 |

The second is not a mistake: `#FF0052` genuinely has 655 nm as its dominant
wavelength, at 63% purity. But the line from 655 nm to the white point exits the
gamut through the **magenta** edge, so it lands on pink — and against a black
background, saturation carries the impression far more than dominant wavelength
does. CIEDE2000 agrees, at less than half the error.

**So: pure `#FF0000`.** The current `LED_RED` is `#E81810`, which mixes in a
little green and blue and so reads slightly duller and browner than the emitter
did. Worth noting the watch's OLED covers P3 — rendering in a wide-gamut space
would get the red primary from x = 0.64 to x = 0.68, which is a real step closer
and free.

Two caveats before treating this as settled. The emitter is only part of what the
eye saw: light left these parts through a moulded epoxy bubble lens, and at the
currents they ran at the segments bloomed. And photographs of the real things are
*not* good evidence — sensors clip hard on saturated red and shift it orange. The
number above is where to start, not where to stop; the bloom and scatter Dave
wants to chase are the rest of it.

### How many segments is too many? (open question, worth deciding early)

The point was never to reproduce a 1970s LED bubble display. It was to borrow
its *style*. And we are not constrained the way it was:

| Their constraint | Ours |
|---|---|
| Every segment needs a bond pad, a pin and a multiplex line | None. A segment is a `Path` |
| Segments are straight diffused regions on a monolithic die | Any curve is free |
| Die area and yield | None |
| Gaps between emitters are physically necessary | We already abolished them — segments meet flush |

So a dozen more segments would cost nothing and every letter could be made
handsome. The question is what that would destroy.

**A first answer: the style is the visible kit.** What reads as "segment
display" is not the segment *count* — it is that you can see each glyph
assembled from a small, fixed, reusable set of strokes, and see the same set
recurring in every character. Add segments and glyphs converge on their true
outlines; the kit stops being legible; and at some point it is no longer a
segment font but a slightly stiff sans-serif. The count matters only because it
is what keeps the kit visible.

**A second answer, less comfortable: the wrongness is the charm.** A `W` that is
really two columns with a peak between them, an `m` that is a compromise, a `V`
that cannot close because there is no lower-right diagonal — these are what say
*this is a machine approximating a letter*. Fix them all and the font stops
saying it.

That suggests a rule for spending new segments:

> Spend them on **legibility** failures, not **fidelity** failures.

- A semicolon that **cannot be drawn at all** is a legibility failure. Buy the
  segment.
- Two characters that are **indistinguishable** — `l` and `|`, or `(` and `[`
  before the hooks — is a legibility failure. Buy the segment.
- A `W` that is readable but not beautiful is a fidelity failure. Leave it.

**Settled by looking at it: more rounding ruins the effect.** A single rounded
`(` sitting among square glyphs was enough to answer it. The HP-01 look depends
on corners being *mostly* square, with the left-hand curl as the exception that
reads as a signature. Round more and it drifts toward a 1980s
vacuum-fluorescent face. So: no further rounding, and the only new element bought
is the one the semicolon needed.

Two corrections to what was first written here, both worth keeping:

- **The cost was quoted wrong.** "Two more hook pairs would take us to 32" is
  false. The left corner needs three pieces beyond the main bar and column —
  `A4` (square stub), `A3` (arc), `F2` (column stub). The right side has no
  stubs: `A2` and `B` run straight to the corner, so an arc there would sit
  inside square ink and round nothing. Making the right corner *choosable* means
  splitting `A2`, `B`, `D2` and `C` into main + stub too: **+6, so 36 elements,
  and the mask becomes a `Long`.**
- **Going fully round would have been *cheaper*, not dearer** — drop
  `A4 D4 F2 E2`, add two arcs, and the font falls to 28 elements. The
  square-or-hooked *choice* is what costs; the curves are nearly free. Cost and
  boldness pointed in opposite directions here, and the look decided it.

A method note, since it cost a round trip. The mismatched `(` `)` pair was
described accurately in three places and still shipped. *"No hook exists on the
right"* is a fact about the segment table; *"that pair looks broken"* is a fact
about the glyph. Reasoning carefully about the first is not a substitute for
rendering the second and looking at it.

**The `Int` ceiling was crossed on 2026-08-07, deliberately, for the parens.**
The mask is now a `Long`: 33 elements, 31 bits spare. The earlier framing —
"their budget was pins, ours is bits, both land at about the same number" —
stays true in spirit: the discipline of paying for each element is what keeps
the kit legible, and the rule for spending stands (legibility failures yes,
fidelity failures no). The parens qualified: an unmatched pair is a legibility
failure, and `A5`/`D5` were the cheapest matched fix.

### The cell outline might be a feature, not just a tool

The bounds box drawn for diagnosing layout — a muted cyan hairline (`#3D8B96`,
1.2 cell units) against the LED red — reads better than it has any right to. It
was built to answer "where does the ink sit inside its fixed pitch", but the
contrast against the red is worth trying on the calculator display itself. Judge
it on the watch before committing: a hairline that looks crisp on a 1700 px page
may not survive at 2 px on the wrist.

### A rendering bug worth remembering

Segments were drawn one stroke at a time, which looked right at heavy strokes and
wrong at light ones: where two overlap, the second stroke's antialiased edge
blends over the first, and the doubled coverage reads as a **brighter** line. A
real display has no such seam. Every lit bar now goes into one `Path`, stroked
once, so an overlap is painted exactly as often as anything else. The PDF
renderer had the same bug and the same fix.

### Reviewing it

`font_design/talkrpn_font_reference.pdf` is the artefact: geometry diagram,
centreline listing, then every glyph drawn over its unlit ghost inside its cell
bounds, with the lit segment names printed underneath — so a correction can be
dictated as segment names rather than described as a shape.
`make_font_reference.ps1` regenerates it and **mirrors** the Kotlin table; if the
two disagree, the Kotlin wins.

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

