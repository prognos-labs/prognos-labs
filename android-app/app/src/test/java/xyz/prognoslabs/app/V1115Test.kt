package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.cleanRecognizedText
import xyz.prognoslabs.app.data.parseVoiceUtterance

/**
 * v1.1.15: the video-review fixes, from Flint's second screen
 * recording of the app. One test per fix, all pure and on the JVM:
 * the rendering itself is covered by the Robolectric voice UI tests
 * in the same module (the header mic on every screen, the
 * no-price row in the markets list).
 */
class V1115Test {

    @Test
    fun versionIs1123() {
        assertEquals(38, BuildConfig.VERSION_CODE)
        assertEquals("1.1.29", BuildConfig.VERSION_NAME)
    }

    // ---- Fix 1: the mic is a header button, never floating ----

    @Test
    fun noFloatingMicButtonInScaffold() {
        // The big lime button floated over every screen and covered
        // content: the voice toggle in Settings, the BTC row
        // percentage in Markets, the Transactions text in Wallet.
        // The scaffold carries no mic button any more.
        val main = sourceFile(
            "src/main/java/xyz/prognoslabs/app/MainActivity.kt"
        ).readText()
        assertFalse("Scaffold must not have a floatingActionButton", main.contains("floatingActionButton"))
        assertFalse("VoiceMicFab must be gone", main.contains("VoiceMicFab"))
    }

    @Test
    fun headerMicButtonOnEveryScreen() {
        // One small mic button in the header of every screen a user
        // browses: dashboard, markets, portfolio, wallet, settings,
        // notifications, market detail, change password. It lives in
        // the layout, so it can never overlap content.
        val screens = sourceFile(
            "src/main/java/xyz/prognoslabs/app/ui/Screens.kt"
        ).readText()
        val count = screens.split("VoiceMicHeaderButton(onClick = { vm.onMicTapped() })").size - 1
        assertEquals("Eight screens must carry the header mic button", 8, count)
    }

    @Test
    fun headerMicButtonIsSmallAndBordered() {
        // The old button was a 64dp lime circle. The header button
        // is a 40dp bordered circle, the same size and shape as the
        // bell, theme and avatar buttons next to it.
        val voice = sourceFile(
            "src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt"
        ).readText()
        assertTrue("VoiceMicHeaderButton must exist", voice.contains("fun VoiceMicHeaderButton"))
        assertFalse("VoiceMicFab must be gone", voice.contains("fun VoiceMicFab"))
        val start = voice.indexOf("fun VoiceMicHeaderButton")
        val body = voice.substring(start, (start + 600).coerceAtMost(voice.length))
        assertTrue("Header mic must be 40dp", body.contains("size(40.dp)"))
        assertTrue("Header mic must be a bordered circle", body.contains("border(1.dp"))
        assertTrue("Header mic keeps its label", body.contains("\"Voice trading\""))
        assertFalse("Header mic must not be the lime circle", body.contains("size(64.dp)"))
    }

    // ---- Fix 2: the greeting answers on the phone ----

    @Test
    fun punctuatedHelloGreets() {
        // The recognizer punctuates ("Hello."), and the old check
        // compared the raw string with "hello", so a plain hello
        // fell through to "say it again" instead of being answered.
        for (heard in listOf("Hello.", "Hello!", "hello?", "Hello")) {
            val parsed = parseVoiceUtterance(heard, emptyList())
            assertTrue("$heard must parse as a question, got $parsed", parsed is VoiceParseResult.Question)
            val question = (parsed as VoiceParseResult.Question).question
            assertTrue("$heard must greet, got $question", question is VoiceQuestion.GreetingHello)
        }
    }

    @Test
    fun helloWithThankYouHallucinationGreets() {
        // The library appends "Thank you." to a short greeting.
        // The greeting is the first word, so it still greets.
        val parsed = parseVoiceUtterance("Hello. Thank you.", emptyList())
        assertTrue(parsed is VoiceParseResult.Question)
        assertTrue(
            (parsed as VoiceParseResult.Question).question is VoiceQuestion.GreetingHello
        )
    }

