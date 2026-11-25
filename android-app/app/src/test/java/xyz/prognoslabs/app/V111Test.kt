package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.DisplayCurrency
import xyz.prognoslabs.app.data.btcEquivalentText
import xyz.prognoslabs.app.data.changePasswordError
import xyz.prognoslabs.app.data.convertUsd
import xyz.prognoslabs.app.data.fmtMoney
import xyz.prognoslabs.app.data.formatResendCountdown
import xyz.prognoslabs.app.data.isAcceptableLinkedReturn
import xyz.prognoslabs.app.data.isValidNewPassword
import xyz.prognoslabs.app.data.orderAmountError
import xyz.prognoslabs.app.data.parseAuthResponse
import xyz.prognoslabs.app.data.parseFxRates
import xyz.prognoslabs.app.data.resendSecondsLeft

/**
 * v1.1.1: forgot password from login, OTP-loop fix, 60s resend
 * countdown, wallet change, display currency, VIP status, default
 * trade size, dashboard BTC equivalent. Pure JVM tests, no Android.
 */
class V111Test {

    // ---- Helpers to read the shipped sources (same roots as AuthTradingTest) ----

    private fun mainRoot(): File {
        val cwd = System.getProperty("user.dir") ?: "."
        return listOf(
            File("src/main"),
            File("app/src/main"),
            File("$cwd/src/main"),
            File("$cwd/app/src/main")
        ).firstOrNull { it.isDirectory }
            ?: throw AssertionError("Cannot find src/main from $cwd")
    }

    private fun readSource(relative: String): String {
        val root = mainRoot()
        val f = File(root, relative)
        assertTrue("Missing source file $relative", f.isFile)
        return f.readText()
    }

    /** Text of one suspend function, from its declaration to the next. */
    private fun functionBody(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        assertTrue("Cannot find $declaration", start >= 0)
        val next = source.indexOf("\n    suspend fun ", start + declaration.length)
        return if (next < 0) source.substring(start) else source.substring(start, next)
    }

    // ---- 1. Forgot password: reset flow validation ----

    @Test
    fun resetPasswordNeedsAValidNewPassword() {
        assertFalse(isValidNewPassword("12345"))
        assertFalse(isValidNewPassword("abcdefgh"))
        assertFalse(isValidNewPassword("12345678"))
        assertTrue(isValidNewPassword("Secret123"))
    }

    @Test
    fun resetPasswordChangeError() {
        assertEquals(
            "New password needs at least 8 characters.",
            changePasswordError(true, "short", "short")
        )
        assertEquals(
            "New passwords do not match.",
            changePasswordError(true, "Secret123", "Secret124")
        )
        assertNull(changePasswordError(true, "Secret123", "Secret123"))
    }

    @Test
    fun resetRequestAndConfirmResponsesParse() {
        // reset_request answers ok:true and sends the code.
        val req = parseAuthResponse("""{"ok":true}""")
        assertNotNull(req)
        assertTrue(req!!.ok == true)
        // reset_confirm answers ok:true once the password is set.
        val conf = parseAuthResponse("""{"ok":true}""")
        assertNotNull(conf)
        assertTrue(conf!!.ok == true)
    }

    // ---- 2. OTP-loop fix: login must NOT re-send the code ----
    // The server sends the login code with the login response and
    // invalidates all earlier unused codes for the email when a new
    // code is issued. A second send inside logIn killed the code the
    // user just received and looped them back to "send a new code".

    @Test
    fun loginDoesNotResendTheCode() {
        val repo = readSource("java/xyz/prognoslabs/app/data/Repository.kt")
        val login = functionBody(repo, "suspend fun logIn(")
        assertFalse(
            "logIn must not call sendOtp: the server already sent the login code",
            login.contains("sendOtp")
        )
    }

