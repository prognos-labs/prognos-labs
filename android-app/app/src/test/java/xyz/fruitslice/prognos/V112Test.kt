package xyz.fruitslice.prognos

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import xyz.fruitslice.prognos.data.MARKETS_CACHE_TTL_MS
import xyz.fruitslice.prognos.data.TICKER_CACHE_TTL_MS
import xyz.fruitslice.prognos.data.MemoryMarketCache
import xyz.fruitslice.prognos.data.PrognosApi
import xyz.fruitslice.prognos.data.Repository
import xyz.fruitslice.prognos.data.marketsResponseFromJson
import xyz.fruitslice.prognos.data.marketsResponseToJson
import xyz.fruitslice.prognos.data.tickerResponseFromJson
import xyz.fruitslice.prognos.data.tickerResponseToJson
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.ChartResponse
import xyz.fruitslice.prognos.model.LangText
import xyz.fruitslice.prognos.model.LearningResponse
import xyz.fruitslice.prognos.model.MarketDto
import xyz.fruitslice.prognos.model.MarketsResponse
import xyz.fruitslice.prognos.model.NewsResponse
import xyz.fruitslice.prognos.model.OutcomeDto
import xyz.fruitslice.prognos.model.TickerAsset
import xyz.fruitslice.prognos.model.TickerResponse
import xyz.fruitslice.prognos.model.WatchdogResponse
import xyz.fruitslice.prognos.model.toDto
import xyz.fruitslice.prognos.model.toMarket

private fun sampleDto(id: String, question: String, bid: Double, ask: Double) = MarketDto(
    marketId = id,
    question = LangText(question),
    status = "open",
    closed = false,
    bestBid = bid,
    bestAsk = ask,
    lastTradePrice = (bid + ask) / 2.0,
    outcomes = listOf(
        OutcomeDto(LangText("Yes"), "tok-up-$id"),
        OutcomeDto(LangText("No"), "tok-down-$id")
    )
)

private val sampleMarkets = MarketsResponse(
    listOf(
        sampleDto("m1", "Will Bitcoin go up?", 0.51, 0.53),
        sampleDto("m2", "Will Ethereum go up?", 0.48, 0.50)
    )
)

private val sampleTicker = TickerResponse(
    listOf(
        TickerAsset("BTC", "Bitcoin", 61_840.0, -1.86),
        TickerAsset("ETH", "Ethereum", 1_753.0, -1.34)
    )
)

/**
 * v1.1.2: performance. The app was slow because every screen waited
 * on the network from scratch: the markets endpoint takes ~7s
 * server-side and the old refresh ran markets, ticker, watchdog and
 * learning one after another (~11s) behind a full-screen spinner.
 * Markets and ticker are now cached (memory + disk), shown instantly
 * and refreshed in the background; the home, detail and trading
 * loads run in parallel; timeouts fail fast. Pure JVM tests.
 */
class V112Test {

    /** Counts network calls and can fail after N calls, to test fallback. */
    private class CountingFakeApi(
        private val marketsResponses: List<MarketsResponse> = listOf(sampleMarkets),
        private val tickerResponses: List<TickerResponse> = listOf(sampleTicker),
        private val failMarketsAfter: Int = Int.MAX_VALUE,
        private val failTickerAfter: Int = Int.MAX_VALUE
    ) : PrognosApi {
        var marketsCalls = 0
        var tickerCalls = 0

        override suspend fun markets(limit: Int): MarketsResponse {
            marketsCalls++
            if (marketsCalls > failMarketsAfter) throw RuntimeException("network down")
            return marketsResponses[minOf(marketsCalls - 1, marketsResponses.size - 1)]
        }

        override suspend fun ticker(): TickerResponse {
            tickerCalls++
            if (tickerCalls > failTickerAfter) throw RuntimeException("network down")
            return tickerResponses[minOf(tickerCalls - 1, tickerResponses.size - 1)]
        }

        override suspend fun market(id: String): MarketDto = throw UnsupportedOperationException()
        override suspend fun book(tokenId: String, depth: Int): BookResponse = throw UnsupportedOperationException()
        override suspend fun chart(marketId: String): ChartResponse = throw UnsupportedOperationException()
        override suspend fun news(): NewsResponse = throw UnsupportedOperationException()
        override suspend fun watchdog(): WatchdogResponse = throw UnsupportedOperationException()
        override suspend fun learning(): LearningResponse = throw UnsupportedOperationException()
        override suspend fun recordForecasts(body: Map<String, Any>): Response<ResponseBody> = throw UnsupportedOperationException()
    }

    // ---- 1. Memory cache: no second network call inside the TTL ----

