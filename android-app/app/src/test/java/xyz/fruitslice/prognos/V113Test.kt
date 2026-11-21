package xyz.fruitslice.prognos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fruitslice.prognos.data.ForecastContext
import xyz.fruitslice.prognos.data.forecastFor
import xyz.fruitslice.prognos.data.newsMood
import xyz.fruitslice.prognos.model.BookEntry
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.NewsFeed
import xyz.fruitslice.prognos.model.NewsItem
import xyz.fruitslice.prognos.model.NewsMood
import xyz.fruitslice.prognos.model.Outcome
import xyz.fruitslice.prognos.model.TagDto

/**
 * v1.1.3: the forecast is a short analyst note, not a pick. The verdict
 * is a label for the market (Moving, One-sided, Crowded, Tense, Quiet,
 * Closed), never a side. A liquidity wall is only named when it is big
 * enough to matter (at least $1,000 at one price level); a thin book is
 * skipped, never dressed up as a wall. Small book numbers never appear.
 */
class V113Test {

    companion object {
        private const val NOW = 1794930000L

        private fun market(
            p0: Double? = 0.5,
            bid: Double? = 0.5,
            ask: Double? = 0.51,
            end: Long = NOW + 23 * 3600,
            vol24: Any? = 0,
            question: String = "Bitcoin Up or Down - November 21, 9:00AM-9:05AM ET",
            tags: List<TagDto> = listOf(TagDto(slug = "bitcoin"))
        ) = Market(
            id = "test-1",
            question = question,
            status = "",
            closed = false,
            start = NOW - 3600,
            end = end,
            volume = 0,
            vol24 = vol24,
            last = p0,
            bid = bid,
            ask = ask,
            p0 = p0,
            p1 = if (p0 == null) null else 1 - p0,
            outcomes = listOf(Outcome("Up", "tok-up"), Outcome("Down", "tok-down")),
            tags = tags,
            desc = "",
            winOutcome = null
        )

        /** The live thin book: $586 on the sell side, $270 on the buy side.
         *  Neither number can push a market, so neither may appear. */
        private fun thinBook() = BookResponse(
            bids = listOf(BookEntry("0.50", "544.79")),
            asks = listOf(BookEntry("0.51", "1148.85"))
        )

        /** A real wall: 16,700 shares at 0.72 is $12,024. */
        private fun wallBook() = BookResponse(
            bids = listOf(BookEntry("0.72", "16700"), BookEntry("0.71", "500")),
            asks = listOf(BookEntry("0.73", "200"))
        )
    }

    @Test
    fun thinBookOmitsTheBookSentence() {
        val fc = forecastFor(market(), NOW, ForecastContext(bookUp = thinBook()))
        assertNotNull(fc)
        // No dollar figure at all: the book is skipped, not quoted.
        assertFalse(fc!!.reason.contains("$"))
        assertFalse(fc.reason.contains("liquidity walls"))
        assertFalse(fc.reason.contains("empty"))
        assertFalse(fc.reason.contains("no trades"))
        // It still ends on what comes next.
        assertTrue(fc.reason.contains("what comes next"))
    }

    @Test
    fun noSmallBookNumbers() {
        val fc = forecastFor(market(), NOW, ForecastContext(bookUp = thinBook()))
        assertNotNull(fc)
        // $586 sell / $270 buy must not appear anywhere in the note.
        assertFalse(fc!!.reason.contains("586"))
        assertFalse(fc.reason.contains("270"))
        assertFalse(fc.reason.contains("544"))
        assertFalse(fc.reason.contains("1148"))
    }

    @Test
    fun bigWallIsCited() {
        val m = market(p0 = 0.72, bid = 0.71, ask = 0.73)
        val fc = forecastFor(m, NOW, ForecastContext(bookUp = wallBook()))
        assertNotNull(fc)
        assertEquals("One-sided", fc!!.verdict)
        assertTrue(fc.reason.contains("buy side is sitting at \$12k in liquidity walls at 0.72"))
        // The $146 ask level is below the $1,000 wall line, so it is not cited.
        assertFalse(fc.reason.contains("sell side"))
    }

