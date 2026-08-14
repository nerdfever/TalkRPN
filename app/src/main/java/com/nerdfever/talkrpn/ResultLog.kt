package com.nerdfever.talkrpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Durable record of every recognition.
 *
 * Written because three test sessions in a row were lost: logcat only survives while
 * a connection is live, and wireless debugging switches itself off whenever Wi-Fi so
 * much as flickers. A file on the watch does not care.
 *
 * It also makes the airplane-mode comparison possible at all - with the radios off
 * there is no ADB, so a file is the only way to get results back.
 *
 * Deliberately in getExternalFilesDir rather than filesDir: still app-scoped and
 * removed with the app, but readable over ADB without root, which filesDir is not.
 *   /sdcard/Android/data/com.nerdfever.talkrpn/files/results.tsv
 */

private const val LOG_TAG = "TalkRPN"

/** Tab-separated: the text can contain almost anything, but never a tab. */
private const val SEPARATOR = "\t"

/** File names, in the app's external files directory. Pulled with `adb pull`. */
private const val RESULTS_FILE = "results.tsv"
private const val PARTIALS_FILE = "partials.tsv"

/** Wall-clock stamp format for every row. Sortable, and matches logcat's ordering. */
private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss"

private val COLUMNS = listOf(
    "time",
    "heard",
    "total_ms",
    "think_ms",
    "deaf_ms",
    "revisions",
    "confidence",
    "prefer_offline",
    "network",
    "alternatives",
    "tokens",
)

private val PARTIAL_COLUMNS = listOf(
    "time",
    "utterance",
    "ms",
    "text",
    "new_tokens",
)

/**
 * Appends one line per recognised utterance.
 *
 * Every field that might explain a surprising result is recorded, because the whole
 * point is not having to reproduce the session to answer a question about it. In
 * particular [network] records whether the recognizer *could* have reached a server,
 * which is the only handle we have on the local-versus-cloud question - the platform
 * recognizer never says which it used.
 */
class ResultLog(context: Context) {

    private val file: File? = try {

        val dir = context.getExternalFilesDir(null)

        dir?.let {
            it.mkdirs()
            val f = File(it, RESULTS_FILE)

            // Header once, on creation.
            if (!f.exists()) f.writeText(COLUMNS.joinToString(SEPARATOR) + "\n")

            Log.d(LOG_TAG, "result log: ${f.absolutePath}")
            f
        }

    } catch (e: Exception) {
        Log.e(LOG_TAG, "could not open result log", e)
        null
    }

    /** Absolute path, for showing on screen so it can be pulled without guessing. */
    val path: String? get() = file?.absolutePath

    /**
     * Every partial hypothesis, in its own file.
     *
     * Separate from the results because there are ten to twenty per utterance and
     * mixing them would drown the finals. This is the file that answers "what was
     * known, when" - which is the question a commit rule has to be built on.
     */
    private val partialFile: File? = try {

        context.getExternalFilesDir(null)?.let {
            val f = File(it, PARTIALS_FILE)
            if (!f.exists()) f.writeText(PARTIAL_COLUMNS.joinToString(SEPARATOR) + "\n")
            f
        }

    } catch (e: Exception) {
        Log.e(LOG_TAG, "could not open partial log", e)
        null
    }

    /** Utterance counter, so partials can be grouped back into their utterance. */
    private var utterance = 0

    /** Called at the start of each utterance. */
    fun beginUtterance() {
        utterance += 1
    }

    /**
     * Record one partial hypothesis.
     *
     * [newTokens] is what changed since the previous hypothesis — the delta the API
     * refuses to provide, computed by the source.
     */
    fun recordPartial(text: String, atMs: Long, newTokens: List<TokenArrival>) {

        val target = partialFile ?: return

        val row = listOf(
            timestamps.format(Date()),
            utterance.toString(),
            atMs.toString(),
            text,
            newTokens.joinToString(" ") { it.token },
        )

        append(target, PARTIAL_COLUMNS, row, "partial")
    }

    /**
     * Append one row, or say why not.
     *
     * The row is CHECKED against its header rather than trusted. A field added to
     * one and not the other would otherwise shift every later column silently, and
     * a log that is quietly wrong is worse than one that is visibly missing a line.
     */
    private fun append(target: File, header: List<String>, row: List<String>, what: String) {

        if (row.size != header.size) {
            Log.e(LOG_TAG, "$what: ${row.size} fields for ${header.size} columns - row dropped")
            return
        }

        try {
            target.appendText(row.joinToString(SEPARATOR) + "\n")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "could not append $what", e)
        }
    }

    private val timestamps = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US)

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Whether a usable network exists right now.
     *
     * Not whether one was *used* — the recognizer does not report that. But a result
     * produced with no network was certainly local, which is the inference that
     * matters, and it is why this is sampled per utterance rather than per session.
     */
    private fun networkState(): String {

        val active = connectivity?.activeNetwork ?: return "none"
        val caps = connectivity.getNetworkCapabilities(active) ?: return "none"

        return when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "no-internet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
    }

    /**
     * Record a recognition that failed after speech was detected.
     *
     * These matter as much as the successes and were invisible until now: a row was
     * only written when there was text, so "I said 8087 and got nothing" left no
     * trace at all, and the file silently overstated how well the engine was doing.
     */
    fun recordFailure(source: SpeechSource, preferOffline: Boolean) {

        val target = file ?: return

        val row = listOf(
            timestamps.format(Date()),
            "<FAILED: ${source.lastFailure ?: "unknown"}>",
            "", "", "",
            source.partialUpdates.toString(),
            "",
            if (preferOffline) "yes" else "no",
            networkState(),

            // What it had heard before giving up — often the most telling field.
            source.lastFailurePartial?.let { "partial: $it" } ?: "",
            "",
        )

        append(target, COLUMNS, row, "failure")
    }

    /** Record one finished utterance. Failures are logged, never thrown at the UI. */
    fun record(source: SpeechSource, preferOffline: Boolean) {

        val target = file ?: return
        val best = source.results.firstOrNull() ?: return

        val row = listOf(
            timestamps.format(Date()),
            best.text,
            source.totalMs?.toString() ?: "",
            source.processingMs?.toString() ?: "",
            source.deafWindowMs?.toString() ?: "",
            source.partialUpdates.toString(),
            best.confidence?.let { "%.3f".format(it) } ?: "",
            if (preferOffline) "yes" else "no",
            networkState(),

            // Runners-up, pipe-separated so the column stays one field.
            source.results.drop(1).joinToString(" | ") { it.text },

            // Per-token arrival times for THIS utterance — not the whole session,
            // which is what the column used to contain and what made it useless.
            ((source as? PlatformSpeechSource)?.lastUtteranceTokens ?: source.tokens)
                .joinToString(" ") { "${it.token}@${it.atMs}" },
        )

        append(target, COLUMNS, row, "result")
    }
}
