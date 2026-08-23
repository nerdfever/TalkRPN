package com.nerdfever.talkrpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.AppScaffold
import com.nerdfever.talkrpn.RpnEngine.Token
import kotlinx.coroutines.delay

/*
 * The calculator itself: [RpnEngine] behind [CalculatorDisplay], starting
 * from the settled display defaults - with the rig's tuning panel a tap
 * away for nudging them against live values. What the desktop tester
 * simulates, this shows for real.
 *
 * INPUT IS VOICE: the platform recognizer runs continuously while the
 * calculator is up (PlatformSpeechSource - the app's own microphone
 * behind a pipe, so nothing is lost between utterances), and each FINAL
 * result goes through [SpokenTokens] - atomically, an utterance applies
 * whole or not at all. Partial results deliberately do nothing yet:
 * streaming digits as they are heard needs revision handling (undo),
 * which is not built.
 *
 * The adb broadcasts remain as the test rig - one token word per TOKEN
 * broadcast (the desktop pad's watch mode), one whole utterance per
 * UTTER broadcast - the same vocabularies as the microphone:
 *
 *   adb shell am broadcast -a com.nerdfever.talkrpn.TOKEN --es token 5
 *
 * No touch keypad, deliberately: the product's input is voice.
 */

/** The broadcast the pad sends, and the extra the token word rides in. */
private const val TOKEN_ACTION = "com.nerdfever.talkrpn.TOKEN"
private const val TOKEN_EXTRA = "token"

/**
 * The knob-state broadcast, and the extra the [stateLine] rides in - what
 * the bridge (tools/knob_bridge.py) sends when knobs move on the OTHER
 * device, so the emulator's panel can drive this display live.
 */
private const val KNOBS_ACTION = "com.nerdfever.talkrpn.KNOBS"
private const val KNOBS_EXTRA = "state"

/**
 * A whole spoken utterance, parsed by [SpokenTokens] - atomically, so a
 * rejected utterance leaves the engine untouched and shows its first
 * unknown word instead. The microphone feeds this same path.
 */
private const val UTTER_ACTION = "com.nerdfever.talkrpn.UTTER"
private const val UTTER_EXTRA = "utterance"

/**
 * With no calculator activity for this long, the app finishes and the
 * watch returns to its face - a calculator left on the wrist must not
 * hold the screen and microphone forever. Only input that MOVES THE
 * MACHINE restarts the clock - an applied utterance, a pad press, a
 * successful undo. Rejected utterances deliberately do not: the open
 * microphone hears every nearby conversation, and ambient speech must
 * not hold the app awake.
 */
private const val IDLE_FINISH_MS = 3L * 60L * 1000L

/**
 * Where the machine sleeps between runs: the whole engine - stack,
 * registers, entry, undo history - as [RpnEngine.saveState] text, written
 * on every pause and restored on launch. A calculator that forgot its
 * stack on the idle timeout would be no calculator at all.
 */
private const val STATE_PREFS = "calculator_state"
private const val STATE_KEY = "machine"

/**
 * The undo trail on the glass: the last few input groups, dim and small,
 * down the usually-dark right side. Each entry is one engine mark, so
 * "undo" removes exactly one line - the trail IS the undo stack, shown.
 * It draws UNDER the register ink, so long values win the overlap.
 */
private const val TRAIL_LINES = 7
private val TRAIL_TEXT = 10.sp
private val TRAIL_END_MARGIN = 8.dp

/**
 * The rejection message: SYSTEM font, never the segment font - a rejected
 * word is a meta-message, not a register value, and segment glyphs invite
 * misreading ("3:55?" read as 3:557, the question mark passing for a 7).
 * Sits just below the X row; X keeps showing the true register.
 */
private val REJECT_TEXT = 12.sp
private val REJECT_OFFSET_BELOW_CENTRE = 42.dp

class CalcActivity : ComponentActivity() {

    // The engine's entry field is the display's field, so entry stops
    // exactly where the glass would run out.
    private val engine = RpnEngine(SEGMENT_FIELD_POSITIONS)

    /** The display's field: the segment font's settled size. */
    private val field =
        NumberFormatter.FieldShape(SEGMENT_FIELD_POSITIONS, punctuationCostsCell = false)

    /**
     * The live angle mode for the annunciator; refreshed with values.
     * DECLARED ABOVE [values] because Kotlin initialises fields in
     * declaration order and values' initialiser writes this one - below
     * it, construction dies on a null state (the splash-and-vanish
     * launch crash of 2026-08-23).
     */
    private val angleMode = mutableStateOf("DEG")

    /** What the display shows; rebuilt after every press. */
    private val values = mutableStateOf(currentValues())

    // An activity field rather than a remember, so the KNOBS receiver can
    // reach it; DisplayKnobs is snapshot state, so the display follows.
    private val knobs = DisplayKnobs()

    /** The durable diagnostic record - one row per event. Set in onCreate. */
    private var log: CalcLog? = null

