package xyz.prognoslabs.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.1.7: the microphone fix and the profile/settings cleanup.
 *
 * The mic bug: v1.1.6 ran the speech recognizer and an AudioRecord
 * capture at the same time. On Flint's Samsung the capture took the
 * microphone first, so the recognizer heard silence for the whole
 * timeout and came back with no match, twice, on two phrases.
 * v1.1.7 splits them: words first (recognizer alone), then a
 * separate voice sample pass (microphone alone). A trade still
 * needs the words recognized AND the voice check passed.
 *
 * The cleanup: no version numbers anywhere a user can see them, no
 * status rows, no explanatory paragraphs on settings screens, and
 * "Trading opens soon" in exactly one place: the trade button.
 * Pure JVM tests, no Android.
 */
class V117Test {

    // ---- Helpers ----

    private fun mainRoot(): File {
        val cwd = System.getProperty("user.dir") ?: "."
        return listOf(
            File("src/main"),
            File("app/src/main"),
            File("$cwd/src/main"),
            File("$cwd/app/src/main")
        ).firstOrNull { it.isDirectory }
            ?: throw AssertionError("Cannot find src/main from $cwd")
    }

    private fun readSource(relative: String): String {
        val f = File(mainRoot(), relative)
        assertTrue("Missing source file $relative", f.isFile)
        return f.readText()
    }

    /** Text of one function, from its declaration to the next sibling. */
    private fun funBody(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        assertTrue("Cannot find $declaration", start >= 0)
        val after = start + declaration.length
        val next = source.indexOf("\n    fun ", after)
        val nextSuspend = source.indexOf("\n    suspend fun ", after)
        val nextSection = source.indexOf("\n    // ----", after)
        val end = listOf(next, nextSuspend, nextSection).filter { it >= 0 }.minOrNull() ?: source.length
        return source.substring(start, end)
    }

    /** Like funBody, but also stops at the next private function, so a
     * body never spans into the function after it. */
    private fun privateFunBody(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        assertTrue("Cannot find $declaration", start >= 0)
        val after = start + declaration.length
        val next = source.indexOf("\n    fun ", after)
        val nextPrivate = source.indexOf("\n    private fun ", after)
        val nextSuspend = source.indexOf("\n    suspend fun ", after)
        val nextSection = source.indexOf("\n    // ----", after)
        val end = listOf(next, nextPrivate, nextSuspend, nextSection).filter { it >= 0 }.minOrNull() ?: source.length
        return source.substring(start, end)
    }

