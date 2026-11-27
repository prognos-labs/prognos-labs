package xyz.prognoslabs.app.data

import xyz.prognoslabs.app.model.ASSET_LABELS
import xyz.prognoslabs.app.model.Market
import xyz.prognoslabs.app.model.parseNum

/**
 * Voice orders, pure. No Android classes here, so unit tests can run on
 * the JVM. This is the only place a spoken sentence becomes a trade:
 * it pulls out the side, the market and the amount, and it never
 * guesses. Anything unclear comes back as [VoiceParseResult.AskAgain]
 * with a plain message, and the app asks the user to say it again.
 *
 * The spoken number is the USD amount in the same buy form the tap
 * buttons fill in: "buy 50 Yes on BTC above 90k" spends $50, and the
 * form shows the shares that buys at the live price. There is no
 * second trading path: a parsed order is confirmed by the user and
 * then goes through AppViewModel.placeOrder exactly like a tap.
 *
 * Scope rule (Flint, 22 Nov): the voice only knows Prognos Labs
 * trading. A question that is not about Prognos Labs trading is not
 * answered; it comes back as OutOfScope with a short plain decline.
 */

/** One parsed voice order, ready to fill the buy form. */
data class VoiceOrder(
    val marketId: String,
    val marketLabel: String,
    val side: TradeSide,
    val amountUsd: Double,
    /** Plain-words read-back, e.g. "Buy 50 Yes on BTC close above $90k". */
    val readback: String
)

/** What the voice assistant understood a spoken question to be about. */
sealed class VoiceQuestion {
    /** "What is the BTC price?" */
    data class Price(val asset: String) : VoiceQuestion()

    /** "How much is in my balance?" */
    object Balance : VoiceQuestion()

    /** "How do I buy?" */
    object HowToTrade : VoiceQuestion()

    /** "When does trading open?" */
    object TradingOpens : VoiceQuestion()

    /** "What is the BTC market?" */
    data class MarketInfo(val marketId: String) : VoiceQuestion()

    /** "Hello" or "hi": a plain hello back, never a "how are you"
     * in reply. A conversational turn: the voice answers and keeps
     * listening for the follow-up. */
    object GreetingHello : VoiceQuestion() {
        override val isConversational: Boolean = true
    }

    /** "How are you" or "how's it going": its own answer, never
     * given to a plain "hello". Also conversational. */
    object GreetingHowAreYou : VoiceQuestion() {
        override val isConversational: Boolean = true
    }

    /** "Can you hear me?" / "Are you listening?": the user checking
     * the mic is live. A plain yes back, never a refusal: refusing
     * it reads as the voice not working at all. Conversational. */
    object CheckIn : VoiceQuestion()

    /** "What is the most liquid market on Prognos Labs?" */
    object MostLiquid : VoiceQuestion()

    /** A question the platform knowledge base answers, in plain words. */
    data class Knowledge(val answer: String) : VoiceQuestion()

    /** "What is the latest news about Bitcoin?" */
    data class News(val asset: String) : VoiceQuestion()

    /**
     * True when the answer is a conversational turn: the voice
     * answers and keeps the session open for the follow-up, instead
     * of closing it. v1.1.14: every answered question keeps the
     * session open. The old rule closed the session after a
     * one-shot answer, so "Tell me about Prognos Labs" answered
     * and hung up on its own, and the next question was never
     * heard at all. A question is never the end of the call: the
     * user hangs up when they are done.
     */
    open val isConversational: Boolean get() = true
}

sealed class VoiceParseResult {
    data class Order(val order: VoiceOrder) : VoiceParseResult()
    data class Question(val question: VoiceQuestion, val heard: String) : VoiceParseResult()
    /** Voice navigation: open a market or a screen in the app. */
    data class Navigate(val route: String, val label: String) : VoiceParseResult()
    /** Unclear: the app must ask the user to say it again, never guess. */
    data class AskAgain(val message: String) : VoiceParseResult()
    /** Not about Prognos Labs trading: the voice does not answer it. */
    data class OutOfScope(val message: String) : VoiceParseResult()
}

/** The answer to a plain "hello" or "hi". No "how are you" in it. */
const val GREETING_HELLO_ANSWER = "Hi there. What would you like to do?"

/**
 * A greeting in any of the app languages, checked whatever the
 * turn language is. "Bonjour" is a greeting even when the language
 * was not detected and the Settings language is English, so it must
 * never fall through to "say it again". Single words match the first
 * word only; phrases match anywhere in the sentence.
 */
val HELLO_WORDS_ANY_LANGUAGE: Set<String> = setOf(
    "bonjour", "salut", // French
    "hola", // Spanish
    "ciao", // Italian, widely used as a greeting
    "olá", "ola", "oi", // Portuguese
    "hallo", // German
    "merhaba", "selam", // Turkish
    "salam", "marhaba", // Arabic
    "shalom", // Hebrew
    "ni hao", // Chinese
    "bawo", // Yoruba
    "sannu", // Hausa
    "ndewo" // Igbo
)

/** The answer to "how are you" or "how's it going". */
const val GREETING_HOW_ARE_YOU_ANSWER = "I'm doing well. What would you like to do?"

/** The voice only answers questions about Prognos Labs trading. */
const val VOICE_OUT_OF_SCOPE =
    "I only answer questions about Prognos Labs trading."

/** One number heard in the text, with where it sits. */
data class SpokenNumber(val value: Double, val start: Int, val end: Int)

private data class WordTok(val word: String, val start: Int, val end: Int)

private fun tokenize(text: String): List<WordTok> {
    val out = mutableListOf<WordTok>()
    // Unicode letters, so Arabic and accented words tokenize too.
    val re = Regex("[\\p{L}\\p{N}.$]+")
    for (m in re.findAll(text)) out.add(WordTok(m.value, m.range.first, m.range.last + 1))
    return out
}

/** True when [lower] contains [word] as a whole word, or [phrase]
 * as a phrase. Single words never match inside another word, so
 * "no" does not fire on "know" and Turkish "al" does not fire on
 * "saldo". Phrases (two or more words) match as written. A hyphen
 * reads as a space, so French "vas-y" is the phrase "vas y". */
fun containsWord(lower: String, word: String): Boolean {
    val normLower = lower.replace('-', ' ')
    val normWord = word.replace('-', ' ')
    if (normWord.contains(' ')) return normLower.contains(normWord)
    // v1.1.27: Chinese has no spaces between words, so a single
    // Chinese word never equals a whole token: match it as a
    // substring instead. Every other language keeps whole-word
    // matching, so "no" never fires on "know".
    if (normWord.any { it in '\u4E00'..'\u9FFF' }) return normLower.contains(normWord)
    return tokenize(normLower).any { it.word.lowercase() == normWord }
}

fun containsAnyWord(lower: String, words: Iterable<String>): Boolean =
    words.any { containsWord(lower, it) }

private val ARABIC_INDIC_DIGITS = mapOf(
    '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
    '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
)

private fun digitNumber(tok: String): Double? {
    val t = tok.trimStart('$').map { ARABIC_INDIC_DIGITS[it] ?: it }.joinToString("")
    if (t.isEmpty()) return null
    val k = t.endsWith("k", ignoreCase = true)
    val core = if (k) t.dropLast(1) else t
    val v = core.toDoubleOrNull() ?: return null
    return if (k) v * 1000.0 else v
}

/** Words that join number words in one language: "fifty and five"
 * in English, "vingt et un" in French, "yirmi beş" needs none.
 * Kept in the parser (not the vocab) because they are grammar,
 * not commands. */
private fun numberConnectors(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("and", "a")
    VoiceLanguage.SPANISH -> setOf("y")
    VoiceLanguage.FRENCH -> setOf("et")
    VoiceLanguage.PORTUGUESE -> setOf("e")
    VoiceLanguage.GERMAN -> setOf("und")
    VoiceLanguage.TURKISH -> setOf("ve")
    VoiceLanguage.ARABIC -> setOf("و")
    VoiceLanguage.IGBO -> setOf("na")
    VoiceLanguage.YORUBA -> setOf("ati", "le")
    VoiceLanguage.HAUSA -> setOf("da")
    VoiceLanguage.HEBREW -> setOf("ו")
    VoiceLanguage.CHINESE -> emptySet()
}

