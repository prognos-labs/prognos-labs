package xyz.fruitslice.prognos.data

import java.util.Locale

/**
 * Voice languages, round 1 of the 7-day voice training program
 * (Flint, 22 Nov 2026). The voice must work for people who do not
 * speak English: recognition follows the phone's language when this
 * build supports it, English is the default and the fallback, and
 * each supported language gets its own command vocabulary (orders,
 * yes and no, greetings, questions about price, the most liquid
 * market, news, balance, and what Prognos Labs is). The scope rule
 * holds in every language: only Prognos Labs and trading on Prognos
 * Labs, anything else gets a plain refusal, never a made-up answer.
 *
 * Pure Kotlin, no Android classes, so unit tests run on the JVM.
 * A later round adds the phrasings and languages real testing on
 * real phones shows it missed; nothing here is wall-clock training,
 * the box has no mic.
 */

enum class VoiceLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    PORTUGUESE("pt", "Portuguese"),
    GERMAN("de", "German"),
    TURKISH("tr", "Turkish"),
    ARABIC("ar", "Arabic")
}

val SUPPORTED_VOICE_LANGUAGES: List<VoiceLanguage> = VoiceLanguage.entries.toList()

/** The voice language for a device locale, English when the device
 * language is one this build does not support. */
fun voiceLanguageForLocale(locale: Locale): VoiceLanguage =
    when (locale.language.lowercase(Locale.US)) {
        "es" -> VoiceLanguage.SPANISH
        "fr" -> VoiceLanguage.FRENCH
        "pt" -> VoiceLanguage.PORTUGUESE
        "de" -> VoiceLanguage.GERMAN
        "tr" -> VoiceLanguage.TURKISH
        "ar" -> VoiceLanguage.ARABIC
        else -> VoiceLanguage.ENGLISH
    }

/** True when the device language is one this build supports for
 * voice. Anything else falls back to English. */
fun voiceLanguageSupported(locale: Locale): Boolean =
    locale.language.lowercase(Locale.US) in
        setOf("en", "es", "fr", "pt", "de", "tr", "ar")

/** Plain note for a phone whose language is not supported yet.
 * Voice listens in English, and the tap buttons still work. */
const val UNSUPPORTED_LANGUAGE_NOTE =
    "Your phone language is not supported for voice yet, so voice listens in English. The tap buttons still work."

/**
 * Everything the parser and the answers need for one language.
 * Words are matched lowercase against the normalized transcript;
 * phrases are matched with contains. Number words feed the amount
 * reader. Templates use {placeholders}; fill() below applies them.
 */
data class LanguageVocab(
    val language: VoiceLanguage,
    val yesWords: Set<String>,
    val noWords: Set<String>,
    val cancelWords: Set<String>,
    val buyVerbs: Set<String>,
    val sellVerbs: Set<String>,
    val upWords: Set<String>,
    val downWords: Set<String>,
    val helloPhrases: Set<String>,
    val howAreYouPhrases: Set<String>,
    val checkInPhrases: Set<String>,
    val questionStarters: Set<String>,
    val priceWords: Set<String>,
    val liquidWords: Set<String>,
    val newsWords: Set<String>,
    val balanceWords: Set<String>,
    val marketWords: Set<String>,
    val tradingOpenWords: Set<String>,
    val navVerbs: Set<String>,
    val navTargets: Map<String, String>,
    val numberWords: Map<String, Double>,
    val truncatedTailWords: Set<String>,
    val greetingAnswer: String,
    val howAreYouAnswer: String,
    val checkInAnswer: String,
    val outOfScopeAnswer: String,
    val askAmount: String,
    val askAmountFraction: String,
    val askMarket: String,
    val askPrice: String,
    val askNews: String,
    val answerPrice: String,
    val answerLiquid: String,
    val answerNews: String,
    val answerBalance: String,
    val answerMarket: String,
    val answerTradingOpen: String,
    val answerHowToTrade: String,
    val answerNoPrice: String,
    val answerNoBalance: String,
    val answerNoMarkets: String,
    val answerNoVolume: String,
    val answerNoNews: String,
    val answerNoMarket: String,
    val enrollPhrases: List<String>,
    val challengePhrases: List<String>,
    val challengeInstructionFirst: String,
    val challengeInstructionAgain: String,
    val mishearings: List<Pair<Regex, String>>
)

fun vocabFor(language: VoiceLanguage): LanguageVocab = when (language) {
    VoiceLanguage.ENGLISH -> ENGLISH_VOCAB
    VoiceLanguage.SPANISH -> SPANISH_VOCAB
    VoiceLanguage.FRENCH -> FRENCH_VOCAB
    VoiceLanguage.PORTUGUESE -> PORTUGUESE_VOCAB
    VoiceLanguage.GERMAN -> GERMAN_VOCAB
    VoiceLanguage.TURKISH -> TURKISH_VOCAB
    VoiceLanguage.ARABIC -> ARABIC_VOCAB
}

/** Fills {placeholders} in a template. Unknown placeholders are
 * left as they are, so a missing value never breaks the sentence. */
fun fill(template: String, vararg pairs: Pair<String, String>): String {
    var out = template
    for ((key, value) in pairs) out = out.replace("{$key}", value)
    return out
}

private val ENGLISH_NUMBER_WORDS = mapOf(
    "zero" to 0.0, "one" to 1.0, "two" to 2.0, "three" to 3.0,
    "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0,
    "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0,
    "twelve" to 12.0, "thirteen" to 13.0, "fourteen" to 14.0,
    "fifteen" to 15.0, "sixteen" to 16.0, "seventeen" to 17.0,
    "eighteen" to 18.0, "nineteen" to 19.0, "twenty" to 20.0,
    "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0,
    "sixty" to 60.0, "seventy" to 70.0, "eighty" to 80.0,
    "ninety" to 90.0, "hundred" to 100.0, "thousand" to 1000.0,
    "million" to 1_000_000.0
)

private val ENGLISH_TRUNCATED_TAIL = setOf(
    "are", "is", "the", "my", "how", "what", "when", "why", "where",
    "can", "could", "do", "does", "did", "will", "would", "should",
    "to", "on", "for", "about", "a", "an", "and", "or", "of", "in",
    "at", "by", "with", "your"
)

private val ENGLISH_NAV_TARGETS = mapOf(
    "markets" to "markets", "market" to "markets",
    "dashboard" to "dashboard", "home" to "dashboard",
    "portfolio" to "portfolio", "positions" to "portfolio",
    "wallet" to "wallet", "settings" to "settings",
    "notifications" to "notifications", "balance" to "dashboard"
)

