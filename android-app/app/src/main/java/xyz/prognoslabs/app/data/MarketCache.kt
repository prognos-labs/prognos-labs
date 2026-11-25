package xyz.prognoslabs.app.data

import com.google.gson.Gson
import xyz.prognoslabs.app.model.BookResponse
import xyz.prognoslabs.app.model.ChartResponse
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.MarketsResponse
import xyz.prognoslabs.app.model.TickerAsset
import xyz.prognoslabs.app.model.TickerResponse

/**
 * v1.1.2: the app was slow because every screen waited on the network
 * from scratch. The markets endpoint takes ~7s server-side, and the
 * old refresh ran markets, ticker, watchdog and learning one after
 * another, so the home screen showed a spinner for 10+ seconds on
 * every start. These caches fix that: the last real data is shown
 * instantly (memory first, then disk), and the network only refreshes
 * it in the background. Nothing here invents data: a cache entry is
 * always a real server reply, stored verbatim.
 */

/** Markets are refreshed at most once per auto-refresh tick. */
const val MARKETS_CACHE_TTL_MS = 30_000L
/** The price strip is light (one small call), so it ticks faster. */
const val TICKER_CACHE_TTL_MS = 10_000L

private val cacheGson = Gson()

/** Markets reply as JSON, for the disk cache. Null when unreadable. */
fun marketsResponseToJson(response: MarketsResponse): String = cacheGson.toJson(response)

fun marketsResponseFromJson(json: String?): MarketsResponse? {
    if (json.isNullOrBlank()) return null
    return try { cacheGson.fromJson(json, MarketsResponse::class.java) } catch (e: Exception) { null }
}

/** Ticker reply as JSON, for the disk cache. Null when unreadable. */
fun tickerResponseToJson(response: TickerResponse): String = cacheGson.toJson(response)

fun tickerResponseFromJson(json: String?): TickerResponse? {
    if (json.isNullOrBlank()) return null
    return try { cacheGson.fromJson(json, TickerResponse::class.java) } catch (e: Exception) { null }
}

/**
 * In-memory cache for the two lists the home screen needs. `now` is
 * injectable so unit tests can move time without sleeping. A value is
 * "fresh" inside its TTL; an older value is still returned as "stale"
 * so the screen can show the last real data while a refresh runs.
 *
 * v1.1.8: the methods are synchronized. The disk and parse work moved
 * off the main thread, so the 30s refresh and the 10s ticker tick can
 * now touch this cache from different threads at once.
 */
class MemoryMarketCache(private val now: () -> Long = { System.currentTimeMillis() }) {
    private var markets: List<Market>? = null
    private var marketsAtMs: Long = 0L
    private var ticker: List<TickerAsset>? = null
    private var tickerAtMs: Long = 0L

    @Synchronized
    fun putMarkets(items: List<Market>) {
        markets = items
        marketsAtMs = now()
    }

    @Synchronized
    fun putTicker(items: List<TickerAsset>) {
        ticker = items
        tickerAtMs = now()
    }

    /** Fresh markets, or null when none have been cached or they expired. */
    @Synchronized
    fun freshMarkets(): List<Market>? {
        val m = markets ?: return null
        return if (now() - marketsAtMs < MARKETS_CACHE_TTL_MS) m else null
    }

    /** Fresh ticker, or null when none has been cached or it expired. */
    @Synchronized
    fun freshTicker(): List<TickerAsset>? {
        val t = ticker ?: return null
        return if (now() - tickerAtMs < TICKER_CACHE_TTL_MS) t else null
    }

    /** Last markets, fresh or stale. Null only when nothing was ever cached. */
    @Synchronized
    fun staleMarkets(): List<Market>? = markets

    /** Last ticker, fresh or stale. Null only when nothing was ever cached. */
    @Synchronized
    fun staleTicker(): List<TickerAsset>? = ticker
}

/** Books are live data: 10 seconds old is still the last real book. */
const val DETAIL_BOOK_CACHE_TTL_MS = 10_000L
/** Charts move slowly: 30 seconds old is still the last real chart. */
const val DETAIL_CHART_CACHE_TTL_MS = 30_000L

/**
 * v1.1.8: in-memory cache for one market's books and chart, so
 * reopening a market shows the last real data at once instead of a
 * spinner. Only the first paint of the detail screen reads it; the
 * 5-second live polling always fetches fresh, so ticking stays live.
 * `now` is injectable so unit tests can move time without sleeping.
 */
class DetailCache(private val now: () -> Long = { System.currentTimeMillis() }) {
    private data class BookEntry(val book: BookResponse, val atMs: Long)
    private data class ChartEntry(val chart: ChartResponse, val atMs: Long)
    private val books = mutableMapOf<String, BookEntry>()
    private val charts = mutableMapOf<String, ChartEntry>()

    @Synchronized
    fun putBook(tokenId: String, book: BookResponse) {
        books[tokenId] = BookEntry(book, now())
    }

    /** Fresh book for a token, or null when none was cached or it expired. */
    @Synchronized
    fun book(tokenId: String): BookResponse? {
        val e = books[tokenId] ?: return null
        return if (now() - e.atMs < DETAIL_BOOK_CACHE_TTL_MS) e.book else null
    }

    @Synchronized
    fun putChart(marketId: String, chart: ChartResponse) {
        charts[marketId] = ChartEntry(chart, now())
    }

    /** Fresh chart for a market, or null when none was cached or it expired. */
    @Synchronized
    fun chart(marketId: String): ChartResponse? {
        val e = charts[marketId] ?: return null
        return if (now() - e.atMs < DETAIL_CHART_CACHE_TTL_MS) e.chart else null
    }
}
