package xyz.prognoslabs.app

import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.TradingLock

/**
 * The gate is the rule the whole app follows: trading, deposits and
 * withdrawals are all open for testers (Flint, 25 Nov). Every money
 * button in the app reads these flags, so if one of them ever flips
 * back to false by mistake, this test fails and the build cannot
 * ship it.
 */
class TradingLockTest {

    @Test
    fun tradingIsOpen() {
        assertTrue(TradingLock.tradingOpen)
    }

    @Test
    fun depositsAreOpen() {
        assertTrue(TradingLock.depositsOpen)
    }

    @Test
    fun withdrawalsAreOpen() {
        assertTrue(TradingLock.withdrawalsOpen)
    }
}
