package xyz.fruitslice.prognos.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * The server auth contract (fixed; the backend builds to this).
 * Base: https://prognoslabs.xyz/auth.php?action=<action>, all JSON.
 *
 * signup {name, email, password} -> {ok, user_id, message} + OTP emailed.
 * send_otp {email, purpose} -> {ok} always. purpose: verify | login | reset.
 * verify_otp {email, code, purpose} -> verify: {ok, verified}; login:
 *   {ok, token, user}; reset: {ok, reset_token}.
 * login {email, password} -> {ok, need_otp} or {error: "Email or password
 *   is not correct."}.
 * refresh {token} -> {ok, token}. logout {token} -> {ok}.
 * reset_request {email} -> {ok}. reset_confirm {email, reset_token,
 *   new_password} -> {ok}.
 * GET me, Authorization: Bearer <token> -> {ok, user} or 401
 *   {error: "Please sign in again."}.
 * OTP is 6 digits, 10-minute expiry, single use.
 */

data class AuthUser(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    @SerializedName("email_verified") val emailVerified: Boolean? = null
)

data class AuthResponse(
    val ok: Boolean? = null,
    @SerializedName("user_id") val userId: String? = null,
    val message: String? = null,
    val error: String? = null,
    @SerializedName("need_otp") val needOtp: Boolean? = null,
    @SerializedName("verify_email") val verifyEmail: Boolean? = null,
    val verified: Boolean? = null,
    val token: String? = null,
    val user: AuthUser? = null,
    @SerializedName("reset_token") val resetToken: String? = null
)

interface AuthApi {
    @POST("auth.php")
    suspend fun signup(@Query("action") action: String = "signup", @Body body: Map<String, String>): AuthResponse

    @POST("auth.php")
    suspend fun sendOtp(@Query("action") action: String = "send_otp", @Body body: Map<String, String>): AuthResponse

    @POST("auth.php")
    suspend fun verifyOtp(@Query("action") action: String = "verify_otp", @Body body: Map<String, String>): AuthResponse

    @POST("auth.php")
    suspend fun login(@Query("action") action: String = "login", @Body body: Map<String, String>): AuthResponse

    @POST("auth.php")
    suspend fun refresh(@Query("action") action: String = "refresh", @Body body: Map<String, String>): AuthResponse

    @POST("auth.php")
    suspend fun logout(@Query("action") action: String = "logout", @Body body: Map<String, String>): AuthResponse

    @POST("auth.php")
    suspend fun resetRequest(@Query("action") action: String = "reset_request", @Body body: Map<String, String>): AuthResponse

    @POST("auth.php")
    suspend fun resetConfirm(@Query("action") action: String = "reset_confirm", @Body body: Map<String, String>): AuthResponse

    @GET("auth.php")
    suspend fun me(
        @Query("action") action: String = "me",
        @Header("Authorization") auth: String
    ): AuthResponse
}

fun createAuthApi(baseUrl: String = AppConfig.AUTH_BASE_URL): AuthApi {
    // v1.1.2: 8s connect / 10s read (was 20s / 30s). A stalled auth
    // call used to hold the login screen for 30s; it now fails fast
    // and the screen says accounts are not ready yet.
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    // Retrofit needs a base URL ending in "/"; auth.php is the file, so
    // use the host root and put auth.php in each endpoint path.
    val root = baseUrl.substringBeforeLast("/") + "/"
    return Retrofit.Builder()
        .baseUrl(root)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)
}

/** A one-time code is exactly 6 digits. Anything else is rejected locally. */
fun isValidOtpCode(code: String): Boolean {
    val c = code.trim()
    return c.length == 6 && c.all { it.isDigit() }
}

