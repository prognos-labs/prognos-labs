package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.TradeSide
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.data.parseBestVoiceUtteranceMulti
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto

/**
 * v1.1.29: Flint tested the build from the website (two screen
 * recordings, 27 Nov ~00:47 WAT) and two things were still wrong.
 *
 * 1. Prices are not interpreted. "Trade Bitcoin for me below 35"
 *    was answered with the market info (Yes 0.51, No 0.50) and the
 *    35 was never used at all: "trade" was not an order verb, so
 *    the sentence fell to the question path, and the
 *    asset-availability branch answered it as "can I trade
 *    Bitcoin". The follow-ups failed the same way: "Yes, trade is
 *    from there" was refused as out of scope, and "trade that
 *    trade that you sent to me just now" got "I did not catch
 *    that", because the app keeps no memory of the market it just
 *    showed. A number inside the market's time ("6:35PM") also
 *    counted as a price, so an amount of 35 was thrown away.
 * 2. The language-first enrollment is missing: the setup screen
 *    still deals the test phrases in the Settings language with
 *    no picker, so a speaker of another language has to say
 *    English phrases.
 * 3. The second video showed one more: Flint said "Tell me about
 *    Prognos Labs" and Groq heard "Tell me about food, Ms. Labs",
 *    which the app refused as out of scope. The brand's second
 *    word survives the mishearing, so "tell me about ... labs"
 *    now answers about Prognos Labs. A repeat of the same sentence
 *    was also swallowed with no answer at all; that one is still
 *    open, it needs the engine, not the parser.
 *
 * Fixes, all proven here through the pure parser, plus the
 * enrollment screen (proven by a source check: the picker is
 * Compose, not pure Kotlin). The real proof is still Flint's
 * phone.
 */
class V1129Test {

    private fun testMarket(id: String, question: String, slug: String, p0: Double?, p1: Double?): Market =
        Market(
            id = id, question = question, status = "open", closed = false,
            start = 0, end = 0, volume = null, vol24 = null, last = null,
            bid = null, ask = null, p0 = p0, p1 = p1,
            outcomes = listOf(Outcome("Yes", "tok-up-$id"), Outcome("No", "tok-down-$id")),
            tags = listOf(TagDto(slug = slug)), desc = "", winOutcome = null
        )