val ENGLISH_VOCAB = LanguageVocab(
    language = VoiceLanguage.ENGLISH,
    yesWords = setOf(
        "yes", "yeah", "yep", "yup", "sure", "correct", "right",
        "ok", "okay", "definitely", "absolutely", "confirm",
        "confirmed", "go ahead", "do it", "please do",
        "sounds good", "that is right", "thats right",
        "that is correct", "thats correct", "that is it", "thats it"
    ),
    noWords = setOf(
        "no", "nope", "nah", "never", "wrong", "incorrect",
        "not", "do not", "dont", "not that one", "not that",
        "that is not it", "thats not it", "that is wrong"
    ),
    cancelWords = setOf("cancel", "stop", "never mind", "nevermind", "forget it"),
    buyVerbs = setOf("buy", "get", "pick up", "grab", "purchase", "acquire", "take", "by", "bye"),
    sellVerbs = setOf("sell", "dump", "offload", "cell"),
    upWords = setOf("up", "yes", "higher", "above", "rise", "rising"),
    downWords = setOf("down", "no", "lower", "below", "fall", "falling"),
    helloPhrases = setOf(
        "hello", "hi", "hey", "hiya", "howdy", "good morning",
        "good afternoon", "good evening", "greetings",
        "what's up", "whats up"
    ),
    howAreYouPhrases = setOf(
        "how are you", "how's it going", "how is it going",
        "how's things", "how are things", "how is it",
        "how are u", "how do you do"
    ),
    checkInPhrases = setOf(
        "can you hear me", "are you listening", "are you there",
        "can you hear", "do you hear me", "are you hearing me"
    ),
    questionStarters = setOf(
        "how", "what", "when", "why", "where", "is ", "are ",
        "can ", "do ", "will ", "should ", "tell me"
    ),
    priceWords = setOf("price", "cost", "worth", "trading at", "how much"),
    liquidWords = setOf("liquid", "liquidity", "most liquid", "biggest", "largest", "volume", "most traded"),
    newsWords = setOf("news", "headline", "headlines", "latest"),
    balanceWords = setOf("balance", "my money", "my funds", "how much do i have", "my account"),
    marketWords = setOf("market", "markets"),
    tradingOpenWords = setOf("trading open", "when does trading open", "when can i trade"),
    navVerbs = setOf("open", "go", "show", "take me", "navigate"),
    navTargets = ENGLISH_NAV_TARGETS,
    numberWords = ENGLISH_NUMBER_WORDS,
    truncatedTailWords = ENGLISH_TRUNCATED_TAIL,
    greetingAnswer = GREETING_HELLO_ANSWER,
    checkInAnswer = "Yes, I can hear you. What would you like to do?",
    howAreYouAnswer = GREETING_HOW_ARE_YOU_ANSWER,
    outOfScopeAnswer = VOICE_OUT_OF_SCOPE,
    askAmount = "I did not catch the amount. Say it again, for example buy 50 Yes.",
    askAmountFraction = "How much in dollars? Say the amount, for example sell 50 No.",
    askMarket = "Which market? Say the coin, for example Bitcoin.",
    askPrice = "Which price? Say the coin, for example Bitcoin.",
    askNews = "Which pair? Say the coin, for example Bitcoin.",
    answerPrice = "{label} is {price}.",
    answerLiquid = "The most liquid market on Prognos Labs right now is {market}, with {volume} in 24-hour volume.",
    answerNews = "The latest news about {label}: {title}{source}.{more}",
    answerBalance = "Your balance is {balance}.",
    answerMarket = "{question} Yes is {yes}, No is {no}.",
    answerTradingOpen = "Trading is not open yet.",
    answerHowToTrade = "Pick a market, choose Buy Up or Sell Down, enter an amount and confirm. Or say it out loud and I will fill the form for you.",
    answerNoPrice = "I do not have a price for {label} right now.",
    answerNoBalance = "Your balance is not available right now.",
    answerNoMarkets = "I do not have the markets loaded right now.",
    answerNoVolume = "I do not have volume data for the markets right now.",
    answerNoNews = "I have no news about {label} in the app right now.{extra}",
    answerNoMarket = "I could not find that market right now.",
    enrollPhrases = VOICE_ENROLL_PHRASE_POOL,
    challengePhrases = VOICE_CHALLENGE_PHRASES,
    challengeInstructionFirst = "Say \"{phrase}\" so the app can check it is you.",
    challengeInstructionAgain = "Say \"{phrase}\" once more so the app can check your voice.",
    mishearings = listOf(
        Regex("\\bbit\\s+coin\\b", RegexOption.IGNORE_CASE) to "bitcoin",
        Regex("\\be\\s+therium\\b", RegexOption.IGNORE_CASE) to "ethereum",
        Regex("\\bprogno" + "sis\\b", RegexOption.IGNORE_CASE) to "prognos",
        Regex("\\bprognose\\b", RegexOption.IGNORE_CASE) to "prognos",
        // v1.1.15: Flint says "prognose labs" and the recognizer
        // also hears "progress labs" for the brand name.
        Regex("\\bprogress\\s+labs\\b", RegexOption.IGNORE_CASE) to "prognos labs",
        Regex("\\bpro\\s+nose\\b", RegexOption.IGNORE_CASE) to "prognos",
        Regex("\\bb\\s+t\\s+c\\b", RegexOption.IGNORE_CASE) to "btc",
        Regex("\\be\\s+t\\s+h\\b", RegexOption.IGNORE_CASE) to "eth",
        Regex("\\bd\\s+o\\s+g\\s+e\\b", RegexOption.IGNORE_CASE) to "doge",
        Regex("\\bs\\s+o\\s+l\\b", RegexOption.IGNORE_CASE) to "sol",
        Regex("\\bx\\s+r\\s+p\\b", RegexOption.IGNORE_CASE) to "xrp",
        Regex("\\byeah\\b", RegexOption.IGNORE_CASE) to "yes",
        Regex("\\byep\\b", RegexOption.IGNORE_CASE) to "yes",
        Regex("\\byup\\b", RegexOption.IGNORE_CASE) to "yes",
        Regex("\\bnope\\b", RegexOption.IGNORE_CASE) to "no"
    )
)

