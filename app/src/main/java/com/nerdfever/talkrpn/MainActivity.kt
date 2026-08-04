package com.nerdfever.talkrpn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import kotlinx.coroutines.delay
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/*
 * A free-running listening tool.
 *
 * Say things; watch what comes back. The live guess is the largest thing on screen
 * and updates as you speak, with finished utterances stacking underneath.
 *
 * The engine question is settled: Android's platform recognizer, measured against a
 * bundled Vosk model over a long evening. Vosk was removed, but the SpeechSource
 * seam it forced into existence was kept - see that file for why.
 */

// ---------------------------------------------------------------------------
// Tweakables.
//
// Everything adjustable about this screen lives here. Sizes carry their unit in
// the Compose type rather than the name: Dp is a density-independent length, sp
// is the same but scaled by the user's accessibility font setting. Durations are
// milliseconds and say so.
// ---------------------------------------------------------------------------

private val GOOD = Color(0xFF6BD46B)
private val BAD = Color(0xFFE06C6C)
private val NEUTRAL = Color(0xFFBFBFBF)
private val HEADING = Color(0xFFE8C36B)

/** How many finished utterances to keep on screen. */
private const val HISTORY_DEPTH = 8

/** How often the airplane and network indicators re-read system state. */
private const val STATUS_POLL_INTERVAL_MS = 1000L

/** Global setting backing the Developer-options wireless debugging toggle. */
private const val ADB_WIFI_SETTING = "adb_wifi_enabled"

/**
 * Screen inset.
 *
 * A round screen is narrowest at top and bottom, so the sides can be tighter than
 * the ends. The bottom is deepest because that is where the scroll comes to rest.
 */
private val PAD_SIDE = 22.dp
private val PAD_TOP = 28.dp
private val PAD_BOTTOM = 40.dp

/** Vertical gaps, smallest to largest. Named by role so each can move alone. */
private val GAP_TIGHT = 4.dp
private val GAP_SMALL = 6.dp
private val GAP_MEDIUM = 8.dp
private val GAP_LARGE = 10.dp
private val GAP_BLOCK = 12.dp
private val GAP_SECTION = 14.dp
private val GAP_BOTTOM = 20.dp

/** Type sizes. The live partial is deliberately the largest thing on screen. */
private val TEXT_LIVE_PARTIAL = 22.sp
private val TEXT_HISTORY = 15.sp
private val TEXT_BUTTON = 13.sp
private val TEXT_BUTTON_SMALL = 11.sp
private val TEXT_HEADING = 12.sp
private val TEXT_DETAIL = 11.sp

class MainActivity : ComponentActivity() {

    /**
     * Whether this screen is in the foreground.
     *
     * The microphone is released the moment it is not. Two reasons: leaving the app
     * does not necessarily destroy the composition, so a second visit would otherwise
     * create a second recognizer while the first was still listening - two of them
     * competing for one microphone. And the standing design rule is that this app
     * listens only while it is being used.
     */
    private var resumed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        // Wear OS dismisses an app back to the watch face after a spell without touch
        // input - and talking to it is not touch input. Without this the app vanishes
        // mid-sentence, which releases the microphone via onPause and looks like the
        // recognizer failing.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                AppScaffold {
                    ListenScreen(resumed = resumed)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }
}

/** One finished utterance, kept for the on-screen history. */
private data class Heard(
    val text: String,
    val totalMs: Long?,
    val thinkMs: Long?,
)

