package com.nerdfever.talkrpn

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.OutputStream
import kotlin.concurrent.thread

/*
 * Continuous microphone capture, decoupled from the recognizer.
 *
 * The problem this solves: SpeechRecognizer normally opens the microphone itself,
 * and it stops dead after every result. Rebuilding it costs 140-310 ms during which
 * the microphone is shut, and anything said in that window is gone. Measured on the
 * watch, the cycle repeats roughly every 5 seconds even in silence - so speaking at
 * the wrong moment loses the start of a phrase, and the "mic open" indicator can go
 * red while you are mid-sentence.
 *
 * The fix is to invert the ownership. We hold a single AudioRecord that never stops.
 * The recognizer is handed the read end of a pipe via EXTRA_AUDIO_SOURCE and reads
 * PCM from that instead of touching the microphone. It can then stop, restart and
 * re-endpoint as often as it likes; capture is unaffected.
 *
 * Audio captured while no recognizer is attached is buffered rather than dropped, so
 * the words spoken during a restart still reach the next session.
 */

private const val LOG_TAG = "TalkRPN"

/** Recognizers expect 16 kHz mono PCM; anything else is resampled or rejected. */
const val MIC_SAMPLE_RATE = 16000
const val MIC_ENCODING = AudioFormat.ENCODING_PCM_16BIT
const val MIC_CHANNELS = 1

/**
 * How much audio to hold while no recognizer is listening.
 *
 * 16 kHz x 2 bytes = 32 kB per second, so this is about two seconds - comfortably
 * more than the longest restart gap measured, and small enough that stale audio is
 * never replayed into a much later session.
 */
private const val BACKLOG_LIMIT_BYTES = 64 * 1024

/**
 * Multiple of AudioRecord's minimum buffer to request.
 *
 * Slack so a scheduling hiccup on a busy watch does not overrun the hardware buffer
 * and drop samples. Larger costs only memory.
 */
private const val CAPTURE_BUFFER_MULTIPLE = 4

/**
 * Owns the microphone for as long as the app is listening.
 *
 * Start once; call [openPipe] for each recognition session.
 */
class MicStream {

    private var record: AudioRecord? = null
    private var capturing = false

    /** Where captured audio currently goes. Null between sessions. */
    @Volatile
    private var sink: OutputStream? = null

    /** Audio captured while [sink] was null, replayed into the next session. */
    private val backlog = java.io.ByteArrayOutputStream()

    /** Whether the microphone is actually open. */
    val isCapturing: Boolean get() = capturing

    @SuppressLint("MissingPermission")
    fun start(): Boolean {

        if (capturing) return true

        val minBuffer = AudioRecord.getMinBufferSize(MIC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, MIC_ENCODING)

        if (minBuffer <= 0) {
            Log.e(LOG_TAG, "mic: unsupported capture format")
            return false
        }

        val created = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            MIC_ENCODING,
            minBuffer * CAPTURE_BUFFER_MULTIPLE,
        )

        if (created.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "mic: AudioRecord failed to initialise")
            created.release()
            return false
        }

        record = created
        capturing = true
        created.startRecording()

        thread(name = "TalkRPN-mic", isDaemon = true) { pump(created, minBuffer) }

        Log.d(LOG_TAG, "mic: capture started (${MIC_SAMPLE_RATE} Hz, buffer ${minBuffer * CAPTURE_BUFFER_MULTIPLE} bytes)")
        return true
    }

    /**
     * The capture loop.
     *
     * Runs for the whole listening session, regardless of what the recognizer is
     * doing. Reads are blocking, so this thread spends its life asleep in the driver.
     */
    private fun pump(source: AudioRecord, chunk: Int) {

        val buffer = ByteArray(chunk)

        while (capturing) {

            val read = source.read(buffer, 0, buffer.size)
            if (read <= 0) continue

            val destination = sink

            if (destination == null) {

                // No recognizer attached — hold the audio so a restart does not lose
                // the words spoken during it, but cap it so we never replay history.
                synchronized(backlog) {
                    if (backlog.size() > BACKLOG_LIMIT_BYTES) backlog.reset()
                    backlog.write(buffer, 0, read)
                }

            } else {
                try {
                    destination.write(buffer, 0, read)
                } catch (e: Exception) {
                    // The recognizer closed its end; stop feeding it and start
                    // buffering again for whoever comes next.
                    sink = null
                }
            }
        }

        source.stop()
        source.release()

        Log.d(LOG_TAG, "mic: capture stopped")
    }

    /**
     * Create a pipe for one recognition session.
     *
     * Returns the read end, to be passed as EXTRA_AUDIO_SOURCE. Anything buffered
     * since the last session is written in first, so the recognizer hears the words
     * spoken while it was being rebuilt.
     */
    fun openPipe(): ParcelFileDescriptor? {

        closePipe()

        return try {

            val pipe = ParcelFileDescriptor.createPipe()
            val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

            synchronized(backlog) {
                if (backlog.size() > 0) {
                    out.write(backlog.toByteArray())
                    Log.d(LOG_TAG, "mic: replayed ${backlog.size()} buffered bytes into new session")
                    backlog.reset()
                }
            }

            sink = out
            pipe[0]

        } catch (e: Exception) {
            Log.e(LOG_TAG, "mic: could not create pipe", e)
            null
        }
    }

    /** Detach the current session; capture continues. */
    fun closePipe() {

        val current = sink
        sink = null

        runCatching { current?.close() }
    }

    /** Release the microphone entirely. */
    fun stop() {

        closePipe()
        capturing = false
        record = null

        synchronized(backlog) { backlog.reset() }
    }
}
