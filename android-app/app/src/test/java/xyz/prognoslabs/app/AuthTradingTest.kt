package xyz.prognoslabs.app

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.AppConfig
import xyz.prognoslabs.app.data.AuthCall
import xyz.prognoslabs.app.data.AuthRepository
import xyz.prognoslabs.app.data.TradingRepository
import xyz.prognoslabs.app.data.TradingState
import xyz.prognoslabs.app.data.TradeSide
import xyz.prognoslabs.app.data.authErrorFromJson
import xyz.prognoslabs.app.data.balanceFromJson
import xyz.prognoslabs.app.data.buildPlaceOrderBody
import xyz.prognoslabs.app.data.createAuthApi
import xyz.prognoslabs.app.data.createTradingApi
import xyz.prognoslabs.app.data.isAcceptableLinkedReturn
import xyz.prognoslabs.app.data.isValidOtpCode
import xyz.prognoslabs.app.data.isValidTradingSession
import xyz.prognoslabs.app.data.isValidWalletAddress
import xyz.prognoslabs.app.data.isLinkedReturnUrl
import xyz.prognoslabs.app.data.linkMessageText
import xyz.prognoslabs.app.data.linkPageUrl
import xyz.prognoslabs.app.data.orderAmountError
import xyz.prognoslabs.app.data.ordersFromJson
import xyz.prognoslabs.app.data.parseAuthResponse
import xyz.prognoslabs.app.data.parseLinkedReturn
import xyz.prognoslabs.app.data.positionsFromJson
import xyz.prognoslabs.app.data.selectTradeSide
import xyz.prognoslabs.app.data.tradeSideColour
import xyz.prognoslabs.app.data.tradingStateFor
import java.io.File
import java.net.ServerSocket

/**
 * v1.1.0: rename, server auth contract, OTP, trading lock states and
 * the Buy Up / Sell Down colour rule. Pure JVM tests, no Android.
 */
class AuthTradingTest {

    // ---- 1. Rename: zero "Prognosis" in app/src/main, any case ----
    // Case-insensitive on purpose: a lowercase internal name (the
    // DataStore was once "prognosis") must not slip past a scan that
    // only looks for the capitalised word.

