package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.ForecastContext
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.cleanRecognizedText
import xyz.prognoslabs.app.data.dashboardShowsSpinner
import xyz.prognoslabs.app.data.forecastFor
import xyz.prognoslabs.app.data.isEnrollComplete
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.model.BookEntry
import xyz.prognoslabs.app.model.BookResponse
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto
import xyz.prognoslabs.app.data.TradeSide
import xyz.prognoslabs.app.data.VoiceOrder
import xyz.prognoslabs.app.ui.SettingsRowId
import xyz.prognoslabs.app.ui.VoiceEngine
import xyz.prognoslabs.app.ui.VoiceSheet
import xyz.prognoslabs.app.ui.settingsDropDownRows

/**
 * v1.1.14: the video-review fixes, from Flint's screen recording of
 * the app. One test per fix, all pure and on the JVM: the rendering
 * itself is covered by the Robolectric voice UI tests in the same
 * module (settings rows, enrollment screen, execution sheet).
 */
class V1114Test {

    @Test
    fun versionIs1120() {
        assertEquals(29, BuildConfig.VERSION_CODE)
        assertEquals("1.1.20", BuildConfig.VERSION_NAME)
    }

    // ---- Fix 1: settings are compact drop-down rows ----

    @Test
    fun settingsHasFourDropDownRowsInOrder() {
        val rows = settingsDropDownRows()
        assertEquals(
            listOf(
                SettingsRowId.WALLET,
                SettingsRowId.CHANGE_PASSWORD,
                SettingsRowId.CURRENCY,
                SettingsRowId.TRADE_SIZE
            ),
            rows
        )
        assertEquals(
            listOf("Link wallet", "Change password", "Currency", "Default trade size"),
            rows.map { it.title }
        )
    }

    // ---- Fix 2: enrollment stops listening by itself ----

    @Test
    fun captureStopsAfterSilenceFollowingSpeech() {
        // Speech, then quiet for 1,500 ms: the capture ends on its
        // own, so enrollment never waits on a Stop tap.
        assertTrue(VoiceEngine.shouldStopCapture(true, 1_500L))
        assertTrue(VoiceEngine.shouldStopCapture(true, 5_000L))
        // A short pause does not end it: the user may still be talking.
        assertFalse(VoiceEngine.shouldStopCapture(true, 1_499L))
        assertFalse(VoiceEngine.shouldStopCapture(true, 400L))
        // Silence before any speech never ends it: the user may not
        // have started yet.
        assertFalse(VoiceEngine.shouldStopCapture(false, 10_000L))
    }

    @Test
    fun staleCaptureSessionStandsDown() {
        // A capture queued behind a newer one must exit without
        // touching the microphone: the newer pass owns it. This is
        // how an enrollment phrase could go unheard when the user
        // tapped through the phrases quickly.
        assertTrue(VoiceEngine.isStaleCaptureSession(1, 2))
        assertTrue(VoiceEngine.isStaleCaptureSession(2, 3))
        assertFalse(VoiceEngine.isStaleCaptureSession(2, 2))
    }

    // ---- Fix 3: voice questions keep the session open ----

    @Test
    fun everyQuestionKeepsTheSessionOpen() {
        val questions: List<VoiceQuestion> = listOf(
            VoiceQuestion.Price("BTC"),
            VoiceQuestion.Balance,
            VoiceQuestion.HowToTrade,
            VoiceQuestion.TradingOpens,
            VoiceQuestion.MarketInfo("btc-90k"),
            VoiceQuestion.GreetingHello,
            VoiceQuestion.GreetingHowAreYou,
            VoiceQuestion.MostLiquid,
            VoiceQuestion.Knowledge("Prognos Labs is a prediction market."),
            VoiceQuestion.News("BTC")
        )
        for (q in questions) {
            assertTrue("${q::class.simpleName} must keep the session open", q.isConversational)
        }
    }

    @Test
    fun tellMeAboutPrognosStaysOpenAndAnswers() {
        // The video: "Tell me about Prognos Labs" answered and the
        // session closed on its own, so the deposit question after
        // it was never heard. It must answer and keep listening.
        val parsed = parseVoiceUtterance("tell me about prognos labs", emptyList())
        assertTrue(parsed is VoiceParseResult.Question)
        val question = (parsed as VoiceParseResult.Question).question
        assertTrue(question is VoiceQuestion.Knowledge)
        assertTrue(question.isConversational)
    }