    @Test
    fun noPickLanguage() {
        val moving = forecastFor(market(), NOW, ForecastContext(chg24 = -0.032))
        assertNotNull(moving)
        assertEquals("Moving", moving!!.verdict)
        val thin = forecastFor(market(), NOW, ForecastContext(bookUp = thinBook()))
        assertNotNull(thin)
        val wall = forecastFor(market(p0 = 0.72, bid = 0.71, ask = 0.73), NOW, ForecastContext(bookUp = wallBook()))
        assertNotNull(wall)
        val allowed = setOf("Moving", "One-sided", "Crowded", "Tense", "Quiet", "Closed")
        for (fc in listOf(moving, thin, wall)) {
            assertTrue(allowed.contains(fc!!.verdict))
            assertFalse(fc.reason.contains("Leaning"))
            assertFalse(fc.reason.contains("About even"))
            assertFalse(fc.reason.contains("pick Yes"))
            assertFalse(fc.reason.contains("pick No"))
            assertFalse(fc.reason.contains("buy Yes"))
            assertFalse(fc.reason.contains("buy No"))
        }
    }

    @Test
    fun newsMatchesByAssetTag() {
        val btcItem = NewsItem(
            title = "Bitcoin nears repeat of chart pattern that preceded 2023's \$25K retest",
            tags = listOf("BTC")
        )
        val ethItem = NewsItem(
            title = "Binance hacker moves nearly \$600M in ETH",
            tags = listOf("ETH")
        )
        val news = NewsFeed(
            items = listOf(btcItem, ethItem),
            mood = NewsMood(scanned = 55, positive = 0, negative = 1)
        )
        val btc = forecastFor(market(), NOW, ForecastContext(news = news.items, mood = news.mood))
        assertNotNull(btc)
        assertTrue(btc!!.reason.contains("chart watchers flagging a pattern"))
        assertFalse(btc.reason.contains("a hack"))
        val ethMarket = market(
            question = "Ethereum Up or Down - November 21, 8:45AM-8:50AM ET",
            tags = listOf(TagDto(slug = "ethereum"))
        )
        val eth = forecastFor(ethMarket, NOW, ForecastContext(news = news.items, mood = news.mood))
        assertNotNull(eth)
        assertTrue(eth!!.reason.contains("a hack"))
        assertFalse(eth.reason.contains("chart watchers"))
    }

    @Test
    fun moodHeavyAndUpbeat() {
        assertEquals("heavy", newsMood(NewsMood(scanned = 55, positive = 0, negative = 1)))
        assertEquals("upbeat", newsMood(NewsMood(scanned = 40, positive = 3, negative = 1)))
        assertNull(newsMood(NewsMood(scanned = 10, positive = 1, negative = 1)))
        assertNull(newsMood(null))
        // A heavy mood shows up in the note.
        val news = NewsFeed(items = emptyList(), mood = NewsMood(scanned = 55, positive = 0, negative = 1))
        val fc = forecastFor(market(), NOW, ForecastContext(news = news.items, mood = news.mood))
        assertNotNull(fc)
        assertTrue(fc!!.reason.contains("The mood in the headlines is heavy"))
    }

    @Test
    fun timeAndWhatsNext() {
        val fc = forecastFor(market(), NOW)
        assertNotNull(fc)
        assertTrue(fc!!.reason.contains("about 23 hours left"))
        assertTrue(fc.reason.contains("what comes next for Bitcoin"))
        // A five-minute window says so plainly.
        val fiveMin = forecastFor(market(end = NOW + 240), NOW)
        assertNotNull(fiveMin)
        assertTrue(fiveMin!!.reason.contains("under five minutes left"))
        assertTrue(fiveMin.reason.contains("what comes next"))
    }

    @Test
    fun movingMarketReadsTheMove() {
        val fc = forecastFor(market(), NOW, ForecastContext(chg24 = -0.032))
        assertNotNull(fc)
        assertEquals("Moving", fc!!.verdict)
        assertTrue(fc.reason.contains("Bitcoin is under pressure."))
        assertTrue(fc.reason.contains("sliding, down about 3.2% on the day"))
        // The chart is the fallback when the ticker has no move.
        val chartFc = forecastFor(
            market(), NOW,
            ForecastContext(chartPoints = listOf(listOf(0.0, 0.52), listOf(1.0, 0.50)))
        )
        assertNotNull(chartFc)
        assertEquals("Moving", chartFc!!.verdict)
        assertTrue(chartFc.reason.contains("over the last hour"))
        assertTrue(chartFc.reason.contains("3.8%"))
    }

    @Test
    fun noPriceReturnsNull() {
        assertNull(forecastFor(market(p0 = null, bid = null, ask = null), NOW))
    }

    @Test
    fun closedMarket() {
        val fc = forecastFor(market(end = NOW - 60), NOW)
        assertNotNull(fc)
        assertEquals("Closed", fc!!.verdict)
        assertTrue(fc.reason.contains("Time is up, the market has closed."))
    }
}
