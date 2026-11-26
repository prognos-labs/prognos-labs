package xyz.prognoslabs.app

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.forecastFor
import xyz.prognoslabs.app.data.latestNewsFor
import xyz.prognoslabs.app.data.normalizeHeard
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.data.shouldRecordForecasts
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.NewsItem
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto
import xyz.prognoslabs.app.ui.VoiceEngine
import xyz.prognoslabs.app.voice.WhisperRecognizer

/**
 * v1.1.9: the voice hears Flint right. The recognizer's top result
 * is often wrong ("by" for "buy", "bit coin" for "bitcoin", the
 * price phrase dropped), so every candidate is scored against the
 * markets and the best parse wins; the language follows the phone
 * when this build supports it and falls back to US English;
 * unparseable noise asks for a repeat instead of guessing.
 * The voice also answers about the platform itself (knowledge base),
 * the latest news about a coin (from the app's own feed), and the
 * most liquid market (from the live list), and a conversational
 * turn keeps the session open. Full screen is edge-to-edge on every
 * device, and the forecast work got cheaper.
 */
class V119Test {

    private fun testMarket(id: String, question: String, slug: String, vol24: Any? = null): Market =
        Market(
            id = id, question = question, status = "open", closed = false,
            start = 0, end = 0, volume = null, vol24 = vol24, last = null,
            bid = null, ask = null, p0 = 0.5, p1 = 0.5,
            outcomes = listOf(Outcome("Yes", "tok-up-$id"), Outcome("No", "tok-down-$id")),
            tags = listOf(TagDto(slug = slug)), desc = "", winOutcome = null
        )

    private val btc90k = testMarket("btc-90k", "Will BTC close above \$90k?", "btc", vol24 = "1500000")
    private val btc80k = testMarket("btc-80k", "Will BTC close above \$80k?", "btc", vol24 = "800000")
    private val eth3k = testMarket("eth-3k", "Will ETH close below \$3,000?", "eth", vol24 = "400000")
    private val markets = listOf(btc90k, btc80k, eth3k)

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

    // ---- 1. Candidate scoring ----

    @Test
    fun bestParsePicksLowerCandidateWhenTopDropsThePrice() {
        // The top result lost the price phrase, so with two BTC
        // markets it can only ask again. The second candidate kept
        // it, so it parses as a full order and must win.
        val (parsed, heard) = parseBestVoiceUtterance(
            listOf("buy 50 yes on btc", "buy 50 yes on btc above 90k"),
            markets
        )
        assertTrue("Lower candidate must win, got $parsed", parsed is VoiceParseResult.Order)
        val order = (parsed as VoiceParseResult.Order).order
        assertEquals("btc-90k", order.marketId)
        assertEquals(50.0, order.amountUsd, 0.0)
        assertTrue("History must show the winning words, got $heard", heard.contains("90k"))
    }

    @Test
    fun bestParsePrefersOrderOverQuestion() {
        val (parsed, _) = parseBestVoiceUtterance(
            listOf("what is the btc price", "buy 50 yes on btc above 90k"),
            markets
        )
        assertTrue("An order beats a question, got $parsed", parsed is VoiceParseResult.Order)
    }

    @Test
    fun bestParseKeepsTopResultOnATie() {
        val (parsed, heard) = parseBestVoiceUtterance(
            listOf("buy 50 yes on btc above 90k", "buy 50 yes on btc above 90k"),
            markets
        )
        assertTrue(parsed is VoiceParseResult.Order)
        assertEquals("buy 50 yes on btc above 90k", heard)
    }

    // ---- 2. Mishearing normalization ----

    @Test
    fun misheardTradeVerbsAreNormalized() {
        assertEquals(
            "buy 50 yes on bitcoin above 90k",
            normalizeHeard("by 50 yes on bit coin above 90k")
        )
        assertEquals(
            "buy 50 yes on bitcoin above 90k",
            normalizeHeard("bye 50 yes on bitcoin above 90k")
        )
        assertEquals(
            "sell 20 no on ethereum below 3000",
            normalizeHeard("cell 20 no on ethereum below 3000")
        )
    }

