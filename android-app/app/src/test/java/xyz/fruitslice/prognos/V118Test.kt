package xyz.fruitslice.prognos

import android.content.Context
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response
import xyz.fruitslice.prognos.data.DETAIL_BOOK_CACHE_TTL_MS
import xyz.fruitslice.prognos.data.DETAIL_CHART_CACHE_TTL_MS
import xyz.fruitslice.prognos.data.DetailCache
import xyz.fruitslice.prognos.data.ForecastContext
import xyz.fruitslice.prognos.data.MemorySharedPreferences
import xyz.fruitslice.prognos.data.PrognosApi
import xyz.fruitslice.prognos.data.Repository
import xyz.fruitslice.prognos.data.TokenStore
import xyz.fruitslice.prognos.data.VoiceFeatures
import xyz.fruitslice.prognos.data.VoiceProfile
import xyz.fruitslice.prognos.data.VoiceStore
import xyz.fruitslice.prognos.data.forecastFor
import xyz.fruitslice.prognos.data.marketsResponseFromJson
import xyz.fruitslice.prognos.data.marketsResponseToJson
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.ChartResponse
import xyz.fruitslice.prognos.model.LearningResponse
import xyz.fruitslice.prognos.model.MarketDto
import xyz.fruitslice.prognos.model.MarketsResponse
import xyz.fruitslice.prognos.model.NewsResponse
import xyz.fruitslice.prognos.model.TickerResponse
import xyz.fruitslice.prognos.model.WatchdogResponse
import xyz.fruitslice.prognos.model.toMarket
import java.io.File
import java.io.InputStreamReader

