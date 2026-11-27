package xyz.prognoslabs.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.AppConfig
import xyz.prognoslabs.app.data.AuthCall
import xyz.prognoslabs.app.data.AuthRepository
import xyz.prognoslabs.app.data.createAuthApi
import xyz.prognoslabs.app.data.userFacingVerifyOtpError
import java.io.File
import java.net.ServerSocket

/**
 * v1.1.5: a wrong OTP code must say the code is wrong, not that
 * accounts are not ready yet.
 *
 * Root cause: the server answers a wrong code with HTTP 400 and the
 * message in the JSON error body, and a lockout with HTTP 429. Retrofit
 * throws HttpException for any non-2xx status, and AuthRepository
 * swallowed every exception as NotReady, so the server's distinct
 * messages never reached the screen. The existing stub test never caught
 * it because its stub answered the login failure with HTTP 200 and the
 * error in the body, which is not what the real server does.
 */
class V115Test {

    // ---- 1. The user-facing copy ----

    @Test
    fun wrongCodeSaysTheCodeIsWrong() {
        assertEquals(
            "That code is wrong. Try again.",
            userFacingVerifyOtpError("That code is not correct. Please try again.")
        )
        assertEquals(AppConfig.WRONG_CODE, userFacingVerifyOtpError("That code is not correct. Please try again."))
    }

    @Test
    fun lockoutExpiredAndNewCodeMessagesPassThroughUnchanged() {
        // These tell the user something a retry cannot fix, so their
        // wording stays exactly as the server sent it.
        assertEquals(
            "Too many wrong codes. Please try again in an hour.",
            userFacingVerifyOtpError("Too many wrong codes. Please try again in an hour.")
        )
        assertEquals(
            "This code has expired. Please ask for a new code.",
            userFacingVerifyOtpError("This code has expired. Please ask for a new code.")
        )
        assertEquals(
            "That code is not correct. Please ask for a new code.",
            userFacingVerifyOtpError("That code is not correct. Please ask for a new code.")
        )
    }

    @Test
    fun accountsNotReadyCopyUnchanged() {
        assertEquals(
            "Accounts are not ready yet. Please try again later.",
            AppConfig.ACCOUNTS_NOT_READY
        )
    }

    // ---- 2. HTTP error statuses reach the caller as ServerError ----

    @Test
    fun wrongCode400IsServerErrorNotNotReady() = runBlocking {
        OtpStubServer().use { stub ->
            val repo = AuthRepository(createAuthApi(stub.base + "auth.php"))
            val wrong = repo.verifyOtp("flint@example.com", "000000", "login")
            assertTrue("A wrong code must not map to NotReady: $wrong", wrong is AuthCall.ServerError)
            assertEquals(
                "That code is not correct. Please try again.",
                (wrong as AuthCall.ServerError).message
            )
        }
    }

    @Test
    fun lockout429ShowsTheServerMessage() = runBlocking {
        OtpStubServer().use { stub ->
            val repo = AuthRepository(createAuthApi(stub.base + "auth.php"))
            val locked = repo.verifyOtp("flint@example.com", "999999", "login")
            assertTrue("A lockout must not map to NotReady: $locked", locked is AuthCall.ServerError)
            assertEquals(
                "Too many wrong codes. Please try again in an hour.",
                (locked as AuthCall.ServerError).message
            )
        }
    }

    @Test
    fun expiredCode400ShowsTheServerMessage() = runBlocking {
        OtpStubServer().use { stub ->
            val repo = AuthRepository(createAuthApi(stub.base + "auth.php"))
            val expired = repo.verifyOtp("flint@example.com", "111111", "login")
            assertTrue(expired is AuthCall.ServerError)
            assertEquals(
                "This code has expired. Please ask for a new code.",
                (expired as AuthCall.ServerError).message
            )
        }
    }

    @Test
    fun rightCodeStillVerifies() = runBlocking {
        OtpStubServer().use { stub ->
            val repo = AuthRepository(createAuthApi(stub.base + "auth.php"))
            val ok = repo.verifyOtp("flint@example.com", "123456", "login")
            assertTrue("A right code must verify: $ok", ok is AuthCall.Ok)
            assertEquals("tok-abc", (ok as AuthCall.Ok).response.token)
        }
    }

    @Test
    fun unreadableErrorBodyStillMapsToNotReady() = runBlocking {
        OtpStubServer().use { stub ->
            val repo = AuthRepository(createAuthApi(stub.base + "auth.php"))
            val garbled = repo.verifyOtp("flint@example.com", "222222", "login")
            assertTrue("A 400 with no readable message stays NotReady: $garbled", garbled is AuthCall.NotReady)
        }
    }

    @Test
    fun unreachableServerStillMapsToNotReady() = runBlocking {
        val deadPort = run {
            val s = ServerSocket(0)
            val p = s.localPort
            s.close()
            p
        }
        val deadAuth = AuthRepository(createAuthApi("http://127.0.0.1:$deadPort/auth.php"))
        assertTrue(deadAuth.verifyOtp("flint@example.com", "000000", "login") is AuthCall.NotReady)
    }

    // ---- 3. Shipping shape ----

    @Test
    fun versionIs1123() {
        val cwd = System.getProperty("user.dir") ?: "."
        val gradle = listOf(
            File("app/build.gradle.kts"),
            File("$cwd/app/build.gradle.kts"),
            File("build.gradle.kts"),
            File("$cwd/build.gradle.kts")
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find app/build.gradle.kts from $cwd")
        val text = gradle.readText()
        assertTrue("versionCode must be 40", text.contains("versionCode = 40"))
        assertTrue("versionName must be 1.1.30", text.contains("versionName = \"1.1.30\""))
    }
}

/**
 * A tiny hand-rolled HTTP server for the v1.1.5 contract tests: one
 * thread, canned JSON per code, real HTTP statuses like the live
 * auth.php (400 wrong code, 429 lockout), no extra dependencies.
 */
private class OtpStubServer : AutoCloseable {
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
            path.startsWith("/auth.php") && action == "verify_otp" && body.contains("\"code\":\"123456\"") ->
                200 to """{"ok":true,"token":"tok-abc","user":{"id":"42","name":"Flint","email":"flint@example.com"}}"""
            path.startsWith("/auth.php") && action == "verify_otp" && body.contains("\"code\":\"000000\"") ->
                400 to """{"error":"That code is not correct. Please try again."}"""
            path.startsWith("/auth.php") && action == "verify_otp" && body.contains("\"code\":\"999999\"") ->
                429 to """{"error":"Too many wrong codes. Please try again in an hour."}"""
            path.startsWith("/auth.php") && action == "verify_otp" && body.contains("\"code\":\"111111\"") ->
                400 to """{"error":"This code has expired. Please ask for a new code."}"""
            path.startsWith("/auth.php") && action == "verify_otp" && body.contains("\"code\":\"222222\"") ->
                400 to """not json at all"""
            path.startsWith("/auth.php") -> 200 to """{"ok":true}"""
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