val SPANISH_VOCAB = LanguageVocab(
    language = VoiceLanguage.SPANISH,
    yesWords = setOf(
        "si", "sí", "claro", "correcto", "correcta", "vale",
        "de acuerdo", "adelante", "hazlo", "confirmo", "confirmado",
        "eso es", "asi es", "así es", "exacto", "exactamente",
        "por supuesto", "seguro"
    ),
    noWords = setOf(
        "no", "nunca", "incorrecto", "incorrecta", "equivocado",
        "no es eso", "ese no", "esa no", "no ese", "no esa",
        "para nada", "de ninguna manera"
    ),
    cancelWords = setOf("cancela", "cancelar", "para", "olvídalo", "olvidalo", "deja eso"),
    buyVerbs = setOf("compra", "comprar", "quiero comprar", "consigue", "consigueme", "consígueme", "dame"),
    sellVerbs = setOf("vende", "vender", "quiero vender"),
    upWords = setOf("sí", "si", "sube", "subir", "arriba", "alza", "alto"),
    downWords = setOf("no", "baja", "bajar", "abajo", "caída", "caida"),
    helloPhrases = setOf(
        "hola", "buenos días", "buenos dias", "buenas tardes",
        "buenas noches", "qué tal", "que tal", "qué pasa", "que pasa",
        "saludos"
    ),
    howAreYouPhrases = setOf(
        "cómo estás", "como estas", "cómo está", "como esta",
        "cómo te va", "como te va", "qué tal estás", "que tal estas",
        "cómo andas", "como andas"
    ),
    checkInPhrases = setOf(
        "me escuchas", "me oyes", "estás ahí", "estas ahi",
        "me puedes oír", "me puedes oir"
    ),
    questionStarters = setOf(
        "qué", "que", "cómo", "como", "cuándo", "cuando",
        "dónde", "donde", "por qué", "por que", "cuál", "cual",
        "dime", "muéstrame", "muestrame"
    ),
    priceWords = setOf("precio", "cuánto vale", "cuanto vale", "cuánto cuesta", "cuanto cuesta", "cotización", "cotizacion"),
    liquidWords = setOf("líquido", "liquido", "liquidez", "más líquido", "mas liquido", "mayor volumen", "volumen"),
    newsWords = setOf("noticias", "noticia", "últimas", "ultimas", "novedades"),
    balanceWords = setOf("saldo", "mi saldo", "mi dinero", "cuánto tengo", "cuanto tengo", "mi cuenta"),
    marketWords = setOf("mercado", "mercados"),
    tradingOpenWords = setOf("cuándo abre", "cuando abre", "cuándo empieza", "cuando empieza"),
    navVerbs = setOf("abre", "abrir", "ir", "muestra", "muéstrame", "muestrame", "llévame", "llevame"),
    navTargets = mapOf(
        "mercados" to "markets", "mercado" to "markets",
        "panel" to "dashboard", "inicio" to "dashboard",
        "cartera" to "portfolio", "posiciones" to "portfolio",
        "billetera" to "wallet", "monedero" to "wallet",
        "ajustes" to "settings", "configuración" to "settings",
        "notificaciones" to "notifications", "saldo" to "dashboard"
    ),
    numberWords = mapOf(
        "cero" to 0.0, "uno" to 1.0, "una" to 1.0, "dos" to 2.0,
        "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0,
        "nueve" to 9.0, "diez" to 10.0, "once" to 11.0,
        "doce" to 12.0, "trece" to 13.0, "catorce" to 14.0,
        "dós" to 2.0, "trés" to 3.0,
        "quince" to 15.0, "dieciséis" to 16.0, "dieciseis" to 16.0,
        "diecisiete" to 17.0, "dieciocho" to 18.0, "diecinueve" to 19.0,
        "veinte" to 20.0, "treinta" to 30.0, "cuarenta" to 40.0,
        "cincuenta" to 50.0, "sesenta" to 60.0, "setenta" to 70.0,
        "ochenta" to 80.0, "noventa" to 90.0, "cien" to 100.0,
        "ciento" to 100.0, "mil" to 1000.0, "millón" to 1_000_000.0,
        "millon" to 1_000_000.0
    ),
    truncatedTailWords = setOf(
        "cómo", "como", "qué", "que", "cuándo", "cuando",
        "dónde", "donde", "cuál", "cual", "de", "en", "a",
        "el", "la", "mi", "tu", "su", "es", "está", "esta",
        "y", "o", "con", "por", "para"
    ),
    greetingAnswer = "Hola. ¿Qué quieres hacer?",
    checkInAnswer = "Sí, te escucho. ¿Qué quieres hacer?",
    howAreYouAnswer = "Estoy bien. ¿Qué quieres hacer?",
    outOfScopeAnswer = "Solo ayudo con el trading en Prognos Labs. Pregúntame por un mercado, un precio, o di una orden.",
    askAmount = "No entendí la cantidad. Dila de nuevo, por ejemplo compra 50 Sí.",
    askAmountFraction = "¿Cuánto en dólares? Di la cantidad, por ejemplo vende 50 No.",
    askMarket = "¿Qué mercado? Di la moneda, por ejemplo Bitcoin.",
    askPrice = "¿Qué precio? Di la moneda, por ejemplo Bitcoin.",
    askNews = "¿Noticias de qué moneda? Di la moneda, por ejemplo Bitcoin.",
    answerPrice = "{label} está en {price}.",
    answerLiquid = "El mercado más líquido en Prognos Labs ahora mismo es {market}, con {volume} de volumen en 24 horas.",
    answerNews = "Las últimas noticias sobre {label}: {title}{source}.{more}",
    answerBalance = "Tu saldo es {balance}.",
    answerMarket = "{question} Sí está en {yes}, No está en {no}.",
    answerTradingOpen = "El trading todavía no está abierto.",
    answerHowToTrade = "Elige un mercado, elige Comprar Arriba o Vender Abajo, pon una cantidad y confirma. O dilo en voz alta y yo relleno el formulario por ti.",
    answerNoPrice = "No tengo el precio de {label} ahora mismo.",
    answerNoBalance = "Tu saldo no está disponible ahora mismo.",
    answerNoMarkets = "No tengo los mercados cargados ahora mismo.",
    answerNoVolume = "No tengo datos de volumen de los mercados ahora mismo.",
    answerNoNews = "No tengo noticias sobre {label} en la app ahora mismo.{extra}",
    answerNoMarket = "No pude encontrar ese mercado ahora mismo.",
    enrollPhrases = listOf(
        "Compra cincuenta Sí en Bitcoin por encima de noventa mil",
        "Vende veinte No en Ethereum por debajo de tres mil",
        "Cuál es mi saldo",
        "Compra diez Sí en Solana por encima de doscientos",
        "Muéstrame el mercado más líquido",
        "Qué noticias hay sobre Bitcoin",
        "Compra cien Sí en XRP por encima de tres dólares",
        "Cuánto vale Ethereum ahora mismo",
        "Abre el mercado de Bitcoin",
        "Vende quince No en Solana por debajo de ciento ochenta",
        "Muéstrame mis posiciones abiertas",
        "Cuándo abre el trading en Prognos"
    ),
    challengePhrases = listOf(
        "Compra cincuenta Sí en Bitcoin",
        "Vende veinte No en Ethereum",
        "Cuál es mi saldo",
        "Muéstrame el mercado más líquido",
        "Qué noticias hay sobre Bitcoin",
        "Abre el mercado de Bitcoin",
        "Cuánto vale Ethereum",
        "Compra diez Sí en Solana"
    ),
    challengeInstructionFirst = "Di \"{phrase}\" para que la app compruebe que eres tú.",
    challengeInstructionAgain = "Di \"{phrase}\" una vez más para que la app compruebe tu voz.",
    mishearings = listOf(
        Regex("\\bbit\\s+coin\\b", RegexOption.IGNORE_CASE) to "bitcoin",
        Regex("\\bprogno" + "sis\\b", RegexOption.IGNORE_CASE) to "prognos"
    )
)

