package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.data.platformAnswer
import xyz.prognoslabs.app.ui.VoiceEngine

/**
 * v1.1.22: Flint asked the app "can you take a trade for me" and
 * got the out-of-scope refusal. A bare trade request is a trade to
 * start, not a question to refuse: the voice now answers with the
 * guided trade prompt and keeps listening. The cloud capture ends
 * after 800 ms of silence instead of 1,500 ms, so the voice answers
 * sooner.
 *
 * v1.1.24: the spoken voice is gone on Flint's order. Answers and
 * the order read-back are shown on screen instead of spoken aloud,
 * and the mic reopens right after the text appears. The two old
 * spoken-voice tests below were updated for that, not deleted:
 * they now assert the text-and-mic behaviour that replaced speech.
 *
 * These tests run on the JVM. There is no microphone and no phone
 * here, so the voice is proven through pure helpers and source
 * wiring, and the real proof is still Flint's phone.
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
    fun versionIs1123() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 37"))
        assertTrue(gradle.contains("versionName = \"1.1.27\""))
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

    // ---- v1.1.24: no spoken voice. Answers and the read-back are
    // shown on screen, and the mic reopens right after. ----

    @Test
    fun answersAreShownAsTextNotSpoken() {
        val vm = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt"
        )
        // A conversational answer is put on the sheet as text.
        assertTrue(vm.contains("_voiceSheet.value = VoiceSheet.Conversation(answer)"))
        // The mic reopens right after the text appears.
        assertTrue(vm.contains("restartListening()"))
        // Nothing is spoken anywhere in the view model.
        assertFalse(vm.contains("voiceEngine.speak"))
        assertFalse(vm.contains("stopSpeaking"))
    }

    @Test
    fun orderReadBackIsShownNotSpoken() {
        val vm = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt"
        )
        // The order read-back is a confirm card on screen.
        assertTrue(vm.contains("_voiceSheet.value = VoiceSheet.Confirm(order, ready)"))
        // The mic reopens so the user answers it out loud.
        assertTrue(vm.contains("restartListening()"))
        // The old spoken read-back is gone.
        assertFalse(vm.contains("Say yes to place it, or no to cancel."))
        assertFalse(vm.contains("voiceEngine.speak"))
    }

    // ---- Speed: the cloud capture ends sooner ----

    @Test
    fun captureEndsAfter800msOfSilence() {
        assertTrue(VoiceEngine.shouldStopCapture(true, 800L))
        assertFalse(VoiceEngine.shouldStopCapture(true, 799L))
    }
}
