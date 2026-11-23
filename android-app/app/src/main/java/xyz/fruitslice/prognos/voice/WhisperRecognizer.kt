package xyz.fruitslice.prognos.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import xyz.fruitslice.prognos.data.cleanRecognizedText
import xyz.fruitslice.prognos.ui.VoiceEngine

/**
 * The words pass through the on-device speech library (v1.1.13).
 * It records with the microphone, cuts the audio into a segment
 * when the user stops speaking, and transcribes each segment on a
 * background thread. v1.1.16: it is the ONLY recognizer. The
 * phone's built-in recognizer is gone, so a library failure is
 * reported as unavailable instead of falling back.
 *
 * The words pass and the voice-check capture never run at the same
 * time: the view model runs one, then the other. This class owns
 * its microphone only while it listens and releases it when it
 * stops.
 *
 * Words are shown as spoken: while the user is still talking, the
 * current segment is transcribed every couple of seconds and the
 * latest words go out as a Partial. The final transcription runs
 * when the user stops speaking, or when stop is called.
 */
class WhisperRecognizer(
    private val modelManager: WhisperModelManager,
    private val languageCode: () -> String = { "en" }
) {
    companion object {
        const val SAMPLE_RATE = 16_000

        /** RMS below this counts as silence (16-bit samples). */
        const val SILENCE_RMS = 400.0

        /** Silence this long after speech ends the segment. */
        const val SILENCE_TO_FINAL_MS = 1_200L

        /** Words shown live at most this often while speaking. */
        const val PARTIAL_EVERY_MS = 2_000L

        /** A segment never runs longer than this. */
        const val MAX_SEGMENT_SECONDS = 30

        /** The speech-library language code for a device locale:
         * the two-letter code when this build supports it, English
         * otherwise. The library is multilingual, so all seven
         * voice languages use their own code. */
        fun whisperLanguageCode(locale: Locale): String {
            val code = locale.language.lowercase(Locale.US)
            return if (code in setOf("en", "es", "fr", "pt", "de", "tr", "ar")) code else "en"
        }

        /** Root mean square of a slice of 16-bit audio. */
        fun rmsOf(pcm: ShortArray, from: Int = 0, to: Int = pcm.size): Double {
            if (to <= from) return 0.0
            var sum = 0.0
            for (i in from until to) {
                val v = pcm[i].toDouble()
                sum += v * v
            }
            return sqrt(sum / (to - from))
        }

        fun isSilent(rms: Double): Boolean = rms < SILENCE_RMS

        /**
         * True when the current segment should be transcribed and
         * closed: speech was heard and silence has run long
         * enough, or the segment hit the length cap.
         */
        fun shouldFinalize(
            speechHeard: Boolean,
            silenceMs: Long,
            segmentMs: Long
        ): Boolean =
            (speechHeard && silenceMs >= SILENCE_TO_FINAL_MS) ||
                segmentMs >= MAX_SEGMENT_SECONDS * 1000L
    }

    private var recordExecutor: ExecutorService? = null
    private var transcribeExecutor: ExecutorService? = null
    private var record: AudioRecord? = null
    private val recording = AtomicBoolean(false)
    private val transcribing = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private var nativeContext: Long = 0
    private val transcribeLock = Any()
    private var onEvent: ((VoiceEngine.Event) -> Unit)? = null

    /** True when this recognizer can run right now: the model file
     * is verified and the native library loaded. */
    fun ready(): Boolean = modelManager.isModelReady() && WhisperNative.isAvailable

    /**
     * True when the voice can be made ready: the model file is
     * already verified, or a bundled copy is in the APK waiting to
     * be copied out. The first tap copies it, so a missing file is
     * not "unavailable". The native library is the only thing that
     * can stop it, and that is found when listening starts.
     */
    fun isAvailable(): Boolean =
        modelManager.isModelReady() || modelManager.hasBundledModel()

    /**
     * Makes the model ready: copies the bundled copy into the
     * app's files and checks it by size and hash, then confirms
     * the native library loaded. False when any of that fails.
     */
    suspend fun ensureModel(): Boolean =
        modelManager.ensureModel() && WhisperNative.isAvailable

    /**
     * Starts listening. Emits Partial while the user speaks and one
     * Final when they stop, or Error / Unavailable when it cannot
     * run. The engine makes the model ready first; this is the
     * safety net for a recognizer that was never prepared. Ends
     * any previous pass first.
     */
    fun start(onEvent: (VoiceEngine.Event) -> Unit) {
        this.onEvent = onEvent
        released.set(false)
        if (!ready()) {
            onEvent(VoiceEngine.Event.Unavailable)
            return
        }
        stop()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            onEvent(VoiceEngine.Event.Unavailable)
            return
        }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            try {
                rec?.release()
            } catch (e: Exception) {
                // Nothing to release.
            }
            onEvent(VoiceEngine.Event.Unavailable)
            return
        }
        record = rec
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            record = null
            onEvent(VoiceEngine.Event.Unavailable)
            return
        }
        recording.set(true)
        recordExecutor().execute { runLoop(rec) }
    }

    /**
     * Ends the listen; the recorded audio is still transcribed and
     * reported. Returns at once; the result arrives on the event
     * callback.
     */
    fun stop() {
        if (!recording.get()) return
        recording.set(false)
        try {
            record?.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
    }

    /** Stops everything and frees the native model. No event is
     * delivered after this call. */
    fun release() {
        released.set(true)
        recording.set(false)
        try {
            record?.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
        try {
            record?.release()
        } catch (e: Exception) {
            // Already released.
        }
        record = null
        recordExecutor?.shutdownNow()
        transcribeExecutor?.shutdownNow()
        recordExecutor = null
        transcribeExecutor = null
        synchronized(transcribeLock) {
            if (nativeContext != 0L) {
                try {
                    WhisperNative.nativeFree(nativeContext)
                } catch (e: Throwable) {
                    // Already freed.
                }
                nativeContext = 0
            }
        }
    }

    private fun recordExecutor(): ExecutorService {
        var ex = recordExecutor
        if (ex == null || ex.isShutdown) {
            ex = Executors.newSingleThreadExecutor()
            recordExecutor = ex
        }
        return ex
    }

    private fun transcribeExecutor(): ExecutorService {
        var ex = transcribeExecutor
        if (ex == null || ex.isShutdown) {
            ex = Executors.newSingleThreadExecutor()
            transcribeExecutor = ex
        }
        return ex
    }

    private fun runLoop(rec: AudioRecord) {
        val segment = ByteArrayOutputStream()
        var speechHeard = false
        var lastSpeechMs = 0L
        var lastPartialMs = 0L
        var lastPartialText = ""
        val startedMs = SystemClock.uptimeMillis()
        val buf = ByteArray(4096)
        while (recording.get()) {
            val read = try {
                rec.read(buf, 0, buf.size)
            } catch (e: Exception) {
                break
            }
            if (read <= 0) break
            val now = SystemClock.uptimeMillis()
            val shorts = bytesToShorts(buf, read)
            if (!isSilent(rmsOf(shorts))) {
                speechHeard = true
                lastSpeechMs = now
            }
            segment.write(buf, 0, read)
            val segmentMs = now - startedMs
            val silenceMs = if (speechHeard) now - lastSpeechMs else 0L
            if (shouldFinalize(speechHeard, silenceMs, segmentMs)) break
            if (speechHeard && now - lastPartialMs >= PARTIAL_EVERY_MS &&
                transcribing.compareAndSet(false, true)
            ) {
                val snapshot = segment.toByteArray()
                transcribeExecutor().execute {
                    try {
                        val text = transcribeBytes(snapshot)?.let { cleanRecognizedText(it) }
                        if (!released.get() && text != null && text.isNotBlank() &&
                            text != lastPartialText
                        ) {
                            lastPartialText = text
                            onEvent?.invoke(VoiceEngine.Event.Partial(text))
                        }
                    } finally {
                        transcribing.set(false)
                    }
                }
                lastPartialMs = now
            }
        }
        try {
            rec.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
        if (released.get()) return
        val bytes = segment.toByteArray()
        if (!speechHeard || bytes.isEmpty()) {
            onEvent?.invoke(VoiceEngine.Event.Error("I did not catch that. Say it again.", silent = true))
            return
        }
        // The model could not be loaded at all: that is voice
        // unavailable, not a missed sentence. There is no fallback
        // recognizer any more, so say so plainly.
        if (nativeContext() == 0L) {
            onEvent?.invoke(VoiceEngine.Event.Unavailable)
            return
        }
        val text = transcribeBytes(bytes)?.let { cleanRecognizedText(it) }
        if (text.isNullOrBlank()) {
            onEvent?.invoke(VoiceEngine.Event.Error("I did not catch that. Say it again.", silent = true))
        } else {
            onEvent?.invoke(VoiceEngine.Event.Final(text, listOf(text)))
        }
    }

    /** Transcribes one audio segment. Null when the model could
     * not be loaded or the run failed. One run at a time: the
     * native context is not safe to share between threads. */
    private fun transcribeBytes(bytes: ByteArray): String? {
        val pcm = bytesToShorts(bytes, bytes.size)
        if (pcm.isEmpty()) return null
        val ctx = nativeContext()
        if (ctx == 0L) return null
        synchronized(transcribeLock) {
            return try {
                WhisperNative.nativeTranscribe(
                    ctx,
                    pcm,
                    languageCode(),
                    Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                )
            } catch (e: Throwable) {
                null
            }
        }
    }

    /** Loads the model into the native library on first use and
     * keeps the handle until release. */
    private fun nativeContext(): Long {
        var ctx = nativeContext
        if (ctx != 0L) return ctx
        synchronized(transcribeLock) {
            ctx = nativeContext
            if (ctx == 0L) {
                ctx = try {
                    WhisperNative.nativeInit(modelManager.modelFile.absolutePath)
                } catch (e: Throwable) {
                    0L
                }
                nativeContext = ctx
            }
            return ctx
        }
    }

    private fun bytesToShorts(bytes: ByteArray, length: Int): ShortArray {
        val out = ShortArray(length / 2)
        for (i in out.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }
}
