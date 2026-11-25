package xyz.fruitslice.prognos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fruitslice.prognos.data.VoiceLanguage
import xyz.fruitslice.prognos.data.chooseReplyLanguage
import xyz.fruitslice.prognos.data.platformAnswer
import xyz.fruitslice.prognos.data.resolvePlatformAnswer
import xyz.fruitslice.prognos.voice.GroqAsrProvider
import xyz.fruitslice.prognos.voice.parseDetectedLanguageCode

/**
 * v1.1.19: voice learns the whole platform (Prompt 1, minus the
 * early-sell topic held out on Flint's order), answers in the language
 * spoken (Prompt 2), and trades only behind the biometric gate
 * (Prompt 3, manual checklist in BiometricGate.kt).
 */
class V1119Test {

    // ---- Prompt 1: platform knowledge topics (English) ----

    @Test
    fun topic1HowTradingWorks() {
        val answer = platformAnswer("how does trading work on prognos labs")
        assertTrue(answer!!.startsWith("First, pick a market and choose Yes or No."))
    }

    @Test
    fun topic2EnterAMarket() {
        val answer = platformAnswer("how do i enter a market")
        assertTrue(answer!!.contains("Pick Yes if you think it will happen."))
    }

    @Test
    fun topic3PriceAndAmountStatesFiveDollarMinimum() {
        val answer = platformAnswer("how do i set a price and an amount")
        assertTrue(answer!!.contains("The smallest amount is \$5, and there is no largest amount."))
        assertTrue(answer.contains("the price shown on the chart"))
    }

    @Test
    fun topic4AfterSayingYes() {
        val answer = platformAnswer("what happens after i say yes")
        assertTrue(answer!!.contains("the order is filled"))
    }

    @Test
    fun topic5SettlementAndPayout() {
        val answer = platformAnswer("how do i get paid if i win")
        assertTrue(answer!!.contains("A human decides the result"))
    }

    @Test
    fun topic7OpenPositions() {
        val answer = platformAnswer("what positions do i have open")
        assertTrue(answer!!.contains("what positions you have open"))
    }

    @Test
    fun topic8PlaceTradeNow() {
        val answer = platformAnswer("place this trade for me now")
        assertTrue(answer!!.contains("Trading is open."))
    }

    @Test
    fun topic1BrandMishearingsStillAnswer() {
        // v1.1.20: Groq mishears the brand name; the answer must still land.
        for (variant in listOf("pronios", "prognus", "prognoz", "provenos", "probe news")) {
            val answer = platformAnswer("what is $variant labs")
            assertTrue("'$variant' must still answer what Prognos Labs is", answer!!.contains("Prognos Labs is a prediction market"))
        }
    }

    @Test
    fun isTradingOpenAnswersOpen() {
        val answer = platformAnswer("is trading open")
        assertTrue(answer!!.contains("Trading is open now."))
    }

    @Test
    fun topic9CannotDo() {
        val answer = platformAnswer("what should i buy")
        assertTrue(answer!!.contains("I never trade without a read back and a yes from you."))
    }

    @Test
    fun topic6EarlySellHeldOut() {
        // Flint, 25 Nov: build without Q3. No early-sell answer in v1.1.19.
        assertNull(platformAnswer("how do i sell before the market ends"))
    }

    @Test
    fun topic1TranslatedYoruba() {
        val answer = platformAnswer("bawo ni iṣowo ṣe n ṣiṣẹ", VoiceLanguage.YORUBA)
        assertTrue(answer!!.contains("yan ọja"))
    }

    // ---- Prompt 2: reply language fallback ----

    @Test
    fun chooseReplyLanguageDetectedWins() {
        val chosen = chooseReplyLanguage(VoiceLanguage.YORUBA, VoiceLanguage.ENGLISH) { it == VoiceLanguage.YORUBA }
        assertEquals(VoiceLanguage.YORUBA, chosen)
    }

    @Test
    fun chooseReplyLanguageFallsBackToSet() {
        val chosen = chooseReplyLanguage(null, VoiceLanguage.SPANISH) { it == VoiceLanguage.SPANISH }
        assertEquals(VoiceLanguage.SPANISH, chosen)
    }

    @Test
    fun chooseReplyLanguageFallsBackToEnglish() {
        val chosen = chooseReplyLanguage(VoiceLanguage.SPANISH, VoiceLanguage.ENGLISH) { it == VoiceLanguage.ENGLISH }
        assertEquals(VoiceLanguage.ENGLISH, chosen)
    }

    @Test
    fun resolvePlatformAnswerDetectedLanguage() {
        val resolved = resolvePlatformAnswer(
            "bawo ni iṣowo ṣe n ṣiṣẹ",
            VoiceLanguage.YORUBA,
            VoiceLanguage.ENGLISH
        )
        assertEquals(VoiceLanguage.YORUBA, resolved?.second)
        assertTrue(resolved!!.first.contains("yan ọja"))
    }

    @Test
    fun resolvePlatformAnswerFallsBackToSetLanguage() {
        // English text has no Spanish answer, so it falls to the set language (English).
        val resolved = resolvePlatformAnswer(
            "how does trading work on prognos labs",
            VoiceLanguage.SPANISH,
            VoiceLanguage.ENGLISH
        )
        assertEquals(VoiceLanguage.ENGLISH, resolved?.second)
        assertTrue(resolved!!.first.startsWith("First, pick a market"))
    }

    // ---- Prompt 2: Groq response parsing ----

    @Test
    fun parseGroqResponseWithLanguage() {
        val result = GroqAsrProvider.parseGroqAsrResponse(
            "{\"ok\":true,\"text\":\"hello\",\"language\":\"yo\"}"
        )
        assertEquals("hello", result?.text)
        assertEquals(VoiceLanguage.YORUBA, result?.detectedLanguage)
    }

    @Test
    fun parseGroqResponseWithoutLanguage() {
        val result = GroqAsrProvider.parseGroqAsrResponse(
            "{\"ok\":true,\"text\":\"hello\"}"
        )
        assertEquals("hello", result?.text)
        assertNull(result?.detectedLanguage)
    }

    @Test
    fun parseGroqResponseUnknownLanguage() {
        val result = GroqAsrProvider.parseGroqAsrResponse(
            "{\"ok\":true,\"text\":\"hello\",\"language\":\"xx\"}"
        )
        assertEquals("hello", result?.text)
        assertNull(result?.detectedLanguage)
    }

    @Test
    fun parseGroqResponseEmptyText() {
        assertNull(GroqAsrProvider.parseGroqAsrResponse("{\"ok\":true,\"text\":\"\"}"))
        assertNull(GroqAsrProvider.parseGroqAsrResponse("not json"))
    }

    @Test
    fun parseDetectedLanguageCode() {
        assertEquals(VoiceLanguage.YORUBA, parseDetectedLanguageCode("yo"))
        assertEquals(VoiceLanguage.HEBREW, parseDetectedLanguageCode("iw"))
        assertEquals(VoiceLanguage.IGBO, parseDetectedLanguageCode("ig"))
        assertEquals(VoiceLanguage.CHINESE, parseDetectedLanguageCode("zh"))
        assertNull(parseDetectedLanguageCode("xx"))
        assertNull(parseDetectedLanguageCode(null))
        assertNull(parseDetectedLanguageCode(""))
    }
}
