package xyz.fruitslice.prognos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fruitslice.prognos.data.VoiceCheck
import xyz.fruitslice.prognos.data.VoiceHistoryEntry
import xyz.fruitslice.prognos.data.VoiceHistoryOutcome
import xyz.fruitslice.prognos.data.VoiceParseResult
import xyz.fruitslice.prognos.data.VoiceQuestion
import xyz.fruitslice.prognos.data.addVoiceHistory
import xyz.fruitslice.prognos.data.buildVoiceProfile
import xyz.fruitslice.prognos.data.extractVoiceFeatures
import xyz.fruitslice.prognos.data.formatVoiceAmount
import xyz.fruitslice.prognos.data.isLinkedReturnUrl
import xyz.fruitslice.prognos.data.label
import xyz.fruitslice.prognos.data.parseVoiceUtterance
import xyz.fruitslice.prognos.data.registrationCompleteNotice
import xyz.fruitslice.prognos.data.spokenNumbers
import xyz.fruitslice.prognos.data.voiceCheckDecision
import xyz.fruitslice.prognos.data.voiceHistoryFromJson
import xyz.fruitslice.prognos.data.voiceHistoryToJson
import xyz.fruitslice.prognos.data.voiceMatches
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.Outcome
import xyz.fruitslice.prognos.model.TagDto
import xyz.fruitslice.prognos.ui.validChartPoints
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * v1.1.6: voice trading (tap-to-talk, enrollment + voice lock,
 * read-back confirmation, voice history) and the full bug sweep:
 * chart crash, double-tap orders, signup-verify notice, wallet-link
 * return URL. Pure JVM tests, no Android.
 */
class V116Test {

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

    /** Text of one function, from its declaration to the next sibling. */
    private fun funBody(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        assertTrue("Cannot find $declaration", start >= 0)
        val after = start + declaration.length
        val next = source.indexOf("\n    fun ", after)
        val nextSuspend = source.indexOf("\n    suspend fun ", after)
        val nextSection = source.indexOf("\n    // ----", after)
        val end = listOf(next, nextSuspend, nextSection).filter { it >= 0 }.minOrNull() ?: source.length
        return source.substring(start, end)
    }

    private fun testMarket(id: String, question: String, slug: String): Market =
        Market(
            id = id, question = question, status = "open", closed = false,
            start = 0, end = 0, volume = null, vol24 = null, last = null,
            bid = null, ask = null, p0 = 0.5, p1 = 0.5,
            outcomes = listOf(Outcome("Yes", "tok-up-$id"), Outcome("No", "tok-down-$id")),
            tags = listOf(TagDto(slug = slug)), desc = "", winOutcome = null
        )

    private val btc90k = testMarket("btc-90k", "Will BTC close above \$90k?", "btc")
    private val btc80k = testMarket("btc-80k", "Will BTC close above \$80k?", "btc")
    private val eth3k = testMarket("eth-3k", "Will ETH close below \$3,000?", "eth")
    private val sol200 = testMarket("sol-200", "Will SOL close above \$200?", "sol")
    private val markets = listOf(btc90k, eth3k, sol200)

    /** A pure sine wave, 1 second, 16 kHz, amplitude 0.5. */
    private fun sine(freqHz: Double, seconds: Double = 1.0, sampleRate: Int = 16_000): ShortArray {
        val n = (seconds * sampleRate).toInt()
        return ShortArray(n) { i ->
            (0.5 * 32767 * sin(2 * PI * freqHz * i / sampleRate)).toInt().toShort()
        }
    }

    // ---- 1. Order parsing: the spoken sentence becomes a trade ----

    @Test
    fun buyFiftyYesOnBtcAbove90k() {
        val r = parseVoiceUtterance("buy 50 Yes on BTC above 90k", markets)
        assertTrue("Expected an Order, got $r", r is VoiceParseResult.Order)
        val order = (r as VoiceParseResult.Order).order
        assertEquals("btc-90k", order.marketId)
        assertEquals(xyz.fruitslice.prognos.data.TradeSide.UP, order.side)
        assertEquals(50.0, order.amountUsd, 0.0)
        assertEquals("Buy 50 Yes on BTC close above \$90k", order.readback)
    }

