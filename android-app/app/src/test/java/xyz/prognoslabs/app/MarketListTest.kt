package xyz.prognoslabs.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.MarketFilter
import xyz.prognoslabs.app.data.MarketSort
import xyz.prognoslabs.app.data.PAGE_SIZE
import xyz.prognoslabs.app.data.filterMarkets
import xyz.prognoslabs.app.data.greetingForHour
import xyz.prognoslabs.app.data.pageMarkets
import xyz.prognoslabs.app.data.remainingCount
import xyz.prognoslabs.app.data.sortMarkets
import xyz.prognoslabs.app.data.topMovers
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TickerAsset

private fun fakeMarket(id: String, question: String, asset: String, p0: Double?, end: Long): Market {
    return Market(
        id = id, question = question, status = "active", closed = false,
        start = 0L, end = end, volume = null, vol24 = null, last = null,
        bid = null, ask = null, p0 = p0, p1 = p0?.let { 1.0 - it },
        outcomes = listOf(Outcome("Up", "t1"), Outcome("Down", "t2")),
        tags = emptyList(), desc = "", winOutcome = null
    )
}

class MarketListTest {

    private val markets = listOf(
        fakeMarket("m1", "Bitcoin Up or Down - 5 min", "BTC", 0.62, 1000L),
        fakeMarket("m2", "Ethereum Up or Down - 5 min", "ETH", 0.41, 500L),
        fakeMarket("m3", "Bitcoin Up or Down - 15 min", "BTC", null, 2000L)
    )
    private val chg = mapOf("BTC" to 0.02, "ETH" to -0.015)

    @Test
    fun gainersFollowAssetMove() {
        val out = filterMarkets(markets, "", MarketFilter.GAINERS, "all", emptySet(), chg)
        assertEquals(listOf("m1", "m3"), out.map { it.id })
    }

    @Test
    fun losersFollowAssetMove() {
        val out = filterMarkets(markets, "", MarketFilter.LOSERS, "all", emptySet(), chg)
        assertEquals(listOf("m2"), out.map { it.id })
    }

    @Test
    fun watchlistFilterKeepsOnlySaved() {
        val out = filterMarkets(markets, "", MarketFilter.WATCHLIST, "all", setOf("m2"), chg)
        assertEquals(listOf("m2"), out.map { it.id })
    }

    @Test
    fun searchSpansQuestionAndAsset() {
        assertEquals(listOf("m1", "m3"), filterMarkets(markets, "bitcoin", MarketFilter.ALL, "all", emptySet(), chg).map { it.id })
        assertEquals(listOf("m2"), filterMarkets(markets, "ethereum", MarketFilter.ALL, "all", emptySet(), chg).map { it.id })
        assertEquals(3, filterMarkets(markets, "up or down", MarketFilter.ALL, "all", emptySet(), chg).size)
    }

    @Test
    fun sortEndingSoonFirst() {
        val out = sortMarkets(markets, MarketSort.ENDING_SOON)
        assertEquals(listOf("m2", "m1", "m3"), out.map { it.id })
    }

    @Test
    fun sortPriceHighPutsPricedFirst() {
        val out = sortMarkets(markets, MarketSort.PRICE_HIGH)
        assertEquals("m1", out.first().id)
        assertEquals("m3", out.last().id)
    }

    @Test
    fun sortMostOneSidedFirst() {
        val out = sortMarkets(markets, MarketSort.MOST_ONE_SIDED)
        assertEquals("m1", out.first().id)
    }

    @Test
    fun topMoversTakesBiggestMove() {
        val ticker = listOf(
            TickerAsset("BTC", null, 65000.0, 0.01),
            TickerAsset("ETH", null, 2000.0, -0.03),
            TickerAsset("SOL", null, 100.0, 0.005)
        )
        val out = topMovers(ticker, 2)
        assertEquals(listOf("ETH", "BTC"), out.map { it.asset })
    }

    @Test
    fun greetingFollowsTimeOfDay() {
        assertEquals("Good morning", greetingForHour(9))
        assertEquals("Good afternoon", greetingForHour(14))
        assertEquals("Good evening", greetingForHour(19))
        assertEquals("Good night", greetingForHour(2))
    }

    @Test
    fun emptyFilterResultIsEmpty() {
        val out = filterMarkets(markets, "zzzz", MarketFilter.ALL, "all", emptySet(), chg)
        assertTrue(out.isEmpty())
    }

    @Test
    fun pageSizeIsEight() {
        assertEquals(8, PAGE_SIZE)
    }

    @Test
    fun pageMarketsShowsFirstPageOnly() {
        val ten = (1..10).map { fakeMarket("m$it", "Bitcoin Up or Down - 5 min", "BTC", 0.5, 1000L) }
        val out = pageMarkets(ten, PAGE_SIZE)
        assertEquals(8, out.size)
        assertEquals("m1", out.first().id)
        assertEquals("m8", out.last().id)
        assertEquals(10, pageMarkets(ten, 20).size)
        assertTrue(pageMarkets(ten, 0).isEmpty())
    }

    @Test
    fun remainingCountCountsWhatIsLeft() {
        assertEquals(2, remainingCount(10, 8))
        assertEquals(0, remainingCount(8, 8))
        assertEquals(0, remainingCount(3, 8))
    }
}
