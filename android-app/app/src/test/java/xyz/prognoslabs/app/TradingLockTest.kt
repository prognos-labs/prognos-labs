package xyz.prognoslabs.app

import org.junit.Assert.assertFalse
import org.junit.Test
import xyz.prognoslabs.app.data.TradingLock

/**
 * The gate is the rule the whole app follows: trading, deposits and
 * withdrawals are all closed until trading opens. Every money button
 * in the app reads these flags, so if one of them ever flips to true
 * by mistake, this test fails and the build cannot ship it.
 */
class TradingLockTest {

    @Test
    fun tradingIsClosed() {
        assertFalse(TradingLock.tradingOpen)
    }

    @Test
    fun depositsAreClosed() {
        assertFalse(TradingLock.depositsOpen)
    }

    @Test
    fun withdrawalsAreClosed() {
        assertFalse(TradingLock.withdrawalsOpen)
    }
}
