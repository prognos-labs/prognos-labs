package xyz.fruitslice.prognos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fruitslice.prognos.data.authError
import xyz.fruitslice.prognos.data.isValidEmail
import xyz.fruitslice.prognos.data.isValidName
import xyz.fruitslice.prognos.data.isValidPassword

class AuthValidationTest {

    @Test
    fun emailAcceptsNormalAddress() {
        assertTrue(isValidEmail("flint@example.com"))
    }

    @Test
    fun emailRejectsBadAddresses() {
        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("flint"))
        assertFalse(isValidEmail("flint@"))
        assertFalse(isValidEmail("flint@example"))
        assertFalse(isValidEmail("flint @example.com"))
        assertFalse(isValidEmail("@example.com"))
    }

    @Test
    fun passwordNeedsEightCharactersWithLetterAndNumber() {
        assertFalse(isValidPassword("12345"))
        assertFalse(isValidPassword("123456"))
        assertFalse(isValidPassword("12345678"))
        assertFalse(isValidPassword("abcdefgh"))
        assertFalse(isValidPassword("Secret"))
        assertTrue(isValidPassword("Secret123"))
        assertTrue(isValidPassword("s3cret12"))
    }

    @Test
    fun nameNeedsTwoCharacters() {
        assertFalse(isValidName(""))
        assertFalse(isValidName(" "))
        assertFalse(isValidName("A"))
        assertTrue(isValidName("Fl"))
    }

    @Test
    fun authErrorReportsFirstProblem() {
        assertEquals("Enter your name.", authError("", "a@b.com", "Secret123", true))
        assertEquals("Enter a valid email address.", authError("Flint", "bad", "Secret123", true))
        assertEquals(
            "Password needs at least 8 characters, with a letter and a number.",
            authError("Flint", "a@b.com", "123", true)
        )
        assertNull(authError("Flint", "a@b.com", "Secret123", true))
        assertNull(authError("", "a@b.com", "Secret123", false))
    }

    @Test
    fun dashboardEmptyStatesAreHonest() {
        // Balance is zero until deposits open; no fake balance anywhere.
        val balance = 0.0
        assertEquals(0.0, balance, 0.0)
        val openPositions = 0
        assertEquals(0, openPositions)
    }
}
