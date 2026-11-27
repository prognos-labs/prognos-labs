package xyz.prognoslabs.app.data

import xyz.prognoslabs.app.model.ASSET_LABELS
import xyz.prognoslabs.app.model.NewsItem

/**
 * What the voice knows about Prognos Labs itself. A plain data file
 * in the app: no network, no paid service, no key. Everything here
 * is a fact about the platform as it is today. When a question is
 * not covered here, the voice says so plainly and never invents an
 * answer.
 *
 * Standing facts (Flint, 22 Nov): wallet submissions close Dec 1,
 * public launch Dec 2, trading is open for testers ahead of launch, deposits are
 * USDT on BSC through the user's own wallet, the platform is
 * non-custodial, every trade carries a small fee, there is a points
 * system and referrals. The liquidity backer is never named.
 */

/**
 * The brand name as Groq actually hears it. The multilingual real-speech
 * test (25 Nov) caught these variants: prognose, pronios, prognus,
 * prognoz, provenos, probe news, nefrognos, provenal. Matching on them
 * here means "what is Prognos Labs" is answered even when the name is
 * misheard. German keeps the plain check: "Prognose" is the German word
 * for forecast, so the variant would steal forecast questions.
 */
private fun isPrognos(t: String) = t.contains("prognos") || t.contains("prognose") ||
    t.contains("pronios") || t.contains("prognus") || t.contains("prognoz") ||
    t.contains("provenos") || t.contains("probe news") || t.contains("nefrognos") ||
    t.contains("provenal") ||
    // v1.1.29: Groq mishears the brand as "food, Ms. Labs" — the
    // first word is lost but "labs" survives, so a "tell me about
    // ... labs" question is still about Prognos Labs. Every use of
    // this check also needs an about word, so a bare "labs" never
    // answers on its own.
    t.contains("labs")

/**
 * Traditional to simplified Chinese, for the trigger characters only.
 * Groq sometimes returns traditional script ("買什麼" for "买什么"),
 * which does not match the simplified triggers. Applied to the
 * transcript before matching, Chinese only.
 */
private fun simplifiedChinese(t: String): String {
    val map = mapOf(
        '買' to '买', '麼' to '么', '實' to '实', '價' to '价', '錢' to '钱',
        '預' to '预', '測' to '测', '賣' to '卖', '還' to '还', '過' to '过',
        '來' to '来', '會' to '会', '個' to '个',
        '開' to '开', '關' to '关', '點' to '点'
    )
    val out = StringBuilder(t.length)
    for (ch in t) out.append(map[ch] ?: ch)
    return out.toString()
}