    // The exact market from Flint's video: a 5-minute BTC up/down
    // market, Yes 0.51, No 0.50.
    private val btc5m = testMarket(
        "btc-5m-1835", "Bitcoin Up or Down - November 27, 6:35PM-6:40PM ET", "btc", 0.51, 0.50
    )
    private val btc90k = testMarket("btc-90k", "Will BTC close above \$90k?", "btc", 0.5, 0.5)
    private val markets = listOf(btc5m, btc90k)

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
    fun versionIs1129() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 40"))
        assertTrue(gradle.contains("versionName = \"1.1.30\""))
    }

    @Test
    fun tradeBitcoinBelow35IsALimitAnswerNotMarketInfo() {
        // Flint's exact sentence. Neither side is at 0.35, so the
        // app must say the current prices against his limit, not
        // show the market and drop the number.
        val parsed = parseVoiceUtterance("trade Bitcoin for me below 35", markets, null, VoiceLanguage.ENGLISH)
        assertTrue(parsed is VoiceParseResult.Question)
        val answer = (parsed as VoiceParseResult.Question).question
        assertTrue(answer is VoiceQuestion.Knowledge)
        val text = (answer as VoiceQuestion.Knowledge).answer
        assertTrue(text.contains("0.51"))
        assertTrue(text.contains("0.50"))
        assertTrue(text.contains("0.35"))
        assertTrue(text.contains("above"))
    }

    @Test
    fun misheardBrandNameStillAnswersAboutPrognosLabs() {
        // Video 2, audio 6.5 s: Flint said "Tell me about Prognos
        // Labs". Groq heard "Tell me about food, Ms. Labs" and the
        // app refused it as out of scope. The brand's second word
        // survives the mishearing, so "tell me about ... labs" is
        // still a question about Prognos Labs.
        val parsed = parseVoiceUtterance(
            "Tell me about food, Ms. Labs", markets, null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Question)
        val answer = (parsed as VoiceParseResult.Question).question
        assertTrue(answer is VoiceQuestion.Knowledge)
        val text = (answer as VoiceQuestion.Knowledge).answer
        assertTrue(text.contains("Prognos Labs is a prediction market"))
    }

    @Test
    fun tradeBelow60WithAmountIsAnOrder() {
        // The limit is met (Yes 0.51 is below 0.60), so this is a
        // trade: $30 Yes on the 5-minute market. The 60 is the
        // price, never the amount.
        val parsed = parseVoiceUtterance(
            "buy 30 yes below 60", markets, btc5m.id, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Order)
        val order = (parsed as VoiceParseResult.Order).order
        assertEquals("btc-5m-1835", order.marketId)
        assertEquals(TradeSide.UP, order.side)
        assertEquals(30.0, order.amountUsd, 0.001)
    }

    @Test
    fun tradeBelow60WithoutAmountAsksTheAmount() {
        val parsed = parseVoiceUtterance(
            "buy yes below 60", markets, btc5m.id, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        assertTrue((parsed as VoiceParseResult.AskAgain).message.contains("amount"))
    }

    @Test
    fun tradeBelow505InfersTheSideFromThePrice() {
        // Only the No side is at or below 0.505 (No 0.50, Yes
        // 0.51), so the side is Down even though no side word
        // was spoken. The 30 before the coin is the amount.
        val parsed = parseVoiceUtterance(
            "trade 30 bitcoin below 50.5", listOf(btc5m), null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Order)
        val order = (parsed as VoiceParseResult.Order).order
        assertEquals(TradeSide.DOWN, order.side)
        assertEquals(30.0, order.amountUsd, 0.001)
    }

    @Test
    fun yesTradeIsFromThereAsksTheAmountOnTheLastMarket() {
        // The follow-up after the market was shown: "Yes" is the
        // side, the open market is the market, the amount is the
        // only missing piece. It must not be refused.
        val parsed = parseVoiceUtterance(
            "Yes, trade is from there", markets, btc5m.id, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        assertTrue((parsed as VoiceParseResult.AskAgain).message.contains("amount"))
    }

    @Test
    fun tradeThatOneAsksUpOrDownInsteadOfNotCatchingIt() {
        val parsed = parseVoiceUtterance(
            "trade that trade that you sent to me just now", markets, btc5m.id, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        assertTrue((parsed as VoiceParseResult.AskAgain).message.contains("buy up or sell down"))
    }

    @Test
    fun whenYouHearMeIsACheckIn() {
        // "Can you hear me?" misheard as "When you hear me" still
        // counts: the mic check is answered, never refused.
        val (parsed, _) = parseBestVoiceUtterance(
            listOf("When you hear me"), markets, null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.CheckIn, (parsed as VoiceParseResult.Question).question)
    }

    @Test
    fun canYouHearMeIsACheckIn() {
        val (parsed, _) = parseBestVoiceUtterance(
            listOf("Can you hear me?"), markets, null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.CheckIn, (parsed as VoiceParseResult.Question).question)
    }

    @Test
    fun multiCandidateCheckInWinsInEnglish() {
        // The v1.1.28 retry case, still true: a Portuguese
        // mis-transcription loses to the forced English one, and
        // the answer comes back in English.
        val (parsed, _, turnLang) = parseBestVoiceUtteranceMulti(
            listOf("Ai, mas posso folio não" to VoiceLanguage.PORTUGUESE, "Can you hear me?" to VoiceLanguage.ENGLISH),
            markets
        )
        assertTrue(parsed is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.CheckIn, (parsed as VoiceParseResult.Question).question)
        assertEquals(VoiceLanguage.ENGLISH, turnLang)
    }

    @Test
    fun portugueseAskAgainIsPortuguese() {
        // A turn the app cannot parse is asked again in the
        // language spoken, not in English.
        val (parsed, _) = parseBestVoiceUtterance(
            listOf("Então, eu abarço o meu lápis"), markets, null, VoiceLanguage.PORTUGUESE
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        assertEquals("Não entendi. Diga novamente.", (parsed as VoiceParseResult.AskAgain).message)
    }

    @Test
    fun timeInMarketQuestionIsNotAnAmount() {
        // "buy bitcoin 35": the 35 in "6:35PM" is a time, not a
        // price, so the $35 amount survives.
        val parsed = parseVoiceUtterance(
            "buy bitcoin 35", listOf(btc5m), null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Order)
        assertEquals(35.0, (parsed as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun above90kIsTheMarketNotALimit() {
        // "above 90k" names the market, not a 0.90 price limit.
        // With no amount given, the app asks for the amount.
        val parsed = parseVoiceUtterance(
            "buy bitcoin above 90k", listOf(btc90k), null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        assertTrue((parsed as VoiceParseResult.AskAgain).message.contains("amount"))
    }

    @Test
    fun enrollmentPicksLanguageFirst() {
        val screens = readSource("app/src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt")
        assertTrue(screens.contains("Choose your language first."))
        assertTrue(screens.contains("pickEnrollLanguage"))
        val vm = readSource("app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        assertTrue(vm.contains("languagePicked"))
        assertTrue(vm.contains("fun pickEnrollLanguage"))
        assertTrue(vm.contains("lastVoiceMarketId"))
    }
}