/** Pulls the plain "error" string out of a raw auth JSON body, if any. */
fun authErrorFromJson(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        Gson().fromJson(raw, AuthResponse::class.java)?.error?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

/** Parses a raw auth JSON body into an AuthResponse, or null when unreadable. */
fun parseAuthResponse(raw: String?): AuthResponse? {
    if (raw.isNullOrBlank()) return null
    return try {
        Gson().fromJson(raw, AuthResponse::class.java)
    } catch (e: Exception) {
        null
    }
}

/**
 * The server's wrong-code reply is "That code is not correct. Please
 * try again."; the app says it shorter: "That code is wrong. Try
 * again." Every other server message is shown as-is: the lockout
 * ("Too many wrong codes. Please try again in an hour."), the expired
 * code and the "ask for a new code" replies all tell the user something
 * a retry cannot fix, so their wording stays.
 */
fun userFacingVerifyOtpError(serverMessage: String): String =
    if (serverMessage == "That code is not correct. Please try again.") {
        AppConfig.WRONG_CODE
    } else {
        serverMessage
    }

/**
 * What a server auth call returned. Network trouble, an unreachable
 * server and any unreadable reply all map to [NotReady]: the app must
 * say accounts are not ready yet, never show a raw error, and never
 * crash. A real server error message is shown as-is when the server
 * sent one (for example wrong email or password, or a wrong code):
 * that includes HTTP error statuses (400, 401, 429), whose JSON body
 * carries the message. Only a reply with no readable message, or no
 * reply at all, is NotReady.
 */
sealed class AuthCall {
    data class Ok(val response: AuthResponse) : AuthCall()
    data class ServerError(val message: String) : AuthCall()
    object NotReady : AuthCall()
}

class AuthRepository(private val api: AuthApi = createAuthApi()) {

    private suspend fun call(block: suspend () -> AuthResponse): AuthCall {
        return try {
            val r = block()
            if (r.error != null && r.error.isNotBlank()) AuthCall.ServerError(r.error)
            else AuthCall.Ok(r)
        } catch (e: HttpException) {
            // The server answered with an error status: 400 for a wrong
            // or expired code, 401 for a wrong password, 429 after too
            // many wrong codes. Retrofit throws for non-2xx, so the
            // message lives in the error body, not the response. Read
            // it: a wrong code is a wrong code, not a dead server.
            // Only a body with no readable message falls back to
            // NotReady.
            val message = try {
                authErrorFromJson(e.response()?.errorBody()?.string())
            } catch (io: Exception) {
                null
            }
            if (message != null) AuthCall.ServerError(message) else AuthCall.NotReady
        } catch (e: Exception) {
            // Unreachable, timed out, partner lock, bad JSON: the account
            // server is not ready. Never surface the raw failure.
            AuthCall.NotReady
        }
    }

    suspend fun signup(name: String, email: String, password: String): AuthCall =
        call { api.signup(body = mapOf("name" to name, "email" to email, "password" to password)) }

    suspend fun sendOtp(email: String, purpose: String): AuthCall =
        call { api.sendOtp(body = mapOf("email" to email, "purpose" to purpose)) }

    suspend fun verifyOtp(email: String, code: String, purpose: String): AuthCall =
        call { api.verifyOtp(body = mapOf("email" to email, "code" to code, "purpose" to purpose)) }

    suspend fun login(email: String, password: String): AuthCall =
        call { api.login(body = mapOf("email" to email, "password" to password)) }

    suspend fun refresh(token: String): AuthCall =
        call { api.refresh(body = mapOf("token" to token)) }

    suspend fun logout(token: String): AuthCall =
        call { api.logout(body = mapOf("token" to token)) }

    suspend fun resetRequest(email: String): AuthCall =
        call { api.resetRequest(body = mapOf("email" to email)) }

    suspend fun resetConfirm(email: String, resetToken: String, newPassword: String): AuthCall =
        call { api.resetConfirm(body = mapOf("email" to email, "reset_token" to resetToken, "new_password" to newPassword)) }

    suspend fun me(token: String): AuthCall =
        call { api.me(auth = "Bearer $token") }
}
