package com.nerdfever.wdbtile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/*
 * The same toggle as the tile, as a plain launcher app - for when the app list
 * is closer to hand than the tile carousel, and for exercising the toggle from
 * adb on the emulator, where tiles are awkward to drive.
 */

// ---- Tweakables ------------------------------------------------------------

/** The lit-LED red, matched by eye to the calculator's display red. */
private val COLOUR_ON = Color(0xFFFF0000)

/** The unlit state: the grey of the calculator's labels. */
private val COLOUR_OFF = Color(0xFF8A8A8A)

/** Text sizes. */
private val TITLE_SP = 24.sp
private val STATE_SP = 40.sp
private val HINT_SP = 14.sp

class WdbActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            // What the display believes; re-read after every tap.
            var on by remember { mutableStateOf(wdbIsOn(this)) }
            var denied by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        denied = !wdbSet(this@WdbActivity, !wdbIsOn(this@WdbActivity))
                        on = wdbIsOn(this@WdbActivity)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text("WDB", color = COLOUR_OFF, fontSize = TITLE_SP)

                if (denied) {
                    Text("NO PERMIT", color = COLOUR_OFF, fontSize = HINT_SP)
                    Text("grant via adb", color = COLOUR_OFF, fontSize = HINT_SP)
                } else {
                    Text(
                        if (on) "ON" else "OFF",
                        color = if (on) COLOUR_ON else COLOUR_OFF,
                        fontSize = STATE_SP
                    )
                }
            }
        }
    }
}