val FRENCH_VOCAB = LanguageVocab(
    language = VoiceLanguage.FRENCH,
    yesWords = setOf(
        "oui", "ouais", "bien sûr", "bien sur", "correct",
        "exact", "exactement", "d'accord", "vas-y", "vas y",
        "fais-le", "fais le", "confirme", "confirmé",
        "c'est ça", "cest ca", "c'est bien ça", "absolument", "volontiers"
    ),
    noWords = setOf(
        "non", "jamais", "incorrect", "faux", "fausse",
        "ce n'est pas ça", "pas ça", "pas celui-là", "pas celui la",
        "pas du tout", "surtout pas"
    ),
    cancelWords = setOf("annule", "annuler", "arrête", "arrete", "laisse tomber", "oublie ça", "oublie ca"),
    buyVerbs = setOf("achète", "achete", "acheter", "je veux acheter", "prends", "donne-moi", "donne moi"),
    sellVerbs = setOf("vends", "vendre", "je veux vendre"),
    upWords = setOf("oui", "hausse", "monte", "monter", "haut", "au-dessus", "au dessus"),
    downWords = setOf("non", "baisse", "descend", "descendre", "bas", "en-dessous", "en dessous"),
    helloPhrases = setOf(
        "bonjour", "salut", "bonsoir", "bonne journée", "bonne journee",
        "coucou", "ça va", "ca va", "quoi de neuf"
    ),
    howAreYouPhrases = setOf(
        "comment vas-tu", "comment vas tu", "comment allez-vous",
        "comment allez vous", "comment ça va", "comment ca va",
        "comment tu vas", "ça va bien", "ca va bien"
    ),
    checkInPhrases = setOf(
        "tu m'entends", "m'entends-tu", "tu es là", "tu es la",
        "est-ce que tu m'entends", "est ce que tu m'entends"
    ),
    questionStarters = setOf(
        "quel", "quelle", "quels", "quelles", "comment",
        "quand", "où", "ou", "pourquoi", "qui", "dis-moi",
        "dis moi", "montre-moi", "montre moi", "est-ce", "est ce"
    ),
    priceWords = setOf("prix", "cours", "cotation", "combien vaut", "combien coûte", "combien coute"),
    liquidWords = setOf("liquide", "liquidité", "liquidite", "plus liquide", "plus gros volume", "volume"),
    newsWords = setOf("actualités", "actualites", "nouvelles", "dernières", "dernieres"),
    balanceWords = setOf("solde", "mon solde", "mon argent", "combien j'ai", "combien jai", "mon compte"),
    marketWords = setOf("marché", "marche", "marchés", "marches"),
    tradingOpenWords = setOf("quand ouvre", "quand le trading ouvre", "quand ça commence", "quand ca commence"),
    navVerbs = setOf("ouvre", "ouvrir", "aller", "montre", "montre-moi", "montre moi", "emmène-moi", "emmene moi"),
    navTargets = mapOf(
        "marchés" to "markets", "marches" to "markets",
        "marché" to "markets", "marche" to "markets",
        "tableau de bord" to "dashboard", "accueil" to "dashboard",
        "portefeuille" to "portfolio", "positions" to "portfolio",
        "portefeuille crypto" to "wallet", "réglages" to "settings",
        "reglages" to "settings", "paramètres" to "settings",
        "parametres" to "settings", "notifications" to "notifications",
        "solde" to "dashboard"
    ),
    numberWords = mapOf(
        "zéro" to 0.0, "zero" to 0.0, "un" to 1.0, "une" to 1.0,
        "deux" to 2.0, "trois" to 3.0, "quatre" to 4.0,
        "cinq" to 5.0, "six" to 6.0, "sept" to 7.0,
        "huit" to 8.0, "neuf" to 9.0, "dix" to 10.0,
        "onze" to 11.0, "douze" to 12.0, "treize" to 13.0,
        "quatorze" to 14.0, "quinze" to 15.0, "seize" to 16.0,
        "vingt" to 20.0, "trente" to 30.0, "quarante" to 40.0,
        "cinquante" to 50.0, "soixante" to 60.0, "cent" to 100.0,
        "mille" to 1000.0, "million" to 1_000_000.0
    ),
    truncatedTailWords = setOf(
        "comment", "quel", "quelle", "quand", "où", "ou",
        "pourquoi", "qui", "de", "en", "à", "a", "le", "la",
        "mon", "ma", "ton", "est", "c'est", "et", "ou", "avec",
        "pour", "sur"
    ),
    greetingAnswer = "Bonjour. Que veux-tu faire ?",
    checkInAnswer = "Oui, je t'entends. Que veux-tu faire ?",
    howAreYouAnswer = "Je vais bien. Que veux-tu faire ?",
    outOfScopeAnswer = "Je ne réponds qu'aux questions sur le trading sur Prognos Labs. Demande-moi un marché, un prix, ou dis un ordre.",
    askAmount = "Je n'ai pas compris le montant. Dis-le encore, par exemple achète 50 Oui.",
    askAmountFraction = "Combien en dollars ? Dis le montant, par exemple vends 50 Non.",
    askMarket = "Quel marché ? Dis la monnaie, par exemple Bitcoin.",
    askPrice = "Quel prix ? Dis la monnaie, par exemple Bitcoin.",
    askNews = "Des nouvelles sur quelle monnaie ? Dis la monnaie, par exemple Bitcoin.",
    answerPrice = "{label} est à {price}.",
    answerLiquid = "Le marché le plus liquide sur Prognos Labs en ce moment est {market}, avec {volume} de volume en 24 heures.",
    answerNews = "Les dernières nouvelles sur {label} : {title}{source}.{more}",
    answerBalance = "Ton solde est de {balance}.",
    answerMarket = "{question} Oui est à {yes}, Non est à {no}.",
    answerTradingOpen = "Le trading n'est pas encore ouvert.",
    answerHowToTrade = "Choisis un marché, choisis Acheter Haut ou Vendre Bas, entre un montant et confirme. Ou dis-le à voix haute et je remplis le formulaire pour toi.",
    answerNoPrice = "Je n'ai pas le prix de {label} pour le moment.",
    answerNoBalance = "Ton solde n'est pas disponible pour le moment.",
    answerNoMarkets = "Je n'ai pas les marchés chargés pour le moment.",
    answerNoVolume = "Je n'ai pas les données de volume des marchés pour le moment.",
    answerNoNews = "Je n'ai pas de nouvelles sur {label} dans l'application pour le moment.{extra}",
    answerNoMarket = "Je n'ai pas trouvé ce marché pour le moment.",
    enrollPhrases = listOf(
        "Achète cinquante Oui sur Bitcoin au-dessus de quatre-vingt-dix mille",
        "Vends vingt Non sur Ethereum en dessous de trois mille",
        "Quel est mon solde",
        "Achète dix Oui sur Solana au-dessus de deux cents",
        "Montre-moi le marché le plus liquide",
        "Quelles sont les dernières nouvelles sur Bitcoin",
        "Achète cent Oui sur XRP au-dessus de trois dollars",
        "Combien vaut Ethereum en ce moment",
        "Ouvre le marché Bitcoin",
        "Vends quinze Non sur Solana en dessous de cent quatre-vingts",
        "Montre-moi mes positions ouvertes",
        "Quand le trading ouvre-t-il sur Prognos"
    ),
    challengePhrases = listOf(
        "Achète cinquante Oui sur Bitcoin",
        "Vends vingt Non sur Ethereum",
        "Quel est mon solde",
        "Montre-moi le marché le plus liquide",
        "Quelles sont les dernières nouvelles sur Bitcoin",
        "Ouvre le marché Bitcoin",
        "Combien vaut Ethereum",
        "Achète dix Oui sur Solana"
    ),
    challengeInstructionFirst = "Dis \"{phrase}\" pour que l'application vérifie que c'est bien toi.",
    challengeInstructionAgain = "Dis \"{phrase}\" une fois de plus pour que l'application vérifie ta voix.",
    mishearings = listOf(
        Regex("\\bbit\\s+coin\\b", RegexOption.IGNORE_CASE) to "bitcoin",
        Regex("\\bprogno" + "sis\\b", RegexOption.IGNORE_CASE) to "prognos"
    )
)