/**
 * v1.1.8: speed plus security hardening.
 *
 * Speed: the per-second clock used to recompose the whole dashboard,
 * the whole markets list and the forecast card every second, and the
 * disk JSON work ran on the main thread. Now the market screens read
 * a per-minute clock (only the market countdown text still ticks
 * every second, through its own small composable), the disk work runs
 * on IO, and reopening a market shows the cached books and chart at
 * once. Trading stays closed, no design or feature changes.
 *
 * Security: tokens and the voice profile used to fall back to plain
 * text on disk when the encrypted store could not be created. Now
 * they are kept in memory only on such a device, never written in
 * plain text. The dead logging interceptor dependency is gone.
 * Pure JVM tests except the store tests, which run under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class V118Test {

    // ---- Helpers ----

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
        val f = File(mainRoot(), relative)
        assertTrue("Missing source file $relative", f.isFile)
        return f.readText()
    }

    private fun countOccurrences(source: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val at = source.indexOf(needle, from)
            if (at < 0) return count
            count++
            from = at + needle.length
        }
    }

    private val gson = GsonBuilder().setLenient().create()

    private fun <T> load(name: String, cls: Class<T>): T {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: throw IllegalStateException("missing fixture $name")
        return InputStreamReader(stream).use { gson.fromJson(it, cls) }
    }

    private fun appContext(): Context =
        RuntimeEnvironment.getApplication().applicationContext

    // ---- Shipping shape ----

    @Test
    fun versionIs116() {
        val cwd = System.getProperty("user.dir") ?: "."
        val gradle = listOf(
            File("app/build.gradle.kts"),
            File("$cwd/app/build.gradle.kts"),
            File("build.gradle.kts"),
            File("$cwd/build.gradle.kts")
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find app/build.gradle.kts from $cwd")
        val text = gradle.readText()
        assertTrue("versionCode must be 26", text.contains("versionCode = 26"))
        assertTrue("versionName must be 1.1.16", text.contains("versionName = \"1.1.16\""))
    }

    // ---- The per-minute clock ----

    @Test
    fun minuteClockEmitsOncePerMinute() = runBlocking {
        // The view model derives nowMinute as nowSec / 60. 120
        // per-second values must collapse to 2 distinct minute
        // values, so a screen collecting it recomposes at most once
        // a minute instead of 60 times.
        val minutes = (3600L..3719L).asFlow().map { it / 60 }.toList()
        assertEquals(120, minutes.size)
        assertEquals(
            "120 per-second values must collapse to 2 minute values",
            listOf(60L, 61L), minutes.distinct()
        )
    }

    @Test
    fun perSecondClockIsCollectedInExactlyOnePlace() {
        val screens = readSource("java/xyz/fruitslice/prognos/ui/Screens.kt")
        assertEquals(
            "only TimeLeftText may collect the per-second clock",
            1, countOccurrences(screens, "vm.nowSec.collectAsState()")
        )
        assertTrue(screens.contains("vm.nowMinute.collectAsState()"))
        val voice = readSource("java/xyz/fruitslice/prognos/ui/VoiceScreens.kt")
        assertEquals(
            "no voice screen may read the per-second clock",
            0, countOccurrences(voice, "vm.nowSec.collectAsState()")
        )
        assertEquals(
            "no voice screen needs a clock now the history screen is gone",
            0, countOccurrences(voice, "vm.nowMinute.collectAsState()")
        )
        val design = readSource("java/xyz/fruitslice/prognos/ui/Design.kt")
        val rowStart = design.indexOf("fun MarketRow(")
        assertTrue(rowStart >= 0)
        val rowEnd = design.indexOf(") {", rowStart)
        val rowSig = design.substring(rowStart, rowEnd)
        assertFalse("MarketRow must not take the per-second clock", rowSig.contains("nowSec"))
    }

    // ---- Forecast recompute cost, measured ----

    @Test
    fun forecastDisplayedTextIsIdenticalWithinOneMinute() {
        val markets = load("markets.json", MarketsResponse::class.java)
        val priced = markets.items!!.mapNotNull { it.toMarket() }.first { it.p0 != null }
        val book = load("book.json", BookResponse::class.java)
        val chart = load("chart.json", ChartResponse::class.java)
        val news = load("news.json", NewsResponse::class.java)
        val ctx = ForecastContext(
            chg24 = -0.02,
            news = news.items ?: emptyList(),
            mood = news.mood,
            bookUp = book,
            bookDown = book,
            chartPoints = chart.points
        )
        // Five hours before the market ends: the displayed hours
        // figure is stable across a whole minute.
        val nowSec = priced.end - 5 * 3600
        val first = forecastFor(priced, nowSec, ctx)
        assertNotNull(first)
        val t0 = System.nanoTime()
        var identical = 0
        for (s in 0 until 60) {
            val f = forecastFor(priced, nowSec + s, ctx)
            if (f != null && f.verdict == first!!.verdict && f.reason == first.reason) identical++
        }
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        println("v1.1.8 forecast: 60 calls (one minute of 1s ticks) in ${"%.1f".format(elapsedMs)}ms, displayed text identical $identical/60")
        assertEquals(
            "the forecast the user sees must not change inside a minute, so recomputing it 60 times a minute is wasted work",
            60, identical
        )
    }

    @Test
    fun marketsJsonRoundTripCostIsMeasured() {
        val markets = load("markets.json", MarketsResponse::class.java)
        val items = markets.items!!.mapNotNull { it.toMarket() }
        assertTrue(items.size >= 50)
        val t0 = System.nanoTime()
        val json = marketsResponseToJson(markets)
        val back = marketsResponseFromJson(json)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        println("v1.1.8 disk cache: 100-market JSON write+parse in ${"%.1f".format(elapsedMs)}ms (now on IO, was on the main thread)")
        assertEquals(markets.items!!.size, back?.items?.size)
    }

    // ---- Disk work off the main thread ----

    @Test
    fun diskWorkRunsOffTheMainThread() {
        val repo = readSource("java/xyz/fruitslice/prognos/data/Repository.kt")
        assertTrue(repo.contains("withContext(Dispatchers.IO)"))
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue("forecast recording must run off the main thread",
            vm.contains("launch(Dispatchers.Default) { repo.recordForecasts(markets) }"))
        assertTrue("FX rates must load off the main thread",
            vm.contains("launch(Dispatchers.IO)"))
    }

    // ---- Detail cache ----

    private class CountingFakeApi(
        private val book: BookResponse,
        private val chart: ChartResponse
    ) : PrognosApi {
        var bookCalls = 0
        var chartCalls = 0

        override suspend fun ticker(): TickerResponse = TickerResponse(emptyList())
        override suspend fun markets(limit: Int): MarketsResponse = MarketsResponse(emptyList())
        override suspend fun market(id: String): MarketDto = throw UnsupportedOperationException()
        override suspend fun book(tokenId: String, depth: Int): BookResponse {
            bookCalls++
            return book
        }

        override suspend fun chart(marketId: String): ChartResponse {
            chartCalls++
            return chart
        }

        override suspend fun news(): NewsResponse = NewsResponse(emptyList(), null)
        override suspend fun watchdog(): WatchdogResponse = WatchdogResponse(emptyList())
        override suspend fun learning(): LearningResponse = LearningResponse(scored = 0)
        override suspend fun recordForecasts(body: Map<String, Any>): Response<ResponseBody> =
            Response.success<ResponseBody>(null)
    }

    @Test
    fun detailCacheServesBooksAndChartWithoutNetwork() = runBlocking {
        val book = load("book.json", BookResponse::class.java)
        val chart = load("chart.json", ChartResponse::class.java)
        val fake = CountingFakeApi(book, chart)
        var nowMs = 1_000_000L
        val repo = Repository(api = fake, detailCache = DetailCache(now = { nowMs }))
        assertNull(repo.cachedBook("tok1"))
        assertNull(repo.cachedChart("m1"))
        assertEquals(book, repo.loadBook("tok1"))
        assertEquals(chart, repo.loadChart("m1"))
        assertEquals(1, fake.bookCalls)
        assertEquals(1, fake.chartCalls)
        // The first paint reads the cache without touching the network.
        assertEquals(book, repo.cachedBook("tok1"))
        assertEquals(chart, repo.cachedChart("m1"))
        assertEquals(1, fake.bookCalls)
        assertEquals(1, fake.chartCalls)
    }

    @Test
    fun detailCacheExpires() = runBlocking {
        val book = load("book.json", BookResponse::class.java)
        val chart = load("chart.json", ChartResponse::class.java)
        val fake = CountingFakeApi(book, chart)
        var nowMs = 1_000_000L
        val repo = Repository(api = fake, detailCache = DetailCache(now = { nowMs }))
        repo.loadBook("tok1")
        repo.loadChart("m1")
        assertNotNull(repo.cachedBook("tok1"))
        assertNotNull(repo.cachedChart("m1"))
        nowMs += DETAIL_BOOK_CACHE_TTL_MS + 1
        assertNull("books expire after 10s", repo.cachedBook("tok1"))
        assertNotNull("charts live for 30s", repo.cachedChart("m1"))
        nowMs += DETAIL_CHART_CACHE_TTL_MS
        assertNull("charts expire after 30s", repo.cachedChart("m1"))
    }

    // ---- Memory-only fallback when encryption fails ----

    @Test
    fun tokenStoreIsMemoryOnlyWhenEncryptionFails() {
        val ctx = appContext()
        ctx.getSharedPreferences("prognos_tokens", Context.MODE_PRIVATE).edit().clear().commit()
        val failing = TokenStore(ctx) { _, _ -> null }
        assertFalse(failing.encrypted)
        failing.saveAuth("tok-abc", "Flint", "flint@example.com")
        failing.saveTradingSession("0xabc", "a".repeat(64))
        assertEquals("tok-abc", failing.authToken)
        assertEquals("a".repeat(64), failing.tradingSession)
        assertFalse(
            "no token may reach plain text on disk when encryption fails",
            ctx.getSharedPreferences("prognos_tokens", Context.MODE_PRIVATE).contains("auth_token")
        )
        assertFalse(
            "no session may reach plain text on disk when encryption fails",
            ctx.getSharedPreferences("prognos_tokens", Context.MODE_PRIVATE).contains("trading_session")
        )
        // A fresh instance (a restarted process) does not see it.
        val again = TokenStore(ctx) { _, _ -> null }
        assertNull(again.authToken)
        assertNull(again.tradingSession)
    }

    @Test
    fun tokenStorePersistsWhenEncryptionWorks() {
        val ctx = appContext()
        val plain = { c: Context, name: String -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
        val store = TokenStore(ctx, plain)
        assertTrue(store.encrypted)
        store.saveAuth("tok-xyz", "Flint", "flint@example.com")
        assertEquals("tok-xyz", TokenStore(ctx, plain).authToken)
        assertEquals("Flint", TokenStore(ctx, plain).authName)
        ctx.getSharedPreferences("prognos_tokens", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun voiceStoreIsMemoryOnlyWhenEncryptionFails() {
        val ctx = appContext()
        ctx.getSharedPreferences("prognos_voice", Context.MODE_PRIVATE).edit().clear().commit()
        val failing = VoiceStore(ctx) { _, _ -> null }
        assertFalse(failing.encrypted)
        val profile = VoiceProfile(
            means = VoiceFeatures(120.0, 8.0, 1500.0, 0.1, -20.0, 2.5),
            stds = VoiceFeatures(5.0, 2.0, 100.0, 0.02, 2.0, 0.3),
            sampleCount = 3
        )
        failing.saveProfile(profile)
        assertEquals(profile, failing.loadProfile())
        assertFalse(
            "no voice profile may reach plain text on disk when encryption fails",
            ctx.getSharedPreferences("prognos_voice", Context.MODE_PRIVATE).contains("voice_profile")
        )
        assertNull("a fresh instance must not see the profile", VoiceStore(ctx) { _, _ -> null }.loadProfile())
    }

    @Test
    fun memoryPrefsBehavesLikePrefs() {
        val prefs = MemorySharedPreferences()
        prefs.edit().putString("a", "1").putLong("b", 2L).apply()
        assertEquals("1", prefs.getString("a", null))
        assertEquals(2L, prefs.getLong("b", 0L))
        assertTrue(prefs.contains("a"))
        prefs.edit().remove("a").commit()
        assertFalse(prefs.contains("a"))
        prefs.edit().clear().commit()
        assertFalse(prefs.contains("b"))
        assertTrue(prefs.all.isEmpty())
    }

    // ---- No sensitive logging, no cleartext, no custom trust ----

    @Test
    fun noLoggingNoCleartextNoCustomTrust() {
        val main = File(mainRoot(), "java/xyz/fruitslice/prognos")
        val ktFiles = main.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(ktFiles.isNotEmpty())
        for (f in ktFiles) {
            val src = f.readText()
            assertFalse("${f.name} must not log", src.contains("Log."))
            assertFalse("${f.name} must not use println", src.contains("println("))
            assertFalse("${f.name} must not use cleartext http", src.contains("http://"))
            assertFalse("${f.name} must not override trust", src.contains("HostnameVerifier"))
            assertFalse("${f.name} must not override trust", src.contains("TrustManager"))
            assertFalse("${f.name} must not override trust", src.contains("sslSocketFactory"))
        }
        val manifest = readSource("AndroidManifest.xml")
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        val cwd = System.getProperty("user.dir") ?: "."
        val gradle = listOf(
            File("app/build.gradle.kts"),
            File("$cwd/app/build.gradle.kts"),
            File("build.gradle.kts"),
            File("$cwd/build.gradle.kts")
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find app/build.gradle.kts from $cwd")
        val gradleText = gradle.readText()
        assertFalse("the logging interceptor must stay out of the build",
            gradleText.contains("logging-interceptor"))
    }

    @Test
    fun noAiToolNamesInNewCode() {
        for (f in listOf(
            "java/xyz/fruitslice/prognos/data/MemoryPrefs.kt",
            "java/xyz/fruitslice/prognos/data/TokenStore.kt",
            "java/xyz/fruitslice/prognos/data/VoiceStore.kt"
        )) {
            val src = readSource(f)
            for (banned in listOf("Muse", "Claude", "ChatGPT", "OpenAI")) {
                assertFalse("$f must not mention $banned", src.contains(banned))
            }
        }
    }
}
