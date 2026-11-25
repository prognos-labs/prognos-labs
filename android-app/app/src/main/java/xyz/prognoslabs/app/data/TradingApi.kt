package xyz.prognoslabs.app.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Trading client. Every call goes through api.php on prognoslabs.xyz;
 * the app never talks to the trading service directly and never holds
 * its key. Session tokens come from the wallet link flow only.
 *
 * Contract (api.php, ?action= dispatch):
 * link_nonce (GET) -> {nonce} (single use, 10 minutes).
 * link_wallet (POST {address, message, signature}) -> {linked, address,
 *   session}. message is "Link this wallet to Prognos Labs\nNonce: <nonce>".
 * link_status (GET ?address=) -> {linked, address}.
 * session_status (GET ?address=, X-Prognos-Session header) -> {ok, address}
 *   when the session belongs to that wallet, 403 otherwise. Read-only,
 *   no broker call: this is how the app checks a session handed back in
 *   a redirect before storing it.
 * balance (GET ?address=, X-Prognos-Session header) -> the service reply
 *   passed through.
 * orders (GET ?address=, X-Prognos-Session header) -> the service reply
 *   passed through.
 * positions (GET ?address=, X-Prognos-Session header) -> the service
 *   reply passed through.
 * The session travels in the X-Prognos-Session header on every read, never
 * in the URL query string: a token in a URL ends up in server access logs.
 * place_order (POST {address, session, token_id, side, order_mode,
 *   order_type, total_cost}) -> the service reply passed through;
 *   order id is order_id or data.order_id. side BUY or SELL.
 *
 * Any 401 / 403 / unreachable reply means trading is locked: the UI
 * shows "Trading opens soon", never a raw error and never fake data.
 */

data class LinkNonceResponse(val nonce: String? = null)
data class LinkStatusResponse(val linked: Boolean? = null, val address: String? = null)
data class LinkWalletResponse(val linked: Boolean? = null, val address: String? = null, val session: String? = null)

interface TradingApi {
    @GET("api.php")
    suspend fun linkNonce(@Query("action") action: String = "link_nonce"): Response<LinkNonceResponse>

    @GET("api.php")
    suspend fun linkStatus(
        @Query("action") action: String = "link_status",
        @Query("address") address: String
    ): Response<LinkStatusResponse>

    @POST("api.php")
    suspend fun linkWallet(
        @Query("action") action: String = "link_wallet",
        @Body body: Map<String, String>
    ): Response<LinkWalletResponse>

    @GET("api.php")
    suspend fun sessionStatus(
        @Query("action") action: String = "session_status",
        @Query("address") address: String,
        @Header("X-Prognos-Session") session: String
    ): Response<JsonObject>

    @GET("api.php")
    suspend fun balance(
        @Query("action") action: String = "balance",
        @Query("address") address: String,
        @Header("X-Prognos-Session") session: String
    ): Response<JsonObject>

    @GET("api.php")
    suspend fun orders(
        @Query("action") action: String = "orders",
        @Query("address") address: String,
        @Header("X-Prognos-Session") session: String,
        @Query("limit") limit: Int = 50
    ): Response<JsonObject>

    @GET("api.php")
    suspend fun positions(
        @Query("action") action: String = "positions",
        @Query("address") address: String,
        @Header("X-Prognos-Session") session: String,
        @Query("limit") limit: Int = 50
    ): Response<JsonObject>

    @POST("api.php")
    suspend fun placeOrder(
        @Query("action") action: String = "place_order",
        @Body body: Map<String, Any>
    ): Response<JsonObject>
}

fun createTradingApi(baseUrl: String = AppConfig.TRADING_BASE_URL): TradingApi {
    // v1.1.2: 8s connect / 10s read (was 20s / 30s). Same reason as
    // the market client: fail fast, let the cache cover the gap.
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    val root = baseUrl.substringBeforeLast("/") + "/"
    return Retrofit.Builder()
        .baseUrl(root)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TradingApi::class.java)
}

/** Which side of a market the trade panel has picked. */
enum class TradeSide { UP, DOWN }

/**
 * The colour a side button shows. Buy Up is green, Sell Down is red,
 * and the colour stays on the side that was tapped until the other
 * side is tapped. Unselected sides show no colour.
 */
fun tradeSideColour(selected: TradeSide?, side: TradeSide): String {
    if (selected != side) return "none"
    return if (side == TradeSide.UP) "green" else "red"
}

/** Picking a side keeps it picked; the other side loses its colour. */
fun selectTradeSide(current: TradeSide?, tapped: TradeSide): TradeSide = tapped