val PORTUGUESE_VOCAB = LanguageVocab(
    language = VoiceLanguage.PORTUGUESE,
    yesWords = setOf(
        "sim", "claro", "correto", "correta", "certo",
        "de acordo", "vai em frente", "faz isso", "confirmo",
        "confirmado", "isso mesmo", "exato", "exatamente",
        "com certeza", "pode ser"
    ),
    noWords = setOf(
        "não", "nao", "nunca", "incorreto", "incorreta",
        "errado", "errada", "não é isso", "nao e isso",
        "esse não", "esse nao", "de jeito nenhum"
    ),
    cancelWords = setOf("cancela", "cancelar", "para", "esquece", "deixa isso"),
    buyVerbs = setOf("compra", "comprar", "quero comprar", "me dá", "me da", "pega"),
    sellVerbs = setOf("vende", "vender", "quero vender"),
    upWords = setOf("sim", "sobe", "subir", "alta", "acima", "para cima"),
    downWords = setOf("não", "nao", "desce", "descer", "baixa", "abaixo", "para baixo"),
    helloPhrases = setOf(
        "olá", "ola", "oi", "bom dia", "boa tarde", "boa noite",
        "tudo bem", "e aí", "e ai", "saudações", "saudacoes"
    ),
    howAreYouPhrases = setOf(
        "como você está", "como voce esta", "como vai",
        "como vai você", "como vai voce", "tudo bem com você",
        "tudo bem com voce", "como estão as coisas", "como estao as coisas"
    ),
    checkInPhrases = setOf(
        "você me ouve", "voce me ouve", "está me ouvindo",
        "esta me ouvindo", "você está aí", "voce esta ai"
    ),
    questionStarters = setOf(
        "o que", "como", "quando", "onde", "por que",
        "porque", "qual", "quais", "quem", "me diz",
        "me mostra", "mostra"
    ),
    priceWords = setOf("preço", "preco", "quanto vale", "quanto custa", "cotação", "cotacao"),
    liquidWords = setOf("líquido", "liquido", "liquidez", "mais líquido", "mais liquido", "maior volume", "volume"),
    newsWords = setOf("notícias", "noticias", "notícia", "noticia", "últimas", "ultimas", "novidades"),
    balanceWords = setOf("saldo", "meu saldo", "meu dinheiro", "quanto eu tenho", "minha conta"),
    marketWords = setOf("mercado", "mercados"),
    tradingOpenWords = setOf("quando abre", "quando começa", "quando comeca", "quando o trading abre"),
    navVerbs = setOf("abre", "abrir", "ir", "mostra", "me mostra", "me leva", "navega"),
    navTargets = mapOf(
        "mercados" to "markets", "mercado" to "markets",
        "painel" to "dashboard", "início" to "dashboard",
        "inicio" to "dashboard", "carteira" to "portfolio",
        "posições" to "portfolio", "posicoes" to "portfolio",
        "bolsa" to "wallet", "ajustes" to "settings",
        "configurações" to "settings", "configuracoes" to "settings",
        "notificações" to "notifications", "notificacoes" to "notifications",
        "saldo" to "dashboard"
    ),
    numberWords = mapOf(
        "zero" to 0.0, "um" to 1.0, "uma" to 1.0, "dois" to 2.0,
        "duas" to 2.0, "três" to 3.0, "tres" to 3.0,
        "quatro" to 4.0, "cinco" to 5.0, "seis" to 6.0,
        "sete" to 7.0, "oito" to 8.0, "nove" to 9.0,
        "dez" to 10.0, "onze" to 11.0, "doze" to 12.0,
        "treze" to 13.0, "quatorze" to 14.0, "quinze" to 15.0,
        "dezesseis" to 16.0, "dezessete" to 17.0, "dezoito" to 18.0,
        "dezenove" to 19.0, "vinte" to 20.0, "trinta" to 30.0,
        "quarenta" to 40.0, "cinquenta" to 50.0, "sessenta" to 60.0,
        "setenta" to 70.0, "oitenta" to 80.0, "noventa" to 90.0,
        "cem" to 100.0, "cento" to 100.0, "mil" to 1000.0,
        "milhão" to 1_000_000.0, "milhao" to 1_000_000.0
    ),
    truncatedTailWords = setOf(
        "como", "o que", "quando", "onde", "por que",
        "qual", "quem", "de", "em", "a", "o", "meu",
        "minha", "seu", "é", "e", "está", "esta",
        "e", "ou", "com", "por", "para"
    ),
    greetingAnswer = "Olá. O que você quer fazer?",
    checkInAnswer = "Sim, estou ouvindo você. O que você quer fazer?",
    howAreYouAnswer = "Estou bem. O que você quer fazer?",
    outOfScopeAnswer = "Só ajudo com o trading na Prognos Labs. Pergunte por um mercado, um preço, ou diga uma ordem.",
    askAmount = "Não entendi o valor. Diga de novo, por exemplo compra 50 Sim.",
    askAmountFraction = "Quanto em dólares? Diga o valor, por exemplo vende 50 Não.",
    askMarket = "Qual mercado? Diga a moeda, por exemplo Bitcoin.",
    askPrice = "Qual preço? Diga a moeda, por exemplo Bitcoin.",
    askNews = "Notícias de qual moeda? Diga a moeda, por exemplo Bitcoin.",
    answerPrice = "{label} está em {price}.",
    answerLiquid = "O mercado mais líquido na Prognos Labs agora é {market}, com {volume} de volume em 24 horas.",
    answerNews = "As últimas notícias sobre {label}: {title}{source}.{more}",
    answerBalance = "Seu saldo é {balance}.",
    answerMarket = "{question} Sim está em {yes}, Não está em {no}.",
    answerTradingOpen = "O trading ainda não está aberto.",
    answerHowToTrade = "Escolha um mercado, escolha Comprar Para Cima ou Vender Para Baixo, coloque um valor e confirme. Ou diga em voz alta e eu preencho o formulário para você.",
    answerNoPrice = "Não tenho o preço de {label} agora.",
    answerNoBalance = "Seu saldo não está disponível agora.",
    answerNoMarkets = "Não tenho os mercados carregados agora.",
    answerNoVolume = "Não tenho dados de volume dos mercados agora.",
    answerNoNews = "Não tenho notícias sobre {label} no app agora.{extra}",
    answerNoMarket = "Não consegui encontrar esse mercado agora.",
    enrollPhrases = listOf(
        "Compra cinquenta Sim em Bitcoin acima de noventa mil",
        "Vende vinte Não em Ethereum abaixo de três mil",
        "Qual é o meu saldo",
        "Compra dez Sim em Solana acima de duzentos",
        "Mostra o mercado mais líquido",
        "Quais são as últimas notícias sobre Bitcoin",
        "Compra cem Sim em XRP acima de três dólares",
        "Quanto vale Ethereum agora",
        "Abre o mercado de Bitcoin",
        "Vende quinze Não em Solana abaixo de cento e oitenta",
        "Mostra minhas posições abertas",
        "Quando abre o trading na Prognos"
    ),
    challengePhrases = listOf(
        "Compra cinquenta Sim em Bitcoin",
        "Vende vinte Não em Ethereum",
        "Qual é o meu saldo",
        "Mostra o mercado mais líquido",
        "Quais são as últimas notícias sobre Bitcoin",
        "Abre o mercado de Bitcoin",
        "Quanto vale Ethereum",
        "Compra dez Sim em Solana"
    ),
    challengeInstructionFirst = "Diga \"{phrase}\" para o app confirmar que é você.",
    challengeInstructionAgain = "Diga \"{phrase}\" mais uma vez para o app confirmar a sua voz.",
    mishearings = listOf(
        Regex("\\bbit\\s+coin\\b", RegexOption.IGNORE_CASE) to "bitcoin",
        Regex("\\bprogno" + "sis\\b", RegexOption.IGNORE_CASE) to "prognos"
    )
)

