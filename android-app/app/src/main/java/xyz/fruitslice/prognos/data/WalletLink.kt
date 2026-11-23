package xyz.fruitslice.prognos.data

/**
 * Wallet linking, pure helpers. The app holds no wallet and no private
 * key. Linking works like this:
 *
 * 1. The app asks api.php for a single-use nonce (10 minutes).
 * 2. The app opens the link page on prognoslabs.xyz in the system
 *    browser, with the nonce in the URL (?app_link_nonce=). The user
 *    signs the link message there with their own wallet app.
 * 3. Back in the app, the app polls link_status for the wallet
 *    address the user entered. When the server says linked, the
 *    wallet shows as linked.
 * 4. The trading session token is created by link_wallet and returned
 *    to whoever called it, which is the browser page, so the session
 *    lives in the browser. The app gets it back when the link page
 *    sends the user back to the app: the page redirects to
 *    https://prognoslabs.xyz/linked?session=<token>&address=<wallet>
 *    (or prognoslabs://linked with the same query), which the app
 *    catches. The redirect is never trusted on its own: the app stores
 *    the session only after the server confirms it belongs to the
 *    wallet the app itself asked to link (session_status), and only
 *    for that wallet. Until the site sends that redirect, a linked
 *    wallet shows as linked in the app but trading stays locked,
 *    because the app holds no session. Nothing is faked.
 */

/** The message a wallet signs to link it. Free signature, no funds moved. */
fun linkMessageText(nonce: String?): String {
    if (nonce.isNullOrBlank()) return "Link this wallet to Prognos Labs"
    return "Link this wallet to Prognos Labs\nNonce: ${nonce.lowercase()}"
}

/** EVM 0x address (40 hex) or a Solana base58 address, as the server checks. */
fun isValidWalletAddress(v: String): Boolean {
    val s = v.trim()
    if (s.isEmpty()) return false
    if (Regex("^0x[0-9a-fA-F]{40}$").matches(s)) return true
    if (Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$").matches(s)) return true
    return false
}

/**
 * v1.1.10: coarse wallet type for the wallet_linked analytics
 * event. "evm" for a 0x address, "solana" otherwise. Never the
 * address itself.
 */
fun walletAddressType(address: String): String =
    if (address.trim().startsWith("0x", ignoreCase = true)) "evm" else "solana"

/** The browser URL that starts the link, carrying the fresh nonce. */
fun linkPageUrl(nonce: String?): String {
    val base = AppConfig.LINK_PAGE_URL
    if (nonce.isNullOrBlank()) return base
    val sep = if (base.contains("?")) "&" else "?"
    return base + sep + "app_link_nonce=" + nonce.lowercase()
}

/** Parses the redirect the link page sends back: session + address. */
data class LinkedReturn(val session: String?, val address: String?)

fun parseLinkedReturn(url: String?): LinkedReturn {
    if (url.isNullOrBlank()) return LinkedReturn(null, null)
    val query = url.substringAfter("?", "")
    if (query.isBlank()) return LinkedReturn(null, null)
    var session: String? = null
    var address: String? = null
    for (part in query.split("&")) {
        val kv = part.split("=", limit = 2)
        if (kv.size != 2) continue
        val value = try {
            java.net.URLDecoder.decode(kv[1], "UTF-8")
        } catch (e: Exception) {
            kv[1]
        }
        when (kv[0].lowercase()) {
            "session" -> session = value.takeIf { it.isNotBlank() }
            "address" -> address = value.takeIf { it.isNotBlank() }
        }
    }
    return LinkedReturn(session, address)
}

/** True when a URL is the link-page return the app should catch. */
/**
 * Whether this URL is the wallet-link return page. The path must be
 * exactly /linked: a plain startsWith also matched /linkedXYZ, which
 * is not the return page. The server-side session check is the real
 * gate; this only decides which page the app treats as a return.
 */
fun isLinkedReturnUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    if (lower.startsWith("prognoslabs://linked")) {
        val rest = lower.removePrefix("prognoslabs://linked")
        return rest.isEmpty() || rest.startsWith("?") || rest.startsWith("#") || rest.startsWith("/")
    }
    if (lower.startsWith("https://prognoslabs.xyz/linked")) {
        val rest = lower.removePrefix("https://prognoslabs.xyz/linked")
        return rest.isEmpty() || rest.startsWith("?") || rest.startsWith("#") || rest == "/" || rest.startsWith("/?")
    }
    return false
}

/** A trading session token is 64 lowercase-or-uppercase hex chars. */
fun isValidTradingSession(session: String?): Boolean {
    if (session.isNullOrBlank()) return false
    return Regex("^[0-9a-fA-F]{64}$").matches(session.trim())
}

/**
 * Whether a redirect return may be stored for this wallet. The token
 * must be well formed AND the wallet must be one the app itself asked
 * to link (the pending link address, kept across the browser trip) or
 * one the server already confirmed as linked. Anything else is
 * ignored: a page or another app firing the return URL with an
 * attacker-chosen token or a different wallet must not plant a
 * session in the app. The server check itself happens separately in
 * TradingRepository.validateSession.
 */
fun isAcceptableLinkedReturn(
    session: String?,
    address: String?,
    pendingLinkAddress: String?,
    linkedWallet: String?
): Boolean {
    if (!isValidTradingSession(session)) return false
    if (address.isNullOrBlank() || !isValidWalletAddress(address)) return false
    val want = address.trim().lowercase()
    if (!pendingLinkAddress.isNullOrBlank() && pendingLinkAddress.trim().lowercase() == want) return true
    if (!linkedWallet.isNullOrBlank() && linkedWallet.trim().lowercase() == want) return true
    return false
}
