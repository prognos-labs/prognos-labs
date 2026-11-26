package xyz.prognoslabs.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.prognoslabs.app.BuildConfig
import xyz.prognoslabs.app.data.AlertsStore
import xyz.prognoslabs.app.data.Analytics
import xyz.prognoslabs.app.data.AnalyticsEvents
import xyz.prognoslabs.app.data.AppConfig
import xyz.prognoslabs.app.data.AuthState
import xyz.prognoslabs.app.data.AuthStore
import xyz.prognoslabs.app.data.CurrencyStore
import xyz.prognoslabs.app.data.cleanRecognizedText
import xyz.prognoslabs.app.voice.GroqAsrProvider
import xyz.prognoslabs.app.data.DisplayCurrency
import xyz.prognoslabs.app.data.FX_RATES_MAX_AGE_MS
import xyz.prognoslabs.app.data.FxRates
import xyz.prognoslabs.app.data.FxRatesStore
import xyz.prognoslabs.app.data.GREETING_HELLO_ANSWER
import xyz.prognoslabs.app.data.GREETING_HOW_ARE_YOU_ANSWER
import xyz.prognoslabs.app.data.LinkedReturn
import xyz.prognoslabs.app.data.MarketDiskCache
import xyz.prognoslabs.app.data.OrderItem
import xyz.prognoslabs.app.data.OtpPending
import xyz.prognoslabs.app.data.PositionItem
import xyz.prognoslabs.app.data.Repository
import xyz.prognoslabs.app.data.resolvePlatformAnswer
import xyz.prognoslabs.app.data.ThemeStore
import xyz.prognoslabs.app.data.TokenStore
import xyz.prognoslabs.app.data.TradeSizeStore
import xyz.prognoslabs.app.data.TradingRepository
import xyz.prognoslabs.app.data.TradingState
import xyz.prognoslabs.app.data.TradeSide
import xyz.prognoslabs.app.data.VoiceCheck
import xyz.prognoslabs.app.data.VoiceConfirmation
import xyz.prognoslabs.app.data.VoiceFeedbackReporter
import xyz.prognoslabs.app.data.VoiceFeedbackStore
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceFeatures
import xyz.prognoslabs.app.data.VoiceHistoryEntry
import xyz.prognoslabs.app.data.VoiceHistoryOutcome
import xyz.prognoslabs.app.data.VoiceOrder
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.VoiceStore
import xyz.prognoslabs.app.data.WatchlistStore
import xyz.prognoslabs.app.data.addVoiceHistory
import xyz.prognoslabs.app.data.buildVoiceFeedbackReport
import xyz.prognoslabs.app.data.buildVoiceProfile
import xyz.prognoslabs.app.data.challengeMatches
import xyz.prognoslabs.app.data.changePasswordError
import xyz.prognoslabs.app.data.extractVoiceFeatures
import xyz.prognoslabs.app.data.fill
import xyz.prognoslabs.app.data.fetchFxRatesJson
import xyz.prognoslabs.app.data.forecastFor
import xyz.prognoslabs.app.data.formatVoiceAmount
import xyz.prognoslabs.app.data.isAcceptableLinkedReturn
import xyz.prognoslabs.app.data.isValidWalletAddress
import xyz.prognoslabs.app.data.latestNewsFor
import xyz.prognoslabs.app.data.linkMessageText
import xyz.prognoslabs.app.data.linkPageUrl
import xyz.prognoslabs.app.data.marketShortLabel
import xyz.prognoslabs.app.data.orderAmountError
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.data.parseConfirmation
import xyz.prognoslabs.app.data.parseFxRates
import xyz.prognoslabs.app.data.parseLinkedReturn
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.data.pickChallengePhrase
import xyz.prognoslabs.app.data.pickEnrollPhrases
import xyz.prognoslabs.app.data.resendSecondsLeft as resendSecondsLeftFn
import xyz.prognoslabs.app.data.shouldReportVoiceFeedback
import xyz.prognoslabs.app.data.selectTradeSide
import xyz.prognoslabs.app.data.vocabFor
import xyz.prognoslabs.app.data.voiceCheckDecision
import xyz.prognoslabs.app.data.voiceQuestionParams
import xyz.prognoslabs.app.data.walletAddressType
import kotlin.random.Random
import xyz.prognoslabs.app.model.ASSET_LABELS
import xyz.prognoslabs.app.model.BookResponse
import xyz.prognoslabs.app.model.ChartResponse
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.NewsFeed
import xyz.prognoslabs.app.model.TickerAsset
import xyz.prognoslabs.app.model.WatchFlag
import xyz.prognoslabs.app.model.parseNum
import java.util.Locale

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

/**
 * Voice trading (v1.1.7). What the voice sheet is showing, if
 * anything. The sheet is hosted once, above every screen, so the
 * mic works the same from the markets list and from a market.
 */
sealed class VoiceSheet {
    /** Listening sheet: the words live as the user speaks. */
    object Listening : VoiceSheet()

    /** Trade-time check: the app shows and speaks a fresh random
     * challenge phrase, and the user must say it back. The voice
     * check runs on that challenge utterance, so a recording of the
     * fixed enrollment phrases cannot pass it. WORDS is the
     * recognizer pass for the phrase; VOICE is the microphone pass
     * that checks the voice itself. */
    data class VoiceCheck(
        val challenge: String,
        val phase: VoiceCheckPhase,
        val note: String? = null
    ) : VoiceSheet()

    /** Read-back: the order in plain words, answered by voice.
     * v1.1.16: no Confirm / Cancel buttons any more, so no price
     * or share count is carried: you say yes or no out loud, and
     * hanging up cancels. */
    data class Confirm(val order: VoiceOrder, val tradingReady: Boolean) : VoiceSheet()

    /** v1.1.14: the order is being placed, visibly inside the sheet:
     * the order card stays on screen with a spinner while the same
     * placeOrder the tap buttons use runs. The sheet never closes
     * mid-execution. */
    data class Executing(val order: VoiceOrder) : VoiceSheet()

    /** v1.1.14: the filled state, still inside the sheet: the order
     * placed (or the plain reason it could not), with the order card
     * above it. No navigating away. */
    data class Executed(val order: VoiceOrder, val success: Boolean, val message: String) : VoiceSheet()

    /** A plain answer, an ask-again, or a refusal. */
    data class Message(val text: String, val isError: Boolean = false) : VoiceSheet()

    /** A conversational answer, with the session still open: the
     * answer shows and the recognizer keeps listening for the
     * follow-up, so the user does not tap the mic again. */
    data class Conversation(val text: String) : VoiceSheet()

    /** Voice trading needs enrollment first. */
    object EnrollRequired : VoiceSheet()

    /** No speech recognition on this device. */
    object Unavailable : VoiceSheet()
}

/** Which pass of the trade-time voice check is showing. */
enum class VoiceCheckPhase { WORDS, VOICE }

