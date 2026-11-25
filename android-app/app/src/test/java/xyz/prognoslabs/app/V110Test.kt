package xyz.prognoslabs.app

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import xyz.prognoslabs.app.data.Analytics
import xyz.prognoslabs.app.data.AnalyticsEvents
import xyz.prognoslabs.app.data.AnalyticsTracker
import xyz.prognoslabs.app.data.FirebaseAnalyticsTracker
import xyz.prognoslabs.app.data.NoOpAnalyticsTracker
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.sanitizeAnalyticsParams
import xyz.prognoslabs.app.data.voiceQuestionParams
import xyz.prognoslabs.app.data.voiceQuestionType
import xyz.prognoslabs.app.data.walletAddressType

/**
 * v1.1.10: Firebase Analytics (Google Analytics) so Flint can track
 * installs, signups and in-app activity. Automatic events
 * (first_open, screen views) come free from the SDK; the four custom
 * events are sign_up, wallet_linked, voice_order and voice_question,
 * each with coarse, non-identifying parameters only. No wallet
 * address, email, name, phone number, voice data or trade amount is
 * ever logged, no user properties are set, and there is no purchase
 * event while trading is closed. The analytics layer never crashes
 * the app if Firebase is unavailable.
 */
@RunWith(RobolectricTestRunner::class)
class V110Test {

    private fun appContext(): Context =
        RuntimeEnvironment.getApplication().applicationContext

    private fun findAppFile(name: String): File {
        val cwd = System.getProperty("user.dir") ?: "."
        return listOf(
            File("app/$name"),
            File("$cwd/app/$name"),
            File(name),
            File("$cwd/$name")
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find $name from $cwd")
    }

    private class RecordingTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        override fun logEvent(name: String, params: Map<String, String>) {
            events += name to params
        }
    }