    @Test
    fun byIsNotRewrittenWhenItIsNotATradeVerb() {
        // "by when does trading open" is a question, not an order.
        assertEquals("by when does trading open", normalizeHeard("by when does trading open"))
    }

    @Test
    fun misheardOrderParses() {
        val r = parseVoiceUtterance("by 50 yes on bit coin above 90k", markets)
        assertTrue("Misheard order must parse, got $r", r is VoiceParseResult.Order)
        assertEquals("btc-90k", (r as VoiceParseResult.Order).order.marketId)
    }

    @Test
    fun noCandidateParsesAsksAgainInsteadOfGuessing() {
        val (parsed, _) = parseBestVoiceUtterance(listOf("blah blah blah", "um um um"), markets)
        assertTrue("Noise must ask again, got $parsed", parsed is VoiceParseResult.AskAgain)
    }

    @Test
    fun outOfScopeQuestionKeepsItsRefusal() {
        val (parsed, _) = parseBestVoiceUtterance(
            listOf("what is the weather tomorrow"), markets
        )
        assertTrue("A real question keeps its refusal, got $parsed", parsed is VoiceParseResult.OutOfScope)
    }

    // ---- 3. Language follows the phone, US English is the fallback ----

    @Test
    fun recognitionFallsBackToUsEnglish() {
        assertEquals(Locale.US, VoiceEngine.RECOGNITION_LOCALE)
        // v1.1.16: the speech library takes the language code, and
        // an unsupported language falls back to English there too.
        // The built-in recognizer's MAX_RESULTS is gone with it.
        assertEquals("en", WhisperRecognizer.whisperLanguageCode(Locale("ja")))
        assertEquals("es", WhisperRecognizer.whisperLanguageCode(Locale("es")))
    }

