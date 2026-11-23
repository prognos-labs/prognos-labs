package xyz.fruitslice.prognos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Creates the encrypted preferences store for a name, or null when
 * the device Keystore cannot provide it (very old or broken
 * Keystore). Null is the signal to keep the values in memory only:
 * the callers never fall back to plain text on disk.
 */
fun createEncryptedPrefs(context: Context, name: String): SharedPreferences? {
    return try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Where the auth token and the trading session live on the device.
 *
 * EncryptedSharedPreferences (AES256-GCM, key in the Android Keystore)
 * when it can be created. When it cannot (a broken Keystore), the
 * values are kept in memory only and never written to disk: a token
 * in plain text on disk is worse than asking the user to sign in
 * again after the app restarts. [encrypted] reports which one is in
 * use, so the build report can say which shipped. The app never
 * writes a password or a private key anywhere: only the server-issued
 * tokens, the linked wallet address and the signed-in display
 * name/email.
 *
 * `encryptedFactory` is injectable so tests can simulate a broken
 * Keystore without one.
 */
class TokenStore(
    context: Context,
    encryptedFactory: (Context, String) -> SharedPreferences? = ::createEncryptedPrefs
) {
    private val prefs: SharedPreferences
    val encrypted: Boolean

    init {
        val enc = encryptedFactory(context, "prognos_tokens")
        encrypted = enc != null
        prefs = enc ?: MemorySharedPreferences()
    }

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(v) = prefs.edit().putString("auth_token", v).apply()

    /** When the current auth token was saved, millis since epoch. */
    var authTokenSavedAt: Long
        get() = prefs.getLong("auth_token_saved_at", 0L)
        set(v) = prefs.edit().putLong("auth_token_saved_at", v).apply()

    var authName: String?
        get() = prefs.getString("auth_name", null)
        set(v) = prefs.edit().putString("auth_name", v).apply()

    var authEmail: String?
        get() = prefs.getString("auth_email", null)
        set(v) = prefs.edit().putString("auth_email", v).apply()

    /** Trading session token from the link flow (24h, bound to a wallet). */
    var tradingSession: String?
        get() = prefs.getString("trading_session", null)
        set(v) = prefs.edit().putString("trading_session", v).apply()

    /** Wallet address the trading session belongs to, lowercased. */
    var linkedWallet: String?
        get() = prefs.getString("linked_wallet", null)
        set(v) = prefs.edit().putString("linked_wallet", v?.lowercase()).apply()

    /**
     * The wallet address the app itself asked to link, set when the
     * link flow starts and cleared once the return is handled. It is
     * not a secret, just the address the user typed. The app only
     * accepts a session handed back in a redirect when it belongs to
     * this address (or to the already linked wallet), so a page or
     * another app cannot plant a session for a different wallet.
     */
    var pendingLinkAddress: String?
        get() = prefs.getString("pending_link_address", null)
        set(v) = prefs.edit().putString("pending_link_address", v?.lowercase()).apply()

    fun clearPendingLinkAddress() {
        prefs.edit().remove("pending_link_address").apply()
    }

    fun saveAuth(token: String, name: String?, email: String?) {
        prefs.edit()
            .putString("auth_token", token)
            .putLong("auth_token_saved_at", System.currentTimeMillis())
            .putString("auth_name", name)
            .putString("auth_email", email)
            .apply()
    }

    fun saveTradingSession(address: String, session: String) {
        prefs.edit()
            .putString("linked_wallet", address.lowercase())
            .putString("trading_session", session)
            .apply()
    }

    fun clearAuth() {
        prefs.edit()
            .remove("auth_token")
            .remove("auth_token_saved_at")
            .remove("auth_name")
            .remove("auth_email")
            .apply()
    }

    fun clearTrading() {
        prefs.edit()
            .remove("trading_session")
            .remove("linked_wallet")
            .apply()
    }

    /**
     * Drops only the trading session, keeping the linked wallet. Used
     * when a wallet change is confirmed for a different address: the old
     * session belongs to the old wallet and must never be reused for the
     * new one. Trading stays locked until the new link hands back its
     * own session.
     */
    fun clearTradingSession() {
        prefs.edit().remove("trading_session").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