    @Test
    fun marketsServedFromMemoryWithoutRefetch() = runBlocking {
        val api = CountingFakeApi()
        val repo = Repository(api)
        val first = repo.loadMarkets()
        val second = repo.loadMarkets()
        assertEquals(1, api.marketsCalls)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun tickerServedFromMemoryWithoutRefetch() = runBlocking {
        val api = CountingFakeApi()
        val repo = Repository(api)
        val first = repo.loadTicker()
        val second = repo.loadTicker()
        assertEquals(1, api.tickerCalls)
        assertEquals(first.map { it.asset }, second.map { it.asset })
    }

    @Test
    fun marketsCacheExpiresAfterTtl() = runBlocking {
        var nowMs = 1_000_000L
        val cache = MemoryMarketCache(now = { nowMs })
        val api = CountingFakeApi()
        val repo = Repository(api, memory = cache)
        repo.loadMarkets()
        assertEquals(1, api.marketsCalls)
        nowMs += MARKETS_CACHE_TTL_MS + 1
        repo.loadMarkets()
        assertEquals(2, api.marketsCalls)
    }

    @Test
    fun freshCacheBeatsStaleCache() {
        var nowMs = 1_000_000L
        val cache = MemoryMarketCache(now = { nowMs })
        val market = sampleMarkets.items!!.mapNotNull { it.toMarket() }
        cache.putMarkets(market)
        assertNotNull(cache.freshMarkets())
        assertNotNull(cache.staleMarkets())
        nowMs += MARKETS_CACHE_TTL_MS + 1
        assertNull(cache.freshMarkets())
        // Stale data is still there to show while a refresh runs.
        assertNotNull(cache.staleMarkets())
    }

    // ---- 2. Failure: last real data is returned, never a blank screen ----

    @Test
    fun marketsFallBackToStaleDataWhenNetworkFails() = runBlocking {
        var nowMs = 1_000_000L
        val cache = MemoryMarketCache(now = { nowMs })
        val api = CountingFakeApi(failMarketsAfter = 1)
        val repo = Repository(api, memory = cache)
        val first = repo.loadMarkets()
        assertEquals(1, api.marketsCalls)
        nowMs += MARKETS_CACHE_TTL_MS + 1
        val second = repo.loadMarkets()
        assertEquals(2, api.marketsCalls)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun marketsThrowOnlyWhenNoDataExistsAnywhere() = runBlocking {
        val api = CountingFakeApi(failMarketsAfter = 0)
        val repo = Repository(api)
        try {
            repo.loadMarkets()
            throw AssertionError("Expected loadMarkets to throw with no cache and no network")
        } catch (e: RuntimeException) {
            assertEquals("network down", e.message)
        }
    }

    // ---- 3. Disk cache: the stored JSON is a real server reply ----

    @Test
    fun marketsJsonRoundTripKeepsRealData() {
        val json = marketsResponseToJson(sampleMarkets)
        val back = marketsResponseFromJson(json)
        assertNotNull(back)
        val markets = back!!.items!!.mapNotNull { it.toMarket() }
        assertEquals(listOf("m1", "m2"), markets.map { it.id })
        assertEquals("Will Bitcoin go up?", markets[0].question)
        assertEquals(0.52, markets[0].p0!!, 0.0001)
        assertEquals(0.49, markets[1].p0!!, 0.0001)
    }

    @Test
    fun tickerJsonRoundTripKeepsRealData() {
        val json = tickerResponseToJson(sampleTicker)
        val back = tickerResponseFromJson(json)
        assertNotNull(back)
        assertEquals(listOf("BTC", "ETH"), back!!.assets!!.map { it.asset })
        assertEquals(61_840.0, back.assets!![0].price!!, 0.001)
    }

    @Test
    fun marketDtoRoundTripKeepsPrices() {
        val market = sampleMarkets.items!!.mapNotNull { it.toMarket() }[0]
        val back = market.toDto().toMarket()
        assertNotNull(back)
        assertEquals(market.id, back!!.id)
        assertEquals(market.question, back.question)
        assertEquals(market.bid, back.bid)
        assertEquals(market.ask, back.ask)
        assertEquals(market.p0, back.p0)
    }

    @Test
    fun badCacheJsonIsIgnored() {
        assertNull(marketsResponseFromJson(""))
        assertNull(marketsResponseFromJson("not json"))
        assertNull(tickerResponseFromJson(""))
        assertNull(tickerResponseFromJson("not json"))
    }

    // ---- 4. Timeouts fail fast instead of freezing a screen ----

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

    @Test
    fun apiTimeoutsAreShort() {
        for (file in listOf(
            "java/xyz/fruitslice/prognos/data/Api.kt",
            "java/xyz/fruitslice/prognos/data/AuthApi.kt",
            "java/xyz/fruitslice/prognos/data/TradingApi.kt"
        )) {
            val src = readSource(file)
            assertTrue("$file must use an 8s connect timeout", src.contains("connectTimeout(8, TimeUnit.SECONDS)"))
            assertTrue("$file must use a 10s read timeout", src.contains("readTimeout(10, TimeUnit.SECONDS)"))
            assertFalse("$file must not keep the 20s connect timeout", src.contains("connectTimeout(20,"))
            assertFalse("$file must not keep the 30s read timeout", src.contains("readTimeout(30,"))
        }
    }

    // ---- 5. Refresh runs in parallel, never overlapping ----

    @Test
    fun refreshAllRunsCallsInParallelWithAGuard() {
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue("refreshAll must guard against overlapping refreshes", vm.contains("refreshMutex.tryLock()"))
        assertTrue("refreshAll must load markets in parallel", vm.contains("async { runCatching { repo.loadMarkets() }"))
        assertTrue("refreshAll must load the ticker in parallel", vm.contains("async { runCatching { repo.loadTicker() }"))
        assertTrue("refreshAll must not wipe a screen that already shows data", vm.contains("if (!silent && !hadData)"))
    }

    // ---- 6. Launcher icon: lime background, black P, proper sizes ----
    // javax.imageio is not on the unit-test classpath, so the PNGs
    // are read with a minimal header + pixel decoder below instead.

    private fun resFile(relative: String): File {
        val root = mainRoot()
        val f = File(root, "res/$relative")
        assertTrue("Missing resource file res/$relative", f.isFile)
        return f
    }

    /** Minimal PNG reader: dimensions, colour type, RGBA pixels. */
    private data class Png(val width: Int, val height: Int, val colourType: Int, val rgba: ByteArray?)

    private fun readPng(file: File): Png {
        val b = file.readBytes()
        assertTrue("Not a PNG: ${file.path}", b.size > 33 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte())
        fun be32(at: Int) =
            ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
                ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
        var pos = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colourType = 0
        val idat = java.io.ByteArrayOutputStream()
        while (pos + 8 <= b.size) {
            val len = be32(pos)
            val type = String(b, pos + 4, 4, Charsets.US_ASCII)
            val data = pos + 8
            when (type) {
                "IHDR" -> {
                    width = be32(data); height = be32(data + 4)
                    bitDepth = b[data + 8].toInt() and 0xFF
                    colourType = b[data + 9].toInt() and 0xFF
                }
                "IDAT" -> idat.write(b, data, len)
                "IEND" -> break
            }
            pos = data + len + 4
        }
        if (colourType != 6 || bitDepth != 8) return Png(width, height, colourType, null)
        val raw = ByteArray(height * (1 + width * 4))
        val inflater = java.util.zip.Inflater()
        inflater.setInput(idat.toByteArray())
        val inflated = inflater.inflate(raw)
        inflater.end()
        assertEquals("PNG inflated size for ${file.name}", raw.size, inflated)
        val px = ByteArray(width * height * 4)
        val bpp = 4
        var src = 0
        for (y in 0 until height) {
            val filter = raw[src].toInt() and 0xFF
            val rowStart = src + 1
            val dstRow = y * width * 4
            for (x in 0 until width * 4) {
                val rawVal = raw[rowStart + x].toInt() and 0xFF
                val a = if (x >= bpp) px[dstRow + x - bpp].toInt() and 0xFF else 0
                val bb = if (y > 0) px[dstRow - width * 4 + x].toInt() and 0xFF else 0
                val c = if (x >= bpp && y > 0) px[dstRow - width * 4 + x - bpp].toInt() and 0xFF else 0
                val v = when (filter) {
                    0 -> rawVal
                    1 -> (rawVal + a) and 0xFF
                    2 -> (rawVal + bb) and 0xFF
                    3 -> (rawVal + (a + bb) / 2) and 0xFF
                    4 -> {
                        val p = a + bb - c
                        val pa = kotlin.math.abs(p - a)
                        val pb = kotlin.math.abs(p - bb)
                        val pc = kotlin.math.abs(p - c)
                        val pr = if (pa <= pb && pa <= pc) a else if (pb <= pc) bb else c
                        (rawVal + pr) and 0xFF
                    }
                    else -> throw AssertionError("Unknown PNG filter $filter in ${file.name}")
                }
                px[dstRow + x] = v.toByte()
            }
            src = rowStart + width * 4
        }
        return Png(width, height, colourType, px)
    }

    @Test
    fun launcherIconIsLimeWithBlackMark() {
        // Flint (20 Nov voice call): the old white background with a
        // black logo was boring. Chosen: lime #C8F542 background with
        // the black P from the real logo v3 (not redrawn, not AI).
        val bg = readSource("res/drawable/ic_launcher_background.xml")
        assertTrue("Launcher background must be lime #C8F542", bg.contains("#C8F542"))
        assertFalse("Launcher background must not still be white", bg.contains("#FFFFFF"))
        val adaptive = readSource("res/mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue(adaptive.contains("@drawable/ic_launcher_background"))
        assertTrue(adaptive.contains("@mipmap/ic_launcher_foreground"))
        val round = readSource("res/mipmap-anydpi-v26/ic_launcher_round.xml")
        assertTrue(round.contains("@drawable/ic_launcher_background"))
        assertTrue(round.contains("@mipmap/ic_launcher_foreground"))
    }

    @Test
    fun launcherIconPngsExistAtTheRightSizes() {
        // Adaptive foreground: 108dp canvas (mdpi 108px .. xxxhdpi 432px).
        // Legacy icons: 48dp (mdpi 48px .. xxxhdpi 192px).
        val sizes = mapOf(
            "mdpi" to (108 to 48), "hdpi" to (162 to 72), "xhdpi" to (216 to 96),
            "xxhdpi" to (324 to 144), "xxxhdpi" to (432 to 192)
        )
        for ((density, want) in sizes) {
            val (fgWant, lgWant) = want
            val fg = readPng(resFile("mipmap-$density/ic_launcher_foreground.png"))
            assertEquals("Foreground $density must be ${fgWant}px wide", fgWant, fg.width)
            assertEquals("Foreground $density must be ${fgWant}px tall", fgWant, fg.height)
            val lg = readPng(resFile("mipmap-$density/ic_launcher.png"))
            assertEquals("Legacy icon $density must be ${lgWant}px wide", lgWant, lg.width)
            assertEquals("Legacy icon $density must be ${lgWant}px tall", lgWant, lg.height)
            val rd = readPng(resFile("mipmap-$density/ic_launcher_round.png"))
            assertEquals("Round icon $density must be ${lgWant}px wide", lgWant, rd.width)
            assertEquals("Round icon $density must be ${lgWant}px tall", lgWant, rd.height)
        }
        // The foreground must carry real transparency: the black P
        // floats on the lime background layer instead of a white box.
        val fg = readPng(resFile("mipmap-xxxhdpi/ic_launcher_foreground.png"))
        assertEquals("Foreground must be RGBA (colour type 6)", 6, fg.colourType)
        val px = fg.rgba!!
        var transparent = 0
        var opaque = 0
        var i = 3
        while (i < px.size) {
            val a = px[i].toInt() and 0xFF
            if (a < 128) transparent++ else opaque++
            i += 4
        }
        assertTrue("Foreground must have transparent pixels around the mark", transparent > 0)
        assertTrue("Foreground must have opaque pixels in the mark", opaque > 0)
    }

    // ---- 7. Live prices: fast ticker, live-ticking detail screen ----

    @Test
    fun tickerTicksFasterThanMarkets() {
        assertEquals(30_000L, MARKETS_CACHE_TTL_MS)
        assertEquals(10_000L, TICKER_CACHE_TTL_MS)
    }

    @Test
    fun detailScreenPollsLiveWhileOpen() {
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue("Detail polling must exist", vm.contains("fun startDetailPolling("))
        assertTrue("Detail polling must stop when the screen closes", vm.contains("fun stopDetailPolling()"))
        assertTrue("Detail polling must tick every 5 seconds", vm.contains("delay(5_000)"))
        assertTrue("Detail ticks must not overlap", vm.contains("detailMutex.tryLock()"))
        assertTrue("Detail ticks must refresh the chart", vm.contains("repo.loadChart(id)"))
        assertTrue("Detail ticks must refresh both books", vm.contains("repo.loadBook(listed.outcomes[0].tokenId)") && vm.contains("repo.loadBook(listed.outcomes[1].tokenId)"))
        assertTrue("Detail ticks must refresh the market price", vm.contains("repo.loadMarket(id)"))
        val main = readSource("java/xyz/fruitslice/prognos/MainActivity.kt")
        assertTrue("Detail screen must start polling on open", main.contains("vm.startDetailPolling(id)"))
        assertTrue("Detail screen must stop polling on close", main.contains("onDispose { vm.stopDetailPolling() }"))
    }

    @Test
    fun restoreDoesNotPreRefreshTheToken() {
        val repo = readSource("java/xyz/fruitslice/prognos/data/Repository.kt")
        assertFalse(
            "restore must try me() first instead of refreshing on age: up to 4 sequential auth calls",
            repo.contains("TOKEN_REFRESH_AFTER_MS")
        )
    }
}