/** Client-side order check. Returns a plain error, or null when fine. */
fun orderAmountError(amount: String): String? {
    val raw = amount.trim()
    if (raw.isEmpty()) return "Enter an amount."
    val v = raw.toDoubleOrNull() ?: return "Enter an amount in numbers only."
    if (v <= 0) return "Enter an amount above zero."
    val decimals = raw.substringAfter(".", "").length
    if (raw.contains(".") && decimals > 6) return "Enter at most 6 decimal places."
    return null
}

/** Body for a market order: buy one side for a USDT amount. Both the
 *  Buy Up and the Sell Down buttons buy the chosen outcome's token
 *  (buying the Down token is the down bet), exactly as the website
 *  trade panel does: side is always BUY, order_mode MARKET. */
fun buildPlaceOrderBody(
    address: String,
    session: String,
    tokenId: String,
    side: TradeSide,
    totalCost: String
): Map<String, Any> = mapOf(
    "address" to address,
    "session" to session,
    "token_id" to tokenId,
    "side" to "BUY",
    "order_mode" to "MARKET",
    "order_type" to "GTC",
    "total_cost" to totalCost
)

/** One open position, read leniently: the service reply shape is not fixed. */
data class PositionItem(
    val label: String,
    val detail: String,
    val quantity: String?
)

data class OrderItem(
    val label: String,
    val detail: String
)

fun jsonString(obj: JsonObject, vararg keys: String): String? {
    for (k in keys) {
        val el = obj.get(k) ?: continue
        if (el.isJsonPrimitive) {
            val s = el.asString.trim()
            if (s.isNotEmpty()) return s
        }
    }
    return null
}

fun jsonNumber(obj: JsonObject, vararg keys: String): Double? {
    for (k in keys) {
        val el = obj.get(k) ?: continue
        if (el.isJsonPrimitive) {
            try {
                val v = el.asDouble
                if (v.isFinite()) return v
            } catch (e: Exception) {
            }
        }
    }
    return null
}

/** Finds the first list of objects under any of the usual wrapper keys. */
fun jsonObjectList(root: JsonObject, vararg keys: String): List<JsonObject> {
    for (k in keys) {
        val el = root.get(k) ?: continue
        if (el.isJsonArray) {
            return el.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
        }
    }
    return emptyList()
}

/** Balance in USDT from a passed-through balance reply, or null. */
fun balanceFromJson(root: JsonObject?): Double? {
    if (root == null) return null
    val direct = jsonNumber(root, "available", "available_balance", "balance", "total", "usdt", "amount")
    if (direct != null) return direct
    val data = root.get("data")
    if (data != null && data.isJsonObject) {
        return jsonNumber(data.asJsonObject, "available", "available_balance", "balance", "total", "usdt", "amount")
    }
    return null
}

fun positionsFromJson(root: JsonObject?): List<PositionItem> {
    if (root == null) return emptyList()
    val items = jsonObjectList(root, "positions", "data", "items")
    return items.map { p ->
        val qty = jsonString(p, "quantity", "size")
        val tok = jsonString(p, "token_id", "tokenId") ?: ""
        val out = jsonString(p, "outcome", "outcome_label") ?: ""
        val avg = jsonString(p, "average_price", "avg_price")
        val ev = jsonString(p, "event_id", "condition_id") ?: ""
        val label = if (out.isNotEmpty()) "$out · ${qty ?: ""}".trim().trimEnd('·').trim() else tok
        val detail = listOf(ev, avg?.let { "AVG $it" }).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")
        PositionItem(label = label.ifBlank { tok.ifBlank { "Position" } }, detail = detail, quantity = qty)
    }
}

fun ordersFromJson(root: JsonObject?): List<OrderItem> {
    if (root == null) return emptyList()
    val items = jsonObjectList(root, "orders", "data", "items")
    return items.map { o ->
        val side = jsonString(o, "side") ?: ""
        val tok = jsonString(o, "token_id", "tokenId") ?: ""
        val status = jsonString(o, "status") ?: ""
        val price = jsonString(o, "price")
        val size = jsonString(o, "size", "quantity")
        OrderItem(
            label = listOf(side, tok).filter { it.isNotEmpty() }.joinToString(" · ").ifBlank { "Order" },
            detail = listOf(status, price?.let { "at $it" }, size?.let { "size $it" }).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")
        )
    }
}

fun orderIdFromJson(root: JsonObject?): String? {
    if (root == null) return null
    val direct = jsonString(root, "order_id")
    if (direct != null) return direct
    val data = root.get("data")
    if (data != null && data.isJsonObject) return jsonString(data.asJsonObject, "order_id")
    return null
}

