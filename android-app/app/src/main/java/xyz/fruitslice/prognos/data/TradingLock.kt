package xyz.fruitslice.prognos.data

/**
 * Single source of truth for what is open. Trading, deposits and
 * withdrawals are all locked: nothing is open yet, so every money
 * button in the app is disabled. The UI reads these flags, never a
 * hardcoded true. Flip one flag here when trading opens.
 */
object TradingLock {
    val tradingOpen: Boolean = false
    val depositsOpen: Boolean = false
    val withdrawalsOpen: Boolean = false
}
