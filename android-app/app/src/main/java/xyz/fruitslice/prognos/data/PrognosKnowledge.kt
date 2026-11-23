package xyz.fruitslice.prognos.data

import xyz.fruitslice.prognos.model.ASSET_LABELS
import xyz.fruitslice.prognos.model.NewsItem

/**
 * What the voice knows about Prognos Labs itself. A plain data file
 * in the app: no network, no paid service, no key. Everything here
 * is a fact about the platform as it is today. When a question is
 * not covered here, the voice says so plainly and never invents an
 * answer.
 *
 * Standing facts (Flint, 22 Nov): wallet submissions close Dec 1,
 * public launch Dec 2, trading is closed until launch, deposits are
 * USDT on BSC through the user's own wallet, the platform is
 * non-custodial, every trade carries a small fee, there is a points
 * system and referrals. The liquidity backer is never named.
 */
fun platformAnswer(lowerText: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): String? {
    val t = lowerText
    if (language != VoiceLanguage.ENGLISH) return platformAnswerTranslated(t, language)
    return when {
        // "What is Prognos Labs?" / "Tell me about Prognos Labs" /
        // "What's Prognos Labs?" / "How does Prognos Labs work?"
        // v1.1.15: "what's" and "what does" and "how does" asked
        // the same question and were refused, because only "what
        // is" matched. "How does" stays out of the forecast branch:
        // a forecast question still gets the forecast answer.
        t.contains("prognos") && (t.contains("what is") || t.contains("what are") ||
            t.contains("what's") || t.contains("whats") || t.contains("what does") ||
            (t.contains("how does") && !t.contains("forecast") && !t.contains("ai")) ||
            t.contains("tell me") || t.contains("about") || t.contains("who are") ||
            t.contains("what can you do") || t.contains("explain") ||
            t == "prognos" || t == "prognos labs") ->
            "Prognos Labs is a prediction market with AI forecasting built in. " +
                "Every market carries a short, plain-language forecast, and when the market settles " +
                "the forecast is scored against the real outcome, so the next one is sharper. " +
                "People decide every market. Our AI never creates or settles one. " +
                "Users keep their own wallets and keys, we never hold their funds."
        // "What can you do?"
        t.contains("what can you do") ->
            "I can help with Prognos Labs trading. Ask me for a price, the most liquid market, " +
                "the latest news about a coin, or say an order out loud and I will fill the form for you."
        // "How do prediction markets work?"
        (t.contains("prediction market") || t.contains("prediction markets")) &&
            (t.contains("how") || t.contains("work") || t.contains("what")) ->
            "You pick a market, choose Yes or No, and enter an amount. If the outcome matches " +
                "your side when the market settles, you win. Prices move with the market, " +
                "and you can sell before it closes."
        // "How does the AI forecast work?"
        (t.contains("forecast") || t.contains("ai")) &&
            (t.contains("how") || t.contains("work") || t.contains("what")) ->
            "Every market carries a short forecast that explains what is happening and what could " +
                "come next. When the market settles on the real outcome, the forecast it gave is " +
                "scored against that outcome, so the next forecast is sharper. People decide every " +
                "market. Our AI never creates or settles one."
        // "How do I link my wallet?"
        t.contains("wallet") && (t.contains("link") || t.contains("connect") || t.contains("how")) ->
            "Prognos Labs is non-custodial. You link your own wallet and keep your own keys. " +
                "We never see your private key and we never hold your funds."
        // "How do I deposit?"
        t.contains("deposit") ->
            "Deposits are USDT on BSC, onchain, through your own wallet. You sign the deposit " +
                "from your wallet, and the funds stay yours."
        // "When is the launch?" / "When do wallet submissions close?"
        t.contains("launch") || t.contains("go live") || t.contains("submission") ->
            "Wallet submissions are open now and close December 1, ahead of the public launch " +
                "on December 2. Trading opens at the launch."
        // "What are the fees?" / "How do you make money?"
        t.contains("fee") || t.contains("fees") || t.contains("make money") ->
            "Every trade on Prognos Labs carries a small fee, and that fee is how we make money. " +
                "We do not sell user data."
        // "What are points?" / "How do referrals work?"
        t.contains("point") || t.contains("referral") || t.contains("refer") ->
            "Prognos Labs has a points system, and you earn points for bringing people in through " +
                "your referral link. The points follow your trading and referrals."
        else -> null
    }
}

