package xyz.fruitslice.prognos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.ChartResponse
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.MarketsResponse
import xyz.fruitslice.prognos.model.NewsFeed
import xyz.fruitslice.prognos.model.TickerAsset
import xyz.fruitslice.prognos.model.TickerResponse
import xyz.fruitslice.prognos.model.WatchFlag
import xyz.fruitslice.prognos.model.toDto
import xyz.fruitslice.prognos.model.toMarket

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prognos")

class Repository(
    private val api: PrognosApi = createApi(),
    private val diskCache: MarketDiskCache? = null,
    private val memory: MemoryMarketCache = MemoryMarketCache()
) {

    /**
     * Last markets, from memory or disk, fresh or stale. Null only
     * when the app has never fetched them. This is what the home
     * screen shows instantly on start while a refresh runs.
     */
    suspend fun cachedMarkets(): List<Market>? {
        memory.staleMarkets()?.let { return it }
        val disk = diskCache?.loadMarkets() ?: return null
        memory.putMarkets(disk)
        return disk
    }

    /**
     * Last ticker, from memory or disk, fresh or stale. Null only
     * when the app has never fetched it.
     */
    suspend fun cachedTicker(): List<TickerAsset>? {
        memory.staleTicker()?.let { return it }
        val disk = diskCache?.loadTicker() ?: return null
        memory.putTicker(disk)
        return disk
    }

    /**
     * Markets, fast. A fresh memory entry answers without touching
     * the network. Otherwise the network is tried; on any failure
     * the last real data (memory, then disk) is returned instead of
     * throwing, so a slow or dead server never blanks the screen.
     * It throws only when no data exists anywhere.
     */
    suspend fun loadMarkets(forceRefresh: Boolean = false): List<Market> {
        if (!forceRefresh) memory.freshMarkets()?.let { return it }
        return try {
            val items = api.markets(100).items ?: emptyList()
            val markets = items.mapNotNull { it.toMarket() }
            memory.putMarkets(markets)
            diskCache?.saveMarkets(markets)
            markets
        } catch (e: Exception) {
            memory.staleMarkets() ?: cachedMarkets() ?: throw e
        }
    }

    /**
     * Ticker, fast. Same rule as [loadMarkets]: fresh memory first,
     * then the network, then the last real data on failure.
     */
    suspend fun loadTicker(forceRefresh: Boolean = false): List<TickerAsset> {
        if (!forceRefresh) memory.freshTicker()?.let { return it }
        return try {
            val assets = api.ticker().assets ?: emptyList()
            memory.putTicker(assets)
            diskCache?.saveTicker(assets)
            assets
        } catch (e: Exception) {
            memory.staleTicker() ?: cachedTicker() ?: throw e
        }
    }

    suspend fun loadMarket(id: String): Market? {
        return try { api.market(id).toMarket() } catch (e: Exception) { null }
    }

    suspend fun loadBook(tokenId: String): BookResponse? {
        if (tokenId.isBlank()) return null
        return try { api.book(tokenId, 20) } catch (e: Exception) { null }
    }

    suspend fun loadChart(marketId: String): ChartResponse? {
        return try { api.chart(marketId) } catch (e: Exception) { null }
    }

    suspend fun loadNews(): NewsFeed {
        return try {
            val r = api.news()
            NewsFeed(items = r.items ?: emptyList(), mood = r.mood)
        } catch (e: Exception) {
            NewsFeed()
        }
    }

    suspend fun loadWatchdog(): List<WatchFlag> {
        return try { api.watchdog().flags ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun loadLearningScored(): Int? {
        return try { api.learning().scored } catch (e: Exception) { null }
    }

    suspend fun recordForecasts(markets: List<Market>) {
        try {
            val now = System.currentTimeMillis() / 1000
            val items = markets.mapNotNull { m ->
                val fc = forecastFor(m, now) ?: return@mapNotNull null
                val p = m.p0 ?: return@mapNotNull null
                mapOf<String, Any>(
                    "id" to m.id,
                    "asset" to (m.asset ?: ""),
                    "label0" to m.outcomes[0].label,
                    "p" to fc.p,
                    "price" to p,
                    "spread" to (if (m.bid != null && m.ask != null) m.ask - m.bid else 0.0),
                    "mins" to if (m.end > 0) maxOf(0L, (m.end - now) / 60) else 0L,
                    "vol" to (xyz.fruitslice.prognos.model.parseNum(m.vol24) ?: 0.0),
                    "end" to m.end,
                    "win" to 300
                )
            }
            if (items.isNotEmpty()) api.recordForecasts(mapOf("items" to items))
        } catch (e: Exception) {
            // Recording must never break the app.
        }
    }
}

/**
 * Last markets and ticker on disk, so a cold start still shows the
 * last real data instantly instead of a spinner. Only real server
 * replies are stored, verbatim JSON, with the time they were fetched.
 * A corrupt or unreadable entry is ignored, never shown as data.
 */
class MarketDiskCache(private val context: Context) {
    private val marketsJsonKey = stringPreferencesKey("cached_markets_json")
    private val marketsAtKey = longPreferencesKey("cached_markets_at_ms")
    private val tickerJsonKey = stringPreferencesKey("cached_ticker_json")
    private val tickerAtKey = longPreferencesKey("cached_ticker_at_ms")

    suspend fun saveMarkets(markets: List<Market>) {
        val json = marketsResponseToJson(MarketsResponse(markets.map { it.toDto() }))
        context.dataStore.edit { it[marketsJsonKey] = json; it[marketsAtKey] = System.currentTimeMillis() }
    }

    suspend fun loadMarkets(): List<Market>? {
        val prefs = context.dataStore.data.first()
        val items = marketsResponseFromJson(prefs[marketsJsonKey])?.items ?: return null
        return items.mapNotNull { it.toMarket() }.ifEmpty { null }
    }

    suspend fun saveTicker(ticker: List<TickerAsset>) {
        val json = tickerResponseToJson(TickerResponse(ticker))
        context.dataStore.edit { it[tickerJsonKey] = json; it[tickerAtKey] = System.currentTimeMillis() }
    }

    suspend fun loadTicker(): List<TickerAsset>? {
        val prefs = context.dataStore.data.first()
        return tickerResponseFromJson(prefs[tickerJsonKey])?.assets?.ifEmpty { null }
    }
}

class WatchlistStore(private val context: Context) {
    private val key = stringSetPreferencesKey("watchlist_ids")
    val ids: Flow<Set<String>> = context.dataStore.data.map { it[key] ?: emptySet() }

    suspend fun toggle(id: String) {
        context.dataStore.edit { prefs ->
            val cur = (prefs[key] ?: emptySet()).toMutableSet()
            if (cur.contains(id)) cur.remove(id) else cur.add(id)
            prefs[key] = cur
        }
    }
}

data class AuthState(
    val loading: Boolean = true,
    val loggedIn: Boolean = false,
    val name: String = "",
    val email: String = ""
)

/** An OTP step the auth screen is waiting on, if any. */
data class OtpPending(val email: String, val purpose: String)

/**
 * Server auth against auth.php on prognoslabs.xyz (see AuthApi.kt for
 * the fixed contract). The token lives in TokenStore (encrypted when
 * the device allows it); no password is ever stored. Until the server
 * answers, every auth call fails plainly with "Accounts are not ready
 * yet. Please try again later." and nothing crashes.
 */
class AuthStore(
    context: Context,
    private val authRepo: AuthRepository = AuthRepository(),
    private val tokens: TokenStore = TokenStore(context)
) {
    private val _state = MutableStateFlow(AuthState())
    val state: Flow<AuthState> = _state.asStateFlow()

    private val _otpPending = MutableStateFlow<OtpPending?>(null)
    val otpPending: StateFlow<OtpPending?> = _otpPending.asStateFlow()

    /** Email waiting for a new password after a reset OTP was verified. */
    private val _resetStep = MutableStateFlow<String?>(null)
    val resetStep: StateFlow<String?> = _resetStep.asStateFlow()

    /**
     * A one-off green notice for the auth screen, e.g. "Registration
     * complete". Set when a step finishes; cleared when the next step
     * starts or the user dismisses it.
     */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * When the last code was sent (epoch ms). The OTP screen counts a
     * 60-second resend cooldown from this, so the "Send a new code" link
     * is never live right after a code went out. The server also caps
     * sends at 3 per email per hour and stays silent past that, so the
     * UI must not encourage rapid taps.
     */
    private val _lastCodeSentAt = MutableStateFlow(0L)
    val lastCodeSentAt: StateFlow<Long> = _lastCodeSentAt.asStateFlow()

    /** Clears the one-off auth notice. */
    fun clearNotice() { _notice.value = null }

    private var pendingResetToken: String? = null
    private var pendingPassword: String? = null
    private var resetEmail: String? = null

    /**
     * Runs on app start: restores the session or signs out. Never throws.
     *
     * v1.1.2: the old flow refreshed the token first when it was older
     * than 20 hours, then called me(), then on any failure refreshed
     * AGAIN and called me() a second time: up to 4 slow calls before
     * the app showed anything. Now it tries me() with the saved token
     * first (one call when the token is still good) and refreshes only
     * once, only when the server rejects the token. At most 3 calls,
     * usually 1.
     */
    suspend fun restore() {
        val savedToken = tokens.authToken
        if (savedToken.isNullOrBlank()) {
            _state.value = AuthState(loading = false)
            return
        }
        when (val me = authRepo.me(savedToken)) {
            is AuthCall.Ok -> {
                val u = me.response.user
                tokens.saveAuth(savedToken, u?.name ?: tokens.authName, u?.email ?: tokens.authEmail)
                _state.value = AuthState(
                    loading = false, loggedIn = true,
                    name = u?.name ?: tokens.authName ?: "",
                    email = u?.email ?: tokens.authEmail ?: ""
                )
            }
            is AuthCall.ServerError -> {
                // Token rejected: one refresh attempt, then sign out.
                val refreshed = authRepo.refresh(savedToken)
                val newToken = (refreshed as? AuthCall.Ok)?.response?.token
                var ok = false
                if (!newToken.isNullOrBlank()) {
                    val retry = authRepo.me(newToken)
                    if (retry is AuthCall.Ok) {
                        val u = retry.response.user
                        tokens.saveAuth(newToken, u?.name ?: tokens.authName, u?.email ?: tokens.authEmail)
                        _state.value = AuthState(
                            loading = false, loggedIn = true,
                            name = u?.name ?: tokens.authName ?: "",
                            email = u?.email ?: tokens.authEmail ?: ""
                        )
                        ok = true
                    }
                }
                if (!ok) signOutLocal()
            }
            AuthCall.NotReady -> {
                // Server unreachable: stay signed in with the stored
                // details so markets still work. Any server call will
                // say accounts are not ready yet when it is tried.
                _state.value = AuthState(
                    loading = false, loggedIn = true,
                    name = tokens.authName ?: "", email = tokens.authEmail ?: ""
                )
            }
        }
    }

    private fun signOutLocal() {
        tokens.clearAuth()
        tokens.clearTrading()
        _otpPending.value = null
        _resetStep.value = null
        pendingResetToken = null
        pendingPassword = null
        resetEmail = null
        _state.value = AuthState(loading = false)
    }

    /** Returns a plain error message, or null on success (OTP sent). */
    suspend fun signUp(name: String, email: String, password: String): String? {
        val err = authError(name, email, password, true)
        if (err != null) return err
        return when (val r = authRepo.signup(name.trim(), email.trim(), password)) {
            is AuthCall.Ok -> {
                pendingPassword = password
                // The server sent the verification code with signup.
                _lastCodeSentAt.value = System.currentTimeMillis()
                _otpPending.value = OtpPending(email.trim(), "verify")
                null
            }
            is AuthCall.ServerError -> r.message
            AuthCall.NotReady -> AppConfig.ACCOUNTS_NOT_READY
        }
    }

    /** Returns a plain error message, or null on success (OTP sent). */
    suspend fun logIn(email: String, password: String): String? {
        val err = authError("", email, password, false)
        if (err != null) return err
        return when (val r = authRepo.login(email.trim(), password)) {
            is AuthCall.Ok -> {
                val token = r.response.token
                if (!token.isNullOrBlank()) {
                    val u = r.response.user
                    tokens.saveAuth(token, u?.name, u?.email ?: email.trim())
                    _state.value = AuthState(
                        loading = false, loggedIn = true,
                        name = u?.name ?: "", email = u?.email ?: email.trim()
                    )
                    null
                } else {
                    // The server already sent the login code with this
                    // response. Do NOT send another one here: the server
                    // invalidates all earlier unused codes for the email
                    // when a new code is issued, so a second send would
                    // kill the code the user just received and loop them
                    // back to "send a new code".
                    pendingPassword = password
                    _lastCodeSentAt.value = System.currentTimeMillis()
                    _otpPending.value = OtpPending(email.trim(), "login")
                    null
                }
            }
            is AuthCall.ServerError -> r.message
            AuthCall.NotReady -> AppConfig.ACCOUNTS_NOT_READY
        }
    }

    /** Verifies the pending OTP code. Returns a plain error, or null. */
    suspend fun verifyOtp(code: String): String? {
        val pending = _otpPending.value ?: return "No code was requested. Try again."
        if (!isValidOtpCode(code)) return "Enter the 6-digit code."
        return when (val r = authRepo.verifyOtp(pending.email, code.trim(), pending.purpose)) {
            is AuthCall.Ok -> when (pending.purpose) {
                "login" -> {
                    val token = r.response.token
                    if (token.isNullOrBlank()) return "Something went wrong. Please try again."
                    val u = r.response.user
                    tokens.saveAuth(token, u?.name, u?.email ?: pending.email)
                    _otpPending.value = null
                    pendingPassword = null
                    _state.value = AuthState(
                        loading = false, loggedIn = true,
                        name = u?.name ?: "", email = u?.email ?: pending.email
                    )
                    null
                }
                "reset" -> {
                    val resetToken = r.response.resetToken
                    if (resetToken.isNullOrBlank()) return "Something went wrong. Please try again."
                    pendingResetToken = resetToken
                    resetEmail = pending.email
                    _otpPending.value = null
                    _resetStep.value = pending.email
                    null
                }
                else -> {
                    // Signup verified. Tell the user plainly that
                    // registration is complete, then sign in with the
                    // password just used, which sends the login code.
                    // The login code is a separate step: verify_otp for
                    // "verify" returns no token by design, so a single
                    // signup code cannot land on the dashboard.
                    _otpPending.value = null
                    _notice.value = "Registration complete. We sent a sign-in code to ${pending.email}."
                    val pw = pendingPassword
                    if (pw.isNullOrBlank()) return null
                    pendingPassword = null
                    logIn(pending.email, pw)
                }
            }
            // v1.1.5: a wrong code used to surface as "Accounts are not
            // ready yet" because the server's 400 threw inside Retrofit
            // and was swallowed as NotReady. The server message now
            // reaches here; say the code is wrong, in the app's words.
            is AuthCall.ServerError -> userFacingVerifyOtpError(r.message)
            AuthCall.NotReady -> AppConfig.ACCOUNTS_NOT_READY
        }
    }

    /** Sends a fresh code for the pending step. Returns an error, or null. */
    suspend fun resendOtp(): String? {
        val pending = _otpPending.value ?: return "No code was requested. Try again."
        return when (val r = authRepo.sendOtp(pending.email, pending.purpose)) {
            is AuthCall.Ok -> {
                _lastCodeSentAt.value = System.currentTimeMillis()
                null
            }
            is AuthCall.ServerError -> r.message
            AuthCall.NotReady -> AppConfig.ACCOUNTS_NOT_READY
        }
    }

    /** Starts a password reset: sends a code to the email. */
    suspend fun requestPasswordReset(email: String): String? {
        if (!isValidEmail(email)) return "Enter a valid email address."
        return when (val r = authRepo.resetRequest(email.trim())) {
            is AuthCall.Ok -> {
                _lastCodeSentAt.value = System.currentTimeMillis()
                _otpPending.value = OtpPending(email.trim(), "reset")
                null
            }
            is AuthCall.ServerError -> r.message
            AuthCall.NotReady -> AppConfig.ACCOUNTS_NOT_READY
        }
    }

    /** Finishes a password reset with the new password. */
    suspend fun confirmPasswordReset(newPassword: String): String? {
        val email = resetEmail ?: return "Start the reset again."
        val resetToken = pendingResetToken ?: return "Start the reset again."
        if (!isValidNewPassword(newPassword)) return "New password needs at least 8 characters."
        return when (val r = authRepo.resetConfirm(email, resetToken, newPassword)) {
            is AuthCall.Ok -> {
                pendingResetToken = null
                resetEmail = null
                _resetStep.value = null
                null
            }
            is AuthCall.ServerError -> r.message
            AuthCall.NotReady -> AppConfig.ACCOUNTS_NOT_READY
        }
    }

    /** Clears the pending OTP / reset step without signing out. */
    fun clearPending() {
        _otpPending.value = null
        _resetStep.value = null
        _notice.value = null
        pendingResetToken = null
        resetEmail = null
    }

    suspend fun logOut() {
        val token = tokens.authToken
        if (!token.isNullOrBlank()) {
            try { authRepo.logout(token) } catch (e: Exception) { /* best effort */ }
        }
        signOutLocal()
    }
}

/** Price-alerts preference, toggled from Settings. Defaults to on. */
class AlertsStore(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("price_alerts_enabled")

    val enabled: Flow<Boolean> = context.dataStore.data.map { it[enabledKey] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[enabledKey] = enabled }
    }
}

class ThemeStore(private val context: Context) {
    private val darkKey = booleanPreferencesKey("dark_mode")
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[darkKey] ?: false }

    suspend fun setDark(dark: Boolean) {
        context.dataStore.edit { it[darkKey] = dark }
    }
}

