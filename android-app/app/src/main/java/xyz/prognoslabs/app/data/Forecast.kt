package xyz.prognoslabs.app.data

import xyz.prognoslabs.app.model.ASSET_LABELS
import xyz.prognoslabs.app.model.BookResponse
import xyz.prognoslabs.app.model.ChartResponse
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.NewsFeed
import xyz.prognoslabs.app.model.NewsItem
import xyz.prognoslabs.app.model.NewsMood
import xyz.prognoslabs.app.model.TickerAsset
import xyz.prognoslabs.app.model.parseNum
import kotlin.math.roundToLong

data class Forecast(
    val verdict: String,
    val reason: String,
    val basis: String,
    val p: Double,
    val pRaw: Double
)

/**
 * Everything the forecast reads beyond the market itself. All of it is
 * optional: when a piece is missing, that part of the note is left out
 * instead of invented. The home screen passes the ticker and the news;
 * the market screen adds both books and the chart.
 */
data class ForecastContext(
    val chg24: Double? = null,
    val news: List<NewsItem> = emptyList(),
    val mood: NewsMood? = null,
    val bookUp: BookResponse? = null,
    val bookDown: BookResponse? = null,
    val chartPoints: List<List<Double>>? = null
)

/** Builds the forecast context for one market from the live screen state. */
fun buildForecastContext(
    market: Market,
    ticker: List<TickerAsset>,
    news: NewsFeed,
    bookUp: BookResponse?,
    bookDown: BookResponse?,
    chart: ChartResponse?
): ForecastContext {
    val chg = ticker.firstOrNull { it.asset == market.asset }?.chg24
    return ForecastContext(
        chg24 = chg,
        news = news.items,
        mood = news.mood,
        bookUp = bookUp,
        bookDown = bookDown,
        chartPoints = chart?.points
    )
}

fun fmtPct(p: Double?): String {
    if (p == null) return "-"
    val v = p * 100
    val s = String.format("%.1f", v)
    return s.removeSuffix(".0") + "%"
}

fun fmtPrice(p: Double?): String {
    if (p == null) return "-"
    return String.format("%.2f", p)
}

fun fmtVol(v: Any?): String {
    val n = parseNum(v) ?: return "-"
    if (n == 0.0) return "0"
    return String.format("%,.0f", n)
}

fun fmtTickerPrice(v: Double?): String {
    if (v == null) return "-"
    if (v >= 1000) return "$" + String.format("%,.0f", v)
    return "$" + String.format("%,.2f", v)
}

fun fmtUsd(v: Double?): String {
    if (v == null) return "-"
    return "$" + String.format("%,.2f", v)
}

fun timeLeft(secs: Long): String {
    if (secs <= 0) return "no time, it has closed"
    val d = secs / 86400
    val h = (secs % 86400) / 3600
    val m = (secs % 3600) / 60
    if (d >= 2) return "$d days"
    if (d == 1L) return "1 day and $h ${if (h == 1L) "hour" else "hours"}"
    if (h >= 1) return "$h ${if (h == 1L) "hour" else "hours"} and $m ${if (m == 1L) "minute" else "minutes"}"
    if (m >= 1) return "$m ${if (m == 1L) "minute" else "minutes"}"
    return "under a minute"
}

fun countdownText(diffSec: Long): String {
    if (diffSec <= 0) return "CLOSED - AWAITING RESOLUTION"
    val d = diffSec / 86400
    val h = (diffSec % 86400) / 3600
    val m = (diffSec % 3600) / 60
    val s = diffSec % 60
    return "CLOSES IN " + (if (d > 0) "${d}d " else "") + "${h}h ${m}m ${s}s"
}