    // ---- Fix 3: the scope refusal only fires off-topic ----

    @Test
    fun checkInIsAnsweredNotRefused() {
        // "Can you hear me?" got the scope refusal in the video.
        // It is the user checking the mic is live, not an
        // off-topic question: it gets a plain yes back.
        for (heard in listOf("Can you hear me?", "can you hear me", "are you listening?", "are you there?")) {
            val parsed = parseVoiceUtterance(heard, emptyList())
            assertTrue("$heard must parse as a question, got $parsed", parsed is VoiceParseResult.Question)
            val question = (parsed as VoiceParseResult.Question).question
            assertTrue("$heard must be a check-in, got $question", question is VoiceQuestion.CheckIn)
            assertTrue("$heard must keep the session open", question.isConversational)
        }
    }

    @Test
    fun whatsPrognosIsAnswered() {
        // "What's Prognos Labs?" was refused: only "what is"
        // matched. The question IS about Prognos Labs, so it gets
        // the platform description, not a refusal. Same for the
        // misheard brand name and for "how does it work".
        for (heard in listOf(
            "What's Prognos Labs?",
            "What is progress labs?",
            "How does Prognos Labs work?",
            "what is Prognos Labs?"
        )) {
            val parsed = parseVoiceUtterance(heard, emptyList())
            assertTrue("$heard must parse as a question, got $parsed", parsed is VoiceParseResult.Question)
            val question = (parsed as VoiceParseResult.Question).question
            assertTrue("$heard must be knowledge, got $question", question is VoiceQuestion.Knowledge)
            val answer = (question as VoiceQuestion.Knowledge).answer
            assertTrue("Answer must name Prognos Labs", answer.contains("Prognos Labs"))
            assertTrue("Answer must say what it is", answer.contains("prediction market"))
        }
    }

    @Test
    fun genuinelyOffTopicStillRefused() {
        for (heard in listOf("what is the weather", "tell me a joke")) {
            val parsed = parseVoiceUtterance(heard, emptyList())
            assertTrue("$heard must be out of scope, got $parsed", parsed is VoiceParseResult.OutOfScope)
        }
    }

    @Test
    fun everyQuestionKeepsTheSessionOpen() {
        val questions: List<VoiceQuestion> = listOf(
            VoiceQuestion.Price("BTC"),
            VoiceQuestion.Balance,
            VoiceQuestion.HowToTrade,
            VoiceQuestion.TradingOpens,
            VoiceQuestion.MarketInfo("btc-90k"),
            VoiceQuestion.GreetingHello,
            VoiceQuestion.GreetingHowAreYou,
            VoiceQuestion.CheckIn,
            VoiceQuestion.MostLiquid,
            VoiceQuestion.Knowledge("Prognos Labs is a prediction market."),
            VoiceQuestion.News("BTC")
        )
        for (q in questions) {
            assertTrue("${q::class.simpleName} must keep the session open", q.isConversational)
        }
    }

    // ---- Fix 4: the spoken words show as text ----

    @Test
    fun finalWordsShowOnSheet() {
        // Partials only arrive while speaking, and a short sentence
        // ends before the first one, so the final result is what
        // puts the words on screen. The final handler must set the
        // listening text from what was heard.
        val root = findAppFile()
        val vm = File(root, "src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt").readText()
        val start = vm.indexOf("private fun processVoiceFinal")
        assertTrue("processVoiceFinal must exist", start >= 0)
        val body = vm.substring(start, (start + 3200).coerceAtMost(vm.length))
        assertTrue(
            "processVoiceFinal must show the heard words",
            body.contains("_voiceListeningText.value = heard")
        )
    }

    // ---- Fix 5: the hint example cannot go stale ----

    @Test
    fun hintExampleHasNoStalePrice() {
        // v1.1.16: the call screen has no example hints at all, so
        // no hint can go stale.
        val voice = sourceFile(
            "src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt"
        ).readText()
        assertFalse(
            "No example order hint may remain",
            voice.contains("Buy 10 Bitcoin Up")
        )
        assertFalse(
            "The stale 90k example must be gone",
            voice.contains("Buy 50 Yes on Bitcoin above 90k")
        )
        assertFalse(
            "No example hint text may remain",
            voice.contains("Say an order, for example")
        )
    }

