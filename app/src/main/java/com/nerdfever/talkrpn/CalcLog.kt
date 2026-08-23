package com.nerdfever.talkrpn

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Durable diagnostic record of everything the calculator does - the same
 * reasoning as ResultLog: logcat only survives while an ADB link does,
 * and the questions worth answering ("what did it hear, what did it do")
 * arrive after the link has dropped. A file on the watch does not care.
 *
 * One row per event: every utterance heard (with its outcome and the
 * machine afterwards), every pad press, and the lifecycle moments -
 * start, restore, pause, the idle finish. Reading this file back is how
 * a wrist session gets debugged without the wearer having to narrate it.
 *
 * In getExternalFilesDir, app-scoped but readable over ADB without root:
 *
 *   adb pull /sdcard/Android/data/com.nerdfever.talkrpn/files/calculator.tsv
 *
 * Every row is also mirrored to logcat (tag TalkRPN), so a LIVE link
 * shows the same story as it happens.
 */

private const val LOG_TAG = "TalkRPN"

/** Tab-separated: fields can contain almost anything, but never a tab. */
private const val SEPARATOR = "\t"

/** The file name, in the app's external files directory. */
private const val CALC_LOG_FILE = "calculator.tsv"

/** Wall-clock stamp, sortable, matching logcat's ordering. */
private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss"

private val COLUMNS = listOf(
    "time",
    "source",   // mic | utter | pad | system
    "input",    // the utterance or token word as it arrived
    "outcome",  // applied N | rejected:word | undo | undo-empty | ...
    "display",  // what the X display shows afterwards
    "x", "y", "z", "t", "lastx", "sto",
    "entry",
    "dsp",
    "error",
)

/** Appends one row per calculator event; failures log, never throw. */
class CalcLog(context: Context) {

    private val file: File? = try {

        context.getExternalFilesDir(null)?.let {
            it.mkdirs()
            val f = File(it, CALC_LOG_FILE)

            // Header once, on creation.
            if (!f.exists()) f.writeText(COLUMNS.joinToString(SEPARATOR) + "\n")

            Log.d(LOG_TAG, "calc log: ${f.absolutePath}")
            f
        }

    } catch (e: Exception) {
        Log.e(LOG_TAG, "could not open calc log", e)
        null
    }

    private val timestamps = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US)

    /**
     * One event: what arrived, what became of it, and the whole machine
     * afterwards - enough to replay the session from the file alone.
     */
    fun record(
        source: String,
        input: String,
        outcome: String,
        engine: RpnEngine,
        display: String,
    ) {

        // The live mirror, for when a link happens to be up.
        Log.i(LOG_TAG, "calc: [$source] \"$input\" -> $outcome | display=$display")

        val target = file ?: return

        val row = listOf(
            timestamps.format(Date()),
            source,
            input,
            outcome,
            display,
            engine.x.toString(), engine.y.toString(),
            engine.z.toString(), engine.t.toString(),
            engine.lastX.toString(), engine.storage.toString(),
            engine.entry,
            engine.dspPlaces.toString(),
            if (engine.error) "1" else "0",
        )

        // Checked against the header rather than trusted - a silently
        // shifted column is worse than a visibly missing row.
        if (row.size != COLUMNS.size) {
            Log.e(LOG_TAG, "calc log: ${row.size} fields for ${COLUMNS.size} columns - row dropped")
            return
        }

        try {
            target.appendText(row.joinToString(SEPARATOR) + "\n")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "could not append calc log row", e)
        }
    }
}
