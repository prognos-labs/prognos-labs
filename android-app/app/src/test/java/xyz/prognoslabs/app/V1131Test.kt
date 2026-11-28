package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.PendingDisambiguation
import xyz.prognoslabs.app.data.TradeSide
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto

/**
 * v1.1.31: Flint tested v1.1.30 on his phone (screen recording,
 * 28 Nov ~08:56 WAT) and the voice order looped without ever
 * trading.
 *
 * 1. "Ok, buy BTC down, 90k, 0.5k" was heard exactly right and
 *    still got "I heard more than one BTC market. Say the price,
 *    for example above 90k." No 90k market existed, and the live
 *    BTC markets differ by time window, not by price, so naming a
 *    price could never settle it.
 * 2. He answered the question, "Bitcoin above 58k", and got "I
 *    did not catch that." The app had already forgotten it asked:
 *    a follow-up with no trade verb falls to the question path,
 *    which has nothing to say about a bare price.
 * 3. "Buy Bitcoin above 58k for me" got the same "more than one
 *    market" question again. Same-strike markets tie on price
 *    alone, and a tie was never settled.
 * 4. The example itself was stale: BTC was at $59,742, and the
 *    app kept pointing at "above 90k".
 *
 * Fixes, all proven here through the pure parser. The real proof
 * is still Flint's phone.
 */
class V1131Test {

    private fun testMarket(id: String, question: String, slug: String, end: Long = 0): Market =
        Market(
            id = id, question = question, status = "open", closed = false,
            start = 0, end = end, volume = null, vol24 = null, last = null,
            bid = null, ask = null, p0 = 0.51, p1 = 0.50,
            outcomes = listOf(Outcome("Yes", "tok-up-$id"), Outcome("No", "tok-down-$id")),
            tags = listOf(TagDto(slug = slug)), desc = "", winOutcome = null
        )

    // Flint's video: two 5-minute BTC up/down windows, no strike
    // price in either question.
    private val btc5mA = testMarket(
        "btc-5m-1835", "Bitcoin Up or Down - November 28, 6:35PM-6:40PM ET", "btc", end = 1795797000
    )
    private val btc5mB = testMarket(
        "btc-5m-1905", "Bitcoin Up or Down - November 28, 7:05PM-7:10PM ET", "btc", end = 1795798800
    )
    private val btc90k = testMarket("btc-90k", "Will BTC close above \$90k?", "btc")
    private val windows = listOf(btc5mA, btc5mB)

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
    fun versionIs1131() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 41"))
        assertTrue(gradle.contains("versionName = \"1.1.31\""))
    }

    @Test
    fun buyBtcDown90kAsksWhichTimeAndRemembers() {
        // Flint's exact first sentence. No 90k market exists, so
        // the app must ask about what actually differs (the time
        // windows), name them, and remember the side and the $500
        // for the follow-up. It must never say "above 90k".
        val parsed = parseVoiceUtterance(
            "Ok, buy BTC down, 90k, 0.5k", windows, null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        val again = parsed as VoiceParseResult.AskAgain
        assertTrue(again.message.contains("Which time?"))
        assertTrue(again.message.contains("6:35PM to 6:40PM"))
        assertTrue(again.message.contains("7:05PM to 7:10PM"))
        assertFalse(again.message.contains("90k"))
        val pending = again.pending
        assertNotNull(pending)
        assertEquals("BTC", pending!!.asset)
        assertEquals(TradeSide.DOWN, pending.side)
        assertEquals(500.0, pending.amountUsd!!, 0.001)
        assertEquals(listOf("btc-5m-1835", "btc-5m-1905"), pending.candidateIds)
    }

    @Test
    fun priceAnswerToTheQuestionAsksWhichTimeAgain() {
        // Flint's exact second sentence. A bare price answer is
        // not a trade verb, so it used to die on the question path
        // with "I did not catch that". With the pending question
        // live it is answered with the real options instead, and
        // the pending answer stays live for one more try.
        val pending = PendingDisambiguation(
            "BTC", TradeSide.DOWN, 500.0, listOf("btc-5m-1835", "btc-5m-1905")
        )
        val parsed = parseVoiceUtterance(
            "Bitcoin above 58k", windows, null, VoiceLanguage.ENGLISH, pending
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        val again = parsed as VoiceParseResult.AskAgain
        assertTrue(again.message.contains("Which time?"))
        assertFalse(again.message.contains("did not catch"))
        assertNotNull(again.pending)
    }

    @Test
    fun timeAnswerResolvesToTheWindow() {
        // The follow-up that works: the user names the window, and
        // the remembered side ($500 down) completes the order.
        val pending = PendingDisambiguation(
            "BTC", TradeSide.DOWN, 500.0, listOf("btc-5m-1835", "btc-5m-1905")
        )
        val parsed = parseVoiceUtterance(
            "the 6:35 one", windows, null, VoiceLanguage.ENGLISH, pending
        )
        assertTrue(parsed is VoiceParseResult.Order)
        val order = (parsed as VoiceParseResult.Order).order
        assertEquals("btc-5m-1835", order.marketId)
        assertEquals(TradeSide.DOWN, order.side)
        assertEquals(500.0, order.amountUsd, 0.001)
        assertTrue(order.readback.contains("6:35PM-6:40PM"))
    }

    @Test
    fun sameStrikeTiePicksSoonestExpiry() {
        // Two markets with the same strike and the same words tie
        // on price alone. The soonest-expiring one wins, and the
        // read-back names it, so the user can still say no.
        val early = testMarket("btc-58k-early", "Will BTC close above \$58k?", "btc", end = 1000)
        val late = testMarket("btc-58k-late", "Will BTC close above \$58k?", "btc", end = 2000)
        val parsed = parseVoiceUtterance(
            "buy bitcoin above 58k 50", listOf(early, late), null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Order)
        val order = (parsed as VoiceParseResult.Order).order
        assertEquals("btc-58k-early", order.marketId)
        assertEquals(TradeSide.UP, order.side)
        assertEquals(50.0, order.amountUsd, 0.001)
    }

    @Test
    fun strikeMarketStillResolvesDirectly() {
        // A named strike that exists settles the market outright.
        val parsed = parseVoiceUtterance(
            "buy bitcoin above 90k 50", listOf(btc5mA, btc90k), null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.Order)
        val order = (parsed as VoiceParseResult.Order).order
        assertEquals("btc-90k", order.marketId)
        assertEquals(50.0, order.amountUsd, 0.001)
    }

    @Test
    fun strikeExampleUsesARealStrike() {
        // When the markets do differ by price, the example names
        // a strike that actually exists, not a hardcoded 90k.
        val early = testMarket("btc-58k-early", "Will BTC close above \$58k?", "btc", end = 1000)
        val other = testMarket("btc-60k", "Will BTC close above \$60k?", "btc", end = 2000)
        val parsed = parseVoiceUtterance(
            "buy bitcoin 50", listOf(early, other), null, VoiceLanguage.ENGLISH
        )
        assertTrue(parsed is VoiceParseResult.AskAgain)
        val again = parsed as VoiceParseResult.AskAgain
        assertTrue(again.message.contains("58k") || again.message.contains("60k"))
        assertFalse(again.message.contains("90k"))
    }

    @Test
    fun viewModelHoldsPendingDisambiguation() {
        val vm = readSource("app/src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt")
        assertTrue(vm.contains("voicePendingDisambiguation"))
        assertTrue(vm.contains("PendingDisambiguation"))
        assertTrue(vm.contains("voicePendingDisambiguation = null"))
    }
}
