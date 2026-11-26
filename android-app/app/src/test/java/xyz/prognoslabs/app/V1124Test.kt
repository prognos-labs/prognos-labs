package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.data.platformAnswer

/**
 * v1.1.24: the spoken voice is gone, the mic no longer hears
 * breathing as speech, and "can I see my portfolio" opens the
 * portfolio instead of refusing. Winnings are paid in 30 days, in
 * USDT (Flint, 26 Nov).
 *
 * These tests run on the JVM. There is no microphone and no phone
 * here, so the gate and the navigation are proven through pure
 * helpers and source wiring, and the real proof is still Flint's
 * phone.
 */
class V1124Test {

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
    fun versionIs1124() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 35"))
        assertTrue(gradle.contains("versionName = \"1.1.25\""))
    }

    // ---- Direction 3: the portfolio is back ----

    @Test
    fun portfolioQuestionNavigatesToPortfolio() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Can I see my portfolio on Prognos Labs?"),
            emptyList()
        )
        assertTrue(res is VoiceParseResult.Navigate)
        val nav = res as VoiceParseResult.Navigate
        assertEquals("portfolio", nav.route)
        assertEquals("your portfolio", nav.label)
    }

    // ---- Section 11: winnings paid in 30 days, in USDT ----

    @Test
    fun payoutAnswerMentions30DaysAndUsdt() {
        val ans = platformAnswer("when are winnings paid and in what coin", VoiceLanguage.ENGLISH)
        assertNotNull(ans)
        assertTrue(ans!!.contains("30 days"))
        assertTrue(ans.contains("USDT"))
    }

    // ---- Direction 1: no spoken voice anywhere ----

    @Test
    fun noTtsUsageInVoiceEngineOrViewModel() {
        val engineSrc = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt"
        )
        val vmSrc = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt"
        )
        assertFalse(engineSrc.contains("TextToSpeech"))
        assertFalse(engineSrc.contains("ttsLocale"))
        assertFalse(engineSrc.contains("speakAndThen"))
        assertFalse(vmSrc.contains("voiceEngine.speak"))
        assertFalse(vmSrc.contains("stopSpeaking"))
    }

    // ---- Direction 2: the noise gate is wired into capture ----
    // v1.1.25: the cloud capture no longer keeps its own inline copy
    // of the gate. It runs the shared VoiceGate, so the two paths
    // cannot drift apart again the way they did in v1.1.24.

    @Test
    fun adaptiveNoiseGateWiredIntoCapture() {
        val engineSrc = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt"
        )
        assertTrue(engineSrc.contains("VoiceGate()"))
        assertTrue(engineSrc.contains("gate.onFrame"))
        assertTrue(engineSrc.contains("preRoll"))
        assertTrue(engineSrc.contains("MIN_CAPTURE_SECONDS"))
        assertTrue(engineSrc.contains("VoiceSample.NoAudio"))
        val gateSrc = readSource(
            "app/src/main/java/xyz/prognoslabs/app/voice/VoiceGate.kt"
        )
        assertTrue(gateSrc.contains("isSilentAdaptive"))
        assertTrue(gateSrc.contains("WhisperRecognizer.CALIBRATION_MS"))
        assertTrue(gateSrc.contains("WhisperRecognizer.ONSET_FRAMES_REQUIRED"))
    }
}