/**
 * Trading availability, decided from what the server actually said.
 * Unreachable covers a dead server, a lock page (401/403) and any
 * 5xx: the app cannot tell those apart and must not guess.
 */
sealed class TradingState {
    /** api.php cannot be reached, or it answered 401/403/5xx. */
    object Unreachable : TradingState()

    /** Reachable, but no wallet is linked on this device yet. */
    object NotLinked : TradingState()

    /** Wallet linked, but the app holds no trading session for it. */
    data class Linked(val address: String) : TradingState()

    /** Linked wallet with a live session: balance, orders and trades work. */
    data class Ready(val address: String) : TradingState()
}

fun tradingStateFor(serverReachable: Boolean, linked: Boolean, address: String?, hasSession: Boolean): TradingState {
    if (!serverReachable) return TradingState.Unreachable
    if (!linked || address.isNullOrBlank()) return TradingState.NotLinked
    if (!hasSession) return TradingState.Linked(address)
    return TradingState.Ready(address)
}

class TradingRepository(
    private val api: TradingApi = createTradingApi(),
    private val tokens: TokenStore? = null
) {
    /** Current trading state. Never throws; trouble means Unreachable. */
    suspend fun tradingState(serverReachable: Boolean): TradingState {
        val address = tokens?.linkedWallet
        if (address.isNullOrBlank()) return tradingStateFor(serverReachable, false, null, false)
        return try {
            val r = api.linkStatus(address = address)
            if (!r.isSuccessful) return TradingState.Unreachable
            val linked = r.body()?.linked == true
            tradingStateFor(serverReachable, linked, address, tokens.tradingSession?.isNotBlank() == true)
        } catch (e: Exception) {
            TradingState.Unreachable
        }
    }

    /** Asks the server for a fresh single-use link nonce. Null on trouble. */
    suspend fun requestLinkNonce(): String? {
        return try {
            val r = api.linkNonce()
            if (!r.isSuccessful) null else r.body()?.nonce?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Whether the given wallet is linked on the server. Null on trouble. */
    suspend fun isWalletLinked(address: String): Boolean? {
        return try {
            val r = api.linkStatus(address = address)
            if (!r.isSuccessful) null else r.body()?.linked
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks with the server whether a session token belongs to a
     * wallet, without touching the broker and without trusting the
     * caller: only a 2xx from session_status counts. False covers a
     * wrong session, an expired one, and a dead server alike; the
     * caller must treat all three as "do not store".
     */
    suspend fun validateSession(address: String, session: String): Boolean {
        if (!isValidTradingSession(session)) return false
        return try {
            val r = api.sessionStatus(address = address, session = session)
            r.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sends a finished link (address + signed message + signature) to the
     * server. The app never signs: the signature comes from the user's
     * own wallet app. Returns the session token on success, null otherwise.
     */
    suspend fun submitLink(address: String, message: String, signature: String): String? {
        return try {
            val r = api.linkWallet(body = mapOf("address" to address, "message" to message, "signature" to signature))
            if (!r.isSuccessful) null else r.body()?.session?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadBalance(address: String, session: String): Double? {
        return try {
            val r = api.balance(address = address, session = session)
            if (!r.isSuccessful) null else balanceFromJson(r.body())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadPositions(address: String, session: String): List<PositionItem>? {
        return try {
            val r = api.positions(address = address, session = session)
            if (!r.isSuccessful) null else positionsFromJson(r.body())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadOrders(address: String, session: String): List<OrderItem>? {
        return try {
            val r = api.orders(address = address, session = session)
            if (!r.isSuccessful) null else ordersFromJson(r.body())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Places a market order. Returns the order id on success, or a plain
     * error message. A null id with no message means trading is locked.
     */
    suspend fun placeOrder(address: String, session: String, tokenId: String, side: TradeSide, totalCost: String): Pair<String?, String?> {
        val amountErr = orderAmountError(totalCost)
        if (amountErr != null) return null to amountErr
        if (tokenId.isBlank()) return null to "This market has no tradeable token for that side yet."
        return try {
            val r = api.placeOrder(body = buildPlaceOrderBody(address, session, tokenId, side, totalCost))
            if (!r.isSuccessful) {
                val serverMsg = r.errorBody()?.string()?.let { authErrorFromJson(it) }
                val msg = when {
                    r.code() == 401 || r.code() == 403 -> "Please link your wallet to Prognos Labs first."
                    !serverMsg.isNullOrBlank() -> serverMsg
                    else -> "Trading is not available right now. Please try again later."
                }
                return null to msg
            }
            val id = orderIdFromJson(r.body())
            id to null
        } catch (e: Exception) {
            null to "Trading is not available right now. Please try again later."
        }
    }
}
