package xyz.prognoslabs.app

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.AppConfig
import xyz.prognoslabs.app.data.TradeSide
import xyz.prognoslabs.app.data.VoiceConfirmation
import xyz.prognoslabs.app.data.VoiceFeedbackReporter
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.VoiceParseResult
import xyz.prognoslabs.app.data.VoiceQuestion
import xyz.prognoslabs.app.data.buildVoiceFeedbackReport
import xyz.prognoslabs.app.data.normalizeHeard
import xyz.prognoslabs.app.data.parseBestVoiceUtterance
import xyz.prognoslabs.app.data.parseConfirmation
import xyz.prognoslabs.app.data.parseVoiceUtterance
import xyz.prognoslabs.app.data.shouldReportVoiceFeedback
import xyz.prognoslabs.app.data.vocabFor
import xyz.prognoslabs.app.data.voiceFeedbackJson
import xyz.prognoslabs.app.data.voiceLanguageForLocale
import xyz.prognoslabs.app.data.voiceLanguageSupported
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.Outcome
import xyz.prognoslabs.app.model.TagDto
import xyz.prognoslabs.app.ui.VoiceEngine

/**
 * v1.1.12: voice training round 1. The voice understands natural
 * speech in seven languages, not just three test questions in
 * English. Recognition follows the phone language where this build
 * supports it and falls back to US English. The voice history page
 * is gone, the mic is one entry point opening a call-style overlay,
 * change password is its own request flow, and failed phrases are
 * reported only when the user opts in, anonymously, phrase text
 * only. Trading stays closed, no version numbers anywhere.
 */
class V1112Test {

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
    private val singleBtc = listOf(btc90k)

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

    private fun orderOf(text: String, language: VoiceLanguage = VoiceLanguage.ENGLISH) =
        parseVoiceUtterance(text, singleBtc, language = language)

    private fun questionOf(text: String, language: VoiceLanguage = VoiceLanguage.ENGLISH) =
        parseVoiceUtterance(text, markets, language = language)

    // ---- 1. English phrase expansion ----

    @Test
    fun buyTenOfBitcoinParsesAsOrder() {
        val r = orderOf("buy 10 of bitcoin")
        assertTrue("got $r", r is VoiceParseResult.Order)
        val order = (r as VoiceParseResult.Order).order
        assertEquals(10.0, order.amountUsd, 0.001)
        assertEquals(TradeSide.UP, order.side)
    }

    @Test
    fun wantToBuySomeBitcoinAsksForAmount() {
        val r = orderOf("I want to buy some bitcoin")
        assertTrue("got $r", r is VoiceParseResult.AskAgain)
    }

