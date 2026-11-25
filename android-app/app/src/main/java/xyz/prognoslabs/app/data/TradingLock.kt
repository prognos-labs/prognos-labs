package xyz.prognoslabs.app.data

/**
 * Single source of truth for what is open. Trading, deposits and
 * withdrawals are all open for testers (Flint, 25 Nov), so every
 * money button in the app is enabled. The UI reads these flags,
 * never a hardcoded true.
 */
object TradingLock {
    val tradingOpen: Boolean = true
    val depositsOpen: Boolean = true
    val withdrawalsOpen: Boolean = true
}
