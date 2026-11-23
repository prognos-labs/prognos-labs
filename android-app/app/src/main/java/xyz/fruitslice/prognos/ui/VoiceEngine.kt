package xyz.fruitslice.prognos.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
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
import xyz.fruitslice.prognos.data.VoiceLanguage
import xyz.fruitslice.prognos.data.voiceLanguageForLocale
import xyz.fruitslice.prognos.data.voiceLanguageSupported
import xyz.fruitslice.prognos.voice.WhisperModelManager
import xyz.fruitslice.prognos.voice.WhisperNative
import xyz.fruitslice.prognos.voice.WhisperRecognizer

/**
 * The voice engine: the on-device speech library, and only it.
 * v1.1.16: the phone's built-in recognizer is gone. The app used
 * to fall back to it whenever the model was not downloaded yet,
 * which is why it still heard badly on Flint's phone: the weak
 * recognizer was doing the listening. Now every listen goes
 * through the speech library, whose model ships inside the APK,
 * so there is no download and no wait. If the library cannot run
 * at all, the mic says voice is unavailable; it never falls back
 * to the phone's recognizer.
 *
 * The two passes NEVER run at the same time. v1.1.6 ran them in
 * parallel: the recognizer and an AudioRecord capture shared the
 * microphone, and on some phones (Flint's Samsung) the capture took
 * the microphone first, so the recognizer heard silence for the
 * whole timeout and came back with no match, twice, on two phrases.
 * The words pass runs the library alone; the voice check runs
 * the capture alone afterwards. A trade still needs the words
 * recognized AND the voice check passed, just not from the same
 * recording. The tap buttons always stay available.
 *
 * Recognition callbacks arrive on the main thread. The capture and
 * the feature extraction run off the main thread, so prices keep
 * updating while listening.
 */
class VoiceEngine(private val context: Context) {

    sealed class Event {
        /** Words heard so far, shown live in the listening sheet. */
        data class Partial(val text: String) : Event()

        /** The finished sentence, with every candidate the recognizer
         * offered, best first. The view model scores the candidates
         * against what the app can actually do and takes the best
         * parse, instead of trusting the top result blindly. */
        data class Final(val text: String, val candidates: List<String> = listOf(text)) : Event()

        /** Something went wrong; the message is plain words for the sheet.
         * [silent] means the recognizer heard nothing at all (silence or
         * no match): the session can simply listen again instead of
         * closing on the user. */
        data class Error(val message: String, val silent: Boolean = false) : Event()

        /** No speech recognition on this device at all. */
        object Unavailable : Event()
    }

    companion object {
        /**
         * The recognition language, English, the default and the
         * fallback. v1.1.12 follows the phone's language when this
         * build supports it (see [recognitionLocale]); the old build
         * pinned US English, which left non-English speakers
         * unheard. US English stays the fallback for every language
         * the build does not support yet, and it is still the
         * best-trained model for English itself.
         */
        val RECOGNITION_LOCALE: Locale = Locale.US

        /**
         * v1.1.14: the microphone-only capture (enrollment and the
         * trade-time voice check) ends by itself once the user has
         * spoken and then gone quiet, the same 1,500 ms of silence
         * the speech library uses. The old capture ran until the
         * user tapped Stop or the 10-second cap, so enrollment never
         * finished on its own.
         */
        const val CAPTURE_SILENCE_TO_STOP_MS = 1_500L

        /**
         * True when the capture should end on its own: speech was
         * heard and silence has run long enough. Silence before any
         * speech never ends it: the user may not have started yet.
         */
        fun shouldStopCapture(speechHeard: Boolean, silenceMs: Long): Boolean =
            speechHeard && silenceMs >= CAPTURE_SILENCE_TO_STOP_MS

        /**
         * True when a queued capture task is stale: a newer capture
         * has started since it was queued, so it must exit without
         * touching the microphone. The old code let a stale task
         * open the mic and hold it for up to 10 seconds while the
         * real capture waited behind it, which is how an enrollment
         * phrase could go unheard.
         */
        fun isStaleCaptureSession(session: Int, currentSession: Int): Boolean =
            session != currentSession
    }

    /**
     * The recognition locale: the phone's language when this build
     * supports it, US English otherwise. TTS follows the same
     * locale, so the spoken challenge is in the user's language.
     */
    fun recognitionLocale(): Locale {
        val device = Locale.getDefault()
        return if (voiceLanguageSupported(device)) device else RECOGNITION_LOCALE
    }

    /** The voice language this device speaks, from the phone's language. */
    fun voiceLanguage(): VoiceLanguage = voiceLanguageForLocale(recognitionLocale())

    /** True when the phone's language is one this build supports. */
    fun deviceLanguageSupported(): Boolean = voiceLanguageSupported(Locale.getDefault())

    /** Result of a microphone-only voice sample. */
    sealed class VoiceSample {
        /** Raw mono 16-bit audio at [sampleRate]. */
        data class Captured(val pcm: ShortArray, val sampleRate: Int) : VoiceSample()

        /** The microphone could not be opened at all. */
        object Unavailable : VoiceSample()

        /** Recording ran but nothing usable was heard. */
        object NoAudio : VoiceSample()
    }

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
    /** Read by the capture thread, written by the UI thread: a stale
     * queued capture must see the newer session and stand down. */
    @Volatile
    private var captureSession = 0
    private var onEvent: ((Event) -> Unit)? = null
    private var listening = false

