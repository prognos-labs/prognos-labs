package xyz.fruitslice.prognos

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import xyz.fruitslice.prognos.data.GREETING_HELLO_ANSWER
import xyz.fruitslice.prognos.data.GREETING_HOW_ARE_YOU_ANSWER
import xyz.fruitslice.prognos.data.VOICE_CHALLENGE_PHRASES
import xyz.fruitslice.prognos.data.VOICE_ENROLL_PHRASE_POOL
import xyz.fruitslice.prognos.data.VoiceHistoryEntry
import xyz.fruitslice.prognos.data.VoiceHistoryOutcome
import xyz.fruitslice.prognos.data.VoiceParseResult
import xyz.fruitslice.prognos.data.VoiceQuestion
import xyz.fruitslice.prognos.data.VoiceStore
import xyz.fruitslice.prognos.data.addVoiceHistory
import xyz.fruitslice.prognos.data.challengeMatches
import xyz.fruitslice.prognos.data.label
import xyz.fruitslice.prognos.data.looksTruncated
import xyz.fruitslice.prognos.data.parseBestVoiceUtterance
import xyz.fruitslice.prognos.data.parseVoiceUtterance
import xyz.fruitslice.prognos.data.pickChallengePhrase
import xyz.fruitslice.prognos.data.pickEnrollPhrases
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.Outcome
import xyz.fruitslice.prognos.model.TagDto

