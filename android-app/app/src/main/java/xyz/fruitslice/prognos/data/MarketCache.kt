package xyz.fruitslice.prognos.data

import com.google.gson.Gson
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.MarketsResponse
import xyz.fruitslice.prognos.model.TickerAsset
import xyz.fruitslice.prognos.model.TickerResponse

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
 */
class MemoryMarketCache(private val now: () -> Long = { System.currentTimeMillis() }) {
    private var markets: List<Market>? = null
    private var marketsAtMs: Long = 0L
    private var ticker: List<TickerAsset>? = null
    private var tickerAtMs: Long = 0L

    fun putMarkets(items: List<Market>) {
        markets = items
        marketsAtMs = now()
    }

    fun putTicker(items: List<TickerAsset>) {
        ticker = items
        tickerAtMs = now()
    }

    /** Fresh markets, or null when none have been cached or they expired. */
    fun freshMarkets(): List<Market>? {
        val m = markets ?: return null
        return if (now() - marketsAtMs < MARKETS_CACHE_TTL_MS) m else null
    }

    /** Fresh ticker, or null when none has been cached or it expired. */
    fun freshTicker(): List<TickerAsset>? {
        val t = ticker ?: return null
        return if (now() - tickerAtMs < TICKER_CACHE_TTL_MS) t else null
    }

    /** Last markets, fresh or stale. Null only when nothing was ever cached. */
    fun staleMarkets(): List<Market>? = markets

    /** Last ticker, fresh or stale. Null only when nothing was ever cached. */
    fun staleTicker(): List<TickerAsset>? = ticker
}