    @Test
    fun noPrognosisAnywhereInShippedCode() {
        val cwd = System.getProperty("user.dir") ?: "."
        val roots = listOf(
            File("src/main"),
            File("app/src/main"),
            File("$cwd/src/main"),
            File("$cwd/app/src/main")
        )
        val root = roots.firstOrNull { it.isDirectory }
            ?: throw AssertionError("Cannot find src/main from ${System.getProperty("user.dir")}")
        val hits = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".xml")) }
            .forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    if (line.contains("prognosis", ignoreCase = true)) hits.add("${f.relativeTo(root)}:${i + 1}")
                }
            }
        assertTrue("Found Prognosis in shipped code: $hits", hits.isEmpty())
    }

    @Test
    fun appNameIsPrognosLabs() {
        assertEquals("Prognos Labs", AppConfig.APP_NAME)
        val strings = File("src/main/res/values/strings.xml")
            .takeIf { it.isFile }
            ?: File("app/src/main/res/values/strings.xml")
        assertTrue(strings.isFile)
        val text = strings.readText()
        assertTrue(text.contains(">Prognos Labs<"))
        assertFalse(text.contains("prognosis", ignoreCase = true))
    }

    // ---- 2. OTP validation ----

    @Test
    fun otpCodeMustBeSixDigits() {
        assertTrue(isValidOtpCode("123456"))
        assertTrue(isValidOtpCode("000000"))
        assertFalse(isValidOtpCode("12345"))
        assertFalse(isValidOtpCode("1234567"))
        assertFalse(isValidOtpCode("12345a"))
        assertFalse(isValidOtpCode(""))
        // Surrounding spaces from a paste are trimmed, so they pass.
        assertTrue(isValidOtpCode(" 123456 "))
        assertFalse(isValidOtpCode("12 456"))
    }

    // ---- 3. Auth response parsing (fixed contract) ----

    @Test
    fun signupSuccessParses() {
        val r = parseAuthResponse("""{"ok":true,"user_id":"42","message":"A code was sent to your email."}""")
        assertNotNull(r)
        assertEquals(true, r!!.ok)
        assertEquals("42", r.userId)
        assertNull(r.error)
    }

    @Test
    fun loginVerifyReturnsTokenAndUser() {
        val r = parseAuthResponse(
            """{"ok":true,"token":"abc123token","user":{"id":"42","name":"Flint","email":"flint@example.com"}}"""
        )
        assertNotNull(r)
        assertEquals("abc123token", r!!.token)
        assertEquals("Flint", r.user?.name)
        assertEquals("flint@example.com", r.user?.email)
    }

    @Test
    fun verifyPurposeReturnsVerified() {
        val r = parseAuthResponse("""{"ok":true,"verified":true}""")
        assertEquals(true, r!!.verified)
    }

    @Test
    fun resetPurposeReturnsResetToken() {
        val r = parseAuthResponse("""{"ok":true,"reset_token":"reset-abc"}""")
        assertEquals("reset-abc", r!!.resetToken)
    }

    @Test
    fun loginNeedOtpParses() {
        val r = parseAuthResponse("""{"ok":true,"need_otp":true}""")
        assertEquals(true, r!!.needOtp)
    }

    @Test
    fun wrongCredentialsErrorIsExactContractMessage() {
        val r = parseAuthResponse("""{"error":"Email or password is not correct."}""")
        assertEquals("Email or password is not correct.", r!!.error)
        assertEquals("Email or password is not correct.", authErrorFromJson("""{"error":"Email or password is not correct."}"""))
    }

    @Test
    fun signInAgainErrorParses() {
        val r = parseAuthResponse("""{"error":"Please sign in again."}""")
        assertEquals("Please sign in again.", r!!.error)
    }

    @Test
    fun unreadableAuthBodyIsNull() {
        assertNull(parseAuthResponse(null))
        assertNull(parseAuthResponse(""))
        assertNull(parseAuthResponse("not json"))
        assertNull(authErrorFromJson("not json"))
    }

    @Test
    fun notReadyMessageIsPlainLanguage() {
        assertEquals(
            "Accounts are not ready yet. Please try again later.",
            AppConfig.ACCOUNTS_NOT_READY
        )
        assertFalse(AppConfig.ACCOUNTS_NOT_READY.contains("Exception"))
        assertFalse(AppConfig.ACCOUNTS_NOT_READY.contains("HTTP"))
    }

    // ---- 4. Trading lock states ----

    @Test
    fun unreachableServerIsLocked() {
        assertTrue(tradingStateFor(false, false, null, false) is TradingState.Unreachable)
        assertTrue(tradingStateFor(false, true, "0xabc", true) is TradingState.Unreachable)
    }

    @Test
    fun reachableButUnlinkedIsLocked() {
        val s = tradingStateFor(true, false, null, false)
        assertTrue(s is TradingState.NotLinked)
    }

    @Test
    fun linkedWithoutSessionIsLockedButKnown() {
        val s = tradingStateFor(true, true, "0x216e95babd339a07ab020f448aea3911151677d9", false)
        assertTrue(s is TradingState.Linked)
        assertEquals("0x216e95babd339a07ab020f448aea3911151677d9", (s as TradingState.Linked).address)
    }

    @Test
    fun linkedWithSessionIsReady() {
        val s = tradingStateFor(true, true, "0xabc", true)
        assertTrue(s is TradingState.Ready)
        assertEquals("0xabc", (s as TradingState.Ready).address)
    }

    @Test
    fun tradingOpensSoonCopyHasNoRawError() {
        assertEquals("Trading opens soon", AppConfig.TRADING_OPENS_SOON)
    }

    // ---- 5. Buy Up green / Sell Down red, colour persists ----

    @Test
    fun buyUpIsGreenAndSellDownIsRed() {
        assertEquals("green", tradeSideColour(TradeSide.UP, TradeSide.UP))
        assertEquals("red", tradeSideColour(TradeSide.DOWN, TradeSide.DOWN))
        assertEquals("none", tradeSideColour(TradeSide.UP, TradeSide.DOWN))
        assertEquals("none", tradeSideColour(TradeSide.DOWN, TradeSide.UP))
        assertEquals("none", tradeSideColour(null, TradeSide.UP))
    }

    @Test
    fun pickedSideStaysPickedUntilOtherSideTapped() {
        var selected: TradeSide? = TradeSide.UP
        assertEquals("green", tradeSideColour(selected, TradeSide.UP))
        selected = selectTradeSide(selected, TradeSide.DOWN)
        assertEquals(TradeSide.DOWN, selected)
        assertEquals("red", tradeSideColour(selected, TradeSide.DOWN))
        assertEquals("none", tradeSideColour(selected, TradeSide.UP))
        selected = selectTradeSide(selected, TradeSide.DOWN)
        assertEquals(TradeSide.DOWN, selected)
    }

    @Test
    fun placeOrderBodyAlwaysBuysChosenToken() {
        // Both buttons buy the chosen outcome's token, as the website
        // trade panel does; buying the Down token is the down bet.
        val up = buildPlaceOrderBody("0xabc", "sess", "tok0", TradeSide.UP, "10.00")
        assertEquals("BUY", up["side"])
        assertEquals("MARKET", up["order_mode"])
        assertEquals("tok0", up["token_id"])
        assertEquals("10.00", up["total_cost"])
        val down = buildPlaceOrderBody("0xabc", "sess", "tok1", TradeSide.DOWN, "5")
        assertEquals("BUY", down["side"])
        assertEquals("tok1", down["token_id"])
    }

    @Test
    fun orderAmountValidation() {
        assertNull(orderAmountError("10.00"))
        assertNull(orderAmountError("0.000001"))
        assertEquals("Enter an amount.", orderAmountError(""))
        assertEquals("Enter an amount.", orderAmountError("   "))
        assertEquals("Enter an amount in numbers only.", orderAmountError("abc"))
        assertEquals("Enter an amount above zero.", orderAmountError("0"))
        assertEquals("Enter an amount above zero.", orderAmountError("-5"))
        assertEquals("Enter at most 6 decimal places.", orderAmountError("1.0000001"))
    }

    // ---- 6. Wallet linking helpers ----

    @Test
    fun walletAddressValidation() {
        assertTrue(isValidWalletAddress("0x216e95bABd339a07aB020f448AeA3911151677D9"))
        assertTrue(isValidWalletAddress("41nX57qXtLuJ7YWfJRmXBYdjrJPFiH4xNrSeNuTtLfef"))
        assertFalse(isValidWalletAddress(""))
        assertFalse(isValidWalletAddress("0x123"))
        assertFalse(isValidWalletAddress("not an address"))
        assertFalse(isValidWalletAddress("0xZZZZ95bABd339a07aB020f448AeA3911151677D9"))
    }

    @Test
    fun linkMessageCarriesNonce() {
        assertEquals("Link this wallet to Prognos Labs", linkMessageText(null))
        assertEquals(
            "Link this wallet to Prognos Labs\nNonce: abc123",
            linkMessageText("ABC123")
        )
    }

    @Test
    fun linkPageUrlCarriesNonce() {
        assertTrue(linkPageUrl("abc123").startsWith(AppConfig.LINK_PAGE_URL))
        assertTrue(linkPageUrl("abc123").contains("app_link_nonce=abc123"))
        assertEquals(AppConfig.LINK_PAGE_URL, linkPageUrl(null))
    }

    @Test
    fun linkedReturnParsesSessionAndAddress() {
        val r = parseLinkedReturn("https://prognoslabs.xyz/linked?session=abc123&address=0xABC")
        assertEquals("abc123", r.session)
        assertEquals("0xABC", r.address)
        val r2 = parseLinkedReturn("prognoslabs://linked?session=sess&address=0xDEF")
        assertEquals("sess", r2.session)
        assertEquals("0xDEF", r2.address)
        assertNull(parseLinkedReturn(null).session)
        assertNull(parseLinkedReturn("https://prognoslabs.xyz/").session)
        assertTrue(isLinkedReturnUrl("https://prognoslabs.xyz/linked?session=x"))
        assertTrue(isLinkedReturnUrl("prognoslabs://linked?session=x"))
        assertFalse(isLinkedReturnUrl("https://prognoslabs.xyz/"))
        assertFalse(isLinkedReturnUrl(null))
    }

    // ---- 6b. A redirect return is never trusted on its own ----
    // Parsing is not accepting: the session must be 64 hex chars and
    // the wallet must be one this app asked to link (or one the server
    // already confirmed). A planted token or a different wallet fails.

    @Test
    fun tradingSessionMustBeSixtyFourHexChars() {
        assertTrue(isValidTradingSession("a".repeat(64)))
        assertTrue(isValidTradingSession("A".repeat(64)))
        assertTrue(isValidTradingSession("0123456789abcdef".repeat(4)))
        assertFalse(isValidTradingSession("abc123"))
        assertFalse(isValidTradingSession("g".repeat(64)))
        assertFalse(isValidTradingSession("a".repeat(63)))
        assertFalse(isValidTradingSession("a".repeat(65)))
        assertFalse(isValidTradingSession(null))
        assertFalse(isValidTradingSession(""))
    }

    @Test
    fun linkedReturnAcceptedOnlyForTheWalletThisAppAskedToLink() {
        val session = "a".repeat(64)
        val wallet = "0x216e95bABd339a07aB020f448AeA3911151677D9"
        val other = "0x0000000000000000000000000000000000000001"
        // The wallet the app asked to link: accepted.
        assertTrue(isAcceptableLinkedReturn(session, wallet, wallet, null))
        // A wallet the server already confirmed: accepted.
        assertTrue(isAcceptableLinkedReturn(session, wallet, null, wallet))
        // No link was started and nothing is linked: rejected.
        assertFalse(isAcceptableLinkedReturn(session, wallet, null, null))
        // A return for a different wallet than the one requested: rejected.
        assertFalse(isAcceptableLinkedReturn(session, other, wallet, null))
        // An attacker-chosen short token: rejected even for the right wallet.
        assertFalse(isAcceptableLinkedReturn("attacker-token", wallet, wallet, null))
        // No session at all: rejected.
        assertFalse(isAcceptableLinkedReturn(null, wallet, wallet, null))
        // Bad address: rejected.
        assertFalse(isAcceptableLinkedReturn(session, "not-an-address", wallet, null))
    }

    // ---- 7. Lenient trading reply parsing ----

    @Test
    fun balanceParsesFromUsualShapes() {
        val gson = Gson()
        assertEquals(25.5, balanceFromJson(gson.fromJson("""{"balance":"25.5"}""", JsonObject::class.java))!!, 0.0)
        assertEquals(10.0, balanceFromJson(gson.fromJson("""{"data":{"available":10}}""", JsonObject::class.java))!!, 0.0)
        assertNull(balanceFromJson(gson.fromJson("""{"something_else":1}""", JsonObject::class.java)))
        assertNull(balanceFromJson(null))
    }

    @Test
    fun positionsParseLikeTheWebsiteReadsThem() {
        val gson = Gson()
        val root = gson.fromJson(
            """{"data":[{"quantity":"20","token_id":"tok1","outcome":"Yes","average_price":"0.5","event_id":"ev1"}]}""",
            JsonObject::class.java
        )
        val items = positionsFromJson(root)
        assertEquals(1, items.size)
        assertTrue(items[0].label.contains("Yes"))
        assertTrue(items[0].label.contains("20"))
        assertTrue(items[0].detail.contains("ev1"))
        assertTrue(items[0].detail.contains("AVG 0.5"))
        assertEquals(0, positionsFromJson(null).size)
    }

    @Test
    fun ordersParseLeniently() {
        val gson = Gson()
        val root = gson.fromJson(
            """{"orders":[{"side":"BUY","token_id":"tok1","status":"open","price":"0.5","size":"20"}]}""",
            JsonObject::class.java
        )
        val items = ordersFromJson(root)
        assertEquals(1, items.size)
        assertTrue(items[0].label.contains("BUY"))
        assertTrue(items[0].detail.contains("open"))
    }

    @Test
    fun sessionValidationAgainstStub() = runBlocking {
        StubServer().use { stub ->
            val trading = TradingRepository(createTradingApi(stub.base + "api.php"))
            // The stub confirms the session: valid.
            assertTrue(trading.validateSession("0xabc", "a".repeat(64)))
            // A malformed token never even reaches the server.
            assertFalse(trading.validateSession("0xabc", "short"))
            assertFalse(trading.validateSession("0xabc", ""))
        }
        // Dead port: an unreachable server never validates a session.
        val deadPort = run {
            val s = ServerSocket(0)
            val p = s.localPort
            s.close()
            p
        }
        val deadTrading = TradingRepository(createTradingApi("http://127.0.0.1:$deadPort/api.php"))
        assertFalse(deadTrading.validateSession("0xabc", "a".repeat(64)))
    }

    // ---- 8. Stub-server round trips (contract over real HTTP) ----

    @Test
    fun authContractRoundTripAgainstStub() = runBlocking {
        StubServer().use { stub ->
            val repo = AuthRepository(createAuthApi(stub.base + "auth.php"))
            val signup = repo.signup("Flint", "flint@example.com", "secret123")
            assertTrue(signup is AuthCall.Ok)
            assertEquals("42", (signup as AuthCall.Ok).response.userId)

            val verify = repo.verifyOtp("flint@example.com", "123456", "login")
            assertTrue(verify is AuthCall.Ok)
            assertEquals("tok-abc", (verify as AuthCall.Ok).response.token)
            assertEquals("Flint", verify.response.user?.name)

            val bad = repo.login("flint@example.com", "wrong")
            assertTrue(bad is AuthCall.ServerError)
            assertEquals("Email or password is not correct.", (bad as AuthCall.ServerError).message)

            val me = repo.me("tok-abc")
            assertTrue(me is AuthCall.Ok)
            assertEquals("flint@example.com", (me as AuthCall.Ok).response.user?.email)
        }
    }

    @Test
    fun tradingLockAgainstStubAndDeadPort() = runBlocking {
        StubServer().use { stub ->
            val trading = TradingRepository(createTradingApi(stub.base + "api.php"))
            assertEquals("nonce-abc", trading.requestLinkNonce())
            assertEquals(false, trading.isWalletLinked("0x216e95bABd339a07aB020f448AeA3911151677D9"))
            // Balance 401 on the stub: no balance, no crash.
            assertNull(trading.loadBalance("0xabc", "bad-session"))
            // A linked wallet with no session is locked, not ready.
            assertTrue(tradingStateFor(true, true, "0xabc", false) is TradingState.Linked)
        }
        // Dead port: everything maps to locked / not-ready, never throws.
        val deadPort = run {
            val s = ServerSocket(0)
            val p = s.localPort
            s.close()
            p
        }
        val deadAuth = AuthRepository(createAuthApi("http://127.0.0.1:$deadPort/auth.php"))
        assertTrue(deadAuth.signup("F", "a@b.com", "secret123") is AuthCall.NotReady)
        val deadTrading = TradingRepository(createTradingApi("http://127.0.0.1:$deadPort/api.php"))
        assertNull(deadTrading.requestLinkNonce())
        assertNull(deadTrading.isWalletLinked("0xabc"))
        assertNull(deadTrading.loadBalance("0xabc", "sess"))
    }
}