    @Test
    fun sellTwentyNoOnEth() {
        val r = parseVoiceUtterance("sell 20 no on eth", markets)
        assertTrue("Expected an Order, got $r", r is VoiceParseResult.Order)
        val order = (r as VoiceParseResult.Order).order
        assertEquals("eth-3k", order.marketId)
        assertEquals(xyz.fruitslice.prognos.data.TradeSide.DOWN, order.side)
        assertEquals(20.0, order.amountUsd, 0.0)
        assertEquals("Sell 20 No on ETH close below \$3,000", order.readback)
    }

    @Test
    fun wordNumbersAreUnderstood() {
        val fiftyFive = parseVoiceUtterance("buy fifty five yes on sol", markets)
        assertTrue(fiftyFive is VoiceParseResult.Order)
        assertEquals(55.0, (fiftyFive as VoiceParseResult.Order).order.amountUsd, 0.0)

        val hundred = parseVoiceUtterance("buy one hundred yes on btc above 90k", markets)
        assertTrue(hundred is VoiceParseResult.Order)
        assertEquals(100.0, (hundred as VoiceParseResult.Order).order.amountUsd, 0.0)

        val twoThousand = parseVoiceUtterance("buy two thousand yes on btc above 90k", markets)
        assertTrue(twoThousand is VoiceParseResult.Order)
        assertEquals(2000.0, (twoThousand as VoiceParseResult.Order).order.amountUsd, 0.0)
    }

    @Test
    fun marketPriceIsNotTheAmount() {
        // "90k" belongs to the market, not the amount: the amount is
        // the number before the coin is named.
        val r = parseVoiceUtterance("buy 10 yes on btc above 90k", markets)
        assertTrue(r is VoiceParseResult.Order)
        assertEquals(10.0, (r as VoiceParseResult.Order).order.amountUsd, 0.0)
        assertEquals("btc-90k", r.order.marketId)
    }

    @Test
    fun missingAmountAsksAgainNeverGuesses() {
        val r = parseVoiceUtterance("buy yes on btc", markets)
        assertTrue("A missing amount must ask again, got $r", r is VoiceParseResult.AskAgain)
    }

    @Test
    fun missingMarketAsksAgainNeverGuesses() {
        val r = parseVoiceUtterance("buy 50 yes", markets)
        assertTrue("A missing market must ask again, got $r", r is VoiceParseResult.AskAgain)
    }

    @Test
    fun openMarketIsTheContextWhenNoCoinIsNamed() {
        // On the BTC detail screen, "buy 50 yes" means that market.
        val r = parseVoiceUtterance("buy 50 yes", markets, currentMarketId = "btc-90k")
        assertTrue(r is VoiceParseResult.Order)
        assertEquals("btc-90k", (r as VoiceParseResult.Order).order.marketId)
    }

    @Test
    fun ambiguousMarketAsksAgainNeverGuesses() {
        val twoBtc = listOf(btc90k, btc80k, eth3k)
        val r = parseVoiceUtterance("buy 50 yes on btc", twoBtc)
        assertTrue("Two BTC markets and no price must ask again, got $r", r is VoiceParseResult.AskAgain)
    }

    @Test
    fun priceDisambiguatesTheMarket() {
        val twoBtc = listOf(btc90k, btc80k, eth3k)
        val r = parseVoiceUtterance("buy 50 yes on btc above 80k", twoBtc)
        assertTrue(r is VoiceParseResult.Order)
        assertEquals("btc-80k", (r as VoiceParseResult.Order).order.marketId)
    }

    @Test
    fun spokenNumbersFindDigitsAndWords() {
        val nums = spokenNumbers("buy fifty five yes on btc above ninety thousand")
        assertEquals(2, nums.size)
        assertEquals(55.0, nums[0].value, 0.0)
        assertEquals(90000.0, nums[1].value, 0.0)
    }