/** The decimal word in one language: "fifty point five". */
private fun decimalWords(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("point")
    VoiceLanguage.SPANISH -> setOf("coma", "punto")
    VoiceLanguage.FRENCH -> setOf("virgule")
    VoiceLanguage.PORTUGUESE -> setOf("vírgula", "virgula", "ponto")
    VoiceLanguage.GERMAN -> setOf("komma")
    VoiceLanguage.TURKISH -> setOf("virgül", "virgul", "nokta")
    VoiceLanguage.ARABIC -> setOf("فاصلة", "نقطة")
    VoiceLanguage.IGBO -> setOf("point")
    VoiceLanguage.YORUBA -> setOf("point")
    VoiceLanguage.HAUSA -> setOf("point")
    VoiceLanguage.HEBREW -> setOf("נקודה")
    VoiceLanguage.CHINESE -> setOf("点")
}

/** The "how" word in one language, for "how do I buy". */
private fun howWord(language: VoiceLanguage): String = when (language) {
    VoiceLanguage.ENGLISH -> "how"
    VoiceLanguage.SPANISH -> "cómo"
    VoiceLanguage.FRENCH -> "comment"
    VoiceLanguage.PORTUGUESE -> "como"
    VoiceLanguage.GERMAN -> "wie"
    VoiceLanguage.TURKISH -> "nasıl"
    VoiceLanguage.ARABIC -> "كيف"
    VoiceLanguage.IGBO -> "kedu"
    VoiceLanguage.YORUBA -> "bawo"
    VoiceLanguage.HAUSA -> "yaya"
    VoiceLanguage.HEBREW -> "איך"
    VoiceLanguage.CHINESE -> "怎么"
}

/** "Sell half" / "sell all" in one language: a fraction of the
 * balance, never a dollar amount, so the voice asks for the
 * amount instead of guessing against the balance. */
private fun fractionWords(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("half", "all")
    VoiceLanguage.SPANISH -> setOf("mitad", "todo")
    VoiceLanguage.FRENCH -> setOf("moitié", "moitie", "tout", "toute")
    VoiceLanguage.PORTUGUESE -> setOf("metade", "tudo")
    VoiceLanguage.GERMAN -> setOf("hälfte", "halfte", "alles")
    VoiceLanguage.TURKISH -> setOf("yarısı", "yarisi", "hepsi", "tamamı", "tamami")
    VoiceLanguage.ARABIC -> setOf("نصف", "كل", "كله")
    VoiceLanguage.IGBO -> setOf("ọkara", "okara", "niile", "half", "all")
    VoiceLanguage.YORUBA -> setOf("idaji", "gbogbo", "half", "all")
    VoiceLanguage.HAUSA -> setOf("rabi", "duka", "half", "all")
    VoiceLanguage.HEBREW -> setOf("חצי", "הכל")
    VoiceLanguage.CHINESE -> setOf("一半", "全部", "所有")
}

private fun isNumberWord(w: String, vocab: LanguageVocab): Boolean =
    vocab.numberWords.containsKey(w) || w in numberConnectors(vocab.language) ||
        w in decimalWords(vocab.language)

/**
 * Reads one number-word run starting at [i], in the caller's
 * language: "fifty five", "one hundred and fifty", "ninety
 * thousand", "fifty point five", "a hundred", "cinquante",
 * "quatre-vingt" (French: quatre + vingt is 80, not 24),
 * "einundzwanzig" (German: split in normalize first),
 * "veinticinco" (Spanish: split in normalize first). Returns the
 * value and the index just past the run.
 */
private fun readWordNumber(tokens: List<WordTok>, i: Int, vocab: LanguageVocab): Pair<Double, Int>? {
    val numbers = vocab.numberWords
    val language = vocab.language
    var idx = i
    var total = 0.0
    var current = 0.0
    var seen = false
    var decimal: Double? = null
    var decimalPlace = 0.1
    while (idx < tokens.size) {
        val w = tokens[idx].word.lowercase()
        when {
            w in numberConnectors(language) -> idx++
            // French: "quatre-vingt" is 80, "quatre-vingt-dix" 90.
            language == VoiceLanguage.FRENCH && w == "quatre" &&
                idx + 1 < tokens.size &&
                tokens[idx + 1].word.lowercase() in setOf("vingt", "vingts") && decimal == null -> {
                current += 80.0
                seen = true
                idx += 2
            }
            numbers.containsKey(w) && decimal == null -> {
                val v = numbers.getValue(w)
                when (v) {
                    100.0 -> {
                        current = (if (current == 0.0) 1.0 else current) * 100.0
                        seen = true
                    }
                    1000.0 -> {
                        total += (if (current == 0.0) 1.0 else current) * 1000.0
                        current = 0.0
                        seen = true
                    }
                    1_000_000.0 -> {
                        total += (if (current == 0.0) 1.0 else current) * 1_000_000.0
                        current = 0.0
                        seen = true
                    }
                    else -> {
                        current += v
                        seen = true
                    }
                }
                idx++
            }
            w in decimalWords(language) -> {
                if (decimal != null) break
                decimal = 0.0
                idx++
            }
            decimal != null && numbers.containsKey(w) -> {
                decimal += numbers.getValue(w) * decimalPlace
                decimalPlace /= 10.0
                seen = true
                idx++
            }
            else -> break
        }
    }
    if (!seen) return null
    val v = total + current + (decimal ?: 0.0)
    return v to idx
}

/** Every number in the text, digits and words, in order. */
fun spokenNumbers(text: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): List<SpokenNumber> {
    val vocab = vocabFor(language)
    val tokens = tokenize(text)
    val out = mutableListOf<SpokenNumber>()
    var i = 0
    while (i < tokens.size) {
        val tok = tokens[i]
        val digit = digitNumber(tok.word)
        if (digit != null) {
            out.add(SpokenNumber(digit, tok.start, tok.end))
            i++
            continue
        }
        if (isNumberWord(tok.word.lowercase(), vocab)) {
            val parsed = readWordNumber(tokens, i, vocab)
            if (parsed != null) {
                val (value, next) = parsed
                val end = tokens[next - 1].end
                out.add(SpokenNumber(value, tok.start, end))
                i = next
                continue
            }
        }
        i++
    }
    return out
}

private val ASSET_WORDS = mapOf(
    "btc" to "BTC", "bitcoin" to "BTC", "bitcóin" to "BTC",
    "eth" to "ETH", "ethereum" to "ETH", "ether" to "ETH",
    "xrp" to "XRP", "ripple" to "XRP",
    "sol" to "SOL", "solana" to "SOL",
    "doge" to "DOGE", "dogecoin" to "DOGE",
    "bnb" to "BNB", "binance" to "BNB",
    "hype" to "HYPE", "hyperliquid" to "HYPE",
    "zec" to "ZEC", "zcash" to "ZEC",
    // Arabic coin names, as the recognizer returns them.
    "بيتكوين" to "BTC",
    "إيثيريوم" to "ETH", "ايثيريوم" to "ETH", "إيثريوم" to "ETH",
    "سولانا" to "SOL", "دوجكوين" to "DOGE", "ريبل" to "XRP",
    "بينانس" to "BNB", "زدكاش" to "ZEC", "هايبرليكويد" to "HYPE",
    "ביטקוין" to "BTC", "אתריום" to "ETH", "סולאנה" to "SOL", "דוג'קוין" to "DOGE"
)

/** Multi-word coin names, checked before single words. */
private val ASSET_PHRASES = mapOf(
    "زد كاش" to "ZEC",
    "بيت كوين" to "BTC",
    "比特币" to "BTC",
    "以太坊" to "ETH",
    "索拉纳" to "SOL",
    "狗狗币" to "DOGE",
    "币安币" to "BNB"
)

/** The answer when no market for the asset is loaded: a plain yes
 * that names the asset, in the turn language. */
fun assetAvailableAnswer(asset: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): String {
    val name = ASSET_LABELS[asset] ?: asset
    return when (language) {
        VoiceLanguage.ENGLISH -> "Yes, $name can be traded on Prognos Labs."
        VoiceLanguage.FRENCH -> "Oui, on peut trader $name sur Prognos Labs."
        VoiceLanguage.SPANISH -> "Sí, $name se puede operar en Prognos Labs."
        VoiceLanguage.PORTUGUESE -> "Sim, $name pode ser negociado na Prognos Labs."
        VoiceLanguage.GERMAN -> "Ja, $name kann auf Prognos Labs gehandelt werden."
        VoiceLanguage.TURKISH -> "Evet, $name Prognos Labs'te alınıp satılabilir."
        VoiceLanguage.ARABIC -> "نعم، يمكن تداول $name على Prognos Labs."
        VoiceLanguage.YORUBA -> "Bẹẹni, a le ṣowo $name lori Prognos Labs."
        VoiceLanguage.HAUSA -> "Eh, ana iya ciniki $name a Prognos Labs."
        VoiceLanguage.HEBREW -> "כן, אפשר לסחור ב$name ב-Prognos Labs."
        VoiceLanguage.CHINESE -> "可以，${name}可以在Prognos Labs上交易。"
        VoiceLanguage.IGBO -> "Ee, a pụrụ ịzụ ahịa $name na Prognos Labs."
    }
}