    // ---- Fix 6: no stray "Thank you." ----

    @Test
    fun thankYouIsNotSpeech() {
        // The library hallucinates a bare "Thank you." at the end
        // of a quiet stretch. It is not speech: it is dropped,
        // and when it is all there is, nothing was said.
        assertNull(cleanRecognizedText("Thank you."))
        assertNull(cleanRecognizedText("thank you very much"))
        assertNull(cleanRecognizedText("Thanks!"))
        assertNull(cleanRecognizedText("Merci."))
        assertEquals("Hello.", cleanRecognizedText("Hello. Thank you."))
        assertEquals("buy 10 bitcoin up.", cleanRecognizedText("buy 10 bitcoin up. Thank you."))
    }

    @Test
    fun thankYouForSomethingRealStays() {
        // Only a bare thank-you is dropped. A real sentence that
        // starts with one is kept whole.
        assertEquals("Thank you for the help", cleanRecognizedText("Thank you for the help"))
        assertEquals("hello", cleanRecognizedText("hello"))
    }

    // ---- Fix 7: a market with no price yet says so ----

    @Test
    fun marketRowShowsNoPriceYetWhenNoPrice() {
        // The ETH 5-minute markets genuinely have no price yet:
        // no bid, no ask, no last trade. The row said "-" as if
        // it were broken. It now says so, in the row and in the
        // watchlist, instead of a bare dash.
        val design = sourceFile(
            "src/main/java/xyz/prognoslabs/app/ui/Design.kt"
        ).readText()
        assertTrue("MarketPriceLabel must exist", design.contains("fun MarketPriceLabel"))
        assertTrue("No-price text must exist", design.contains("\"No price yet\""))
        assertTrue("MarketRow must use it", design.contains("MarketPriceLabel(market.p0, extraBold = true)"))
        assertTrue("WatchlistRow must use it", design.contains("MarketPriceLabel(market.p0, extraBold = false)"))
    }

    // ---- Standing rules ----

    @Test
    fun noAiToolNamesInEditedCode() {
        // The standing rule: no AI-tool names in shipped code,
        // comments, READMEs or commit messages. Every file this
        // build touched is scanned here.
        val banned = listOf("Muse", "Claude", "ChatGPT", "OpenAI")
        val root = findAppFile()
        val files = listOf(
            "src/main/java/xyz/prognoslabs/app/data/VoiceText.kt",
            "src/main/java/xyz/prognoslabs/app/data/VoiceOrder.kt",
            "src/main/java/xyz/prognoslabs/app/data/VoiceLanguage.kt",
            "src/main/java/xyz/prognoslabs/app/data/PrognosKnowledge.kt",
            "src/main/java/xyz/prognoslabs/app/data/Analytics.kt",
            "src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt",
            "src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt",
            "src/main/java/xyz/prognoslabs/app/ui/Screens.kt",
            "src/main/java/xyz/prognoslabs/app/ui/Design.kt",
            "src/main/java/xyz/prognoslabs/app/MainActivity.kt",
            "build.gradle.kts"
        )
        for (path in files) {
            val file = File(root, path)
            assertTrue("missing: ${file.path}", file.exists())
            val text = file.readText()
            for (word in banned) {
                assertFalse("${file.name} contains $word", text.contains(word))
            }
        }
    }

    private fun sourceFile(relative: String): File {
        val cwd = System.getProperty("user.dir") ?: "."
        return listOf(
            File(relative),
            File("$cwd/$relative"),
            File("app/$relative"),
            File("$cwd/app/$relative")
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find $relative from $cwd")
    }

    private fun findAppFile(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "app")
            if (File(candidate, "build.gradle.kts").exists()) return candidate
            dir = dir.parentFile
        }
        val direct = File("build.gradle.kts")
        if (direct.exists()) return File(".")
        throw AssertionError("app module not found from ${System.getProperty("user.dir")}")
    }
}
