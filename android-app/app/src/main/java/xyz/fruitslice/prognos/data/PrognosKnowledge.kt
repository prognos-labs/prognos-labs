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
VoiceLanguage.IGBO -> when {
    t.contains("prognos") && has("gịnị bụ", "gini bu", "kedu ihe bụ", "kedu ihe bu", "gwa m", "maka", "what is") ->
        "Prognos Labs bụ ahịa amụma nwere amụma AI. Ahịa ọ bụla nwere amụma dị mkpirikpi n'asụsụ dị mfe, ma mgbe ahịa mechiri a na-atụle amụma ahụ na nsonaazụ eziokwu ka nke ọzọ wee ka mma. Ndị mmadụ na-ekpebi ahịa ọ bụla; AI anyị anaghị emepụta ma ọ bụ kpebie ahịa. Ndị ọrụ na-ejide akpa ego na mkpịsị ugodi ha, anyị anaghị ejide ego ha."
    has("amụma", "amuma", "forecast", "ai") && has("kedu", "gịnị", "gini", "how", "what") ->
        "Ahịa ọ bụla nwere amụma dị mkpirikpi na-akọwa ihe na-eme na ihe nwere ike ịbịa. Mgbe ahịa mechiri na nsonaazụ eziokwu, a na-atụle amụma ahụ ka nke ọzọ wee ka mma. Ndị mmadụ na-ekpebi ahịa ọ bụla; AI anyị anaghị emepụta ma ọ bụ kpebie ahịa."
    has("wallet", "akpa ego") && has("jikọọ", "jikoo", "connect", "link", "kedu", "how") ->
        "Prognos Labs anaghị ejide ego. Ị na-ejikọ akpa ego gị ma debe mkpịsị ugodi gị. Anyị anaghị ahụ mkpịsị ugodi nzuzo gị ma anyị anaghị ejide ego gị."
    has("tinye ego", "nkwụnye", "nkwunye", "deposit") ->
        "Nkwụnye ego bụ USDT na BSC, n'elu blockchain, site na akpa ego gị. Ị na-abịanye aka na nkwụnye ego site na akpa ego gị, ego ahụ bụkwa nke gị."
    has("mmeghe", "mbido", "launch", "mgbe", "submission") ->
        "Ndebanye aha akpa ego emeghela ugbu a ma ga-emechi na 1 Disemba, tupu mmeghe ọha na 2 Disemba. Ahịa ga-emeghe n'ụbọchị mmeghe."
    has("ụgwọ", "ugwo", "fee", "fees", "ego") && has("kedu", "gịnị", "gini", "ole", "how", "what") ->
        "Ahịa ọ bụla na Prognos Labs nwere obere ụgwọ, nke ahụ bụkwa otu anyị si enweta ego. Anyị anaghị ere data ndị ọrụ."
    has("isi", "point", "points", "referral", "kpọbata", "kpobata") ->
        "Prognos Labs nwere usoro points, ị na-enwetakwa points mgbe ị kpọbatara ndị mmadụ site na njikọ referral gị. Points gị na-eso ahịa gị na ndị ị kpọbatara."
    else -> null
}