    @Test
    fun depositQuestionStaysOpenAndAnswers() {
        val parsed = parseVoiceUtterance("how do i deposit", emptyList())
        assertTrue(parsed is VoiceParseResult.Question)
        val question = (parsed as VoiceParseResult.Question).question
        assertTrue(question is VoiceQuestion.Knowledge)
        assertTrue(question.isConversational)
    }

    // ---- Fix 4: order execution is visible inside the sheet ----

    @Test
    fun executionSheetStatesCarryTheOrder() {
        val order = VoiceOrder(
            marketId = "btc-90k",
            marketLabel = "BTC close above \$90k",
            side = TradeSide.UP,
            amountUsd = 50.0,
            readback = "Buy 50 Yes on BTC close above \$90k"
        )
        val executing = VoiceSheet.Executing(order)
        assertEquals(order, executing.order)
        val executed = VoiceSheet.Executed(order, true, "Order placed.")
        assertEquals(order, executed.order)
        assertTrue(executed.success)
        assertEquals("Order placed.", executed.message)
        val failed = VoiceSheet.Executed(order, false, "Order could not be placed. Try again.")
        assertFalse(failed.success)
    }

    // ---- Fix 5: silence is never shown as speech ----

    @Test
    fun blankAudioIsSilenceNotSpeech() {
        // The library's marker for a silent stretch.
        assertNull(cleanRecognizedText("[BLANK_AUDIO]"))
        assertNull(cleanRecognizedText("[blank audio]"))
        // The same words as plain text, as the built-in recognizer
        // can return them.
        assertNull(cleanRecognizedText("blank audio"))
        assertNull(cleanRecognizedText("Blank Audio"))
        assertNull(cleanRecognizedText("blank_audio"))
        // Other non-speech markers.
        assertNull(cleanRecognizedText("[SILENCE]"))
        assertNull(cleanRecognizedText("[MUSIC]"))
        assertNull(cleanRecognizedText("[NOISE]"))
        // Nothing at all.
        assertNull(cleanRecognizedText(""))
        assertNull(cleanRecognizedText("   "))
    }

    @Test
    fun realSpeechSurvivesCleaning() {
        assertEquals("buy 10 bitcoin up", cleanRecognizedText("buy 10 bitcoin up"))
        // A marker mixed into real speech is dropped, the words stay.
        assertEquals("buy 10 bitcoin up", cleanRecognizedText("buy 10 bitcoin up [BLANK_AUDIO]"))
        assertEquals("hello", cleanRecognizedText("[MUSIC] hello"))
        assertEquals("buy 50 yes", cleanRecognizedText("  buy   50  yes  "))
    }

    // ---- Fix 6: unlocked only at 3 of 3 ----

    @Test
    fun voiceUnlockedOnlyAfterThreeOfThree() {
        assertFalse(isEnrollComplete(listOf(false, false, false)))
        assertFalse(isEnrollComplete(listOf(true, false, false)))
        assertFalse(isEnrollComplete(listOf(true, true, false)))
        assertTrue(isEnrollComplete(listOf(true, true, true)))
        // Wrong shape is never complete.
        assertFalse(isEnrollComplete(emptyList()))
        assertFalse(isEnrollComplete(listOf(true, true)))
        assertFalse(isEnrollComplete(listOf(true, true, true, true)))
    }

    // ---- Fix 7: top movers show at once ----

    @Test
    fun dashboardSpinnerOnlyWhenNoDataAtAll() {
        // No data anywhere: the spinner is honest.
        assertTrue(dashboardShowsSpinner(true, true, true))
        // The ticker is in: the movers show while the markets list
        // still loads behind them.
        assertFalse(dashboardShowsSpinner(true, true, false))
        // Markets in, ticker out: the screen shows what it has.
        assertFalse(dashboardShowsSpinner(true, false, true))
        // Not loading: never a spinner.
        assertFalse(dashboardShowsSpinner(false, true, true))
        assertFalse(dashboardShowsSpinner(false, false, false))
    }

    // ---- Fix 8: the forecast reads the price the page shows ----

    private fun marketNoPrice(): Market = Market(
        id = "eth-x", question = "Will ETH close above \$3k?", status = "open",
        closed = false, start = 0, end = 0, volume = null, vol24 = null,
        last = null, bid = null, ask = null, p0 = null, p1 = null,
        outcomes = listOf(Outcome("Yes", "tok-up"), Outcome("No", "tok-down")),
        tags = listOf(TagDto(slug = "eth")), desc = "", winOutcome = null
    )

