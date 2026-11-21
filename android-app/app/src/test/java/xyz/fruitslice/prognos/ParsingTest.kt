package xyz.fruitslice.prognos

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fruitslice.prognos.data.forecastFor
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.ChartResponse
import xyz.fruitslice.prognos.model.LearningResponse
import xyz.fruitslice.prognos.model.MarketsResponse
import xyz.fruitslice.prognos.model.NewsResponse
import xyz.fruitslice.prognos.model.TickerResponse
import xyz.fruitslice.prognos.model.WatchdogResponse
import xyz.fruitslice.prognos.model.toMarket
import java.io.InputStreamReader

class ParsingTest {
    private val gson = GsonBuilder().setLenient().create()

    private fun <T> load(name: String, cls: Class<T>): T {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: throw IllegalStateException("missing fixture $name")
        return InputStreamReader(stream).use { gson.fromJson(it, cls) }
    }

    @Test
    fun tickerParsesEightAssets() {
        val t = load("ticker.json", TickerResponse::class.java)
        assertEquals(8, t.assets!!.size)
        val btc = t.assets.first { it.asset == "BTC" }
        assertTrue((btc.price ?: 0.0) > 1000)
        assertTrue(t.assets.map { it.asset }.containsAll(listOf("BTC", "ETH", "XRP", "SOL", "DOGE", "BNB", "HYPE", "ZEC")))
    }

    @Test
    fun marketsParseWithEmptyPrices() {
        val m = load("markets.json", MarketsResponse::class.java)
        assertTrue((m.items?.size ?: 0) >= 50)
        val markets = m.items!!.mapNotNull { it.toMarket() }
        assertTrue(markets.isNotEmpty())
        // First fixture market has empty best_bid/best_ask: price must be null, not a crash.
        val empty = markets.first { it.id == "ge_e214001_m1204814" }
        assertNull(empty.p0)
        assertNull(forecastFor(empty))
        // A priced market parses and forecasts.
        val priced = markets.first { it.p0 != null }
        assertEquals(0.5, priced.p0!!, 0.001)
        val fc = forecastFor(priced, priced.end - 600)
        assertNotNull(fc)
        assertTrue(fc!!.verdict.isNotBlank())
        // New format: an analyst note that ends on what comes next. It
        // never says "quotes only" and never reports a thin book as a
        // volume line.
        assertTrue(fc.reason.contains("what comes next"))
        assertFalse(fc.reason.contains("quotes only"))
        assertFalse(fc.reason.contains("traded"))
    }

    @Test
    fun marketAssetDetection() {
        val m = load("markets.json", MarketsResponse::class.java)
        val markets = m.items!!.mapNotNull { it.toMarket() }
        val hype = markets.first { it.id == "ge_1403288_7097536" }
        assertEquals("HYPE", hype.asset)
        assertEquals("Hyperliquid", hype.sectorLabel)
        val eth = markets.first { it.question.contains("Ethereum") }
        assertEquals("ETH", eth.asset)
    }

    @Test
    fun bookParsesBidsAsks() {
        val b = load("book.json", BookResponse::class.java)
        assertTrue((b.bids?.size ?: 0) > 0)
        assertTrue((b.asks?.size ?: 0) > 0)
        assertEquals("0.49", b.bids!![0].price)
        assertEquals("0.51", b.asks!![0].price)
        // Bids green, asks red: best bid below best ask.
        assertTrue(b.bids[0].price!!.toDouble() < b.asks[0].price!!.toDouble())
    }

    @Test
    fun chartWithLineNull() {
        val c = load("chart.json", ChartResponse::class.java)
        assertEquals(true, c.available)
        assertNull(c.line)
        assertEquals(false, c.started)
        assertTrue((c.points?.size ?: 0) > 10)
        assertTrue((c.current ?: 0.0) > 0)
    }

    @Test
    fun newsParsesHttpLinksOnly() {
        val n = load("news.json", NewsResponse::class.java)
        assertTrue((n.items?.size ?: 0) >= 5)
        for (item in n.items!!) {
            assertTrue(item.link!!.startsWith("http://") || item.link.startsWith("https://"))
            assertTrue(item.title!!.isNotBlank())
        }
    }

    @Test
    fun watchdogAndLearningParse() {
        val w = load("watchdog.json", WatchdogResponse::class.java)
        assertEquals(100, w.checked)
        assertNotNull(w.flags)
        val l = load("learning.json", LearningResponse::class.java)
        assertEquals("formula", l.champion)
        assertEquals(0, l.scored)
        assertEquals(30, l.samplesNeeded)
    }

    @Test
    fun forecastThresholds() {
        val m = load("markets.json", MarketsResponse::class.java)
            .items!!.mapNotNull { it.toMarket() }
            .first { it.p0 != null }
        // 50% price, far from close, no move, no mood, no volume: Quiet.
        val fc = forecastFor(m, m.end - 3600)
        assertEquals("Quiet", fc!!.verdict)
        assertTrue(fc.reason.contains("what comes next"))
    }
}
