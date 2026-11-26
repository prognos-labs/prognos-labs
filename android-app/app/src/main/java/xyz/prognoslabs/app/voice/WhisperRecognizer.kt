package xyz.prognoslabs.app.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt
import xyz.prognoslabs.app.data.cleanRecognizedText
import xyz.prognoslabs.app.ui.VoiceEngine

/**
 * On-device speech recognizer backed by whisper.cpp.
 *
 * Performance & noise-gating upgrades:
 * - Uses VOICE_RECOGNITION audio source for OS noise suppression / AGC.
 * - Calibrates an adaptive ambient noise floor over the first 250 ms and
 *   requires sustained speech energy before opening a segment.
 * - Eliminates mid-utterance partial Whisper runs (which previously caused
 *   transcribeLock contention and 11-20s delays). Live RMS levels are emitted
 *   instead, and Whisper runs once when speech ends.
 * - Attaches the captured 16 kHz mono PCM to [VoiceEngine.Event.Final] so the
 *   voice-check pass can verify words and speaker features in a single pass.
 */
class WhisperRecognizer(
    private val modelManager: WhisperModelManager,
    private val languageCode: () -> String = { "en" },
    private val initialPrompt: () -> String? = { null }
) {
    companion object {
        const val SAMPLE_RATE = 16_000

        /** Minimum RMS floor for speech detection (16-bit PCM). */
        const val SILENCE_RMS = 400.0

        /** Silence this long after speech ends finalizes the segment. */
        const val SILENCE_TO_FINAL_MS = 750L

        /** Hard cap on a single utterance segment. */
        const val MAX_SEGMENT_SECONDS = 30

        /** Ambient noise calibration window at start of listen. */
        const val CALIBRATION_MS = 250L

        /** Multiplier above calibrated noise floor to count as speech. */
        const val NOISE_FLOOR_MULTIPLIER = 1.8

        /** Consecutive active frames required to confirm speech onset. */
        const val ONSET_FRAMES_REQUIRED = 2

        /**
         * Frames this loud may be steady room noise, not speech.
         * The gate absorbs a short run of steady frames in this band
         * into the noise floor (see VoiceGate). Anything louder is
         * speech, whatever the room sounds like.
         */
        const val NOISE_BAND_MAX_RMS = 1200.0

        /** Steady frames in a row before the run counts as room noise. */
        const val STABLE_RUN_FRAMES = 3

        /** How close together the frames in a steady run must be. */
        const val STABLE_RUN_TOLERANCE = 0.15

        /** A captured segment shorter than this is a fragment, not a
         * word: keep listening instead of transcribing it. */
        const val MIN_SEGMENT_MS = 800L

        /** Supported on-device Whisper language codes. */
        fun whisperLanguageCode(locale: Locale): String {
            val code = locale.language.lowercase(Locale.US)
            return if (code in setOf("en", "es", "fr", "pt", "de", "tr", "ar", "zh")) code else "en"
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

        fun isSilentAdaptive(rms: Double, noiseFloorRms: Double): Boolean {
            val threshold = max(SILENCE_RMS, noiseFloorRms * NOISE_FLOOR_MULTIPLIER)
            return rms < threshold
        }

        fun shouldFinalize(
            speechHeard: Boolean,
            silenceMs: Long,
            segmentMs: Long
        ): Boolean =
            (speechHeard && silenceMs >= SILENCE_TO_FINAL_MS) ||
                segmentMs >= MAX_SEGMENT_SECONDS * 1000L
    }

    private var recordExecutor: ExecutorService? = null
    private var record: AudioRecord? = null
    private val recording = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private var nativeContext: Long = 0
    private val transcribeLock = Any()
    private var onEvent: ((VoiceEngine.Event) -> Unit)? = null

    /**
     * The language for the next transcription, when the caller knows
     * better than the constructor lambda: the cloud path detected a
     * language, or the cloud missed and the recognizer should detect
     * the language itself ("auto") instead of forcing the language
     * set in Settings onto audio spoken in another language. Null
     * means the constructor lambda decides, as before.
     */
    private var languageOverride: String? = null

    fun setLanguageCode(code: String?) {
        languageOverride = code
    }

    fun ready(): Boolean = modelManager.isModelReady() && WhisperNative.isAvailable

    fun isAvailable(): Boolean =
        modelManager.isModelReady() || modelManager.hasBundledModel()

    suspend fun ensureModel(): Boolean =
        modelManager.ensureModel() && WhisperNative.isAvailable

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
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
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

    fun stop() {
        if (!recording.get()) return
        recording.set(false)
        try {
            record?.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
    }

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
        recordExecutor = null
        synchronized(transcribeLock) {
            if (nativeContext != 0L) {
                try {
                    WhisperNative.nativeFree(nativeContext)
                } catch (e: Throwable) {
                    // Already freed.
                }
                nativeContext = 0L
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

    private fun runLoop(rec: AudioRecord) {
        val segment = ByteArrayOutputStream()
        val gate = VoiceGate()

        // Pre-roll: frames heard before the gate confirms speech
        // (calibration frames and the first onset frame) are kept,
        // not dropped, and written into the segment once speech is
        // confirmed, so a word spoken the moment the mic opens
        // arrives whole instead of truncated. Capped at about a
        // second of audio.
        val preRoll = ArrayDeque<ByteArray>()
        var preRollBytes = 0
        val preRollMaxBytes = SAMPLE_RATE * 2

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
            val elapsedMs = now - startedMs
            val shorts = bytesToShorts(buf, read)
            val rms = rmsOf(shorts)

            // Emit normalized amplitude (0f..1f) for live waveform animation.
            val normLevel = (rms / 4000.0).toFloat().coerceIn(0f, 1f)
            if (!released.get()) {
                onEvent?.invoke(VoiceEngine.Event.Level(normLevel))
            }

            val wasHeard = gate.speechHeard
            gate.onFrame(rms, now, elapsedMs)

            if (gate.speechHeard) {
                if (!wasHeard) {
                    // Speech just confirmed: the frames kept in the
                    // pre-roll are the start of this word, so they go
                    // into the segment first, then this frame.
                    for (frame in preRoll) segment.write(frame)
                    preRoll.clear()
                    preRollBytes = 0
                }
                segment.write(buf, 0, read)
            } else {
                preRoll.addLast(buf.copyOf(read))
                preRollBytes += read
                while (preRollBytes > preRollMaxBytes && preRoll.isNotEmpty()) {
                    preRollBytes -= preRoll.removeFirst().size
                }
            }

            if (gate.shouldFinalize(now, elapsedMs)) break
        }

        try {
            rec.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
        try {
            rec.release()
        } catch (e: Exception) {
            // Already released.
        }
        if (record === rec) record = null

        if (released.get()) return

        val bytes = segment.toByteArray()
        if (!gate.speechHeard || bytes.isEmpty()) {
            onEvent?.invoke(VoiceEngine.Event.Error("I did not catch that. Say it again.", silent = true))
            return
        }

        // A fragment shorter than a real word is not transcribed:
        // the recognizer invents whole sentences from fragments, so
        // a short capture means keep listening instead. The segment
        // holds the word plus the trailing silence before the
        // finalize, so a real short word ("yes") is well over this.
        if (bytes.size < SAMPLE_RATE * 2 * MIN_SEGMENT_MS / 1000) {
            onEvent?.invoke(VoiceEngine.Event.Error("I did not catch that. Say it again.", silent = true))
            return
        }

        if (nativeContext() == 0L) {
            // The model file is the right size but the native library
            // cannot load it: the file is corrupt, and the mic would
            // stay dead on every listen until it is replaced. Delete
            // the suspect file so the next listen re-extracts a fresh
            // copy from the app, and say so in plain words instead of
            // leaving the mic silently dead.
            if (modelManager.isModelReady()) modelManager.deleteModel()
            onEvent?.invoke(VoiceEngine.Event.Error("Voice is getting ready. Tap the mic to try again."))
            return
        }

        val pcm = bytesToShorts(bytes, bytes.size)
        val text = transcribePcm(pcm)?.let { cleanRecognizedText(it) }
        if (text.isNullOrBlank()) {
            onEvent?.invoke(VoiceEngine.Event.Error("I did not catch that. Say it again.", silent = true))
        } else {
            onEvent?.invoke(VoiceEngine.Event.Final(text, listOf(text), pcm))
        }
    }

    private fun transcribePcm(pcm: ShortArray): String? {
        if (pcm.isEmpty()) return null
        val ctx = nativeContext()
        if (ctx == 0L) return null
        synchronized(transcribeLock) {
            return try {
                WhisperNative.nativeTranscribe(
                    context = ctx,
                    pcm = pcm,
                    language = languageOverride ?: languageCode(),
                    threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                    initialPrompt = initialPrompt()
                )
            } catch (e: Throwable) {
                null
            }
        }
    }

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

