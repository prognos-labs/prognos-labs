package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.assetMention
import xyz.prognoslabs.app.data.levenshtein
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.data.parseBestVoiceUtteranceMulti
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto

/**
 * v1.1.28: Flint tested 1.1.27 on his phone (3:14 screen recording,
 * 26 Nov 15:10 WAT) and five things were still wrong.
 *
 * 1. His English was still heard as Portuguese: "Open my portfolio
 *    now" came back as "Ai, mas posso folio não" and was refused.
 *    When the detected language is not the one set in Settings, the
 *    app now runs the same audio once more forced to the Settings
 *    language, and the parser scores each transcript in its own
 *    language: the better parse wins.
 * 2. "Opened your portfolio" and "Opened the markets" were spoken
 *    claims only: the app navigated behind the call view, which
 *    never closed, so the user never saw the screen.
 * 3. The next thing he said after that (BTC at 50k) got no
 *    transcription and no reply at all: the navigation answer never
 *    listened again, so the session was dead.
 * 4. "Good morning. Tell me about Prognos Labs." was answered with
 *    a greeting: the greeting branch fired first and the question
 *    was thrown away. The capture also stopped after 800 ms of
 *    silence, mid-sentence.
 * 5. "Tell me about Ethereum" came back as "Ecterium" and was
 *    refused: no asset was named, and no branch answers "tell me
 *    about" a coin.
 *
 * Fixes 2 and 3 live in AppViewModel (close the call view on a
 * navigation, so the screen shows and the session ends cleanly):
 * they are proven by the phone test, not by a JVM test. The rest
 * is proven here, through the pure parser, and the real proof is
 * still Flint's phone.
 */
class V1128Test {

    private fun testMarket(id: String, question: String, slug: String): Market =
        Market(
            id = id, question = question, status = "open", closed = false,
            start = 0, end = 0, volume = null, vol24 = null, last = null,
            bid = null, ask = null, p0 = 0.5, p1 = 0.5,
            outcomes = listOf(Outcome("Yes", "tok-up-$id"), Outcome("No", "tok-down-$id")),
            tags = listOf(TagDto(slug = slug)), desc = "", winOutcome = null
        )

    private val btc90k = testMarket("btc-90k", "Will BTC close above \$90k?", "btc")
    private val eth3k = testMarket("eth-3k", "Will ETH close below \$3,000?", "eth")
    private val markets = listOf(btc90k, eth3k)

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
    fun versionIs1128() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 39"))
        assertTrue(gradle.contains("versionName = \"1.1.29\""))
    }

    // ---- The silence that ends a turn: 1200 ms, not 750/800 ----

    @Test
    fun silenceToStopIs1200() {
        val engine = readSource("app/src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt")
        assertTrue(engine.contains("CAPTURE_SILENCE_TO_STOP_MS = 1200L"))
        val recognizer = readSource("app/src/main/java/xyz/prognoslabs/app/voice/WhisperRecognizer.kt")
        assertTrue(recognizer.contains("SILENCE_TO_FINAL_MS = 1200L"))
    }

    // ---- Fix 1: the Portuguese mishearing, scored per language ----

    @Test
    fun portfolioMisheardAsPortuguese() {
        val (res, heard, lang) = parseBestVoiceUtteranceMulti(
            listOf(
                "Ai, mas posso folio não" to VoiceLanguage.PORTUGUESE,
                "Open my portfolio now" to VoiceLanguage.ENGLISH
            ),
            markets, null
        )
        assertTrue(res is VoiceParseResult.Navigate)
        assertEquals("portfolio", (res as VoiceParseResult.Navigate).route)
        assertEquals(VoiceLanguage.ENGLISH, lang)
        assertTrue(heard.contains("portfolio"))
    }

    @Test
    fun genuinePortugueseStillWins() {
        val (res, _, lang) = parseBestVoiceUtteranceMulti(
            listOf(
                "Posso negociar Bitcoin?" to VoiceLanguage.PORTUGUESE,
                "Possum negotiate Bitcoin?" to VoiceLanguage.ENGLISH
            ),
            markets, null
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceLanguage.PORTUGUESE, lang)
    }

    @Test
    fun multiSingleCandidateBehaves() {
        val (res, _, lang) = parseBestVoiceUtteranceMulti(
            listOf("Hello" to VoiceLanguage.ENGLISH),
            emptyList(), null
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.GreetingHello, (res as VoiceParseResult.Question).question)
        assertEquals(VoiceLanguage.ENGLISH, lang)
    }

    // ---- Fix 4: a greeting that continues into a question ----

    @Test
    fun goodMorningTellMeAboutPrognos() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Good morning. Tell me about Prognos Labs."),
            markets, null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertTrue((q as VoiceQuestion.Knowledge).answer.contains("prediction market"))
    }

    @Test
    fun helloWhatIsEthPrice() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Hello. What is the price of Ethereum?"),
            markets, null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.Price("ETH"), (res as VoiceParseResult.Question).question)
    }

    @Test
    fun helloThankYouGreets() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Hello. Thank you."),
            emptyList(), null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.GreetingHello, (res as VoiceParseResult.Question).question)
    }

    @Test
    fun helloAloneGreets() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Hello."),
            emptyList(), null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.GreetingHello, (res as VoiceParseResult.Question).question)
    }

    @Test
    fun hiThereGreets() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Hi there."),
            emptyList(), null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.GreetingHello, (res as VoiceParseResult.Question).question)
    }

    @Test
    fun heyHowAreAsksAgain() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Hey how are"),
            emptyList(), null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.AskAgain)
    }

    @Test
    fun goodMorningOpenPortfolioNavigates() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Good morning. Open my portfolio."),
            markets, null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Navigate)
        assertEquals("portfolio", (res as VoiceParseResult.Navigate).route)
    }

    // ---- Fix 5: "tell me about" a coin, misheard or not ----

    @Test
    fun tellMeAboutEthereumNoMarket() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Tell me about Ethereum"),
            emptyList(), null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.Price("ETH"), (res as VoiceParseResult.Question).question)
    }

    @Test
    fun tellMeAboutEthereumWithMarket() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Tell me about Ethereum"),
            markets, null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.MarketInfo("eth-3k"), (res as VoiceParseResult.Question).question)
    }

    @Test
    fun tellMeAboutEcteriumFuzzy() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Tell me about Ecterium"),
            emptyList(), null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.Price("ETH"), (res as VoiceParseResult.Question).question)
    }

    @Test
    fun whatAboutSolana() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("What about Solana?"),
            emptyList(), null, VoiceLanguage.ENGLISH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.Price("SOL"), (res as VoiceParseResult.Question).question)
    }

    @Test
    fun assetMentionEcterium() {
        assertEquals("ETH", assetMention("Tell me about Ecterium")?.first)
    }

    @Test
    fun balanceIsNotBinance() {
        // The first fuzzy matcher turned "balance" into BNB (2 edits
        // from "binance", same first and last letter) and "show my
        // balance" opened a BNB market instead of the dashboard.
        // A word the parser already understands must never become
        // a coin.
        assertEquals(null, assetMention("show my balance"))
        assertEquals(null, assetMention("What is my balance?"))
    }

    @Test
    fun levenshteinChecks() {
        assertEquals(3, levenshtein("ecterium", "ethereum"))
        assertEquals(3, levenshtein("kitten", "sitting"))
        assertEquals(3, levenshtein("", "abc"))
        assertEquals(0, levenshtein("same", "same"))
    }
}
