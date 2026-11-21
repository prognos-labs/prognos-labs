package xyz.fruitslice.prognos.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import xyz.fruitslice.prognos.data.AlertsStore
import xyz.fruitslice.prognos.data.AppConfig
import xyz.fruitslice.prognos.data.AuthState
import xyz.fruitslice.prognos.data.AuthStore
import xyz.fruitslice.prognos.data.CurrencyStore
import xyz.fruitslice.prognos.data.DisplayCurrency
import xyz.fruitslice.prognos.data.FX_RATES_MAX_AGE_MS
import xyz.fruitslice.prognos.data.FxRates
import xyz.fruitslice.prognos.data.FxRatesStore
import xyz.fruitslice.prognos.data.LinkedReturn
import xyz.fruitslice.prognos.data.MarketDiskCache
import xyz.fruitslice.prognos.data.OrderItem
import xyz.fruitslice.prognos.data.OtpPending
import xyz.fruitslice.prognos.data.PositionItem
import xyz.fruitslice.prognos.data.Repository
import xyz.fruitslice.prognos.data.ThemeStore
import xyz.fruitslice.prognos.data.TokenStore
import xyz.fruitslice.prognos.data.TradeSizeStore
import xyz.fruitslice.prognos.data.TradingRepository
import xyz.fruitslice.prognos.data.TradingState
import xyz.fruitslice.prognos.data.TradeSide
import xyz.fruitslice.prognos.data.WatchlistStore
import xyz.fruitslice.prognos.data.changePasswordError
import xyz.fruitslice.prognos.data.fetchFxRatesJson
import xyz.fruitslice.prognos.data.isAcceptableLinkedReturn
import xyz.fruitslice.prognos.data.isValidWalletAddress
import xyz.fruitslice.prognos.data.linkMessageText
import xyz.fruitslice.prognos.data.linkPageUrl
import xyz.fruitslice.prognos.data.orderAmountError
import xyz.fruitslice.prognos.data.parseFxRates
import xyz.fruitslice.prognos.data.parseLinkedReturn
import xyz.fruitslice.prognos.data.resendSecondsLeft as resendSecondsLeftFn
import xyz.fruitslice.prognos.data.selectTradeSide
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.ChartResponse
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.NewsFeed
import xyz.fruitslice.prognos.model.TickerAsset
import xyz.fruitslice.prognos.model.WatchFlag

data class HomeState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val markets: List<Market> = emptyList(),
    val ticker: List<TickerAsset> = emptyList(),
    val flags: List<WatchFlag> = emptyList(),
    val prevPrices: Map<String, Double?> = emptyMap(),
    val learnedScored: Int? = null,
    val news: NewsFeed = NewsFeed()
)

data class DetailState(
    val loading: Boolean = true,
    val market: Market? = null,
    val bookUp: BookResponse? = null,
    val bookDown: BookResponse? = null,
    val chart: ChartResponse? = null
)

