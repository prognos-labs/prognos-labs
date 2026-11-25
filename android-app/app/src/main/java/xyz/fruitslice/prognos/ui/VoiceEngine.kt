package xyz.fruitslice.prognos.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.fruitslice.prognos.data.VoiceLanguage
import xyz.fruitslice.prognos.data.cleanRecognizedText
import xyz.fruitslice.prognos.data.voiceLanguageForLocale
import xyz.fruitslice.prognos.data.voiceLanguageSupported
import xyz.fruitslice.prognos.data.whisperCodeFor
import xyz.fruitslice.prognos.voice.WhisperModelManager
import xyz.fruitslice.prognos.voice.WhisperNative
import xyz.fruitslice.prognos.voice.WhisperRecognizer

/**
 * Optional cloud ASR provider for languages where on-device ggml-tiny
 * has low accuracy (e.g. Igbo, Yoruba, Hausa, Hebrew).
 */
fun interface AsrProvider {
    suspend fun transcribe(pcm: ShortArray, sampleRate: Int, languageCode: String): String?
}

class VoiceEngine(
    private val context: Context,
    private val cloudAsr: AsrProvider? = null
) {
    sealed class Event {
        /** Live normalized mic amplitude (0f..1f) for waveform animation. */
        data class Level(val rmsNorm: Float) : Event()

        /** Words heard so far (kept for compatibility). */
        data class Partial(val text: String) : Event()

        /**
         * Finished utterance with candidates and the captured 16 kHz mono PCM
         * so the voice-check pass can verify speaker features in a single pass.
         */
        data class Final(
            val text: String,
            val candidates: List<String> = listOf(text),
            val pcm: ShortArray = shortArrayOf()
        ) : Event()

        data class Error(val message: String, val silent: Boolean = false) : Event()

        object Unavailable : Event()
    }

    companion object {
        val RECOGNITION_LOCALE: Locale = Locale.US
        const val CAPTURE_SILENCE_TO_STOP_MS = 1_500L

        fun shouldStopCapture(speechHeard: Boolean, silenceMs: Long): Boolean =
            speechHeard && silenceMs >= CAPTURE_SILENCE_TO_STOP_MS

        fun isStaleCaptureSession(session: Int, currentSession: Int): Boolean =
            session != currentSession
    }

    @Volatile
    private var overrideLanguage: VoiceLanguage? = null

    fun setLanguageOverride(language: VoiceLanguage?) {
        overrideLanguage = language
        tts?.language = recognitionLocale()
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
        (cloudAsr != null && voiceLanguage().requiresCloudAsr) ||
            whisperModel.isModelReady() ||
            whisperModel.hasBundledModel()

    fun startListening(onEvent: (Event) -> Unit) {
        this.onEvent = onEvent
        val lang = voiceLanguage()

        // Route tonal / non-Whisper-ready languages to cloud ASR when configured.
        if (lang.code != "ig" && lang.requiresCloudAsr && cloudAsr != null) {
            startCloudListening(lang.code, onEvent)
            return
        }

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

    private fun startCloudListening(langCode: String, onEvent: (Event) -> Unit) {
        stopListening()
        listening = true
        startVoiceCapture(maxSeconds = 15) { sample ->
            listening = false
            when (sample) {
                is VoiceSample.Unavailable -> mainHandler.post { onEvent(Event.Unavailable) }
                is VoiceSample.NoAudio -> mainHandler.post {
                    onEvent(Event.Error("I did not catch that. Say it again.", silent = true))
                }
                is VoiceSample.Captured -> {
                    whisperScope.launch {
                        val raw = runCatching {
                            cloudAsr?.transcribe(sample.pcm, sample.sampleRate, langCode)
                        }.getOrNull()
                        val cleaned = raw?.let { cleanRecognizedText(it) }
                        withContext(Dispatchers.Main) {
                            if (cleaned.isNullOrBlank()) {
                                // Cloud heard nothing: fall back to on-device listening
                                // instead of erroring, so the user just says it again once.
                                startWhisperListening(onEvent)
                            } else {
                                onEvent(Event.Final(cleaned, listOf(cleaned), sample.pcm))
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

    private var tts: TextToSpeech? = null
    private val speakCallbacks = ConcurrentHashMap<String, () -> Unit>()

    fun speak(text: String) = speakAndThen(text, onDone = {})

    /**
     * Speaks [text] and invokes [onDone] on the main thread only after playback
     * finishes (or immediately if TTS is unavailable), preventing TTS-to-mic echo.
     */
    fun speakAndThen(text: String, onDone: () -> Unit) {
        val utteranceId = "prognos-voice-${System.nanoTime()}"
        speakCallbacks[utteranceId] = onDone
        try {
            val existing = tts
            if (existing != null) {
                existing.language = recognitionLocale()
                val res = existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (res != TextToSpeech.SUCCESS) fireSpeakDone(utteranceId)
                return
            }
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = recognitionLocale()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) = fireSpeakDone(id)
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) = fireSpeakDone(id)
                        override fun onError(id: String?, errorCode: Int) = fireSpeakDone(id)
                    })
                    val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                    if (res != TextToSpeech.SUCCESS) fireSpeakDone(utteranceId)
                } else {
                    fireSpeakDone(utteranceId)
                }
            }
        } catch (e: Exception) {
            fireSpeakDone(utteranceId)
        }
    }

    private fun fireSpeakDone(id: String?) {
        if (id == null) return
        val cb = speakCallbacks.remove(id) ?: return
        mainHandler.post { cb() }
    }

    fun stopSpeaking() {
        speakCallbacks.clear()
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Never crash on TTS.
        }
    }

    fun destroy() {
        stopListening()
        stopVoiceCapture()
        stopSpeaking()
        whisperScope.cancel()
        try {
            whisperRecognizer?.release()
        } catch (e: Exception) {
            // Already released.
        }
        whisperRecognizer = null
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // Already shut down.
        }
        tts = null
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
        val startedMs = android.os.SystemClock.uptimeMillis()
        var speechHeard = false
        var lastSpeechMs = startedMs

        while (capturing.get()) {
            if (isStaleCaptureSession(session, captureSession)) break
            if (android.os.SystemClock.uptimeMillis() - startedMs >= maxSeconds * 1000L) break
            val read = try {
                record.read(buf, 0, buf.size)
            } catch (e: Exception) {
                break
            }
            if (read <= 0) break
            val nowMs = android.os.SystemClock.uptimeMillis()
            if (!WhisperRecognizer.isSilent(WhisperRecognizer.rmsOf(bytesToShorts(buf, read)))) {
                speechHeard = true
                lastSpeechMs = nowMs
            }
            if (shouldStopCapture(speechHeard, nowMs - lastSpeechMs)) break
            synchronized(captureLock) {
                if (!isStaleCaptureSession(session, captureSession) && capturedBytes.size() < maxBytes) {
                    capturedBytes.write(buf, 0, minOf(read, maxBytes - capturedBytes.size()))
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
        if (audioOk && bytes.isNotEmpty()) {
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

