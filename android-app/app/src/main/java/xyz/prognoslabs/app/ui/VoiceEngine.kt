package xyz.prognoslabs.app.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.cleanRecognizedText
import xyz.prognoslabs.app.data.voiceLanguageForLocale
import xyz.prognoslabs.app.data.voiceLanguageSupported
import xyz.prognoslabs.app.data.whisperCodeFor
import xyz.prognoslabs.app.voice.WhisperModelManager
import xyz.prognoslabs.app.voice.WhisperNative
import xyz.prognoslabs.app.voice.WhisperRecognizer

/**
 * Result of one recognition pass: the text, plus the language the
 * recognizer reported for that turn, if any. Null when the recognizer
 * did not report a language, and the caller falls back to the set
 * language in Settings.
 */
data class AsrTranscript(
    val text: String,
    val detectedLanguage: VoiceLanguage? = null
)

/**
 * Optional cloud ASR provider for languages where on-device ggml-tiny
 * has low accuracy (e.g. Igbo, Yoruba, Hausa, Hebrew).
 */
fun interface AsrProvider {
    /**
     * Transcribes captured audio. [languageCode] is a hint for the
     * recognizer: blank means detect the spoken language, a code
     * pins it. Igbo is never transcribed: the provider returns null
     * and the caller falls back to on-device English.
     */
    suspend fun transcribe(pcm: ShortArray, sampleRate: Int, languageCode: String): AsrTranscript?
}