data class WalletLinkState(
    val address: String = "",
    val linkedAddress: String? = null,
    val checking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(diskCache = MarketDiskCache(app))
    val watchlist = WatchlistStore(app)
    private val tokens = TokenStore(app)
    private val authStore = AuthStore(app)
    private val tradingRepo = TradingRepository(tokens = tokens)
    private val themeStore = ThemeStore(app)
    private val alertsStore = AlertsStore(app)
    private val currencyStore = CurrencyStore(app)
    private val tradeSizeStore = TradeSizeStore(app)
    private val fxRatesStore = FxRatesStore(app)
    private val fxClient = OkHttpClient()

    val auth: StateFlow<AuthState> = authStore.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState())

    val otpPending: StateFlow<OtpPending?> = authStore.otpPending
    val resetStep: StateFlow<String?> = authStore.resetStep

    /** The auth screen's forgot-password form, reachable from Log in. */
    private val _showForgotPassword = MutableStateFlow(false)
    val showForgotPassword: StateFlow<Boolean> = _showForgotPassword.asStateFlow()

    /** Display currency for balances (USD, EUR, GBP, NGN). */
    val displayCurrency: StateFlow<DisplayCurrency> = currencyStore.code
        .map { DisplayCurrency.fromCode(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DisplayCurrency.USD)

    /** Live FX rates, or null while none have been fetched yet. */
    private val _rates = MutableStateFlow<FxRates?>(null)
    val rates: StateFlow<FxRates?> = _rates.asStateFlow()

    /** Default amount for a new trade, in USD. */
    val defaultTradeAmount: StateFlow<String> = tradeSizeStore.amount
        .stateIn(viewModelScope, SharingStarted.Eagerly, "10.00")

    val darkMode: StateFlow<Boolean> = themeStore.darkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val alertsEnabled: StateFlow<Boolean> = alertsStore.enabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    private val _nowSec = MutableStateFlow(System.currentTimeMillis() / 1000)
    val nowSec: StateFlow<Long> = _nowSec.asStateFlow()

    /**
     * Seconds left before "Send a new code" may be tapped again.
     * Counts down from 60 after every code sent (signup, login,
     * reset, resend). 0 means a new code may be asked for.
     */
    val resendSecondsLeft: StateFlow<Long> = combine(
        _nowSec, authStore.lastCodeSentAt
    ) { now, sentAt -> resendSecondsLeftFn(now * 1000, sentAt) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    /** One-off success notes on the auth screens (e.g. password changed). */
    private val _authNotice = MutableStateFlow<String?>(null)
    val authNotice: StateFlow<String?> = _authNotice.asStateFlow()

    /** Settings feedback: a message plus whether it is an error. */
    private val _settingsMessage = MutableStateFlow<String?>(null)
    val settingsMessage: StateFlow<String?> = _settingsMessage.asStateFlow()
    private val _settingsMessageIsError = MutableStateFlow(false)
    val settingsMessageIsError: StateFlow<Boolean> = _settingsMessageIsError.asStateFlow()

    /** Trading availability: closed until the server says otherwise. */
    private val _tradingState = MutableStateFlow<TradingState>(TradingState.Unreachable)
    val tradingState: StateFlow<TradingState> = _tradingState.asStateFlow()

    /** Real balance in USDT when trading is ready, null otherwise. */
    private val _balance = MutableStateFlow<Double?>(null)
    val balance: StateFlow<Double?> = _balance.asStateFlow()

    private val _positions = MutableStateFlow<List<PositionItem>>(emptyList())
    val positions: StateFlow<List<PositionItem>> = _positions.asStateFlow()

    private val _orders = MutableStateFlow<List<OrderItem>>(emptyList())
    val orders: StateFlow<List<OrderItem>> = _orders.asStateFlow()

    /** Trade panel: which side is picked per market. The pick stays put. */
    private val _tradeSides = MutableStateFlow<Map<String, TradeSide>>(emptyMap())
    val tradeSides: StateFlow<Map<String, TradeSide>> = _tradeSides.asStateFlow()

    /** Trade feedback on the detail screen: message + whether it is an error. */
    private val _tradeMessage = MutableStateFlow<String?>(null)
    val tradeMessage: StateFlow<String?> = _tradeMessage.asStateFlow()
    private val _tradeMessageIsError = MutableStateFlow(false)
    val tradeMessageIsError: StateFlow<Boolean> = _tradeMessageIsError.asStateFlow()
    private val _tradeBusy = MutableStateFlow(false)
    val tradeBusy: StateFlow<Boolean> = _tradeBusy.asStateFlow()

    private val _link = MutableStateFlow(WalletLinkState(linkedAddress = tokens.linkedWallet))
    val link: StateFlow<WalletLinkState> = _link.asStateFlow()

    /** URLs the UI should open in the system browser (link page). */
    private val _openBrowser = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openBrowser: SharedFlow<String> = _openBrowser.asSharedFlow()

    private var refreshJob: Job? = null

    /** Guards refreshAll: one refresh at a time, never overlapping. */
    private val refreshMutex = Mutex()

    /** Guards one detail refresh at a time while live polling runs. */
    private val detailMutex = Mutex()

    /** Live polling for the open market detail screen, if any. */
    private var detailPollJob: Job? = null
    private var detailPollId: String? = null

    init {
        viewModelScope.launch { authStore.restore() }
        // One-off auth notices (e.g. "Registration complete") come
        // from the store; show them on the auth screens.
        viewModelScope.launch {
            authStore.notice.collect { if (it != null) _authNotice.value = it }
        }
        viewModelScope.launch {
            while (true) {
                _nowSec.value = System.currentTimeMillis() / 1000
                delay(1000)
            }
        }
        refreshRates()
        // v1.1.2: show the last real data instantly, then refresh in
        // the background. The old code started with a full network
        // refresh and a spinner, so the first screen took 10+ seconds.
        viewModelScope.launch {
            val cachedMarkets = repo.cachedMarkets()
            val cachedTicker = repo.cachedTicker()
            if (!cachedMarkets.isNullOrEmpty() || !cachedTicker.isNullOrEmpty()) {
                _home.value = HomeState(
                    loading = false, error = false,
                    markets = cachedMarkets ?: emptyList(),
                    ticker = cachedTicker ?: emptyList(),
                    flags = emptyList(), prevPrices = emptyMap(), learnedScored = null
                )
            }
            refreshAll()
        }
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                refreshAll(silent = true)
            }
        }
        // v1.1.2: prices tick fast. The ticker call is one small
        // request (~1.5s, under 1 KB), so it refreshes every 10s
        // while the heavy markets list stays on the 30s tick. The
        // price strip on every screen updates without waiting for
        // the slow markets call.
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                val ticker = runCatching { repo.loadTicker() }.getOrNull()
                if (ticker != null) _home.value = _home.value.copy(ticker = ticker)
            }
        }
    }

    // ---- Auth (server) ----

    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.signUp(name, email, password)
            _authBusy.value = false
        }
    }

    fun logIn(email: String, password: String) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.logIn(email, password)
            _authBusy.value = false
        }
    }

    fun verifyOtp(code: String) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.verifyOtp(code)
            _authBusy.value = false
        }
    }

    fun resendOtp() {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.resendOtp()
            _authBusy.value = false
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.requestPasswordReset(email)
            _authBusy.value = false
        }
    }

    /** Shows the forgot-password form on the Log in screen. */
    fun showForgotPassword() {
        _showForgotPassword.value = true
        _authError.value = null
        authStore.clearNotice()
        _authNotice.value = null
    }

    /** Hides the forgot-password form, back to Log in. */
    fun hideForgotPassword() {
        _showForgotPassword.value = false
        _authError.value = null
    }

    fun confirmPasswordReset(newPassword: String, confirm: String) {
        viewModelScope.launch {
            if (newPassword != confirm) {
                _authError.value = "New passwords do not match."
                return@launch
            }
            _authBusy.value = true
            val err = authStore.confirmPasswordReset(newPassword)
            _authError.value = err
            if (err == null) {
                _authNotice.value = "Password changed. Log in with your new password."
                _showForgotPassword.value = false
            }
            _authBusy.value = false
        }
    }

    fun clearAuthNotice() {
        _authNotice.value = null
    }

    fun cancelOtp() {
        authStore.clearPending()
        _authError.value = null
    }

    fun logOut() {
        viewModelScope.launch {
            authStore.logOut()
            _balance.value = null
            _positions.value = emptyList()
            _orders.value = emptyList()
            _tradingState.value = TradingState.Unreachable
            _link.value = WalletLinkState()
        }
    }

    /**
     * Change password from Settings, through the reset flow (the server
     * has no change-password call): a code goes to the signed-in email,
     * then the OTP step, then the new password. There is no current
     * password check: the code sent to the account email is the check.
     */
    fun changePassword(new: String, confirm: String) {
        viewModelScope.launch {
            val err = changePasswordError(true, new, confirm)
            if (err != null) {
                _settingsMessage.value = err
                _settingsMessageIsError.value = true
                return@launch
            }
            val email = auth.value.email
            if (email.isBlank()) {
                _settingsMessage.value = "Please sign in again."
                _settingsMessageIsError.value = true
                return@launch
            }
            _settingsMessage.value = null
            val reqErr = authStore.requestPasswordReset(email)
            if (reqErr != null) {
                _settingsMessage.value = reqErr
                _settingsMessageIsError.value = true
            } else {
                _settingsMessage.value = "A code was sent to $email. Enter it below with your new password."
                _settingsMessageIsError.value = false
            }
        }
    }

    /** Verifies the reset code, then sets the new password. */
    fun confirmNewPassword(code: String, newPassword: String, confirm: String) {
        viewModelScope.launch {
            val err = changePasswordError(true, newPassword, confirm)
            if (err != null) {
                _settingsMessage.value = err
                _settingsMessageIsError.value = true
                return@launch
            }
            val otpErr = authStore.verifyOtp(code)
            if (otpErr != null) {
                _settingsMessage.value = otpErr
                _settingsMessageIsError.value = true
                return@launch
            }
            val resetErr = authStore.confirmPasswordReset(newPassword)
            if (resetErr != null) {
                _settingsMessage.value = resetErr
                _settingsMessageIsError.value = true
            } else {
                _settingsMessage.value = "Password changed."
                _settingsMessageIsError.value = false
            }
        }
    }

    fun clearSettingsMessage() {
        _settingsMessage.value = null
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { alertsStore.setEnabled(enabled) }
    }

    /** Display currency for balances. Only changes how figures are
     *  shown; every amount is still stored in USD. */
    fun setDisplayCurrency(code: String) {
        viewModelScope.launch {
            currencyStore.setCode(code)
            if (DisplayCurrency.fromCode(code) != DisplayCurrency.USD) refreshRates()
        }
    }

    /** Default amount for a new trade. Returns an error, or null. */
    fun setDefaultTradeAmount(amount: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val err = orderAmountError(amount)
            if (err != null) {
                _settingsMessage.value = err
                _settingsMessageIsError.value = true
                onDone(err)
                return@launch
            }
            val ok = tradeSizeStore.setAmount(amount)
            if (ok) {
                _settingsMessage.value = "Default trade size saved."
                _settingsMessageIsError.value = false
                onDone(null)
            } else {
                _settingsMessage.value = "That amount could not be saved."
                _settingsMessageIsError.value = true
                onDone("That amount could not be saved.")
            }
        }
    }

    /**
     * Loads FX rates: cached ones first (they are good for an hour),
     * then a live fetch when the cache is stale or missing. If the
     * fetch fails, the cached rates (if any) stay and balances keep
     * showing in USD rather than a made-up conversion.
     */
    fun refreshRates() {
        viewModelScope.launch {
            val cachedJson = fxRatesStore.json.first()
            val cachedAt = fxRatesStore.fetchedAtMs.first()
            val cached = cachedJson?.let { parseFxRates(it, cachedAt) }
            if (cached != null) _rates.value = cached
            val now = System.currentTimeMillis()
            if (cached != null && now - cached.fetchedAtMs < FX_RATES_MAX_AGE_MS) return@launch
            val json = fetchFxRatesJson(fxClient) ?: return@launch
            val fresh = parseFxRates(json, System.currentTimeMillis()) ?: return@launch
            _rates.value = fresh
            fxRatesStore.save(json, fresh.fetchedAtMs)
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun toggleDarkMode() {
        viewModelScope.launch { themeStore.setDark(!darkMode.value) }
    }

    // ---- Markets ----

    /**
     * v1.1.2: the four home calls used to run one after another
     * (~11s: markets 7s, ticker 1.5s, watchdog 1s, learning 1s) and
     * the first load wiped the screen to a spinner. Now they run in
     * parallel, each piece is published as soon as it arrives (prices
     * first, ~1.5s), the screen is never wiped while it already shows
     * data, and a refresh already running is not started again (the
     * 30s ticker used to pile refreshes up when the server was slow).
     */
    fun refreshAll(silent: Boolean = false) {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            try {
                val hadData = _home.value.markets.isNotEmpty()
                if (!silent && !hadData) _home.value = _home.value.copy(loading = true, error = false)
                coroutineScope {
                    val marketsJob = async { runCatching { repo.loadMarkets() }.getOrNull() }
                    val tickerJob = async { runCatching { repo.loadTicker() }.getOrNull() }
                    val flagsJob = async { runCatching { repo.loadWatchdog() }.getOrNull() }
                    val scoredJob = async { runCatching { repo.loadLearningScored() }.getOrNull() }
                    // v1.1.3: the forecast reads the headlines and the
                    // mood, so the news loads here too, in parallel.
                    // A failed news fetch leaves the last headlines in
                    // place; the forecast simply skips the news part.
                    val newsJob = async { runCatching { repo.loadNews() }.getOrNull() }
                    // Prices first: the ticker answers in ~1.5s while
                    // the markets list can take ~7s server-side.
                    tickerJob.await()?.let { ticker ->
                        _home.value = _home.value.copy(ticker = ticker)
                    }
                    newsJob.await()?.let { news ->
                        _home.value = _home.value.copy(news = news)
                    }
                    val markets = marketsJob.await()
                    if (markets != null) {
                        val prev = _home.value.markets.associate { it.id to it.p0 }
                        _home.value = _home.value.copy(markets = markets, prevPrices = prev)
                        viewModelScope.launch { repo.recordForecasts(markets) }
                    }
                    flagsJob.await()?.let { flags ->
                        _home.value = _home.value.copy(flags = flags)
                    }
                    scoredJob.await()?.let { scored ->
                        _home.value = _home.value.copy(learnedScored = scored)
                    }
                    _home.value = _home.value.copy(
                        loading = false,
                        error = markets == null && _home.value.markets.isEmpty()
                    )
                }
                refreshTrading()
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    fun loadDetail(id: String) {
        viewModelScope.launch {
            val listed = _home.value.markets.find { it.id == id }
            _detail.value = DetailState(loading = true, market = listed)
            // v1.1.2: market, both books and chart used to load one
            // after another (~4s). They now run in parallel; the
            // books use the listed market's token ids, which are the
            // same ones the fresh market carries.
            coroutineScope {
                val marketJob = async { repo.loadMarket(id) }
                val upJob = async { if (listed != null) repo.loadBook(listed.outcomes[0].tokenId) else null }
                val downJob = async { if (listed != null) repo.loadBook(listed.outcomes[1].tokenId) else null }
                val chartJob = async { repo.loadChart(id) }
                val m = marketJob.await() ?: listed
                _detail.value = DetailState(
                    loading = false, market = m,
                    bookUp = upJob.await(), bookDown = downJob.await(), chart = chartJob.await()
                )
            }
        }
    }

    /**
     * Live ticking for the open market screen. While a market is
     * open, its price, chart and both books re-fetch every 5 seconds
     * and the screen updates as the data arrives. There is no
     * websocket: api.php is plain request/response PHP on shared
     * hosting, so 5-second polling is the fastest real option, and
     * each tick is four parallel calls (~2s worst case). Polling
     * starts when the screen opens and stops the moment it closes,
     * so it never runs in the background or on other screens.
     */
    fun startDetailPolling(id: String) {
        if (detailPollId == id && detailPollJob?.isActive == true) return
        stopDetailPolling()
        detailPollId = id
        detailPollJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                refreshDetail(id)
            }
        }
    }

    fun stopDetailPolling() {
        detailPollJob?.cancel()
        detailPollJob = null
        detailPollId = null
    }

    /** One live tick: market, chart and both books in parallel. */
    private suspend fun refreshDetail(id: String) {
        if (!detailMutex.tryLock()) return
        try {
            val listed = _home.value.markets.find { it.id == id }
            val curUp = _detail.value.bookUp
            val curDown = _detail.value.bookDown
            coroutineScope {
                val marketJob = async { runCatching { repo.loadMarket(id) }.getOrNull() }
                val chartJob = async { runCatching { repo.loadChart(id) }.getOrNull() }
                val upJob = async {
                    if (listed != null) runCatching { repo.loadBook(listed.outcomes[0].tokenId) }.getOrNull() ?: curUp else curUp
                }
                val downJob = async {
                    if (listed != null) runCatching { repo.loadBook(listed.outcomes[1].tokenId) }.getOrNull() ?: curDown else curDown
                }
                marketJob.await()?.let { m -> _detail.value = _detail.value.copy(market = m) }
                chartJob.await()?.let { c -> _detail.value = _detail.value.copy(chart = c) }
                _detail.value = _detail.value.copy(bookUp = upJob.await(), bookDown = downJob.await())
            }
        } finally {
            detailMutex.unlock()
        }
    }

    fun toggleWatch(id: String) {
        viewModelScope.launch { watchlist.toggle(id) }
    }

    // ---- Trading ----

    fun refreshTrading() {
        viewModelScope.launch {
            val reachable = _home.value.markets.isNotEmpty() || !_home.value.error
            val state = tradingRepo.tradingState(serverReachable = reachable)
            _tradingState.value = state
            if (state is TradingState.Ready) {
                loadTradingData(state.address, tokens.tradingSession ?: "")
            } else {
                _balance.value = null
                _positions.value = emptyList()
                _orders.value = emptyList()
            }
        }
    }

    private suspend fun loadTradingData(address: String, session: String) {
        if (session.isBlank()) return
        // v1.1.2: balance, positions and orders used to load one after
        // another; they now run in parallel.
        coroutineScope {
            val balanceJob = async { tradingRepo.loadBalance(address, session) }
            val positionsJob = async { tradingRepo.loadPositions(address, session) }
            val ordersJob = async { tradingRepo.loadOrders(address, session) }
            _balance.value = balanceJob.await()
            _positions.value = positionsJob.await() ?: emptyList()
            _orders.value = ordersJob.await() ?: emptyList()
        }
    }

    fun selectTradeSide(marketId: String, side: TradeSide) {
        val cur = _tradeSides.value[marketId]
        _tradeSides.value = _tradeSides.value + (marketId to selectTradeSide(cur, side))
        _tradeMessage.value = null
    }

    fun clearTradeMessage() {
        _tradeMessage.value = null
    }

    fun placeOrder(marketId: String, tokenId: String, side: TradeSide, amount: String) {
        viewModelScope.launch {
            val state = _tradingState.value
            if (state !is TradingState.Ready) {
                _tradeMessage.value = AppConfig.TRADING_OPENS_SOON + "."
                _tradeMessageIsError.value = true
                return@launch
            }
            val session = tokens.tradingSession
            if (session.isNullOrBlank()) {
                _tradeMessage.value = "Please link your wallet to Prognos Labs first."
                _tradeMessageIsError.value = true
                return@launch
            }
            _tradeBusy.value = true
            val (orderId, err) = tradingRepo.placeOrder(state.address, session, tokenId, side, amount)
            _tradeBusy.value = false
            if (err != null) {
                _tradeMessage.value = err
                _tradeMessageIsError.value = true
                if (err.contains("link your wallet")) refreshTrading()
            } else {
                _tradeMessage.value = "Order placed" + (if (!orderId.isNullOrBlank()) " ($orderId)" else "") + ". It may take a moment to fill."
                _tradeMessageIsError.value = false
                loadTradingData(state.address, session)
            }
        }
    }

    // ---- Wallet linking ----

    fun setLinkAddress(address: String) {
        _link.value = _link.value.copy(address = address, message = null)
    }

    /**
     * Starts the link: fresh nonce, then the browser opens the link
     * page where the user signs with their own wallet app.
     */
    fun startWalletLink() {
        viewModelScope.launch {
            val address = _link.value.address.trim()
            if (!isValidWalletAddress(address)) {
                _link.value = _link.value.copy(message = "Enter a valid wallet address.", isError = true)
                return@launch
            }
            _link.value = _link.value.copy(checking = true, message = null)
            val nonce = tradingRepo.requestLinkNonce()
            _link.value = _link.value.copy(checking = false)
            if (nonce == null) {
                _link.value = _link.value.copy(
                    message = AppConfig.TRADING_OPENS_SOON + ". Please try again later.",
                    isError = true
                )
                return@launch
            }
            // Remember which wallet this device asked to link, so the
            // return from the browser can only ever complete this link.
            tokens.pendingLinkAddress = address
            _openBrowser.emit(linkPageUrl(nonce))
            _link.value = _link.value.copy(
                message = "Sign the link message in your browser, then come back here and tap Check link.",
                isError = false
            )
        }
    }

    /** Polls the server for whether the entered wallet is linked yet. */
    fun checkWalletLink() {
        viewModelScope.launch {
            val address = _link.value.address.trim()
            if (!isValidWalletAddress(address)) {
                _link.value = _link.value.copy(message = "Enter a valid wallet address.", isError = true)
                return@launch
            }
            _link.value = _link.value.copy(checking = true, message = null)
            val linked = tradingRepo.isWalletLinked(address)
            _link.value = _link.value.copy(checking = false)
            when (linked) {
                true -> {
                    // If this replaces a different linked wallet, the old
                    // trading session belongs to the old wallet and must
                    // never be reused for the new one. Trading stays
                    // closed until the new link hands back its own
                    // session (redirect or a fresh link).
                    val oldLinked = tokens.linkedWallet
                    if (oldLinked != null && !oldLinked.equals(address, ignoreCase = true)) {
                        tokens.clearTradingSession()
                    }
                    tokens.linkedWallet = address
                    _link.value = _link.value.copy(
                        linkedAddress = address.lowercase(),
                        message = "Wallet linked. Trading in the app opens when the link page sends the session back.",
                        isError = false
                    )
                    refreshTrading()
                }
                false -> {
                    _link.value = _link.value.copy(
                        message = "That wallet is not linked yet. Sign the link message in your browser first.",
                        isError = true
                    )
                }
                null -> {
                    _link.value = _link.value.copy(
                        message = AppConfig.TRADING_OPENS_SOON + ". Please try again later.",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Handles the redirect back from the link page, if it carries a session.
     *
     * The URL alone is never trusted: a session is stored only when
     * (a) the wallet is one this app asked to link, or one the server
     * already confirmed as linked, and (b) the server confirms the
     * session belongs to that wallet (session_status, checked server-side only).
     * A planted or guessed token fails (b) and is dropped, and a
     * return for a different wallet fails (a). Nothing is stored
     * until both pass.
     */
    fun handleLinkedReturn(url: String) {
        viewModelScope.launch {
            val parsed: LinkedReturn = parseLinkedReturn(url)
            val session = parsed.session?.trim()
            val address = parsed.address?.trim()
            if (!isAcceptableLinkedReturn(session, address, tokens.pendingLinkAddress, tokens.linkedWallet)) {
                tokens.clearPendingLinkAddress()
                _link.value = _link.value.copy(
                    message = "That link could not be verified. Start the link again.",
                    isError = true
                )
                return@launch
            }
            val valid = tradingRepo.validateSession(address!!, session!!)
            tokens.clearPendingLinkAddress()
            if (!valid) {
                _link.value = _link.value.copy(
                    message = "That link could not be verified. Start the link again.",
                    isError = true
                )
                return@launch
            }
            tokens.saveTradingSession(address, session)
            _link.value = _link.value.copy(
                linkedAddress = address.lowercase(),
                address = address,
                message = "Wallet linked.",
                isError = false
            )
            refreshTrading()
        }
    }

    /** The exact link message for a nonce, for display or debugging. */
    fun linkMessage(nonce: String?): String = linkMessageText(nonce)
}