/**
 * Words for trading itself in one language: the verb "to trade"
 * and the noun "trading", not the buy and sell verbs, which the
 * vocab already holds. A bare "can I trade Bitcoin" names no buy
 * or sell verb, so without these the question has no trading word
 * at all and falls through to the refusal. Each language lists its
 * own spellings, with and without accents, the way the vocab does.
 */
private fun tradeWords(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("trade", "trading")
    VoiceLanguage.FRENCH -> setOf("trader", "trading", "négocier", "negocier", "échanger", "echanger")
    VoiceLanguage.SPANISH -> setOf("operar", "comerciar", "negociar", "trade", "trading")
    VoiceLanguage.PORTUGUESE -> setOf("negociar", "operar", "trade", "trading")
    VoiceLanguage.GERMAN -> setOf("handeln", "traden", "trading", "tauschen")
    VoiceLanguage.TURKISH -> setOf("trade", "trading", "işlem", "islem", "alım satım", "alim satim", "al sat")
    VoiceLanguage.ARABIC -> setOf("تداول", "التداول", "أتداول", "اتداول", "تداولات")
    VoiceLanguage.YORUBA -> setOf("ṣowo", "sowo", "iṣowo", "isowo")
    VoiceLanguage.HAUSA -> setOf("ciniki", "kasuwanci")
    VoiceLanguage.HEBREW -> setOf("לסחור", "מסחר", "סחר")
    VoiceLanguage.CHINESE -> setOf("交易", "买卖")
    VoiceLanguage.IGBO -> setOf("ịzụ ahịa", "izu ahia", "ahịa", "ahia")
}

/** Words that set a price limit below a price, in each language. */
private fun limitBelowWords(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("below", "under", "beneath", "less", "lower")
    VoiceLanguage.SPANISH -> setOf("debajo", "por debajo", "menos")
    VoiceLanguage.FRENCH -> setOf("dessous", "au-dessous", "moins")
    VoiceLanguage.PORTUGUESE -> setOf("abaixo", "menos")
    VoiceLanguage.GERMAN -> setOf("unter", "unterhalb", "weniger")
    VoiceLanguage.TURKISH -> setOf("altında", "altinda", "aşağı", "asagi")
    VoiceLanguage.ARABIC -> setOf("أقل", "اقل", "تحت")
    VoiceLanguage.YORUBA -> setOf("lábẹ́", "labe")
    VoiceLanguage.HAUSA -> setOf("ƙasa", "kasa")
    VoiceLanguage.HEBREW -> setOf("מתחת")
    VoiceLanguage.CHINESE -> setOf("低于")
    VoiceLanguage.IGBO -> setOf("n'okpuru", "nokpuru")
}

/** Words that set a price limit above a price, in each language. */
private fun limitAboveWords(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("above", "over", "more", "higher")
    VoiceLanguage.SPANISH -> setOf("encima", "por encima", "más", "mas")
    VoiceLanguage.FRENCH -> setOf("au-dessus", "plus")
    VoiceLanguage.PORTUGUESE -> setOf("acima", "mais")
    VoiceLanguage.GERMAN -> setOf("über", "uber", "oberhalb", "mehr")
    VoiceLanguage.TURKISH -> setOf("üstünde", "ustunde", "yukarı", "yukari")
    VoiceLanguage.ARABIC -> setOf("فوق", "أعلى", "اعلى")
    VoiceLanguage.YORUBA -> setOf("lókè", "loke")
    VoiceLanguage.HAUSA -> setOf("sama")
    VoiceLanguage.HEBREW -> setOf("מעל")
    VoiceLanguage.CHINESE -> setOf("高于")
    VoiceLanguage.IGBO -> setOf("n'elu", "nelu")
}

/**
 * A price the user named as a limit on a trade: "trade Bitcoin
 * below 35" wants the outcome at 0.35 or less, not $35. [start]
 * and [end] cover the whole limit phrase, so its words never
 * count as a direction; [numberStart] is the number itself, so
 * it never counts as the amount.
 */
private data class PriceLimit(
    val below: Boolean,
    val price: Double,
    val start: Int,
    val end: Int,
    val numberStart: Int
)

/**
 * Finds a price limit in the sentence, if any: a below/above word
 * with a number right after it. The number must read as an outcome
 * price (35 is 0.35, 0.35 stays 0.35); 100 or more is dollars or an
 * amount, never a Yes/No price. A number that belongs to a market's
 * own question ("above 90k") is the market, not a limit.
 */
private fun findPriceLimit(
    lower: String,
    candidates: List<Market>,
    language: VoiceLanguage
): PriceLimit? {
    val numbers = spokenNumbers(lower, language)
    if (numbers.isEmpty()) return null
    val tokens = tokenize(lower)
    for (n in numbers) {
        val before = tokens.filter { it.end <= n.start }.takeLast(2)
        for (tok in before) {
            val w = tok.word.lowercase()
            val below = w in limitBelowWords(language)
            val above = !below && w in limitAboveWords(language)
            if (!below && !above) continue
            val price = when {
                n.value > 0 && n.value < 1 -> n.value
                n.value >= 1 && n.value < 100 -> n.value / 100.0
                else -> continue
            }
            if (candidates.any { numberAppearsIn(it.question, n.value) }) continue
            return PriceLimit(below, price, tok.start, n.end, n.start)
        }
    }
    return null
}

private fun formatOutcomePrice(v: Double): String =
    String.format(java.util.Locale.US, "%.2f", v)

/**
 * "Can I trade" in one language, for the HowToTrade branch: the
 * question form that needs no "how" word. Each language brings
 * its own phrasing, with and without accents.
 */
private fun canTradePhrases(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("can i trade")
    VoiceLanguage.FRENCH -> setOf("puis-je trader", "puis je trader", "je peux trader", "peut-on trader", "on peut trader")
    VoiceLanguage.SPANISH -> setOf("puedo operar", "puedo comerciar", "puedo negociar", "se puede operar")
    VoiceLanguage.PORTUGUESE -> setOf("posso negociar", "posso operar", "posso trader", "consigo negociar")
    VoiceLanguage.GERMAN -> setOf("kann ich handeln", "kann ich traden", "kann man handeln")
    VoiceLanguage.TURKISH -> setOf("trade yapabilir miyim", "işlem yapabilir miyim", "islem yapabilir miyim", "al sat yapabilir miyim")
    VoiceLanguage.ARABIC -> setOf("هل يمكنني التداول", "هل أستطيع التداول", "هل استطيع التداول", "يمكنني التداول")
    VoiceLanguage.YORUBA -> setOf("ṣe mo le ṣowo", "se mo le sowo", "ṣe mo le ra", "se mo le ra")
    VoiceLanguage.HAUSA -> setOf("zan iya ciniki", "zan iya saya", "ina iya ciniki")
    VoiceLanguage.HEBREW -> setOf("האם אני יכול לסחור", "אני יכול לסחור", "אפשר לסחור")
    VoiceLanguage.CHINESE -> setOf("我可以交易吗", "我可以交易", "可以交易吗")
    VoiceLanguage.IGBO -> setOf("enwere m ike ịzụ ahịa", "enwere m ike izu ahia", "m nwere ike ịzụ ahịa", "m nwere ike izu ahia")
}

/**
 * "In" or "on Prognos Labs" in one language. Matched as a plain
 * substring, because every phrase carries the brand name, so a
 * false hit is not possible. Turkish and Hebrew glue the
 * preposition onto the name, so those forms are listed as written.
 */