/** Voice enrollment progress: which phrases are done, what is next. */
data class EnrollState(
    /** The three phrases assigned to this user, picked at random
     * from the enrollment pool and stored, so two users almost
     * never enroll on the same words. Empty until enrollment starts. */
    val phrases: List<String> = emptyList(),
    val done: List<Boolean> = listOf(false, false, false),
    val current: Int = 0,
    val listening: Boolean = false,
    val error: String? = null,
    val complete: Boolean = false
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
    private val voiceFeedbackStore = VoiceFeedbackStore(app)
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

    /** The last saved crash report, if the app died on an earlier
     * launch (v1.1.21). Shown in Settings so it can be copied out. */
    private val _lastCrashReport = MutableStateFlow<String?>(null)
    val lastCrashReport: StateFlow<String?> = _lastCrashReport.asStateFlow()

    fun clearCrashReport() {
        CrashReporter.clear(getApplication())
        _lastCrashReport.value = null
    }

    init {
        _lastCrashReport.value = CrashReporter.readLastCrash(getApplication())
    }

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    private val _nowSec = MutableStateFlow(System.currentTimeMillis() / 1000)
    val nowSec: StateFlow<Long> = _nowSec.asStateFlow()

    /**
     * v1.1.8: the clock the market screens read, in whole minutes.
     * The per-second clock changes 60 times a minute and every screen
     * collecting it recomposes 60 times a minute; the time-left and
     * forecast text on those screens only changes once a minute, so
     * they read this instead. StateFlow only notifies when the value
     * changes, so collectors recompose at most once a minute. The
     * one-second clock stays for the OTP resend countdown (the one
     * place seconds matter) and for the market countdown text, which
     * reads it through its own small composable so only that one
     * line recomposes each second.
     */
    val nowMinute: StateFlow<Long> = _nowSec
        .map { it / 60 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis() / 1000 / 60)

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

    /** The change-password flow finished: the new password is saved. */
    private val _changePasswordDone = MutableStateFlow(false)
    val changePasswordDone: StateFlow<Boolean> = _changePasswordDone.asStateFlow()

    /** Trading availability: locked until the server says otherwise. */
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

    // ---- Voice trading (v1.1.7) ----

    private val voiceStore = VoiceStore(app)
    private val voiceEngine = VoiceEngine(app, GroqAsrProvider())

    /** Whether the owner has enrolled their voice for voice trading. */
    private val _voiceEnrolled = MutableStateFlow(voiceStore.loadProfile() != null)
    val voiceEnrolled: StateFlow<Boolean> = _voiceEnrolled.asStateFlow()

    /** Voice history: what was said and what happened to each one. */
    private val _voiceHistory = MutableStateFlow(voiceStore.loadHistory())
    val voiceHistory: StateFlow<List<VoiceHistoryEntry>> = _voiceHistory.asStateFlow()

    /** v1.1.12: the voice language, from the phone's language when
     * this build supports it, English otherwise. Recognition,
     * answers, enrollment and the challenge all follow it. */
    private val _voiceLanguage = MutableStateFlow(voiceEngine.voiceLanguage())
    val voiceLanguage: StateFlow<VoiceLanguage> = _voiceLanguage.asStateFlow()

    /** True when the phone's language is one this build supports
     * for voice. When false the app says so plainly and listens
     * in English. */
    private val _deviceLanguageSupported = MutableStateFlow(voiceEngine.deviceLanguageSupported())
    val deviceLanguageSupported: StateFlow<Boolean> = _deviceLanguageSupported.asStateFlow()

/** Live mic amplitude (0f..1f) for waveform animation. */
private val _voiceAudioLevel = MutableStateFlow(0f)
val voiceAudioLevel: StateFlow<Float> = _voiceAudioLevel.asStateFlow()

/** Watchdog timer so the sheet never gets stuck on "Listening...". */
private var listenWatchdogJob: Job? = null

/** In-app language override, persisted in VoiceStore. */
fun setVoiceLanguage(language: VoiceLanguage, persist: Boolean = true) {
    voiceEngine.setLanguageOverride(language)
    _voiceLanguage.value = language
    _deviceLanguageSupported.value = true
    if (persist) voiceStore.saveVoiceLanguage(language.code)
}

    /** Opt-in anonymous failed-phrase reporting (Flint, 22 Nov):
     * off by default, one toggle in voice settings. */
    val voiceFeedbackOptIn: StateFlow<Boolean> = voiceFeedbackStore.enabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The reporter posts failed phrases in the background; the
     * post is plain OkHttp, and any failure is swallowed. */
    private val voiceFeedbackReporter = VoiceFeedbackReporter { url, json ->
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        fxClient.newCall(request).execute().close()
    }

    /** The voice sheet showing now, if any. */
    private val _voiceSheet = MutableStateFlow<VoiceSheet?>(null)
    val voiceSheet: StateFlow<VoiceSheet?> = _voiceSheet.asStateFlow()

    /** The words heard so far, shown live in the listening sheet. */
    private val _voiceListeningText = MutableStateFlow("")
    val voiceListeningText: StateFlow<String> = _voiceListeningText.asStateFlow()

    /** A route the voice asked the app to open (a market or a tab).
     * The activity collects it, navigates, then clears it. */
    private val _voiceNavRequest = MutableStateFlow<String?>(null)
    val voiceNavRequest: StateFlow<String?> = _voiceNavRequest.asStateFlow()

    /** Clears a navigation request after the activity has acted on it. */
    fun clearVoiceNavRequest() {
        _voiceNavRequest.value = null
    }

    /** Voice enrollment progress. */
    private val _enrollState = MutableStateFlow(EnrollState())
    val enrollState: StateFlow<EnrollState> = _enrollState.asStateFlow()

    /** The activity collects this and asks for the mic permission. */
    private val _voicePermissionNeeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val voicePermissionNeeded: SharedFlow<Unit> = _voicePermissionNeeded.asSharedFlow()

    /** One voice sample per enrollment phrase, replaced on a re-do. */
    private val enrollSamples = mutableMapOf<Int, VoiceFeatures>()
    private var voicePendingStart = false
    private var voicePendingEnrollIndex: Int? = null

    /** An order whose words are recognized and is waiting on the
     * separate voice check pass before it can reach the confirm card. */
    private var voicePendingOrder: VoiceOrder? = null
    private var voicePendingHeard: String? = null

    /** The fresh challenge phrase the voice check is waiting on,
     * and how many wrong tries the user has had at it (two, then
     * the order is refused). While set, final results are challenge
     * answers, not new orders. */
    private var voicePendingChallenge: String? = null
    private var voiceChallengeAttempts = 0
    private var awaitingChallengeWords = false

    /** The confirm card is listening for a spoken yes or no. Two
     * unclear tries, then listening stops and the card waits for
     * the user to hang up: there are no tap buttons on the call
     * screen. */
    private var awaitingConfirmation = false
    private var confirmUnclearTries = 0
    private val _confirmListening = MutableStateFlow(false)
    val confirmListening: StateFlow<Boolean> = _confirmListening.asStateFlow()

    /** Guards stale engine events when a new listen starts. */
    private var voiceSession = 0

    /**
     * v1.1.14: silent misses in a row (the recognizer heard nothing)
     * while a voice session is open. A quiet room is not a reason to
     * hang up: the session listens again a few times before it gives
     * the user a message. Reset by anything heard.
     */
    private var voiceSilentErrors = 0

    private var refreshJob: Job? = null

    /** Guards refreshAll: one refresh at a time, never overlapping. */
    private val refreshMutex = Mutex()

    /** Guards one detail refresh at a time while live polling runs. */
    private val detailMutex = Mutex()

    /** Live polling for the open market detail screen, if any. */
    private var detailPollJob: Job? = null
    private var detailPollId: String? = null

    init {
        voiceStore.loadVoiceLanguage()?.let { setVoiceLanguage(it, persist = false) }
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
        // A double tap could otherwise send two codes; the server
        // invalidates the earlier code, so the second send kills the
        // code the user just received.
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.signUp(name, email, password)
            _authBusy.value = false
        }
    }

    fun logIn(email: String, password: String) {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.logIn(email, password)
            _authBusy.value = false
        }
    }

    fun verifyOtp(code: String) {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.verifyOtp(code)
            _authBusy.value = false
        }
    }

    fun resendOtp() {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = authStore.resendOtp()
            _authBusy.value = false
        }
    }

    fun requestPasswordReset(email: String) {
        if (_authBusy.value) return
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
     * Change password, as its own request flow (v1.1.12: no longer a
     * section on the profile page). The server has no
     * change-password call, so it goes through the reset flow: a
     * code goes to the signed-in email, then the code step, then
     * the new password. There is no current password check: the
     * code sent to the account email is the check.
     */

    /** Step 1: a code goes to the signed-in email. */
    fun requestChangePasswordCode() {
        viewModelScope.launch {
            val email = auth.value.email
            if (email.isBlank()) {
                _settingsMessage.value = "Please sign in again."
                _settingsMessageIsError.value = true
                return@launch
            }
            _settingsMessage.value = null
            val err = authStore.requestPasswordReset(email)
            if (err != null) {
                _settingsMessage.value = err
                _settingsMessageIsError.value = true
            } else {
                _settingsMessage.value = "A code was sent to $email. Enter it below."
                _settingsMessageIsError.value = false
            }
        }
    }

    /** Step 2: the code from the email. A wrong code is a plain
     * message, not a dead end: the user can try again. */
    fun verifyChangePasswordCode(code: String) {
        viewModelScope.launch {
            _settingsMessage.value = null
            val err = authStore.verifyOtp(code)
            if (err != null) {
                _settingsMessage.value = err
                _settingsMessageIsError.value = true
            }
        }
    }

    /** Step 3: the new password, after the code was verified. */
    fun saveNewPasswordAfterChange(newPassword: String, confirm: String) {
        viewModelScope.launch {
            val err = changePasswordError(true, newPassword, confirm)
            if (err != null) {
                _settingsMessage.value = err
                _settingsMessageIsError.value = true
                return@launch
            }
            val resetErr = authStore.confirmPasswordReset(newPassword)
            if (resetErr != null) {
                _settingsMessage.value = resetErr
                _settingsMessageIsError.value = true
            } else {
                _settingsMessage.value = null
                _changePasswordDone.value = true
            }
        }
    }

    /** Leaves the change-password flow: clears the code state and
     * the done flag, so the next visit starts fresh. */
    fun clearChangePasswordFlow() {
        authStore.clearPending()
        _settingsMessage.value = null
        _changePasswordDone.value = false
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
     *
     * v1.1.8: the DataStore reads and the JSON parse used to run on
     * the main thread; they now run on IO, and a refresh already
     * running is not started again.
     */
    private var ratesJob: Job? = null

    fun refreshRates() {
        if (ratesJob?.isActive == true) return
        ratesJob = viewModelScope.launch(Dispatchers.IO) {
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
                        // v1.1.14: the top movers come from the ticker,
                        // so the dashboard shows them as soon as the
                        // ticker is in. The old code left the
                        // full-screen spinner up until the slow
                        // markets call finished too, hiding the
                        // movers behind it for seconds.
                        _home.value = _home.value.copy(loading = false)
                    }
                    newsJob.await()?.let { news ->
                        _home.value = _home.value.copy(news = news)
                    }
                    val markets = marketsJob.await()
                    if (markets != null) {
                        val prev = _home.value.markets.associate { it.id to it.p0 }
                        _home.value = _home.value.copy(markets = markets, prevPrices = prev)
                        // v1.1.8: the forecast read runs off the main
                        // thread; it used to run on it, one string
                        // build per market on every refresh.
                        viewModelScope.launch(Dispatchers.Default) { repo.recordForecasts(markets) }
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
            // v1.1.8: the books and chart are cached (see Repository),
            // so reopening a market shows the last real data at once
            // instead of a spinner, then refreshes in the background.
            // The market itself comes from the list when it is there:
            // the 30s home refresh keeps it fresh, so opening a market
            // no longer spends a network call on a market the list
            // already has. A deep link with no listed market still
            // fetches it.
            val upToken = listed?.outcomes?.getOrNull(0)?.tokenId
            val downToken = listed?.outcomes?.getOrNull(1)?.tokenId
            val cachedUp = upToken?.let { repo.cachedBook(it) }
            val cachedDown = downToken?.let { repo.cachedBook(it) }
            val cachedChart = repo.cachedChart(id)
            if (listed != null && (cachedUp != null || cachedDown != null || cachedChart != null)) {
                _detail.value = DetailState(
                    loading = false, market = listed,
                    bookUp = cachedUp, bookDown = cachedDown, chart = cachedChart
                )
            } else {
                _detail.value = DetailState(loading = true, market = listed)
            }
            // v1.1.2: market, both books and chart used to load one
            // after another (~4s). They now run in parallel; the
            // books use the listed market's token ids, which are the
            // same ones the fresh market carries.
            coroutineScope {
                val marketJob = async { if (listed == null) repo.loadMarket(id) else null }
                val upJob = async { upToken?.let { repo.loadBook(it) } }
                val downJob = async { downToken?.let { repo.loadBook(it) } }
                val chartJob = async { repo.loadChart(id) }
                val m = marketJob.await() ?: listed
                _detail.value = DetailState(
                    loading = false, market = m,
                    bookUp = upJob.await() ?: _detail.value.bookUp,
                    bookDown = downJob.await() ?: _detail.value.bookDown,
                    chart = chartJob.await() ?: _detail.value.chart
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

    /**
     * Last trading-state check, so the automatic 30s refresh does not
     * re-check a linked wallet every 30 seconds: trading is closed
     * and the state barely changes. Link and order flows force a
     * fresh check.
     */
    private var lastTradingCheckMs = 0L

    fun refreshTrading(force: Boolean = false) {
        if (!force) {
            val now = System.currentTimeMillis()
            if (now - lastTradingCheckMs < 60_000) return
            lastTradingCheckMs = now
        } else {
            lastTradingCheckMs = System.currentTimeMillis()
        }
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

    fun placeOrder(marketId: String, tokenId: String, side: TradeSide, amount: String, onDone: (Boolean) -> Unit = {}) {
        // A double tap within the same frame could otherwise launch
        // two order coroutines before the busy flag landed, placing
        // two orders. The guard is checked first, before any work,
        // and the flag is set at once so the second tap sees it.
        if (_tradeBusy.value) return
        _tradeBusy.value = true
        viewModelScope.launch {
            try {
                val state = _tradingState.value
                if (state !is TradingState.Ready) {
                    _tradeMessage.value = "Please link your wallet to Prognos Labs first."
                    _tradeMessageIsError.value = true
                    onDone(false)
                    return@launch
                }
                val session = tokens.tradingSession
                if (session.isNullOrBlank()) {
                    _tradeMessage.value = "Please link your wallet to Prognos Labs first."
                    _tradeMessageIsError.value = true
                    onDone(false)
                    return@launch
                }
                val (orderId, err) = tradingRepo.placeOrder(state.address, session, tokenId, side, amount)
                if (err != null) {
                    _tradeMessage.value = err
                    _tradeMessageIsError.value = true
                    if (err.contains("link your wallet")) refreshTrading(force = true)
                    onDone(false)
                } else {
                    _tradeMessage.value = "Order placed" + (if (!orderId.isNullOrBlank()) " ($orderId)" else "") + ". It may take a moment to fill."
                    _tradeMessageIsError.value = false
                    loadTradingData(state.address, session)
                    onDone(true)
                }
            } finally {
                _tradeBusy.value = false
            }
        }
    }

    // ---- Voice trading (v1.1.7) ----

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /** The mic button was tapped. Tap-to-talk only: the app never
     * listens unless the mic is pressed, and never answers on its own. */
    fun onMicTapped() {
        if (!voiceEngine.isAvailable()) {
            _voiceSheet.value = VoiceSheet.Unavailable
            return
        }
        if (!_voiceEnrolled.value) {
            _voiceSheet.value = VoiceSheet.EnrollRequired
            return
        }
        if (!hasMicPermission()) {
            voicePendingStart = true
            _voicePermissionNeeded.tryEmit(Unit)
            return
        }
        startVoiceListening()
    }

    /** The activity reports the mic permission result here. */
    fun onVoicePermissionResult(granted: Boolean) {
        if (!granted) {
            voicePendingStart = false
            voicePendingEnrollIndex = null
            _voiceSheet.value = VoiceSheet.Message(
                "Microphone permission is needed for voice trading. The tap buttons still work.",
                isError = true
            )
            return
        }
        if (voicePendingStart) {
            voicePendingStart = false
            startVoiceListening()
            return
        }
        val enrollIndex = voicePendingEnrollIndex
        if (enrollIndex != null) {
            voicePendingEnrollIndex = null
            startEnrollListening(enrollIndex)
        }
    }

    private fun startVoiceListening() {
        _voiceListeningText.value = ""
        _voiceSheet.value = VoiceSheet.Listening
        restartListening()
    }

    /** Starts the recognizer again without changing the sheet: the
     * follow-up after a conversational answer. Tap-to-talk still
     * rules: this only runs while a session the user started is open. */
private fun restartListening() {
    voiceSession++
    val session = voiceSession
    _voiceAudioLevel.value = 0f

    listenWatchdogJob?.cancel()
    listenWatchdogJob = viewModelScope.launch {
        delay(10_000L)
        if (session == voiceSession &&
            (_voiceSheet.value is VoiceSheet.Listening ||
                _voiceSheet.value is VoiceSheet.VoiceCheck ||
                awaitingConfirmation)
        ) {
            voiceEngine.stopListening()
            handleVoiceEvent(
                VoiceEngine.Event.Error("I could not hear you. Try again.", silent = true),
                session
            )
        }
    }

    voiceEngine.startListening { event ->
        viewModelScope.launch { handleVoiceEvent(event, session) }
    }
}

    /** Stops listening. Stops whichever voice pass is running: the
     * words pass, or the voice check pass. Stopping the check pass
     * means "I am done speaking", so the check still runs on what
     * was recorded. */
    fun stopVoiceListening() {
        listenWatchdogJob?.cancel()
        voiceEngine.stopListening()
        voiceEngine.stopVoiceCapture()
    }

    /** Closes the voice sheet without doing anything. If the app was
     * listening, the recognizer stops and its pending result is
     * ignored, so the sheet does not pop back up. If a voice check
     * was running, its result is ignored the same way. */
    fun dismissVoiceSheet() {
        val sheet = _voiceSheet.value
        if (sheet is VoiceSheet.Listening || sheet is VoiceSheet.VoiceCheck || sheet is VoiceSheet.Conversation) {
            listenWatchdogJob?.cancel()
            voiceSession++ // the pending result is stale; ignore it
            voiceEngine.stopListening()
            voiceEngine.stopVoiceCapture()
            clearChallengeState()
            voicePendingOrder = null
            voicePendingHeard = null
        }
        _voiceSheet.value = null
    }

    private fun handleVoiceEvent(event: VoiceEngine.Event, session: Int) {
        if (session != voiceSession) return // a stale listen, ignore it
        when (event) {
            is VoiceEngine.Event.Partial -> {
                voiceSilentErrors = 0
                // v1.1.14: a partial is only the live words for the
                // sheet. Silence must never show as speech, so the
                // words are cleaned first; a silent stretch shows
                // nothing at all.
                _voiceListeningText.value = (cleanRecognizedText(event.text) ?: "").take(200)
            }
            is VoiceEngine.Event.Level -> {
                _voiceAudioLevel.value = event.rmsNorm
            }
            is VoiceEngine.Event.Final -> {
                listenWatchdogJob?.cancel()
                _voiceAudioLevel.value = 0f
                voiceSilentErrors = 0
                // v1.1.14: a final result is cleaned the same way
                // before it reaches the parser, so silence never
                // becomes a word the app answers to.
                val cleaned = cleanRecognizedText(event.text)
                if (cleaned == null) return
                val cleanedEvent = event.copy(
                    text = cleaned,
                    candidates = event.candidates.mapNotNull { cleanRecognizedText(it) }
                )
                // During the challenge pass, a final result is the
                // user saying the challenge phrase, not a new order.
                // On the confirm card it is a spoken yes or no.
                if (awaitingConfirmation) processConfirmFinal(cleanedEvent)
                else if (awaitingChallengeWords) processChallengeFinal(cleanedEvent)
                else processVoiceFinal(cleanedEvent)
            }
            is VoiceEngine.Event.Error -> {
                listenWatchdogJob?.cancel()
                _voiceAudioLevel.value = 0f
                if (_voiceSheet.value is VoiceSheet.Conversation) {
                    // v1.1.14: the follow-up listen after an answer
                    // failed. The answer stays on screen: the user
                    // reads it and taps the mic again to speak.
                    // Hiding it behind an error would throw away the
                    // answer they just asked for.
                } else if (awaitingConfirmation) {
                    // The recognizer heard nothing on the confirm
                    // card: one more listen, then stop listening and
                    // leave the card up until the user hangs up.
                    confirmUnclearTries++
                    if (confirmUnclearTries >= 2) {
                        awaitingConfirmation = false
                        _confirmListening.value = false
                    } else {
                        restartListening()
                    }
                } else if (awaitingChallengeWords) {
                    clearChallengeState()
                    _voiceSheet.value = VoiceSheet.Message(
                        "I did not catch the phrase, so I will not place the order by voice. The tap buttons still work.",
                        isError = true
                    )
                } else if (event.silent &&
                    (_voiceSheet.value is VoiceSheet.Listening || _voiceSheet.value is VoiceSheet.Conversation)
                ) {
                    // v1.1.14: the recognizer heard nothing while a
                    // session is open. A quiet room is not a reason
                    // to hang up: listen again, and only after three
                    // silent misses in a row tell the user.
                    voiceSilentErrors++
                    if (voiceSilentErrors >= 3) {
                        voiceSilentErrors = 0
                        _voiceSheet.value = VoiceSheet.Message(event.message, isError = true)
                    } else {
                        restartListening()
                    }
                } else {
                    _voiceSheet.value = VoiceSheet.Message(event.message, isError = true)
                }
            }
            VoiceEngine.Event.Unavailable -> {
                listenWatchdogJob?.cancel()
                _voiceAudioLevel.value = 0f
                // v1.1.14: same as the error above: a dead mic after
                // an answer must not hide the answer.
                if (_voiceSheet.value !is VoiceSheet.Conversation) {
                    _voiceSheet.value = VoiceSheet.Unavailable
                }
            }
        }
    }

    /** The words are in. The recognizer offers several candidates;
     * they are scored against the markets and the best parse wins,
     * so one misheard top result does not lose the order. Questions
     * are answered straight away; an order first goes through the
     * separate voice check pass, so the recognizer and the microphone
     * capture never run together. v1.1.14: every answer keeps the
     * session open for the follow-up, so the user never has to tap
     * the mic again mid-conversation. */
    private fun processVoiceFinal(event: VoiceEngine.Event.Final) {
        // Reply language for this spoken turn only. The detected spoken
        // language wins when the recognizer reported one; otherwise the
        // language set in Settings. Never mutates _voiceLanguage.
        // For Igbo: Groq does not support Igbo, so recognition runs as
        // English and detectedLanguage is null (or English) unless a path
        // truly signals Igbo.
        val setLang = _voiceLanguage.value
        val turnLang = event.detectedLanguage ?: setLang
        val (parsed, heard) = parseBestVoiceUtterance(
            event.candidates, _home.value.markets, _detail.value.market?.id, turnLang
        )
        // v1.1.15: the words the user just said show as text in the
        // sheet. Partials only arrive while speaking, and a short
        // sentence ends before the first one, so the final result
        // is what puts the words on screen.
        _voiceListeningText.value = heard.take(200)
        when (parsed) {
            is VoiceParseResult.Order -> startVoiceCheck(heard, parsed.order)
            is VoiceParseResult.Navigate -> {
                recordVoiceHistory(heard, VoiceHistoryOutcome.NAVIGATED)
                _voiceNavRequest.value = parsed.route
                _voiceSheet.value = VoiceSheet.Message("Opened ${parsed.label}.")
            }
            is VoiceParseResult.Question -> {
                // For platform knowledge questions, enforce the exact 3-step fallback:
                // detected language -> set language -> English.
                val answer = if (parsed.question is VoiceQuestion.Knowledge) {
                    resolvePlatformAnswer(
                        heard.lowercase(),
                        event.detectedLanguage,
                        setLang
                    )?.first ?: answerVoiceQuestion(parsed.question, turnLang)
                } else {
                    answerVoiceQuestion(parsed.question, turnLang)
                }
                recordVoiceHistory(heard, VoiceHistoryOutcome.ANSWERED)
                if (parsed.question.isConversational) {
                    _voiceSheet.value = VoiceSheet.Conversation(answer)
                    restartListening()
                } else {
                    _voiceSheet.value = VoiceSheet.Message(answer)
                }
            }
            is VoiceParseResult.AskAgain -> {
                maybeReportVoiceFeedback(parsed, heard)
                // v1.1.14: "say it again" means the session stays
                // open so the user can say it again, without
                // tapping the mic.
                _voiceSheet.value = VoiceSheet.Conversation(parsed.message)
                restartListening()
            }
            is VoiceParseResult.OutOfScope -> {
                recordVoiceHistory(heard, VoiceHistoryOutcome.ANSWERED)
                maybeReportVoiceFeedback(parsed, heard)
                // v1.1.14: the refusal is not the end of the call
                // either: the user may ask a question that is in
                // scope next, without tapping the mic again.
                _voiceSheet.value = VoiceSheet.Conversation(parsed.message)
                restartListening()
            }
        }
    }

    /** Clears the voice check challenge state: no pending phrase,
     * no attempts counted, not waiting on challenge words. */
    private fun clearChallengeState() {
        awaitingChallengeWords = false
        voicePendingChallenge = null
        voiceChallengeAttempts = 0
    }

    /** Voice check pass, after the words: the app shows a fresh
     * random challenge phrase, and the user must say it.
     * The phrase is different every time, so a recording of the
     * fixed enrollment phrases cannot pass it. This is
     * challenge-response anti-replay, not true liveness detection:
     * the login and the read-back confirmation stay the real
     * protection. Only a matched voice reaches the confirm card. */
    private fun startVoiceCheck(heard: String, order: VoiceOrder) {
    voicePendingOrder = order
    voicePendingHeard = heard
    voicePendingChallenge = pickChallengePhrase(
        vocabFor(_voiceLanguage.value).challengePhrases, Random.Default
    )
    voiceChallengeAttempts = 0
    awaitingChallengeWords = true
    val challenge = voicePendingChallenge ?: return
    _voiceSheet.value = VoiceSheet.VoiceCheck(challenge, VoiceCheckPhase.WORDS)
    restartListening()
}

    private fun processChallengeFinal(event: VoiceEngine.Event.Final) {
    val challenge = voicePendingChallenge ?: return
    val matched = event.candidates.any { challengeMatches(it, challenge, _voiceLanguage.value) }
    Analytics.logEvent(
        AnalyticsEvents.VOICE_CHALLENGE,
        mapOf(AnalyticsEvents.PARAM_SUCCESS to matched.toString())
    )
    if (!matched) {
        voiceChallengeAttempts++
        if (voiceChallengeAttempts >= 2) {
            val heard = voicePendingHeard
            if (heard != null) recordVoiceHistory(heard, VoiceHistoryOutcome.CANCELLED)
            voicePendingOrder = null
            voicePendingHeard = null
            clearChallengeState()
            _voiceSheet.value = VoiceSheet.Message(
                "That was not the phrase I asked for, so I will not place the order by voice. The tap buttons still work.",
                isError = true
            )
            return
        }
        _voiceSheet.value = VoiceSheet.VoiceCheck(
            challenge, VoiceCheckPhase.WORDS,
            note = "That was not the phrase. Say it again."
        )
        restartListening()
        return
    }

    awaitingChallengeWords = false

    // Single-pass voice check: reuse the PCM captured during the challenge phrase.
    if (event.pcm.isNotEmpty()) {
        voiceSession++
        val session = voiceSession
        handleVoiceCheckResult(VoiceEngine.VoiceSample.Captured(event.pcm, 16_000), session)
        return
    }

    // Fallback to second pass only if PCM was not attached.
    _voiceSheet.value = VoiceSheet.VoiceCheck(challenge, VoiceCheckPhase.VOICE)
    voiceSession++
    val session = voiceSession
    voiceEngine.startVoiceCapture(maxSeconds = 5) { sample ->
        viewModelScope.launch { handleVoiceCheckResult(sample, session) }
    }
}

    private fun handleVoiceCheckResult(sample: VoiceEngine.VoiceSample, session: Int) {
        if (session != voiceSession) return // dismissed or moved on; ignore it
        val order = voicePendingOrder ?: return
        val heard = voicePendingHeard ?: return
        val challenge = voicePendingChallenge
        voicePendingOrder = null
        voicePendingHeard = null
        voicePendingChallenge = null
        viewModelScope.launch(Dispatchers.Default) {
            val pcm = (sample as? VoiceEngine.VoiceSample.Captured)?.pcm
            // The feature check runs on the challenge words, not the
            // order words: the word count sets the speaking-rate
            // feature, so it must match what was actually said.
            val words = challenge?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 1
            val features = pcm?.let { extractVoiceFeatures(it, 16_000, words) }
            val check = voiceCheckDecision(voiceStore.loadProfile(), features)
            withContext(Dispatchers.Main) {
                if (session != voiceSession) return@withContext
                onVoiceOrder(heard, order, check)
            }
        }
    }

    private fun onVoiceOrder(heard: String, order: VoiceOrder, check: VoiceCheck) {
        when (check) {
            VoiceCheck.UNAVAILABLE -> {
                // No usable audio: the lock cannot check, so the order
                // does not go through by voice. The tap buttons stay.
                recordVoiceHistory(heard, VoiceHistoryOutcome.CANCELLED)
                _voiceSheet.value = VoiceSheet.Message(
                    "I could not check your voice this time, so I will not place the order by voice. The tap buttons still work.",
                    isError = true
                )
            }
            VoiceCheck.NOT_MATCHED -> {
                recordVoiceHistory(heard, VoiceHistoryOutcome.CANCELLED)
                _voiceSheet.value = VoiceSheet.Message(
                    "That did not sound like your voice. Voice trading is locked to the voice you set up.",
                    isError = true
                )
            }
            VoiceCheck.MATCHED -> {
                val ready = _tradingState.value is TradingState.Ready
                _voiceSheet.value = VoiceSheet.Confirm(order, ready)
                // The read-back is on screen; the user answers it
                // out loud. Listening starts now, capped at two
                // unclear tries, then the card waits for hang-up.
                awaitingConfirmation = true
                confirmUnclearTries = 0
                _confirmListening.value = true
                restartListening()
            }
        }
    }

    /** The user confirmed the read-back: place it through the same
     * placeOrder the tap buttons use. No second trading path. The
     * execution happens visibly inside the sheet: the order card
     * stays up with a spinner while it places, then the filled
     * state, never a jump away from the sheet. */
    data class BiometricTradeRequest(val requestId: Long)

    private sealed class PendingBiometricTrade {
        data class Voice(
            val order: VoiceOrder,
            val marketId: String,
            val tokenId: String,
            val asset: String
        ) : PendingBiometricTrade()

        data class Tapped(
            val marketId: String,
            val tokenId: String,
            val side: TradeSide,
            val amount: String,
            val onDone: (Boolean) -> Unit
        ) : PendingBiometricTrade()
    }

    private val _biometricTradeRequest = MutableSharedFlow<BiometricTradeRequest>(extraBufferCapacity = 1)
    val biometricTradeRequest: SharedFlow<BiometricTradeRequest> = _biometricTradeRequest.asSharedFlow()

    private var pendingBiometricRequestId: Long = 0L
    private var pendingBiometricTrade: PendingBiometricTrade? = null

    /**
     * Voice confirm path: called only after the user says yes to the read back.
     * Stays behind the closed trading gate; never shows the prompt while trading is closed.
     */
    fun confirmVoiceOrder() {
        val sheet = _voiceSheet.value as? VoiceSheet.Confirm ?: return
        awaitingConfirmation = false
        _confirmListening.value = false
        voiceSession++ // a pending listen result is stale; ignore it
        voiceEngine.stopListening()
        val order = sheet.order
        if (!sheet.tradingReady || _tradingState.value !is TradingState.Ready) return

        val market = _home.value.markets.find { it.id == order.marketId }
            ?: _detail.value.market?.takeIf { it.id == order.marketId }
        val tokenId = market?.let {
            if (order.side == TradeSide.UP) it.outcomes.getOrNull(0)?.tokenId
            else it.outcomes.getOrNull(1)?.tokenId
        }
        if (market == null || tokenId.isNullOrBlank()) {
            _voiceSheet.value = VoiceSheet.Message(
                "I could not find that market any more. Say it again.",
                isError = true
            )
            return
        }

        val requestId = ++pendingBiometricRequestId
        pendingBiometricTrade = PendingBiometricTrade.Voice(
            order = order,
            marketId = order.marketId,
            tokenId = tokenId,
            asset = market.asset ?: "more"
        )
        _biometricTradeRequest.tryEmit(BiometricTradeRequest(requestId))
    }

    /**
     * Tap Confirm button path: route the on-screen Confirm button here so no
     * execution path can skip the biometric gate. Stays behind the closed trading gate.
     */
    fun requestTappedOrder(
        marketId: String,
        tokenId: String,
        side: TradeSide,
        amount: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (_tradingState.value !is TradingState.Ready) {
            onDone(false)
            return
        }
        val requestId = ++pendingBiometricRequestId
        pendingBiometricTrade = PendingBiometricTrade.Tapped(
            marketId = marketId,
            tokenId = tokenId,
            side = side,
            amount = amount,
            onDone = onDone
        )
        _biometricTradeRequest.tryEmit(BiometricTradeRequest(requestId))
    }

    /**
     * Handles the result from BiometricGate.
     * Only BiometricResult.Success moves the trade to placeOrder().
     */
    fun onBiometricTradeResult(requestId: Long, result: BiometricResult) {
        if (requestId != pendingBiometricRequestId) return
        val pending = pendingBiometricTrade ?: return
        pendingBiometricTrade = null

        if (result !is BiometricResult.Success) {
            val refusal = if (result is BiometricResult.NotSetUp) {
                BiometricGate.REFUSAL_NOT_SET_UP
            } else {
                BiometricGate.REFUSAL_FAILED_OR_CANCELLED
            }
            when (pending) {
                is PendingBiometricTrade.Voice -> {
                    recordVoiceHistory(pending.order.readback, VoiceHistoryOutcome.CANCELLED)
                    _voiceSheet.value = VoiceSheet.Message(refusal, isError = true)
                }
                is PendingBiometricTrade.Tapped -> {
                    _tradeMessage.value = refusal
                    pending.onDone(false)
                }
            }
            return
        }

        // Re-check the closed trading gate before execution.
        if (_tradingState.value !is TradingState.Ready) return

        when (pending) {
            is PendingBiometricTrade.Voice -> executeConfirmedVoiceOrder(pending)
            is PendingBiometricTrade.Tapped -> {
                placeOrder(
                    pending.marketId,
                    pending.tokenId,
                    pending.side,
                    pending.amount,
                    pending.onDone
                )
            }
        }
    }

    private fun executeConfirmedVoiceOrder(pending: PendingBiometricTrade.Voice) {
        val order = pending.order
        _voiceSheet.value = VoiceSheet.Executing(order)
        placeOrder(order.marketId, pending.tokenId, order.side, formatVoiceAmount(order.amountUsd)) { ok ->
            recordVoiceHistory(
                order.readback,
                if (ok) VoiceHistoryOutcome.TRADED else VoiceHistoryOutcome.CANCELLED
            )
            // v1.1.10: log the confirmed voice order for Analytics.
            // Coarse only: side, market asset, success. Never the amount.
            Analytics.logEvent(
                AnalyticsEvents.VOICE_ORDER,
                mapOf(
                    AnalyticsEvents.PARAM_SIDE to if (order.side == TradeSide.UP) "up" else "down",
                    AnalyticsEvents.PARAM_ASSET to pending.asset,
                    AnalyticsEvents.PARAM_SUCCESS to ok.toString()
                )
            )
            val msg = _tradeMessage.value
                ?: if (ok) "Order placed." else "Order could not be placed. Try again."
            // The user may have hung up while it placed: then the
            // sheet is gone and the result stays gone with it, so
            // the sheet never pops back up on its own.
            if (_voiceSheet.value is VoiceSheet.Executing) {
                _voiceSheet.value = VoiceSheet.Executed(order, ok, msg)
            }
        }
    }

    /** The user cancelled the read-back: nothing is placed. */
    fun cancelVoiceOrder() {
        val sheet = _voiceSheet.value as? VoiceSheet.Confirm ?: return
        awaitingConfirmation = false
        _confirmListening.value = false
        voiceSession++ // a pending listen result is stale; ignore it
        voiceEngine.stopListening()
        recordVoiceHistory(sheet.order.readback, VoiceHistoryOutcome.CANCELLED)
        _voiceSheet.value = null
    }

    /** The user answered the read-back out loud. A yes confirms the
     * order, a no cancels it, an unclear answer gets one more
     * listen and then listening stops: the card stays up until the
     * user hangs up, since there are no tap buttons on it. */
    private fun processConfirmFinal(event: VoiceEngine.Event.Final) {
        val result = event.candidates
            .map { parseConfirmation(it, _voiceLanguage.value) }
            .firstOrNull { it != VoiceConfirmation.UNCLEAR }
            ?: VoiceConfirmation.UNCLEAR
        when (result) {
            VoiceConfirmation.YES -> {
                awaitingConfirmation = false
                _confirmListening.value = false
                confirmVoiceOrder()
            }
            VoiceConfirmation.NO -> {
                awaitingConfirmation = false
                _confirmListening.value = false
                cancelVoiceOrder()
            }
            VoiceConfirmation.UNCLEAR -> {
                confirmUnclearTries++
                if (confirmUnclearTries >= 2) {
                    awaitingConfirmation = false
                    _confirmListening.value = false
                } else {
                    restartListening()
                }
            }
        }
    }

    /** Ends the voice call: hangs up whatever the voice session is
     * doing. On the confirm card it cancels the order, like tapping
     * Cancel. The overlay going away is the end of the session. */
    fun hangUpVoice() {
        if (_voiceSheet.value is VoiceSheet.Confirm) {
            cancelVoiceOrder()
            return
        }
        dismissVoiceSheet()
    }

    /** The opt-in toggle in voice settings. Off by default. */
    fun setVoiceFeedbackOptIn(enabled: Boolean) {
        viewModelScope.launch { voiceFeedbackStore.setEnabled(enabled) }
    }

    /** Sends a failed phrase back for training, only when the user
     * opted in, only the phrase text, the language and the app
     * version. Best-effort on IO: it never blocks the session, and
     * a failed send is silent. */
    private fun maybeReportVoiceFeedback(parsed: VoiceParseResult, heard: String) {
        if (!shouldReportVoiceFeedback(parsed)) return
        val report = buildVoiceFeedbackReport(
            heard, _voiceLanguage.value.code, BuildConfig.VERSION_NAME
        )
        viewModelScope.launch(Dispatchers.IO) {
            voiceFeedbackReporter.report(report, voiceFeedbackOptIn.value)
        }
    }

    private fun answerVoiceQuestion(
        q: VoiceQuestion,
        replyLanguage: VoiceLanguage = _voiceLanguage.value
    ): String {
        // v1.1.10: log the answered voice question for Analytics.
        // Coarse only: the question type, plus the asset code where
        // the question names one.
        Analytics.logEvent(AnalyticsEvents.VOICE_QUESTION, voiceQuestionParams(q))
        // v1.1.12: answers follow the voice language. The English
        // strings are byte-identical to the old ones.
        val vocab = vocabFor(replyLanguage)
        return when (q) {
        is VoiceQuestion.Price -> {
            val asset = _home.value.ticker.find { it.asset == q.asset }
            val label = ASSET_LABELS[q.asset] ?: q.asset
            val price = asset?.price
            if (price != null) fill(vocab.answerPrice, "label" to label, "price" to formatUsd(price))
            else fill(vocab.answerNoPrice, "label" to label)
        }
        VoiceQuestion.Balance -> {
            val b = _balance.value
            if (b != null) fill(vocab.answerBalance, "balance" to formatUsd(b))
            else vocab.answerNoBalance
        }
        VoiceQuestion.HowToTrade -> vocab.answerHowToTrade
        VoiceQuestion.TradingOpens -> vocab.answerTradingOpen
        VoiceQuestion.GreetingHello -> if (replyLanguage == VoiceLanguage.ENGLISH) GREETING_HELLO_ANSWER else vocab.greetingAnswer
        VoiceQuestion.GreetingHowAreYou -> if (replyLanguage == VoiceLanguage.ENGLISH) GREETING_HOW_ARE_YOU_ANSWER else vocab.howAreYouAnswer
        VoiceQuestion.CheckIn -> vocab.checkInAnswer
        VoiceQuestion.MostLiquid -> {
            val markets = _home.value.markets
            if (markets.isEmpty()) {
                vocab.answerNoMarkets
            } else {
                val best = markets.maxByOrNull { parseNum(it.vol24) ?: parseNum(it.volume) ?: 0.0 }
                val vol = best?.let { parseNum(it.vol24) ?: parseNum(it.volume) ?: 0.0 } ?: 0.0
                if (best == null || vol <= 0.0) {
                    vocab.answerNoVolume
                } else {
                    fill(vocab.answerLiquid, "market" to marketShortLabel(best), "volume" to formatUsd(vol))
                }
            }
        }
        is VoiceQuestion.Knowledge -> q.answer
        is VoiceQuestion.News -> {
            val label = ASSET_LABELS[q.asset] ?: q.asset
            val items = latestNewsFor(_home.value.news.items, q.asset)
            if (items.isEmpty()) {
                // No news in the app for this coin: say so plainly,
                // and offer the forecast and the price instead. Never
                // invent news, and never present a price move as news.
                val market = _home.value.markets.firstOrNull { it.asset == q.asset }
                val price = _home.value.ticker.find { it.asset == q.asset }?.price
                val verdict = market?.let { forecastFor(it)?.verdict }
                val extra = buildString {
                    if (price != null) append(" $label is ${formatUsd(price)}.")
                    if (verdict != null) append(" The forecast for the $label market is $verdict.")
                }
                fill(vocab.answerNoNews, "label" to label, "extra" to extra)
            } else {
                val first = items.first()
                val source = first.source?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val more = if (items.size > 1) " There are ${items.size} stories." else ""
                fill(
                    vocab.answerNews,
                    "label" to label,
                    "title" to (first.title ?: "No headline"),
                    "source" to source,
                    "more" to more
                )
            }
        }
        is VoiceQuestion.MarketInfo -> {
            val m = _home.value.markets.find { it.id == q.marketId }
                ?: _detail.value.market?.takeIf { it.id == q.marketId }
            if (m == null) {
                vocab.answerNoMarket
            } else {
                val yes = m.p0?.let { formatOutcomePrice(it) } ?: "-"
                val no = m.p1?.let { formatOutcomePrice(it) } ?: "-"
                fill(vocab.answerMarket, "question" to m.question, "yes" to yes, "no" to no)
            }
        }
    }
    }

    private fun formatUsd(v: Double): String = "$" + String.format(Locale.US, "%,.2f", v)

    private fun formatOutcomePrice(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun recordVoiceHistory(text: String, outcome: VoiceHistoryOutcome) {
        val updated = addVoiceHistory(
            _voiceHistory.value,
            VoiceHistoryEntry(text, outcome, System.currentTimeMillis())
        )
        _voiceHistory.value = updated
        voiceStore.saveHistory(updated)
    }

    fun clearVoiceHistory() {
        _voiceHistory.value = emptyList()
        voiceStore.clearHistory()
    }

    // ---- Voice enrollment ----

    /** Starts (or restarts) voice enrollment from the first phrase.
     * The three phrases are picked at random from the pool and
     * stored, so each user enrolls on different words; coming back
     * mid-enrollment reuses the stored assignment instead of
     * dealing a new hand. */
    fun startEnrollment() {
        enrollSamples.clear()
        val assigned = voiceStore.loadEnrollPhrases()
        val phrases = if (assigned != null && assigned.size == 3 && assigned.distinct().size == 3) {
            assigned
        } else {
            pickEnrollPhrases(
                vocabFor(_voiceLanguage.value).enrollPhrases, 3, Random.Default
            ).also {
                voiceStore.saveEnrollPhrases(it)
            }
        }
        _enrollState.value = EnrollState(phrases = phrases)
    }

    /** The user tapped the mic on one enrollment phrase. Enrollment
     * records the voice alone: no recognizer runs here, so the
     * microphone is never shared. */
    fun enrollListen(index: Int) {
        if (index !in _enrollState.value.phrases.indices) return
        if (!hasMicPermission()) {
            voicePendingEnrollIndex = index
            _voicePermissionNeeded.tryEmit(Unit)
            return
        }
        startEnrollListening(index)
    }

    private fun startEnrollListening(index: Int) {
        _enrollState.value = _enrollState.value.copy(current = index, listening = true, error = null)
        voiceSession++
        val session = voiceSession
        voiceEngine.startVoiceCapture(maxSeconds = 10) { sample ->
            viewModelScope.launch { handleEnrollCapture(sample, session, index) }
        }
    }

    /** Stops the enrollment recording; the phrase counts from what
     * was recorded. The result is not dropped: stopping means the
     * user is done speaking, not that the sample is cancelled. */
    fun stopEnrollmentListening() {
        voiceEngine.stopVoiceCapture()
        _enrollState.value = _enrollState.value.copy(listening = false)
    }

    private fun handleEnrollCapture(sample: VoiceEngine.VoiceSample, session: Int, index: Int) {
        if (session != voiceSession) return
        viewModelScope.launch(Dispatchers.Default) {
            val pcm = (sample as? VoiceEngine.VoiceSample.Captured)?.pcm
            val words = _enrollState.value.phrases.getOrNull(index)
                ?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
            val features = pcm?.let { extractVoiceFeatures(it, 16_000, words) }
            withContext(Dispatchers.Main) {
                if (session != voiceSession) return@withContext
                if (sample is VoiceEngine.VoiceSample.Unavailable) {
                    _enrollState.value = _enrollState.value.copy(
                        listening = false,
                        error = "Voice recognition is not available on this device."
                    )
                    return@withContext
                }
                if (features == null) {
                    _enrollState.value = _enrollState.value.copy(
                        listening = false,
                        error = "I could not hear you clearly. Say it again."
                    )
                    return@withContext
                }
                enrollSamples[index] = features
                val phrases = _enrollState.value.phrases
                val done = phrases.indices.map { enrollSamples.containsKey(it) }
                if (enrollSamples.size >= phrases.size) {
                    val profile = buildVoiceProfile(enrollSamples.values.toList())
                    if (profile == null) {
                        _enrollState.value = _enrollState.value.copy(
                            listening = false,
                            error = "Something went wrong setting up your voice. Try again."
                        )
                        return@withContext
                    }
                    voiceStore.saveProfile(profile)
                    _voiceEnrolled.value = true
                    _enrollState.value = _enrollState.value.copy(done = done, listening = false, complete = true)
                } else {
                    val next = phrases.indices.firstOrNull { !done[it] } ?: 0
                    _enrollState.value = _enrollState.value.copy(done = done, current = next, listening = false)
                }
            }
        }
    }

    /** Removes the voice setup. Voice trading locks again until the
     * user enrolls again. */
    fun resetVoiceEnrollment() {
        voiceStore.clearProfile()
        voiceStore.clearEnrollPhrases()
        _voiceEnrolled.value = false
        enrollSamples.clear()
        _enrollState.value = EnrollState()
    }

    override fun onCleared() {
        voiceEngine.destroy()
        super.onCleared()
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
                    message = "Could not reach Prognos Labs. Please try again later.",
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

    /**
     * Opens the Prognos Labs site in the browser, where deposits and
     * withdrawals happen through the user's own wallet. The app has no
     * native deposit flow: the site holds the wired Gate deposit and
     * withdrawal flows, and the user signs everything in their wallet.
     */
    fun openTradingSite() {
        viewModelScope.launch {
            _openBrowser.emit(AppConfig.LINK_PAGE_URL)
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
                    // locked until the new link hands back its own
                    // session (redirect or a fresh link).
                    val oldLinked = tokens.linkedWallet
                    if (oldLinked != null && !oldLinked.equals(address, ignoreCase = true)) {
                        tokens.clearTradingSession()
                    }
                    tokens.linkedWallet = address
                    // v1.1.10: log the link for Analytics. Coarse
                    // only: the wallet type, never the address.
                    Analytics.logEvent(
                        AnalyticsEvents.WALLET_LINKED,
                        mapOf(AnalyticsEvents.PARAM_ADDRESS_TYPE to walletAddressType(address))
                    )
                    _link.value = _link.value.copy(
                        linkedAddress = address.lowercase(),
                        message = "Wallet linked. Trading in the app opens when the link page sends the session back.",
                        isError = false
                    )
                    refreshTrading(force = true)
                }
                false -> {
                    _link.value = _link.value.copy(
                        message = "That wallet is not linked yet. Sign the link message in your browser first.",
                        isError = true
                    )
                }
                null -> {
                    _link.value = _link.value.copy(
                        message = "Could not reach Prognos Labs. Please try again later.",
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
     * session belongs to that wallet (session_status, no broker call).
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
            refreshTrading(force = true)
        }
    }

    /** The exact link message for a nonce, for display or debugging. */
    fun linkMessage(nonce: String?): String = linkMessageText(nonce)
}