/**
 * The forecast is a short analyst note, not a pick. It reads the live
 * price move, the headlines, the mood, the order book and the time left,
 * and says what the market looks like and what comes next. It never says
 * which side to pick. A liquidity wall is only named when it is big
 * enough to matter (at least $1,000 at one price level); a thin book is
 * skipped, never dressed up as a wall. The probability p is still
 * returned for scoring; only the words changed.
 *
 * Same rule as the website forecastFor in index.html. No price, no
 * forecast. The verdict is a label for the market, never a side:
 * Moving (a sharp move), One-sided (a real wall that dominates),
 * Crowded (heavy volume), Tense (near even under pressure), Quiet,
 * Closed. When a source is missing, that part is left out.
 */
private const val WALL_MIN_USD = 1000.0
private const val WALL_NEAR = 0.05

fun fmtWallUsd(v: Double): String {
    if (v >= 10000.0) return "$" + (v / 1000.0).roundToLong() + "k"
    return "$" + String.format("%,d", v.roundToLong())
}

fun fmtMovePct(v: Double): String {
    var s = String.format("%.1f", Math.abs(v) * 100)
    if (s.endsWith(".0")) s = s.dropLast(2)
    return "$s%"
}

/** Topic of one headline, from its title. Only clear keywords count;
 *  anything else returns null so the forecast never invents a story. */
fun newsTopic(title: String?): String? {
    if (title.isNullOrBlank()) return null
    val t = title.lowercase()
    if ("hack" in t) return "a hack"
    if ("outflow" in t) return if ("record" in t) "record outflows" else "outflows"
    if ("loses" in t && "record" in t && ("etf" in t || "ibit" in t || "fund" in t)) return "record outflows"
    if ("etf" in t) return "ETF flows"
    if ("sec" in t || "fraud" in t || "charges" in t) return "regulatory action"
    if ("ipo" in t) return "a big listing"
    if ("death cross" in t || "retest" in t || "chart pattern" in t) return "chart watchers flagging a pattern"
    if ("rally" in t || "surge" in t || "soars" in t) return "a sharp rally"
    if ("crash" in t || "plunge" in t || "tumble" in t) return "a sharp drop"
    if ("halving" in t) return "the halving"
    if ("upgrade" in t) return "an upgrade"
    if ("fork" in t) return "a fork"
    return null
}

private val ASSET_TITLE_WORDS = mapOf(
    "BTC" to listOf("bitcoin", "btc", "ibit"),
    "ETH" to listOf("ethereum", "ether", "eth"),
    "XRP" to listOf("xrp", "ripple"),
    "SOL" to listOf("solana", "sol"),
    "DOGE" to listOf("dogecoin", "doge"),
    "BNB" to listOf("bnb", "binance"),
    "HYPE" to listOf("hyperliquid"),
    "ZEC" to listOf("zcash", "zec")
)

/** A headline counts for an asset when it is tagged with it, or when
 *  its title names the asset (the feed tags by keyword, so a story
 *  about a Bitcoin fund that only says IBIT still counts for Bitcoin). */