/**
 * The same platform facts in the user's language: what Prognos Labs
 * is, how the forecasting works, wallets, deposits, launch, fees,
 * points. Short, plain, and only facts the English answers already
 * state. Anything not covered here returns null, and the voice
 * refuses plainly instead of inventing an answer.
 */
private fun platformAnswerTranslated(t: String, language: VoiceLanguage): String? {
    fun has(vararg words: String) = words.any { t.contains(it) }
    return when (language) {
        VoiceLanguage.SPANISH -> when {
            t.contains("prognos") && has("qué es", "que es", "qué son", "cuéntame", "cuentame", "dime", "sobre") ->
                "Prognos Labs es un mercado de predicción con pronósticos de IA. Cada mercado lleva un pronóstico corto en lenguaje sencillo, y cuando el mercado se resuelve el pronóstico se puntúa contra el resultado real, así el siguiente es mejor. Las personas deciden cada mercado. Nuestra IA nunca crea ni resuelve uno. Los usuarios guardan sus propias billeteras y claves, nunca guardamos sus fondos."
            has("pronóstico", "pronostico", "predicción", "prediccion", "ia") && has("cómo", "como", "qué", "que") ->
                "Cada mercado lleva un pronóstico corto que explica lo que está pasando y lo que puede venir después. Cuando el mercado se resuelve con el resultado real, el pronóstico se puntúa contra ese resultado, así el siguiente es mejor. Las personas deciden cada mercado. Nuestra IA nunca crea ni resuelve uno."
            has("billetera", "cartera") && has("vincular", "conectar", "cómo", "como") ->
                "Prognos Labs no guarda fondos. Vinculas tu propia billetera y guardas tus propias claves. Nunca vemos tu clave privada y nunca guardamos tus fondos."
            has("depósito", "deposito", "depositar") ->
                "Los depósitos son USDT en BSC, en cadena, a través de tu propia billetera. Firmas el depósito desde tu billetera, y los fondos siguen siendo tuyos."
            has("lanzamiento", "cuándo abre", "cuando abre", "apertura") ->
                "Las inscripciones de billeteras están abiertas ahora y cierran el 1 de diciembre, antes del lanzamiento público el 2 de diciembre. El trading abre con el lanzamiento."
            has("comisión", "comision", "tarifa", "cómo ganan", "como ganan", "cómo ganas") ->
                "Cada operación en Prognos Labs lleva una pequeña comisión, y esa comisión es como ganamos dinero. No vendemos los datos de los usuarios."
            has("puntos", "punto", "referido", "referencia", "referir") ->
                "Prognos Labs tiene un sistema de puntos, y ganas puntos por traer personas con tu enlace de referencia. Los puntos siguen tu trading y tus referencias."
            else -> null
        }
        VoiceLanguage.FRENCH -> when {
            t.contains("prognos") && has("c'est quoi", "qu'est-ce que", "qu est-ce que", "dis-moi", "dis moi", "à propos", "a propos") ->
                "Prognos Labs est un marché de prédiction avec des prévisions IA. Chaque marché porte une prévision courte en langage simple, et quand le marché se résout la prévision est notée contre le vrai résultat, pour que la suivante soit meilleure. Ce sont les gens qui décident de chaque marché. Notre IA n'en crée ni n'en résout jamais. Les utilisateurs gardent leurs propres portefeuilles et clés, nous ne gardons jamais leurs fonds."
            has("prévision", "prevision", "ia") && has("comment", "que", "quoi") ->
                "Chaque marché porte une prévision courte qui explique ce qui se passe et ce qui peut venir ensuite. Quand le marché se résout sur le vrai résultat, la prévision est notée contre ce résultat, pour que la suivante soit meilleure. Ce sont les gens qui décident de chaque marché. Notre IA n'en crée ni n'en résout jamais."
            has("portefeuille") && has("lier", "connecter", "comment") ->
                "Prognos Labs ne garde pas les fonds. Vous liez votre propre portefeuille et gardez vos propres clés. Nous ne voyons jamais votre clé privée et nous ne gardons jamais vos fonds."
            has("dépôt", "depot", "déposer", "deposer") ->
                "Les dépôts sont en USDT sur BSC, en chaîne, via votre propre portefeuille. Vous signez le dépôt depuis votre portefeuille, et les fonds restent à vous."
            has("lancement", "quand ouvre", "ouverture") ->
                "Les inscriptions de portefeuilles sont ouvertes maintenant et ferment le 1er décembre, avant le lancement public le 2 décembre. Le trading ouvre au lancement."
            has("frais", "commission", "comment gagnez", "comment vous gagnez") ->
                "Chaque opération sur Prognos Labs porte une petite commission, et c'est comme cela que nous gagnons de l'argent. Nous ne vendons pas les données des utilisateurs."
            has("points", "point", "parrainage", "référence", "reference") ->
                "Prognos Labs a un système de points, et vous gagnez des points en amenant des gens avec votre lien de parrainage. Les points suivent votre trading et vos parrainages."
            else -> null
        }
        VoiceLanguage.PORTUGUESE -> when {
            t.contains("prognos") && has("o que é", "o que e", "me diz", "me fala", "sobre") ->
                "A Prognos Labs é um mercado de previsões com previsões de IA. Cada mercado tem uma previsão curta em linguagem simples, e quando o mercado se resolve a previsão é pontuada contra o resultado real, para a próxima ser melhor. As pessoas decidem cada mercado. Nossa IA nunca cria nem resolve um. Os usuários guardam suas próprias carteiras e chaves, nunca guardamos seus fundos."
            has("previsão", "previsao", "ia") && has("como", "o que") ->
                "Cada mercado tem uma previsão curta que explica o que está acontecendo e o que pode vir depois. Quando o mercado se resolve no resultado real, a previsão é pontuada contra esse resultado, para a próxima ser melhor. As pessoas decidem cada mercado. Nossa IA nunca cria nem resolve um."
            has("carteira") && has("vincular", "conectar", "como") ->
                "A Prognos Labs não guarda fundos. Você vincula sua própria carteira e guarda suas próprias chaves. Nunca vemos sua chave privada e nunca guardamos seus fundos."
            has("depósito", "deposito", "depositar") ->
                "Os depósitos são em USDT na BSC, on-chain, pela sua própria carteira. Você assina o depósito na sua carteira, e os fundos continuam sendo seus."
            has("lançamento", "lancamento", "quando abre", "abertura") ->
                "As inscrições de carteiras estão abertas agora e fecham em 1º de dezembro, antes do lançamento público em 2 de dezembro. O trading abre no lançamento."
            has("taxa", "taxas", "como ganham", "como vocês ganham") ->
                "Cada operação na Prognos Labs tem uma pequena taxa, e essa taxa é como ganhamos dinheiro. Não vendemos os dados dos usuários."
            has("pontos", "ponto", "indicação", "indicacao", "referência", "referencia") ->
                "A Prognos Labs tem um sistema de pontos, e você ganha pontos trazendo pessoas com seu link de indicação. Os pontos acompanham seu trading e suas indicações."
            else -> null
        }
        VoiceLanguage.GERMAN -> when {
            t.contains("prognos") && has("was ist", "was sind", "sag mir", "über", "uber") ->
                "Prognos Labs ist ein Prognosemarkt mit eingebauter KI-Prognose. Jeder Markt hat eine kurze Prognose in einfacher Sprache, und wenn der Markt sich auflöst, wird die Prognose am echten Ergebnis gemessen, damit die nächste schärfer wird. Menschen entscheiden über jeden Markt. Unsere KI erstellt oder löst keinen. Nutzer behalten ihre eigenen Wallets und Schlüssel, wir halten nie ihre Gelder."
            has("prognose", "ki") && has("wie", "was") ->
                "Jeder Markt hat eine kurze Prognose, die erklärt was passiert und was als Nächstes kommen kann. Wenn der Markt sich auf das echte Ergebnis auflöst, wird die Prognose an diesem Ergebnis gemessen, damit die nächste schärfer wird. Menschen entscheiden über jeden Markt. Unsere KI erstellt oder löst keinen."
            has("wallet") && has("verknüpfen", "verknupfen", "verbinden", "wie") ->
                "Prognos Labs hält keine Gelder. Du verknüpfst deine eigene Wallet und behältst deine eigenen Schlüssel. Wir sehen deinen privaten Schlüssel nie und halten deine Gelder nie."
            has("einzahlung", "einzahlen") ->
                "Einzahlungen sind USDT auf BSC, onchain, über deine eigene Wallet. Du signierst die Einzahlung aus deiner Wallet, und die Gelder bleiben deine."
            has("start", "eröffnung", "eroffnung", "wann öffnet", "wann offnet") ->
                "Wallet-Anmeldungen sind jetzt offen und schließen am 1. Dezember, vor dem öffentlichen Start am 2. Dezember. Das Trading öffnet zum Start."
            has("gebühr", "gebuhr", "gebühren", "gebuhren", "wie verdient") ->
                "Jeder Trade auf Prognos Labs trägt eine kleine Gebühr, und diese Gebühr ist wie wir Geld verdienen. Wir verkaufen keine Nutzerdaten."
            has("punkte", "punkt", "empfehlung", "empfehlen") ->
                "Prognos Labs hat ein Punktesystem, und du verdienst Punkte, wenn du Leute über deinen Empfehlungslink bringst. Die Punkte folgen deinem Trading und deinen Empfehlungen."
            else -> null
        }
        VoiceLanguage.TURKISH -> when {
            t.contains("prognos") && has("nedir", "bana söyle", "bana soyle", "hakkında", "hakkinda") ->
                "Prognos Labs, yapay zeka tahminli bir tahmin piyasasıdır. Her piyasada sade dille kısa bir tahmin olur, ve piyasa sonuçlanınca tahmin gerçek sonuca göre puanlanır, böylece bir sonraki daha keskin olur. Her piyasaya insanlar karar verir. Yapay zekamız hiçbirini oluşturmaz ya da sonuçlandırmaz. Kullanıcılar kendi cüzdanlarını ve anahtarlarını saklar, fonlarını asla tutmayız."
            has("tahmin", "yapay zeka") && has("nasıl", "nasil", "ne") ->
                "Her piyasada ne olduğunu ve sonra ne gelebileceğini açıklayan kısa bir tahmin olur. Piyasa gerçek sonuçla sonuçlanınca, tahmin bu sonuca göre puanlanır, böylece bir sonraki daha keskin olur. Her piyasaya insanlar karar verir. Yapay zekamız hiçbirini oluşturmaz ya da sonuçlandırmaz."
            has("cüzdan", "cuzdan") && has("bağla", "bagla", "nasıl", "nasil") ->
                "Prognos Labs fon tutmaz. Kendi cüzdanını bağlarsın ve kendi anahtarlarını saklarsın. Özel anahtarını asla görmeyiz ve fonlarını asla tutmayız."
            has("yatırma", "yatirma", "para yatırma", "para yatirma") ->
                "Yatırmalar BSC üzerinde USDT ile, zincir üstünde, kendi cüzdanından yapılır. Yatırmayı cüzdanından imzalarsın, ve fonlar senin kalır."
            has("lansman", "açılış", "acilis", "ne zaman açılıyor", "ne zaman aciliyor") ->
                "Cüzdan kayıtları şu an açık ve 2 Aralık'taki halka açık lansmandan önce, 1 Aralık'ta kapanıyor. Trading lansmanla birlikte açılıyor."
            has("ücret", "ucret", "komisyon", "nasıl kazanıyorsunuz", "nasil kazaniyorsunuz") ->
                "Prognos Labs üzerindeki her işlemde küçük bir ücret vardır, ve para kazanma yolumuz bu ücrettir. Kullanıcı verilerini satmıyoruz."
            has("puan", "referans", "davet") ->
                "Prognos Labs'ın bir puan sistemi var, ve referans bağlantınla insanları getirerek puan kazanırsın. Puanlar tradingini ve referanslarını takip eder."
            else -> null
        }
        VoiceLanguage.ARABIC -> when {
            t.contains("prognos") && has("ما هو", "ما هي", "ما هذا", "أخبرني", "اخبرني", "عن") ->
                "Prognos Labs سوق توقعات مع تنبؤات ذكاء اصطناعي مدمجة. كل سوق يحمل تنبؤا قصيرا بلغة بسيطة، وعندما ينتهي السوق يقيم التنبؤ مقابل النتيجة الحقيقية، فيكون التالي أدق. الناس يقررون كل سوق. ذكاؤنا الاصطناعي لا ينشئ أي سوق ولا يحسمه. المستخدمون يحتفظون بمحافظهم ومفاتيحهم، ولا نحتفظ بأموالهم أبدا."
            has("تنبؤ", "ذكاء") && has("كيف", "ما") ->
                "كل سوق يحمل تنبؤا قصيرا يشرح ما يحدث وما قد يأتي بعد ذلك. عندما ينتهي السوق على النتيجة الحقيقية، يقيم التنبؤ مقابل تلك النتيجة، فيكون التالي أدق. الناس يقررون كل سوق. ذكاؤنا الاصطناعي لا ينشئ أي سوق ولا يحسمه."
            has("محفظة") && has("ربط", "كيف") ->
                "Prognos Labs لا تحتفظ بالأموال. تربط محفظتك الخاصة وتحتفظ بمفاتيحك الخاصة. لا نرى مفتاحك الخاص أبدا ولا نحتفظ بأموالك أبدا."
            has("إيداع", "ايداع") ->
                "الإيداعات بعملة USDT على شبكة BSC، على السلسلة، من خلال محفظتك الخاصة. توقع الإيداع من محفظتك، وتبقى الأموال لك."
            has("الإطلاق", "الاطلاق", "الافتتاح", "متى يفتح") ->
                "تسجيل المحافظ مفتوح الآن ويغلق في 1 ديسمبر، قبل الإطلاق العام في 2 ديسمبر. يفتح التداول مع الإطلاق."
            has("رسوم", "عمولة", "كيف تربحون") ->
                "كل صفقة على Prognos Labs تحمل رسما صغيرا، وهذا الرسم هو كيف نكسب المال. لا نبيع بيانات المستخدمين."
            has("نقاط", "نقطة", "إحالة", "احالة", "دعوة") ->
                "لدى Prognos Labs نظام نقاط، وتكسب نقاطا بجلب الأشخاص عبر رابط الإحالة الخاص بك. النقاط تتبع تداولك وإحالاتك."
            else -> null
        }
        VoiceLanguage.ENGLISH -> null
    }
}

/**
 * The latest news about one coin, from the news feed the app already
 * fetches (api.php?action=news). An item counts when it is tagged
 * with the coin, or its headline names the coin. The list is in feed
 * order, newest first. Nothing here is invented: when nothing
 * matches, the list is empty and the voice says so.
 */
fun latestNewsFor(items: List<NewsItem>, asset: String): List<NewsItem> {
    val code = asset.uppercase()
    val label = (ASSET_LABELS[code] ?: code).lowercase()
    return items.filter { item ->
        item.tags.orEmpty().any {
            it.equals(code, ignoreCase = true) || it.lowercase() == label
        } || (item.title ?: "").lowercase().contains(code.lowercase()) ||
            (item.title ?: "").lowercase().contains(label)
    }
}
