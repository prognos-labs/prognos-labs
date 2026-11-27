package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto

/**
 * v1.1.26: Flint tested v1.1.25 on his phone and the mic heard both
 * questions exactly right, but the app refused both with "I only
 * answer questions about Prognos Labs trading": "Is Bitcoin in
 * Prognos Labs?" and "Can I trade Bitcoin on Prognos Labs?".
 * No branch answered whether an asset is on Prognos Labs, and the
 * price, news and market branches all need their own trigger words.
 * An asset-availability question is now a trading question: with a
 * matching market loaded it answers with that market's info, and
 * with no market loaded it answers with a plain yes naming the
 * asset. "Can I trade" also reaches HowToTrade without "how".
 *
 * These tests run on the JVM. There is no microphone and no phone
 * here, so the matcher is proven through pure helpers, and the real
 * proof is still Flint's phone.
 */
class V1126Test {

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
    fun versionIs1126() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 40"))
        assertTrue(gradle.contains("versionName = \"1.1.30\""))
    }

    // ---- Flint's two phone cases ----

    @Test
    fun isBitcoinInPrognosLabsWithMarket() {
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
    fun isBitcoinInPrognosLabsWithoutMarket() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Is Bitcoin in Prognos Labs?"),
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

    @Test
    fun canITradeBitcoinWithMarket() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Can I trade Bitcoin on Prognos Labs?"),
            markets
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("btc-90k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun canITradeBitcoinWithoutMarket() {
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

    // ---- Same shape, other assets ----

    @Test
    fun isEthereumOnPrognosLabs() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Is Ethereum on Prognos Labs?"),
            markets
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.MarketInfo)
        assertEquals("eth-3k", (q as VoiceQuestion.MarketInfo).marketId)
    }

    @Test
    fun canITradeSolana() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Can I trade Solana?"),
            emptyList()
        )
        assertTrue(res is VoiceParseResult.Question)
        val q = (res as VoiceParseResult.Question).question
        assertTrue(q is VoiceQuestion.Knowledge)
        assertEquals(
            "Yes, Solana can be traded on Prognos Labs.",
            (q as VoiceQuestion.Knowledge).answer
        )
    }

    // ---- "Can I trade" without an asset, and "how" questions ----

    @Test
    fun canITradeWithoutAssetIsHowToTrade() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("Can I trade?"),
            emptyList()
        )
        assertTrue(res is VoiceParseResult.Question)
        assertTrue((res as VoiceParseResult.Question).question is VoiceQuestion.HowToTrade)
    }

    @Test
    fun howDoITradeBitcoinIsHowToTrade() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("How do I trade Bitcoin?"),
            markets
        )
        assertTrue(res is VoiceParseResult.Question)
        assertTrue((res as VoiceParseResult.Question).question is VoiceQuestion.HowToTrade)
    }

    // ---- A genuinely off-topic question is still refused ----

    @Test
    fun weatherStillOutOfScope() {
        val (res, _) = parseBestVoiceUtterance(
            listOf("what is the weather"),
            markets
        )
        assertTrue(res is VoiceParseResult.OutOfScope)
    }
}