    @Test
    fun formatVoiceAmountHasNoTrailingZero() {
        assertEquals("50", formatVoiceAmount(50.0))
        assertEquals("50.5", formatVoiceAmount(50.5))
        assertEquals("0.25", formatVoiceAmount(0.25))
    }

    // ---- 2. Questions and Prognos-only scope ----

    @Test
    fun priceQuestionIsParsed() {
        val r = parseVoiceUtterance("what is the bitcoin price", markets)
        assertTrue(r is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.Price("BTC"), (r as VoiceParseResult.Question).question)
    }

    @Test
    fun balanceQuestionIsParsed() {
        val r = parseVoiceUtterance("what is my balance", markets)
        assertTrue(r is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.Balance, (r as VoiceParseResult.Question).question)
    }

    @Test
    fun howToTradeQuestionIsParsed() {
        val r = parseVoiceUtterance("how do i buy", markets)
        assertTrue(r is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.HowToTrade, (r as VoiceParseResult.Question).question)
    }

    @Test
    fun tradingOpensQuestionIsParsed() {
        val r = parseVoiceUtterance("when does trading open", markets)
        assertTrue(r is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.TradingOpens, (r as VoiceParseResult.Question).question)
    }

    @Test
    fun outOfScopeIsNotAnswered() {
        val r = parseVoiceUtterance("what is the weather tomorrow", markets)
        assertTrue("Non-Prognos questions must be declined, got $r", r is VoiceParseResult.OutOfScope)
        assertEquals(
            "I only answer questions about Prognos Labs trading.",
            (r as VoiceParseResult.OutOfScope).message
        )
    }

    // ---- 3. Voice profile and lock ----

    @Test
    fun sineWaveHasItsPitch() {
        val f120 = extractVoiceFeatures(sine(120.0), 16_000, wordsSpoken = 5)
        assertNotNull("A clear tone must give features", f120)
        assertTrue("120 Hz tone measured as ${f120!!.meanF0Hz}", f120.meanF0Hz in 100.0..140.0)
        val f200 = extractVoiceFeatures(sine(200.0), 16_000, wordsSpoken = 5)
        assertNotNull(f200)
        assertTrue("200 Hz tone measured as ${f200!!.meanF0Hz}", f200.meanF0Hz in 180.0..220.0)
    }

    @Test
    fun silenceGivesNoFeatures() {
        assertNull(extractVoiceFeatures(ShortArray(16_000), 16_000, wordsSpoken = 5))
        assertNull(extractVoiceFeatures(ShortArray(100), 16_000, wordsSpoken = 5))
    }

    @Test
    fun profileNeedsThreeSamples() {
        val one = extractVoiceFeatures(sine(120.0), 16_000, wordsSpoken = 5)!!
        assertNull("Two samples are not an enrollment", buildVoiceProfile(listOf(one, one)))
        assertNotNull(buildVoiceProfile(listOf(one, one, one)))
    }

    @Test
    fun sameVoiceMatchesDifferentVoiceDoesNot() {
        val samples = List(3) { extractVoiceFeatures(sine(120.0), 16_000, wordsSpoken = 5)!! }
        val profile = buildVoiceProfile(samples)!!
        val same = extractVoiceFeatures(sine(125.0), 16_000, wordsSpoken = 5)!!
        val other = extractVoiceFeatures(sine(200.0), 16_000, wordsSpoken = 5)!!
        assertTrue("The same voice must match", voiceMatches(profile, same))
        assertFalse("A different voice must not match", voiceMatches(profile, other))
    }

    @Test
    fun voiceCheckIsUnavailableWithoutProfileOrAudio() {
        val features = extractVoiceFeatures(sine(120.0), 16_000, wordsSpoken = 5)!!
        val profile = buildVoiceProfile(List(3) { features })!!
        assertEquals(VoiceCheck.UNAVAILABLE, voiceCheckDecision(null, features))
        assertEquals(VoiceCheck.UNAVAILABLE, voiceCheckDecision(profile, null))
        assertEquals(VoiceCheck.MATCHED, voiceCheckDecision(profile, features))
        val other = extractVoiceFeatures(sine(200.0), 16_000, wordsSpoken = 5)!!
        assertEquals(VoiceCheck.NOT_MATCHED, voiceCheckDecision(profile, other))
    }