/**
 * A tiny hand-rolled HTTP server for contract tests: one thread,
 * canned JSON per action, no extra dependencies.
 */
private class StubServer : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int = server.localPort
    val base: String = "http://127.0.0.1:$port/"
    private val thread = Thread {
        while (!server.isClosed) {
            try {
                val sock = server.accept()
                Thread {
                    try {
                        handle(sock)
                    } catch (e: Exception) {
                    } finally {
                        try { sock.close() } catch (e: Exception) { }
                    }
                }.start()
            } catch (e: Exception) {
            }
        }
    }.also { it.isDaemon = true; it.start() }

    private fun handle(sock: java.net.Socket) {
        val input = sock.getInputStream().bufferedReader(Charsets.UTF_8)
        val out = sock.getOutputStream()
        val requestLine = input.readLine() ?: return
        var contentLength = 0
        var line: String?
        while (input.readLine().also { line = it } != null) {
            if (line!!.isBlank()) break
            val m = Regex("(?i)^Content-Length:\\s*(\\d+)").find(line!!)
            if (m != null) contentLength = m.groupValues[1].toInt()
        }
        val bodyChars = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = input.read(bodyChars, read, contentLength - read)
            if (n <= 0) break
            read += n
        }
        val body = String(bodyChars, 0, read)
        val path = requestLine.split(" ").getOrNull(1) ?: ""
        val action = Regex("[?&]action=([^&]*)").find(path)?.groupValues?.get(1) ?: ""
        val (code, json) = when {
            path.startsWith("/auth.php") && action == "signup" ->
                200 to """{"ok":true,"user_id":"42","message":"A code was sent to your email."}"""
            path.startsWith("/auth.php") && action == "verify_otp" && body.contains("\"purpose\":\"login\"") ->
                200 to """{"ok":true,"token":"tok-abc","user":{"id":"42","name":"Flint","email":"flint@example.com"}}"""
            path.startsWith("/auth.php") && action == "login" ->
                200 to """{"error":"Email or password is not correct."}"""
            path.startsWith("/auth.php") && action == "me" ->
                200 to """{"ok":true,"user":{"id":"42","name":"Flint","email":"flint@example.com","email_verified":true}}"""
            path.startsWith("/auth.php") -> 200 to """{"ok":true}"""
            path.startsWith("/api.php") && action == "link_nonce" ->
                200 to """{"nonce":"nonce-abc"}"""
            path.startsWith("/api.php") && action == "link_status" ->
                200 to """{"linked":false,"address":"0x216e95babd339a07ab020f448aea3911151677d9"}"""
            path.startsWith("/api.php") && action == "session_status" ->
                200 to """{"ok":true,"address":"0xabc"}"""
            path.startsWith("/api.php") && action == "balance" ->
                401 to """{"error":"Please link your wallet to Prognos Labs first.","action":"link"}"""
            else -> 404 to """{"error":"Unknown action."}"""
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    override fun close() {
        try { server.close() } catch (e: Exception) { }
    }
}