    @Test
    fun signupVerifySetsARegistrationCompleteNotice() {
        val repo = readSource("java/xyz/prognoslabs/app/data/Repository.kt")
        val verify = functionBody(repo, "suspend fun verifyOtp(")
        // v1.1.6: the notice goes through registrationCompleteNotice,
        // which says "log in to get your code" when the password is
        // gone (the app restarted) instead of claiming a code was sent.
        assertTrue(
            "verifyOtp must set a Registration complete notice after signup verifies",
            verify.contains("registrationCompleteNotice(pending.email, passwordPresent = true)")
        )
        assertTrue(
            "verifyOtp must use the no-password notice when the password is gone",
            verify.contains("registrationCompleteNotice(pending.email, passwordPresent = false)")
        )
    }

    @Test
    fun resendRecordsTheSendTime() {
        val repo = readSource("java/xyz/prognoslabs/app/data/Repository.kt")
        val resend = functionBody(repo, "suspend fun resendOtp(")
        assertTrue("resendOtp must record when the code was sent", resend.contains("_lastCodeSentAt"))
        val reset = functionBody(repo, "suspend fun requestPasswordReset(")
        assertTrue("requestPasswordReset must record when the code was sent", reset.contains("_lastCodeSentAt"))
    }

    // ---- 3. Resend countdown: 60 seconds after every code sent ----

    @Test
    fun resendCountdownIsSixtySeconds() {
        val now = 1_000_000_000L
        assertEquals(0L, resendSecondsLeft(now, 0L))
        assertEquals(60L, resendSecondsLeft(now, now))
        assertEquals(30L, resendSecondsLeft(now, now - 30_000))
        assertEquals(1L, resendSecondsLeft(now, now - 59_000))
        assertEquals(0L, resendSecondsLeft(now, now - 60_000))
        assertEquals(0L, resendSecondsLeft(now, now - 120_000))
    }

    @Test
    fun resendCountdownLabel() {
        assertEquals("Send a new code in 0:45", formatResendCountdown(45))
        assertEquals("Send a new code in 1:00", formatResendCountdown(60))
        assertEquals("Send a new code in 0:00", formatResendCountdown(0))
    }

    // ---- 4. Display currency: live rates, never a hardcoded rate ----

    private val sampleFxJson = """
        {"result":"success","base_code":"USD","rates":{"USD":1,"EUR":0.86,"GBP":0.75,"NGN":1500.0}}
    """.trimIndent()

    @Test
    fun fxRatesParse() {
        val rates = parseFxRates(sampleFxJson, 1_000L)
        assertNotNull(rates)
        assertEquals("USD", rates!!.base)
        assertEquals(0.86, rates.rateFor("EUR")!!, 0.0001)
        assertEquals(0.75, rates.rateFor("GBP")!!, 0.0001)
        assertEquals(1500.0, rates.rateFor("NGN")!!, 0.0001)
        assertEquals(1.0, rates.rateFor("USD")!!, 0.0001)
        assertNull(rates.rateFor("JPY"))
    }

    @Test
    fun fxRatesRejectBadPayloads() {
        assertNull(parseFxRates("", 1_000L))
        assertNull(parseFxRates("not json", 1_000L))
        assertNull(parseFxRates("""{"result":"error","base_code":"USD","rates":{}}""", 1_000L))
        assertNull(parseFxRates("""{"result":"success","base_code":"EUR","rates":{"EUR":1}}""", 1_000L))
        assertNull(parseFxRates("""{"result":"success","base_code":"USD","rates":{}}""", 1_000L))
    }

    @Test
    fun currencyConversionUsesLiveRates() {
        val rates = parseFxRates(sampleFxJson, 1_000L)!!
        assertEquals(100.0, convertUsd(100.0, DisplayCurrency.USD, rates)!!, 0.0001)
        assertEquals(86.0, convertUsd(100.0, DisplayCurrency.EUR, rates)!!, 0.0001)
        assertEquals(150000.0, convertUsd(100.0, DisplayCurrency.NGN, rates)!!, 0.0001)
        // No rates at all: no conversion is possible.
        assertNull(convertUsd(100.0, DisplayCurrency.EUR, null))
        assertEquals(100.0, convertUsd(100.0, DisplayCurrency.USD, null)!!, 0.0001)
    }

