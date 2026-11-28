package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto

/**
 * v1.1.27: the v1.1.26 asset-availability fix was English only.
 * Flint tested 1.1.26 on his phone and a Portuguese trading
 * question still got the Portuguese refusal: "Só ajudo com o
 * trading na Prognos Labs." No translated knowledge topic catches
 * a bare "can I trade Bitcoin" either, in Portuguese or in any
 * other language. The availability branch and the "can I trade"
 * form now work in all 12 languages, with the plain yes answer
 * in the turn language.
 *
 * These tests run on the JVM. There is no microphone and no phone
 * here, so the matcher is proven through pure helpers, and the real
 * proof is still Flint's phone.
 */
class V1127Test {

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
    fun versionIs1127() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 41"))
        assertTrue(gradle.contains("versionName = \"1.1.31\""))
    }

    // ---- Flint's Portuguese case, with and without a loaded market ----

    @Test
    fun possoNegociarBitcoinWithMarket() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Posso negociar Bitcoin?"),
            markets, null, VoiceLanguage.PORTUGUESE
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun possoNegociarBitcoinWithoutMarket() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Posso negociar Bitcoin?"),
            emptyList(), null, VoiceLanguage.PORTUGUESE
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertEquals(
            "Sim, Bitcoin pode ser negociado na Prognos Labs.",
            (q as VoiceQuestion.Knowledge).answer
        )
    }

    @Test
    fun oBitcoinEstaNaPrognosLabs() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("O Bitcoin está na Prognos Labs?"),
            markets, null, VoiceLanguage.PORTUGUESE
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun possoNegociarWithoutAssetIsHowToTrade() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Posso negociar?"),
            emptyList(), null, VoiceLanguage.PORTUGUESE
        )
        assertTrue(res is VoiceParseResult.Question)
        assertTrue((res as VoiceParseResult.Question).question is VoiceQuestion.HowToTrade)
    }

    @Test
    fun comoNegociarBitcoinIsHowToTrade() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Como faço para negociar Bitcoin?"),
            markets, null, VoiceLanguage.PORTUGUESE
        )
        assertTrue(res is VoiceParseResult.Question)
        assertTrue((res as VoiceParseResult.Question).question is VoiceQuestion.HowToTrade)
    }

    // ---- The same shape in French, Spanish and German ----

    @Test
    fun puisJeTraderLeBitcoin() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Puis-je trader le Bitcoin ?"),
            markets, null, VoiceLanguage.FRENCH
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun puisJeTraderSansMarche() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Puis-je trader le Bitcoin ?"),
            emptyList(), null, VoiceLanguage.FRENCH
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertEquals(
            "Oui, on peut trader Bitcoin sur Prognos Labs.",
            (q as VoiceQuestion.Knowledge).answer
        )
    }

    @Test
    fun commentTraderBitcoinIsHowToTrade() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Comment trader le Bitcoin ?"),
            markets, null, VoiceLanguage.FRENCH
        )
        assertTrue(res is VoiceParseResult.Question)
        assertTrue((res as VoiceParseResult.Question).question is VoiceQuestion.HowToTrade)
    }

    @Test
    fun puedoOperarConBitcoin() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("¿Puedo operar con Bitcoin?"),
            markets, null, VoiceLanguage.SPANISH
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun puedoOperarSinMercado() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("¿Puedo operar con Bitcoin?"),
            emptyList(), null, VoiceLanguage.SPANISH
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertEquals(
            "Sí, Bitcoin se puede operar en Prognos Labs.",
            (q as VoiceQuestion.Knowledge).answer
        )
    }

    @Test
    fun kannIchBitcoinHandeln() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Kann ich Bitcoin handeln?"),
            markets, null, VoiceLanguage.GERMAN
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun kannIchBitcoinHandelnOhneMarkt() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Kann ich Bitcoin handeln?"),
            emptyList(), null, VoiceLanguage.GERMAN
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertEquals(
            "Ja, Bitcoin kann auf Prognos Labs gehandelt werden.",
            (q as VoiceQuestion.Knowledge).answer
        )
    }

    // ---- The hard scripts: Chinese has no spaces, Turkish glues
    // the preposition onto the brand name ----

    @Test
    fun woKeyiJiaoyiBitebi() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("我可以交易比特币吗"),
            markets, null, VoiceLanguage.CHINESE
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun woKeyiJiaoyiBitebiSansMarket() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("我可以交易比特币吗"),
            emptyList(), null, VoiceLanguage.CHINESE
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertEquals(
            "可以，Bitcoin可以在Prognos Labs上交易。",
            (q as VoiceQuestion.Knowledge).answer
        )
    }

    @Test
    fun bitcoinPrognosLabstaVarMi() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Bitcoin Prognos Labs'ta var mı?"),
            markets, null, VoiceLanguage.TURKISH
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    // ---- The English cases from v1.1.26 stay green ----

    @Test
    fun isBitcoinInPrognosLabsStillWorks() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Is Bitcoin in Prognos Labs?"),
            markets
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun canITradeBitcoinStillWorks() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Can I trade Bitcoin on Prognos Labs?"),
            emptyList()
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertEquals(
            "Yes, Bitcoin can be traded on Prognos Labs.",
            (q as VoiceQuestion.Knowledge).answer
        )
    }

    // ---- A genuinely off-topic question is still refused,
    // in Portuguese too ----

    @Test
    fun portugueseWeatherStillOutOfScope() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("qual é o clima"),
            markets, null, VoiceLanguage.PORTUGUESE
        )
        assertTrue(res is VoiceParseResult.OutOfScope)
    }

    @Test
    fun englishWeatherStillOutOfScope() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("what is the weather"),
            markets
        )
        assertTrue(res is VoiceParseResult.OutOfScope)
    }
}