val GERMAN_VOCAB = LanguageVocab(
    language = VoiceLanguage.GERMAN,
    yesWords = setOf(
        "ja", "genau", "richtig", "stimmt", "klar",
        "einverstanden", "mach schon", "los", "bestätige",
        "bestatige", "bestätigt", "bestatigt", "das stimmt",
        "so ist es", "absolut", "sicher", "okay", "ok"
    ),
    noWords = setOf(
        "nein", "nie", "falsch", "nicht richtig",
        "das stimmt nicht", "nicht das", "nicht dieser",
        "auf keinen fall", "keinesfalls"
    ),
    cancelWords = setOf("abbrechen", "stopp", "vergiss es", "lass es"),
    buyVerbs = setOf("kaufe", "kaufen", "ich will kaufen", "hol", "gib mir", "nimm"),
    sellVerbs = setOf("verkaufe", "verkaufen", "ich will verkaufen"),
    upWords = setOf("ja", "hoch", "steigt", "steigen", "oben", "über", "uber"),
    downWords = setOf("nein", "runter", "fällt", "fallt", "fallen", "unten", "unter"),
    helloPhrases = setOf(
        "hallo", "hi", "hey", "guten morgen", "guten tag",
        "guten abend", "grüß dich", "gruss dich", "wie geht's",
        "wie gehts", "was geht"
    ),
    howAreYouPhrases = setOf(
        "wie geht es dir", "wie geht's dir", "wie gehts dir",
        "wie geht es ihnen", "wie geht es", "wie läuft es",
        "wie lauft es", "alles gut"
    ),
    checkInPhrases = setOf(
        "hörst du mich", "horst du mich", "kannst du mich hören",
        "kannst du mich horen", "bist du da"
    ),
    questionStarters = setOf(
        "was", "wie", "wann", "wo", "warum", "wer",
        "welche", "welcher", "welches", "sag mir", "zeig mir",
        "kann", "ist", "sind"
    ),
    priceWords = setOf("preis", "kurs", "wie viel kostet", "was kostet", "wert"),
    liquidWords = setOf("liquidität", "liquiditat", "liquideste", "größtes volumen", "grosstes volumen", "volumen", "meiste gehandelt"),
    newsWords = setOf("nachrichten", "neuigkeiten", "aktuelle", "schlagzeilen"),
    balanceWords = setOf("guthaben", "mein guthaben", "mein geld", "wie viel habe ich", "mein konto", "kontostand"),
    marketWords = setOf("markt", "märkte", "markte"),
    tradingOpenWords = setOf("wann öffnet", "wann offnet", "wann beginnt", "wann startet"),
    navVerbs = setOf("öffne", "offne", "öffnen", "offnen", "geh", "zeig", "zeig mir", "bring mich"),
    navTargets = mapOf(
        "märkte" to "markets", "markte" to "markets",
        "markt" to "markets", "übersicht" to "dashboard",
        "ubersicht" to "dashboard", "startseite" to "dashboard",
        "portfolio" to "portfolio", "positionen" to "portfolio",
        "wallet" to "wallet", "geldbörse" to "wallet",
        "geldboerse" to "wallet", "einstellungen" to "settings",
        "benachrichtigungen" to "notifications",
        "guthaben" to "dashboard"
    ),
    numberWords = mapOf(
        "null" to 0.0, "eins" to 1.0, "ein" to 1.0,
        "zwei" to 2.0, "drei" to 3.0, "vier" to 4.0,
        "fünf" to 5.0, "funf" to 5.0, "sechs" to 6.0,
        "sieben" to 7.0, "acht" to 8.0, "neun" to 9.0,
        "zehn" to 10.0, "elf" to 11.0, "zwölf" to 12.0,
        "zwolf" to 12.0, "dreizehn" to 13.0, "vierzehn" to 14.0,
        "fünfzehn" to 15.0, "funfzehn" to 15.0,
        "sechzehn" to 16.0, "siebzehn" to 17.0, "achtzehn" to 18.0,
        "neunzehn" to 19.0, "zwanzig" to 20.0, "dreißig" to 30.0,
        "dreissig" to 30.0, "vierzig" to 40.0, "fünfzig" to 50.0,
        "funfzig" to 50.0, "sechzig" to 60.0, "siebzig" to 70.0,
        "achtzig" to 80.0, "neunzig" to 90.0, "hundert" to 100.0,
        "tausend" to 1000.0, "million" to 1_000_000.0
    ),
    truncatedTailWords = setOf(
        "wie", "was", "wann", "wo", "warum", "wer",
        "welche", "welcher", "der", "die", "das", "mein",
        "meine", "ist", "sind", "und", "oder", "mit",
        "für", "fur", "von", "zu", "in", "auf"
    ),
    greetingAnswer = "Hallo. Was möchtest du tun?",
    checkInAnswer = "Ja, ich höre dich. Was möchtest du tun?",
    howAreYouAnswer = "Mir geht es gut. Was möchtest du tun?",
    outOfScopeAnswer = "Ich helfe nur beim Trading auf Prognos Labs. Frag mich nach einem Markt, einem Preis, oder sag einen Auftrag.",
    askAmount = "Ich habe den Betrag nicht verstanden. Sag ihn noch einmal, zum Beispiel kaufe 50 Ja.",
    askAmountFraction = "Wie viel in Dollar? Sag den Betrag, zum Beispiel verkaufe 50 Nein.",
    askMarket = "Welcher Markt? Sag die Münze, zum Beispiel Bitcoin.",
    askPrice = "Welcher Preis? Sag die Münze, zum Beispiel Bitcoin.",
    askNews = "Nachrichten über welche Münze? Sag die Münze, zum Beispiel Bitcoin.",
    answerPrice = "{label} liegt bei {price}.",
    answerLiquid = "Der liquideste Markt auf Prognos Labs gerade ist {market}, mit {volume} Volumen in 24 Stunden.",
    answerNews = "Die neuesten Nachrichten über {label}: {title}{source}.{more}",
    answerBalance = "Dein Guthaben ist {balance}.",
    answerMarket = "{question} Ja liegt bei {yes}, Nein liegt bei {no}.",
    answerTradingOpen = "Das Trading ist noch nicht geöffnet.",
    answerHowToTrade = "Wähle einen Markt, wähle Kaufen Hoch oder Verkaufen Runter, gib einen Betrag ein und bestätige. Oder sag es laut und ich fülle das Formular für dich aus.",
    answerNoPrice = "Ich habe gerade keinen Preis für {label}.",
    answerNoBalance = "Dein Guthaben ist gerade nicht verfügbar.",
    answerNoMarkets = "Ich habe die Märkte gerade nicht geladen.",
    answerNoVolume = "Ich habe gerade keine Volumendaten für die Märkte.",
    answerNoNews = "Ich habe gerade keine Nachrichten über {label} in der App.{extra}",
    answerNoMarket = "Ich konnte diesen Markt gerade nicht finden.",
    enrollPhrases = listOf(
        "Kaufe fünfzig Ja auf Bitcoin über neunzigtausend",
        "Verkaufe zwanzig Nein auf Ethereum unter dreitausend",
        "Wie hoch ist mein Guthaben",
        "Kaufe zehn Ja auf Solana über zweihundert",
        "Zeig mir den liquidesten Markt",
        "Was gibt es Neues über Bitcoin",
        "Kaufe hundert Ja auf XRP über drei Dollar",
        "Was kostet Ethereum gerade",
        "Öffne den Bitcoin Markt",
        "Verkaufe fünfzehn Nein auf Solana unter einhundertachtzig",
        "Zeig mir meine offenen Positionen",
        "Wann öffnet das Trading auf Prognos"
    ),
    challengePhrases = listOf(
        "Kaufe fünfzig Ja auf Bitcoin",
        "Verkaufe zwanzig Nein auf Ethereum",
        "Wie hoch ist mein Guthaben",
        "Zeig mir den liquidesten Markt",
        "Was gibt es Neues über Bitcoin",
        "Öffne den Bitcoin Markt",
        "Was kostet Ethereum",
        "Kaufe zehn Ja auf Solana"
    ),
    challengeInstructionFirst = "Sag \"{phrase}\", damit die App prüfen kann, dass du es bist.",
    challengeInstructionAgain = "Sag \"{phrase}\" noch einmal, damit die App deine Stimme prüfen kann.",
    mishearings = listOf(
        Regex("\\bbit\\s+coin\\b", RegexOption.IGNORE_CASE) to "bitcoin",
        Regex("\\bprogno" + "sis\\b", RegexOption.IGNORE_CASE) to "prognos"
    )
)