    @Test
    fun forecastUsesChartPriceWhenMarketHasNone() {
        // The video: the Ethereum page showed the price and the chart,
        // while the forecast box said the market has no price. The
        // chart's last point is the same live price the page shows.
        val ctx = ForecastContext(
            chartPoints = listOf(listOf(1000.0, 0.50), listOf(2000.0, 0.62))
        )
        val forecast = forecastFor(marketNoPrice(), 3000L, ctx)
        assertNotNull(forecast)
        assertEquals(0.62, forecast!!.p, 0.0)
    }

    @Test
    fun forecastUsesBookMidWhenNeitherMarketNorChartHasAPrice() {
        val ctx = ForecastContext(
            bookUp = BookResponse(bids = listOf(BookEntry("0.48", "100")), asks = emptyList()),
            bookDown = BookResponse(bids = emptyList(), asks = listOf(BookEntry("0.52", "100")))
        )
        val forecast = forecastFor(marketNoPrice(), 3000L, ctx)
        assertNotNull(forecast)
        assertEquals(0.50, forecast!!.p, 0.0)
    }

    @Test
    fun forecastIsNullOnlyWhenNothingHasAPrice() {
        assertNull(forecastFor(marketNoPrice(), 3000L, null))
        assertNull(forecastFor(marketNoPrice(), 3000L, ForecastContext()))
    }

    @Test
    fun forecastStillPrefersTheMarketPrice() {
        val withPrice = marketNoPrice().copy(p0 = 0.45, p1 = 0.55)
        val ctx = ForecastContext(
            chartPoints = listOf(listOf(1000.0, 0.50), listOf(2000.0, 0.62))
        )
        val forecast = forecastFor(withPrice, 3000L, ctx)
        assertNotNull(forecast)
        assertEquals(0.45, forecast!!.p, 0.0)
    }

    // ---- Fix 3 (follow-on): a failed restart must not hide the answer ----

    @Test
    fun failedRestartKeepsTheAnswerOnScreen() {
        // The follow-up listen after an answer can fail (no mic,
        // recognizer unavailable). The answer must stay on screen:
        // the user reads it and taps the mic again. Hiding it
        // behind an error would throw away what they just asked for.
        val root = findAppFile()
        val vm = File(root, "src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt").readText()
        assertTrue(vm.contains("if (_voiceSheet.value is VoiceSheet.Conversation)"))
        assertTrue(vm.contains("if (_voiceSheet.value !is VoiceSheet.Conversation)"))
    }

    // ---- Standing rules ----

    @Test
    fun noAiToolNamesInEditedCode() {
        // The standing rule: no AI-tool names in shipped code,
        // comments, READMEs or commit messages. Every file this
        // build touched is scanned here.
        val banned = listOf("Muse", "Claude", "ChatGPT", "OpenAI")
        val root = findAppFile()
        val files = listOf(
            "src/main/java/xyz/prognoslabs/app/data/VoiceText.kt",
            "src/main/java/xyz/prognoslabs/app/data/VoiceOrder.kt",
            "src/main/java/xyz/prognoslabs/app/data/VoiceProfile.kt",
            "src/main/java/xyz/prognoslabs/app/data/Forecast.kt",
            "src/main/java/xyz/prognoslabs/app/data/MarketList.kt",
            "src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt",
            "src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt",
            "src/main/java/xyz/prognoslabs/app/ui/AppViewModel.kt",
            "src/main/java/xyz/prognoslabs/app/ui/Screens.kt",
            "src/main/java/xyz/prognoslabs/app/ui/Design.kt",
            "src/main/java/xyz/prognoslabs/app/MainActivity.kt",
            "src/main/java/xyz/prognoslabs/app/voice/WhisperRecognizer.kt",
            "build.gradle.kts"
        )
        for (path in files) {
            val file = File(root, path)
            assertTrue("missing: ${file.path}", file.exists())
            val text = file.readText()
            for (word in banned) {
                assertFalse("${file.name} contains $word", text.contains(word))
            }
        }
    }

    private fun findAppFile(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "app")
            if (File(candidate, "build.gradle.kts").exists()) return candidate
            dir = dir.parentFile
        }
        val direct = File("build.gradle.kts")
        if (direct.exists()) return File(".")
        throw AssertionError("app module not found from ${System.getProperty("user.dir")}")
    }
}