/** Display currency for balances. USD is the default; a choice only
 *  changes how a figure is shown, never its value. */
class CurrencyStore(private val context: Context) {
    private val codeKey = stringPreferencesKey("display_currency")
    val code: Flow<String> = context.dataStore.data.map { it[codeKey] ?: DisplayCurrency.USD.code }

    suspend fun setCode(code: String) {
        context.dataStore.edit { it[codeKey] = DisplayCurrency.fromCode(code).code }
    }
}

/**
 * Default amount for a new trade, in USD. The market screen starts its
 * amount field from this instead of a hardcoded number.
 */
class TradeSizeStore(private val context: Context) {
    private val amountKey = stringPreferencesKey("default_trade_amount")
    val amount: Flow<String> = context.dataStore.data.map { it[amountKey] ?: "10.00" }

    /** Saves the amount only when it is a valid order amount. */
    suspend fun setAmount(amount: String): Boolean {
        if (orderAmountError(amount) != null) return false
        context.dataStore.edit { it[amountKey] = amount.trim() }
        return true
    }
}

/** Cached FX rates (raw JSON + fetch time) so the app does not fetch on
 *  every open. Rates older than FX_RATES_MAX_AGE_MS are refetched. */
class FxRatesStore(private val context: Context) {
    private val jsonKey = stringPreferencesKey("fx_rates_json")
    private val atKey = longPreferencesKey("fx_rates_at_ms")

    val json: Flow<String?> = context.dataStore.data.map { it[jsonKey] }
    val fetchedAtMs: Flow<Long> = context.dataStore.data.map { it[atKey] ?: 0L }

    suspend fun save(json: String, atMs: Long) {
        context.dataStore.edit { it[jsonKey] = json; it[atKey] = atMs }
    }
}