    /** The speech model: bundled in the APK, copied into the app's
     * private files on first use and checked by size and hash. */
    private val whisperModel = WhisperModelManager(
        context.filesDir,
        bundledModel = { context.assets.open(WhisperModelManager.MODEL_FILE_NAME) }
    )
    private var whisperRecognizer: WhisperRecognizer? = null

    /** Background model copy and nothing else. Cancelled on destroy. */
    private val whisperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Warm the model up at app start, in the background: by
        // the time the user taps the mic, the bundled copy is
        // already in place and listening starts at once.
        if (WhisperNative.isAvailable) {
            whisperScope.launch { whisperModel.ensureModel() }
        }
    }

    /** True when the voice can be made ready: the model is already
     * verified, or a bundled copy is in the APK waiting to be
     * copied out. The first tap copies it, so a missing file is
     * not "unavailable". The native library is the only thing that
     * can stop it, and that is found when listening starts. */
    fun isAvailable(): Boolean =
        whisperModel.isModelReady() || whisperModel.hasBundledModel()

    /**
     * Words pass: the on-device speech library, and only it. The
     * model is bundled in the APK, so the first listen copies it
     * into place silently (a local copy, no download, no progress
     * screen) and then listens at once. If the library cannot run,
     * the caller hears Unavailable; there is no fallback to the
     * phone's recognizer.
     */
    fun startListening(onEvent: (Event) -> Unit) {
        this.onEvent = onEvent
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
            if (ready) {
                withContext(Dispatchers.Main) { startWhisperListening(onEvent) }
            } else {
                onEvent(Event.Unavailable)
            }
        }
    }

    /** Words pass through the on-device speech library. */
    private fun startWhisperListening(onEvent: (Event) -> Unit) {
        stopListening()
        val rec = whisperRecognizer ?: WhisperRecognizer(whisperModel) {
            recognitionLocale().language
        }.also { whisperRecognizer = it }
        listening = true
        rec.start { event ->
            if (event is Event.Final || event is Event.Error || event is Event.Unavailable) {
                listening = false
            }
            this.onEvent?.invoke(event)
        }
    }

    /** Stops listening; the recognizer still reports what it heard. */
    fun stopListening() {
        if (!listening) return
        listening = false
        try {
            whisperRecognizer?.stop()
        } catch (e: Exception) {
            // Already stopped.
        }
    }

    /**
     * Voice sample pass: the microphone alone, no recognizer. Records
     * until [stopVoiceCapture] is called or [maxSeconds] pass, then
     * delivers the audio on a background thread. Only one pass runs
     * at a time: starting a new one ends the old one, and the old
     * caller's result is dropped, never delivered twice.
     */
    fun startVoiceCapture(maxSeconds: Int, onResult: (VoiceSample) -> Unit) {
        // End any running pass first; its result is dropped.
        captureSession++
        stopCaptureSignal()
        val session = captureSession
        synchronized(captureLock) {
            captureListener = onResult
            captureListenerSession = session
            capturedBytes.reset()
        }
        audioOk = false
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

    /** Ends the voice sample pass; the recorded audio is delivered. */
    fun stopVoiceCapture() {
        if (!capturing.get()) return
        stopCaptureSignal()
    }

    private var tts: TextToSpeech? = null

    /**
     * Speaks a short prompt out loud (the challenge phrase). The
     * speaker is created lazily and guarded: if the device has no
     * text-to-speech, speaking is skipped silently and the on-screen
     * phrase is enough.
     */
    fun speak(text: String) {
        try {
            val existing = tts
            if (existing != null) {
                existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prognos-voice")
                return
            }
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = recognitionLocale()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prognos-voice")
                }
            }
        } catch (e: Exception) {
            // No text-to-speech on this device: skip silently.
        }
    }

    /** Stops any prompt that is speaking. Never throws. */
    fun stopSpeaking() {
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

    /** One capture thread for the life of the engine: passes run one
     * after another, never together, so a new pass never fights the
     * old one for the microphone. */
    private fun captureExecutor(): ExecutorService {
        var ex = captureExecutor
        if (ex == null || ex.isShutdown) {
            ex = Executors.newSingleThreadExecutor()
            captureExecutor = ex
        }
        return ex
    }

    private fun runCapture(minBuf: Int, maxSeconds: Int, session: Int) {
        // A capture queued behind a newer one stands down without
        // touching the microphone: the newer pass owns it.
        if (isStaleCaptureSession(session, captureSession)) {
            deliverCapture(session, VoiceSample.NoAudio)
            return
        }
        val record = try {
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
        // 30 seconds of audio is the most we keep.
        val maxBytes = sampleRate * 2 * 30
        val startedMs = android.os.SystemClock.uptimeMillis()
        // v1.1.14: the capture ends by itself once the user has
        // spoken and then gone quiet, so enrollment never waits on
        // a Stop tap. Silence before any speech does not end it.
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
                // A stale pass must not write into the new pass's
                // buffer: the new pass reset it after this task was
                // queued, so anything written here would be the old
                // phrase's audio at the front of the new sample.
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

    /** Delivers a capture result once, and only to the pass that is
     * still current. A pass ended early never hears back. */
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

    /** Tells the capture thread to stop, without waiting for it. */
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