    private class ThrowingTracker : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>): Nothing =
            throw IllegalStateException("firebase down")
    }

    // ---- Shipping shape ----

    @Test
    fun versionIs1120() {
        val text = findAppFile("build.gradle.kts").readText()
        assertTrue("versionCode must be 30", text.contains("versionCode = 30"))
        assertTrue("versionName must be 1.1.20", text.contains("versionName = \"1.1.20\""))
    }

    @Test
    fun googleServicesJsonMatchesApp() {
        val text = findAppFile("google-services.json").readText()
        assertTrue(text.contains("prognos-labs"))
        assertTrue(text.contains("xyz.prognoslabs.app"))
        assertTrue(text.contains("1:1021974818658:android:298105067d9bce31cf463b"))
    }

    @Test
    fun firebasePluginApplied() {
        val cwd = System.getProperty("user.dir") ?: "."
        // The root build file carries the versioned google-services
        // plugin; the app module file only applies it, without a version.
        val rootGradle = listOf(
            File("build.gradle.kts"),
            File("$cwd/build.gradle.kts"),
            File("$cwd/../build.gradle.kts")
        ).firstOrNull {
            it.isFile && it.readText().contains("com.google.gms.google-services") &&
                it.readText().contains("4.4.2")
        } ?: throw AssertionError("Cannot find root build.gradle.kts with google-services plugin from $cwd")
        assertTrue(rootGradle.readText().contains("version \"4.4.2\""))
        val appText = findAppFile("build.gradle.kts").readText()
        assertTrue(appText.contains("firebase-bom"))
        assertTrue(appText.contains("firebase-analytics"))
    }

    // ---- The sanitizer: no personal data ever reaches Firebase ----

    @Test
    fun sanitizerKeepsSafeParams() {
        val kept = sanitizeAnalyticsParams(
            mapOf(
                "method" to "email",
                "address_type" to "evm",
                "side" to "up",
                "asset" to "BTC",
                "success" to "true",
                "type" to "price"
            )
        )
        assertEquals(6, kept.size)
        assertEquals("email", kept["method"])
        assertEquals("evm", kept["address_type"])
        assertEquals("up", kept["side"])
        assertEquals("BTC", kept["asset"])
        assertEquals("true", kept["success"])
        assertEquals("price", kept["type"])
    }

    @Test
    fun sanitizerDropsForbiddenKeys() {
        val kept = sanitizeAnalyticsParams(
            mapOf(
                "email" to "flint@example.com",
                "name" to "Flint",
                "phone" to "09012345678",
                "wallet_address" to "0x216e95bABd339a07aB020f448AeA3911151677D9",
                "address" to "0x216e95bABd339a07aB020f448AeA3911151677D9",
                "amount" to "50",
                "amount_usd" to "50",
                "balance" to "100",
                "token" to "abc123",
                "session" to "sess-1",
                "voiceprint" to "voice-data",
                "password" to "secret",
                "otp" to "123456",
                "user_id" to "42",
                "method" to "email"
            )
        )
        assertEquals(mapOf("method" to "email"), kept)
    }

    @Test
    fun sanitizerDropsUnsafeValues() {
        val kept = sanitizeAnalyticsParams(
            mapOf(
                "asset" to "flint@example.com",
                "side" to "up down",
                "success" to "yes!",
                "type" to "a".repeat(41),
                "method" to "0x216e95bABd339a07aB020f448AeA3911151677D9",
                "address_type" to "evm"
            )
        )
        assertEquals(mapOf("address_type" to "evm"), kept)
    }

    @Test
    fun sanitizerDropsUnknownKeys() {
        val kept = sanitizeAnalyticsParams(
            mapOf("surprise" to "x", "asset" to "ETH")
        )
        assertEquals(mapOf("asset" to "ETH"), kept)
    }

    @Test
    fun hookParamsPassSanitizerUnchanged() {
        // The exact parameter maps the four call sites build. If any
        // of them ever carried personal data, the sanitizer would
        // strip it and this test would fail.
        val hookParams = listOf(
            mapOf(AnalyticsEvents.PARAM_METHOD to "email"),
            mapOf(AnalyticsEvents.PARAM_ADDRESS_TYPE to "evm"),
            mapOf(AnalyticsEvents.PARAM_ADDRESS_TYPE to "solana"),
            mapOf(
                AnalyticsEvents.PARAM_SIDE to "up",
                AnalyticsEvents.PARAM_ASSET to "BTC",
                AnalyticsEvents.PARAM_SUCCESS to "true"
            ),
            mapOf(
                AnalyticsEvents.PARAM_SIDE to "down",
                AnalyticsEvents.PARAM_ASSET to "more",
                AnalyticsEvents.PARAM_SUCCESS to "false"
            ),
            mapOf(
                AnalyticsEvents.PARAM_TYPE to "price",
                AnalyticsEvents.PARAM_ASSET to "ETH"
            ),
            mapOf(AnalyticsEvents.PARAM_TYPE to "greeting_hello")
        )
        for (params in hookParams) {
            assertEquals(params, sanitizeAnalyticsParams(params))
        }
    }

    // ---- Event shapes ----

    @Test
    fun voiceQuestionParamsForEveryType() {
        assertEquals("price", voiceQuestionType(VoiceQuestion.Price("BTC")))
        assertEquals("balance", voiceQuestionType(VoiceQuestion.Balance))
        assertEquals("how_to_trade", voiceQuestionType(VoiceQuestion.HowToTrade))
        assertEquals("trading_opens", voiceQuestionType(VoiceQuestion.TradingOpens))
        assertEquals("market_info", voiceQuestionType(VoiceQuestion.MarketInfo("m1")))
        assertEquals("greeting_hello", voiceQuestionType(VoiceQuestion.GreetingHello))
        assertEquals("greeting_how_are_you", voiceQuestionType(VoiceQuestion.GreetingHowAreYou))
        assertEquals("most_liquid", voiceQuestionType(VoiceQuestion.MostLiquid))
        assertEquals("knowledge", voiceQuestionType(VoiceQuestion.Knowledge("answer")))
        assertEquals("news", voiceQuestionType(VoiceQuestion.News("ETH")))

        assertEquals(
            mapOf("type" to "price", "asset" to "BTC"),
            voiceQuestionParams(VoiceQuestion.Price("BTC"))
        )
        assertEquals(
            mapOf("type" to "news", "asset" to "ETH"),
            voiceQuestionParams(VoiceQuestion.News("ETH"))
        )
        assertEquals(
            mapOf("type" to "greeting_hello"),
            voiceQuestionParams(VoiceQuestion.GreetingHello)
        )
        assertEquals(
            mapOf("type" to "greeting_how_are_you"),
            voiceQuestionParams(VoiceQuestion.GreetingHowAreYou)
        )
        assertFalse(voiceQuestionParams(VoiceQuestion.Balance).containsKey("asset"))
    }

    @Test
    fun walletAddressTypeEvmAndSolana() {
        assertEquals("evm", walletAddressType("0x216e95bABd339a07aB020f448AeA3911151677D9"))
        assertEquals("evm", walletAddressType("  0xabcdef0123456789abcdef0123456789abcdef01  "))
        assertEquals("solana", walletAddressType("41nX57qXtLuJ7YWfJRmXBYdjrJPFiH4xNrSeNuTtLfef"))
        assertEquals("solana", walletAddressType("DYw8jCTfwHNRJhhmFcbXvVDTqWMEVFBX6ZKUmG5CNSKK"))
    }

    // ---- The layer never crashes the app ----

    @Test
    fun analyticsNeverThrowsWhenTrackerThrows() {
        Analytics.setTrackerForTests(ThrowingTracker())
        try {
            Analytics.logEvent("sign_up", mapOf("method" to "email"))
            Analytics.logEvent("voice_order", mapOf("side" to "up"))
        } finally {
            Analytics.setTrackerForTests(NoOpAnalyticsTracker)
        }
    }

    @Test
    fun recordingTrackerReceivesExactEvents() {
        val recording = RecordingTracker()
        Analytics.setTrackerForTests(recording)
        try {
            Analytics.logEvent(
                AnalyticsEvents.SIGN_UP,
                mapOf(AnalyticsEvents.PARAM_METHOD to "email")
            )
            Analytics.logEvent(
                AnalyticsEvents.WALLET_LINKED,
                mapOf(AnalyticsEvents.PARAM_ADDRESS_TYPE to "solana")
            )
            Analytics.logEvent(
                AnalyticsEvents.VOICE_ORDER,
                mapOf(
                    AnalyticsEvents.PARAM_SIDE to "down",
                    AnalyticsEvents.PARAM_ASSET to "BTC",
                    AnalyticsEvents.PARAM_SUCCESS to "true"
                )
            )
            Analytics.logEvent(
                AnalyticsEvents.VOICE_QUESTION,
                voiceQuestionParams(VoiceQuestion.News("BTC"))
            )
        } finally {
            Analytics.setTrackerForTests(NoOpAnalyticsTracker)
        }
        assertEquals(4, recording.events.size)
        assertEquals("sign_up", recording.events[0].first)
        assertEquals(mapOf("method" to "email"), recording.events[0].second)
        assertEquals("wallet_linked", recording.events[1].first)
        assertEquals(mapOf("address_type" to "solana"), recording.events[1].second)
        assertEquals("voice_order", recording.events[2].first)
        assertEquals(
            mapOf("side" to "down", "asset" to "BTC", "success" to "true"),
            recording.events[2].second
        )
        assertEquals("voice_question", recording.events[3].first)
        assertEquals(
            mapOf("type" to "news", "asset" to "BTC"),
            recording.events[3].second
        )
    }

    @Test
    fun firebaseTrackerNeverThrowsWithoutFirebase() {
        // Under Robolectric there is no Play services and Firebase may
        // or may not initialize: either way logging must not throw.
        val tracker = FirebaseAnalyticsTracker(appContext())
        tracker.logEvent("sign_up", mapOf("method" to "email"))
        tracker.logEvent(
            "voice_order",
            mapOf("side" to "up", "asset" to "BTC", "success" to "false")
        )
    }

    @Test
    fun initSwapsInFirebaseTracker() {
        Analytics.init(appContext())
        try {
            assertTrue(Analytics.tracker is FirebaseAnalyticsTracker)
        } finally {
            Analytics.setTrackerForTests(NoOpAnalyticsTracker)
        }
    }

    @Test
    fun noPurchaseOrDepositEventNamesExist() {
        // Trading is closed: the event names must not include any
        // purchase, deposit or trade event.
        val names = listOf(
            AnalyticsEvents.SIGN_UP,
            AnalyticsEvents.WALLET_LINKED,
            AnalyticsEvents.VOICE_ORDER,
            AnalyticsEvents.VOICE_QUESTION
        )
        for (name in names) {
            assertFalse(name.contains("purchase"))
            assertFalse(name.contains("deposit"))
            assertFalse(name.contains("trade"))
        }
    }
}
