package com.nerdfever.talkrpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.Executor

/*
 * Everything in this file answers one question: what speech recognition does this
 * device actually have?
 *
 * It deliberately records no audio and needs no permission, so it is safe to run
 * the instant the app starts. Whether recognition *works* is a separate question,
 * answered by RecognitionTrial.
 *
 * Android has two distinct notions here, and the difference is the whole point of
 * the project:
 *
 *   - a *recognition service* may exist and still send audio to Google's servers
 *   - an *on-device* recognizer is one that promises not to
 *
 * TalkRPN must never make a network call, so only the second kind is acceptable.
 */

/**
 * What the platform reports about its own installed recognition support.
 *
 * All of these are synchronous, cheap, and permission-free.
 */
data class InstalledSupport(

    /** Whether the device claims to have a microphone at all. */
    val hasMicrophone: Boolean,

    /** Whether *any* recognizer exists — this one may well be cloud-backed. */
    val anyRecognitionAvailable: Boolean,

    /** Whether an on-device recognizer exists. This is the one that matters to us. */
    val onDeviceRecognitionAvailable: Boolean,

    /** Every installed service advertising itself as a recognizer, as package/class. */
    val recognitionServices: List<String>,

    /** The one the user (or the manufacturer) has selected as default, if readable. */
    val defaultRecognitionService: String?,
)

/**
 * Which languages a recognizer can handle, and where each one runs.
 *
 * This comes back asynchronously, because answering it may require the recognizer
 * to consult a model catalogue.
 */
data class LanguageSupport(

    /** Which recognizer answered — on-device, or the system default. */
    val answeredBy: String,

    /** Models already present. These work with no network. */
    val installedOnDevice: List<String>,

    /** Models that *could* be downloaded and then run offline. */
    val supportedOnDevice: List<String>,

    /** Models currently downloading. */
    val pendingOnDevice: List<String>,

    /** Languages available only by sending audio to a server. Useless to us. */
    val online: List<String>,
)

/**
 * Ask the platform what it has installed.
 *
 * Called on the main thread, returns immediately.
 */
fun probeInstalledSupport(context: Context): InstalledSupport {

    val packageManager = context.packageManager

    // A watch without a microphone would make the whole project moot, so check
    // rather than assume. This is a declared hardware feature, not a live test.
    val hasMicrophone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    // Enumerate every app offering itself as a recognition service. Note this needs
    // the <queries> element in the manifest: since Android 11 an app cannot see
    // other packages by default, and without that declaration this list comes back
    // empty even when services are installed.
    val serviceIntent = Intent(RecognitionService.SERVICE_INTERFACE)

    val recognitionServices = packageManager
        .queryIntentServices(serviceIntent, 0)
        .mapNotNull { it.serviceInfo }
        .map { "${it.packageName}/${it.name}" }
        .sorted()

    // Which of those the system has actually selected. Not part of the public API,
    // so read defensively — a null here is uninteresting, not an error.
    val defaultRecognitionService = try {
        Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
    } catch (e: Exception) {
        null
    }

    return InstalledSupport(
        hasMicrophone = hasMicrophone,
        anyRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context),
        onDeviceRecognitionAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context),
        recognitionServices = recognitionServices,
        defaultRecognitionService = defaultRecognitionService,
    )
}

/**
 * Ask a recognizer which languages it supports and where they run.
 *
 * Prefers the on-device recognizer; falls back to the system default one so that a
 * device with no offline support still tells us something useful rather than nothing.
 *
 * Must be called on the main thread — SpeechRecognizer requires it. The callbacks
 * arrive on the main thread too, via [mainThreadExecutor].
 */
fun queryLanguageSupport(
    context: Context,
    languageTag: String,
    mainThreadExecutor: Executor,
    onResult: (LanguageSupport) -> Unit,
    onFailure: (String) -> Unit,
) {

    // Bail out early when there is no service at all. createSpeechRecognizer still
    // hands back an object in that case, but checkRecognitionSupport on it never
    // calls back either way — so without this guard the caller waits forever and
    // the report sits on "asking…" looking like a slow query rather than a verdict.
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onFailure("no recognition service installed — nothing to ask")
        return
    }

    // Prefer the offline recognizer. If there isn't one, fall back to the default so
    // the report can still say what the device *would* do, and label it honestly.
    val preferOnDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    val recognizer = try {
        if (preferOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    } catch (e: Exception) {
        onFailure("Could not create a recognizer: ${e.javaClass.simpleName}: ${e.message}")
        return
    }

    val answeredBy = if (preferOnDevice) "on-device recognizer" else "system default recognizer"

    // The support query takes the same shape of intent a real recognition would.
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
    }

    // Release the recognizer once it has answered — it holds a binding to another
    // process, and leaking those is how you end up with ERROR_RECOGNIZER_BUSY later.
    fun finish(action: () -> Unit) {
        recognizer.destroy()
        action()
    }

    recognizer.checkRecognitionSupport(
        intent,
        mainThreadExecutor,
        object : RecognitionSupportCallback {

            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                finish {
                    onResult(
                        LanguageSupport(
                            answeredBy = answeredBy,
                            installedOnDevice = recognitionSupport.installedOnDeviceLanguages,
                            supportedOnDevice = recognitionSupport.supportedOnDeviceLanguages,
                            pendingOnDevice = recognitionSupport.pendingOnDeviceLanguages,
                            online = recognitionSupport.onlineLanguages,
                        )
                    )
                }
            }

            override fun onError(error: Int) {
                finish { onFailure("checkRecognitionSupport failed: ${recognizerErrorName(error)}") }
            }
        },
    )
}

/**
 * Turn a SpeechRecognizer error code into something readable.
 *
 * Worth having as a real function rather than a toast message: these codes are the
 * primary diagnostic when recognition fails, and "ERROR_INSUFFICIENT_PERMISSIONS"
 * tells you what to do next in a way that "9" does not.
 */
fun recognizerErrorName(code: Int): String = when (code) {

    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT (1) — tried to use the network"
    SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK (2) — tried to use the network"
    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO (3) — microphone capture failed"
    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER (4) — a server answered, so this was not offline"
    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT (5) — usually: no recognition service installed"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT (6) — heard no speech"
    SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH (7) — heard speech, recognised nothing"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY (8) — a previous session is still open"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS (9) — RECORD_AUDIO not granted"
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS (10)"
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED (11)"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED (12)"
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE (13) — model not downloaded"
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT (14)"
    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS (15)"

    else -> "unknown error code $code"
}
