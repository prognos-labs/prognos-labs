package xyz.fruitslice.prognos.model

import com.google.gson.annotations.SerializedName
import xyz.fruitslice.prognos.R

fun parseNum(v: Any?): Double? {
    if (v == null) return null
    if (v is Number) return v.toDouble()
    val s = v.toString().trim()
    if (s.isEmpty()) return null
    return s.toDoubleOrNull()
}

data class LangText(
    @SerializedName("en") val en: String? = null
)

data class TagDto(
    val slug: String? = null,
    val label: LangText? = null
)

data class OutcomeDto(
    val outcome: LangText? = null,
    @SerializedName("token_id") val tokenId: String? = null
)

data class MarketDto(
    @SerializedName("market_id") val marketId: String? = null,
    val question: LangText? = null,
    val status: String? = null,
    val closed: Boolean? = null,
    @SerializedName("start_date") val startDate: Long? = null,
    @SerializedName("end_date") val endDate: Long? = null,
    val volume: Any? = null,
    @SerializedName("volume_24hr") val volume24hr: Any? = null,
    @SerializedName("best_bid") val bestBid: Any? = null,
    @SerializedName("best_ask") val bestAsk: Any? = null,
    @SerializedName("last_trade_price") val lastTradePrice: Any? = null,
    val outcomes: List<OutcomeDto>? = null,
    val tags: List<TagDto>? = null,
    val description: LangText? = null,
    @SerializedName("resolution_source") val resolutionSource: String? = null,
    @SerializedName("winning_outcome") val winningOutcome: String? = null
)

data class MarketsResponse(val items: List<MarketDto>? = null)

data class TickerAsset(
    val asset: String,
    val label: String? = null,
    val price: Double? = null,
    val chg24: Double? = null
)

data class TickerResponse(val assets: List<TickerAsset>? = null)

data class BookEntry(val price: String? = null, val size: String? = null)

data class BookResponse(
    val bids: List<BookEntry>? = null,
    val asks: List<BookEntry>? = null,
    @SerializedName("min_order_size") val minOrderSize: String? = null,
    @SerializedName("tick_size") val tickSize: String? = null
)

data class ChartResponse(
    val available: Boolean? = null,
    @SerializedName("market_id") val marketId: String? = null,
    val asset: String? = null,
    @SerializedName("window_secs") val windowSecs: Long? = null,
    @SerializedName("win_start") val winStart: Long? = null,
    @SerializedName("win_end") val winEnd: Long? = null,
    val started: Boolean? = null,
    val ended: Boolean? = null,
    val line: Double? = null,
    val current: Double? = null,
    val average: Double? = null,
    val points: List<List<Double>>? = null
)

data class NewsItem(
    val title: String? = null,
    val link: String? = null,
    val time: String? = null,
    val source: String? = null,
    val tags: List<String>? = null
)

/** Mood from the news payload: how many stories in the last hour read
 *  positive vs negative. Nulls mean the feed did not report a mood. */
data class NewsMood(
    val scanned: Int? = null,
    val positive: Int? = null,
    val negative: Int? = null
)

data class NewsResponse(val items: List<NewsItem>? = null, val mood: NewsMood? = null)

/** News plus mood, as the forecast reads it. */
data class NewsFeed(val items: List<NewsItem> = emptyList(), val mood: NewsMood? = null)

data class WatchFlag(
    @SerializedName("market_id") val marketId: String? = null,
    val kind: String? = null,
    val reason: String? = null
)

data class WatchdogResponse(
    val flags: List<WatchFlag>? = null,
    val checked: Int? = null
)

data class LearningResponse(
    val champion: String? = null,
    val scored: Int? = null,
    val correct: Int? = null,
    @SerializedName("samples_needed") val samplesNeeded: Int? = null
)

val ASSET_LABELS = mapOf(
    "BTC" to "Bitcoin", "ETH" to "Ethereum", "XRP" to "XRP", "SOL" to "Solana",
    "DOGE" to "Dogecoin", "BNB" to "BNB", "HYPE" to "Hyperliquid", "ZEC" to "ZCash"
)

/**
 * v1.1.4: the real official logo for each listed asset, bundled in
 * res/drawable (no network loading). Null for an asset with no logo,
 * so the UI falls back to the letter circle instead of inventing one.
 */
val ASSET_LOGOS = mapOf(
    "BTC" to R.drawable.ic_asset_btc,
    "ETH" to R.drawable.ic_asset_eth,
    "XRP" to R.drawable.ic_asset_xrp,
    "SOL" to R.drawable.ic_asset_sol,
    "DOGE" to R.drawable.ic_asset_doge,
    "BNB" to R.drawable.ic_asset_bnb,
    "HYPE" to R.drawable.ic_asset_hype,
    "ZEC" to R.drawable.ic_asset_zec
)

