package xyz.fruitslice.prognos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fruitslice.prognos.data.changePasswordError
import xyz.fruitslice.prognos.data.isValidNewPassword
import xyz.fruitslice.prognos.data.sanitizeAmountInput

/**
 * v9: Settings change-password validation and the trade-panel amount
 * input filter. Both are pure functions, so they run on the JVM.
 * Settings navigation itself is a Compose NavHost route and is covered
 * by the build (the "settings" destination exists in MainActivity);
 * it needs an instrumented test to tap, so it is not asserted here.
 */
class SettingsTest {

    @Test
    fun newPasswordNeedsEightCharactersWithLetterAndNumber() {
        assertEquals(false, isValidNewPassword("1234567"))
        assertEquals(false, isValidNewPassword("12345678"))
        assertEquals(false, isValidNewPassword("abcdefgh"))
        assertEquals(true, isValidNewPassword("newpass12"))
    }

    @Test
    fun changePasswordRejectsWrongCurrentPassword() {
        assertEquals(
            "Your current password is wrong.",
            changePasswordError(false, "newpass12", "newpass12")
        )
    }

    @Test
    fun changePasswordRejectsShortNewPassword() {
        assertEquals(
            "New password needs at least 8 characters.",
            changePasswordError(true, "short", "short")
        )
    }

    @Test
    fun changePasswordRejectsMismatch() {
        assertEquals(
            "New passwords do not match.",
            changePasswordError(true, "newpass12", "newpass13")
        )
    }

    @Test
    fun changePasswordAcceptsValidChange() {
        assertNull(changePasswordError(true, "newpass12", "newpass12"))
    }

    @Test
    fun amountInputKeepsDigitsAndDotsOnly() {
        assertEquals("10.00", sanitizeAmountInput("10.00"))
        assertEquals("10.00", sanitizeAmountInput("10a.00"))
        assertEquals("1000", sanitizeAmountInput("1,000"))
        assertEquals("", sanitizeAmountInput(""))
        assertEquals("10.00", sanitizeAmountInput("\$10.00"))
    }
}