val TURKISH_VOCAB = LanguageVocab(
    language = VoiceLanguage.TURKISH,
    yesWords = setOf(
        "evet", "tabii", "tabi", "doğru", "dogru", "aynen",
        "tamam", "olur", "devam et", "onayla", "onaylıyorum",
        "onayliyorum", "öyle", "kesinlikle", "elbette"
    ),
    noWords = setOf(
        "hayır", "hayir", "asla", "yanlış", "yanlis",
        "doğru değil", "dogru degil", "o değil", "o degil",
        "bu değil", "bu degil", "hiç", "hic"
    ),
    cancelWords = setOf("iptal", "iptal et", "dur", "vazgeç", "vazgec", "unut gitsin"),
    buyVerbs = setOf("al", "almak", "satın al", "satin al", "almak istiyorum", "bana al", "ver"),
    sellVerbs = setOf("sat", "satmak", "satmak istiyorum"),
    upWords = setOf("evet", "yüksel", "yuksel", "yukarı", "yukari", "artış", "artis", "üst", "ust"),
    downWords = setOf("hayır", "hayir", "düş", "dus", "aşağı", "asagi", "düşüş", "dusus", "alt"),
    helloPhrases = setOf(
        "merhaba", "selam", "günaydın", "gunaydin", "iyi günler",
        "iyi gunler", "iyi akşamlar", "iyi aksamlar", "naber",
        "nasılsın", "nasilsin"
    ),
    howAreYouPhrases = setOf(
        "nasılsın", "nasilsin", "nasılsınız", "nasilisiniz",
        "nasıl gidiyor", "nasil gidiyor", "ne haber", "iyi misin",
        "iyi misiniz"
    ),
    checkInPhrases = setOf(
        "beni duyuyor musun", "duyuyor musun",
        "orada mısın", "orada misin"
    ),
    questionStarters = setOf(
        "ne", "nasıl", "nasil", "ne zaman", "nerede",
        "nerde", "neden", "niçin", "nicin", "kim",
        "hangi", "hangisi", "bana söyle", "bana soyle",
        "göster", "goster"
    ),
    priceWords = setOf("fiyat", "fiyatı", "fiyati", "kaç para", "kac para", "ne kadar", "değeri", "degeri"),
    liquidWords = setOf("likidite", "en likit", "en büyük hacim", "en buyuk hacim", "hacim", "en çok işlem", "en cok islem"),
    newsWords = setOf("haber", "haberler", "son dakika", "gündem", "gundem"),
    balanceWords = setOf("bakiye", "bakiyem", "param", "ne kadarım var", "ne kadarim var", "hesabım", "hesabim"),
    marketWords = setOf("piyasa", "piyasalar", "market"),
    tradingOpenWords = setOf("ne zaman açılıyor", "ne zaman aciliyor", "ne zaman başlıyor", "ne zaman basliyor"),
    navVerbs = setOf("aç", "ac", "git", "göster", "goster", "bana göster", "bana goster", "götür", "gotur"),
    navTargets = mapOf(
        "piyasalar" to "markets", "piyasa" to "markets",
        "market" to "markets", "panel" to "dashboard",
        "ana sayfa" to "dashboard", "portföy" to "portfolio",
        "portfoy" to "portfolio", "pozisyonlar" to "portfolio",
        "cüzdan" to "wallet", "cuzdan" to "wallet",
        "ayarlar" to "settings", "bildirimler" to "notifications",
        "bakiye" to "dashboard"
    ),
    numberWords = mapOf(
        "sıfır" to 0.0, "sifir" to 0.0, "bir" to 1.0,
        "iki" to 2.0, "üç" to 3.0, "uc" to 3.0,
        "dört" to 4.0, "dort" to 4.0, "beş" to 5.0,
        "bes" to 5.0, "altı" to 6.0, "alti" to 6.0,
        "yedi" to 7.0, "sekiz" to 8.0, "dokuz" to 9.0,
        "on" to 10.0, "on bir" to 11.0, "on iki" to 12.0,
        "on üç" to 13.0, "on uc" to 13.0, "on dört" to 14.0,
        "on dort" to 14.0, "on beş" to 15.0, "on bes" to 15.0,
        "on altı" to 16.0, "on alti" to 16.0, "on yedi" to 17.0,
        "on sekiz" to 18.0, "on dokuz" to 19.0, "yirmi" to 20.0,
        "otuz" to 30.0, "kırk" to 40.0, "kirk" to 40.0,
        "elli" to 50.0, "altmış" to 60.0, "altmis" to 60.0,
        "yetmiş" to 70.0, "yetmis" to 70.0, "seksen" to 80.0,
        "doksan" to 90.0, "yüz" to 100.0, "yuz" to 100.0,
        "bin" to 1000.0, "milyon" to 1_000_000.0
    ),
    truncatedTailWords = setOf(
        "nasıl", "nasil", "ne", "ne zaman", "nerede",
        "nerde", "neden", "hangi", "hangisi", "kim",
        "ve", "veya", "ile", "için", "icin", "de",
        "da", "mi", "mı", "mu", "mü", "benim",
        "senin", "bu", "şu", "su", "o"
    ),
    greetingAnswer = "Merhaba. Ne yapmak istersin?",
    checkInAnswer = "Evet, seni duyuyorum. Ne yapmak istersin?",
    howAreYouAnswer = "İyiyim. Ne yapmak istersin?",
    outOfScopeAnswer = "Sadece Prognos Labs üzerinde trading konusunda yardımcı oluyorum. Bana bir piyasa, bir fiyat sor, ya da bir emir söyle.",
    askAmount = "Miktarı anlayamadım. Tekrar söyle, örneğin elli Evet al.",
    askAmountFraction = "Dolar olarak ne kadar? Miktarı söyle, örneğin elli Hayır sat.",
    askMarket = "Hangi piyasa? Coin söyle, örneğin Bitcoin.",
    askPrice = "Hangi fiyat? Coin söyle, örneğin Bitcoin.",
    askNews = "Hangi coin hakkında haber? Coin söyle, örneğin Bitcoin.",
    answerPrice = "{label} {price}.",
    answerLiquid = "Prognos Labs üzerinde şu an en likit piyasa {market}, 24 saatlik hacim {volume}.",
    answerNews = "{label} hakkında son haberler: {title}{source}.{more}",
    answerBalance = "Bakiyen {balance}.",
    answerMarket = "{question} Evet {yes}, Hayır {no}.",
    answerTradingOpen = "Trading henüz açık değil.",
    answerHowToTrade = "Bir piyasa seç, Yukarı Al ya da Aşağı Sat seç, bir miktar gir ve onayla. Ya da sesli söyle, formu senin için doldurayım.",
    answerNoPrice = "Şu an {label} için fiyatım yok.",
    answerNoBalance = "Bakiyen şu an kullanılamıyor.",
    answerNoMarkets = "Piyasalar şu an yüklü değil.",
    answerNoVolume = "Şu an piyasalar için hacim verim yok.",
    answerNoNews = "Şu an uygulamada {label} hakkında haber yok.{extra}",
    answerNoMarket = "Bu piyasayı şu an bulamadım.",
    enrollPhrases = listOf(
        "Doksan binin üzerinde Bitcoin için elli Evet al",
        "Üç binin altında Ethereum için yirmi Hayır sat",
        "Bakiyem ne kadar",
        "İki yüzün üzerinde Solana için on Evet al",
        "En likit piyasayı göster",
        "Bitcoin hakkında son haberler neler",
        "Üç doların üzerinde XRP için yüz Evet al",
        "Ethereum şu an kaç para",
        "Bitcoin piyasasını aç",
        "Yüz seksenin altında Solana için on beş Hayır sat",
        "Açık pozisyonlarımı göster",
        "Prognos üzerinde trading ne zaman açılıyor"
    ),
    challengePhrases = listOf(
        "Bitcoin için elli Evet al",
        "Ethereum için yirmi Hayır sat",
        "Bakiyem ne kadar",
        "En likit piyasayı göster",
        "Bitcoin hakkında son haberler neler",
        "Bitcoin piyasasını aç",
        "Ethereum şu an kaç para",
        "Solana için on Evet al"
    ),
    challengeInstructionFirst = "Uygulamanın sen olduğunu doğrulaması için \"{phrase}\" de.",
    challengeInstructionAgain = "Uygulamanın sesini doğrulaması için \"{phrase}\" bir kez daha de.",
    mishearings = listOf(
        Regex("\\bbit\\s+coin\\b", RegexOption.IGNORE_CASE) to "bitcoin",
        Regex("\\bprogno" + "sis\\b", RegexOption.IGNORE_CASE) to "prognos"
    )
)