    private fun countOccurrences(source: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val at = source.indexOf(needle, from)
            if (at < 0) return count
            count++
            from = at + needle.length
        }
    }

    // ---- 1. The split: recognizer runs without capture ----

    @Test
    fun recognizerRunsWithoutCapture() {
        // v1.1.16: the words pass runs through the speech library
        // only. There is no built-in recognizer left to create.
        val engine = readSource("java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        val body = funBody(engine, "fun startWhisperListening(")
        assertTrue("The words pass must start the Whisper recognizer", body.contains("rec.start"))
        assertFalse("The words pass must not use the built-in recognizer", body.contains("SpeechRecognizer"))
        assertFalse("The words pass must not use a recognizer intent", body.contains("RecognizerIntent"))
        assertFalse("The words pass must not start a capture", body.contains("startCapture"))
        assertFalse("The words pass must not touch AudioRecord", body.contains("AudioRecord"))
        assertFalse("The words pass must not touch the capture flag", body.contains("capturing"))
    }

    @Test
    fun stopListeningStopsTheWordsPassCapture() {
        // v1.1.18: the words pass records through the capture when
        // cloud ASR is used, so hanging up must stop that capture
        // or the mic would keep recording. The enrollment capture is
        // a separate session, started only by startVoiceCapture.
        val engine = readSource("java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        val body = funBody(engine, "fun stopListening(")
        assertTrue("Stopping the words pass must stop its capture", body.contains("stopCaptureSignal"))
    }

    // ---- 2. The split: voice sample pass records without the recognizer ----

    @Test
    fun voiceprintPassRecordsWithoutTheRecognizer() {
        val engine = readSource("java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        val body = funBody(engine, "fun startVoiceCapture(")
        assertTrue("The sample pass must create an AudioRecord", body.contains("AudioRecord"))
        assertFalse("The sample pass must not use the recognizer", body.contains("SpeechRecognizer"))
        assertFalse("The sample pass must not start listening", body.contains("startListening"))
    }

    @Test
    fun voiceprintPassDeliversOnceToTheCurrentPassOnly() {
        val engine = readSource("java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        val body = funBody(engine, "private fun deliverCapture(")
        assertTrue(
            "A stale pass must never deliver",
            body.contains("if (captureListenerSession != session) null")
        )
        assertTrue(
            "A delivered pass must clear its listener",
            body.contains("captureListener = null")
        )
    }

    // ---- 3. A no-match still shows the retry message ----

    @Test
    fun noMatchStillShowsTheRetryMessage() {
        // v1.1.16: there is no built-in recognizer and no error code
        // any more. When the speech library hears nothing, it ends
        // the segment with the plain retry message, marked silent
        // so the first empty listen never shows it.
        val recognizer = readSource("java/xyz/prognoslabs/app/voice/WhisperRecognizer.kt")
        assertTrue(
            "No match must map to the retry message",
            recognizer.contains("I did not catch that. Say it again.")
        )
        assertTrue(
            "The retry message must be silent on the first listen",
            recognizer.contains("silent = true")
        )
    }

    // ---- 4. Orders still gate on the voice check ----

    @Test
    fun ordersGoThroughTheVoiceCheckPass() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        val body = privateFunBody(vm, "private fun processVoiceFinal(")
        assertTrue("A recognized order must start the voice check", body.contains("startVoiceCheck(heard, parsed.order)"))
        assertFalse("Words must not be checked against the voice in the same pass", body.contains("extractVoiceFeatures"))
        assertFalse("Words must not decide the voice check in the same pass", body.contains("voiceCheckDecision"))
        val checkBody = privateFunBody(vm, "private fun startVoiceCheck(")
        assertTrue("The check pass must show the check sheet", checkBody.contains("VoiceSheet.VoiceCheck"))
        assertTrue("The check pass must pick a fresh challenge phrase", checkBody.contains("pickChallengePhrase"))
        assertTrue("The check pass must speak the challenge", checkBody.contains("voiceEngine.speak"))
        assertTrue("The words pass must listen for the challenge", checkBody.contains("restartListening"))
        val challengeBody = privateFunBody(vm, "private fun processChallengeFinal(")
        assertTrue("The voice pass must record the voice alone", challengeBody.contains("startVoiceCapture"))
    }

    @Test
    fun voiceCheckResultStillGatesTheOrder() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        val body = privateFunBody(vm, "private fun handleVoiceCheckResult(")
        assertTrue("The check result must reach the order gate", body.contains("onVoiceOrder(heard, order, check)"))
        val gate = privateFunBody(vm, "private fun onVoiceOrder(")
        assertTrue("An unmatched voice must be refused", gate.contains("VoiceCheck.NOT_MATCHED"))
        assertTrue("An unavailable check must be refused", gate.contains("VoiceCheck.UNAVAILABLE"))
        assertTrue("Only a matched check reaches the confirm card", gate.contains("VoiceSheet.Confirm"))
    }

    @Test
    fun stoppingTheCheckPassStillChecksTheVoice() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        val body = funBody(vm, "fun stopVoiceListening(")
        assertTrue("Stopping must stop the recognizer", body.contains("voiceEngine.stopListening()"))
        assertTrue("Stopping must stop the voice sample pass", body.contains("voiceEngine.stopVoiceCapture()"))
    }

    @Test
    fun dismissingTheCheckPassDropsTheResult() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        val body = funBody(vm, "fun dismissVoiceSheet(")
        assertTrue("Dismissing the check pass must invalidate it", body.contains("VoiceSheet.VoiceCheck"))
        assertTrue("Dismissing must invalidate the pending result", body.contains("voiceSession++"))
        assertTrue("Dismissing must stop the sample pass", body.contains("voiceEngine.stopVoiceCapture()"))
    }

    // ---- 5. Enrollment records without the recognizer ----

    @Test
    fun enrollmentRecordsWithoutTheRecognizer() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        val body = funBody(vm, "private fun startEnrollListening(")
        assertTrue("Enrollment must record the voice alone", body.contains("startVoiceCapture"))
        assertFalse("Enrollment must not start the recognizer", body.contains("startListening"))
        val stop = funBody(vm, "fun stopEnrollmentListening(")
        assertTrue("Stopping enrollment must stop the recording", stop.contains("stopVoiceCapture()"))
        assertFalse("Stopping enrollment must not stop a recognizer", stop.contains("stopListening()"))
    }

    @Test
    fun enrollmentNoLongerGatesOnTheRecognizer() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        val body = funBody(vm, "fun enrollListen(")
        assertFalse("Enrollment must not need the recognizer", body.contains("isAvailable()"))
        assertTrue("Enrollment must still need the microphone permission", body.contains("hasMicPermission()"))
    }

    // ---- 6. The cleanup: "Trading opens soon" in one place only ----

    @Test
    fun closedNoticeAppearsOnlyOnTheTradeButton() {
        val uiFiles = listOf(
            "java/xyz/prognoslabs/app/ui/Screens.kt",
            "java/xyz/prognoslabs/app/ui/VoiceScreens.kt",
            "java/xyz/prognoslabs/app/ui/AppViewModel.kt",
            "java/xyz/prognoslabs/app/MainActivity.kt"
        )
        for (f in uiFiles) {
            val src = readSource(f)
            assertEquals(
                "$f must not repeat the closed notice",
                0, countOccurrences(src, "Trading opens soon")
            )
        }
        val screens = readSource("java/xyz/prognoslabs/app/ui/Screens.kt")
        assertEquals(
            "The trade button must be the one place that uses the closed notice",
            1, countOccurrences(screens, "AppConfig.TRADING_OPENS_SOON")
        )
        val config = readSource("java/xyz/prognoslabs/app/data/AppConfig.kt")
        assertEquals(
            "The closed notice must be defined once",
            1, countOccurrences(config, "Trading opens soon")
        )
    }

    @Test
    fun notReadyMessagesElsewhereArePlain() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        assertTrue("The trade message must ask for a linked wallet", vm.contains("Please link your wallet to Prognos Labs first."))
        assertTrue(
            "A failed wallet link must say the app could not be reached",
            vm.contains("Could not reach Prognos Labs. Please try again later.")
        )
        val screens = readSource("java/xyz/prognoslabs/app/ui/VoiceScreens.kt")
        assertTrue("The confirm card must keep a plain link-wallet line", screens.contains("Please link your wallet to Prognos Labs first."))
    }

    // ---- 7. The cleanup: no version numbers, no status rows, no essays ----

    @Test
    fun noVersionNumbersInUserFacingScreens() {
        val uiFiles = listOf(
            "java/xyz/prognoslabs/app/ui/Screens.kt",
            "java/xyz/prognoslabs/app/ui/VoiceScreens.kt",
            "java/xyz/prognoslabs/app/MainActivity.kt"
        )
        for (f in uiFiles) {
            val src = readSource(f)
            assertFalse("$f must not show a version number", src.contains("VERSION_NAME"))
            assertFalse("$f must not show a version number", src.contains("Version "))
        }
    }

    @Test
    fun settingsShowsActionsNotStatusRows() {
        val screens = readSource("java/xyz/prognoslabs/app/ui/Screens.kt")
        assertFalse("The voice status line must be gone", screens.contains("Voice trading: Set up"))
        assertFalse("The wallet status line must be gone", screens.contains("No wallet linked"))
        assertFalse("The VIP section must be gone", screens.contains("Your tier"))
        assertFalse("The VIP paragraph must be gone", screens.contains("no VIP programme yet"))
        assertFalse("The currency paragraph must be gone", screens.contains("Balances are shown in this currency"))
        assertFalse("The rates paragraph must be gone", screens.contains("Exchange rates are not available right now"))
        assertFalse("The trade-size paragraph must be gone", screens.contains("The amount a new trade starts with"))
        assertTrue("The voice buttons must stay", screens.contains("Set up your voice"))
        assertFalse("The voice history button is gone in v1.1.12", screens.contains("Voice history"))
        assertTrue("The feedback toggle must be in settings", screens.contains("Help improve voice"))
        assertTrue("The linked wallet must still show", screens.contains("Linked wallet"))
        assertTrue("The currency label must stay", screens.contains("CURRENCY(\"Currency\")"))
        assertTrue("The trade-size label must stay", screens.contains("Default trade size"))
    }

    @Test
    fun aboutCardKeepsOnlyTheName() {
        val screens = readSource("java/xyz/prognoslabs/app/ui/Screens.kt")
        assertTrue("The About card must keep the name", screens.contains("Text(\"Prognos Labs\""))
        assertFalse("The About card must not show a version", screens.contains("BuildConfig"))
    }

    @Test
    fun voiceScreensKeepShortLabelsOnly() {
        val voice = readSource("java/xyz/prognoslabs/app/ui/VoiceScreens.kt")
        val vocab = readSource("java/xyz/prognoslabs/app/data/VoiceLanguage.kt")
        assertFalse("The enroll-required essay must be gone", voice.contains("Before voice trading can be used"))
        assertFalse("The voice-stays-on-phone essay must be gone", voice.contains("Your voice profile stays on this phone"))
        assertFalse("The enrollment essay must be gone", voice.contains("The app learns how your voice sounds"))
        assertTrue("The enrollment screen must keep its one short line", voice.contains("Say each phrase out loud."))
        // v1.1.12: the voice history screen is gone, so its short
        // line and its empty state are gone with it, by design.
        // The challenge instructions now live in the vocab, in the
        // user's language, and stay short.
        assertTrue("The check pass must ask for the challenge phrase", vocab.contains("so the app can check it is you."))
        assertTrue("The voice pass must ask for the phrase once more", vocab.contains("once more so the app can check your voice."))
    }

    @Test
    fun deadTradingSoonStringsAreGone() {
        val strings = readSource("res/values/strings.xml")
        assertFalse("The dead trading_soon string must be gone", strings.contains("trading_soon"))
    }

    // ---- 8. No AI-tool names in the changed voice code ----

    @Test
    fun noAiToolNamesInVoiceCode() {
        val files = listOf(
            "java/xyz/prognoslabs/app/ui/VoiceEngine.kt",
            "java/xyz/prognoslabs/app/ui/VoiceScreens.kt",
            "java/xyz/prognoslabs/app/ui/AppViewModel.kt"
        )
        for (f in files) {
            val src = readSource(f)
            for (banned in listOf("Muse", "Claude", "ChatGPT", "OpenAI")) {
                assertFalse("$f must not mention $banned", src.contains(banned))
            }
        }
    }
}