    @Test
    fun getMeTenDollarsOfBitcoinParsesAsOrder() {
        val r = orderOf("get me ten dollars of bitcoin")
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(10.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun sellMyBitcoinAsksForAmount() {
        val r = orderOf("sell my bitcoin")
        assertTrue("got $r", r is VoiceParseResult.AskAgain)
    }

    @Test
    fun sellHalfAsksForDollarAmount() {
        val r = orderOf("sell half")
        assertTrue("got $r", r is VoiceParseResult.AskAgain)
        assertEquals(
            vocabFor(VoiceLanguage.ENGLISH).askAmountFraction,
            (r as VoiceParseResult.AskAgain).message
        )
    }

    @Test
    fun sellAllAsksForDollarAmount() {
        val r = orderOf("sell all")
        assertTrue("got $r", r is VoiceParseResult.AskAgain)
        assertEquals(
            vocabFor(VoiceLanguage.ENGLISH).askAmountFraction,
            (r as VoiceParseResult.AskAgain).message
        )
    }

    @Test
    fun amountAfterAssetParsesWhenNotAPriceQualifier() {
        val r = orderOf("buy bitcoin 50")
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(50.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun amountMatchingAPriceQualifierIsNotAnAmount() {
        val r = parseVoiceUtterance("buy bitcoin above 90k", markets)
        assertTrue("got $r", r is VoiceParseResult.AskAgain)
    }

    @Test
    fun mishearingsStillNormalize() {
        val r = orderOf("by 50 yes on bitcoin")
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(50.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
        val bitCoin = orderOf("buy 50 yes on bit coin")
        assertTrue("got $bitCoin", bitCoin is VoiceParseResult.Order)
        val cell = orderOf("cell my bitcoin")
        assertTrue("got $cell", cell is VoiceParseResult.AskAgain)
    }

    // ---- 2. Confirmations in every language ----

    @Test
    fun confirmEnglishVariedWording() {
        assertEquals(VoiceConfirmation.YES, parseConfirmation("yes"))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("yeah"))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("correct"))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("go ahead"))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("no"))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("cancel"))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("not that one"))
        assertEquals(VoiceConfirmation.UNCLEAR, parseConfirmation("maybe"))
    }

    @Test
    fun confirmSpanish() {
        val l = VoiceLanguage.SPANISH
        assertEquals(VoiceConfirmation.YES, parseConfirmation("sí", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("correcto", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("de acuerdo", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("no", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("cancelar", l))
    }

    @Test
    fun confirmFrench() {
        val l = VoiceLanguage.FRENCH
        assertEquals(VoiceConfirmation.YES, parseConfirmation("oui", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("correct", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("vas-y", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("non", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("annuler", l))
    }

    @Test
    fun confirmPortuguese() {
        val l = VoiceLanguage.PORTUGUESE
        assertEquals(VoiceConfirmation.YES, parseConfirmation("sim", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("certo", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("não", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("cancelar", l))
    }

    @Test
    fun confirmGerman() {
        val l = VoiceLanguage.GERMAN
        assertEquals(VoiceConfirmation.YES, parseConfirmation("ja", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("genau", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("stimmt", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("nein", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("abbrechen", l))
    }

    @Test
    fun confirmTurkish() {
        val l = VoiceLanguage.TURKISH
        assertEquals(VoiceConfirmation.YES, parseConfirmation("evet", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("tamam", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("doğru", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("hayır", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("iptal", l))
    }

    @Test
    fun confirmArabic() {
        val l = VoiceLanguage.ARABIC
        assertEquals(VoiceConfirmation.YES, parseConfirmation("نعم", l))
        assertEquals(VoiceConfirmation.YES, parseConfirmation("صحيح", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("لا", l))
        assertEquals(VoiceConfirmation.NO, parseConfirmation("إلغاء", l))
    }

    // ---- 3. Multilingual orders ----

    @Test
    fun spanishOrderParses() {
        val r = orderOf("comprar diez dólares de bitcoin", VoiceLanguage.SPANISH)
        assertTrue("got $r", r is VoiceParseResult.Order)
        val order = (r as VoiceParseResult.Order).order
        assertEquals(10.0, order.amountUsd, 0.001)
        assertEquals(TradeSide.UP, order.side)
    }

    @Test
    fun frenchOrderParses() {
        val r = orderOf("acheter dix dollars de bitcoin", VoiceLanguage.FRENCH)
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(10.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun portugueseOrderParses() {
        val r = orderOf("comprar dez dólares de bitcoin", VoiceLanguage.PORTUGUESE)
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(10.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun germanOrderParses() {
        val r = orderOf("kaufe zehn dollar bitcoin", VoiceLanguage.GERMAN)
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(10.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun turkishOrderParses() {
        val r = orderOf("on dolar bitcoin almak istiyorum", VoiceLanguage.TURKISH)
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(10.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun arabicOrderParses() {
        val r = orderOf("اشتري بيتكوين بعشرة دولارات", VoiceLanguage.ARABIC)
        assertTrue("got $r", r is VoiceParseResult.Order)
        assertEquals(10.0, (r as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    @Test
    fun bestParseWorksInSpanish() {
        val (parsed, _) = parseBestVoiceUtterance(
            listOf("comprar bitcoin", "comprar diez dólares de bitcoin"),
            singleBtc, language = VoiceLanguage.SPANISH
        )
        assertTrue("got $parsed", parsed is VoiceParseResult.Order)
        assertEquals(10.0, (parsed as VoiceParseResult.Order).order.amountUsd, 0.001)
    }

    // ---- 4. Multilingual greetings and questions ----

    @Test
    fun greetingsInEveryLanguage() {
        val cases = listOf(
            "hola" to VoiceLanguage.SPANISH,
            "bonjour" to VoiceLanguage.FRENCH,
            "olá" to VoiceLanguage.PORTUGUESE,
            "hallo" to VoiceLanguage.GERMAN,
            "merhaba" to VoiceLanguage.TURKISH,
            "مرحبا" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang hello got $r", r is VoiceParseResult.Question)
            assertTrue(
                "$lang hello must be a greeting",
                (r as VoiceParseResult.Question).question is VoiceQuestion.GreetingHello
            )
        }
    }

    @Test
    fun howAreYouInEveryLanguage() {
        val cases = listOf(
            "cómo estás" to VoiceLanguage.SPANISH,
            "comment allez-vous" to VoiceLanguage.FRENCH,
            "como vai você" to VoiceLanguage.PORTUGUESE,
            "wie geht es dir" to VoiceLanguage.GERMAN,
            "nasıl gidiyor" to VoiceLanguage.TURKISH,
            "كيف حالك" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang how-are-you got $r", r is VoiceParseResult.Question)
            assertTrue(
                "$lang how-are-you must be its own answer",
                (r as VoiceParseResult.Question).question is VoiceQuestion.GreetingHowAreYou
            )
        }
    }

    @Test
    fun priceQuestionInEveryLanguage() {
        val cases = listOf(
            "cuánto cuesta bitcoin" to VoiceLanguage.SPANISH,
            "quel est le prix du bitcoin" to VoiceLanguage.FRENCH,
            "quanto custa o bitcoin" to VoiceLanguage.PORTUGUESE,
            "was kostet bitcoin" to VoiceLanguage.GERMAN,
            "bitcoin fiyatı ne kadar" to VoiceLanguage.TURKISH,
            "كم سعر بيتكوين" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang price got $r", r is VoiceParseResult.Question)
            assertTrue(
                "$lang price must be a price question",
                (r as VoiceParseResult.Question).question is VoiceQuestion.Price
            )
        }
    }

    @Test
    fun mostLiquidQuestionInEveryLanguage() {
        val cases = listOf(
            "cuál es el mercado más líquido" to VoiceLanguage.SPANISH,
            "quel est le marché le plus liquide" to VoiceLanguage.FRENCH,
            "qual é o mercado mais líquido" to VoiceLanguage.PORTUGUESE,
            "welcher markt hat das meiste volumen" to VoiceLanguage.GERMAN,
            "en likit piyasa hangisi" to VoiceLanguage.TURKISH,
            "ما هو السوق الأكثر سيولة" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang liquid got $r", r is VoiceParseResult.Question)
            assertEquals(
                "$lang liquid must be the most-liquid question",
                VoiceQuestion.MostLiquid,
                (r as VoiceParseResult.Question).question
            )
        }
    }

    @Test
    fun newsQuestionInEveryLanguage() {
        val cases = listOf(
            "últimas noticias de bitcoin" to VoiceLanguage.SPANISH,
            "dernières nouvelles sur bitcoin" to VoiceLanguage.FRENCH,
            "últimas notícias do bitcoin" to VoiceLanguage.PORTUGUESE,
            "neueste nachrichten über bitcoin" to VoiceLanguage.GERMAN,
            "bitcoin hakkında son haberler" to VoiceLanguage.TURKISH,
            "ما هي آخر أخبار بيتكوين" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang news got $r", r is VoiceParseResult.Question)
            assertTrue(
                "$lang news must be a news question",
                (r as VoiceParseResult.Question).question is VoiceQuestion.News
            )
        }
    }

    @Test
    fun balanceQuestionInEveryLanguage() {
        val cases = listOf(
            "mi saldo" to VoiceLanguage.SPANISH,
            "mon solde" to VoiceLanguage.FRENCH,
            "meu saldo" to VoiceLanguage.PORTUGUESE,
            "mein guthaben" to VoiceLanguage.GERMAN,
            "bakiyem ne kadar" to VoiceLanguage.TURKISH,
            "ما هو رصيدي" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang balance got $r", r is VoiceParseResult.Question)
            assertEquals(
                "$lang balance must be the balance question",
                VoiceQuestion.Balance,
                (r as VoiceParseResult.Question).question
            )
        }
    }

    @Test
    fun platformQuestionInEveryLanguage() {
        val cases = listOf(
            "qué es prognos labs" to VoiceLanguage.SPANISH,
            "c'est quoi prognos labs" to VoiceLanguage.FRENCH,
            "o que é prognos labs" to VoiceLanguage.PORTUGUESE,
            "was ist prognos labs" to VoiceLanguage.GERMAN,
            "prognos labs nedir" to VoiceLanguage.TURKISH,
            "ما هي prognos labs" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang platform got $r", r is VoiceParseResult.Question)
            assertTrue(
                "$lang platform must be a knowledge answer",
                (r as VoiceParseResult.Question).question is VoiceQuestion.Knowledge
            )
        }
    }

    @Test
    fun tradingOpenQuestionInSpanishAndGerman() {
        val es = questionOf("cuándo abre el trading", VoiceLanguage.SPANISH)
        assertTrue("got $es", es is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.TradingOpens, (es as VoiceParseResult.Question).question)
        val de = questionOf("wann öffnet der handel", VoiceLanguage.GERMAN)
        assertTrue("got $de", de is VoiceParseResult.Question)
        assertEquals(VoiceQuestion.TradingOpens, (de as VoiceParseResult.Question).question)
    }

    @Test
    fun outOfScopeInEveryLanguage() {
        val cases = listOf(
            "cuál es el clima mañana" to VoiceLanguage.SPANISH,
            "quel temps fera-t-il demain" to VoiceLanguage.FRENCH,
            "como está o tempo amanhã" to VoiceLanguage.PORTUGUESE,
            "wie wird das wetter morgen" to VoiceLanguage.GERMAN,
            "yarın hava nasıl olacak" to VoiceLanguage.TURKISH,
            "كيف سيكون الطقس غدا" to VoiceLanguage.ARABIC
        )
        for ((text, lang) in cases) {
            val r = questionOf(text, lang)
            assertTrue("$lang weather must stay out of scope, got $r", r is VoiceParseResult.OutOfScope)
        }
    }

    // ---- 5. Locale follows the phone, English is the fallback ----

    @Test
    fun localeMapsToSupportedLanguages() {
        assertEquals(VoiceLanguage.SPANISH, voiceLanguageForLocale(Locale("es")))
        assertEquals(VoiceLanguage.SPANISH, voiceLanguageForLocale(Locale("es", "ES")))
        assertEquals(VoiceLanguage.FRENCH, voiceLanguageForLocale(Locale("fr")))
        assertEquals(VoiceLanguage.PORTUGUESE, voiceLanguageForLocale(Locale("pt")))
        assertEquals(VoiceLanguage.GERMAN, voiceLanguageForLocale(Locale("de")))
        assertEquals(VoiceLanguage.TURKISH, voiceLanguageForLocale(Locale("tr")))
        assertEquals(VoiceLanguage.ARABIC, voiceLanguageForLocale(Locale("ar")))
        assertEquals(VoiceLanguage.ENGLISH, voiceLanguageForLocale(Locale("en")))
        assertEquals(VoiceLanguage.ENGLISH, voiceLanguageForLocale(Locale("en", "US")))
    }

    @Test
    fun unsupportedLocaleFallsBackToEnglish() {
        assertEquals(VoiceLanguage.ENGLISH, voiceLanguageForLocale(Locale("ja")))
        // Chinese is supported since v1.1.17, so Korean stands in
        // as the unsupported locale here.
        assertEquals(VoiceLanguage.ENGLISH, voiceLanguageForLocale(Locale("ko")))
        assertFalse(voiceLanguageSupported(Locale("ja")))
        assertFalse(voiceLanguageSupported(Locale("ko")))
        assertTrue(voiceLanguageSupported(Locale("es")))
        assertTrue(voiceLanguageSupported(Locale("ar")))
        assertTrue(voiceLanguageSupported(Locale("en")))
    }

    @Test
    fun recognitionLocaleIsUsEnglishFallback() {
        assertEquals(Locale.US, VoiceEngine.RECOGNITION_LOCALE)
        val src = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt"
        )
        assertTrue(src.contains("fun recognitionLocale()"))
        assertTrue(src.contains("voiceLanguageForLocale"))
        assertTrue(src.contains("voiceLanguageSupported"))
    }

    @Test
    fun everySupportedLanguageHasVocab() {
        for (lang in VoiceLanguage.values()) {
            val vocab = vocabFor(lang)
            assertTrue("$lang needs yes words", vocab.yesWords.isNotEmpty())
            assertTrue("$lang needs no words", vocab.noWords.isNotEmpty())
            assertTrue("$lang needs buy verbs", vocab.buyVerbs.isNotEmpty())
            assertTrue("$lang needs sell verbs", vocab.sellVerbs.isNotEmpty())
            assertTrue("$lang needs hello phrases", vocab.helloPhrases.isNotEmpty())
            assertTrue("$lang needs enroll phrases", vocab.enrollPhrases.isNotEmpty())
            assertTrue("$lang needs challenge phrases", vocab.challengePhrases.isNotEmpty())
            assertTrue("$lang needs a greeting answer", vocab.greetingAnswer.isNotEmpty())
            assertTrue("$lang needs an out-of-scope answer", vocab.outOfScopeAnswer.isNotEmpty())
        }
    }

    // ---- 6. Opt-in anonymous failed-phrase reporting ----

    @Test
    fun feedbackToggleOffSendsNothing() {
        var called = 0
        val reporter = VoiceFeedbackReporter { _, _ -> called++ }
        val report = buildVoiceFeedbackReport("comprar bitcoin", "es", "1.1.12")
        assertNotNull(report)
        reporter.report(report, enabled = false)
        assertEquals("Off must send nothing", 0, called)
        reporter.report(null, enabled = true)
        assertEquals("A null report must send nothing", 0, called)
    }

    @Test
    fun feedbackToggleOnSendsPhraseTextOnly() {
        val sent = mutableListOf<Pair<String, String>>()
        val reporter = VoiceFeedbackReporter { url, body -> sent.add(url to body) }
        val report = buildVoiceFeedbackReport("no entendi eso", "es", "1.1.12")
        assertNotNull(report)
        reporter.report(report, enabled = true)
        assertEquals(1, sent.size)
        assertEquals(AppConfig.VOICE_FEEDBACK_URL, sent[0].first)
        val body = sent[0].second
        assertEquals(
            "{\"phrase\":\"no entendi eso\",\"language\":\"es\",\"version\":\"1.1.12\"}",
            body
        )
        assertEquals(voiceFeedbackJson(report!!), body)
        for (forbidden in listOf("audio", "name", "email", "wallet", "device", "ip", "address")) {
            assertFalse("The report must never carry $forbidden", body.contains(forbidden))
        }
    }

    @Test
    fun feedbackReportCapsLongPhrasesAndRejectsBlank() {
        assertNull(buildVoiceFeedbackReport("   ", "en", "1.1.12"))
        val long = buildVoiceFeedbackReport("x".repeat(500), "en", "1.1.12")
        assertNotNull(long)
        assertEquals(200, long!!.phrase.length)
    }

    @Test
    fun feedbackReportsOnlyFailures() {
        assertTrue(shouldReportVoiceFeedback(VoiceParseResult.AskAgain("Say it again.")))
        assertTrue(shouldReportVoiceFeedback(VoiceParseResult.OutOfScope("I only answer questions about Prognos Labs.")))
        val order = orderOf("buy 10 of bitcoin")
        assertTrue(order is VoiceParseResult.Order)
        assertFalse(shouldReportVoiceFeedback(order))
        val question = questionOf("hola", VoiceLanguage.SPANISH)
        assertTrue(question is VoiceParseResult.Question)
        assertFalse(shouldReportVoiceFeedback(question))
        assertFalse(shouldReportVoiceFeedback(VoiceParseResult.Navigate("markets", "Markets")))
    }

    @Test
    fun feedbackFailedSendDoesNotDisturbTheSession() {
        val reporter = VoiceFeedbackReporter { _, _ -> throw RuntimeException("network down") }
        val report = buildVoiceFeedbackReport("comprar bitcoin", "es", "1.1.12")
        reporter.report(report, enabled = true)
    }

    @Test
    fun feedbackEndpointAcceptsOnlyTheReportShape() {
        val php = readSource("../trading-off-build/voice-feedback.php")
        assertTrue(php.contains("voice-feedback.jsonl"))
        assertTrue(php.contains("REQUEST_METHOD"))
        assertTrue(php.contains("bad_phrase"))
        assertTrue(php.contains("LOCK_EX"))
        // The stored record is exactly phrase, language, version and
        // timestamp. The header comments name audio and wallet only
        // to say they are never stored, so those words are not
        // checked here; the record below is the check.
        assertTrue(php.contains("'phrase' => \$phrase"))
        assertTrue(php.contains("'language' => \$language"))
        assertTrue(php.contains("'version' => \$version"))
        assertFalse("The endpoint must never store an IP address", php.contains("REMOTE_ADDR"))
        assertTrue(php.contains("array('ok' => true)"))
    }

    // ---- 7. Voice history page gone, one mic, call overlay ----

    @Test
    fun voiceHistoryScreenIsGone() {
        val screens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt"
        )
        val voiceScreens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt"
        )
        val main = readSource(
            "app/src/main/java/xyz/prognoslabs/app/MainActivity.kt"
        )
        for ((name, src) in listOf("Screens" to screens, "VoiceScreens" to voiceScreens, "Main" to main)) {
            assertFalse("$name must not mention the history screen", src.contains("VoiceHistoryScreen"))
            assertFalse("$name must not mention the history route", src.contains("\"voiceHistory\""))
        }
        assertFalse("Settings must not offer a voice history button", screens.contains("Voice history"))
    }

    @Test
    fun singleMicEntryPointInHeaders() {
        // v1.1.15: the mic moved out of the scaffold. There is one
        // entry point per screen, a small button in the header, and
        // no floating button anywhere: the old lime circle covered
        // content on every screen.
        val screens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt"
        )
        assertFalse("VoiceMicFab must be gone", screens.contains("VoiceMicFab"))
        val calls = Regex("VoiceMicHeaderButton\\(onClick").findAll(screens).count()
        assertEquals("Eight screens must carry the header mic button", 8, calls)
        val main = readSource(
            "app/src/main/java/xyz/prognoslabs/app/MainActivity.kt"
        )
        assertFalse("The scaffold must carry no mic button", main.contains("VoiceMicHeaderButton"))
        assertFalse("The scaffold must have no floating action button", main.contains("floatingActionButton"))
    }

    @Test
    fun callOverlayHasHangUp() {
        val voiceScreens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt"
        )
        assertTrue(voiceScreens.contains("End call"))
        assertTrue("The call view must show the real logo at the top", voiceScreens.contains("LogoImage"))
        assertTrue(voiceScreens.contains("hangUpVoice"))
        assertFalse("The overlay must not offer a history button", voiceScreens.contains("Voice history"))
    }

    @Test
    fun feedbackToggleExistsInSettings() {
        val screens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt"
        )
        assertTrue(screens.contains("Help improve voice"))
        assertTrue(screens.contains("setVoiceFeedbackOptIn"))
        assertTrue(screens.contains("voiceFeedbackOptIn"))
    }

    @Test
    fun changePasswordIsItsOwnFlow() {
        val screens = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt"
        )
        assertTrue(screens.contains("fun ChangePasswordScreen"))
        assertTrue(screens.contains("requestChangePasswordCode"))
        assertTrue(screens.contains("verifyChangePasswordCode"))
        assertTrue(screens.contains("saveNewPasswordAfterChange"))
        assertTrue(screens.contains("Send code"))
        assertTrue(screens.contains("Verify code"))
        assertTrue(screens.contains("Save new password"))
        assertFalse("Settings must not carry an inline Authentication section", screens.contains("SectionHeader(\"Authentication\")"))
        val main = readSource(
            "app/src/main/java/xyz/prognoslabs/app/MainActivity.kt"
        )
        assertTrue(main.contains("\"changePassword\""))
        assertTrue(main.contains("onOpenChangePassword"))
    }

    // ---- 8. Version ----

    @Test
    fun versionIs1123() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 38"))
        assertTrue(gradle.contains("versionName = \"1.1.28\""))
    }
}
