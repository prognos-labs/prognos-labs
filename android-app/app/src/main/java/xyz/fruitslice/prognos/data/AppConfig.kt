package xyz.fruitslice.prognos.data

/**
 * The one place that holds the server addresses. Change a URL here and
 * every screen follows; no screen hardcodes a host.
 *
 * Auth and trading both live on prognoslabs.xyz. The app never talks to
 * the trading service directly and never holds its key: every trading
 * call goes through api.php on this host, which calls the service
 * server-side.
 */
object AppConfig {
    /** Auth backend: signup, login, OTP, password reset, token refresh. */
    const val AUTH_BASE_URL = "https://prognoslabs.xyz/auth.php"

    /** Trading and market data: markets, prices, book, balance, orders. */
    const val TRADING_BASE_URL = "https://prognoslabs.xyz/api.php"

    /**
     * Anonymous voice feedback: the app posts the text of phrases
     * the voice failed to understand here, only when the user opted
     * in. The endpoint stores the phrase, the language, the app
     * version and the time, nothing else. See voice-feedback.php.
     */
    const val VOICE_FEEDBACK_URL = "https://prognoslabs.xyz/voice-feedback.php"

    /**
     * The page in the system browser where the user links their wallet
     * with a free signature. The app opens this page; the signing
     * happens in the user's own wallet app, never inside this app.
     * The nonce the app requests is passed as ?app_link_nonce= so a
     * future site update can finish the link in one step.
     */
    const val LINK_PAGE_URL = "https://prognoslabs.xyz/"

    /** Shown when the account server cannot be reached at all. */
    const val ACCOUNTS_NOT_READY = "Accounts are not ready yet. Please try again later."

    /** Shown when the code the user typed does not match the one sent. */
    const val WRONG_CODE = "That code is wrong. Try again."

    /** The locked trading state, shown on every trading screen. */
    const val TRADING_OPENS_SOON = "Trading opens soon"

    /** App name, shown on the splash and auth screens and in Settings. */
    const val APP_NAME = "Prognos Labs"

    /**
     * How old an auth token may be before the app refreshes it on start
     * (20 hours). The server does not publish a token lifetime in the
     * auth contract, so this is a client-side rule only: a refresh that
     * fails simply signs the user out.
     */
    const val TOKEN_REFRESH_AFTER_MS = 20L * 60 * 60 * 1000
}
