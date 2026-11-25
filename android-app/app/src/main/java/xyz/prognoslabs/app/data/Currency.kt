package xyz.prognoslabs.app.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Display currency for balances. All money in the app is stored in USD; a
 * non-USD choice only changes how a figure is shown, never its value.
 * Rates come from the live FX feed (open.er-api.com, free, no key) and are
 * cached for at most an hour. The app never hardcodes an exchange rate.
 */
enum class DisplayCurrency(val code: String, val symbol: String, val label: String) {
    USD("USD", "$", "US Dollar (USD)"),
    EUR("EUR", "\u20AC", "Euro (EUR)"),
    GBP("GBP", "\u00A3", "British Pound (GBP)"),
    NGN("NGN", "\u20A6", "Nigerian Naira (NGN)");

    companion object {
        fun fromCode(code: String?): DisplayCurrency =
            entries.firstOrNull { it.code == code } ?: USD
    }
}

/** Live FX rates against USD, plus when they were fetched. */
data class FxRates(val base: String, val fetchedAtMs: Long, val rates: Map<String, Double>) {
    fun rateFor(code: String): Double? = if (code == "USD") 1.0 else rates[code]
}

const val FX_RATES_URL = "https://open.er-api.com/v6/latest/USD"
const val FX_RATES_MAX_AGE_MS = 3_600_000L // 1 hour; the feed itself updates daily

/** Parse the exchangerate-api JSON into [FxRates]. Null on any bad shape. */
fun parseFxRates(json: String, fetchedAtMs: Long): FxRates? {
    // v1.1.2: parsed with Gson, not org.json: android.jar ships
    // org.json as stubs that throw at runtime, so the old parser
    // returned null for every payload (and the live app fell back
    // to USD on every currency). Gson is a real parser everywhere.
    return try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("result")?.asString != "success") return null
        if (root.get("base_code")?.asString != "USD") return null
        val ratesObj = try { root.getAsJsonObject("rates") } catch (e: Exception) { null } ?: return null
        val rates = mutableMapOf<String, Double>()
        for ((k, v) in ratesObj.entrySet()) {
            if (!v.isJsonPrimitive || !v.asJsonPrimitive.isNumber) continue
            val d = v.asDouble
            if (d.isFinite() && d > 0) rates[k] = d
        }
        if (rates.isEmpty()) return null
        FxRates(base = "USD", fetchedAtMs = fetchedAtMs, rates = rates)
    } catch (e: Exception) {
        null
    }
}

/** Fetch the raw rates JSON. Returns null on any failure; the caller keeps USD. */
suspend fun fetchFxRatesJson(client: OkHttpClient): String? {
    return try {
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(FX_RATES_URL).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }
    } catch (e: Exception) {
        null
    }
}

/** Convert a USD amount to the display currency. Null if the rate is unknown. */
fun convertUsd(amountUsd: Double, currency: DisplayCurrency, rates: FxRates?): Double? {
    if (currency == DisplayCurrency.USD) return amountUsd
    val r = rates?.rateFor(currency.code) ?: return null
    return amountUsd * r
}

/**
 * Format a USD amount in the display currency. Falls back to USD when the
 * rate is not available, so the app never shows a converted figure it
 * cannot compute.
 */
fun fmtMoney(amountUsd: Double?, currency: DisplayCurrency, rates: FxRates?): String {
    if (amountUsd == null) return "-"
    if (currency == DisplayCurrency.USD) return fmtUsd(amountUsd)
    val converted = convertUsd(amountUsd, currency, rates) ?: return fmtUsd(amountUsd)
    return currency.symbol + String.format(Locale.US, "%,.2f", converted)
}

/**
 * BTC equivalent of a USD balance at the live BTC price, e.g. "≈ 0.1620 BTC".
 * Null when the balance or the live price is not known. The price comes
 * from the app's own market ticker, so it moves when the ticker refreshes.
 */
fun btcEquivalentText(balanceUsd: Double?, btcPriceUsd: Double?): String? {
    if (balanceUsd == null || btcPriceUsd == null || btcPriceUsd <= 0) return null
    val btc = balanceUsd / btcPriceUsd
    return "\u2248 " + String.format(Locale.US, "%.4f", btc) + " BTC"
}

/** How long the "Send a new code" link stays disabled after a code is sent. */
const val RESEND_COOLDOWN_SECONDS = 60L

/** Seconds left on the resend countdown. 0 when a new code may be asked for. */
fun resendSecondsLeft(nowMs: Long, lastSentAtMs: Long): Long {
    if (lastSentAtMs <= 0) return 0
    val elapsed = (nowMs - lastSentAtMs) / 1000
    return (RESEND_COOLDOWN_SECONDS - elapsed).coerceAtLeast(0)
}

/** Countdown label, e.g. "Send a new code in 0:45". */
fun formatResendCountdown(secondsLeft: Long): String {
    val m = secondsLeft / 60
    val s = secondsLeft % 60
    return "Send a new code in $m:${s.toString().padStart(2, '0')}"
}
