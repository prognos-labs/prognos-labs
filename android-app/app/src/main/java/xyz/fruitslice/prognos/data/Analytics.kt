package xyz.fruitslice.prognos.data

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * v1.1.10: Firebase Analytics (Google Analytics) for the Prognos Labs
 * app, so Flint can track installs, signups and in-app activity.
 *
 * What is logged:
 * - Automatic (free from the SDK): first_open (installs), session
 *   starts, screen views, app updates. Nothing to wire in.
 * - sign_up: an account was created and the OTP verified.
 * - wallet_linked: a wallet linked successfully.
 * - voice_order: a voice order was confirmed and placed (or failed).
 * - voice_question: a voice question was answered.
 *
 * Privacy rules, enforced by [sanitizeAnalyticsParams] as well as by
 * the call sites (defence in depth):
 * - Only coarse, non-identifying parameters: market asset code
 *   (BTC, ETH, ...), order side (up/down), success (true/false),
 *   question type, wallet address type (evm/solana), signup method.
 * - NEVER a wallet address, email, name, phone number, voice
 *   recording, voiceprint data, or any trade amount. No user
 *   properties are set. No purchase event exists while trading is
 *   closed; one is added only when trading opens.
 * - The analytics layer never crashes the app: every Firebase call
 *   is guarded, and if Firebase is unavailable (no Play services,
 *   no network, bad config) logging silently becomes a no-op.
 */
object AnalyticsEvents {
    /** Standard Firebase event: an account was created. */
    const val SIGN_UP = "sign_up"
    /** A wallet linked successfully. */
    const val WALLET_LINKED = "wallet_linked"
    /** A voice order was confirmed and placed (or failed). */
    const val VOICE_ORDER = "voice_order"
    /** A voice question was answered. */
    const val VOICE_QUESTION = "voice_question"
    /** A trade-time challenge phrase was matched or not. Coarse
     * only: success true or false, never the phrase itself. */
    const val VOICE_CHALLENGE = "voice_challenge"

    const val PARAM_METHOD = "method"
    const val PARAM_ADDRESS_TYPE = "address_type"
    const val PARAM_SIDE = "side"
    const val PARAM_ASSET = "asset"
    const val PARAM_SUCCESS = "success"
    const val PARAM_TYPE = "type"
}

/** Parameter keys that may ever reach Firebase. Anything else is dropped. */
private val ALLOWED_PARAM_KEYS = setOf(
    AnalyticsEvents.PARAM_METHOD,
    AnalyticsEvents.PARAM_ADDRESS_TYPE,
    AnalyticsEvents.PARAM_SIDE,
    AnalyticsEvents.PARAM_ASSET,
    AnalyticsEvents.PARAM_SUCCESS,
    AnalyticsEvents.PARAM_TYPE
)

/** Parameter keys that must never be logged, even if a call site passes one. */
private val FORBIDDEN_PARAM_KEYS = setOf(
    "email", "name", "phone", "wallet_address", "address", "wallet",
    "amount", "amount_usd", "price", "balance", "token", "session",
    "voiceprint", "recording", "audio", "password", "code", "otp",
    "user_id", "uid", "device_id"
)

private val SAFE_VALUE = Regex("^[A-Za-z0-9_.-]{1,40}$")

/**
 * Keeps only allowed, safe parameters. Drops forbidden keys, unknown
 * keys, values that look like an email or a wallet address, and
 * values with characters outside the safe set. Pure, so unit tests
 * can prove no personal data ever reaches Firebase.
 */
fun sanitizeAnalyticsParams(params: Map<String, String>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for ((key, value) in params) {
        if (key !in ALLOWED_PARAM_KEYS) continue
        if (key in FORBIDDEN_PARAM_KEYS) continue
        val v = value.trim()
        if (!SAFE_VALUE.matches(v)) continue
        if ("@" in v) continue
        if (v.startsWith("0x", ignoreCase = true) && v.length > 10) continue
        out[key] = v
    }
    return out
}

/** Coarse type string for a voice question, for the voice_question event. */
fun voiceQuestionType(q: VoiceQuestion): String = when (q) {
    is VoiceQuestion.Price -> "price"
    VoiceQuestion.Balance -> "balance"
    VoiceQuestion.HowToTrade -> "how_to_trade"
    VoiceQuestion.TradingOpens -> "trading_opens"
    is VoiceQuestion.MarketInfo -> "market_info"
    VoiceQuestion.GreetingHello -> "greeting_hello"
    VoiceQuestion.GreetingHowAreYou -> "greeting_how_are_you"
    VoiceQuestion.CheckIn -> "check_in"
    VoiceQuestion.MostLiquid -> "most_liquid"
    is VoiceQuestion.Knowledge -> "knowledge"
    is VoiceQuestion.News -> "news"
}

/**
 * Parameters for a voice_question event: the question type, plus the
 * asset code where the question names one. Pure, so unit tests can
 * prove the exact event shape.
 */
fun voiceQuestionParams(q: VoiceQuestion): Map<String, String> {
    val params = LinkedHashMap<String, String>()
    params[AnalyticsEvents.PARAM_TYPE] = voiceQuestionType(q)
    val asset = when (q) {
        is VoiceQuestion.Price -> q.asset
        is VoiceQuestion.News -> q.asset
        else -> null
    }
    if (asset != null) params[AnalyticsEvents.PARAM_ASSET] = asset
    return params
}

interface AnalyticsTracker {
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
}

/** Does nothing. Used when Firebase is unavailable, and in unit tests. */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun logEvent(name: String, params: Map<String, String>) = Unit
}

/**
 * Logs to Firebase Analytics. The Firebase instance is fetched lazily
 * and every call is guarded, so a missing or broken Firebase setup
 * degrades to a silent no-op instead of crashing the app.
 */
class FirebaseAnalyticsTracker(context: Context) : AnalyticsTracker {
    private val appContext = context.applicationContext
    private val firebase: FirebaseAnalytics? by lazy {
        runCatching { FirebaseAnalytics.getInstance(appContext) }.getOrNull()
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        val clean = sanitizeAnalyticsParams(params)
        try {
            val bundle = Bundle()
            for ((key, value) in clean) bundle.putString(key, value)
            firebase?.logEvent(name, bundle)
        } catch (t: Throwable) {
            // Analytics must never break the app.
        }
    }
}

/**
 * The app's single analytics entry point. The tracker starts as a
 * no-op and is swapped for the Firebase one by [init] in
 * MainActivity; unit tests swap in a recording fake. Logging never
 * throws, whatever the tracker does.
 */
object Analytics {
    @Volatile
    var tracker: AnalyticsTracker = NoOpAnalyticsTracker
        private set

    fun init(context: Context) {
        tracker = try {
            FirebaseAnalyticsTracker(context)
        } catch (t: Throwable) {
            NoOpAnalyticsTracker
        }
    }

    /** Test-only: replace the tracker with a fake. */
    fun setTrackerForTests(tracker: AnalyticsTracker) {
        this.tracker = tracker
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        try {
            tracker.logEvent(name, params)
        } catch (t: Throwable) {
            // Analytics must never break the app.
        }
    }
}