/**
 * v1.1.11: Flint tested v1.1.9 on his phone and the voice failed
 * five ways: it cut off mid-sentence and answered out of scope,
 * "hello" got a "how are you" reply, enrollment was the same three
 * phrases for everyone, a recording of those phrases could pass
 * the voice check, and voice could not do anything in the app.
 * Each fault gets its fix and its test here. Pure JVM tests except
 * the store test, which runs under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class V1111Test {

    private fun testMarket(id: String, question: String, slug: String, vol24: Any? = null): Market =
        Market(
            id = id, question = question, status = "open", closed = false,
            start = 0, end = 0, volume = null, vol24 = vol24, last = null,
            bid = null, ask = null, p0 = 0.5, p1 = 0.5,
            outcomes = listOf(Outcome("Yes", "tok-up-$id"), Outcome("No", "tok-down-$id")),
            tags = listOf(TagDto(slug = slug)), desc = "", winOutcome = null
        )

    private val btc90k = testMarket("btc-90k", "Will BTC close above \$90k?", "btc", vol24 = "1500000")
    private val btc80k = testMarket("btc-80k", "Will BTC close above \$80k?", "btc", vol24 = "800000")
    private val eth3k = testMarket("eth-3k", "Will ETH close below \$3,000?", "eth", vol24 = "400000")
    private val markets = listOf(btc90k, btc80k, eth3k)

    private fun mainRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return dir
    }

    private fun readSource(relative: String): String =
        File(mainRoot(), relative).readText()

    private fun appContext() =
        RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun versionIs116() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 26"))
        assertTrue(gradle.contains("versionName = \"1.1.16\""))
    }

    // ---- 1. Listening cuts off mid-sentence ----

    @Test
    fun engineWaitsBeforeFinalizing() {
        // v1.1.16: the built-in recognizer's silence extras are
        // gone with it. The speech library ends a segment after
        // 1,200 ms of silence once speech was heard, and never on
        // silence alone.
        val recognizer = readSource("app/src/main/java/xyz/fruitslice/prognos/voice/WhisperRecognizer.kt")
        assertTrue(recognizer.contains("SILENCE_TO_FINAL_MS"))
        assertTrue("Silence must run 1,200 ms before finalizing", recognizer.contains("1_200L"))
        assertTrue(
            "The finalize rule must need speech heard",
            recognizer.contains("speechHeard && silenceMs >= SILENCE_TO_FINAL_MS")
        )
        // Partials are still never acted on: only Final reaches the parser.
        // v1.1.14: a partial is cleaned first, so silence is never
        // shown as speech, then shown as the live words.
        val vm = readSource("app/src/main/java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue(vm.contains("cleanRecognizedText(event.text)"))
        assertTrue(vm.contains("_voiceListeningText.value = cleanRecognizedText(event.text)"))
        assertFalse("A partial must never reach processVoiceFinal", vm.contains("Partial -> processVoiceFinal"))
    }

    @Test
    fun truncatedFragmentAsksAgain() {
        assertTrue(looksTruncated("how are"))
        assertTrue(looksTruncated("hey how are"))
        assertTrue(looksTruncated("what is the"))
        for (fragment in listOf("how are", "hey how are", "what is the")) {
            val r = parseVoiceUtterance(fragment, markets)
            assertTrue("Fragment '$fragment' must ask again, got $r", r is VoiceParseResult.AskAgain)
        }
    }

    @Test
    fun completeQuestionStillRefused() {
        assertFalse(looksTruncated("what is the weather"))
        val r = parseVoiceUtterance("what is the weather", markets)
        assertTrue("A complete out-of-scope question keeps its refusal, got $r", r is VoiceParseResult.OutOfScope)
    }

    @Test
    fun fullGreetingStillParses() {
        val r = parseVoiceUtterance("hey how are you", markets)
        assertTrue(r is VoiceParseResult.Question)
        assertTrue(
            "Full greeting must parse as how-are-you, got ${(r as VoiceParseResult.Question).question}",
            (r as VoiceParseResult.Question).question is VoiceQuestion.GreetingHowAreYou
        )
    }

    // ---- 2. Greeting replies are wrong ----

    @Test
    fun helloGetsPlainHello() {
        for (word in listOf("hello", "hi", "hey", "hello there", "good morning")) {
            val r = parseVoiceUtterance(word, markets)
            assertTrue("'$word' must parse, got $r", r is VoiceParseResult.Question)
            val q = (r as VoiceParseResult.Question).question
            assertTrue("'$word' must be a plain hello, got $q", q is VoiceQuestion.GreetingHello)
            assertTrue("A hello keeps the session open", q.isConversational)
        }
    }

    @Test
    fun howAreYouGetsItsOwnAnswer() {
        for (phrase in listOf("how are you", "hey, how's it going?", "how's things")) {
            val r = parseVoiceUtterance(phrase, markets)
            assertTrue("'$phrase' must parse, got $r", r is VoiceParseResult.Question)
            val q = (r as VoiceParseResult.Question).question
            assertTrue("'$phrase' must be how-are-you, got $q", q is VoiceQuestion.GreetingHowAreYou)
            assertTrue("How-are-you keeps the session open", q.isConversational)
        }
    }

    @Test
    fun greetingAnswersAreDistinct() {
        assertEquals("Hi there. What would you like to do?", GREETING_HELLO_ANSWER)
        assertEquals("I'm doing well. What would you like to do?", GREETING_HOW_ARE_YOU_ANSWER)
        assertFalse("The hello answer must not contain 'how are you'", GREETING_HELLO_ANSWER.contains("how are you", ignoreCase = true))
        assertFalse("The hello answer must not claim to be fine", GREETING_HELLO_ANSWER.contains("I'm fine"))
        assertNotEquals(GREETING_HELLO_ANSWER, GREETING_HOW_ARE_YOU_ANSWER)
    }

    @Test
    fun viewModelAnswersGreetingsWithTheSplitAnswers() {
        val vm = readSource("app/src/main/java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue(vm.contains("VoiceQuestion.GreetingHello ->"))
        assertTrue(vm.contains("GREETING_HELLO_ANSWER"))
        assertTrue(vm.contains("VoiceQuestion.GreetingHowAreYou ->"))
        assertTrue(vm.contains("GREETING_HOW_ARE_YOU_ANSWER"))
        assertFalse("The old wrong greeting reply must be gone", vm.contains("I'm fine, thank you"))
    }

    // ---- 3. Enrollment is the same 3 phrases for everyone ----

    @Test
    fun enrollPoolHasAtLeastTwentyPhrases() {
        assertTrue("Pool must have at least 20 phrases", VOICE_ENROLL_PHRASE_POOL.size >= 20)
        assertEquals("Pool phrases must be distinct", VOICE_ENROLL_PHRASE_POOL.size, VOICE_ENROLL_PHRASE_POOL.distinct().size)
        assertTrue("Pool phrases must be real sentences", VOICE_ENROLL_PHRASE_POOL.all { it.split(" ").size >= 3 })
    }

    @Test
    fun pickerDealsDifferentHands() {
        val first = pickEnrollPhrases(VOICE_ENROLL_PHRASE_POOL, 3, Random(1))
        val second = pickEnrollPhrases(VOICE_ENROLL_PHRASE_POOL, 3, Random(2))
        val repeat = pickEnrollPhrases(VOICE_ENROLL_PHRASE_POOL, 3, Random(1))
        assertEquals(3, first.size)
        assertEquals("Picked phrases must be distinct", 3, first.distinct().size)
        assertTrue("Picked phrases must come from the pool", first.all { it in VOICE_ENROLL_PHRASE_POOL })
        assertEquals("The same seed must deal the same hand", first, repeat)
        assertNotEquals("Different seeds must deal different hands", first, second)
    }

    @Test
    fun enrollPhrasesAreStoredAndReloaded() {
        val store = VoiceStore(appContext())
        assertNull(store.loadEnrollPhrases())
        val assigned = listOf("blue river", "green apple", "morning light")
        store.saveEnrollPhrases(assigned)
        assertEquals(assigned, store.loadEnrollPhrases())
        store.clearEnrollPhrases()
        assertNull(store.loadEnrollPhrases())
    }

    @Test
    fun enrollmentUsesTheAssignedPhrases() {
        val vm = readSource("app/src/main/java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue(vm.contains("vocabFor(_voiceLanguage.value).enrollPhrases, 3"))
        assertTrue(vm.contains("saveEnrollPhrases"))
        assertTrue(vm.contains("loadEnrollPhrases"))
        assertTrue(vm.contains("clearEnrollPhrases"))
        assertTrue(vm.contains("_enrollState.value.phrases.indices"))
        assertFalse("The fixed phrase list must be gone", vm.contains("VOICE_ENROLL_PHRASES"))
    }

    // ---- 4. A stronger uniqueness check ----

    @Test
    fun challengePoolHasShortPhrases() {
        assertTrue("Challenge pool must have at least 20 phrases", VOICE_CHALLENGE_PHRASES.size >= 20)
        assertEquals("Challenge phrases must be distinct", VOICE_CHALLENGE_PHRASES.size, VOICE_CHALLENGE_PHRASES.distinct().size)
        assertTrue("Challenge phrases must be short", VOICE_CHALLENGE_PHRASES.all { it.split(" ").size in 2..3 })
    }

    @Test
    fun challengePickerVaries() {
        val picked = (0..9).map { pickChallengePhrase(VOICE_CHALLENGE_PHRASES, Random(it)) }
        assertTrue("Picked challenges must come from the pool", picked.all { it in VOICE_CHALLENGE_PHRASES })
        assertTrue("Different seeds must not all deal the same phrase", picked.distinct().size > 1)
    }

    @Test
    fun challengeMatchesExactAndWithExtraWords() {
        assertTrue(challengeMatches("blue river", "blue river"))
        assertTrue(challengeMatches("say blue river please", "blue river"))
        assertTrue(challengeMatches("Blue River", "blue river"))
        assertFalse(challengeMatches("blue ocean", "blue river"))
        assertFalse(challengeMatches("blue", "blue river"))
        assertFalse(challengeMatches("river blue", "blue river"))
        // A recording of the enrollment phrases cannot pass a challenge.
        assertFalse(
            challengeMatches("buy fifty yes on bitcoin above ninety thousand", "blue river")
        )
        assertFalse(
            challengeMatches("what is my balance", "blue river")
        )
    }

    @Test
    fun challengeFlowExistsInViewModel() {
        val vm = readSource("app/src/main/java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue(vm.contains("pickChallengePhrase("))
        assertTrue(vm.contains("vocabFor(_voiceLanguage.value).challengePhrases"))
        assertTrue(vm.contains("fun processChallengeFinal"))
        assertTrue(vm.contains("challengeMatches(it, challenge, _voiceLanguage.value)"))
        assertTrue(vm.contains("VoiceSheet.VoiceCheck(challenge, VoiceCheckPhase.WORDS)"))
        assertTrue(vm.contains("VoiceSheet.VoiceCheck(challenge, VoiceCheckPhase.VOICE)"))
        assertTrue(vm.contains("challengeInstructionFirst"))
        assertTrue(vm.contains("AnalyticsEvents.VOICE_CHALLENGE"))
        assertTrue("Two wrong tries refuse the order", vm.contains("voiceChallengeAttempts >= 2"))
    }

    @Test
    fun noLivenessOrCryptoClaims() {
        val roots = listOf(
            "app/src/main/java/xyz/fruitslice/prognos/data/VoiceProfile.kt",
            "app/src/main/java/xyz/fruitslice/prognos/ui/AppViewModel.kt",
            "app/src/main/java/xyz/fruitslice/prognos/ui/VoiceScreens.kt"
        )
        for (relative in roots) {
            val src = readSource(relative)
            assertFalse("$relative must never claim cryptographic gating", src.contains("cryptographically gated"))
            assertFalse("$relative must never claim perfect liveness", src.contains("perfect liveness"))
            assertFalse("$relative must never claim liveness is guaranteed", src.contains("liveness guaranteed"))
            assertFalse("$relative must never claim the check cannot be fooled", src.contains("impossible to fool"))
        }
        val profile = readSource("app/src/main/java/xyz/fruitslice/prognos/data/VoiceProfile.kt")
        assertTrue("The honest limit must stay in the code", profile.contains("not true"))
        assertTrue(profile.contains("liveness detection"))
    }

    // ---- 5. Voice feels disconnected from the app ----

    @Test
    fun openMarketNavigatesToMostLiquid() {
        val r = parseVoiceUtterance("open bitcoin", markets)
        assertTrue("Open bitcoin must navigate, got $r", r is VoiceParseResult.Navigate)
        val nav = r as VoiceParseResult.Navigate
        assertEquals("market/btc-90k", nav.route)
        val eth = parseVoiceUtterance("show me the ethereum market", markets)
        assertTrue("Show ethereum must navigate, got $eth", eth is VoiceParseResult.Navigate)
        assertEquals("market/eth-3k", (eth as VoiceParseResult.Navigate).route)
    }

    @Test
    fun showBalanceNavigates() {
        val r = parseVoiceUtterance("show my balance", markets)
        assertTrue("Show my balance must navigate, got $r", r is VoiceParseResult.Navigate)
        assertEquals("dashboard", (r as VoiceParseResult.Navigate).route)
    }

    @Test
    fun goToScreensNavigate() {
        assertEquals(
            "markets",
            (parseVoiceUtterance("go to markets", markets) as VoiceParseResult.Navigate).route
        )
        assertEquals(
            "wallet",
            (parseVoiceUtterance("go to wallet", markets) as VoiceParseResult.Navigate).route
        )
        assertEquals(
            "portfolio",
            (parseVoiceUtterance("show my positions", markets) as VoiceParseResult.Navigate).route
        )
        assertEquals(
            "dashboard",
            (parseVoiceUtterance("go to dashboard", markets) as VoiceParseResult.Navigate).route
        )
    }

    @Test
    fun balanceQuestionStillAnswers() {
        val r = parseVoiceUtterance("what is my balance", markets)
        assertTrue("What is my balance must stay a spoken answer, got $r", r is VoiceParseResult.Question)
        assertTrue(
            (r as VoiceParseResult.Question).question is VoiceQuestion.Balance
        )
    }

    @Test
    fun navigationIsScoredLikeAQuestion() {
        val (parsed, _) = parseBestVoiceUtterance(listOf("open bitcoin"), markets)
        assertTrue("Best parse of 'open bitcoin' must navigate, got $parsed", parsed is VoiceParseResult.Navigate)
    }

    @Test
    fun historyRecordsNavigation() {
        assertEquals("Opened", VoiceHistoryOutcome.NAVIGATED.label())
        val entries = addVoiceHistory(
            emptyList(),
            VoiceHistoryEntry("open bitcoin", VoiceHistoryOutcome.NAVIGATED, 0L)
        )
        assertEquals(1, entries.size)
        assertEquals(VoiceHistoryOutcome.NAVIGATED, entries[0].outcome)
        assertEquals("open bitcoin", entries[0].text)
    }

    @Test
    fun activityActsOnVoiceNavigation() {
        val main = readSource("app/src/main/java/xyz/fruitslice/prognos/MainActivity.kt")
        assertTrue(main.contains("vm.voiceNavRequest.collect"))
        assertTrue(main.contains("nav.navigate(route)"))
        assertTrue(main.contains("goTab(route)"))
        assertTrue(main.contains("vm.clearVoiceNavRequest()"))
        val vm = readSource("app/src/main/java/xyz/fruitslice/prognos/ui/AppViewModel.kt")
        assertTrue(vm.contains("_voiceNavRequest.value = parsed.route"))
        assertTrue(vm.contains("VoiceHistoryOutcome.NAVIGATED"))
    }
}
