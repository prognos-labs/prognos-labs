package xyz.prognoslabs.app

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.isNoiseTranscript
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.voice.GroqAsrProvider
import xyz.prognoslabs.app.voice.VoiceGate
import xyz.prognoslabs.app.voice.WhisperModelManager

/**
 * v1.1.25: the voice fixes Flint ordered after v1.1.24 still failed
 * on his phone. "Bonjour" gets a greeting instead of "say it again",
 * the cloud capture keeps the first half second of a short word, the
 * on-device gate stops hearing steady room noise as speech, breaths
 * no longer loop "say it again", and the mic recovers instead of
 * going dead after the cache is cleared.
 *
 * These tests run on the JVM. There is no microphone and no phone
 * here, so the gate, the parser and the recovery are proven through
 * pure helpers and source wiring, and the real proof is still Flint's
 * phone.
 */
class V1125Test {

    private fun mainRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return dir
    }

    private fun readSource(relative: String): String =
        File(mainRoot(), relative).readText()

    @Test
    fun versionIs1125() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 37"))
        assertTrue(gradle.contains("versionName = \"1.1.27\""))
    }

    // ---- Fix 1A: the proxy returns the language (verbose_json) ----

    @Test
    fun groqVerboseJsonLanguageCodeIsDetected() {
        val res = GroqAsrProvider.parseGroqAsrResponse(
            "{\"text\": \"Bonjour\", \"language\": \"fr\"}"
        )
        assertEquals("Bonjour", res?.text)
        assertEquals(VoiceLanguage.FRENCH, res?.detectedLanguage)
    }

    @Test
    fun groqVerboseJsonLanguageNameIsDetected() {
        // Groq returns the language as a name ("French"), not a code.
        val res = GroqAsrProvider.parseGroqAsrResponse(
            "{\"text\": \"Bonjour\", \"language\": \"French\"}"
        )
        assertEquals(VoiceLanguage.FRENCH, res?.detectedLanguage)
    }

    @Test
    fun groqResponseWithoutLanguageFallsBack() {
        val res = GroqAsrProvider.parseGroqAsrResponse(
            "{\"text\": \"Bonjour\"}"
        )
        assertEquals("Bonjour", res?.text)
        assertEquals(null, res?.detectedLanguage)
    }

    // ---- Fix 5: greetings work in any language ----

    @Test
    fun greetingInAnyLanguageIsAnswered() {
        for (word in listOf(
            "bonjour", "salut", "hola", "ciao", "hallo", "olá",
            "merhaba", "selam", "shalom", "salam", "ni hao",
            "bawo", "sannu", "ndewo"
        )) {
            val (res, _) = parseBestVoiceUtterance(listOf(word), emptyList())
            assertTrue("'$word' must greet, got $res", res is VoiceParseResult.Question)
            val q = res as VoiceParseResult.Question
            assertEquals(VoiceQuestion.GreetingHello, q.question)
        }
    }

    @Test
    fun greetingWithPunctuationStillGreets() {
        val (res, _) = parseBestVoiceUtterance(listOf("Bonjour."), emptyList())
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(
            VoiceQuestion.GreetingHello,
            (res as VoiceParseResult.Question).question
        )
    }

    // ---- Fix 2B: breaths and room noise are silence, not words ----

    @Test
    fun breathTranscriptsAreNoise() {
        assertTrue(isNoiseTranscript("."))
        assertTrue(isNoiseTranscript("The"))
        assertTrue(isNoiseTranscript("the."))
        assertTrue(isNoiseTranscript("um"))
        assertTrue(isNoiseTranscript("[BLANK_AUDIO]"))
        assertTrue(isNoiseTranscript("Thank you."))
    }

    @Test
    fun realOneWordAnswersAreNotNoise() {
        assertFalse(isNoiseTranscript("yes"))
        assertFalse(isNoiseTranscript("no"))
        assertFalse(isNoiseTranscript("hello"))
        assertFalse(isNoiseTranscript("bonjour"))
        assertFalse(isNoiseTranscript("buy 50 bitcoin"))
    }

    // ---- Fix 2A: steady room noise stops counting as speech ----

    // One frame every 100 ms, like the recognizer reading its buffers.
    private fun feed(gate: VoiceGate, frames: List<Double>): VoiceGate {
        var now = 0L
        for (rms in frames) {
            gate.onFrame(rms, now, now)
            now += 100L
        }
        return gate
    }

    @Test
    fun steadyRoomNoiseIsNotSpeech() {
        // The v1.1.24 failure: a room louder than 400 RMS never raised
        // the floor, so steady noise passed the gate as speech.
        val gate = feed(VoiceGate(), List(10) { 600.0 })
        assertFalse(gate.speechHeard)
        assertTrue(gate.noiseFloorRms > 400.0)
    }

    @Test
    fun loudSpeechAfterSteadyNoiseIsHeard() {
        val gate = feed(
            VoiceGate(),
            listOf(600.0, 600.0, 600.0, 1500.0, 1600.0)
        )
        assertTrue(gate.speechHeard)
    }

    @Test
    fun moderateSpeechFromFirstFrameIsHeard() {
        // Speech starting the moment the mic opens must not poison
        // the floor: a varying voice is not steady room noise.
        val gate = feed(
            VoiceGate(),
            listOf(800.0, 1200.0, 700.0, 1500.0, 900.0)
        )
        assertTrue(gate.speechHeard)
        assertTrue(gate.noiseFloorRms < 400.0)
    }

    // ---- Cache bug: the model check is honest and self-healing ----

    @Test
    fun bundledModelCheckOpensTheAsset() {
        val dir = File(System.getProperty("java.io.tmpdir"), "v1125-model")
        dir.mkdirs()
        val withAsset = WhisperModelManager(
            dir,
            bundledModel = { ByteArrayInputStream(ByteArray(0)) }
        )
        assertTrue(withAsset.hasBundledModel())
        val withoutAsset = WhisperModelManager(
            dir,
            bundledModel = { throw IllegalStateException("no asset") }
        )
        assertFalse(withoutAsset.hasBundledModel())
        val noBundled = WhisperModelManager(dir, bundledModel = null)
        assertFalse(noBundled.hasBundledModel())
    }

    @Test
    fun deleteModelRemovesTheFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "v1125-model-del")
        val modelDir = File(dir, "whisper")
        modelDir.mkdirs()
        val modelFile = File(modelDir, WhisperModelManager.MODEL_FILE_NAME)
        modelFile.writeBytes(ByteArray(10))
        val manager = WhisperModelManager(dir, bundledModel = null)
        manager.deleteModel()
        assertFalse(modelFile.exists())
        assertFalse(manager.isModelReady())
    }

    // ---- Wiring: pre-roll, short-capture guard, auto-detect fallback ----

    @Test
    fun cloudCaptureKeepsPreRollAndShortGuard() {
        val engineSrc = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt"
        )
        assertTrue(engineSrc.contains("preRoll"))
        assertTrue(engineSrc.contains("MIN_CAPTURE_SECONDS"))
        assertTrue(engineSrc.contains("VoiceGate()"))
        // The cloud fallback lets the on-device recognizer detect the
        // language itself instead of forcing the Settings language.
        assertTrue(engineSrc.contains("autoDetect = true"))
        assertTrue(engineSrc.contains("\"auto\""))
    }

    @Test
    fun onDeviceRecognizerTakesALanguageOverride() {
        val recSrc = readSource(
            "app/src/main/java/xyz/prognoslabs/app/voice/WhisperRecognizer.kt"
        )
        assertTrue(recSrc.contains("fun setLanguageCode"))
        assertTrue(recSrc.contains("languageOverride ?: languageCode()"))
        assertTrue(recSrc.contains("MIN_SEGMENT_MS"))
        assertTrue(recSrc.contains("deleteModel()"))
    }

    @Test
    fun noiseTranscriptStaysQuietInViewModel() {
        val vmSrc = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt"
        )
        assertTrue(vmSrc.contains("isNoiseTranscript"))
        assertTrue(vmSrc.contains("voiceNoiseRestarts"))
        // The listen watchdog must outlast a slow cloud transcription,
        // or a slow first answer after a cold start is killed and
        // looks like a dead mic.
        assertTrue(vmSrc.contains("delay(25_000L)"))
    }
}