VoiceLanguage.YORUBA -> when {
    t.contains("prognos") && has("kini", "kí ni", "sọ fun mi", "so fun mi", "nipa", "what is") ->
        "Prognos Labs jẹ ọja asọtẹlẹ ti o ni asọtẹlẹ AI. Gbogbo ọja ni asọtẹlẹ kukuru ni ede ti o rọrun, nigbati ọja ba pari a o ṣe ayẹwo asọtẹlẹ naa pẹlu abajade gidi ki eyi ti o tẹle le dara sii. Awọn eniyan lo n pinnu gbogbo ọja; AI wa kii ṣẹda tabi pinnu ọja kankan. Awọn olumulo lo n tọju apamọwọ ati bọtini wọn, a kii di owo wọn mu."
    has("asọtẹlẹ", "asotele", "forecast", "ai") && has("bawo", "kini", "how", "what") ->
        "Gbogbo ọja ni asọtẹlẹ kukuru ti o ṣalaye ohun ti n ṣẹlẹ ati ohun ti o le tẹle. Nigbati ọja ba pari pẹlu abajade gidi, a o ṣe ayẹwo asọtẹlẹ naa ki eyi ti o tẹle le dara sii. Awọn eniyan lo n pinnu gbogbo ọja; AI wa kii ṣẹda tabi pinnu ọja kankan."
    has("wallet", "apamọwọ", "apamowo") && has("so pọ", "so po", "connect", "link", "bawo", "how") ->
        "Prognos Labs kii di owo mu. O n so apamọwọ tirẹ pọ ki o si tọju bọtini tirẹ. A kii ri bọtini ikọkọ rẹ, a kii si di owo rẹ mu."
    has("fi owo sii", "idogo", "deposit") ->
        "Fifi owo sii jẹ USDT lori BSC, onchain, nipasẹ apamọwọ tirẹ. O n fọwọsi idogo naa lati inu apamọwọ rẹ, owo naa si jẹ tirẹ."
    has("ifilọlẹ", "ifilole", "ṣii", "sii", "launch", "nigbawo", "submission") ->
        "Ififorukọsilẹ apamọwọ wa ni ṣiṣi bayii yoo si ti ni Oṣu Kejila ọjọ 1, ṣaaju ifilọlẹ gbogbogbo ni Oṣu Kejila ọjọ 2. Iṣowo yoo bẹrẹ ni ọjọ ifilọlẹ."
    has("owo iṣẹ", "owo ise", "fee", "fees", "bawo ni ẹ ṣe n ri owo") ->
        "Gbogbo iṣowo lori Prognos Labs ni owo iṣẹ kekere, iyẹn si ni ọna ti a fi n ri owo. A kii ta data awọn olumulo."
    has("aami", "point", "points", "referral", "itọkasi", "itokasi") ->
        "Prognos Labs ni eto points, o si n gba points nigbati o ba mu eniyan wa nipasẹ ọna asopọ referral rẹ. Points rẹ n tẹle iṣowo ati awọn ti o mu wa."
    else -> null
}

VoiceLanguage.HAUSA -> when {
    t.contains("prognos") && has("menene", "mecece", "fada min", "game da", "what is") ->
        "Prognos Labs kasuwar hasashe ce mai hasashen AI. Kowace kasuwa tana da gajeren hasashe cikin harshe mai sauki, kuma idan kasuwa ta rufe ana auna hasashen da sakamakon gaske domin na gaba ya fi kyau. Mutane ne ke yanke hukunci a kowace kasuwa; AI dinmu ba ya kirkiro ko rufe kasuwa. Masu amfani suna rike da walat da makullansu, ba ma rike kudadensu."
    has("hasashe", "forecast", "ai") && has("yaya", "menene", "how", "what") ->
        "Kowace kasuwa tana da gajeren hasashe da ke bayanin abin da ke faruwa da abin da ka iya zuwa. Idan kasuwa ta rufe da sakamakon gaske, ana auna hasashen domin na gaba ya fi inganci. Mutane ne ke yanke hukunci a kowace kasuwa; AI dinmu ba ya kirkiro ko rufe kasuwa."
    has("wallet", "walat") && has("hada", "haɗa", "connect", "link", "yaya", "how") ->
        "Prognos Labs ba ta rike kudade. Kana hada walat dinka kuma kana rike makullanka. Ba ma ganin sirrin makullinka kuma ba ma rike kudinka."
    has("ajiya", "saka kudi", "deposit") ->
        "Ajiyar kudi tana amfani da USDT akan BSC, kai tsaye ta walat dinka. Kana sanya hannu daga walat dinka, kuma kudin naka ne."
    has("kaddamarwa", "bude", "buɗe", "launch", "yaushe", "submission") ->
        "Rijistar walat tana bude yanzu kuma za ta rufe ranar 1 ga Disemba, kafin kaddamarwa ga jama'a ranar 2 ga Disemba. Ciniki zai bude a ranar kaddamarwa."
    has("kudin aiki", "fee", "fees", "yaya kuke samun kudi") ->
        "Kowane ciniki a Prognos Labs yana da karamin kudin aiki, kuma ta haka muke samun kudi. Ba ma sayar da bayanan masu amfani."
    has("maki", "point", "points", "referral", "gayyata") ->
        "Prognos Labs tana da tsarin points, kuma kana samun points idan ka kawo mutane ta hanyar link dinka na referral. Points suna bin cinikinka da wadanda ka gayyata."
    else -> null
}

