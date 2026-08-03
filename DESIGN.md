# TalkRPN — design decisions

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
| deaf window between utterances | **~2000 ms** | none |

Vosk was removed but stayed in git history. The `SpeechSource` interface it forced
into existence was kept: it cost nothing, it is what made the comparison possible,
and a future engine drops in without the calculator noticing.

**The one open weakness** is the deaf window — the platform recognizer closes the
microphone after every result and takes ~2 s to reopen it. Anything said in that
window is lost. `EXTRA_SEGMENTED_SESSION` (API 33) exists to keep a session open
across utterances and is the first thing to try if this proves annoying in use.

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
carrying the most meaning. Vosk rendered it `ie`, `he`, `east`, or dropped it
entirely — and a dropped `e` turns 2.5e6 into 2.56 silently. Needs testing on the
platform engine before it is committed to.

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

## Sign handling

    negative  →  set sign negative   (idempotent)
    negate    →  flip sign           (also: change sign, c h s)

Better than HP's single CHS, and specifically better *for voice*: on a keyboard you
can see the sign before pressing. Speaking, you often can't — so an idempotent "make
it negative" is safe to say without looking, and a toggle isn't.

---

## Undo

Snapshot the whole machine — four stack registers, LastX, the STO registers, mode
flags. A ring buffer of snapshots gives multi-level undo for negligible memory. The
state is small enough that nothing cleverer is warranted.

---

## Units (later, but decide the value type now)

Plus42-style units are a natural fit for voice — saying "five point two kilometres"
is far easier than keying a unit. The open-vocabulary engine makes the extra
vocabulary free.

**Do not build it yet.** But make the value type carry an optional unit from day one
— `Value(magnitude, unit?)` rather than a bare `Double` — so units can be added
later without rewriting the stack, display and parser.

One question still open: on switching unit systems, do stored values *convert*
(10 gallons becomes 37.85 litres, changing the number on screen), or do they keep
their entered unit with the mode only setting the default for new entries? The first
is more surprising mid-calculation.

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