    @Test
    fun moneyFallsBackToUsdWhenNoRate() {
        val rates = parseFxRates(sampleFxJson, 1_000L)!!
        assertEquals("$100.00", fmtMoney(100.0, DisplayCurrency.USD, rates))
        assertEquals("€86.00", fmtMoney(100.0, DisplayCurrency.EUR, rates))
        assertEquals("₦150,000.00", fmtMoney(100.0, DisplayCurrency.NGN, rates))
        // No rate: show USD, never a made-up conversion.
        assertEquals("$100.00", fmtMoney(100.0, DisplayCurrency.EUR, null))
        assertEquals("-", fmtMoney(null, DisplayCurrency.USD, rates))
    }

    @Test
    fun currencyFromCodeFallsBackToUsd() {
        assertEquals(DisplayCurrency.EUR, DisplayCurrency.fromCode("EUR"))
        assertEquals(DisplayCurrency.NGN, DisplayCurrency.fromCode("NGN"))
        assertEquals(DisplayCurrency.USD, DisplayCurrency.fromCode("XXX"))
        assertEquals(DisplayCurrency.USD, DisplayCurrency.fromCode(null))
    }

    // ---- 5. Dashboard BTC equivalent: live price, never hardcoded ----

    @Test
    fun btcEquivalentUsesLivePrice() {
        assertEquals("≈ 0.1617 BTC", btcEquivalentText(10_000.0, 61_840.0))
        assertEquals("≈ 1.0000 BTC", btcEquivalentText(61_840.0, 61_840.0))
        assertEquals("≈ 0.0000 BTC", btcEquivalentText(0.0, 61_840.0))
    }

    @Test
    fun btcEquivalentIsNullWithoutPrice() {
        assertNull(btcEquivalentText(null, 61_840.0))
        assertNull(btcEquivalentText(10_000.0, null))
        assertNull(btcEquivalentText(10_000.0, 0.0))
        assertNull(btcEquivalentText(10_000.0, -5.0))
    }

    // ---- 6. Default trade size: a valid order amount ----

    @Test
    fun defaultTradeSizeMustBeAValidAmount() {
        assertNull(orderAmountError("10.00"))
        assertNull(orderAmountError("25"))
        assertNotNull(orderAmountError(""))
        assertNotNull(orderAmountError("abc"))
        assertNotNull(orderAmountError("0"))
        assertNotNull(orderAmountError("-5"))
        assertNotNull(orderAmountError("10.1234567"))
    }

    // ---- 8. Change wallet: the new wallet must be the pending one ----

    @Test
    fun linkedReturnAcceptsTheNewWalletOnAChange() {
        val session = "a".repeat(64)
        val newWallet = "0x1111111111111111111111111111111111111111"
        val oldWallet = "0x2222222222222222222222222222222222222222"
        // The app asked to link the new wallet; the old one is linked.
        assertTrue(isAcceptableLinkedReturn(session, newWallet, newWallet, oldWallet))
        // A return for a third wallet is not accepted.
        val third = "0x3333333333333333333333333333333333333333"
        assertFalse(isAcceptableLinkedReturn(session, third, newWallet, oldWallet))
        // A return with no session is never accepted.
        assertFalse(isAcceptableLinkedReturn(null, newWallet, newWallet, oldWallet))
        assertFalse(isAcceptableLinkedReturn("short", newWallet, newWallet, oldWallet))
    }

    // ---- 9. Shipping shape: version bump + App Link autoVerify ----

    @Test
    fun versionIs1121() {
        val cwd = System.getProperty("user.dir") ?: "."
        val gradle = listOf(
            File("app/build.gradle.kts"),
            File("$cwd/app/build.gradle.kts"),
            File("build.gradle.kts"),
            File("$cwd/build.gradle.kts")
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find app/build.gradle.kts from $cwd")
        val text = gradle.readText()
        assertTrue("versionCode must be 31", text.contains("versionCode = 31"))
        assertTrue("versionName must be 1.1.21", text.contains("versionName = \"1.1.21\""))
    }

    @Test
    fun linkedIntentFilterHasAutoVerify() {
        val manifest = readSource("AndroidManifest.xml")
        assertTrue(
            "The /linked intent-filter needs android:autoVerify for App Links",
            manifest.contains("android:autoVerify=\"true\"")
        )
    }
}
