package com.nerdfever.wdbtile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

/*
 * The same toggle as the tile, as a plain launcher app - for when the app list
 * is closer to hand than the tile carousel. Unlike the tile it POLLS, so a
 * wedged adbd shows up here within a second of dying rather than at the next
 * tile refresh.
 */

// ---- Tweakables ------------------------------------------------------------

/** The calculator display's neon orange, so ON matches its look. */
private val COLOUR_ON = Color(0xFFFF5F1F)

/** The unlit state: the grey of the calculator's labels. */
private val COLOUR_OFF = Color(0xFF8A8A8A)

/** The wedged state: the dot font's neon orange - alarming, and not red. */
private val COLOUR_WEDGED = Color(0xFFFF5F1F)

/** Text sizes. */
private val TITLE_SP = 24.sp
private val STATE_SP = 40.sp
private val HINT_SP = 14.sp

/** How often the shown state is re-read while the screen is up. */
private const val POLL_MS = 800L

class WdbActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            // The whole truth - setting AND live port - re-read on a timer, so
            // the screen tracks adbd dying without needing a tap.
            var state by remember { mutableStateOf(wdbState(this)) }
            var port by remember { mutableStateOf(wdbLivePort()) }
            var denied by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                while (true) {
                    state = wdbState(this@WdbActivity)
                    port = wdbLivePort()
                    delay(POLL_MS)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        denied = !wdbSet(this@WdbActivity, !wdbIsOn(this@WdbActivity))
                        state = wdbState(this@WdbActivity)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text("WDB", color = COLOUR_OFF, fontSize = TITLE_SP)

                if (denied) {
                    Text("NO PERMIT", color = COLOUR_OFF, fontSize = HINT_SP)
                    Text("grant via adb", color = COLOUR_OFF, fontSize = HINT_SP)
                } else when (state) {

                    WdbState.OFF ->
                        Text("OFF", color = COLOUR_OFF, fontSize = STATE_SP)

                    WdbState.ON -> {
                        Text("ON", color = COLOUR_ON, fontSize = STATE_SP)
                        // The live port: proof of life, and enough to connect
                        // to directly without an mdns scan.
                        Text(":$port", color = COLOUR_ON, fontSize = HINT_SP)
                    }

                    WdbState.WEDGED -> {
                        Text("WEDGED", color = COLOUR_WEDGED, fontSize = STATE_SP)
                        Text("tap twice to revive", color = COLOUR_OFF, fontSize = HINT_SP)
                    }

                    WdbState.UNSURE -> {
                        Text("ON?", color = COLOUR_ON, fontSize = STATE_SP)
                        Text("liveness unreadable here", color = COLOUR_OFF, fontSize = HINT_SP)
                    }
                }
            }
        }
    }
}