val ARABIC_VOCAB = LanguageVocab(
    language = VoiceLanguage.ARABIC,
    yesWords = setOf(
        "نعم", "أجل", "ايوه", "أيوه", "صحيح", "صح",
        "تمام", "موافق", "أكيد", "اكيد", "بالضبط",
        "تفضل", "قم بذلك", "أكد", "اكد", "حسنا", "حسناً"
    ),
    noWords = setOf(
        "لا", "أبدا", "ابدا", "خطأ", "خطا", "غير صحيح",
        "ليس هذا", "ليس ذلك", "هذا ليس", "مستحيل"
    ),
    cancelWords = setOf("ألغ", "الغ", "إلغاء", "الغاء", "توقف", "انسى الأمر", "انسى الامر"),
    buyVerbs = setOf("اشتر", "اشتري", "شراء", "أريد أن أشتري", "اريد ان اشتري", "خذ", "أعطني", "اعطني"),
    sellVerbs = setOf("بع", "بيع", "أريد أن أبيع", "اريد ان ابيع"),
    upWords = setOf("نعم", "يرتفع", "ارتفاع", "أعلى", "اعلى", "فوق", "صعود"),
    downWords = setOf("لا", "ينخفض", "انخفاض", "أسفل", "اسفل", "تحت", "هبوط"),
    helloPhrases = setOf(
        "مرحبا", "مرحباً", "أهلا", "اهلا", "السلام عليكم",
        "صباح الخير", "مساء الخير", "هاي", "أهلين", "اهلين"
    ),
    howAreYouPhrases = setOf(
        "كيف حالك", "كيف حالك؟", "كيف أنت", "كيف انت",
        "كيف الحال", "شلونك", "كيفك", "ما أخبارك", "ما اخبارك"
    ),
    checkInPhrases = setOf(
        "هل تسمعني", "تسمعني", "هل أنت هنا", "هل انت هنا"
    ),
    questionStarters = setOf(
        "ما", "كيف", "متى", "أين", "اين", "لماذا",
        "من", "أي", "اي", "أخبرني", "اخبرني", "أرني", "ارني"
    ),
    priceWords = setOf("سعر", "السعر", "كم سعر", "كم ثمن", "قيمة", "ثمن"),
    liquidWords = setOf("سيولة", "الأكثر سيولة", "الاكثر سيولة", "أكبر حجم", "اكبر حجم", "حجم", "الأكثر تداولا", "الاكثر تداولا"),
    newsWords = setOf("أخبار", "اخبار", "خبر", "آخر الأخبار", "اخر الاخبار", "مستجدات"),
    balanceWords = setOf("رصيد", "رصيدي", "أموالي", "اموالي", "كم أملك", "كم املك", "حسابي"),
    marketWords = setOf("سوق", "أسواق", "اسواق", "السوق"),
    tradingOpenWords = setOf("متى يفتح التداول", "متى يبدأ التداول", "متى يبدا التداول"),
    navVerbs = setOf("افتح", "اذهب", "أرني", "ارني", "اعرض", "خذني"),
    navTargets = mapOf(
        "أسواق" to "markets", "اسواق" to "markets",
        "سوق" to "markets", "لوحة" to "dashboard",
        "الرئيسية" to "dashboard", "الرئيسيه" to "dashboard",
        "محفظة" to "portfolio", "مراكز" to "portfolio",
        "المحفظة" to "wallet", "إعدادات" to "settings",
        "اعدادات" to "settings", "إشعارات" to "notifications",
        "اشعارات" to "notifications", "رصيد" to "dashboard"
    ),
    numberWords = mapOf(
        "صفر" to 0.0, "واحد" to 1.0, "واحدة" to 1.0,
        "اثنان" to 2.0, "اثنين" to 2.0, "ثلاثة" to 3.0,
        "أربعة" to 4.0, "اربعة" to 4.0, "خمسة" to 5.0,
        "ستة" to 6.0, "سبعة" to 7.0, "ثمانية" to 8.0,
        "تسعة" to 9.0, "عشرة" to 10.0, "أحد عشر" to 11.0,
        "احد عشر" to 11.0, "اثنا عشر" to 12.0,
        "ثلاثة عشر" to 13.0, "أربعة عشر" to 14.0,
        "اربعة عشر" to 14.0, "خمسة عشر" to 15.0,
        "ستة عشر" to 16.0, "سبعة عشر" to 17.0,
        "ثمانية عشر" to 18.0, "تسعة عشر" to 19.0,
        "عشرون" to 20.0, "ثلاثون" to 30.0, "أربعون" to 40.0,
        "اربعون" to 40.0, "خمسون" to 50.0, "ستون" to 60.0,
        "سبعون" to 70.0, "ثمانون" to 80.0, "تسعون" to 90.0,
        "مئة" to 100.0, "مائة" to 100.0, "ألف" to 1000.0,
        "الف" to 1000.0, "مليون" to 1_000_000.0
    ),
    truncatedTailWords = setOf(
        "كيف", "ما", "متى", "أين", "اين", "لماذا",
        "من", "أي", "اي", "في", "على", "إلى",
        "الى", "من", "عن", "مع", "هو", "هي",
        "هذا", "هذه", "ذلك", "و", "أو", "او"
    ),
    greetingAnswer = "مرحبا. ماذا تريد أن تفعل؟",
    checkInAnswer = "نعم، أسمعك. ماذا تريد أن تفعل؟",
    howAreYouAnswer = "أنا بخير. ماذا تريد أن تفعل؟",
    outOfScopeAnswer = "أنا أساعد فقط في التداول على Prognos Labs. اسألني عن سوق أو سعر، أو قل أمرا.",
    askAmount = "لم أفهم المبلغ. قله مرة أخرى، مثلا اشتر خمسين نعم.",
    askAmountFraction = "كم بالدولار؟ قل المبلغ، مثلا بع خمسين لا.",
    askMarket = "أي سوق؟ قل العملة، مثلا بيتكوين.",
    askPrice = "أي سعر؟ قل العملة، مثلا بيتكوين.",
    askNews = "أخبار عن أي عملة؟ قل العملة، مثلا بيتكوين.",
    answerPrice = "سعر {label} هو {price}.",
    answerLiquid = "السوق الأكثر سيولة على Prognos Labs الآن هو {market}، بحجم تداول {volume} خلال 24 ساعة.",
    answerNews = "آخر الأخبار عن {label}: {title}{source}.{more}",
    answerBalance = "رصيدك هو {balance}.",
    answerMarket = "{question} نعم بسعر {yes}، لا بسعر {no}.",
    answerTradingOpen = "التداول لم يفتح بعد.",
    answerHowToTrade = "اختر سوقا، ثم اختر شراء صعود أو بيع هبوط، وأدخل مبلغا وأكد. أو قله بصوتك وسأملأ النموذج لك.",
    answerNoPrice = "ليس لدي سعر {label} الآن.",
    answerNoBalance = "رصيدك غير متاح الآن.",
    answerNoMarkets = "الأسواق غير محملة الآن.",
    answerNoVolume = "ليس لدي بيانات حجم للأسواق الآن.",
    answerNoNews = "ليس لدي أخبار عن {label} في التطبيق الآن.{extra}",
    answerNoMarket = "لم أجد ذلك السوق الآن.",
    enrollPhrases = listOf(
        "اشتر خمسين نعم على بيتكوين فوق تسعين ألف",
        "بع عشرين لا على إيثيريوم تحت ثلاثة آلاف",
        "ما هو رصيدي",
        "اشتر عشرة نعم على سولانا فوق مئتين",
        "أرني السوق الأكثر سيولة",
        "ما آخر الأخبار عن بيتكوين",
        "اشتر مئة نعم على إكس آر بي فوق ثلاثة دولارات",
        "كم سعر إيثيريوم الآن",
        "افتح سوق بيتكوين",
        "بع خمسة عشر لا على سولانا تحت مئة وثمانين",
        "أرني مراكزي المفتوحة",
        "متى يفتح التداول على Prognos"
    ),
    challengePhrases = listOf(
        "اشتر خمسين نعم على بيتكوين",
        "بع عشرين لا على إيثيريوم",
        "ما هو رصيدي",
        "أرني السوق الأكثر سيولة",
        "ما آخر الأخبار عن بيتكوين",
        "افتح سوق بيتكوين",
        "كم سعر إيثيريوم",
        "اشتر عشرة نعم على سولانا"
    ),
    challengeInstructionFirst = "قل \"{phrase}\" حتى يتأكد التطبيق أنك أنت.",
    challengeInstructionAgain = "قل \"{phrase}\" مرة أخرى حتى يتأكد التطبيق من صوتك.",
    mishearings = listOf(
        Regex("\\bbit\\s+coin\\b", RegexOption.IGNORE_CASE) to "bitcoin",
        Regex("\\bprogno" + "sis\\b", RegexOption.IGNORE_CASE) to "prognos"
    )
)