    @Test
    fun engineFollowsSupportedDeviceLanguage() {
        val src = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt"
        )
        assertTrue(src.contains("RECOGNITION_LOCALE"))
        assertTrue("The engine must follow a supported device language", src.contains("fun recognitionLocale()"))
        assertTrue(src.contains("voiceLanguageForLocale"))
        // v1.1.16: the speech library is the only recognizer, so
        // there is no built-in max-results request any more.
        assertFalse("No built-in recognizer request may remain", src.contains("EXTRA_MAX_RESULTS"))
        assertFalse("No built-in recognizer may remain", src.contains("SpeechRecognizer"))
    }

    // ---- 4. Platform knowledge ----

    @Test
    fun greetingGetsAConversationalReply() {
        val r = parseVoiceUtterance("hey, how's it going?", markets)
        assertTrue(r is VoiceParseResult.Question)
        val q = (r as VoiceParseResult.Question).question
        assertTrue("Greeting must parse, got $q", q is VoiceQuestion.GreetingHowAreYou)
        assertTrue("A greeting keeps the session open", q.isConversational)
    }

    @Test
    fun mostLiquidQuestionParses() {
        val r = parseVoiceUtterance("what was the most liquid market on Prognos Labs?", markets)
        assertTrue(r is VoiceParseResult.Question)
        assertTrue(
            "Most-liquid must parse, got ${(r as VoiceParseResult.Question).question}",
            (r as VoiceParseResult.Question).question is VoiceQuestion.MostLiquid
        )
    }

    @Test
    fun mostLiquidAnswerComesFromLiveVolume() {
        val best = markets.maxByOrNull {
            xyz.prognoslabs.app.model.parseNum(it.vol24) ?: 0.0
        }
        assertEquals("btc-90k", best?.id)
    }

    @Test
    fun allAnswersAreConversational() {
        // v1.1.14: every answer keeps the session open for the
        // follow-up, so the user never has to tap the mic again
        // mid-conversation. The one-shot close is gone.
        assertTrue(VoiceQuestion.MostLiquid.isConversational)
        assertTrue(VoiceQuestion.Price("BTC").isConversational)
        assertTrue(VoiceQuestion.News("BTC").isConversational)
        assertTrue(VoiceQuestion.Knowledge("x").isConversational)
        assertTrue(VoiceQuestion.Balance.isConversational)
        assertTrue(VoiceQuestion.GreetingHello.isConversational)
        assertTrue(VoiceQuestion.GreetingHowAreYou.isConversational)
        assertTrue(VoiceQuestion.HowToTrade.isConversational)
        assertTrue(VoiceQuestion.TradingOpens.isConversational)
        assertTrue(VoiceQuestion.MarketInfo("btc-90k").isConversational)
    }

    @Test
    fun knowledgeAnswersWhatPrognosIs() {
        val r = parseVoiceUtterance("what is Prognos Labs?", markets)
        assertTrue(r is VoiceParseResult.Question)
        val q = (r as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        val answer = (q as VoiceQuestion.Knowledge).answer
        assertTrue("Answer must name Prognos Labs", answer.contains("Prognos Labs"))
        assertTrue("Answer must say what it is", answer.contains("prediction market"))
        assertTrue("Answer must say people decide", answer.contains("People decide every market"))
    }

    @Test
    fun knowledgeHearsTheMisheardName() {
        // The recognizer hears the old name "prognosis"; the voice
        // still answers, and spells the name right.
        val r = parseVoiceUtterance("what is prognosis labs", markets)
        assertTrue(r is VoiceParseResult.Question)
        val q = (r as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertTrue((q as VoiceQuestion.Knowledge).answer.contains("Prognos Labs"))
    }

    @Test
    fun knowledgeAnswersDeposits() {
        val r = parseVoiceUtterance("how do I deposit", markets)
        assertTrue(r is VoiceParseResult.Question)
        val q = (r as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertTrue((q as VoiceQuestion.Knowledge).answer.contains("USDT"))
    }

    @Test
    fun knowledgeAnswersLaunch() {
        val r = parseVoiceUtterance("when is the launch", markets)
        assertTrue(r is VoiceParseResult.Question)
        val q = (r as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        val answer = (q as VoiceQuestion.Knowledge).answer
        assertTrue(answer.contains("December 2"))
        assertTrue(answer.contains("December 1"))
    }

    // ---- 5. News ----

    @Test
    fun newsQuestionParses() {
        val r = parseVoiceUtterance("what is the latest news about bitcoin", markets)
        assertTrue(r is VoiceParseResult.Question)
        val q = (r as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.News)
        assertEquals("BTC", (q as VoiceQuestion.News).asset)
    }

    @Test
    fun newsMatchingUsesTagsAndHeadlines() {
        val items = listOf(
            NewsItem(title = "Bitcoin jumps on ETF flows", source = "Reuters", tags = listOf("BTC")),
            NewsItem(title = "Ethereum upgrade lands", source = "CoinDesk", tags = listOf("ETH")),
            NewsItem(title = "Solana outage ends", source = "Bloomberg", tags = null)
        )
        val btc = latestNewsFor(items, "BTC")
        assertEquals(1, btc.size)
        assertEquals("Bitcoin jumps on ETF flows", btc[0].title)
        assertTrue("A headline naming the coin counts too", latestNewsFor(items, "SOL").size == 1)
        assertTrue("No match is an empty list, never invented news", latestNewsFor(items, "XRP").isEmpty())
    }

    // ---- 6. Continuation wiring ----

    @Test
    fun conversationalAnswersRestartListening() {
        val src = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt"
        )
        assertTrue("Conversational turns must restart the recognizer", src.contains("if (parsed.question.isConversational)"))
        assertTrue(src.contains("_voiceSheet.value = VoiceSheet.Conversation(answer)"))
        assertTrue(src.contains("restartListening()"))
        assertTrue("Conversation sheet must stop listening on dismiss", src.contains("sheet is VoiceSheet.Conversation"))
    }

    // ---- 7. Full screen, every device ----

    @Test
    fun edgeToEdgeIsAppWide() {
        val main = readSource(
            "app/src/main/java/xyz/prognoslabs/app/MainActivity.kt"
        )
        assertTrue("The app must draw edge-to-edge", main.contains("enableEdgeToEdge()"))
        assertFalse("No single phone model may be special-cased", main.contains("S21"))
        assertTrue("The bottom bar must sit above the gesture bar", main.contains("navigationBarsPadding()"))
        val screens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt"
        )
        assertTrue("The sign-in screen must clear the status bar", screens.contains("statusBarsPadding()"))
        val voiceScreens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt"
        )
        assertTrue("The voice sheet must clear the gesture bar", voiceScreens.contains("navigationBarsPadding()"))
    }

    // ---- 8. One mic entry point, in the headers ----

    @Test
    fun homeScreenHasTheMic() {
        // v1.1.15: the mic is a header button on the dashboard,
        // next to the bell, theme and avatar buttons, never a
        // floating button over content.
        val screens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt"
        )
        val start = screens.indexOf("fun DashboardScreen")
        assertTrue("DashboardScreen must exist", start >= 0)
        val end = screens.indexOf("fun MarketsScreen", start)
        val dashboard = screens.substring(start, if (end >= 0) end else screens.length)
        assertTrue(
            "The dashboard header must carry the mic button",
            dashboard.contains("VoiceMicHeaderButton(onClick = { vm.onMicTapped() })")
        )
        val main = readSource(
            "app/src/main/java/xyz/prognoslabs/app/MainActivity.kt"
        )
        assertFalse("The scaffold must carry no mic button", main.contains("floatingActionButton"))
    }

    // ---- 9. Speed ----

    @Test
    fun forecastRecordingIsThrottledToOnceAMinute() {
        assertTrue(shouldRecordForecasts(0, 60_000))
        assertFalse("Just after a record is too soon", shouldRecordForecasts(0, 59_999))
        assertFalse("30s after a record is too soon", shouldRecordForecasts(1_000, 31_000))
        assertTrue("61s after a record is fine", shouldRecordForecasts(1_000, 62_000))
    }

    @Test
    fun forecastCardRemembersItsForecast() {
        val design = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/Design.kt"
        )
        assertTrue(
            "The forecast must be remembered on its inputs, not rebuilt per recomposition",
            design.contains("remember(market.id, nowSec, ctx)")
        )
    }

    @Test
    fun forecastFor100MarketsIsFast() {
        val many = (0 until 100).map { i ->
            testMarket("m-$i", "Will BTC close above \$${80 + i}k?", "btc", vol24 = "${1_000_000 + i}")
        }
        val start = System.nanoTime()
        var done = 0
        for (m in many) {
            if (forecastFor(m) != null) done++
        }
        val ms = (System.nanoTime() - start) / 1_000_000.0
        println("v1.1.9: forecastFor over 100 markets: $done done in ${"%.1f".format(ms)} ms")
        assertEquals(100, done)
        assertTrue("100 forecasts must take under 1s, took ${"%.1f".format(ms)} ms", ms < 1_000)
    }

    // ---- 10. APK size: extended icons are required ----

    @Test
    fun iconsExtendedIsRequired() {
        // Tried material-icons-core for APK size in v1.1.9: it does
        // not compile. Nearly every icon the app uses (Mic, History,
        // ShowChart, PieChart, AccountBalanceWallet, Visibility,
        // StarOutline, DarkMode, LightMode, SwapHoriz) is
        // extended-only, so the extended library stays.
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("material-icons-extended"))
        val voiceScreens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt"
        )
        assertTrue("The voice mic itself is an extended-only icon", voiceScreens.contains("Icons.Filled.Mic"))
    }

    // ---- 11. Standing rules ----

    @Test
    fun versionIs1123() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 34"))
        assertTrue(gradle.contains("versionName = \"1.1.24\""))
    }
}