VoiceLanguage.HEBREW -> when {
    t.contains("prognos") && has("מה זה", "מהי", "תגיד לי", "ספר לי", "על") ->
        "Prognos Labs היא פלטפורמת שווקי תחזיות עם תחזיות AI מובנות. לכל שוק יש תחזית קצרה בשפה פשוטה, וכשהשוק נסגר התחזית נמדדת מול התוצאה האמיתית כדי שהבאה תהיה חדה יותר. אנשים מכריעים כל שוק; ה-AI שלנו לעולם לא יוצר או מכריע שוק. המשתמשים שומרים את הארנק והמפתחות שלהם, ואנחנו לעולם לא מחזיקים בכספים."
    has("תחזית", "תחזיות", "ai", "בינה") && has("איך", "מה") ->
        "לכל שוק יש תחזית קצרה שמסבירה מה קורה ומה עשוי לקרות. כשהשוק נסגר על התוצאה האמיתית, התחזית מקבלת ציון מול התוצאה כדי שהתחזית הבאה תהיה מדויקת יותר. אנשים מכריעים כל שוק; ה-AI שלנו לעולם לא יוצר או מכריע שוק."
    has("ארנק", "wallet") && has("לחבר", "חיבור", "לקשר", "איך") ->
        "Prognos Labs היא לא משמורתית. אתה מחבר את הארנק שלך ושומר את המפתחות אצלך. אנחנו לעולם לא רואים את המפתח הפרטי שלך ולא מחזיקים בכספים שלך."
    has("הפקדה", "להפקיד", "deposit") ->
        "ההפקדות הן ב-USDT על רשת BSC, על השרשרת, דרך הארנק שלך. אתה חותם על ההפקדה מהארנק שלך והכספים נשארים שלך."
    has("השקה", "מתי נפתח", "launch", "הרשמה") ->
        "הגשת הארנקים פתוחה כעת ונסגרת ב-1 בדצמבר, לפני ההשקה הציבורית ב-2 בדצמבר. המסחר ייפתח עם ההשקה."
    has("עמלה", "עמלות", "איך אתם מרוויחים", "fee") ->
        "לכל עסקה ב-Prognos Labs יש עמלה קטנה, וכך אנחנו מרוויחים. אנחנו לא מוכרים נתוני משתמשים."
    has("נקודות", "הפניה", "הפניות", "חבר מביא חבר", "point", "referral") ->
        "ל-Prognos Labs יש מערכת נקודות, ואתה צובר נקודות על הבאת משתמשים דרך קישור ההפניה שלך. הנקודות עוקבות אחרי המסחר וההפניות שלך."
    else -> null
}

VoiceLanguage.CHINESE -> when {
    t.contains("prognos") && has("是什么", "介绍", "告诉我", "关于") ->
        "Prognos Labs 是一个内置 AI 预测的预测市场平台。每个市场都附有简短直白的预测，市场结算后会对照真实结果评分，让下一次预测更精准。每个市场都由人决定，我们的 AI 从不创建或结算市场。用户自己保管钱包和私钥，我们从不托管资金。"
    has("预测", "ai", "人工智能") && has("怎么", "如何", "什么") ->
        "每个市场都带有简短预测，说明当前动态和可能走向。市场按真实结果结算后，系统会为预测评分以提升后续准确度。每个市场都由人决定，我们的 AI 从不创建或结算市场。"
    has("钱包", "wallet") && has("连接", "绑定", "怎么", "如何") ->
        "Prognos Labs 是非托管平台。你连接自己的钱包并保管自己的私钥，我们从不查看你的私钥，也不托管你的资金。"
    has("充值", "存款", "入金", "deposit") ->
        "充值通过你自己的钱包在 BSC 链上使用 USDT 完成。你在自己的钱包中签名，资金始终归你所有。"
    has("上线", "发布", "开放", "提交", "launch") ->
        "钱包提交现已开放，将于 12 月 1 日截止，随后于 12 月 2 日公开发布。交易将在发布时开放。"
    has("手续费", "费用", "怎么赚钱", "盈利", "fee") ->
        "Prognos Labs 的每笔交易收取少量手续费，这就是我们的盈利方式。我们不出售用户数据。"
    has("积分", "推荐", "邀请", "返佣", "point", "referral") ->
        "Prognos Labs 设有积分系统，你可以通过推荐链接邀请好友来赚取积分。积分与你的交易和推荐记录挂钩。"
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