fun headlineForAsset(item: NewsItem, asset: String): Boolean {
    if ((item.tags ?: emptyList()).contains(asset)) return true
    val words = ASSET_TITLE_WORDS[asset] ?: return false
    val title = item.title ?: return false
    return words.any { w -> Regex("\\b" + Regex.escape(w) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(title) }
}

/** Latest news topics for one asset, at most two, no repeats. */
fun newsTopicsFor(asset: String?, items: List<NewsItem>): List<String> {
    if (asset == null) return emptyList()
    val out = mutableListOf<String>()
    for (item in items) {
        if (out.size >= 2) break
        if (!headlineForAsset(item, asset)) continue
        val topic = newsTopic(item.title) ?: continue
        if (topic !in out) out.add(topic)
    }
    return out
}

/** Mood from the news payload: heavy when more stories read negative
 *  than positive, upbeat when the reverse. Null when unknown. */
fun newsMood(mood: NewsMood?): String? {
    if (mood == null) return null
    val pos = mood.positive ?: return null
    val neg = mood.negative ?: return null
    if (neg > pos) return "heavy"
    if (pos > neg) return "upbeat"
    return null
}

/** Price move over the chart window, as a fraction. Null when the
 *  chart has fewer than two points or starts at zero. */
fun chartMove(points: List<List<Double>>?): Double? {
    if (points == null || points.size < 2) return null
    val first = points.first().getOrNull(1) ?: return null
    val last = points.last().getOrNull(1) ?: return null
    if (first <= 0) return null
    return (last - first) / first
}

private data class WallLevel(val value: Double, val price: Double)

/** Largest near-price level on each side of one token book, in dollars.
 *  Near-price means within 0.05 of the market mid price. Far-out levels
 *  (0.01, 0.99) are not walls, they are just quotes sitting far away. */
private fun bookWalls(book: BookResponse?, p: Double): Pair<WallLevel?, WallLevel?>? {
    if (book == null) return null
    fun best(list: List<xyz.prognoslabs.app.model.BookEntry>?): WallLevel? {
        var best: WallLevel? = null
        for (e in list ?: emptyList()) {
            val pr = parseNum(e.price) ?: continue
            val sz = parseNum(e.size) ?: continue
            if (kotlin.math.abs(pr - p) > WALL_NEAR) continue
            val v = pr * sz
            if (best == null || v > best.value) best = WallLevel(v, pr)
        }
        return best
    }
    return best(book.bids) to best(book.asks)
}

/**
 * The price the forecast reads. The market's own outcome price when
 * it has one; otherwise the latest price the market page already
 * shows, so the forecast never claims there is no price while the
 * price and the chart sit right above it. The chart's last point is
 * the market's latest tick; failing that, the middle of the best
 * bid and ask in the order book. All three are outcome prices
 * between 0 and 1. A price outside that range is not a market
 * price (the asset price is dollars, not a probability) and is
 * ignored. Null only when nothing anywhere has a price.
 */
fun forecastPrice(m: Market, ctx: ForecastContext?): Double? {
    m.p0?.let { if (it in 0.0..1.0) return it }
    ctx?.chartPoints?.lastOrNull()?.getOrNull(1)
        ?.takeIf { it in 0.0..1.0 }?.let { return it }
    val bid = ctx?.bookUp?.bids?.firstOrNull()?.price?.let { parseNum(it) }
    val ask = ctx?.bookDown?.asks?.firstOrNull()?.price?.let { parseNum(it) }
    if (bid != null && ask != null && bid in 0.0..1.0 && ask in 0.0..1.0) {
        return (bid + ask) / 2.0
    }
    return null
}

fun forecastFor(m: Market, nowSec: Long = System.currentTimeMillis() / 1000, ctx: ForecastContext? = null): Forecast? {
    val p = forecastPrice(m, ctx) ?: return null
    val minsLeft: Long? = if (m.end > 0) maxOf(0L, ((m.end - nowSec) / 60)) else null
    val spread: Double? = if (m.bid != null && m.ask != null) m.ask - m.bid else null
    val vol24 = parseNum(m.vol24)
    val fp = p
    val asset = m.asset
    val chg = ctx?.chg24
    val chartMove = if (chg == null) chartMove(ctx?.chartPoints) else null
    val movePct = chg ?: chartMove
    val moveSpan = if (chg != null) "on the day" else "over the last hour"
    val mood = newsMood(ctx?.mood)
    val topics = newsTopicsFor(asset, ctx?.news ?: emptyList())
    val walls = bookWalls(ctx?.bookUp, p)
    var wallBid: WallLevel? = null
    var wallAsk: WallLevel? = null
    if (walls != null) {
        if ((walls.first?.value ?: 0.0) >= WALL_MIN_USD) wallBid = walls.first
        if ((walls.second?.value ?: 0.0) >= WALL_MIN_USD) wallAsk = walls.second
    }
    val oneSided = (wallBid != null && (wallAsk == null || wallBid.value >= 2 * wallAsk.value)) ||
        (wallAsk != null && (wallBid == null || wallAsk.value >= 2 * wallBid.value))
    val verdict = when {
        minsLeft != null && minsLeft <= 0 -> "Closed"
        movePct != null && kotlin.math.abs(movePct) >= 0.03 -> "Moving"
        oneSided -> "One-sided"
        vol24 != null && vol24 >= 10000.0 -> "Crowded"
        p in 0.45..0.55 && ((movePct != null && movePct <= -0.01) || mood == "heavy") -> "Tense"
        else -> "Quiet"
    }
    val assetName = asset?.let { ASSET_LABELS[it] ?: it }
    val sentences = mutableListOf<String>()
    if (assetName != null && movePct != null) {
        when {
            movePct <= -0.01 -> sentences.add("$assetName is under pressure.")
            movePct >= 0.01 -> sentences.add("$assetName is climbing.")
            else -> sentences.add("$assetName is holding steady.")
        }
    }
    val parts = mutableListOf<String>()
    if (movePct != null) {
        when {
            movePct <= -0.01 -> parts.add("it has been sliding, down about ${fmtMovePct(movePct)} $moveSpan")
            movePct >= 0.01 -> parts.add("it has been climbing, up about ${fmtMovePct(movePct)} $moveSpan")
            else -> parts.add("it has barely moved $moveSpan")
        }
    }
    if (mood == "heavy") parts.add("the mood in the headlines is heavy")
    else if (mood == "upbeat") parts.add("the mood in the headlines is upbeat")
    if (parts.isNotEmpty()) {
        var s2 = parts[0].replaceFirstChar { it.uppercase() }
        for (i in 1 until parts.size) s2 += ", and " + parts[i]
        if (topics.isNotEmpty()) s2 += ", with " + topics.joinToString(" and ") + " in the news today"
        s2 += "."
        sentences.add(s2)
    } else if (topics.isNotEmpty()) {
        sentences.add("The headlines are talking about " + topics.joinToString(" and ") + " today.")
    }
    val wbits = mutableListOf<String>()
    if (wallBid != null) wbits.add("the buy side is sitting at ${fmtWallUsd(wallBid.value)} in liquidity walls at ${String.format("%.2f", wallBid.price)}")
    if (wallAsk != null) wbits.add("the sell side is sitting at ${fmtWallUsd(wallAsk.value)} in liquidity walls at ${String.format("%.2f", wallAsk.price)}")
    if (wbits.isNotEmpty()) {
        var ws = wbits[0].replaceFirstChar { it.uppercase() }
        if (wbits.size > 1) ws += " and " + wbits[1]
        sentences.add("$ws.")
    }
    if (minsLeft != null && minsLeft <= 0) {
        sentences.clear()
        sentences.add("Time is up, the market has closed.")
    } else {
        val timePart: String? = when {
            minsLeft == null -> null
            minsLeft < 5 -> "under five minutes left"
            minsLeft < 60 -> "about $minsLeft minutes left"
            minsLeft < 1440 -> {
                val hrs = (minsLeft / 60.0).roundToLong()
                "about $hrs ${if (hrs == 1L) "hour" else "hours"} left"
            }
            else -> {
                val dys = (minsLeft / 1440.0).roundToLong()
                "about $dys ${if (dys == 1L) "day" else "days"} left"
            }
        }
        val subj = if (assetName != null) " for $assetName" else ""
        val nextPart = when {
            movePct != null && movePct <= -0.01 -> "what comes next$subj is a test of whether buyers step in at these levels or the slide carries on"
            movePct != null && movePct >= 0.01 -> "what comes next$subj is a test of whether the climb holds or the price gives back its gains"
            else -> "what comes next$subj is a test of which way the market breaks from here"
        }
        if (timePart != null) sentences.add("With $timePart, $nextPart.")
        else sentences.add(nextPart.replaceFirstChar { it.uppercase() } + ".")
    }
    val reason = sentences.joinToString(" ")
    val basis = "Price ${fmtPrice(p)} - spread ${spread?.let { String.format("%.2f", it) } ?: "-"} - " +
        (if (minsLeft == null) "time left -" else if (minsLeft > 0) "${timeLeft(m.end - nowSec)} left" else "closed") +
        " - volume in the last day ${fmtVol(vol24)}"
    return Forecast(verdict, reason, basis, fp, p)
}