private fun inPrognosPhrases(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("in prognos", "on prognos")
    VoiceLanguage.FRENCH -> setOf("sur prognos", "dans prognos", "chez prognos")
    VoiceLanguage.SPANISH -> setOf("en prognos")
    VoiceLanguage.PORTUGUESE -> setOf("na prognos", "em prognos", "no prognos")
    VoiceLanguage.GERMAN -> setOf("bei prognos", "auf prognos", "in prognos")
    VoiceLanguage.TURKISH -> setOf("prognos'ta", "prognos’ta", "prognosta", "prognos'te", "prognoste", "prognos labs'ta", "prognos labs’ta", "prognos labs'te", "prognos labs’te")
    VoiceLanguage.ARABIC -> setOf("في prognos", "على prognos", "في بروغنوس", "على بروغنوس")
    VoiceLanguage.YORUBA -> setOf("ni prognos", "lori prognos", "ninu prognos")
    VoiceLanguage.HAUSA -> setOf("a prognos", "cikin prognos")
    VoiceLanguage.HEBREW -> setOf("בprognos", "בפרוגנוס", "על prognos")
    VoiceLanguage.CHINESE -> setOf("在prognos", "在 prognos", "prognos上", "prognos上有")
    VoiceLanguage.IGBO -> setOf("na prognos", "n'ime prognos", "nime prognos")
}

/** The "is" / "are" question starter in one language, for "is X
 * on Prognos Labs". Chinese needs none: "in Prognos" already
 * catches it. */
private fun isAreStarters(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("is ", "are ")
    VoiceLanguage.FRENCH -> setOf("est-ce", "est-il", "est-elle", "est ")
    VoiceLanguage.SPANISH -> setOf("está ", "esta ", "hay ")
    VoiceLanguage.PORTUGUESE -> setOf("está ", "esta ", "há ", "ha ", "tem ")
    VoiceLanguage.GERMAN -> setOf("ist ", "gibt es ")
    VoiceLanguage.TURKISH -> setOf("var mı", "var mi")
    VoiceLanguage.ARABIC -> setOf("هل ")
    VoiceLanguage.YORUBA -> setOf("ṣe ", "se ", "ǹjẹ́ ", "nje ")
    VoiceLanguage.HAUSA -> setOf("shin ", "akwai ")
    VoiceLanguage.HEBREW -> setOf("האם ")
    VoiceLanguage.CHINESE -> emptySet()
    VoiceLanguage.IGBO -> setOf("ọ bụ ", "o bu ", "enwere ")
}

/**
 * "Tell me about" in one language, for the asset question branch:
 * "tell me about Ethereum" names the coin and asks about it. The
 * bare word "about" counts in English ("what about Bitcoin?"); in
 * the other languages only the full phrase counts, so a stray
 * "about" in another language cannot misfire.
 */
private fun tellAboutPhrases(language: VoiceLanguage): Set<String> = when (language) {
    VoiceLanguage.ENGLISH -> setOf("tell me about", "what about", "about")
    VoiceLanguage.SPANISH -> setOf("háblame de", "hablame de", "qué hay de", "que hay de", "cuéntame de", "cuentame de")
    VoiceLanguage.FRENCH -> setOf("parle-moi de", "parle moi de", "dis-moi", "dis moi", "à propos de", "a propos de")
    VoiceLanguage.PORTUGUESE -> setOf("fale-me sobre", "fale me sobre", "me fale sobre", "me conta sobre", "sobre")
    VoiceLanguage.GERMAN -> setOf("erzähl mir über", "erzahl mir uber", "erzähle mir über", "was ist mit")
    VoiceLanguage.TURKISH -> setOf("hakkında anlat", "hakkinda anlat", "hakkında", "hakkinda")
    VoiceLanguage.ARABIC -> setOf("أخبرني عن", "اخبرني عن", "ماذا عن")
    VoiceLanguage.IGBO -> setOf("gwa m maka", "banyere")
    VoiceLanguage.YORUBA -> setOf("sọ fun mi nípa", "so fun mi nipa", "nípa", "nipa")
    VoiceLanguage.HAUSA -> setOf("faɗa mini game da", "fada mini game da", "game da")
    VoiceLanguage.HEBREW -> setOf("ספר לי על", "מה עם")
    VoiceLanguage.CHINESE -> setOf("告诉我", "关于")
}

/**
 * v1.1.26: "Is Bitcoin in Prognos Labs?" and "Can I trade Bitcoin
 * on Prognos Labs?" were heard exactly right and still refused:
 * no branch answers whether an asset is on Prognos Labs, and the
 * price, news and market branches all need their own trigger
 * words. An asset is named and the sentence asks whether it can
 * be traded here or is on Prognos Labs.
 * v1.1.27: the same question in any of the 12 languages. The
 * v1.1.26 branch was English only, so a Portuguese "Posso
 * negociar Bitcoin?" still got the Portuguese refusal: no
 * translated knowledge topic catches a bare "can I trade" either.
 * A "how" question is not availability, it is HowToTrade, so it
 * is excluded here.
 */
private fun isAssetAvailabilityQuestion(
    lower: String,
    mention: Pair<String, Int>?,
    language: VoiceLanguage
): Boolean {
    if (mention == null) return false
    if (lower.contains(howWord(language))) return false
    if (containsAnyWord(lower, tradeWords(language))) return true
    if (inPrognosPhrases(language).any { lower.contains(it) }) return true
    val trimmed = lower.trim()
    if (isAreStarters(language).any { trimmed.startsWith(it) } && lower.contains("prognos")) return true
    return false
}

/** Where the first asset word sits in the text, if any. */
fun assetMention(text: String): Pair<String, Int>? {
    val lower = text.lowercase()
    for ((phrase, code) in ASSET_PHRASES) {
        val at = lower.indexOf(phrase)
        if (at >= 0) return code to at
    }
    val tokens = tokenize(text)
    for (tok in tokens) {
        val code = ASSET_WORDS[tok.word.lowercase()] ?: continue
        return code to tok.start
    }
    // v1.1.28: a misheard coin name must still count. Flint's
    // "Ethereum" came back as "Ecterium" and the app refused the
    // question because no asset was named. A token that is close to
    // a coin name counts: edit distance 3 or less, at least 6
    // letters, same first and last letter, so the first and last
    // sounds the recognizer usually gets right still anchor it.
    // But never a word the parser already understands in any
    // language: "balance" is 2 edits from "binance", and "show my
    // balance" opened a BNB market instead of the dashboard. The
    // best match wins, and a tie is no match at all.
    var bestCode: String? = null
    var bestPos = -1
    var bestDist = Int.MAX_VALUE
    var tied = false
    for (tok in tokens) {
        val word = tok.word.lowercase()
        if (word.length < 6) continue
        if (word in KNOWN_VOICE_WORDS) continue
        for ((key, code) in ASSET_WORDS) {
            if (key.length < 5) continue
            if (word.first() != key.first() || word.last() != key.last()) continue
            val d = levenshtein(word, key)
            if (d <= 3 && d < bestDist) {
                bestDist = d
                bestCode = code
                bestPos = tok.start
                tied = false
            } else if (d <= 3 && d == bestDist && code != bestCode) {
                tied = true
            }
        }
    }
    if (bestCode != null && !tied) return bestCode to bestPos
    return null
}

/**
 * Every word the parser already understands, in every language:
 * greeting, question, trading, navigation and balance words. The
 * fuzzy asset match must never claim one of these as a coin.
 */
private val KNOWN_VOICE_WORDS: Set<String> by lazy {
    val words = mutableSetOf<String>()
    fun addAll(phrases: Iterable<String>) {
        for (phrase in phrases) {
            phrase.lowercase().split(' ', '-', '\'').forEach { w ->
                val clean = w.trim('.', '?', '!', ',', ';', ':')
                if (clean.isNotEmpty()) words += clean
            }
        }
    }
    for (language in VoiceLanguage.values()) {
        val v = vocabFor(language)
        addAll(v.yesWords)
        addAll(v.noWords)
        addAll(v.cancelWords)
        addAll(v.buyVerbs)
        addAll(v.sellVerbs)
        addAll(v.upWords)
        addAll(v.downWords)
        addAll(v.helloPhrases)
        addAll(v.howAreYouPhrases)
        addAll(v.checkInPhrases)
        addAll(v.questionStarters)
        addAll(v.priceWords)
        addAll(v.liquidWords)
        addAll(v.newsWords)
        addAll(v.balanceWords)
        addAll(v.marketWords)
        addAll(v.tradingOpenWords)
        addAll(v.navVerbs)
        addAll(v.navTargets.keys)
        addAll(v.numberWords.keys)
        addAll(v.truncatedTailWords)
        addAll(tellAboutPhrases(language))
    }
    words
}

/** Plain edit distance between two words, for the fuzzy asset
 * match above. Small and pure, so the unit tests can pin it. */
fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev
        prev = cur
        cur = tmp
    }
    return prev[b.length]
}

/**
 * Fixes the usual mishearings of the trading words before parsing,
 * so one wrong sound does not lose the whole order. Each language
 * brings its own mishearings from its vocab; the English start-of
 * -sentence fix ("by 50 yes" is "buy 50 yes", "cell 20 no" is
 * "sell 20 no") stays English-only, and only when the next word
 * is an amount or a side, so "by when does trading open" is left
 * alone. Compound number words are split first: German
 * "einundzwanzig" is 21, Spanish "veinticinco" is 25, and the
 * Arabic "and" prefix و stuck to a number word ("وخمسون" is
 * "and fifty") is split only when the rest is a number word.
 */
fun normalizeHeard(text: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): String {
    var t = text.trim()
    // v1.1.15: the recognizer writes a curly apostrophe ("What’s");
    // the matchers use a straight one. Normalise it first, so
    // "what's" matches in every branch that checks for it.
    t = t.replace('’', '\'').replace('‘', '\'')
    if (language == VoiceLanguage.GERMAN) {
        t = t.replace(
            Regex("(ein|zwei|drei|vier|fünf|sechs|sieben|acht|neun)und(zwanzig|dreißig|vierzig|fünfzig|sechzig|siebzig|achtzig|neunzig)", RegexOption.IGNORE_CASE),
            "$1 und $2"
        )
        t = t.replace(
            Regex("(ein|zwei|drei|vier|fünf|sechs|sieben|acht|neun)(hundert|tausend)", RegexOption.IGNORE_CASE),
            "$1 $2"
        )
    }
    if (language == VoiceLanguage.SPANISH) {
        t = t.replace(
            Regex("veinti(uno|dos|dós|tres|trés|cuatro|cinco|seis|siete|ocho|nueve)", RegexOption.IGNORE_CASE),
            "veinte y $1"
        )
    }
    if (language == VoiceLanguage.ARABIC) {
        // "بعشرة دولارات" (for ten dollars): the بـ prefix on a
        // number word is the preposition, not part of the number.
        // Strip it so the amount reader sees the number word.
        val arabicNums = vocabFor(language).numberWords.keys
        t = t.split(" ").joinToString(" ") { tok ->
            if (tok.length > 1 && tok.startsWith("ب") && tok.drop(1) in arabicNums) tok.drop(1) else tok
        }
        val nums = vocabFor(language).numberWords.keys
            .filter { it.length > 1 }.sortedByDescending { it.length }
        for (n in nums) {
            t = t.replace(Regex("(^|\\s)و" + Regex.escape(n)), "$1 $n")
        }
    }
    val vocab = vocabFor(language)
    for ((re, rep) in vocab.mishearings) t = re.replace(t, rep)
    if (language != VoiceLanguage.ENGLISH) return t
    // "by 50 yes..." and "cell 20 no..." at the start of a sentence:
    // the trade verbs, misheard. Only when the next word is an amount
    // or a side, so "by when does trading open" is left alone.
    val amountOrSide = Regex(
        "^(fifty|sixty|seventy|eighty|ninety|twenty|thirty|forty|ten|one|two|three|four|five|six|seven|eight|nine|eleven|twelve|hundred|thousand|\\d|yes|no|up|down|a\\b)",
        RegexOption.IGNORE_CASE
    )
    val first = t.trimStart()
    val startVerb = when {
        first.startsWith("by ", ignoreCase = true) || first.equals("by", ignoreCase = true) -> "by" to 2
        first.startsWith("bye ", ignoreCase = true) || first.equals("bye", ignoreCase = true) -> "bye" to 3
        first.startsWith("cell ", ignoreCase = true) || first.equals("cell", ignoreCase = true) -> "cell" to 4
        else -> null
    }
    if (startVerb != null) {
        val rest = first.substring(startVerb.second).trimStart()
        if (amountOrSide.containsMatchIn(rest)) {
            t = if (startVerb.first == "cell") "sell $rest" else "buy $rest"
        }
    }
    return t
}

/** How much a parse is worth when several recognition candidates
 * compete: an order beats a question, a question beats an
 * ask-again, and an ask-again beats a refusal. Shared by the
 * single-language and the multi-language scorers, so the two
 * cannot drift apart. */
internal fun parseScore(parsed: VoiceParseResult): Int = when (parsed) {
    is VoiceParseResult.Order -> 3
    is VoiceParseResult.Question -> 2
    is VoiceParseResult.Navigate -> 2
    is VoiceParseResult.AskAgain -> 1
    is VoiceParseResult.OutOfScope -> 0
}

/**
 * Scores every recognition candidate against what the app can
 * actually do, and takes the best parse instead of trusting the top
 * result blindly. An order beats a question, a question beats an
 * ask-again, and an ask-again beats a refusal; the recognizer order
 * breaks every tie, so the top result still wins when two candidates
 * parse equally well. Returns the winning parse and the (normalized)
 * words it came from, so the history and the voice check use what the
 * app understood, not a candidate it threw away. Never guesses: when
 * no candidate parses as anything, the answer is AskAgain.
 */
fun parseBestVoiceUtterance(
    candidates: List<String>,
    markets: List<Market>,
    currentMarketId: String? = null,
    language: VoiceLanguage = VoiceLanguage.ENGLISH
): Pair<VoiceParseResult, String> {
    var best: VoiceParseResult? = null
    var bestScore = -1
    var bestText = ""
    for (candidate in candidates) {
        val norm = normalizeHeard(candidate, language)
        val parsed = parseVoiceUtterance(norm, markets, currentMarketId, language)
        val score = parseScore(parsed)
        if (score > bestScore) {
            bestScore = score
            best = parsed
            bestText = norm
        }
    }
    if (bestScore <= 0) {
        // Nothing parsed as an order or a question. When none of the
        // candidates even looks like a question, this is noise the
        // recognizer misheard, not a question to refuse: ask the user
        // to repeat instead of guessing or refusing. A real
        // out-of-scope question ("what is the weather") keeps its
        // refusal.
        val questionWords = vocabFor(language).questionStarters
        val anyQuestionLike = candidates.any { c ->
            val l = normalizeHeard(c, language).lowercase()
            questionWords.any { l.contains(it) }
        }
        if (!anyQuestionLike) {
            return VoiceParseResult.AskAgain(vocabFor(language).askAgain) to bestText
        }
    }
    return (best ?: VoiceParseResult.AskAgain(vocabFor(language).askAgain)) to bestText
}

/**
 * v1.1.28: the same scoring, but every candidate is parsed in its
 * own language. The cloud recognizer can report two transcripts
 * for one turn: the auto-detected one, and a retry forced to the
 * language set in Settings, when the two disagree. Flint's
 * accented English was detected as Portuguese, so the auto
 * transcript was Portuguese garbage and the forced English one
 * was the real sentence; parsing each in its own language lets
 * the better parse win instead of trusting the detector. The
 * winner's language comes back too, so the answer is spoken in
 * the language the winning words were actually in. The recognizer
 * order still breaks ties, so a genuine other-language turn wins
 * its tie against the forced retry.
 */
fun parseBestVoiceUtteranceMulti(
    candidates: List<Pair<String, VoiceLanguage>>,
    markets: List<Market>,
    currentMarketId: String? = null
): Triple<VoiceParseResult, String, VoiceLanguage> {
    var best: VoiceParseResult? = null
    var bestScore = -1
    var bestText = ""
    var bestLanguage = candidates.firstOrNull()?.second ?: VoiceLanguage.ENGLISH
    for ((candidate, language) in candidates) {
        val norm = normalizeHeard(candidate, language)
        val parsed = parseVoiceUtterance(norm, markets, currentMarketId, language)
        val score = parseScore(parsed)
        if (score > bestScore) {
            bestScore = score
            best = parsed
            bestText = norm
            bestLanguage = language
        }
    }
    if (bestScore <= 0) {
        val firstLanguage = candidates.firstOrNull()?.second ?: VoiceLanguage.ENGLISH
        val questionWords = vocabFor(firstLanguage).questionStarters
        val anyQuestionLike = candidates.any { (c, language) ->
            val l = normalizeHeard(c, language).lowercase()
            questionWords.any { l.contains(it) }
        }
        if (!anyQuestionLike) {
            return Triple(
                VoiceParseResult.AskAgain(vocabFor(firstLanguage).askAgain),
                bestText,
                bestLanguage
            )
        }
    }
    return Triple(
        best ?: VoiceParseResult.AskAgain(vocabFor(bestLanguage).askAgain),
        bestText,
        bestLanguage
    )
}
fun formatVoiceAmount(v: Double): String {
    if (v == v.toLong().toDouble()) return v.toLong().toString()
    return v.toString().trimEnd('0').trimEnd('.')
}