@Composable
private fun ListenScreen(resumed: Boolean) {

    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    val source: SpeechSource = remember { PlatformSpeechSource(context) }

    DisposableEffect(Unit) {
        onDispose { source.dispose() }
    }

    var history by remember { mutableStateOf<List<Heard>>(emptyList()) }

    // Durable record, so a dropped ADB link no longer costs us a test session.
    val resultLog = remember { ResultLog(context) }

    // Log every partial. Wired as a callback rather than observed from Compose,
    // because several partials arrive between recompositions and an observer would
    // silently miss most of them.
    DisposableEffect(source) {

        var lastText = ""

        source.onPartial = { text, atMs, fresh ->

            // A hypothesis that no longer extends the previous one is a new utterance.
            if (!text.startsWith(lastText.take(3)) || lastText.isEmpty()) resultLog.beginUtterance()
            lastText = text

            resultLog.recordPartial(text, atMs, fresh)
        }

        onDispose { source.onPartial = null }
    }

    var preferOffline by remember { mutableStateOf(true) }
    var debugToggleResult by remember { mutableStateOf<String?>(null) }

    // ---------------------------------------------------------------------
    // Live network and airplane state.
    //
    // NET_CAPABILITY_VALIDATED is the system's own verdict that a network really
    // carries traffic - it runs the check itself, so we neither ping anything nor
    // need INTERNET permission. That matters: the app still cannot reach the network,
    // which is what keeps "no cloud" a property rather than a promise.
    //
    // Polled rather than observed via callback: two independent sources of truth,
    // one loop, no lifecycle to get wrong, and 1 Hz costs nothing.
    // ---------------------------------------------------------------------
    val connectivity = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    var airplaneOn by remember { mutableStateOf(false) }
    var networkLive by remember { mutableStateOf(false) }
    var networkKind by remember { mutableStateOf("none") }

    // Keyed on `resumed`, not Unit. A LaunchedEffect is tied to the composition, and
    // leaving this screen stops the activity without destroying it — so keyed on Unit
    // this loop would keep waking once a second, forever, while you looked at the
    // watch face. Keying on the lifecycle cancels it on the way out and restarts it
    // on the way back.
    LaunchedEffect(resumed) {

        if (!resumed) return@LaunchedEffect

        while (true) {

            airplaneOn = runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            }.getOrDefault(false)

            val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }

            networkLive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

            networkKind = when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "other"
            }

            delay(STATUS_POLL_INTERVAL_MS)
        }
    }

    // Listen continuously while in the foreground.
    LaunchedEffect(hasAudioPermission, resumed, preferOffline) {

        source.cancel()

        if (!hasAudioPermission || !resumed) return@LaunchedEffect

        source.continuous = true
        source.preferOffline = preferOffline
        source.start()
    }

    // Record each finished utterance — to screen, and to the file.
    LaunchedEffect(source.results) {

        val text = source.results.firstOrNull()?.text.orEmpty()

        if (text.isBlank()) return@LaunchedEffect

        history = (listOf(Heard(text, source.totalMs, source.processingMs)) + history)
            .take(HISTORY_DEPTH)

        resultLog.record(source, preferOffline)
    }

    // Record failures too. Without these the file only shows what worked, which is
    // how "I said 8087 and got nothing" left no evidence at all.
    LaunchedEffect(source.failureCount) {

        if (source.failureCount == 0) return@LaunchedEffect

        resultLog.recordFailure(source, preferOffline)

        history = (listOf(
            Heard("✗ ${source.lastFailurePartial ?: source.lastFailure ?: "no match"}", null, null)
        ) + history).take(HISTORY_DEPTH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = PAD_SIDE, end = PAD_SIDE, top = PAD_TOP, bottom = PAD_BOTTOM),

        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        if (!hasAudioPermission) {

            Detail("Microphone permission needed.", BAD)
            Gap(GAP_MEDIUM)

            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Grant microphone", fontSize = TEXT_BUTTON)
            }

            return@Column
        }

        // When we own the microphone it never closes, so the honest indicator is
        // simply "capturing" — the recognizer restarting behind the scenes no longer
        // costs any audio. Only when it refuses the piped source does the old
        // stop-start behaviour, and the old warning, still apply.
        val ownMic = (source as? PlatformSpeechSource)?.usingOwnMic == true
        val micOpen = source.state == TrialState.Listening || source.state == TrialState.Hearing

        Detail(
            text = when {
                source.state == TrialState.Failed -> source.message ?: "failed"
                ownMic -> "● MIC OPEN (continuous)"
                micOpen -> "● MIC OPEN"
                else -> "○ deaf — wait"
            },
            colour = if (ownMic || micOpen) GOOD else BAD,
        )

        if (!ownMic) source.deafWindowMs?.let { Detail("last gap ${it}ms", NEUTRAL) }

        Gap(GAP_LARGE)

        // The live guess — the largest thing on screen, updating as you speak.
        Text(
            text = source.partial.ifBlank { "…" },
            color = if (source.partial.isBlank()) NEUTRAL else GOOD,
            fontSize = TEXT_LIVE_PARTIAL,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Gap(GAP_BLOCK)

        // ------------------------------------------------------------------
        // Test controls.
        // ------------------------------------------------------------------

        // Whether the network genuinely works, independent of any of the toggles.
        // Reported by the system, not inferred from the airplane setting — those can
        // disagree, and when they do that is exactly what we want to see.
        Detail(
            text = when {
                networkLive -> "network: LIVE ($networkKind)"
                networkKind != "none" -> "network: $networkKind, NOT validated"
                else -> "network: none"
            },
            colour = if (networkLive) GOOD else BAD,
        )

        Gap(GAP_MEDIUM)

        // A request to the engine, not a guarantee — see SpeechSource.preferOffline.
        Button(onClick = { preferOffline = !preferOffline }) {
            Text(if (preferOffline) "prefer offline: ON" else "prefer offline: OFF", fontSize = TEXT_BUTTON_SMALL)
        }

        Gap(GAP_SMALL)

        // Wi-Fi itself is untouchable by apps since Android 10 — setWifiEnabled just
        // returns false regardless of permissions. Airplane mode, however, is a plain
        // global setting, and WRITE_SECURE_SETTINGS (granted over ADB) can write it.
        //
        // Whether the system *acts* on the write is the open question: applying it
        // normally needs a protected broadcast that apps cannot send. On some versions
        // the setting is observed directly and it just works. Cheap to find out, and
        // it is the only route to an in-app network toggle.
        Button(onClick = {
            debugToggleResult = runCatching {

                val on = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
                Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (on == 1) 0 else 1)

                // Ask the system to apply it. Ignored unless we are privileged, but
                // harmless to attempt.
                runCatching {
                    context.sendBroadcast(
                        Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", on != 1)
                    )
                }

                null

            }.getOrElse { "airplane toggle refused: ${it.javaClass.simpleName}" }
        }) {
            Text(if (airplaneOn) "airplane: ON" else "airplane: OFF", fontSize = TEXT_BUTTON_SMALL)
        }

        Gap(GAP_SMALL)

        // Fallback: if writing the setting turns out not to be honoured, the settings
        // screen still works. Kept until we know whether the toggle above does anything.
        // No FLAG_ACTIVITY_NEW_TASK: on Wear it makes the launched screen surface as a
        // separate entry in the recents carousel, which lint flags and which looks
        // wrong. The Activity context can start this directly.
        Button(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }) {
            Text("Wi-Fi settings", fontSize = TEXT_BUTTON_SMALL)
        }

        Gap(GAP_SMALL)

        // Android disables wireless debugging whenever Wi-Fi flickers, and it does not
        // come back by itself. This puts it back without a trip through developer
        // options — but only works if WRITE_SECURE_SETTINGS has been granted over ADB.
        // Write, then READ BACK and report what the setting actually says.
        //
        // The first version reported "on" whenever putInt did not throw — which was
        // wrong: a call that does not throw is not evidence the system acted, so never
        // claim success from its absence. Hence the read-back.
        //
        // Confirmed on the watch: when the write lands, the system does act on it —
        // the watch vibrates and announces wireless debugging is on within a few
        // seconds. The earlier "it wrote but nothing happened" case was Wi-Fi being
        // off entirely, so there was no radio for adbd to bind to.
        //
        // What the read-back still cannot tell us is how long adbd stays up. It has
        // been observed dying within a minute or two of coming up, mid-transfer. So
        // this is a "give me a window" button, not a persistent toggle.
        Button(onClick = {
            debugToggleResult = runCatching {

                Settings.Global.putInt(context.contentResolver, ADB_WIFI_SETTING, 1)

                val readBack = Settings.Global.getInt(context.contentResolver, ADB_WIFI_SETTING, -1)

                when (readBack) {
                    1 -> "on — connect now, adbd may not stay up"
                    -1 -> "setting missing after write"
                    else -> "write ignored — still reads $readBack"
                }

            }.getOrElse { "refused: needs pm grant WRITE_SECURE_SETTINGS" }
        }) {
            Text("wireless debug ON", fontSize = TEXT_BUTTON_SMALL)
        }

        debugToggleResult?.let {
            Gap(GAP_TIGHT)
            Detail(it, if (it.startsWith("needs")) BAD else GOOD)
        }

        Gap(GAP_SECTION)

        if (history.isNotEmpty()) {

            SectionHeading("heard")

            history.forEach {

                Text(
                    text = it.text,
                    color = GOOD,
                    fontSize = TEXT_HISTORY,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Detail("${it.totalMs ?: "-"}ms  think ${it.thinkMs ?: "-"}ms", NEUTRAL)
                Gap(GAP_SMALL)
            }
        }

        Gap(GAP_BOTTOM)
    }
}

// ---------------------------------------------------------------------------
// Small building blocks.
// ---------------------------------------------------------------------------

/** Vertical gap. Named Gap rather than Spacer to avoid shadowing Compose's own. */
@Composable
private fun Gap(height: Dp) {
    Box(modifier = Modifier.height(height))
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        color = HEADING,
        fontSize = TEXT_HEADING,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Detail(text: String, colour: Color = NEUTRAL) {
    Text(
        text = text,
        color = colour,
        fontSize = TEXT_DETAIL,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
