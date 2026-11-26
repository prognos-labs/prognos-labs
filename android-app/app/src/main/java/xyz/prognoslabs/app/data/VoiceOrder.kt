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

/** The answer when no market for the asset is loaded: a plain yes that names the asset. */
fun assetAvailableAnswer(asset: String): String {
    val name = ASSET_LABELS[asset] ?: asset
    return "Yes, $name can be traded on Prognos Labs."
}

/**
 * v1.1.26: "Is Bitcoin in Prognos Labs?" and "Can I trade Bitcoin
 * on Prognos Labs?" were heard exactly right and still refused:
 * no branch answers whether an asset is on Prognos Labs, and the
 * price, news and market branches all need their own trigger
 * words. An asset is named and the sentence asks whether it can
 * be traded here or is on Prognos Labs. English only for now; the
 * other languages keep their current behaviour. A "how" question
 * is not availability, it is HowToTrade, so it is excluded here.
 */
private fun isAssetAvailabilityQuestion(
    lower: String,
    mention: Pair<String, Int>?,
    language: VoiceLanguage
): Boolean {
    if (language != VoiceLanguage.ENGLISH) return false
    if (mention == null) return false
    if (lower.contains(howWord(language))) return false
    if (lower.contains("trade") || lower.contains("trading")) return true
    if (lower.contains("in prognos") || lower.contains("on prognos")) return true
    val trimmed = lower.trim()
    if ((trimmed.startsWith("is ") || trimmed.startsWith("are ")) && lower.contains("prognos")) return true
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
    return null
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
        val score = when (parsed) {
            is VoiceParseResult.Order -> 3
            is VoiceParseResult.Question -> 2
            is VoiceParseResult.Navigate -> 2
            is VoiceParseResult.AskAgain -> 1
            is VoiceParseResult.OutOfScope -> 0
        }
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
            return VoiceParseResult.AskAgain("I did not catch that. Say it again.") to bestText
        }
    }
    return (best ?: VoiceParseResult.AskAgain("I did not catch that. Say it again.")) to bestText
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
    return candidates.filter { it.isNotBlank() }.any { q.contains(it) }
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
    if (clean.isEmpty()) return VoiceParseResult.AskAgain("I did not catch that. Say it again.")
    val lower = clean.lowercase()

    val startsQuestion = vocab.questionStarters.any { lower.startsWith(it) }
    val hasTradeVerb = containsAnyWord(lower, vocab.buyVerbs) || containsAnyWord(lower, vocab.sellVerbs)

    if (startsQuestion || !hasTradeVerb) return parseVoiceQuestion(clean, markets, currentMarketId, language)

    // ---- Order path ----
    // The side comes from the outcome word first (Yes is up, No is
    // down), then from the verb. Whole-word matching, so "no" never
    // fires on "know" and Turkish "al" never fires on "saldo".
    val tokens = tokenize(lower).map { it.word.lowercase() }
    val side: TradeSide = when {
        vocab.downWords.any { it in tokens || (it.contains(' ') && lower.contains(it)) } -> TradeSide.DOWN
        vocab.upWords.any { it in tokens || (it.contains(' ') && lower.contains(it)) } -> TradeSide.UP
        containsAnyWord(lower, vocab.sellVerbs) -> TradeSide.DOWN
        containsAnyWord(lower, vocab.buyVerbs) -> TradeSide.UP
        else -> return VoiceParseResult.AskAgain("I did not catch whether to buy up or sell down. Say it again.")
    }

    // "Sell half" / "sell all": a fraction of the balance, never a
    // dollar amount. Ask for the amount instead of guessing
    // against the balance.
    if (containsAnyWord(lower, fractionWords(language))) {
        return VoiceParseResult.AskAgain(vocab.askAmountFraction)
    }

    val mention = assetMention(clean)
    val asset = mention?.first
    val assetPos = mention?.second
    val candidates: List<Market> = when {
        asset != null -> markets.filter { it.asset == asset }
        currentMarketId != null -> markets.filter { it.id == currentMarketId }
        else -> emptyList()
    }
    if (candidates.isEmpty()) {
        return if (asset == null) {
            VoiceParseResult.AskAgain(vocab.askMarket)
        } else {
            VoiceParseResult.AskAgain("I could not find a $asset market open right now. Say it again.")
        }
    }
    val market: Market = if (candidates.size == 1) {
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
    val numbers = spokenNumbers(clean, language)
    val amountNumber = numbers.firstOrNull { assetPos == null || it.end <= assetPos }
        ?: numbers.firstOrNull { n -> candidates.none { numberAppearsIn(it.question, n.value) } }
        ?: return VoiceParseResult.AskAgain(vocab.askAmount)
    val amount = amountNumber.value
    val amountErr = orderAmountError(formatVoiceAmount(amount))
    if (amountErr != null) return VoiceParseResult.AskAgain(amountErr)
    if (amount <= 0) return VoiceParseResult.AskAgain(vocab.askAmount)

    val sideWord = if (side == TradeSide.UP) "Yes" else "No"
    val verb = if (side == TradeSide.UP) "Buy" else "Sell"
    val readback = "$verb ${formatVoiceAmount(amount)} $sideWord on ${marketShortLabel(market)}"
    return VoiceParseResult.Order(
        VoiceOrder(
            marketId = market.id,
            marketLabel = marketShortLabel(market),
            side = side,
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
    // A hello that continues into a cut-off question ("hey how are")
    // is the recognizer stopping halfway, not a greeting.
    val afterHello = trimmed.substringAfter(" ", "").trimStart()
    val continuesIntoQuestion = vocab.questionStarters.any { afterHello.startsWith(it) }
    if (hello && continuesIntoQuestion && looksTruncated(lower, language)) {
        return VoiceParseResult.AskAgain("I did not catch the whole thing. Say it again.")
    }
    if (hello) return VoiceParseResult.Question(VoiceQuestion.GreetingHello, text)

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
                VoiceQuestion.Knowledge(assetAvailableAnswer(mention!!.first)),
                text
            )
        }
        (lower.contains(howWord(language)) ||
            (language == VoiceLanguage.ENGLISH && lower.contains("can i trade"))) &&
            (containsAnyWord(lower, vocab.buyVerbs) || containsAnyWord(lower, vocab.sellVerbs) ||
                (language == VoiceLanguage.ENGLISH && lower.contains("trade"))) ->
            return VoiceParseResult.Question(VoiceQuestion.HowToTrade, text)
        containsAnyWord(lower, vocab.tradingOpenWords) ->
            return VoiceParseResult.Question(VoiceQuestion.TradingOpens, text)
        containsAnyWord(lower, vocab.marketWords) -> {
            val m = mentionedMarket ?: currentMarket
            if (m != null) return VoiceParseResult.Question(VoiceQuestion.MarketInfo(m.id), text)
            return VoiceParseResult.AskAgain(vocab.askMarket)
        }
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