/** The bundled logo drawable for an asset code, or null when there is none. */
fun assetLogoRes(code: String?): Int? = code?.let { ASSET_LOGOS[it] }

val SECTOR_COLORS = mapOf(
    "BTC" to 0xFFB08A00, "ETH" to 0xFF5E548E, "XRP" to 0xFF2F6690, "SOL" to 0xFF3D7A5E,
    "DOGE" to 0xFFA9713F, "BNB" to 0xFF7A7D2B, "HYPE" to 0xFF9C5B7A, "ZEC" to 0xFF5F7A8A
)

private val ASSET_SLUGS = mapOf(
    "bitcoin" to "BTC", "btc" to "BTC", "ethereum" to "ETH", "eth" to "ETH",
    "ether" to "ETH", "xrp" to "XRP", "ripple" to "XRP", "solana" to "SOL",
    "sol" to "SOL", "dogecoin" to "DOGE", "doge" to "DOGE", "bnb" to "BNB",
    "binance" to "BNB", "hyperliquid" to "HYPE", "hype" to "HYPE",
    "zcash" to "ZEC", "zec" to "ZEC"
)

data class Outcome(val label: String, val tokenId: String)

data class Market(
    val id: String,
    val question: String,
    val status: String,
    val closed: Boolean,
    val start: Long,
    val end: Long,
    val volume: Any?,
    val vol24: Any?,
    val last: Double?,
    val bid: Double?,
    val ask: Double?,
    val p0: Double?,
    val p1: Double?,
    val outcomes: List<Outcome>,
    val tags: List<TagDto>,
    val desc: String,
    val winOutcome: String?
) {
    val asset: String? get() {
        for (t in tags) {
            val s = (t.slug ?: "").lowercase()
            if (ASSET_SLUGS.containsKey(s)) return ASSET_SLUGS[s]
        }
        val q = question.lowercase()
        val names = listOf(
            "bitcoin" to "BTC", "ethereum" to "ETH", "ether" to "ETH", "xrp" to "XRP",
            "ripple" to "XRP", "solana" to "SOL", "dogecoin" to "DOGE", "doge" to "DOGE",
            "bnb" to "BNB", "hyperliquid" to "HYPE", "zcash" to "ZEC"
        )
        for ((name, code) in names) if (q.contains(name)) return code
        return null
    }
    val sectorKey: String get() = asset ?: "more"
    val sectorLabel: String get() = asset?.let { ASSET_LABELS[it] ?: it } ?: "More markets"
    val sectorColor: Long get() = SECTOR_COLORS[asset]?.toLong() ?: 0xFF6E6A61
    val isResolved: Boolean get() = !winOutcome.isNullOrBlank()
    fun statusLabel(nowSec: Long): String {
        if (isResolved) return "RESOLVED - ${winOutcome!!.uppercase()}"
        if (closed || (end > 0 && nowSec >= end)) return "CLOSED - AWAITING RESOLUTION"
        return "OPEN"
    }
}

fun MarketDto.toMarket(): Market? {
    val id = marketId ?: return null
    val q = question?.en ?: return null
    if (q.isBlank()) return null
    val bid = parseNum(bestBid)
    val ask = parseNum(bestAsk)
    val last = parseNum(lastTradePrice)
    val p0 = when {
        bid != null && ask != null -> (bid + ask) / 2.0
        bid != null -> bid
        ask != null -> ask
        last != null && last > 0 -> last
        else -> null
    }
    val outs = (outcomes ?: emptyList()).map {
        Outcome(it.outcome?.en ?: "Outcome", it.tokenId ?: "")
    }.toMutableList()
    while (outs.size < 2) outs.add(Outcome(if (outs.isEmpty()) "Yes" else "No", ""))
    return Market(
        id = id, question = q.take(300), status = status ?: "", closed = closed ?: false,
        start = startDate ?: 0L, end = endDate ?: 0L, volume = volume, vol24 = volume24hr,
        last = last, bid = bid, ask = ask, p0 = p0, p1 = p0?.let { 1.0 - it },
        outcomes = outs.take(2), tags = tags ?: emptyList(),
        desc = description?.en ?: "", winOutcome = winningOutcome
    )
}

/**
 * v1.1.2: the reverse of [toMarket], for the disk cache. A cached
 * market round-trips through its DTO so the stored JSON is a real
 * server-shaped reply. p0/p1 are derived from bid/ask/last on load,
 * so they are not stored.
 */
fun Market.toDto(): MarketDto = MarketDto(
    marketId = id,
    question = LangText(question),
    status = status,
    closed = closed,
    startDate = start,
    endDate = end,
    volume = volume,
    volume24hr = vol24,
    bestBid = bid,
    bestAsk = ask,
    lastTradePrice = last,
    outcomes = outcomes.map { OutcomeDto(LangText(it.label), it.tokenId) },
    tags = tags,
    description = LangText(desc),
    winningOutcome = winOutcome
)