    // ---- 4. Voice history ----

    @Test
    fun historyJsonRoundTrip() {
        val entries = listOf(
            VoiceHistoryEntry("Buy 50 Yes on BTC close above \$90k", VoiceHistoryOutcome.TRADED, 1000L),
            VoiceHistoryEntry("What is my balance", VoiceHistoryOutcome.ANSWERED, 2000L)
        )
        val restored = voiceHistoryFromJson(voiceHistoryToJson(entries))
        assertEquals(entries, restored)
    }

    @Test
    fun historyCapsAtFiftyNewestFirst() {
        var entries = emptyList<VoiceHistoryEntry>()
        for (i in 1..60) {
            entries = addVoiceHistory(entries, VoiceHistoryEntry("order $i", VoiceHistoryOutcome.TRADED, i.toLong()))
        }
        assertEquals(50, entries.size)
        assertEquals("order 60", entries.first().text)
        assertEquals("order 11", entries.last().text)
    }

    @Test
    fun historyBadJsonIsEmpty() {
        assertEquals(emptyList<VoiceHistoryEntry>(), voiceHistoryFromJson("not json"))
        assertEquals(emptyList<VoiceHistoryEntry>(), voiceHistoryFromJson(null))
    }

    @Test
    fun historyOutcomeLabelsArePlain() {
        assertEquals("Traded", VoiceHistoryOutcome.TRADED.label())
        assertEquals("Answered", VoiceHistoryOutcome.ANSWERED.label())
        assertEquals("Cancelled", VoiceHistoryOutcome.CANCELLED.label())
    }

    // ---- 5. Bug sweep regressions ----

    @Test
    fun malformedChartPointsAreDropped() {
        val points = listOf(
            listOf(1.0),
            listOf(2.0, Double.NaN),
            listOf(3.0, Double.POSITIVE_INFINITY),
            listOf(4.0, 0.5),
            listOf(5.0, 0.6)
        )
        assertEquals(
            listOf(listOf(4.0, 0.5), listOf(5.0, 0.6)),
            validChartPoints(points)
        )
    }

    @Test
    fun validChartPointsKeepGoodPoints() {
        val points = listOf(listOf(1.0, 0.5), listOf(2.0, 0.6))
        assertEquals(points, validChartPoints(points))
    }

    @Test
    fun linkedReturnUrlRejectsSuffixedPaths() {
        assertTrue(isLinkedReturnUrl("https://prognoslabs.xyz/linked?session=abc&address=0x1"))
        assertTrue(isLinkedReturnUrl("https://prognoslabs.xyz/linked"))
        assertTrue(isLinkedReturnUrl("prognoslabs://linked?session=abc"))
        assertFalse("linkedXYZ is not the return page", isLinkedReturnUrl("https://prognoslabs.xyz/linkedXYZ"))
        assertFalse("linked.html is not the return page", isLinkedReturnUrl("https://prognoslabs.xyz/linked.html"))
        assertFalse(isLinkedReturnUrl(null))
        assertFalse(isLinkedReturnUrl(""))
    }

    @Test
    fun registrationCompleteNoticeDependsOnThePassword() {
        assertEquals(
            "Registration complete. We sent a sign-in code to a@b.com.",
            registrationCompleteNotice("a@b.com", passwordPresent = true)
        )
        assertEquals(
            "Registration complete. Log in to get your sign-in code.",
            registrationCompleteNotice("a@b.com", passwordPresent = false)
        )
    }

    @Test
    fun placeOrderHasADoubleTapGuardFirst() {
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        val body = funBody(vm, "fun placeOrder(")
        assertTrue("placeOrder must guard on the busy flag", body.contains("if (_tradeBusy.value) return"))
        assertTrue(
            "The guard must come before the order launches",
            body.indexOf("if (_tradeBusy.value) return") < body.indexOf("viewModelScope.launch")
        )
    }

