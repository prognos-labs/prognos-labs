package xyz.prognoslabs.app

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.data.platformAnswer
import xyz.prognoslabs.app.ui.VoiceEngine
import xyz.prognoslabs.app.ui.ttsLocale

/**
 * v1.1.22: Flint asked the app "can you take a trade for me" and
 * got the out-of-scope refusal. A bare trade request is a trade to
 * start, not a question to refuse: the voice now answers with the
 * guided trade prompt and keeps listening. Answers and the order
 * read-back are also spoken aloud with the phone's own free voice,
 * and the cloud capture ends after 800 ms of silence instead of
 * 1,500 ms, so the voice answers sooner.
 *
 * These tests run on the JVM. There is no microphone and no TTS
 * engine here, so the spoken voice is proven through its pure
 * locale helper and the source wiring, and the real proof is still
 * Flint's phone.
 */
class V1122Test {

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
    fun versionIs1122() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 32"))
        assertTrue(gradle.contains("versionName = \"1.1.22\""))
    }

    // ---- Error 1: "can you take a trade for me" must not refuse ----

    @Test
    fun takeATradeForMeGetsTheGuidedAnswer() {
        val answer = platformAnswer("can you take a trade for me on prognos labs")
        assertTrue(answer!!.startsWith("Trading is open. Tell me the market"))
    }

    @Test
    fun bareTradeRequestsGetTheGuidedAnswer() {
        for (phrase in listOf(
            "place a trade",
            "trade for me",
            "make a trade for me",
            "do a trade",
            "take this trade",
            "buy for me",
            "place an order",
            "make an order"
        )) {
            val answer = platformAnswer(phrase)
            assertTrue("no guided answer for: $phrase", answer != null)
            assertTrue(answer!!.contains("Tell me the market"))
        }
    }

    @Test
    fun tradeRequestParsesAsAQuestionNotARefusal() {
        val parsed = parseVoiceUtterance(
            "can you take a trade for me on prognos labs",
            emptyList()
        )
        assertTrue(parsed is VoiceParseResult.Question)
        val question = (parsed as VoiceParseResult.Question).question
        assertTrue(question is VoiceQuestion.Knowledge)
        assertTrue((question as VoiceQuestion.Knowledge).answer.contains("Tell me the market"))
        assertTrue(question.isConversational)
    }

    @Test
    fun bareBuyAsksWhichMarketInsteadOfRefusing() {
        // "buy for me" has a trade verb but no market: the order
        // path asks which market, it never refuses.
        val parsed = parseVoiceUtterance("buy for me", emptyList())
        assertTrue(parsed is VoiceParseResult.AskAgain)
        assertTrue((parsed as VoiceParseResult.AskAgain).message.contains("Which market?"))
    }

    @Test
    fun unrelatedQuestionsStillRefused() {
        val parsed = parseVoiceUtterance("what is the weather", emptyList())
        assertTrue(parsed is VoiceParseResult.OutOfScope)
    }

    // ---- The spoken voice: free, on the phone ----

    @Test
    fun ttsLocaleUsesTheOldHebrewCode() {
        assertEquals(Locale("iw"), ttsLocale(VoiceLanguage.HEBREW))
        assertEquals(Locale("en"), ttsLocale(VoiceLanguage.ENGLISH))
        assertEquals(Locale("yo"), ttsLocale(VoiceLanguage.YORUBA))
        assertEquals(Locale("es"), ttsLocale(VoiceLanguage.SPANISH))
    }

    @Test
    fun answersAndReadBackAreSpoken() {
        val vm = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt"
        )
        // Answers are spoken in the language of the turn.
        assertTrue(vm.contains("voiceEngine.speakAndThen(answer, turnLang)"))
        assertTrue(vm.contains("voiceEngine.speak(answer, turnLang)"))
        // The order read-back is spoken aloud before the mic opens.
        assertTrue(vm.contains("Say yes to place it, or no to cancel."))
        // Hanging up stops any spoken answer at once.
        assertTrue(vm.contains("voiceEngine.stopSpeaking()"))
    }

    // ---- Speed: the cloud capture ends sooner ----

    @Test
    fun captureEndsAfter800msOfSilence() {
        assertTrue(VoiceEngine.shouldStopCapture(true, 800L))
        assertFalse(VoiceEngine.shouldStopCapture(true, 799L))
    }
}