/** Short plain label for the confirm card, e.g. "BTC close above $90k". */
fun marketShortLabel(market: Market): String =
    market.question.removePrefix("Will ").removeSuffix("?").trim()

private fun numberAppearsIn(question: String, value: Double): Boolean {
    val q = question.lowercase()
    val candidates = mutableListOf<String>()
    if (value == value.toLong().toDouble()) {
        val whole = value.toLong().toString()
        candidates.add(whole)
        candidates.add(whole.replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,"))
        if (value >= 1000) {
            val k = value / 1000
            candidates.add("${if (k == k.toLong().toDouble()) k.toLong().toString() else k.toString()}k")
        }
        candidates.add("$$whole")
    }
    candidates.add(value.toString())
    // v1.1.29: a market question carries its window as a time
    // ("6:35PM-6:40PM"), and a bare substring match reads the
    // minutes as a price: "trade Bitcoin below 35" matched the 35
    // in "6:35PM", so the amount was thrown away as part of the
    // market. Times are never prices: strip them first.
    val qNoTimes = q.replace(Regex("\\d{1,2}:\\d{2}(\\s?[ap]m)?"), "")
    return candidates.filter { it.isNotBlank() }.any { qNoTimes.contains(it) }
}

/**
 * Parses a spoken sentence against the live markets. [currentMarketId]
 * is the market open on screen, if any: when the user says "buy 50
 * Yes" with no coin named, the open market is the context, not a
 * guess. The amount is the first number before the coin is named;
 * numbers after the coin ("above 90k") belong to the market, not the
 * amount.
 */
fun parseVoiceUtterance(
    text: String,
    markets: List<Market>,
    currentMarketId: String? = null,
    language: VoiceLanguage = VoiceLanguage.ENGLISH
): VoiceParseResult {
    val vocab = vocabFor(language)
    val clean = normalizeHeard(text, language).trim()
    if (clean.isEmpty()) return VoiceParseResult.AskAgain(vocab.askAgain)
    val lower = clean.lowercase()

    val startsQuestion = vocab.questionStarters.any { lower.startsWith(it) }
    // v1.1.29: "trade" is an order verb too. "Trade Bitcoin for me
    // below 35" fell to the question path, because "trade" is in
    // neither the buy nor the sell verbs, and the asset-availability
    // branch answered it with the market info instead of starting
    // a trade. The price the user named was never used at all.
    val hasTradeVerb = containsAnyWord(lower, vocab.buyVerbs) ||
        containsAnyWord(lower, vocab.sellVerbs) ||
        containsAnyWord(lower, tradeWords(language))

    if (startsQuestion || !hasTradeVerb) return parseVoiceQuestion(clean, markets, currentMarketId, language)

    // ---- Order path ----
    // "Sell half" / "sell all": a fraction of the balance, never a
    // dollar amount. Ask for the amount instead of guessing
    // against the balance. This needs no market at all, so it runs
    // before the market lookup, as it did before v1.1.29 moved the
    // lookup up for the price limit.
    if (containsAnyWord(lower, fractionWords(language))) {
        return VoiceParseResult.AskAgain(vocab.askAmountFraction)
    }

    // The market first: the price limit below needs the candidates
    // to tell a limit from a market's own number ("above 90k" is
    // the market, "below 35" is a limit), and the side comes after
    // the limit, so a "below" that belongs to the limit never
    // counts as a down direction.
    val mention = assetMention(clean)
    val asset = mention?.first
    val assetPos = mention?.second
    val candidates: List<Market> = when {
        asset != null -> markets.filter { it.asset == asset }
        currentMarketId != null -> markets.filter { it.id == currentMarketId }
        else -> emptyList()
    }

    // v1.1.29: a named price limit ("trade Bitcoin below 35") is a
    // price, not an amount and not a direction. The number reads as
    // an outcome price (35 is 0.35 on a Yes/No market); a number
    // that belongs to a market's own question ("above 90k") is
    // never a limit.
    val limit = findPriceLimit(lower, candidates, language)

    // The side comes from the outcome word first (Yes is up, No is
    // down), then from the verb. Whole-word matching, so "no" never
    // fires on "know" and Turkish "al" never fires on "saldo".
    // Words inside the limit phrase never count: "below" in "below
    // 35" is the limit, not a down direction. The side stays
    // unknown here when nothing names it; the limit can still
    // settle it from the prices, and otherwise the app asks.
    val tokens = tokenize(lower).filter { tok ->
        limit == null || tok.start < limit.start || tok.end > limit.end
    }.map { it.word.lowercase() }
    val side: TradeSide? = when {
        vocab.downWords.any { it in tokens || (it.contains(' ') && lower.contains(it)) } -> TradeSide.DOWN
        vocab.upWords.any { it in tokens || (it.contains(' ') && lower.contains(it)) } -> TradeSide.UP
        containsAnyWord(lower, vocab.sellVerbs) -> TradeSide.DOWN
        containsAnyWord(lower, vocab.buyVerbs) -> TradeSide.UP
        else -> null
    }

    // v1.1.29: the trade verb alone does not make an order. "Can I
    // trade Bitcoin" ("Posso negociar Bitcoin?", "Puis-je trader
    // le Bitcoin ?") names no price, no amount and no side, so it
    // is the availability question, and the question path answers
    // it. An order always names a price, an amount or a side. A
    // follow-up with no asset ("trade that one") is different: the
    // market is the one already on screen, so with a current market
    // it stays on the order path and the app asks up or down.
    if (limit == null && side == null &&
        spokenNumbers(clean, language).isEmpty() &&
        (mention != null || currentMarketId == null)
    ) {
        return parseVoiceQuestion(clean, markets, currentMarketId, language)
    }

    if (candidates.isEmpty()) {
        return if (asset == null) {
            VoiceParseResult.AskAgain(vocab.askMarket)
        } else {
            VoiceParseResult.AskAgain("I could not find a $asset market open right now. Say it again.")
        }
    }

    // A price limit settles the market and the side before the
    // amount is read. When no market is at the price yet, the
    // answer is the current prices against the limit, never a
    // silent trade above it. The limit number itself never becomes
    // the amount.
    var limitMarket: Market? = null
    var limitSide: TradeSide? = null
    if (limit != null) {
        val limitSides = when (side) {
            TradeSide.UP -> listOf(TradeSide.UP)
            TradeSide.DOWN -> listOf(TradeSide.DOWN)
            null -> listOf(TradeSide.UP, TradeSide.DOWN)
        }
        fun meets(m: Market, s: TradeSide): Boolean {
            val p = if (s == TradeSide.UP) m.p0 else m.p1
            return p != null && if (limit.below) p <= limit.price else p >= limit.price
        }
        val meeting = candidates.filter { m -> limitSides.any { meets(m, it) } }
        if (meeting.isEmpty()) {
            val m = candidates[0]
            val yes = m.p0?.let { formatOutcomePrice(it) } ?: "-"
            val no = m.p1?.let { formatOutcomePrice(it) } ?: "-"
            val marketLine = fill(vocab.answerMarket, "question" to m.question, "yes" to yes, "no" to no)
            val limitLine = fill(
                if (limit.below) vocab.answerLimitBelow else vocab.answerLimitAbove,
                "limit" to formatOutcomePrice(limit.price)
            )
            return VoiceParseResult.Question(VoiceQuestion.Knowledge("$marketLine $limitLine"), text)
        }
        limitMarket = meeting[0]
        limitSide = side ?: when {
            meets(limitMarket, TradeSide.UP) && !meets(limitMarket, TradeSide.DOWN) -> TradeSide.UP
            meets(limitMarket, TradeSide.DOWN) && !meets(limitMarket, TradeSide.UP) -> TradeSide.DOWN
            else -> return VoiceParseResult.AskAgain("I did not catch whether to buy up or sell down. Say it again.")
        }
    }

    val resolvedSide = side ?: limitSide
        ?: return VoiceParseResult.AskAgain("I did not catch whether to buy up or sell down. Say it again.")

    val market: Market = limitMarket ?: if (candidates.size == 1) {
        candidates[0]
    } else {
        // More than one market for the coin: use the price or direction
        // the user named ("above 90k"). A tie is never guessed.
        val numbers = spokenNumbers(clean)
        val afterAsset = numbers.filter { assetPos == null || it.start > assetPos }
        var best: Market? = null
        var bestScore = 0
        var tied = false
        for (m in candidates) {
            var score = 0
            for (n in afterAsset) if (numberAppearsIn(m.question, n.value)) score += 2
            if ((lower.contains("above") || lower.contains("over")) && m.question.lowercase().contains("above")) score += 1
            if ((lower.contains("below") || lower.contains("under")) && m.question.lowercase().contains("below")) score += 1
            if (score > bestScore) {
                bestScore = score
                best = m
                tied = false
            } else if (score == bestScore && score > 0) {
                tied = true
            }
        }
        if (best == null || bestScore == 0 || tied) {
            return VoiceParseResult.AskAgain("I heard more than one $asset market. Say the price, for example above 90k.")
        }
        best
    }

    // The amount is the first number before the coin is named. When the
    // market came from the open screen (no coin named), it is the first
    // number anywhere. Otherwise a number after the coin counts as the
    // amount only when it is not part of the market's price: "buy
    // bitcoin 50" is $50, "buy bitcoin above 90k" is not $90,000.
    // A price limit's number never counts as the amount either:
    // "below 35" is the price, not $35.
    val numbers = spokenNumbers(clean, language).filter { n ->
        limit == null || n.start != limit.numberStart
    }
    val amountNumber = numbers.firstOrNull { assetPos == null || it.end <= assetPos }
        ?: numbers.firstOrNull { n -> candidates.none { numberAppearsIn(it.question, n.value) } }
        ?: return VoiceParseResult.AskAgain(vocab.askAmount)
    val amount = amountNumber.value
    val amountErr = orderAmountError(formatVoiceAmount(amount))
    if (amountErr != null) return VoiceParseResult.AskAgain(amountErr)
    if (amount <= 0) return VoiceParseResult.AskAgain(vocab.askAmount)

    val sideWord = if (resolvedSide == TradeSide.UP) "Yes" else "No"
    val verb = if (resolvedSide == TradeSide.UP) "Buy" else "Sell"
    val readback = "$verb ${formatVoiceAmount(amount)} $sideWord on ${marketShortLabel(market)}"
    return VoiceParseResult.Order(
        VoiceOrder(
            marketId = market.id,
            marketLabel = marketShortLabel(market),
            side = resolvedSide,
            amountUsd = amount,
            readback = readback
        )
    )
}

private fun parseVoiceQuestion(
    text: String,
    markets: List<Market>,
    currentMarketId: String?,
    language: VoiceLanguage = VoiceLanguage.ENGLISH
): VoiceParseResult {
    val vocab = vocabFor(language)
    val lower = text.lowercase()
    val mention = assetMention(text)
    val currentMarket = currentMarketId?.let { id -> markets.find { it.id == id } }
    val mentionedMarket = mention?.first?.let { code -> markets.firstOrNull { it.asset == code } }

    // Greetings are split: a plain hello gets a plain hello back,
    // "how are you" gets its own answer. A plain "hello" never gets
    // a "how are you" reply. Both are conversational turns: the
    // voice answers and keeps listening for the follow-up. Short
    // greeting words only match at the start, so a sentence that
    // happens to contain one is not mistaken for a greeting.
    // v1.1.15: the recognizer punctuates ("Hello."), so a plain
    // hello never equalled "hello" and fell through to "say it
    // again" instead of being answered. Short greeting words are
    // matched on the first word, punctuation stripped, so "Hello."
    // and "Hello. Thank you." both greet.
    // v1.1.25: a greeting in any language counts, even when the
    // language was not detected and the Settings language is
    // English: "bonjour" is a greeting, not a word to refuse. The
    // answer still comes back in the turn language, so a detected
    // French greeting gets a French answer.
    val trimmed = lower.trim().trimEnd('.', '?', '!', ',')
    val firstWord = trimmed.split(Regex("\\s+")).firstOrNull()
        ?.trimEnd('.', '?', '!', ',')
    val howAreYou = vocab.howAreYouPhrases.any { lower.contains(it) }
    if (howAreYou) return VoiceParseResult.Question(VoiceQuestion.GreetingHowAreYou, text)
    val helloAnyLanguage = HELLO_WORDS_ANY_LANGUAGE.any { word ->
        if (word.length <= 5) firstWord == word
        else lower.contains(word)
    }
    val hello = helloAnyLanguage || vocab.helloPhrases.any { phrase ->
        if (phrase.length <= 5) firstWord == phrase
        else lower.contains(phrase)
    }
    // A hello that continues into a question is the question, not
    // the greeting. v1.1.28: "Good morning. Tell me about Prognos
    // Labs." was answered with a greeting and the question was
    // never answered, because the greeting branch fired first and
    // the rest of the sentence was thrown away. Strip the greeting
    // and parse the rest: a question, an order or a navigation in
    // the tail wins. An unclear tail ("hey how are") still asks
    // the user to repeat the whole thing. A tail that is not a
    // question at all ("hello, thank you") is a pleasantry, so the
    // greeting stands and the session stays open.
    // What follows the greeting, with the greeting itself
    // stripped: "good morning. tell me about X" leaves "tell me
    // about X", not "morning. tell me about X", which is what the
    // old first-space split left behind. Multi-word greetings are
    // tried longest first, and the match must end on a word
    // boundary, so "oil price" never strips "oi". "Hi there" is
    // stripped whole, so it still greets instead of asking again
    // about the leftover word "there".
    val afterHello = run {
        val phrases = (vocab.helloPhrases + HELLO_WORDS_ANY_LANGUAGE +
            setOf("hi there", "hello there", "hey there"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
        for (phrase in phrases) {
            if (trimmed.startsWith(phrase)) {
                val after = trimmed.substring(phrase.length)
                if (after.isEmpty() || after.first() in " .?!,;:") {
                    return@run after.trimStart(' ', '.', '?', '!', ',', ';', ':')
                }
            }
        }
        trimmed.substringAfter(" ", "").trimStart()
    }
    if (hello) {
        val rest = afterHello.trim()
        if (rest.isNotEmpty()) {
            val reparsed = parseVoiceUtterance(rest, markets, currentMarketId, language)
            when (reparsed) {
                is VoiceParseResult.Question, is VoiceParseResult.Order,
                is VoiceParseResult.Navigate -> return reparsed
                is VoiceParseResult.AskAgain -> {
                    // Only when the tail really looks like a question
                    // cut off halfway ("hey how are"). A pleasantry
                    // ("hello, thank you") greets instead of asking
                    // the user to repeat a thank-you.
                    if (vocab.questionStarters.any { rest.startsWith(it) }) return reparsed
                }
                else -> {}
            }
        }
        return VoiceParseResult.Question(VoiceQuestion.GreetingHello, text)
    }

    // v1.1.15: "Can you hear me?" / "Are you listening?" is the
    // user checking the mic is live. It is not an off-topic
    // question: refusing it is what the video showed, and it reads
    // as the voice not working at all.
    if (vocab.checkInPhrases.any { lower.contains(it) }) {
        return VoiceParseResult.Question(VoiceQuestion.CheckIn, text)
    }

    // The most liquid market on Prognos Labs.
    if (containsAnyWord(lower, vocab.liquidWords)) return VoiceParseResult.Question(VoiceQuestion.MostLiquid, text)

    // The latest news about a coin.
    if (containsAnyWord(lower, vocab.newsWords)) {
        val asset = mention?.first ?: currentMarket?.asset
        if (asset != null) return VoiceParseResult.Question(VoiceQuestion.News(asset), text)
        return VoiceParseResult.AskAgain(vocab.askNews)
    }

    // Navigation: the voice does things in the app, it does not only
    // answer. Checked before the question branches, so "show my
    // balance" opens the dashboard instead of reading the balance
    // out loud. A plain question ("what is my balance") has no
    // open/show verb, so it still gets a spoken answer.
    val nav = parseNavigation(lower, markets, language)
    if (nav != null) return nav

    if (isPortfolioQuestion(lower, language)) {
        return VoiceParseResult.Navigate("portfolio", "your portfolio")
    }

    when {
        containsAnyWord(lower, vocab.balanceWords) -> return VoiceParseResult.Question(VoiceQuestion.Balance, text)
        containsAnyWord(lower, vocab.priceWords) -> {
            val asset = mention?.first ?: currentMarket?.asset
            if (asset != null) return VoiceParseResult.Question(VoiceQuestion.Price(asset), text)
            return VoiceParseResult.AskAgain(vocab.askPrice)
        }
        isAssetAvailabilityQuestion(lower, mention, language) -> {
            // v1.1.26: an asset-availability question is a trading
            // question, not a refusal. A matching loaded market
            // answers with its market info; with no market loaded
            // the answer is a plain yes naming the asset.
            val m = mentionedMarket
            if (m != null) return VoiceParseResult.Question(VoiceQuestion.MarketInfo(m.id), text)
            return VoiceParseResult.Question(
                VoiceQuestion.Knowledge(assetAvailableAnswer(mention!!.first, language)),
                text
            )
        }
        (lower.contains(howWord(language)) || containsAnyWord(lower, canTradePhrases(language))) &&
            (containsAnyWord(lower, vocab.buyVerbs) || containsAnyWord(lower, vocab.sellVerbs) ||
                containsAnyWord(lower, tradeWords(language))) ->
            return VoiceParseResult.Question(VoiceQuestion.HowToTrade, text)
        containsAnyWord(lower, vocab.tradingOpenWords) ->
            return VoiceParseResult.Question(VoiceQuestion.TradingOpens, text)
        containsAnyWord(lower, vocab.marketWords) -> {
            val m = mentionedMarket ?: currentMarket
            if (m != null) return VoiceParseResult.Question(VoiceQuestion.MarketInfo(m.id), text)
            return VoiceParseResult.AskAgain(vocab.askMarket)
        }
    }
    // v1.1.28: "Tell me about Ethereum" is an asset question, not
    // a refusal. It has no price word and no trading word, so it
    // fell past every branch above and was refused as out of scope,
    // and the misheard "Ecterium" never even named the asset. With
    // a market loaded the answer is the market; without one it is
    // the price, which the ticker carries either way.
    if (mention != null && containsAnyWord(lower, tellAboutPhrases(language))) {
        val m = mentionedMarket
        if (m != null) return VoiceParseResult.Question(VoiceQuestion.MarketInfo(m.id), text)
        return VoiceParseResult.Question(VoiceQuestion.Price(mention.first), text)
    }
    // Platform knowledge: what Prognos Labs is, how the markets and
    // the forecasting work, wallets, deposits, launch, fees, points.
    // Anything else is out of scope; the voice never invents an answer.
    // Launch words are deliberately not trading-open words, so "when
    // is the launch" reaches this branch and is answered with the
    // dates instead of a bare trading-open line.
    val known = platformAnswer(lower, language)
    if (known != null) return VoiceParseResult.Question(VoiceQuestion.Knowledge(known), text)
    // The recognizer finalized mid-sentence: a fragment like "how
    // are" is not a question to refuse, it is half a sentence. Ask
    // the user to repeat instead of answering out of scope. A
    // complete question ("what is the weather") is not truncated,
    // so it still gets its out-of-scope answer.
    if (looksTruncated(lower, language)) {
        return VoiceParseResult.AskAgain("I did not catch the whole thing. Say it again.")
    }
    return VoiceParseResult.OutOfScope(vocab.outOfScopeAnswer)
}


/** True when the final transcript looks cut off mid-sentence:
 * fewer than three words, or ending on a word a sentence never
 * ends on. */
internal fun looksTruncated(lower: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): Boolean {
    val words = lower.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size < 3) return true
    val last = words.last().trimEnd('?', '.', ',', '!')
    return last in vocabFor(language).truncatedTailWords
}

/** True when the user is asking to see the portfolio, in any
 * supported language. "Can I see my portfolio" never reached
 * navigation, because "see" is not a navigation verb, so it was
 * refused as out of scope. */
private fun isPortfolioQuestion(lower: String, language: VoiceLanguage): Boolean {
    val phrases = when (language) {
        VoiceLanguage.ENGLISH -> setOf(
            "can i see my portfolio",
            "show my portfolio",
            "what is my portfolio",
            "what's my portfolio",
            "whats my portfolio",
            "what is in my portfolio",
            "what's in my portfolio",
            "my portfolio"
        )
        VoiceLanguage.SPANISH -> setOf(
            "puedo ver mi cartera",
            "ver mi cartera",
            "mi cartera",
            "mis posiciones",
            "ver mis posiciones"
        )
        VoiceLanguage.FRENCH -> setOf(
            "voir mon portefeuille",
            "mon portefeuille",
            "mes positions",
            "voir mes positions"
        )
        VoiceLanguage.PORTUGUESE -> setOf(
            "ver minha carteira",
            "minha carteira",
            "minhas posições",
            "minhas posicoes"
        )
        VoiceLanguage.GERMAN -> setOf(
            "mein portfolio",
            "meine positionen",
            "portfolio zeigen"
        )
        VoiceLanguage.TURKISH -> setOf(
            "portföyüm",
            "portfoyum",
            "pozisyonlarım",
            "pozisyonlarim"
        )
        VoiceLanguage.ARABIC -> setOf(
            "محفظتي",
            "مراكزي"
        )
        VoiceLanguage.IGBO -> setOf(
            "portfolio m",
            "akpa ego m"
        )
        VoiceLanguage.YORUBA -> setOf(
            "portfolio mi",
            "apamọwọ mi",
            "apamowo mi"
        )
        VoiceLanguage.HAUSA -> setOf(
            "portfolio dina",
            "walat dina"
        )
        VoiceLanguage.HEBREW -> setOf(
            "התיק שלי",
            "הפוזיציות שלי"
        )
        VoiceLanguage.CHINESE -> setOf(
            "我的投资组合",
            "我的持仓",
            "投资组合"
        )
    }
    return phrases.any { lower.contains(it) }
}

/**
 * Voice navigation: "open Bitcoin" opens the highest-volume
 * Bitcoin market; "show my balance" opens the dashboard; "go to
 * markets" opens the markets tab; "go to wallet" opens the wallet.
 * Returns null when the sentence is not a navigation command.
 */
internal fun parseNavigation(lower: String, markets: List<Market>, language: VoiceLanguage = VoiceLanguage.ENGLISH): VoiceParseResult? {
    val vocab = vocabFor(language)
    val wantsOpen = vocab.navVerbs.any { verb ->
        lower.startsWith(verb) || lower.startsWith("$verb ") || lower.contains(" $verb ")
    }
    if (!wantsOpen) return null
    // A named coin wins over a bare screen word: "show me the
    // ethereum market" opens the ETH market, not the markets list.
    // A plain "open markets" has no coin in it, so it still falls
    // through to the screen targets below.
    val code = assetMention(lower)?.first
    if (code != null) {
        val candidates = markets.filter { it.asset == code }
        val best = candidates.maxByOrNull { parseNum(it.vol24) ?: parseNum(it.volume) ?: 0.0 }
        if (best == null) {
            return VoiceParseResult.AskAgain("I could not find a $code market open right now. Say it again.")
        }
        return VoiceParseResult.Navigate("market/${best.id}", marketShortLabel(best))
    }
    for ((target, route) in vocab.navTargets) {
        val hit = if (target.contains(' ')) lower.contains(target) else containsWord(lower, target)
        if (!hit) continue
        // The label is what the voice says it opened. In English it
        // keeps the old plain labels; in other languages it is the
        // word the user said.
        val label = if (language == VoiceLanguage.ENGLISH) when (route) {
            "dashboard" -> if (target == "balance") "your balance" else "the dashboard"
            "markets" -> "the markets"
            "wallet" -> "your wallet"
            "portfolio" -> "your portfolio"
            else -> target
        } else target
        return VoiceParseResult.Navigate(route, label)
    }
    return null
}

/**
 * The read-back confirmation, pure: yes, no, or unclear. Used by
 * the Confirm sheet when the user answers out loud instead of
 * tapping. "Correct", "go ahead", "not that one" and "cancel" all
 * count, in every supported language. A cancel always wins over a
 * yes, and an unclear answer never confirms an order.
 */
enum class VoiceConfirmation { YES, NO, UNCLEAR }

fun parseConfirmation(text: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): VoiceConfirmation {
    val vocab = vocabFor(language)
    val lower = normalizeHeard(text, language).lowercase()
    if (containsAnyWord(lower, vocab.cancelWords)) return VoiceConfirmation.NO
    if (containsAnyWord(lower, vocab.noWords)) return VoiceConfirmation.NO
    if (containsAnyWord(lower, vocab.yesWords)) return VoiceConfirmation.YES
    return VoiceConfirmation.UNCLEAR
}
