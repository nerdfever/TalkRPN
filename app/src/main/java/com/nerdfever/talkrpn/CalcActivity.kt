package com.nerdfever.talkrpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold

/*
 * The calculator itself: [RpnEngine] behind [CalculatorDisplay], starting
 * from the settled display defaults - with the rig's tuning panel a tap
 * away for nudging them against live values. What the desktop tester
 * simulates, this shows for real.
 *
 * INPUT, FOR NOW, arrives by adb broadcast from the desktop button pad
 * (tools/engine_tester.py in watch mode) - one token word per broadcast,
 * the same vocabulary as everywhere else:
 *
 *   adb shell am broadcast -a com.nerdfever.talkrpn.TOKEN --es token 5
 *
 * No touch keypad, deliberately: the product's input is voice, and the
 * pad already exists. The speech layer will replace the receiver's feed
 * with the same tokens.
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

class CalcActivity : ComponentActivity() {

    // The engine's entry field is the display's field, so entry stops
    // exactly where the glass would run out.
    private val engine = RpnEngine(SEGMENT_FIELD_POSITIONS)

    /** The display's field: the segment font's settled size. */
    private val field =
        NumberFormatter.FieldShape(SEGMENT_FIELD_POSITIONS, punctuationCostsCell = false)

    /** What the display shows; rebuilt after every press. */
    private val values = mutableStateOf(currentValues())

    // An activity field rather than a remember, so the KNOBS receiver can
    // reach it; DisplayKnobs is snapshot state, so the display follows.
    private val knobs = DisplayKnobs()

    /** One press per broadcast: parse the word, press the engine, redraw. */
    private val tokenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val word = intent?.getStringExtra(TOKEN_EXTRA) ?: return
            val token = TokenWords.parse(word) ?: return

            engine.press(token)
            values.value = currentValues()
        }
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
     */
    private fun currentValues(): Map<String, String> =
        RegisterReadout.registerTexts(engine, field) +
            ("X" to RegisterReadout.displayText(engine, field))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setContent {
            AppScaffold {

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
                    CalculatorDisplay(values = values.value, knobs = knobs)

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
        super.onDestroy()
    }
}
