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
import xyz.prognoslabs.app.voice.VoiceGate
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
            val detectedLanguage: VoiceLanguage? = null,
            /**
             * The language each candidate was transcribed in, in
             * the same order as [candidates]. Empty means every
             * candidate is in the detected language (or the one set
             * in Settings): the old single-language path. v1.1.28
             * fills it when the cloud retry ran, so the parser can
             * score the auto-detected transcript in its language
             * and the forced retry in the Settings language.
             */
            val candidateLanguages: List<VoiceLanguage> = listOf()
        ) : Event()
        data class Error(val message: String, val silent: Boolean = false) : Event()
        object Unavailable : Event()
    }

    companion object {
        val RECOGNITION_LOCALE: Locale = Locale.US
        const val CAPTURE_SILENCE_TO_STOP_MS = 1200L
        const val CLOUD_MAX_SECONDS = 15

        /** A captured clip shorter than this is a fragment, not a
         * word: keep listening instead of transcribing it. */
        const val MIN_CAPTURE_SECONDS = 0.8

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

    private fun startOnDeviceListening(onEvent: (Event) -> Unit, autoDetect: Boolean = false) {
        if (!WhisperNative.isAvailable) {
            onEvent(Event.Unavailable)
            return
        }
        if (whisperModel.isModelReady()) {
            startWhisperListening(onEvent, autoDetect)
            return
        }
        whisperScope.launch {
            val ready = whisperModel.ensureModel()
            withContext(Dispatchers.Main) {
                if (ready) startWhisperListening(onEvent, autoDetect)
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
                                // Cloud returned nothing. Fall back to
                                // on-device listening, and let it detect
                                // the language itself: the cloud just
                                // failed, so nothing is known about what
                                // was spoken, and forcing the language
                                // set in Settings onto audio spoken in
                                // another language only returns garbage.
                                startOnDeviceListening(onEvent, autoDetect = true)
                            } else {
                                val detected = result?.detectedLanguage
                                // v1.1.28: the detector can mishear
                                // accented speech as another language
                                // entirely. Flint's English "Open my
                                // portfolio now" came back as
                                // Portuguese "Ai, mas posso folio
                                // não", and the app refused it. When
                                // the detected language is not the
                                // one set in Settings, run the same
                                // audio once more forced to the
                                // Settings language, and hand both
                                // transcripts to the parser: it scores
                                // each in its own language and the
                                // better parse wins. A genuine turn
                                // in another language still wins its
                                // tie, because the auto transcript
                                // comes first. One extra call, only
                                // on a mismatch, so the free tier is
                                // not burned on ordinary turns.
                                var candidates = listOf(cleaned)
                                var candidateLanguages = listOf<VoiceLanguage>()
                                if (detected != null && detected != lang && lang != VoiceLanguage.IGBO) {
                                    val forced = runCatching {
                                        cloudAsr?.transcribe(sample.pcm, sample.sampleRate, lang.code)
                                    }.getOrNull()
                                    val forcedCleaned = forced?.text?.let { cleanRecognizedText(it) }
                                    if (!forcedCleaned.isNullOrBlank() && forcedCleaned != cleaned) {
                                        candidates = listOf(cleaned, forcedCleaned)
                                        candidateLanguages = listOf(detected, lang)
                                    }
                                }
                                onEvent(
                                    Event.Final(
                                        cleaned,
                                        candidates,
                                        sample.pcm,
                                        detectedLanguage = detected,
                                        candidateLanguages = candidateLanguages
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startWhisperListening(onEvent: (Event) -> Unit, autoDetect: Boolean = false) {
        stopListening()
        val rec = whisperRecognizer ?: WhisperRecognizer(
            modelManager = whisperModel,
            languageCode = { whisperCodeFor(voiceLanguage()) }
        ).also { whisperRecognizer = it }
        // The language for this listen: "auto" when the cloud just
        // missed and nothing is known about what was spoken, so the
        // on-device recognizer detects it instead of forcing the
        // language set in Settings; otherwise the language set in
        // Settings, as before.
        rec.setLanguageCode(if (autoDetect) "auto" else whisperCodeFor(voiceLanguage()))
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
     * Captures audio through the shared speech gate (VoiceGate):
     * 250 ms calibration on quiet frames only, steady room noise
     * absorbed into the floor, and 2 consecutive active frames
     * before speech starts. Frames heard before speech is confirmed
     * are kept in a pre-roll buffer and prepended once speech is
     * confirmed, so a word spoken the moment the mic opens arrives
     * whole: v1.1.24 threw the first 250 ms away and needed two loud
     * frames before it wrote anything, so a short word arrived
     * truncated and the recognizer invented a sentence from the
     * fragment. Returns NoAudio if no real speech passed the gate,
     * or if the clip is shorter than a real word.
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
        val gate = VoiceGate()
        val buf = ByteArray(minBuf)
        val maxBytes = sampleRate * 2 * 30
        val preRollMaxBytes = sampleRate * 2
        val preRoll = ArrayDeque<ByteArray>()
        var preRollBytes = 0
        val startedMs = SystemClock.uptimeMillis()
        var speechHeard = false

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
            val elapsedMs = nowMs - startedMs
            val wasHeard = speechHeard
            gate.onFrame(rms, nowMs, elapsedMs)
            speechHeard = gate.speechHeard

            if (speechHeard) {
                if (!wasHeard) {
                    // Speech just confirmed: the frames kept in the
                    // pre-roll are the start of this word, so they go
                    // into the capture first, then this frame.
                    synchronized(captureLock) {
                        if (!isStaleCaptureSession(session, captureSession)) {
                            for (frame in preRoll) {
                                if (capturedBytes.size() >= maxBytes) break
                                capturedBytes.write(frame, 0, minOf(frame.size, maxBytes - capturedBytes.size()))
                            }
                        }
                    }
                    preRoll.clear()
                    preRollBytes = 0
                }
                if (shouldStopCapture(speechHeard, gate.silenceMs(nowMs))) break
                synchronized(captureLock) {
                    if (!isStaleCaptureSession(session, captureSession) && capturedBytes.size() < maxBytes) {
                        capturedBytes.write(buf, 0, minOf(read, maxBytes - capturedBytes.size()))
                    }
                }
            } else {
                preRoll.addLast(buf.copyOf(read))
                preRollBytes += read
                while (preRollBytes > preRollMaxBytes && preRoll.isNotEmpty()) {
                    preRollBytes -= preRoll.removeFirst().size
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
        val durationSec = bytes.size.toDouble() / (sampleRate * 2)
        if (audioOk && speechHeard && bytes.isNotEmpty() && durationSec >= MIN_CAPTURE_SECONDS) {
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