class VoiceEngine(
    private val context: Context,
    private val cloudAsr: AsrProvider? = null
) {
    sealed class Event {
        data class Level(val rmsNorm: Float) : Event()
        data class Partial(val text: String) : Event()
        data class Final(
            val text: String,
            val candidates: List<String> = listOf(text),
            val pcm: ShortArray = shortArrayOf(),
            val detectedLanguage: VoiceLanguage? = null
        ) : Event()
        data class Error(val message: String, val silent: Boolean = false) : Event()
        object Unavailable : Event()
    }

    companion object {
        val RECOGNITION_LOCALE: Locale = Locale.US
        const val CAPTURE_SILENCE_TO_STOP_MS = 800L
        const val CLOUD_MAX_SECONDS = 15
        const val BASELINE_MIN_RMS = 150.0
        const val BASELINE_MAX_RMS = 1200.0

        fun shouldStopCapture(speechHeard: Boolean, silenceMs: Long): Boolean =
            speechHeard && silenceMs >= CAPTURE_SILENCE_TO_STOP_MS

        fun isStaleCaptureSession(session: Int, currentSession: Int): Boolean =
            session != currentSession
    }

    @Volatile
    private var overrideLanguage: VoiceLanguage? = null

    fun setLanguageOverride(language: VoiceLanguage?) {
        overrideLanguage = language
    }

    fun recognitionLocale(): Locale {
        overrideLanguage?.let { return Locale(it.code) }
        val device = Locale.getDefault()
        return if (voiceLanguageSupported(device)) device else RECOGNITION_LOCALE
    }

    fun voiceLanguage(): VoiceLanguage =
        overrideLanguage ?: voiceLanguageForLocale(recognitionLocale())

    fun deviceLanguageSupported(): Boolean =
        overrideLanguage != null || voiceLanguageSupported(Locale.getDefault())

    sealed class VoiceSample {
        data class Captured(val pcm: ShortArray, val sampleRate: Int) : VoiceSample()
        object Unavailable : VoiceSample()
        object NoAudio : VoiceSample()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sampleRate = 16_000
    private var captureExecutor: ExecutorService? = null
    private var audioRecord: AudioRecord? = null
    private val capturing = AtomicBoolean(false)
    private val captureDone = AtomicBoolean(true)
    private val capturedBytes = ByteArrayOutputStream()
    private val captureLock = Any()
    private var audioOk = false
    private var captureListener: ((VoiceSample) -> Unit)? = null
    private var captureListenerSession = -1

    @Volatile
    private var captureSession = 0
    private var onEvent: ((Event) -> Unit)? = null
    private var listening = false

    private val whisperModel = WhisperModelManager(
        context.filesDir,
        bundledModel = { context.assets.open(WhisperModelManager.MODEL_FILE_NAME) }
    )
    private var whisperRecognizer: WhisperRecognizer? = null
    private val whisperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        if (WhisperNative.isAvailable) {
            whisperScope.launch { whisperModel.ensureModel() }
        }
    }

    fun isAvailable(): Boolean =
        (cloudAsr != null && voiceLanguage() != VoiceLanguage.IGBO) ||
            whisperModel.isModelReady() ||
            whisperModel.hasBundledModel()

    fun startListening(onEvent: (Event) -> Unit) {
        this.onEvent = onEvent
        val lang = voiceLanguage()

        // Cloud transcriber runs first for every language except Igbo.
        if (lang != VoiceLanguage.IGBO && cloudAsr != null) {
            startCloudListening(lang, onEvent)
            return
        }

        startOnDeviceListening(onEvent)
    }

    private fun startOnDeviceListening(onEvent: (Event) -> Unit) {
        if (!WhisperNative.isAvailable) {
            onEvent(Event.Unavailable)
            return
        }
        if (whisperModel.isModelReady()) {
            startWhisperListening(onEvent)
            return
        }
        whisperScope.launch {
            val ready = whisperModel.ensureModel()
            withContext(Dispatchers.Main) {
                if (ready) startWhisperListening(onEvent)
                else onEvent(Event.Unavailable)
            }
        }
    }

    private fun startCloudListening(lang: VoiceLanguage, onEvent: (Event) -> Unit) {
        stopListening()
        listening = true
        // Only Yoruba, Hausa, and Hebrew send a language hint.
        val hintCode = when (lang) {
            VoiceLanguage.YORUBA, VoiceLanguage.HAUSA, VoiceLanguage.HEBREW -> lang.code
            else -> ""
        }
        startVoiceCapture(maxSeconds = CLOUD_MAX_SECONDS) { sample ->
            listening = false
            when (sample) {
                is VoiceSample.Unavailable -> mainHandler.post { onEvent(Event.Unavailable) }
                is VoiceSample.NoAudio -> mainHandler.post {
                    onEvent(Event.Error("I did not catch that. Say it again.", silent = true))
                }
                is VoiceSample.Captured -> {
                    whisperScope.launch {
                        val result = runCatching {
                            cloudAsr?.transcribe(sample.pcm, sample.sampleRate, hintCode)
                        }.getOrNull()
                        val cleaned = result?.text?.let { cleanRecognizedText(it) }
                        withContext(Dispatchers.Main) {
                            if (cleaned.isNullOrBlank()) {
                                // Cloud returned nothing. Fall back to on-device listening.
                                startOnDeviceListening(onEvent)
                            } else {
                                onEvent(
                                    Event.Final(
                                        cleaned,
                                        listOf(cleaned),
                                        sample.pcm,
                                        detectedLanguage = result?.detectedLanguage
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startWhisperListening(onEvent: (Event) -> Unit) {
        stopListening()
        val rec = whisperRecognizer ?: WhisperRecognizer(
            modelManager = whisperModel,
            languageCode = { whisperCodeFor(voiceLanguage()) }
        ).also { whisperRecognizer = it }
        listening = true
        rec.start { event ->
            if (event is Event.Final || event is Event.Error || event is Event.Unavailable) {
                listening = false
            }
            mainHandler.post { this.onEvent?.invoke(event) }
        }
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        try {
            whisperRecognizer?.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
        stopCaptureSignal()
    }

    fun startVoiceCapture(maxSeconds: Int, onResult: (VoiceSample) -> Unit) {
        captureSession++
        stopCaptureSignal()
        val session = captureSession
        synchronized(captureLock) {
            captureListener = onResult
            captureListenerSession = session
            capturedBytes.reset()
            audioOk = false
        }
        capturing.set(true)
        captureDone.set(false)
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            captureDone.set(true)
            deliverCapture(session, VoiceSample.Unavailable)
            return
        }
        captureExecutor().execute {
            try {
                runCapture(minBuf, maxSeconds, session)
            } finally {
                captureDone.set(true)
            }
        }
    }

    fun stopVoiceCapture() {
        if (!capturing.get()) return
        stopCaptureSignal()
    }

    fun destroy() {
        stopListening()
        stopVoiceCapture()
        whisperScope.cancel()
        try {
            whisperRecognizer?.release()
        } catch (e: Exception) {
            // Already released.
        }
        whisperRecognizer = null
        captureExecutor?.shutdownNow()
        captureExecutor = null
    }

    private fun captureExecutor(): ExecutorService {
        var ex = captureExecutor
        if (ex == null || ex.isShutdown) {
            ex = Executors.newSingleThreadExecutor()
            captureExecutor = ex
        }
        return ex
    }

    /**
     * Captures audio using the adaptive noise floor gate:
     * 250 ms calibration on quiet frames, clamped floor [150, 1200],
     * threshold max(400, floor * 1.8), and 2 consecutive active frames
     * before speech starts. Returns NoAudio if no real speech passed the gate.
     */
    private fun runCapture(minBuf: Int, maxSeconds: Int, session: Int) {
        if (isStaleCaptureSession(session, captureSession)) {
            deliverCapture(session, VoiceSample.NoAudio)
            return
        }
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            null
        } ?: try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            null
        }
        audioRecord = record
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            try {
                record?.release()
            } catch (e: Exception) {
                // Nothing to release.
            }
            deliverCapture(session, VoiceSample.Unavailable)
            return
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            deliverCapture(session, VoiceSample.Unavailable)
            return
        }
        audioOk = true
        val buf = ByteArray(minBuf)
        val maxBytes = sampleRate * 2 * 30
        val startedMs = SystemClock.uptimeMillis()
        var speechHeard = false
        var onsetStreak = 0
        var lastSpeechMs = startedMs
        var baselineSum = 0.0
        var baselineCount = 0
        var baselineRms = WhisperRecognizer.SILENCE_RMS / WhisperRecognizer.NOISE_FLOOR_MULTIPLIER

        while (capturing.get()) {
            if (isStaleCaptureSession(session, captureSession)) break
            val nowMs = SystemClock.uptimeMillis()
            if (nowMs - startedMs >= maxSeconds * 1000L) break
            val read = try {
                record.read(buf, 0, buf.size)
            } catch (e: Exception) {
                break
            }
            if (read <= 0) break

            val shorts = bytesToShorts(buf, read)
            val rms = WhisperRecognizer.rmsOf(shorts)

            // Calibrate room noise during the first 250 ms using quiet frames only
            // so a loud first frame does not poison the floor.
            if (!speechHeard && nowMs - startedMs < WhisperRecognizer.CALIBRATION_MS) {
                if (rms <= BASELINE_MAX_RMS) {
                    baselineSum += rms
                    baselineCount++
                    baselineRms = (baselineSum / baselineCount).coerceIn(BASELINE_MIN_RMS, BASELINE_MAX_RMS)
                }
                continue
            }

            if (!WhisperRecognizer.isSilentAdaptive(rms, baselineRms)) {
                onsetStreak++
                if (onsetStreak >= WhisperRecognizer.ONSET_FRAMES_REQUIRED) {
                    speechHeard = true
                    lastSpeechMs = nowMs
                }
            } else {
                onsetStreak = 0
            }

            if (speechHeard && shouldStopCapture(speechHeard, nowMs - lastSpeechMs)) break
            if (speechHeard) {
                synchronized(captureLock) {
                    if (!isStaleCaptureSession(session, captureSession) && capturedBytes.size() < maxBytes) {
                        capturedBytes.write(buf, 0, minOf(read, maxBytes - capturedBytes.size()))
                    }
                }
            }
        }
        try {
            record.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
        record.release()
        val bytes = synchronized(captureLock) { capturedBytes.toByteArray() }
        if (audioOk && speechHeard && bytes.isNotEmpty()) {
            deliverCapture(session, VoiceSample.Captured(bytesToShorts(bytes), sampleRate))
        } else {
            deliverCapture(session, VoiceSample.NoAudio)
        }
    }

    private fun deliverCapture(session: Int, sample: VoiceSample) {
        val listener = synchronized(captureLock) {
            if (captureListenerSession != session) null
            else {
                captureListenerSession = -1
                captureListener.also { captureListener = null }
            }
        } ?: return
        listener(sample)
    }

    private fun stopCaptureSignal() {
        capturing.set(false)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray = bytesToShorts(bytes, bytes.size)

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