fun platformAnswer(lowerText: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): String? {
    val t = if (language == VoiceLanguage.CHINESE) simplifiedChinese(lowerText) else lowerText
    if (language != VoiceLanguage.ENGLISH) return platformAnswerTranslated(t, language)
    return when {
        // "What is Prognos Labs?" / "Tell me about Prognos Labs" /
        // "What's Prognos Labs?" / "How does Prognos Labs work?"
        // v1.1.15: "what's" and "what does" and "how does" asked
        // the same question and were refused, because only "what
        // is" matched. "How does" stays out of the forecast branch:
        // a forecast question still gets the forecast answer.
        // Topic 1: How trading works on Prognos Labs, step by step
        (t.contains("trading") || t.contains("trade")) &&
            (t.contains("how does") || t.contains("how do") || t.contains("step by step") || t.contains("work")) ->
            "First, pick a market and choose Yes or No. Next, set a price and an amount. " +
                "I will read the order back to you, and you say yes or no. " +
                "Only after you say yes does anything move forward, and once you say yes, the order goes through."

        // Topic 2: How to enter a market
        (t.contains("enter") || t.contains("join") || t.contains("pick")) && t.contains("market") ->
            "Find a market in the app, such as one asking if something will happen by a set date. " +
                "Pick Yes if you think it will happen. Pick No if you think it will not happen."

        // Topic 3: How to set a price and an amount
        // Price is the one shown on the chart. Smallest amount $5, no largest amount (Flint, 25 Nov).
        (t.contains("price") && t.contains("amount")) ||
            ((t.contains("set") || t.contains("choose")) && (t.contains("price") || t.contains("amount"))) ->
            "Price is what you pay for one share in that market, and it is the price shown on the chart. " +
                "Amount is how much money you put in. The smallest amount is $5, and there is no largest amount. " +
                "Say your side, price, and amount in your voice order, and I will read all of it back before anything happens."

        // Topic 4: What happens after you say yes to the read back
        (t.contains("after") || t.contains("when")) && (t.contains("say yes") || t.contains("confirm")) ->
            "When you say yes, the order is filled and the position is yours until the market settles or you sell it early. " +
                "If you say no, nothing happens and no order is placed."

        // Portfolio question form that does not navigate
        t.contains("portfolio") && (t.contains("how") || t.contains("what") || t.contains("where") || t.contains("see") || t.contains("check")) ->
            "Your portfolio is on the Portfolio tab. It shows your balance and your positions. It is empty until you deposit."

        // Topic 5: How a market settles and how winnings are paid
        t.contains("settle") || t.contains("winnings") || t.contains("get paid") || t.contains("payout") ->
            "When the event is decided, the market settles. A human decides the result, not the app on its own. " +
                "Winning shares are paid out and losing shares are not. " +
                "Winnings are paid in 30 days, in USDT. " +
                "Winnings go back to you in the app, linked to your own wallet, because Prognos Labs is non custodial."

        // Topic 7: How to ask about an open position
        t.contains("open position") || t.contains("positions do i have") || t.contains("my position") ->
            "You can ask me what positions you have open. I can tell you the market, the side you picked, the amount you put in, and whether it is still open. " +
                "If I do not have live position data for you right now, I will tell you I cannot see an open position."

        // Topic 8: Place this trade now (trading is open)
        // v1.1.22: a bare trade request ("can you take a trade for
        // me") is a trade to start, not a question to refuse. It
        // gets the guided answer: which market, which side, price
        // and amount, then a read-back. Never the out-of-scope line.
        t.contains("place this trade") || t.contains("trade for me now") ||
            t.contains("trade for me") || t.contains("take a trade") ||
            t.contains("place a trade") || t.contains("make a trade") ||
            t.contains("do a trade") || t.contains("take this trade") ||
            t.contains("buy for me") || t.contains("place an order") ||
            t.contains("make an order") ->
            "Trading is open. Tell me the market, your side, the price and the amount, " +
                "and I will read the order back before anything goes through."
        // Is trading open?
        t.contains("is trading open") || t.contains("trading is closed") ||
            t.contains("while trading is closed") ->
            "Trading is open now."

        // Topic 9: What the voice cannot do
        t.contains("what should i buy") || t.contains("give me a pick") || t.contains("financial advice") ||
            t.contains("without asking") || t.contains("cannot do") || t.contains("can't do") || t.contains("limits") ->
            "I never trade without a read back and a yes from you. I never create or settle a market, and I cannot give financial advice or tell you what to buy. " +
                "I never name the liquidity backer or state a fee percentage, only that there is a small fee on every trade. " +
                "If you want, I can explain how a market works instead."

        isPrognos(t) && (t.contains("what is") || t.contains("what are") ||
            t.contains("what's") || t.contains("whats") || t.contains("what does") ||
            (t.contains("how does") && !t.contains("forecast") && !t.contains("ai")) ||
            t.contains("tell me") || t.contains("about") || t.contains("who are") ||
            t.contains("what can you do") || t.contains("explain") ||
            t == "prognos" || t == "prognos labs" || t == "prognose" || t == "pronios" ||
            t == "prognus" || t == "prognoz" || t == "provenos" || t == "probe news") ->
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
        t.contains("wallet") && (t.contains("link") || t.contains("connect") || t.contains("how") || t.trim() == "wallet") ->
            "Prognos Labs is non-custodial. You link your own wallet and keep your own keys. " +
                "We never see your private key and we never hold your funds."
        // "How do I deposit?"
        t.contains("deposit") ->
            "Deposits are USDT on BSC, onchain, through your own wallet. You sign the deposit " +
                "from your wallet, and the funds stay yours."
        // "When is the launch?" / "When do wallet submissions close?"
        t.contains("launch") || t.contains("go live") || t.contains("submission") ->
            "Wallet submissions are open now and close December 1, ahead of the public launch " +
                "on December 2. Trading is open now."
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
 * Chooses the reply language for a turn using the exact fallback order:
 * 1. Detected spoken language, if supported and a translated answer exists.
 * 2. Set language from Settings, if a translated answer exists.
 * 3. English.
 *
 * Never uses Igbo unless the recognizer explicitly returned an Igbo signal.
 */
fun chooseReplyLanguage(
    detectedLanguage: VoiceLanguage?,
    setLanguage: VoiceLanguage,
    hasAnswerIn: (VoiceLanguage) -> Boolean
): VoiceLanguage {
    if (detectedLanguage != null && hasAnswerIn(detectedLanguage)) {
        return detectedLanguage
    }
    if (hasAnswerIn(setLanguage)) {
        return setLanguage
    }
    return VoiceLanguage.ENGLISH
}

/**
 * Resolves a platform knowledge answer and the language used for the reply.
 * Returns null only when no answer exists in the detected language, the set
 * language, or English.
 */
fun resolvePlatformAnswer(
    lowerText: String,
    detectedLanguage: VoiceLanguage?,
    setLanguage: VoiceLanguage
): Pair<String, VoiceLanguage>? {
    if (detectedLanguage != null) {
        val detectedAnswer = platformAnswer(lowerText, detectedLanguage)
        if (detectedAnswer != null) return detectedAnswer to detectedLanguage
    }
    if (setLanguage != detectedLanguage) {
        val setAnswer = platformAnswer(lowerText, setLanguage)
        if (setAnswer != null) return setAnswer to setLanguage
    }
    if (detectedLanguage != VoiceLanguage.ENGLISH && setLanguage != VoiceLanguage.ENGLISH) {
        val englishAnswer = platformAnswer(lowerText, VoiceLanguage.ENGLISH)
        if (englishAnswer != null) return englishAnswer to VoiceLanguage.ENGLISH
    }
    return null
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
            has("cómo funciona el trading", "como funciona el trading", "paso a paso") ->
                "Primero eliges un mercado y eliges Sí o No. Luego fijas un precio y una cantidad. Te leo la orden y dices sí o no. Solo después de decir sí avanza algo."
            has("entrar en un mercado", "entrar al mercado", "elegir un mercado") ->
                "Busca un mercado en la app, como uno que pregunta si algo pasará antes de una fecha. Elige Sí si crees que pasará. Elige No si crees que no pasará."
            has("precio y cantidad", "fijar precio", "poner precio", "cantidad") && has("precio") ->
                "El precio es lo que pagas por una acción en ese mercado, y es el precio que se muestra en el gráfico. La cantidad es el dinero que pones. La cantidad mínima es de $5 y no hay cantidad máxima. Di tu lado, el precio y la cantidad en tu orden de voz, y te lo leeré todo antes de que pase nada."
            has("después de decir sí", "despues de decir si", "al confirmar") ->
                "Cuando dices sí, la orden se completa y la posición es tuya hasta que el mercado cierre o la vendas antes. Si dices no, no pasa nada."
            has("cartera", "posiciones", "portfolio") && has("cómo", "como", "dónde", "donde", "ver", "qué", "que") ->
                "Tu cartera está en la pestaña Cartera. Muestra tu saldo y tus posiciones. Está vacía hasta que deposites."
            has("resuelve", "liquidación", "liquidacion", "ganancias", "cobrar", "pagan") ->
                "Cuando se decide el evento, el mercado se resuelve. Una persona decide el resultado, no la app sola. Las acciones ganadoras se pagan y las demás no. Las ganancias se pagan en 30 días, en USDT. Las ganancias vuelven a ti en la app, ligada a tu propia billetera, porque no guardamos fondos."
            has("posiciones abiertas", "mi posición", "mi posicion") ->
                "Puedes preguntarme qué posiciones tienes abiertas. Puedo decirte el mercado, el lado que elegiste, la cantidad que pusiste y si sigue abierta. Si no tengo datos en vivo ahora, te diré que no puedo ver una posición abierta."
            has("haz esta operación ahora", "pon esta orden ahora") ->
                "El trading está abierto. Dime el mercado, tu lado, el precio y la cantidad, y te leeré la orden antes de que pase nada."
            has("trading cerrado") ->
                "El trading está abierto ahora."
            has("qué debería comprar", "que deberia comprar", "consejo", "sin preguntar", "qué no puedes hacer", "que no puedes hacer") ->
                "Nunca opero sin leer la orden y recibir tu sí. Nunca creo ni resuelvo un mercado, y no doy consejos financieros ni digo qué comprar. Nunca nombro al proveedor de liquidez ni doy un porcentaje de comisión, solo que hay una pequeña comisión en cada operación. Si quieres, te explico cómo funciona el mercado."
            isPrognos(t) && has("qué es", "que es", "qué son", "cuéntame", "cuentame", "dime", "sobre") ->
                "Prognos Labs es un mercado de predicción con pronósticos de IA. Cada mercado lleva un pronóstico corto en lenguaje sencillo, y cuando el mercado se resuelve el pronóstico se puntúa contra el resultado real, así el siguiente es mejor. Las personas deciden cada mercado. Nuestra IA nunca crea ni resuelve uno. Los usuarios guardan sus propias billeteras y claves, nunca guardamos sus fondos."
            has("pronóstico", "pronostico", "predicción", "prediccion", "ia") && has("cómo", "como", "qué", "que") ->
                "Cada mercado lleva un pronóstico corto que explica lo que está pasando y lo que puede venir después. Cuando el mercado se resuelve con el resultado real, el pronóstico se puntúa contra ese resultado, así el siguiente es mejor. Las personas deciden cada mercado. Nuestra IA nunca crea ni resuelve uno."
            has("billetera", "cartera") && has("vincular", "conectar", "cómo", "como") ->
                "Prognos Labs no guarda fondos. Vinculas tu propia billetera y guardas tus propias claves. Nunca vemos tu clave privada y nunca guardamos tus fondos."
            has("depósito", "deposito", "depositar") ->
                "Los depósitos son USDT en BSC, en cadena, a través de tu propia billetera. Firmas el depósito desde tu billetera, y los fondos siguen siendo tuyos."
            has("lanzamiento", "cuándo abre", "cuando abre", "apertura") ->
                "Las inscripciones de billeteras están abiertas ahora y cierran el 1 de diciembre, antes del lanzamiento público el 2 de diciembre. El trading está abierto ahora."
            has("comisión", "comision", "tarifa", "cómo ganan", "como ganan", "cómo ganas") ->
                "Cada operación en Prognos Labs lleva una pequeña comisión, y esa comisión es como ganamos dinero. No vendemos los datos de los usuarios."
            has("puntos", "punto", "referido", "referencia", "referir") ->
                "Prognos Labs tiene un sistema de puntos, y ganas puntos por traer personas con tu enlace de referencia. Los puntos siguen tu trading y tus referencias."
            else -> null
        }
        VoiceLanguage.FRENCH -> when {
            has("comment fonctionne le trading", "étape par étape", "etape par etape") ->
                "D'abord tu choisis un marché et Oui ou Non. Ensuite tu fixes un prix et un montant. Je te relis l'ordre et tu dis oui ou non. Rien n'avance avant ton oui."
            has("entrer dans un marché", "entrer sur un marché", "entrez dans un marché", "entrez sur un marché", "choisir un marché") ->
                "Trouve un marché dans l'application, par exemple si quelque chose va arriver avant une date. Choisis Oui si tu penses que cela arrivera. Choisis Non si tu penses le contraire."
            has("prix et montant", "priez mon temps", "fixer le prix", "montant") && has("prix") ->
                "Le prix est ce que tu paies pour une part sur ce marché, et c'est le prix affiché sur le graphique. Le montant est l'argent que tu mets. Le montant minimum est de 5 $, et il n'y a pas de montant maximum. Dis ton côté, ton prix et ton montant à la voix, et je relirai tout avant qu'il ne se passe quoi que ce soit."
            has("après avoir dit oui", "apres avoir dit oui", "après confirmation") ->
                "Quand tu dis oui, l'ordre est exécuté et la position est à toi jusqu'au règlement ou une vente anticipée. Si tu dis non, rien ne se passe."
            has("portefeuille", "positions", "portfolio") && has("comment", "où", "ou", "voir", "quel") ->
                "Ton portefeuille est dans l'onglet Portefeuille. Il affiche ton solde et tes positions. Il est vide tant que tu ne déposes pas."
            has("règlement", "reglement", "résout", "gains", "payé", "paye") ->
                "Quand l'événement est décidé, le marché se règle. Une personne décide du résultat, pas l'application seule. Les parts gagnantes sont payées et les autres non. Les gains sont payés en 30 jours, en USDT. Les gains reviennent dans l'application, liée à ton propre portefeuille, car nous ne gardons pas les fonds."
            has("positions ouvertes", "ma position", "mes positions") ->
                "Tu peux me demander quelles positions tu as ouvertes. Je peux te dire le marché, le côté choisi, le montant mis et si la position est encore ouverte. Si je n'ai pas de données en direct maintenant, je te dirai que je ne vois aucune position ouverte."
            has("passe cet ordre maintenant") ->
                "Le trading est ouvert. Dis-moi le marché, ton côté, le prix et le montant, et je te relirai l'ordre avant qu'il ne se passe quoi que ce soit."
            has("trading fermé", "trading ferme") ->
                "Le trading est ouvert maintenant."
            has("quoi acheter", "conseil", "sans demander", "ce que tu ne peux pas faire") ->
                "Je ne trade jamais sans relecture et sans ton oui. Je ne crée ni ne règle aucun marché, et je ne donne pas de conseil financier ni de choix d'achat. Je ne nomme jamais le fournisseur de liquidité ni de pourcentage de frais, seulement qu'il y a une petite commission sur chaque opération. Si tu veux, je peux t'expliquer comment fonctionne le marché."
            isPrognos(t) && has("c'est quoi", "qu'est-ce que", "qu est-ce que", "dis-moi", "dis moi", "à propos", "a propos") ->
                "Prognos Labs est un marché de prédiction avec des prévisions IA. Chaque marché porte une prévision courte en langage simple, et quand le marché se résout la prévision est notée contre le vrai résultat, pour que la suivante soit meilleure. Ce sont les gens qui décident de chaque marché. Notre IA n'en crée ni n'en résout jamais. Les utilisateurs gardent leurs propres portefeuilles et clés, nous ne gardons jamais leurs fonds."
            has("prévision", "prevision", "ia") && has("comment", "que", "quoi") ->
                "Chaque marché porte une prévision courte qui explique ce qui se passe et ce qui peut venir ensuite. Quand le marché se résout sur le vrai résultat, la prévision est notée contre ce résultat, pour que la suivante soit meilleure. Ce sont les gens qui décident de chaque marché. Notre IA n'en crée ni n'en résout jamais."
            has("portefeuille") && has("lier", "connecter", "comment") ->
                "Prognos Labs ne garde pas les fonds. Vous liez votre propre portefeuille et gardez vos propres clés. Nous ne voyons jamais votre clé privée et nous ne gardons jamais vos fonds."
            has("dépôt", "depot", "déposer", "deposer") ->
                "Les dépôts sont en USDT sur BSC, en chaîne, via votre propre portefeuille. Vous signez le dépôt depuis votre portefeuille, et les fonds restent à vous."
            has("lancement", "quand ouvre", "ouverture") ->
                "Les inscriptions de portefeuilles sont ouvertes maintenant et ferment le 1er décembre, avant le lancement public le 2 décembre. Le trading est ouvert maintenant."
            has("frais", "commission", "comment gagnez", "comment vous gagnez") ->
                "Chaque opération sur Prognos Labs porte une petite commission, et c'est comme cela que nous gagnons de l'argent. Nous ne vendons pas les données des utilisateurs."
            has("points", "point", "parrainage", "référence", "reference") ->
                "Prognos Labs a un système de points, et vous gagnez des points en amenant des gens avec votre lien de parrainage. Les points suivent votre trading et vos parrainages."
            else -> null
        }
        VoiceLanguage.PORTUGUESE -> when {
            has("como funciona o trading", "passo a passo") ->
                "Primeiro você escolhe um mercado e escolhe Sim ou Não. Depois define um preço e um valor. Eu leio a ordem de volta e você diz sim ou não. Só depois do seu sim algo avança."
            has("entrar em um mercado", "entrar no mercado", "escolher um mercado") ->
                "Encontre um mercado no app, como um que pergunta se algo vai acontecer até uma data. Escolha Sim se acha que vai acontecer. Escolha Não se acha que não vai."
            has("preço e valor", "preco e valor", "definir preço", "valor") && has("preço", "preco") ->
                "Preço é o que você paga por uma cota nesse mercado, e é o preço que aparece no gráfico. Valor é quanto dinheiro você coloca. O valor mínimo é $5 e não há valor máximo. Diga o lado, o preço e o valor na sua ordem de voz, e eu leio tudo de volta antes de qualquer coisa acontecer."
            has("depois de dizer sim", "após confirmar", "apos confirmar") ->
                "Quando você diz sim, a ordem é executada e a posição é sua até o mercado resolver ou você vender antes. Se disser não, nada acontece."
            has("carteira", "posições", "posicoes", "portfolio") && has("como", "onde", "ver", "qual") ->
                "Sua carteira fica na aba Carteira. Ela mostra seu saldo e suas posições. Fica vazia até você depositar."
            has("resolve", "liquidação", "liquidacao", "ganhos", "receber", "pago") ->
                "Quando o evento é decidido, o mercado se resolve. Uma pessoa decide o resultado, não o app sozinho. As cotas vencedoras são pagas e as outras não. Os ganhos são pagos em 30 dias, em USDT. Os ganhos voltam para você no app, ligado à sua própria carteira, porque não guardamos fundos."
            has("posições abertas", "posicoes abertas", "minha posição", "minha posicao") ->
                "Você pode me perguntar quais posições tem abertas. Posso dizer o mercado, o lado escolhido, o valor colocado e se ainda está aberta. Se não houver dados ao vivo agora, vou dizer que não consigo ver uma posição aberta."
            has("faça essa ordem agora", "faca essa ordem agora") ->
                "O trading está aberto. Diga o mercado, o lado, o preço e o valor, e eu leio a ordem de volta antes de qualquer coisa acontecer."
            has("trading fechado") ->
                "O trading está aberto agora."
            has("o que devo comprar", "conselho", "sem perguntar", "o que você não pode fazer", "o que voce nao pode fazer") ->
                "Nunca opero sem ler a ordem de volta e receber seu sim. Nunca crio nem resolvo um mercado, e não dou conselho financeiro nem digo o que comprar. Nunca nomeio o provedor de liquidez nem digo uma taxa em porcentagem, apenas que há uma pequena taxa em cada operação. Se quiser, posso explicar como o mercado funciona."
            isPrognos(t) && has("o que é", "o que e", "me diz", "me fala", "sobre") ->
                "A Prognos Labs é um mercado de previsões com previsões de IA. Cada mercado tem uma previsão curta em linguagem simples, e quando o mercado se resolve a previsão é pontuada contra o resultado real, para a próxima ser melhor. As pessoas decidem cada mercado. Nossa IA nunca cria nem resolve um. Os usuários guardam suas próprias carteiras e chaves, nunca guardamos seus fundos."
            has("previsão", "previsao", "ia") && has("como", "o que") ->
                "Cada mercado tem uma previsão curta que explica o que está acontecendo e o que pode vir depois. Quando o mercado se resolve no resultado real, a previsão é pontuada contra esse resultado, para a próxima ser melhor. As pessoas decidem cada mercado. Nossa IA nunca cria nem resolve um."
            has("carteira") && has("vincular", "conectar", "como") ->
                "A Prognos Labs não guarda fundos. Você vincula sua própria carteira e guarda suas próprias chaves. Nunca vemos sua chave privada e nunca guardamos seus fundos."
            has("depósito", "deposito", "depositar") ->
                "Os depósitos são em USDT na BSC, on-chain, pela sua própria carteira. Você assina o depósito na sua carteira, e os fundos continuam sendo seus."
            has("lançamento", "lancamento", "quando abre", "abertura") ->
                "As inscrições de carteiras estão abertas agora e fecham em 1º de dezembro, antes do lançamento público em 2 de dezembro. O trading está aberto agora."
            has("taxa", "taxas", "como ganham", "como vocês ganham") ->
                "Cada operação na Prognos Labs tem uma pequena taxa, e essa taxa é como ganhamos dinheiro. Não vendemos os dados dos usuários."
            has("pontos", "ponto", "indicação", "indicacao", "referência", "referencia") ->
                "A Prognos Labs tem um sistema de pontos, e você ganha pontos trazendo pessoas com seu link de indicação. Os pontos acompanham seu trading e suas indicações."
            else -> null
        }
        VoiceLanguage.GERMAN -> when {
            has("wie funktioniert trading", "schritt für schritt", "schritt fur schritt") ->
                "Zuerst wählst du einen Markt und Ja oder Nein. Dann legst du Preis und Betrag fest. Ich lese den Auftrag vor und du sagst ja oder nein. Erst nach deinem Ja passiert etwas."
            has("markt betreten", "in einen markt", "markt auswählen", "markt auswahlen") ->
                "Finde einen Markt in der App, zum Beispiel ob etwas bis zu einem Datum passiert. Wähle Ja, wenn du glaubst, dass es passiert. Wähle Nein, wenn du glaubst, dass es nicht passiert."
            has("preis und betrag", "preis festlegen", "betrag") && has("preis") ->
                "Der Preis ist das, was du für einen Anteil in diesem Markt zahlst, und es ist der Preis, der im Diagramm angezeigt wird. Der Betrag ist das Geld, das du einsetzt. Der Mindestbetrag ist 5 $ und es gibt keinen Höchstbetrag. Sag deine Seite, deinen Preis und deinen Betrag per Sprache, und ich lese alles vor, bevor etwas passiert."
            has("nachdem ich ja sage", "nach dem ja", "nach bestätigung") ->
                "Wenn du ja sagst, wird der Auftrag ausgeführt und die Position gehört dir bis zur Abrechnung oder bis du vorher verkaufst. Wenn du nein sagst, passiert nichts."
            has("portfolio", "positionen") && has("wie", "wo", "sehen", "was") ->
                "Dein Portfolio ist auf dem Tab Portfolio. Es zeigt dein Guthaben und deine Positionen. Es ist leer, bis du einzahlst."
            has("abrechnung", "auflöst", "auflost", "gewinn", "auszahlung", "bezahlt") ->
                "Wenn das Ereignis entschieden ist, wird der Markt abgerechnet. Ein Mensch entscheidet das Ergebnis, nicht die App allein. Gewinnende Anteile werden ausgezahlt, die anderen nicht. Gewinne werden in 30 Tagen in USDT ausgezahlt. Gewinne gehen in der App an dich zurück, verbunden mit deiner eigenen Wallet, weil wir keine Gelder halten."
            has("offene positionen", "meine position", "welche positionen") ->
                "Du kannst mich fragen, welche Positionen du offen hast. Ich kann dir den Markt, deine Seite, deinen Betrag und den Status nennen. Wenn ich gerade keine Live-Daten für dich habe, sage ich dir, dass ich keine offene Position sehe."
            has("führe diesen trade jetzt aus") ->
                "Das Trading ist offen. Sag mir den Markt, deine Seite, den Preis und den Betrag, und ich lese den Auftrag vor, bevor etwas passiert."
            has("trading geschlossen") ->
                "Das Trading ist jetzt offen."
            has("was soll ich kaufen", "beratung", "ohne zu fragen", "was kannst du nicht") ->
                "Ich handle nie ohne Vorlesen und dein Ja. Ich erstelle oder entscheide keinen Markt und gebe keine Finanzberatung oder Kaufempfehlung. Ich nenne nie den Liquiditätsgeber und keinen Gebührensatz, sondern nur, dass jeder Trade eine kleine Gebühr hat. Wenn du willst, erkläre ich dir, wie der Markt funktioniert."
            t.contains("prognos") && has("was ist", "was sind", "sag mir", "über", "uber") ->
                "Prognos Labs ist ein Prognosemarkt mit eingebauter KI-Prognose. Jeder Markt hat eine kurze Prognose in einfacher Sprache, und wenn der Markt sich auflöst, wird die Prognose am echten Ergebnis gemessen, damit die nächste schärfer wird. Menschen entscheiden über jeden Markt. Unsere KI erstellt oder löst keinen. Nutzer behalten ihre eigenen Wallets und Schlüssel, wir halten nie ihre Gelder."
            has("prognose", "ki") && has("wie", "was") ->
                "Jeder Markt hat eine kurze Prognose, die erklärt was passiert und was als Nächstes kommen kann. Wenn der Markt sich auf das echte Ergebnis auflöst, wird die Prognose an diesem Ergebnis gemessen, damit die nächste schärfer wird. Menschen entscheiden über jeden Markt. Unsere KI erstellt oder löst keinen."
            has("wallet") && (has("verknüpfen", "verknupfen", "verbinden", "wie") || t.trim() == "wallet") ->
                "Prognos Labs hält keine Gelder. Du verknüpfst deine eigene Wallet und behältst deine eigenen Schlüssel. Wir sehen deinen privaten Schlüssel nie und halten deine Gelder nie."
            has("einzahlung", "einzahlen") ->
                "Einzahlungen sind USDT auf BSC, onchain, über deine eigene Wallet. Du signierst die Einzahlung aus deiner Wallet, und die Gelder bleiben deine."
            has("start", "eröffnung", "eroffnung", "wann öffnet", "wann offnet") ->
                "Wallet-Anmeldungen sind jetzt offen und schließen am 1. Dezember, vor dem öffentlichen Start am 2. Dezember. Das Trading ist jetzt offen."
            has("gebühr", "gebuhr", "gebühren", "gebuhren", "wie verdient") ->
                "Jeder Trade auf Prognos Labs trägt eine kleine Gebühr, und diese Gebühr ist wie wir Geld verdienen. Wir verkaufen keine Nutzerdaten."
            has("punkte", "punkt", "empfehlung", "empfehlen") ->
                "Prognos Labs hat ein Punktesystem, und du verdienst Punkte, wenn du Leute über deinen Empfehlungslink bringst. Die Punkte folgen deinem Trading und deinen Empfehlungen."
            else -> null
        }
        VoiceLanguage.TURKISH -> when {
            has("trading nasıl çalışır", "trading nasil calisir", "adım adım", "adim adim") ->
                "Önce bir piyasa seçersin ve Evet ya da Hayır seçersin. Sonra fiyat ve miktar belirlersin. Emri sana geri okurum ve sen evet ya da hayır dersin. Sadece evet dedikten sonra işlem ilerler."
            has("piyasaya nasıl girerim", "piyasaya nasil girerim", "piyasa seç") ->
                "Uygulamada bir piyasa bul, örneğin belirli bir tarihe kadar bir şeyin olup olmayacağını soran bir piyasa. Olacağını düşünüyorsan Evet seç. Olmayacağını düşünüyorsan Hayır seç."
            has("fiyat ve miktar", "fiyat belirle", "miktar") && has("fiyat") ->
                "Fiyat o piyasada bir pay için ödediğin tutardır, ve grafikte gösterilen fiyattır. Miktar koyduğun paradır. En küçük tutar 5 dolardır ve en büyük tutar yoktur. Sesli emrinde tarafını, fiyatını ve miktarını söyle, herhangi bir şey olmadan önce hepsini sana geri okurum."
            has("evet dedikten sonra", "onayladıktan sonra", "onayladiktan sonra") ->
                "Evet dersen emir gerçekleşir ve piyasa sonuçlanana ya da sen erken satana kadar pozisyon senin olur. Hayır dersen hiçbir şey olmaz."
            has("portföy", "portfoy", "pozisyon", "portfolio") && has("nasıl", "nasil", "nerede", "gör", "gor") ->
                "Portföyün Portföy sekmesinde yer alır. Bakiyeni ve pozisyonlarını gösterir. Yatırım yapana kadar boştur."
            has("sonuçlanır", "sonuclanir", "kazanç", "kazanc", "ödeme", "odeme") ->
                "Olay kesinleştiğinde piyasa sonuçlanır. Sonuca uygulama tek başına değil, bir insan karar verir. Kazanan paylara ödeme yapılır, diğerlerine yapılmaz. Kazançlar 30 gün içinde USDT olarak ödenir. Kazançlar uygulamada kendi cüzdanına bağlı olarak sana döner, çünkü fon tutmayız."
            has("açık pozisyon", "acik pozisyon", "pozisyonlarım", "pozisyonlarim") ->
                "Hangi açık pozisyonların olduğunu bana sorabilirsin. Sana piyasayı, seçtiğin tarafı, koyduğun miktarı ve hala açık olup olmadığını söyleyebilirim. Şu an canlı pozisyon verin yoksa açık pozisyon göremediğimi söylerim."
            has("bu işlemi şimdi yap") ->
                "Trading açık. Bana piyasayı, tarafını, fiyatı ve miktarı söyle, herhangi bir şey olmadan önce emri sana geri okurum."
            has("trading kapalı", "trading kapali") ->
                "Trading şu an açık."
            has("ne almalıyım", "ne almaliyim", "tavsiye", "sormadan", "neleri yapamazsın") ->
                "Geri okumadan ve senin evet onayın olmadan asla işlem yapmam. Hiçbir piyasayı oluşturmam ya da sonuçlandırmam, finansal tavsiye vermem ve ne alacağını söylemem. Likidite sağlayıcısının adını vermem ve ücret yüzdesi söylemem, sadece her işlemde küçük bir ücret olduğunu söylerim. İstersen piyasanın nasıl çalıştığını anlatabilirim."
            isPrognos(t) && has("nedir", "bana söyle", "bana soyle", "hakkında", "hakkinda") ->
                "Prognos Labs, yapay zeka tahminli bir tahmin piyasasıdır. Her piyasada sade dille kısa bir tahmin olur, ve piyasa sonuçlanınca tahmin gerçek sonuca göre puanlanır, böylece bir sonraki daha keskin olur. Her piyasaya insanlar karar verir. Yapay zekamız hiçbirini oluşturmaz ya da sonuçlandırmaz. Kullanıcılar kendi cüzdanlarını ve anahtarlarını saklar, fonlarını asla tutmayız."
            has("tahmin", "다흔인", "yapay zeka") && has("nasıl", "nasil", "ne") ->
                "Her piyasada ne olduğunu ve sonra ne gelebileceğini açıklayan kısa bir tahmin olur. Piyasa gerçek sonuçla sonuçlanınca, tahmin bu sonuca göre puanlanır, böylece bir sonraki daha keskin olur. Her piyasaya insanlar karar verir. Yapay zekamız hiçbirini oluşturmaz ya da sonuçlandırmaz."
            has("cüzdan", "cuzdan") && has("bağla", "bagla", "nasıl", "nasil") ->
                "Prognos Labs fon tutmaz. Kendi cüzdanını bağlarsın ve kendi anahtarlarını saklarsın. Özel anahtarını asla görmeyiz ve fonlarını asla tutmayız."
            has("yatırma", "yatirma", "para yatırma", "para yatirma") ->
                "Yatırmalar BSC üzerinde USDT ile, zincir üstünde, kendi cüzdanından yapılır. Yatırmayı cüzdanından imzalarsın, ve fonlar senin kalır."
            has("lansman", "açılış", "acilis", "ne zaman açılıyor", "ne zaman aciliyor") ->
                "Cüzdan kayıtları şu an açık ve 2 Aralık'taki halka açık lansmandan önce, 1 Aralık'ta kapanıyor. Trading şu an açık."
            has("ücret", "ucret", "komisyon", "nasıl kazanıyorsunuz", "nasil kazaniyorsunuz") ->
                "Prognos Labs üzerindeki her işlemde küçük bir ücret vardır, ve para kazanma yolumuz bu ücrettir. Kullanıcı verilerini satmıyoruz."
            has("puan", "phong", "referans", "davet") ->
                "Prognos Labs'ın bir puan sistemi var, ve referans bağlantınla insanları getirerek puan kazanırsın. Puanlar tradingini ve referanslarını takip eder."
            else -> null
        }
        VoiceLanguage.ARABIC -> when {
            has("كيف يعمل التداول", "خطوة بخطوة") ->
                "أولا تختار سوقا وتختار نعم أو لا. ثم تحدد السعر والمبلغ. أقرأ لك الأمر وتقول نعم أو لا. لا يتحرك شيء إلا بعد قولك نعم."
            has("دخول سوق", "أدخل سوق", "اختيار سوق") ->
                "ابحث عن سوق في التطبيق، مثل سوق يسأل إن كان شيء سيحدث قبل تاريخ محدد. اختر نعم إذا كنت تظن أنه سيحدث. اختر لا إذا كنت تظن أنه لن يحدث."
            has("السعر والمبلغ", "تحديد السعر", "المبلغ") && has("سعر", "السعر") ->
                "السعر هو ما تدفعه مقابل سهم واحد في ذلك السوق، وهو السعر الظاهر على الرسم البياني. المبلغ هو المال الذي تضعه. الحد الأدنى للمبلغ هو 5 دولارات ولا يوجد حد أقصى. قل الاتجاه والسعر والمبلغ في أمرك الصوتي وسأقرأ كل شيء قبل حدوث أي شيء."
            has("بعد أن أقول نعم", "بعد قول نعم", "بعد التأكيد") ->
                "عندما تقول نعم ينفذ الأمر ويصبح المركز لك حتى تسوية السوق أو بيعه مبكرا. إذا قلت لا فلن يحدث شيء."
            has("محفظتي", "مراكزي", "portfolio") && !t.contains("ربط") ->
                "محفظتك موجودة في تبويب المحفظة. تعرض رصيدك ومراكزك وتبقى فارغة حتى تقوم بالإيداع."
            has("تسوية", "الأرباح", "أربح", "دفع") ->
                "عندما يتقرر الحدث تتم تسوية السوق. الإنسان يقرر النتيجة وليس التطبيق وحده. الأسهم الرابحة تدفع والأخرى لا. تدفع الأرباح خلال 30 يوما بعملة USDT. تعود الأرباح إليك في التطبيق المرتبط بمحفظتك الخاصة لأننا لا نحتفظ بالأموال."
            has("مراكزي المفتوحة", "مركز مفتوح") ->
                "يمكنك أن تسألني عن مراكزك المفتوحة. يمكنني إخبارك بالسوق والاتجاه والمبلغ وهل ما زال مفتوحا. إذا لم تتوفر بيانات مباشرة الآن فسأقول لك إنني لا أرى مركزا مفتوحا."
            has("نفذ الصفقة الآن") ->
                "التداول مفتوح. أخبرني بالسوق والاتجاه والسعر والمبلغ وسأقرأ لك الأمر قبل حدوث أي شيء."
            has("التداول مغلق") ->
                "التداول مفتوح الآن."
            has("ماذا أشتري", "نصيحة", "دون سؤالي", "لا تستطيع فعله") ->
                "لا أتداول أبدا دون قراءة الأمر وموافقتك بنعم. لا أنشئ ولا أسوي أي سوق ولا أقدم نصيحة مالية أو توصية شراء. لا أذكر اسم مزود السيولة ولا نسبة الرسوم بل فقط أن هناك رسما صغيرا على كل صفقة. إذا أردت يمكنني شرح كيف يعمل السوق."
            isPrognos(t) && has("ما هو", "ما هي", "ما هذا", "أخبرني", "اخبرني", "عن") ->
                "Prognos Labs سوق توقعات مع تنبؤات ذكاء اصطناعي مدمجة. كل سوق يحمل تنبؤا قصيرا بلغة بسيطة، وعندما ينتهي السوق يقيم التنبؤ مقابل النتيجة الحقيقية، فيكون التالي أدق. الناس يقررون كل سوق. ذكاؤنا الاصطناعي لا ينشئ أي سوق ولا يحسمه. المستخدمون يحتفظون بمحافظهم ومفاتيحهم، ولا نحتفظ بأموالهم أبدا."
            has("تنبؤ", "танап", "ذكاء") && has("كيف", "ما") ->
                "كل سوق يحمل تنبؤا قصيرا يشرح ما يحدث وما قد يأتي بعد ذلك. عندما ينتهي السوق على النتيجة الحقيقية، يقيم التنبؤ مقابل تلك النتيجة، فيكون التالي أدق. الناس يقررون كل سوق. ذكاؤنا الاصطناعي لا ينشئ أي سوق ولا يحسمه."
            has("محفظة") && has("ربط", "كيف") ->
                "Prognos Labs لا تحتفظ بالأموال. تربط محفظتك الخاصة وتحتفظ بمفاتيحك الخاصة. لا نرى مفتاحك الخاص أبدا ولا نحتفظ بأموالك أبدا."
            has("إيداع", "ايداع", "إدىون") ->
                "الإيداعات بعملة USDT على شبكة BSC، على السلسلة، من خلال محفظتك الخاصة. توقع الإيداع من محفظتك، وتبقى الأموال لك."
            has("الإطلاق", "الاطلاق", "الافتتاح", "متى يفتح") ->
                "تسجيل المحافظ مفتوح الآن ويغلق في 1 ديسمبر، قبل الإطلاق العام في 2 ديسمبر. التداول مفتوح الآن."
            has("رسوم", "росуму", "عمولة", "كيف تربحون") ->
                "كل صفقة على Prognos Labs تحمل رسما صغيرا، وهذا الرسم هو كيف نكسب المال. لا نبيع بيانات المستخدمين."
            has("نقاط", "نقطة", "إحالة", "احالة", "دعوة") ->
                "لدى Prognos Labs نظام نقاط، وتكسب نقاطا بجلب الأشخاص عبر رابط الإحالة الخاص بك. النقاط تتبع تداولك وإحالاتك."
            else -> null
        }
VoiceLanguage.IGBO -> when {
    has("otu ahịa si arụ ọrụ", "nzọụkwụ", "how does trading work") ->
        "Mbụ, họrọ ahịa ma họrọ Yes ma ọ bụ No. Ọzọ, tinye ọnụ ahịa na ego. Aga m agụghachiri gị iwu ahụ, ị kwuo ee ma ọ bụ mba. Ọ bụ naanị mgbe ị kwuru ee ka ihe ga-aga n'ihu."
    has("banye ahịa", "họrọ ahịa", "enter a market") ->
        "Chọta ahịa n'ime ngwa ahụ, dịka nke na-ajụ ma ihe ga-eme tupu otu ụbọchị. Họrọ Yes ma ọ bụrụ na ị chere na ọ ga-eme. Họrọ No ma ọ bụrụ na ị chere na ọ gaghị eme."
    has("ọnụ ahịa na ego", "tinye ọnụ ahịa", "price", "amount") && has("ọnụ ahịa", "onu ahia", "price") ->
        "Ọnụ ahịa bụ ihe ị na-akwụ maka otu share n'ahịa ahụ, ọ bụkwa ọnụ ahịa egosiri na chaatị ahụ. Ego bụ ego ole ị tinyere. Ego kacha nta bụ $5, enweghịkwa ego kacha ibu. Kwuo akụkụ gị, ọnụ ahịa, na ego n'iwu olu gị, aga m agụghachi ha niile tupu ihe ọ bụla emee."
    has("mgbe m kwuru ee", "after i say yes") ->
        "Mgbe ị kwuo ee, a ga-eme iwu ahụ ma ọnọdụ ahụ bụrụ nke gị ruo mgbe ahịa mechiri ma ọ bụ ị ree ya n'oge. Ọ bụrụ na ị kwuo mba, ọ dịghị ihe ga-eme."
    has("portfolio", "akpa ego") && has("kedu", "gịnị", "gini", "olee", "how") ->
        "Portfolio gị dị na taabụ Portfolio. Ọ na-egosi ego gị na ọnọdụ gị. Ọ dị tọgbọrọ chakoo ruo mgbe ị tinyere ego."
    has("mechie ahịa", "mmeri", "kwụọ ụgwọ", "settle", "paid") ->
        "Mgbe e kpebiri ihe omume ahụ, ahịa na-emechi. Mmadụ na-ekpebi nsonaazụ ya, ọ bụghị ngwa ahụ naanị ya. A na-akwụ ụgwọ maka share meriri, ndị ọzọ anaghị enweta. A na-akwụ ego mmeri n'ime ụbọchị 30, na USDT. Ego mmeri na-alọghachiri gị n'ime ngwa ahụ jikọtara na akpa ego gị, n'ihi na anyị anaghị ejide ego."
    has("ọnọdụ mepere emepe", "open position", "positions") ->
        "Ị nwere ike ịjụ m ọnọdụ ndị mepere emepe i nwere. Enwere m ike ịgwa gị ahịa, akụkụ ị họọrọ, ego ị tinyere, ma ọ ka mepere emepe. Ọ bụrụ na enweghị m data ugbu a, aga m agwa gị na ahụghị m ọnọdụ mepere emepe."
    has("mee ahịa a ugbu a", "place this trade") ->
        "Ahịa emeghela. Gwa m ahịa, akụkụ gị, ọnụ ahịa na ego, aga m agụghachiri gị iwu ahụ tupu ihe ọ bụla emee."
    has("trading is closed") ->
        "Ahịa emeghela ugbu a."
    has("gịnị ka m ga-azụ", "ndụmọdụ", "what should i buy", "without asking", "cannot do") ->
        "Anaghị m eme ahịa na-enweghị ịgụghachi na ee si n'ọnụ gị. Anaghị m emepụta ma ọ bụ kpebie ahịa, anaghịkwa m enye ndụmọdụ ego ma ọ bụ gwa gị ihe ị ga-azụ. Anaghị m akpọ aha onye na-enye liquidity ma ọ bụ kwuo pasent ụgwọ, naanị na e nwere obere ụgwọ n'ahịa ọ bụla. Ọ bụrụ na ị chọrọ, enwere m ike ịkọwa otu ahịa si arụ ọrụ."
    isPrognos(t) && has("gịnị bụ", "gini bu", "kedu ihe bụ", "kedu ihe bu", "gwa m", "maka", "what is") ->
        "Prognos Labs bụ ahịa amụma nwere amụma AI. Ahịa ọ bụla nwere amụma dị mkpirikpi n'asụsụ dị mfe, ma mgbe ahịa mechiri a na-atụle amụma ahụ na nsonaazụ eziokwu ka nke ọzọ wee ka mma. Ndị mmadụ na-ekpebi ahịa ọ bụla; AI anyị anaghị emepụta ma ọ bụ kpebie ahịa. Ndị ọrụ na-ejide akpa ego na mkpịsị ugodi ha, anyị anaghị ejide ego ha."
    has("amụma", "amuma", "forecast", "ai") && has("kedu", "gịnị", "gini", "how", "what") ->
        "Ahịa ọ bụla nwere amụma dị mkpirikpi na-akọwa ihe na-eme na ihe nwere ike ịbịa. Mgbe ahịa mechiri na nsonaazụ eziokwu, a na-atụle amụma ahụ ka nke ọzọ wee ka mma. Ndị mmadụ na-ekpebi ahịa ọ bụla; AI anyị anaghị emepụta ma ọ bụ kpebie ahịa."
    has("wallet", "akpa ego") && has("jikọọ", "jikoo", "connect", "link", "kedu", "how") ->
        "Prognos Labs anaghị ejide ego. Ị na-ejikọ akpa ego gị ma debe mkpịsị ugodi gị. Anyị anaghị ahụ mkpịsị ugodi nzuzo gị ma anyị anaghị ejide ego gị."
    has("tinye ego", "nkwụnye", "nkwunye", "deposit") ->
        "Nkwụnye ego bụ USDT na BSC, n'elu blockchain, site na akpa ego gị. Ị na-abịanye aka na nkwụnye ego site na akpa ego gị, ego ahụ bụkwa nke gị."
    has("mmeghe", "mbido", "launch", "mgbe", "submission") ->
        "Ndebanye aha akpa ego emeghela ugbu a ma ga-emechi na 1 Disemba, tupu mmeghe ọha na 2 Disemba. Ahịa emeghela ugbu a."
    has("ụgwọ", "ugwo", "fee", "fees", "ego") && has("kedu", "gịnị", "gini", "ole", "how", "what") ->
        "Ahịa ọ bụla na Prognos Labs nwere obere ụgwọ, nke ahụ bụkwa otu anyị si enweta ego. Anyị anaghị ere data ndị ọrụ."
    has("isi", "point", "points", "referral", "kpọbata", "kpobata") ->
        "Prognos Labs nwere usoro points, ị na-enwetakwa points mgbe ị kpọbatara ndị mmadụ site na njikọ referral gị. Points gị na-eso ahịa gị na ndị ị kpọbatara."
    else -> null
}

VoiceLanguage.YORUBA -> when {
    has("bawo ni iṣowo ṣe n ṣiṣẹ", "igbesẹ", "how does trading work") ->
        "Ni akọkọ, yan ọja ki o yan Yes tabi No. Lẹhinna, ṣeto idiyele ati iye owo. Ma ka aṣẹ naa pada fun ọ, iwọ yoo si sọ bẹẹni tabi rara. Lẹhin ti o ba sọ bẹẹni nikan ni ohunkohun yoo tẹsiwaju."
    has("wọ ọja", "yan ọja", "enter a market") ->
        "Wa ọja kan ninu app naa, bii eyi ti o n beere boya nkan kan yoo ṣẹlẹ ṣaaju ọjọ kan. Yan Yes ti o ba ro pe yoo ṣẹlẹ. Yan No ti o ba ro pe ko ni ṣẹlẹ."
    has("idiyele ati iye", "ṣeto idiyele", "price", "amount") && has("idiyele", "iye", "price") ->
        "Idiyele ni ohun ti o san fun ipin kan ninu ọja yẹn, ati pe o jẹ idiyele ti a fi han lori apẹrẹ naa. Iye owo ni owo ti o fi sinu rẹ. Iye owo ti o kere julọ jẹ $5, ko si iye owo ti o pọ julọ. Sọ ẹgbẹ rẹ, idiyele, ati iye owo ninu aṣẹ ohun rẹ, ma ka gbogbo rẹ pada ki ohunkohun to ṣẹlẹ."
    has("lẹhin ti mo ba sọ bẹẹni", "after i say yes") ->
        "Nigba ti o ba sọ bẹẹni, aṣẹ naa yoo ṣiṣẹ ati pe ipo naa jẹ tirẹ titi ọja yoo fi pari tabi ti o ba ta a ni kutukutu. Ti o ba sọ rara, ko si ohun ti yoo ṣẹlẹ."
    has("portfolio", "apamọwọ", "apamowo") && has("bawo", "kini", "ibo", "how") ->
        "Portfolio rẹ wa lori taabụ Portfolio. O n fi iwọntunwọnsi ati awọn ipo rẹ han. Ko si nkan ninu rẹ titi o fi fi owo sii."
    has("pari", "ere", "sanwo", "settle", "paid") ->
        "Nigba ti a ba pinnu iṣẹlẹ naa, ọja yoo pari. Eniyan lo n pinnu abajade, kii ṣe app nikan. A o sanwo fun awọn ipin ti o ṣẹgun, awọn iyoku kii gba owo. A n san ere ni ọjọ 30, ninu USDT. Ere rẹ yoo pada si ọ ninu app ti o sopọ mọ apamọwọ rẹ, nitori a kii di owo mu."
    has("ipo ti o ṣii", "open position", "positions") ->
        "O le beere lọwọ mi iru awọn ipo ti o ṣii. Mo le sọ ọja naa, ẹgbẹ ti o yan, iye owo ti o fi sinu rẹ, ati boya o ṣi ṣii. Ti mi o ba ni data ipo lọwọlọwọ, ma sọ fun ọ pe mi o ri ipo ti o ṣii."
    has("ṣe iṣowo yii bayii", "place this trade") ->
        "Iṣowo ti ṣi. Sọ ọja naa, ẹgbẹ rẹ, idiyele ati iye owo fun mi, ma ka aṣẹ naa pada ki ohunkohun to ṣẹlẹ."
    has("trading is closed") ->
        "Iṣowo ti ṣi bayii."
    has("kini ki n ra", "imọran", "what should i buy", "without asking", "cannot do") ->
        "Mi o kii ṣe iṣowo laisi kika rẹ pada ati bẹẹni rẹ. Mi o kii ṣẹda tabi pinnu ọja, mi o si kii funni ni imọran owo tabi sọ ohun ti o yẹ ki o ra. Mi o kii darukọ oluranlọwọ owo tabi sọ ipin owo iṣẹ, nikan ni pe owo iṣẹ kekere wa lori gbogbo iṣowo. Ti o ba fẹ, mo le ṣalaye bi ọja ṣe n ṣiṣẹ."
    isPrognos(t) && has("kini", "kí ni", "sọ fun mi", "so fun mi", "nipa", "what is") ->
        "Prognos Labs jẹ ọja asọtẹlẹ ti o ni asọtẹlẹ AI. Gbogbo ọja ni asọtẹlẹ kukuru ni ede ti o rọrun, nigbati ọja ba pari a o ṣe ayẹwo asọtẹlẹ naa pẹlu abajade gidi ki eyi ti o tẹle le dara sii. Awọn eniyan lo n pinnu gbogbo ọja; AI wa kii ṣẹda tabi pinnu ọja kankan. Awọn olumulo lo n tọju apamọwọ ati bọtini wọn, a kii di owo wọn mu."
    has("asọtẹlẹ", "asotele", "forecast", "ai") && has("bawo", "kini", "how", "what") ->
        "Gbogbo ọja ni asọtẹlẹ kukuru ti o ṣalaye ohun ti n ṣẹlẹ ati ohun ti o le tẹle. Nigbati ọja ba pari pẹlu abajade gidi, a o ṣe ayẹwo asọtẹlẹ naa ki eyi ti o tẹle le dara sii. Awọn eniyan lo n pinnu gbogbo ọja; AI wa kii ṣẹda tabi pinnu ọja kankan."
    has("wallet", "apamọwọ", "apamowo") && has("so pọ", "so po", "connect", "link", "bawo", "how") ->
        "Prognos Labs kii di owo mu. O n so apamọwọ tirẹ pọ ki o si tọju bọtini tirẹ. A kii ri bọtini ikọkọ rẹ, a kii si di owo rẹ mu."
    has("fi owo sii", "idogo", "deposit") ->
        "Fifi owo sii jẹ USDT lori BSC, onchain, nipasẹ apamọwọ tirẹ. O n fọwọsi idogo naa lati inu apamọwọ rẹ, owo naa si jẹ tirẹ."
    has("ifilọlẹ", "ifilole", "ṣii", "sii", "launch", "nigbawo", "submission") ->
        "Ififorukọsilẹ apamọwọ wa ni ṣiṣi bayii yoo si ti ni Oṣu Kejila ọjọ 1, ṣaaju ifilọlẹ gbogbogbo ni Oṣu Kejila ọjọ 2. Iṣowo ti ṣi bayii."
    has("owo iṣẹ", "owo ise", "fee", "fees", "bawo ni ẹ ṣe n ri owo") ->
        "Gbogbo iṣowo lori Prognos Labs ni owo iṣẹ kekere, iyẹn si ni ọna ti a fi n ri owo. A kii ta data awọn olumulo."
    has("aami", "point", "points", "referral", "itọkasi", "itokasi") ->
        "Prognos Labs ni eto points, o si n gba points nigbati o ba mu eniyan wa nipasẹ ọna asopọ referral rẹ. Points rẹ n tẹle iṣowo ati awọn ti o mu wa."
    else -> null
}

VoiceLanguage.HAUSA -> when {
    has("yaya ciniki yake aiki", "mataki", "how does trading work") ->
        "Da farko, zabi kasuwa ka zabi Yes ko No. Na gaba, saita farashi da adadi. Zan karanta maka odar, sai ka ce eh ko a'a. Bayan ka ce eh ne kawai wani abu zai ci gaba."
    has("shiga kasuwa", "zabi kasuwa", "enter a market") ->
        "Nemo kasuwa a cikin app, kamar mai tambayar ko wani abu zai faru kafin wata rana. Zabi Yes idan kana ganin zai faru. Zabi No idan kana ganin ba zai faru ba."
    has("farashi da adadi", "saita farashi", "price", "amount") && has("farashi", "price") ->
        "Farashi shine abin da kake biya na rabo daya a wannan kasuwar, kuma shine farashin da aka nuna a kan jadawalin. Adadi shine kudin da ka saka. Mafi karancin adadi shine $5, kuma babu mafi girma. Fadi bangarenka, farashi, da adadi a odarka ta murya, zan karanta komai kafin wani abu ya faru."
    has("bayan na ce eh", "after i say yes") ->
        "Idan ka ce eh, odar za ta cika kuma matsayin ya zama naka har kasuwa ta rufe ko ka sayar da wuri. Idan ka ce a'a, babu abin da zai faru."
    has("portfolio", "walat") && has("yaya", "menene", "ina", "how") ->
        "Portfolio dinka yana kan shafin Portfolio. Yana nuna ma'aunin kudinka da matsayinka. Yana fanko har sai ka saka kudi."
    has("rufe kasuwa", "riba", "biya", "settle", "paid") ->
        "Idan an yanke hukunci kan lamarin, kasuwa za ta rufe. Mutum ne ke yanke sakamakon, ba app shi kadai ba. Ana biyan rabon da ya yi nasara, sauran ba a biya. Ana biyan riba cikin kwanaki 30, a USDT. Riba za ta koma gare ka a app da ke hade da walat dinka, saboda ba ma rike kudade."
    has("matsayi mai bude", "open position", "positions") ->
        "Kana iya tambayata wadanne matsayi ne a bude. Zan iya gaya maka kasuwa, bangaren da ka zaba, adadin da ka saka, da ko har yanzu yana bude. Idan ba ni da bayanan yanzu, zan gaya maka ba na ganin matsayi a bude."
    has("yi wannan ciniki yanzu", "place this trade") ->
        "Ciniki a bude yake. Fada min kasuwa, bangarenka, farashi da adadi, zan karanta maka odar kafin wani abu ya faru."
    has("trading is closed") ->
        "Ciniki a bude yake yanzu."
    has("me zan saya", "shawara", "what should i buy", "without asking", "cannot do") ->
        "Ba na ciniki ba tare da karantawa da amincewarka ta eh ba. Ba na kirkiro ko rufe kasuwa, kuma ba na ba da shawarar kudi ko gaya maka abin da za ka saya. Ba na fadin sunan mai samar da kudi ko kashi na kudin aiki, sai dai karamin kudin aiki akan kowane ciniki. Idan kana so, zan iya bayyana yadda kasuwa take aiki."
    isPrognos(t) && has("menene", "mecece", "fada min", "game da", "what is") ->
        "Prognos Labs kasuwar hasashe ce mai hasashen AI. Kowace kasuwa tana da gajeren hasashe cikin harshe mai sauki, kuma idan kasuwa ta rufe ana auna hasashen da sakamakon gaske domin na gaba ya fi kyau. Mutane ne ke yanke hukunci a kowace kasuwa; AI dinmu ba ya kirkiro ko rufe kasuwa. Masu amfani suna rike da walat da makullansu, ba ma rike kudadensu."
    has("hasashe", "forecast", "ai") && has("yaya", "menene", "how", "what") ->
        "Kowace kasuwa tana da gajeren hasashe da ke bayanin abin da ke faruwa da abin da ka iya zuwa. Idan kasuwa ta rufe da sakamakon gaske, ana auna hasashen domin na gaba ya fi inganci. Mutane ne ke yanke hukunci a kowace kasuwa; AI dinmu ba ya kirkiro ko rufe kasuwa."
    has("wallet", "walat") && has("hada", "haɗa", "connect", "link", "yaya", "how") ->
        "Prognos Labs ba ta rike kudade. Kana hada walat dinka kuma kana rike makullanka. Ba ma ganin sirrin makullinka kuma ba ma rike kudinka."
    has("ajiya", "saka kudi", "deposit") ->
        "Ajiyar kudi tana amfani da USDT akan BSC, kai tsaye ta walat dinka. Kana sanya hannu daga walat dinka, kuma kudin naka ne."
    has("kaddamarwa", "bude", "buɗe", "launch", "yaushe", "submission") ->
        "Rijistar walat tana bude yanzu kuma za ta rufe ranar 1 ga Disemba, kafin kaddamarwa ga jama'a ranar 2 ga Disemba. Ciniki a bude yake yanzu."
    has("kudin aiki", "fee", "fees", "yaya kuke samun kudi") ->
        "Kowane ciniki a Prognos Labs yana da karamin kudin aiki, kuma ta haka muke samun kudi. Ba ma sayar da bayanan masu amfani."
    has("maki", "point", "points", "referral", "gayyata") ->
        "Prognos Labs tana da tsarin points, kuma kana samun points idan ka kawo mutane ta hanyar link dinka na referral. Points suna bin cinikinka da wadanda ka gayyata."
    else -> null
}

VoiceLanguage.HEBREW -> when {
    has("איך עובד המסחר", "שלב אחר שלב") ->
        "קודם בוחרים שוק ובוחרים כן או לא. אחר כך קובעים מחיר וסכום. אני מקריא את הפקודה בחזרה ואתה אומר כן או לא. רק אחרי שאתה אומר כן משהו מתקדם."
    has("להיכנס לשוק", "לבחור שוק") ->
        "מצא שוק באפליקציה, למשל שוק ששואל אם משהו יקרה עד תאריך מסוים. בחר כן אם אתה חושב שזה יקרה. בחר לא אם אתה חושב שלא."
    has("מחיר וסכום", "לקבוע מחיר", "סכום") && has("מחיר") ->
        "מחיר הוא מה שאתה משלם על מניה אחת באותו שוק, והוא המחיר שמוצג בתרשים. סכום הוא כמה כסף אתה שם. הסכום המינימלי הוא $5, ואין סכום מקסימלי. אמור את הצד, המחיר והסכום בפקודה הקולית, ואני אקריא הכול בחזרה לפני שקורה משהו."
    has("אחרי שאני אומר כן", "לאחר אישור") ->
        "כשאתה אומר כן, הפקודה מתמלאת והפוזיציה שלך עד שהשוק נסגר או שאתה מוכר מוקדם. אם תאמר לא, שום דבר לא קורה."
    has("תיק", "פוזיציות", "portfolio") && has("איך", "איפה", "מה") ->
        "התיק שלך נמצא בלשונית תיק. הוא מציג את היתרה והפוזיציות שלך והוא ריק עד שתפקיד."
    has("נסגר", "זכיות", "תשלום", "משלמים") ->
        "כשהאירוע מוכרע, השוק נסגר. אדם מכריע את התוצאה, לא האפליקציה לבדה. מניות מנצחות מקבלות תשלום והאחרות לא. הזכיות משולמות תוך 30 ימים, ב-USDT. הזכיות חוזרות אליך באפליקציה שמחוברת לארנק שלך, כי אנחנו לא מחזיקים בכספים."
    has("פוזיציות פתוחות", "הפוזיציה שלי") ->
        "אתה יכול לשאול אילו פוזיציות פתוחות יש לך. אני יכול לומר לך את השוק, הצד שבחרת, הסכום ששמת ואם היא עדיין פתוחה. אם אין לי נתונים חיים כרגע, אומר לך שאיני רואה פוזיציה פתוחה."
    has("בצע את העסקה עכשיו") ->
        "המסחר פתוח. תגיד לי את השוק, הצד, המחיר והסכום, ואני אקריא את הפקודה בחזרה לפני שמשהו קורה."
    has("המסחר סגור") ->
        "המסחר פתוח עכשיו."
    has("מה לקנות", "ייעוץ", "בלי לשאול", "מה אתה לא יכול") ->
        "אני לעולם לא סוחר בלי הקראה חוזרת וכן ממך. אני לעולם לא יוצר או מכריע שוק, ולא נותן ייעוץ פיננסי או המלצת קנייה. אני לעולם לא מציין את ספק הנזילות או אחוז עמלה, רק שיש עמלה קטנה בכל עסקה. אם תרצה, אוכל להסביר איך השוק עובד."
    isPrognos(t) && has("מה זה", "מהי", "תגיד לי", "ספר לי", "על") ->
        "Prognos Labs היא פלטפורמת שווקי תחזיות עם תחזיות AI מובנות. לכל שוק יש תחזית קצרה בשפה פשוטה, וכשהשוק נסגר התחזית נמדדת מול התוצאה האמיתית כדי שהבאה תהיה חדה יותר. אנשים מכריעים כל שוק; ה-AI שלנו לעולם לא יוצר או מכריע שוק. המשתמשים שומרים את הארנק והמפתחות שלהם, ואנחנו לעולם לא מחזיקים בכספים."
    has("תחזית", "תחזיות", "ai", "בינה") && has("איך", "מה") ->
        "לכל שוק יש תחזית קצרה שמסבירה מה קורה ומה עשוי לקרות. כשהשוק נסגר על התוצאה האמיתית, התחזית מקבלת ציון מול התוצאה כדי שהתחזית הבאה תהיה מדויקת יותר. אנשים מכריעים כל שוק; ה-AI שלנו לעולם לא יוצר או מכריע שוק."
    has("ארנק", "однако", "wallet") && has("לחבר", "חיבור", "לקשר", "איך") ->
        "Prognos Labs היא לא משמורתית. אתה מחבר את הארנק שלך ושומר את המפתחות אצלך. אנחנו לעולם לא רואים את המפתח הפרטי שלך ולא מחזיקים בכספים שלך."
    has("הפקדה", "להפקיד", "هفكدا", "deposit") ->
        "ההפקדות הן ב-USDT על רשת BSC, על השרשרת, דרך הארנק שלך. אתה חותם על ההפקדה מהארנק שלך והכספים נשארים שלך."
    has("השקה", "מתי נפתח", "launch", "הרשמה") ->
        "הגשת הארנקים פתוחה כעת ונסגרת ב-1 בדצמבר, לפני ההשקה הציבורית ב-2 בדצמבר. המסחר פתוח עכשיו."
    has("עמלה", "עמלות", "αμαλά", "איך אתם מרוויחים", "fee") ->
        "לכל עסקה ב-Prognos Labs יש עמלה קטנה, וכך אנחנו מרוויחים. אנחנו לא מוכרים נתוני משתמשים."
    has("נקודות", "nekudot", "הפניה", "הפניות", "חבר מביא חבר", "point", "referral") ->
        "ל-Prognos Labs יש מערכת נקודות, ואתה צובר נקודות על הבאת משתמשים דרך קישור ההפניה שלך. הנקודות עוקבות אחרי המסחר וההפניות שלך."
    else -> null
}

VoiceLanguage.CHINESE -> when {
    has("怎么交易", "交易步骤", "一步一步") ->
        "首先选择一个市场并选择是或否。然后设置价格和金额。我会把订单读给你听，你说确认或取消。只有你说确认后才会继续。"
    has("进入市场", "选择市场") ->
        "在应用里找一个市场，比如某个事件是否会在指定日期前发生。如果你认为会发生就选是，认为不会发生就选否。"
    has("价格和金额", "设置价格", "金额") && has("价格") ->
        "价格是你购买该市场一份份额的费用，就是图表上显示的价格。金额是你投入的资金。最小金额是5美元，没有最大金额。在语音下单时说出方向、价格和金额，我会在任何操作前全部读给你确认。"
    has("说确认之后", "确认后会发生什么") ->
        "你说确认就会成交，仓位归你所有，直到市场结算或你提前卖出。如果你说不，什么都不会发生。"
    has("投资组合", "持仓", "portfolio") && has("怎么", "如何", "哪里", "看") ->
        "你的投资组合在“投资组合”标签页中。它会显示你的余额和持仓。在充值之前里面是空的。"
    has("结算", "盈利", "怎么赔付", "派彩") ->
        "事件有结果后市场就会结算。结果由人决定，而不是应用自己决定。获胜一方的份额获得派付，另一方没有。盈利会在 30 天内以 USDT 支付。收益会回到你绑定自己钱包的应用账户中，因为我们不托管资金。"
    has("持仓", "未平仓", "有哪些仓位") ->
        "你可以问我你当前有哪些持仓。我可以告诉你市场、方向、投入金额以及是否仍在持仓中。如果目前没有你的实时持仓数据，我会告诉你暂时看不到持仓。"
    has("现在下单") ->
        "交易已开放。告诉我市场、方向、价格和金额，我会在任何操作前把订单读给你确认。"
    has("交易未开放") ->
        "交易现在已开放。"
    has("买什么", "投资建议", "不问我就交易", "不能做什么") ->
        "没有读回确认和你的同意我绝不交易。我从不创建或结算市场，也不提供财务建议或推荐买什么。我从不透露流动性支持方，也不说具体费率，只说明每笔交易有少量手续费。如果你想了解，我可以解释市场如何运作。"
    isPrognos(t) && has("是什么", "介绍", "告诉我", "关于") ->
        "Prognos Labs 是一个内置 AI 预测的预测市场平台。每个市场都附有简短直白的预测，市场结算后会对照真实结果评分，让下一次预测更精准。每个市场都由人决定，我们的 AI 从不创建或结算市场。用户自己保管钱包和私钥，我们从不托管资金。"
    has("预测", "御刺", "ai", "人工智能") && has("怎么", "如何", "什么") ->
        "每个市场都带有简短预测，说明当前动态和可能走向。市场按真实结果结算后，系统会为预测评分以提升后续准确度。每个市场都由人决定，我们的 AI 从不创建或结算市场。"
    has("钱包", "wallet") && has("连接", "绑定", "怎么", "如何") ->
        "Prognos Labs 是非托管平台。你连接自己的钱包并保管自己的私钥，我们从不查看你的私钥，也不托管你的资金。"
    has("充值", "充实", "存款", "入金", "deposit") ->
        "充值通过你自己的钱包在 BSC 链上使用 USDT 完成。你在自己的钱包中签名，资金始终归你所有。"
    has("上线", "发布", "开放", "提交", "launch") ->
        "钱包提交现已开放，将于 12 月 1 日截止，随后于 12 月 2 日公开发布。交易现在已开放。"
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

/*
TEST CHECKLIST (v1.1.20: trading open for testers; early-sell topic held out on Flint's order, build without Q3)
1. User asks: How does trading work on Prognos Labs?
   Expected: First, pick a market and choose Yes or No. Next, set a price and an amount. I will read the order back to you, and you say yes or no. Only after you say yes does anything move forward, and once you say yes, the order goes through.

2. User asks: How do I enter a market?
   Expected: Find a market in the app, such as one asking if something will happen by a set date. Pick Yes if you think it will happen. Pick No if you think it will not happen.

3. User asks: How do I set a price and an amount?
   Expected: Price is what you pay for one share in that market, and it is the price shown on the chart. Amount is how much money you put in. The smallest amount is $5, and there is no largest amount. Say your side, price, and amount in your voice order, and I will read all of it back before anything happens.

4. User asks: What happens after I say yes?
   Expected: When you say yes, the order is filled and the position is yours until the market settles or you sell it early. If you say no, nothing happens and no order is placed.

5. User asks: How does a market settle and how do I get paid if I win?
   Expected: When the event is decided, the market settles. A human decides the result, not the voice and not the app on its own. Winning shares are paid out and losing shares are not. Winnings are paid in 30 days, in USDT. Winnings go back to you in the app, linked to your own wallet, because Prognos Labs is non custodial.

6. User asks: What positions do I have open?
   Expected: You can ask me what positions you have open. I can tell you the market, the side you picked, the amount you put in, and whether it is still open. If I do not have live position data for you right now, I will tell you I cannot see an open position.

7. User asks: Place this trade for me now.
   Expected: Trading is open. Tell me the market, your side, the price and the amount, and I will read the order back before anything goes through.

8. User asks: What should I buy?
   Expected: I never trade without a read back and a yes from you. I never create or settle a market, and I cannot give financial advice or tell you what to buy. I never name the liquidity backer or state a fee percentage, only that there is a small fee on every trade. If you want, I can explain how a market works instead.

9. User asks: Will you trade without asking me first?
   Expected: I never trade without a read back and a yes from you. I never create or settle a market, and I cannot give financial advice or tell you what to buy. I never name the liquidity backer or state a fee percentage, only that there is a small fee on every trade. If you want, I can explain how a market works instead.
*/