    /**
     * The unknown word an utterance was rejected on, shown as "word ?"
     * in its own system-font line until the next input - DESIGN's
     * fail-visibly rule. Compose state, so the line follows it.
     */
    private val rejectedWordState = mutableStateOf<String?>(null)
    private var rejectedWord: String?
        get() = rejectedWordState.value
        set(value) { rejectedWordState.value = value }

    /** One press per broadcast: parse the word, press the engine, redraw. */
    private val tokenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val word = intent?.getStringExtra(TOKEN_EXTRA) ?: return
            val token = TokenWords.parse(word) ?: return

            rejectedWord = null
            engine.mark(word)
            engine.press(token)
            values.value = currentValues()
            activityTick.value++

            log?.record("pad", word, "applied", engine, values.value["X"].orEmpty())
            trail.value = engine.undoLabels
        }
    }

    /**
     * The trail the glass shows: the engine's own undo labels, re-read
     * after every event - derived, never tracked in parallel, so it can
     * never disagree with what undo will actually remove.
     */
    private val trail = mutableStateOf<List<String>>(emptyList())

    /** One utterance per broadcast: all of it presses, or none of it. */
    private val utteranceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            speakUtterance(intent?.getStringExtra(UTTER_EXTRA) ?: return, "utter")
        }
    }

    /**
     * One utterance into the machine - the microphone and the UTTER
     * broadcast share this path, so they can never behave differently.
     * [from] only labels the log row: "mic" or "utter".
     */
    private fun speakUtterance(utterance: String, from: String) {

        val outcome: String

        // The parser learns whether entry is still open, so a number the
        // endpointer split across utterances keeps its "e".
        val entryWasOpen = engine.entry.isNotEmpty()

        when (val result = SpokenTokens.parse(utterance, entryWasOpen)) {

            is SpokenTokens.Result.Parsed -> {
                rejectedWord = null

                // One mark per INPUT GROUP. An utterance whose first
                // token CONTINUES an open entry ("1.515" then "35
                // times") extends the previous group: no new mark, and
                // the previous label re-compacts to show the number the
                // machine actually formed - "1.51535 *", not two lines.
                val continues = entryWasOpen && result.tokens.firstOrNull()
                    .let { it is Token.Digit || it == Token.Eex }

                val merged = continues && engine.relabelLastMark(
                    SpokenTokens.trailLabel(
                        (engine.undoLabels.lastOrNull().orEmpty() + " " + utterance).trim()
                    )
                )
                if (!merged) engine.mark(SpokenTokens.trailLabel(utterance))

                result.tokens.forEach { engine.press(it) }
                activityTick.value++
                outcome = "applied ${result.tokens.size}"
            }

            // A rejected utterance shows its word but does NOT touch the
            // idle clock - this is where ambient speech lands.
            is SpokenTokens.Result.Rejected -> {
                rejectedWord = result.word
                outcome = "rejected:${result.word}"
            }

            // Undo restores the machine to before the last utterance or
            // pad press; with nothing left to undo it says so, in the
            // same asking voice as an unknown word - and only the undo
            // that DID something counts as activity.
            SpokenTokens.Result.Undo ->
                if (engine.undo()) {
                    rejectedWord = null
                    activityTick.value++
                    outcome = "undo"
                } else {
                    rejectedWord = "undo"
                    outcome = "undo-empty"
                }

            SpokenTokens.Result.Redo ->
                if (engine.redo()) {
                    rejectedWord = null
                    activityTick.value++
                    outcome = "redo"
                } else {
                    rejectedWord = "redo"
                    outcome = "redo-empty"
                }
        }

        values.value = currentValues()
        trail.value = engine.undoLabels

        log?.record(from, utterance, outcome, engine, values.value["X"].orEmpty())
    }

    /** Bumps on input that MOVED the machine - the idle clock's key. */
    private val activityTick = mutableStateOf(0)

    /** Foreground state, as Compose state so the mic follows it. */
    private val resumed = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        resumed.value = true
    }

    override fun onPause() {
        resumed.value = false

        // The machine sleeps whenever the app does - the idle finish, a
        // swipe away, the charger; every exit passes through here.
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE)
            .edit().putString(STATE_KEY, engine.saveState()).apply()

        log?.record("system", "pause", "saved", engine, values.value["X"].orEmpty())

        super.onPause()
    }

    /** A whole knob state per broadcast, applied as one. */
    private val knobsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            knobs.applyStateLine(intent?.getStringExtra(KNOBS_EXTRA) ?: return)
        }
    }

    /**
     * Every register at the current DSP - and X as the display proper
     * shows it: entry keystrokes mid-entry, the error word when raised.
     * Rejections do NOT enter here: they show in their own system-font
     * line, and X stays the truth.
     */
    private fun currentValues(): Map<String, String> {
        angleMode.value = engine.angleMode.name
        return RegisterReadout.registerTexts(engine, field) +
            ("X" to RegisterReadout.displayText(engine, field))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The diagnostic record first, so even the wake-up is a row.
        log = CalcLog(this)

        // Wake up as the machine went to sleep - a corrupt or absent
        // store simply means power-on state, per loadState's contract.
        val slept = getSharedPreferences(STATE_PREFS, MODE_PRIVATE).getString(STATE_KEY, null)
        val restored = slept != null && engine.loadState(slept)
        values.value = currentValues()
        trail.value = engine.undoLabels

        log?.record(
            "system", "start", if (restored) "restored" else "power-on",
            engine, values.value["X"].orEmpty(),
        )

        // A calculator being read must not blank mid-thought.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // And at full brightness, like the rig: this overrides the user's
        // dimmer (and auto-brightness resting below maximum) for THIS
        // window only, and reverts on leaving. It cannot reach the panel's
        // sunlight-boost nits - that headroom is sensor-driven and
        // system-owned - but it guarantees everything the slider can give.
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        // Exported so the shell (adb) may send; nothing sensitive rides
        // in, and the receivers only press calculator keys or turn the
        // same knobs the on-screen panel turns.
        registerReceiver(
            tokenReceiver, IntentFilter(TOKEN_ACTION), RECEIVER_EXPORTED
        )
        registerReceiver(
            knobsReceiver, IntentFilter(KNOBS_ACTION), RECEIVER_EXPORTED
        )
        registerReceiver(
            utteranceReceiver, IntentFilter(UTTER_ACTION), RECEIVER_EXPORTED
        )

        setContent {
            AppScaffold {

                // ---- The microphone ---------------------------------------
                //
                // Same recipe as the speech-test screen: ask for the
                // permission once, then listen continuously while in the
                // foreground, and feed each FINAL result to the parser.
                // Keyed on resultCount, not results: two identical
                // utterances in a row must both apply.
                var hasAudioPermission by remember {
                    mutableStateOf(
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> hasAudioPermission = granted }

                LaunchedEffect(Unit) {
                    if (!hasAudioPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                val source: SpeechSource =
                    remember { PlatformSpeechSource(this@CalcActivity) }

                DisposableEffect(Unit) {
                    onDispose { source.dispose() }
                }

                LaunchedEffect(hasAudioPermission, resumed.value) {

                    source.cancel()

                    if (!hasAudioPermission || !resumed.value) return@LaunchedEffect

                    source.continuous = true
                    source.preferOffline = true
                    source.start()
                }

                LaunchedEffect(source.resultCount) {

                    val heard = source.results.firstOrNull()?.text.orEmpty()
                    if (heard.isBlank()) return@LaunchedEffect

                    speakUtterance(heard, "mic")
                }

                // Recognition FAILURES reach the diary too: speech was
                // detected and nothing came back - the gap that made two
                // quiet "undo"s look like the calculator ignoring them.
                LaunchedEffect(source.failureCount) {

                    if (source.failureCount == 0) return@LaunchedEffect

                    log?.record(
                        "mic", source.lastFailurePartial.orEmpty(),
                        "no-result:${source.lastFailure ?: "unknown"}",
                        engine, values.value["X"].orEmpty(),
                    )
                }

                // The idle clock: every input restarts this effect (the
                // tick is its key), and a full quiet interval finishes
                // the activity - back to the watch face, screen and
                // microphone released.
                LaunchedEffect(activityTick.value) {
                    delay(IDLE_FINISH_MS)
                    log?.record(
                        "system", "idle", "finish",
                        engine, values.value["X"].orEmpty(),
                    )
                    finish()
                }

                // The same tuning overlay as the rig, on the same gesture -
                // tap anywhere to show or hide - so the display can be
                // nudged while showing a LIVE calculation. The knobs are
                // the activity's, shared with the KNOBS receiver.
                var showControls by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LedPalette.BACKGROUND)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showControls = !showControls }
                ) {
                    // The trail first, so register ink draws over it.
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = TRAIL_END_MARGIN),
                        horizontalAlignment = Alignment.End,
                    ) {
                        for (label in trail.value.takeLast(TRAIL_LINES)) {
                            Text(
                                text = label,
                                color = LedPalette.LABEL,
                                fontSize = TRAIL_TEXT,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    CalculatorDisplay(
                        values = values.value, knobs = knobs,
                        angleAnnunciator = angleMode.value,
                    )

                    // The rejection line - the fail-visibly voice, in the
                    // system font where a word cannot pass for digits.
                    rejectedWord?.let { word ->
                        Text(
                            text = "$word ?",
                            color = LedPalette.LABEL,
                            fontSize = REJECT_TEXT,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = REJECT_OFFSET_BELOW_CENTRE),
                        )
                    }

                    if (showControls) {
                        DisplayTuningPanel(knobs, Modifier.align(Alignment.Center))
                    }

                    GlassEdgeIfEmulator()
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(tokenReceiver)
        unregisterReceiver(knobsReceiver)
        unregisterReceiver(utteranceReceiver)
        super.onDestroy()
    }
}
