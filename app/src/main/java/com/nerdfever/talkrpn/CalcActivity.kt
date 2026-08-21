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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold

/*
 * The calculator itself: [RpnEngine] behind [CalculatorDisplay], both at
 * their settled defaults. What the desktop tester simulates, this shows
 * for real.
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

class CalcActivity : ComponentActivity() {

    private val engine = RpnEngine()

    /** The display's field: the segment font's settled size. */
    private val field =
        NumberFormatter.FieldShape(SEGMENT_FIELD_POSITIONS, punctuationCostsCell = false)

    /** What the display shows; rebuilt after every press. */
    private val values = mutableStateOf(currentValues())

    /** One press per broadcast: parse the word, press the engine, redraw. */
    private val tokenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val word = intent?.getStringExtra(TOKEN_EXTRA) ?: return
            val token = TokenWords.parse(word) ?: return

            engine.press(token)
            values.value = currentValues()
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

        // Exported so the shell (adb) may send; nothing sensitive rides in,
        // and the receiver only ever presses calculator keys.
        registerReceiver(
            tokenReceiver, IntentFilter(TOKEN_ACTION), RECEIVER_EXPORTED
        )

        setContent {
            AppScaffold {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LedPalette.BACKGROUND)
                ) {
                    CalculatorDisplay(values = values.value)

                    GlassEdgeIfEmulator()
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(tokenReceiver)
        super.onDestroy()
    }
}