    @Test
    fun authCallsHaveDoubleTapGuards() {
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        for (fn in listOf("fun signUp(", "fun logIn(", "fun verifyOtp(", "fun resendOtp(", "fun requestPasswordReset(")) {
            val body = funBody(vm, fn)
            assertTrue("$fn must guard on the auth busy flag", body.contains("if (_authBusy.value) return"))
            assertTrue(
                "$fn guard must come before the call launches",
                body.indexOf("if (_authBusy.value) return") < body.indexOf("viewModelScope.launch")
            )
        }
    }

    @Test
    fun verifyOtpNoticeUsesPasswordPresence() {
        val repo = readSource("java/xyz/fruitslice/prognos/data/Repository.kt")
        val body = funBody(repo, "suspend fun verifyOtp(")
        assertTrue(
            "verifyOtp must use the no-password notice when the password is gone",
            body.contains("registrationCompleteNotice(pending.email, passwordPresent = false)")
        )
        assertTrue(
            "verifyOtp must use the code-sent notice when the password is held",
            body.contains("registrationCompleteNotice(pending.email, passwordPresent = true)")
        )
        assertFalse(
            "verifyOtp must not set the code-sent notice before checking the password",
            body.contains("_notice.value = \"Registration complete. We sent a sign-in code")
        )
    }

    @Test
    fun chartDrawsOnlyValidPoints() {
        val chart = readSource("java/xyz/fruitslice/prognos/ui/Chart.kt")
        assertTrue("PriceChart must filter points through validChartPoints", chart.contains("validChartPoints(points)"))
        assertFalse("PriceChart must not read raw points[1]", chart.contains("points.map { it[1] }"))
    }

    @Test
    fun voiceConfirmGoesThroughTheSamePlaceOrder() {
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        val body = funBody(vm, "fun confirmVoiceOrder(")
        assertTrue("Voice confirm must call placeOrder", body.contains("placeOrder("))
        assertFalse("Voice confirm must not call the trading repo directly", body.contains("tradingRepo.placeOrder"))
    }

    @Test
    fun voiceOrderNeedsAMatchedVoiceCheck() {
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        val body = funBody(vm, "private fun onVoiceOrder(")
        assertTrue("An unmatched voice must be refused", body.contains("VoiceCheck.NOT_MATCHED"))
        assertTrue("An unavailable check must be refused", body.contains("VoiceCheck.UNAVAILABLE"))
        assertTrue("Only a matched check reaches the confirm card", body.contains("VoiceSheet.Confirm"))
    }

    @Test
    fun dismissingWhileListeningDropsThePendingResult() {
        val vm = readSource("java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        val body = funBody(vm, "fun dismissVoiceSheet(")
        assertTrue(
            "Dismissing the listening sheet must stop the recognizer",
            body.contains("voiceEngine.stopListening()")
        )
        assertTrue(
            "Dismissing must invalidate the pending result so the sheet does not pop back",
            body.contains("voiceSession++")
        )
    }

    @Test
    fun micPermissionIsDeclared() {
        val manifest = readSource("AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
    }

    @Test
    fun versionIs118() {
        val gradle = readSource("../../build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 28"))
        assertTrue(gradle.contains("versionName = \"1.1.18\""))
    }

    @Test
    fun noAiToolNamesInVoiceCode() {
        val files = listOf(
            "java/xyz/fruitslice/prognos/data/VoiceOrder.kt",
            "java/xyz/fruitslice/prognos/data/VoiceProfile.kt",
            "java/xyz/fruitslice/prognos/data/VoiceHistory.kt",
            "java/xyz/fruitslice/prognos/data/VoiceStore.kt",
            "java/xyz/fruitslice/prognos/ui/VoiceEngine.kt",
            "java/xyz/fruitslice/prognos/ui/VoiceScreens.kt"
        )
        for (f in files) {
            val src = readSource(f)
            for (banned in listOf("Muse", "Claude", "ChatGPT", "OpenAI")) {
                assertFalse("$f must not mention $banned", src.contains(banned))
            }
        }
    }
}
