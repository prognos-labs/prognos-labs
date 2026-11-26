package xyz.prognoslabs.app.data

/**
 * What the recognizer heard, cleaned of silence. Pure, no Android
 * classes, so unit tests can run on the JVM.
 *
 * v1.1.14: twice in Flint's video the voice screen showed the words
 * "blank audio" as if that was what he said. That is the recognizer
 * hearing silence: the on-device library writes a [BLANK_AUDIO]
 * marker for a silent stretch, and the built-in recognizer can
 * return the same words as plain text. Silence is never speech, so
 * it must never reach the sheet: markers and the bare words are
 * stripped, and when nothing meaningful is left the result is null,
 * which the caller treats as "heard nothing, keep listening".
 *
 * Bracketed groups are markers only: real speech never comes back
 * in square brackets, so every [...] group is dropped, whatever it
 * says ([BLANK_AUDIO], [SILENCE], [MUSIC], [NOISE]). The bare
 * words "blank audio" are dropped too, with or without an
 * underscore, in any case. Everything else passes through untouched.
 *
 * v1.1.15: the library also hallucinates a bare "Thank you." at
 * the end of a quiet stretch. It is not speech either, so a
 * sentence that is only a thank-you is dropped the same way. A
 * real "thank you for ..." sentence is kept: only a bare
 * thank-you, a whole sentence of it, is dropped.
 */
private val BRACKETED_MARKER = Regex("\\[[^\\]]*\\]")

private val BARE_BLANK_AUDIO = Regex("(?i)\\bblank[_\\s]+audio\\b")

/** The spoken words with silence markers removed, or null when
 * nothing was really said. */
fun cleanRecognizedText(text: String): String? {
    var cleaned = BRACKETED_MARKER.replace(text, " ")
    cleaned = BARE_BLANK_AUDIO.replace(cleaned, " ")
    cleaned = stripThankYouSentences(cleaned)
    cleaned = cleaned.trim().replace(Regex("\\s+"), " ")
    return cleaned.ifBlank { null }
}

/** A bare thank-you in the recognizer's language: the speech
 * library hallucinates one at the end of a quiet stretch. */
private val THANK_YOU_SENTENCES = setOf(
    "thank you", "thank you very much", "thanks",
    "gracias", "merci", "obrigado", "obrigada", "danke",
    "teşekkür ederim", "teşekkürler", "teşekkür", "شكرا"
)

/** Drops sentences that are only a bare thank-you, and keeps
 * everything else, punctuation included. */
internal fun stripThankYouSentences(text: String): String {
    val sentences = text.split(Regex("(?<=[.!?])\\s+"))
    return sentences
        .filter { sentence ->
            val core = sentence.trim().trimEnd('.', '?', '!', ',').lowercase()
            core !in THANK_YOU_SENTENCES
        }
        .joinToString(" ")
        .trim()
}

/** True when the recognizer output is silence, not speech. */
fun isSilenceText(text: String): Boolean = cleanRecognizedText(text) == null

/**
 * Words the recognizer invents from breaths and room noise: a bare
 * "The" or a lone "." after a quiet stretch is not something the user
 * said. A single word is only noise when it is one of these: a real
 * one-word answer ("yes", "no", "hello", "bonjour") is never in this
 * set, so it still reaches the parser.
 */
private val NOISE_ONLY_WORDS = setOf(
    "the", "a", "an", "and", "um", "uh", "hmm", "er", "mmm", "huh"
)

/**
 * True when the transcript is breath or room noise the recognizer
 * turned into a word, not speech. The caller treats it as silence:
 * keep listening quietly, never show "say it again" for a breath.
 * Anything the cleaner already drops (blank audio, a bare
 * thank-you) counts as noise too.
 */
fun isNoiseTranscript(text: String): Boolean {
    val cleaned = cleanRecognizedText(text) ?: return true
    val stripped = cleaned.trim().trim('.', '?', '!', ',', ';', ':', '-', '…', '—', '–')
    if (stripped.isEmpty()) return true
    val words = stripped.lowercase().split(Regex("\\s+"))
    return words.size == 1 && words[0] in NOISE_ONLY_WORDS
}
