package xyz.prognoslabs.app.data

import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.TickerAsset

/**
 * Pure market-list logic for v3. Kept out of the UI so it can be unit tested.
 * Filters are small chips, not big tabs: All, Gainers, Losers, Watchlist.
 * Gainers and Losers follow the asset's 24h move from the ticker.
 */
enum class MarketFilter { ALL, GAINERS, LOSERS, WATCHLIST }

enum class MarketSort { ENDING_SOON, PRICE_HIGH, PRICE_LOW, MOST_ONE_SIDED }

/** Markets are shown a page at a time, never as one endless list. */
const val PAGE_SIZE = 8

fun pageMarkets(list: List<Market>, shown: Int): List<Market> {
    return list.take(shown.coerceAtLeast(0))
}

fun remainingCount(total: Int, shown: Int): Int {
    return maxOf(0, total - shown)
}

fun marketMatchesQuery(m: Market, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.lowercase()
    val hay = ((m.asset ?: "") + " " + (m.asset?.let { xyz.prognoslabs.app.model.ASSET_LABELS[it] } ?: "") + " " + m.question).lowercase()
    return hay.contains(q)
}

fun filterMarkets(
    markets: List<Market>,
    query: String,
    filter: MarketFilter,
    sector: String,
    watchIds: Set<String>,
    tickerChg: Map<String, Double>
): List<Market> {
    var list = markets
    if (query.isBlank() && sector != "all") {
        list = list.filter { it.sectorKey == sector }
    }
    if (query.isNotBlank()) {
        list = list.filter { marketMatchesQuery(it, query) }
    }
    when (filter) {
        MarketFilter.ALL -> Unit
        MarketFilter.GAINERS -> list = list.filter { (tickerChg[it.asset] ?: 0.0) > 0.0 }
        MarketFilter.LOSERS -> list = list.filter { (tickerChg[it.asset] ?: 0.0) < 0.0 }
        MarketFilter.WATCHLIST -> list = list.filter { watchIds.contains(it.id) }
    }
    return list
}

fun sortMarkets(markets: List<Market>, sort: MarketSort): List<Market> {
    return when (sort) {
        MarketSort.ENDING_SOON -> markets.sortedWith(
            compareBy<Market> { it.end <= 0L }.thenBy { it.end }
        )
        MarketSort.PRICE_HIGH -> markets.sortedByDescending { it.p0 ?: -1.0 }
        MarketSort.PRICE_LOW -> markets.sortedBy { it.p0 ?: 2.0 }
        MarketSort.MOST_ONE_SIDED -> markets.sortedByDescending {
            val p = it.p0 ?: 0.5
            kotlin.math.abs(p - 0.5)
        }
    }
}

fun topMovers(ticker: List<TickerAsset>, count: Int = 4): List<TickerAsset> {
    return ticker.filter { it.chg24 != null }
        .sortedByDescending { kotlin.math.abs(it.chg24 ?: 0.0) }
        .take(count)
}

/**
 * v1.1.14: whether the dashboard shows the full-screen spinner.
 * The ticker lands first (~1.5s) while the markets list can take
 * ~7s server-side, and the top movers come from the ticker. When
 * the ticker is already in, the dashboard shows it at once and the
 * markets load underneath; only a screen with no data at all waits
 * on the network. The cached ticker counts: a cold start with last
 * session's data shows the movers instantly.
 */
fun dashboardShowsSpinner(loading: Boolean, marketsEmpty: Boolean, tickerEmpty: Boolean): Boolean =
    loading && marketsEmpty && tickerEmpty

fun greetingForHour(hour: Int): String {
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}
