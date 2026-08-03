package com.nerdfever.talkrpn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
 * No script, no prompts, no pass structure - just say things and watch what comes
 * back. The earlier scripted version answered "which engine is faster"; this one
 * exists to answer "does it actually hear what I mean", which needs unstructured
 * play rather than a fixed list.
 *
 * The live guess is the largest thing on screen and updates as you speak. Finished
 * utterances stack up underneath with their timings, so a run of experiments can be
 * compared without looking at a log.
 */

private val GOOD = Color(0xFF6BD46B)
private val BAD = Color(0xFFE06C6C)
private val NEUTRAL = Color(0xFFBFBFBF)
private val HEADING = Color(0xFFE8C36B)

/** How many finished utterances to keep on screen. */
private const val HISTORY_DEPTH = 8

class MainActivity : ComponentActivity() {

    /**
     * Whether this screen is in the foreground.
     *
     * The microphone must be released the moment it is not. Two reasons, and the first
     * one bit us: leaving the app does not necessarily destroy the composition, so a
     * second visit created a second recognizer while the first was still listening -
     * two of them competing for one microphone, interleaving results, and transcribing
     * conversation that had nothing to do with the calculator.
     *
     * The second reason is the stated design rule: this app listens only while it is
     * being used. Anything else is a battery drain and a privacy surprise.
     */
    private var resumed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        // Wear OS dismisses an app back to the watch face after a spell without touch
        // input - and talking to it is not touch input. Without this the app vanishes
        // mid-sentence, which then releases the microphone via onPause and looks like
        // the recognizer failing.
        //
        // Reasonable for a calculator you are actively speaking to; it would not be
        // reasonable for something left open in the background.
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
    val engine: String,
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

    val platformSource = remember { PlatformSpeechSource(context) }
    val voskSource = remember { VoskSpeechSource(context) }

    DisposableEffect(Unit) {
        onDispose {
            platformSource.dispose()
            voskSource.dispose()
        }
    }

    // Default to the platform engine: it is the one that currently works, so play
    // starts somewhere useful rather than on the broken path.
    var useVosk by remember { mutableStateOf(false) }
    val source: SpeechSource = if (useVosk) voskSource else platformSource

    var history by remember { mutableStateOf<List<Heard>>(emptyList()) }

    val ready = hasAudioPermission && (!useVosk || voskSource.modelReady)

    // Listen continuously while in the foreground, and re-arm on any change of engine,
    // readiness, or lifecycle.
    //
    // Both engines are stopped first, every time. That is deliberate belt-and-braces:
    // two recognizers competing for one microphone is the failure we just spent an
    // evening misdiagnosing, and it is cheap to make structurally impossible.
    LaunchedEffect(useVosk, ready, resumed) {

        platformSource.cancel()
        voskSource.cancel()

        if (!ready || !resumed) return@LaunchedEffect

        source.continuous = true
        source.start()
    }

    // Record each finished utterance, newest first.
    LaunchedEffect(source.results) {

        val text = source.results.firstOrNull()?.text.orEmpty()

        if (text.isBlank()) return@LaunchedEffect

        history = (listOf(
            Heard(
                engine = if (useVosk) "V" else "A",
                text = text,
                totalMs = source.totalMs,
                thinkMs = source.processingMs,
            )
        ) + history).take(HISTORY_DEPTH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 40.dp),

        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        if (!hasAudioPermission) {

            Detail("Microphone permission needed.", BAD)
            Gap(8.dp)

            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Grant microphone", fontSize = 13.sp)
            }

            return@Column
        }

        // Which engine, and whether it is actually listening. The bias state is shown
        // because it materially changes what comes back and is easy to forget.
        Detail(source.label, HEADING)
        Detail(if (BIAS_TO_VOCABULARY) "biased to vocabulary" else "unbiased", NEUTRAL)

        // Whether the microphone is actually open, stated plainly. The platform engine
        // shuts it between utterances, and anything said in that window is lost — so
        // "is it hearing me right now" needs to be visible, not inferred.
        val micOpen = source.state == TrialState.Listening || source.state == TrialState.Hearing

        Detail(
            text = when {
                useVosk && !voskSource.modelReady -> "loading model…"
                source.state == TrialState.Failed -> source.message ?: "failed"
                micOpen -> "● MIC OPEN"
                else -> "○ deaf — wait"
            },
            colour = when {
                source.state == TrialState.Failed -> BAD
                micOpen -> GOOD
                else -> BAD
            },
        )

        source.deafWindowMs?.let { Detail("last gap ${it}ms", NEUTRAL) }

        Gap(10.dp)

        // The live guess — the largest thing on screen, updating as you speak.
        Text(
            text = source.partial.ifBlank { "…" },
            color = if (source.partial.isBlank()) NEUTRAL else GOOD,
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Gap(10.dp)

        Button(onClick = { useVosk = !useVosk }) {
            Text(if (useVosk) "Use Android" else "Use Vosk", fontSize = 12.sp)
        }

        Gap(12.dp)

        // Finished utterances, newest first. "A" is the Android platform engine,
        // "V" is Vosk, so a mixed run stays readable.
        if (history.isNotEmpty()) {

            SectionHeading("heard")

            history.forEach {

                Text(
                    text = it.text,
                    color = if (it.engine == "V") Color(0xFF8FB8E8) else GOOD,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Detail("${it.engine}  ${it.totalMs ?: "-"}ms  think ${it.thinkMs ?: "-"}ms", NEUTRAL)
                Gap(6.dp)
            }
        }

        Gap(20.dp)
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
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Detail(text: String, colour: Color = NEUTRAL) {
    Text(
        text = text,
        color = colour,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
