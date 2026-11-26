package xyz.prognoslabs.app

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.16: Whisper only, model bundled, new call screen.
 *
 * The phone's built-in recognizer is gone: every listen goes
 * through the speech library, and its model ships inside the APK,
 * so there is no download and no wait. The voice screen is a
 * full-screen call view: logo at the top, one big lime mic with
 * waveform bars and "Listening...", your words as a bubble on the
 * right as you speak, the app's read-back as a bubble on the left,
 * "Say yes or no" by voice, and one red hang-up button. No other
 * buttons, no hints, no loading state. The header mic from v1.1.15
 * stays as the entry point.
 *
 * Pure JVM tests, no Android. Live recognition can only be proven
 * on a real phone.
 */
class V1116Test {

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

    /** Text of one function inside a class, from its declaration to the next sibling. */
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

    /** Text of one top-level function, to the next top-level item. */
    private fun topLevelBody(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        assertTrue("Cannot find $declaration", start >= 0)
        val after = start + declaration.length
        val end = listOf(
            source.indexOf("\nfun ", after),
            source.indexOf("\nprivate fun ", after),
            source.indexOf("\n@Composable", after),
            source.indexOf("\n/**", after),
            source.indexOf("\nclass ", after),
            source.indexOf("\nobject ", after)
        ).filter { it >= 0 }.minOrNull() ?: source.length
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

    private fun mainSourceFiles(): List<File> =
        File(mainRoot(), "java").walkTopDown().filter { it.isFile }.toList() +
            File(mainRoot(), "cpp").walkTopDown().filter { it.isFile }.toList()

    @Test
    fun versionIs1123() {
        assertEquals(34, BuildConfig.VERSION_CODE)
        assertEquals("1.1.24", BuildConfig.VERSION_NAME)
    }

    // ---- 1. Whisper only: the built-in recognizer is gone ----

    @Test
    fun noBuiltInRecognizerAnywhere() {
        val banned = listOf(
            "SpeechRecognizer", "RecognizerIntent", "createSpeechRecognizer",
            "isRecognitionAvailable", "ERROR_NO_MATCH", "ERROR_SPEECH_TIMEOUT",
            // The text-to-speech import (android.speech.tts) is the
            // read-back voice, not a recognizer, and stays.
            "android.speech.SpeechRecognizer", "android.speech.RecognizerIntent"
        )
        for (file in mainSourceFiles()) {
            val text = file.readText()
            for (word in banned) {
                assertFalse("${file.name} must not mention $word", text.contains(word))
            }
        }
    }

    @Test
    fun engineListensThroughWhisperOnly() {
        val engine = readSource("java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        val body = funBody(engine, "fun startWhisperListening(")
        assertTrue("Listening must start the Whisper recognizer", body.contains("rec.start"))
        assertFalse("Listening must not use the built-in recognizer", body.contains("SpeechRecognizer"))
        assertFalse("Listening must not build a recognizer intent", body.contains("RecognizerIntent"))
        // The engine still exposes an availability check, but it
        // answers from the bundled model, not the phone.
        val available = funBody(engine, "fun isAvailable(")
        assertTrue("Availability must come from the model", available.contains("whisperModel"))
        assertFalse("Availability must not ask the phone", available.contains("SpeechRecognizer"))
    }

    @Test
    fun engineReportsUnavailableWithoutNativeLib() {
        // The mic shows a plain "voice unavailable" note when the
        // library cannot run. There is no fallback recognizer.
        val engine = readSource("java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        val body = funBody(engine, "fun startListening(")
        assertTrue("Listening must check the native library", body.contains("WhisperNative.isAvailable"))
        assertTrue("A missing library must report unavailable", body.contains("Event.Unavailable"))
    }

    @Test
    fun viewModelNeverTouchesTheBuiltInRecognizer() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        assertFalse("The view model must not mention the built-in recognizer", vm.contains("SpeechRecognizer"))
        assertFalse("The view model must not mention a recognizer intent", vm.contains("RecognizerIntent"))
    }

    // ---- 2. The model is bundled, not downloaded ----

    @Test
    fun bundledModelPresentWithKnownSizeAndHash() {
        val model = File(mainRoot(), "assets/ggml-tiny-q8_0.bin")
        assertTrue("The bundled model must be in src/main/assets", model.isFile)
        assertEquals(
            "The bundled model must be the known tiny model",
            43_537_433L, model.length()
        )
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                digest.update(buffer, 0, read)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals(
            "The bundled model hash must match the known model",
            "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca",
            sha
        )
    }

    @Test
    fun modelManagerReadsTheBundledAsset() {
        // The engine builds the model manager with the bundled
        // asset stream; the recognizer itself only takes the
        // manager, it never touches assets.
        val engine = readSource("java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        assertTrue(
            "The engine must open the model from the bundled assets",
            engine.contains("assets.open(WhisperModelManager.MODEL_FILE_NAME)")
        )
        val manager = readSource("java/xyz/prognoslabs/app/voice/WhisperModel.kt")
        assertTrue("The manager must take a bundled copy", manager.contains("bundledModel"))
        assertTrue("The manager must verify by size", manager.contains("expectedSizeBytes"))
        assertTrue("The manager must verify by hash", manager.contains("expectedSha256"))
    }

    @Test
    fun noModelDownloadCode() {
        val banned = listOf(
            "huggingface", "MODEL_URL", "OkHttp", "Downloading", "Getting the voice model"
        )
        for (relative in listOf(
            "java/xyz/prognoslabs/app/voice/WhisperModel.kt",
            "java/xyz/prognoslabs/app/voice/WhisperRecognizer.kt",
            "java/xyz/prognoslabs/app/ui/VoiceEngine.kt"
        )) {
            val text = readSource(relative)
            for (word in banned) {
                assertFalse("$relative must not contain $word", text.contains(word))
            }
        }
    }

    // ---- 3. The call screen, as the approved mockups draw it ----

    @Test
    fun callScreenHasOneHangUpAndNoOtherButtons() {
        val voice = readSource("java/xyz/prognoslabs/app/ui/VoiceScreens.kt")
        assertEquals(
            "There must be exactly one hang-up button on the call screen",
            1, countOccurrences(voice, "\"End call\"")
        )
        assertTrue("The hang-up must end the call", voice.contains("hangUpVoice"))
        val host = topLevelBody(voice, "fun VoiceSheetHost(")
        assertTrue("The host must show the hang-up", host.contains("VoiceCallHangUp"))
        assertFalse("The host must not have any other button", host.contains("Button("))
        assertFalse("The host must not have an outlined button", host.contains("OutlinedButton"))
        val confirm = topLevelBody(voice, "fun VoiceConfirmContent(")
        assertFalse("The confirm view must not have a button", confirm.contains("Button("))
        val executed = topLevelBody(voice, "fun VoiceExecutedContent(")
        assertFalse("The executed view must not have a Done button", executed.contains("Button("))
        val message = topLevelBody(voice, "fun VoiceMessageContent(")
        assertFalse("The message view must not have a Done button", message.contains("Button("))
    }

    @Test
    fun callScreenShowsBubblesAndVoiceYesNo() {
        val voice = readSource("java/xyz/prognoslabs/app/ui/VoiceScreens.kt")
        assertTrue("The mic must show Listening...", voice.contains("\"Listening...\""))
        assertTrue("Your words must be a right bubble", voice.contains("fun UserBubble("))
        assertTrue("The app reply must be a left bubble", voice.contains("fun AppBubble("))
        assertTrue("Yes and no are spoken, not tapped", voice.contains("Say yes or no"))
        assertTrue("The read-back must invite a spoken yes", voice.contains("Say yes to trade."))
        assertTrue("The mic must have waveform bars", voice.contains("fun VoiceWaveform("))
    }

    @Test
    fun callScreenHasNoHintsNoLoadingNoFloatingButton() {
        val voice = readSource("java/xyz/prognoslabs/app/ui/VoiceScreens.kt")
        assertFalse("No example order hint", voice.contains("Say an order, for example"))
        assertFalse("No stale price hint", voice.contains("Buy 10 Bitcoin Up"))
        assertFalse("No model loading text", voice.contains("Getting the voice model"))
        assertFalse("No floating mic button", voice.contains("floatingActionButton"))
        assertFalse("No floating mic button", voice.contains("VoiceMicFab"))
        // The one exception: the order-placing state shows a
        // spinner while the order runs, inside the call view.
        // That spinner lives in VoiceExecutingContent, not the
        // call screen itself.
        val executing = topLevelBody(voice, "fun VoiceExecutingContent(")
        assertTrue("Placing still shows progress", executing.contains("CircularProgressIndicator"))
    }

    @Test
    fun headerMicStaysAsTheEntryPoint() {
        // v1.1.15: one 40dp mic icon in the header of all 8 screens.
        // v1.1.16 keeps it: it opens the call view.
        val screens = readSource("java/xyz/prognoslabs/app/ui/Screens.kt")
        assertTrue("The header mic must stay", screens.contains("VoiceMicHeaderButton"))
        assertTrue("The header mic must open the call view", screens.contains("onMicTapped"))
    }

    // ---- 4. Standing rules ----

    @Test
    fun voiceLockStaysASimilarityMatch() {
        for (file in mainSourceFiles()) {
            val text = file.readText()
            assertFalse(
                "${file.name} must never claim cryptographic gating",
                text.lowercase().contains("cryptograph")
            )
        }
    }

    @Test
    fun noAiToolNamesInVoiceCode() {
        for (file in mainSourceFiles()) {
            val text = file.readText()
            for (banned in listOf("Muse", "Claude", "ChatGPT", "OpenAI")) {
                assertFalse("${file.name} must not mention $banned", text.contains(banned))
            }
        }
    }

    @Test
    fun tradingNeedsLinkedWallet() {
        val vm = readSource("java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        assertTrue("Trading must ask for a linked wallet", vm.contains("Please link your wallet to Prognos Labs first."))
    }
}
