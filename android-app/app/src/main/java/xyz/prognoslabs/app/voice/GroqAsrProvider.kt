package xyz.prognoslabs.app.voice

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import com.google.gson.JsonParser
import xyz.prognoslabs.app.data.VoiceLanguage
import xyz.prognoslabs.app.data.voiceLanguageForLocale
import xyz.prognoslabs.app.data.voiceLanguageSupported
import xyz.prognoslabs.app.ui.AsrProvider
import xyz.prognoslabs.app.ui.AsrTranscript

/**
 * Groq Whisper Large V3 Turbo, reached only through the Prognos Labs server
 * proxy at https://prognoslabs.xyz/asr.php.
 *
 * The app never holds a Groq key and never calls Groq directly. It sends
 * 16 kHz mono WAV audio to the proxy, and the proxy calls Groq with the key
 * from config.php on the server. No audio is stored by the app, no device
 * identifier is sent, only the language code and the audio.
 *
 * Routing, automatic, no user toggle: VoiceEngine sends every language
 * to this provider first (v1.1.18, Flint's order), because the on-device
 * tiny model keeps mishearing him and Groq heard him perfectly in the
 * live test. Everything else stays on-device only as the fallback when
 * the cloud misses. Igbo is never sent here: Whisper has no Igbo, so
 * transcribe returns null for "ig" and the caller falls back to
 * on-device English.
 *
 * v1.1.23: the language is a hint, not a lock. Only Yoruba, Hausa and
 * Hebrew get one, because Groq cannot detect them on its own (real
 * FLEURS speech, 26 Nov: auto-detect heard nothing in Hausa and
 * hallucinated English in Yoruba, while the hint brought both back).
 * Every other language is sent with no hint, so Groq detects what was
 * actually spoken and the app hears any language, not just the one set
 * in Settings. The proxy needs asr.php v3 for that: the old proxy
 * rejects a missing language, and then transcribe returns null and the
 * caller falls back to on-device, so nothing breaks before v3 is live.
 *
 * Any failure returns null. The caller must fall back to on-device Whisper
 * on null, never show a cloud error to the user, and never retry in a loop.
 * The free tier is 20 requests a minute and 2,000 a day, per organisation.
 */
class GroqAsrProvider(
    private val proxyUrl: String = "https://prognoslabs.xyz/asr.php"
) : AsrProvider {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_TEXT_CHARS = 500

        // Whisper languages this provider can actually transcribe. Igbo is
        // deliberately absent: it is not a Whisper language.
        private val SUPPORTED = setOf(
            "en", "es", "fr", "pt", "de", "tr", "ar", "yo", "ha", "he", "zh"
        )

        /**
         * The language header for one call. Blank means auto-detect:
         * no header is sent and Groq reports what it heard. A code
         * Groq cannot transcribe (Igbo) also means no call at all.
         * Pure, so unit tests can pin it without a network.
         */
        fun groqLanguageHeader(languageCode: String): String? {
            if (languageCode.isBlank()) return null
            return if (languageCode in SUPPORTED) languageCode else null
        }

        /**
         * Parses the JSON response from https://prognoslabs.xyz/asr.php.
         * The proxy returns the transcript as "text" and, when it knows it,
         * the language Groq detected as "language" (for example "yo").
         * If "language" is absent or blank, detectedLanguage stays null and
         * the app falls back to the language set in Settings.
         * Gson, not org.json: org.json is a stub in plain JVM unit tests.
         */
        fun parseGroqAsrResponse(jsonBody: String): AsrTranscript? {
            val root = runCatching { JsonParser.parseString(jsonBody).asJsonObject }.getOrNull() ?: return null
            val text = root.get("text")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            if (text.isEmpty()) return null
            val detected = parseDetectedLanguageCode(
                root.get("language")?.takeIf { it.isJsonPrimitive }?.asString
            )
            return AsrTranscript(text = text.take(MAX_TEXT_CHARS), detectedLanguage = detected)
        }
    }

    override suspend fun transcribe(
        pcm: ShortArray,
        sampleRate: Int,
        languageCode: String
    ): AsrTranscript? {
        // A language Groq cannot transcribe (Igbo) is refused here,
        // before any network: a blank code means auto-detect.
        if (languageCode.isNotBlank() && languageCode !in SUPPORTED) return null
        if (pcm.isEmpty() || sampleRate <= 0) return null
        return try {
            val wav = pcmToWav(pcm, sampleRate)
            val connection = (URL(proxyUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "audio/wav")
                groqLanguageHeader(languageCode)?.let { setRequestProperty("X-Prognos-Language", it) }
            }
            connection.outputStream.use { it.write(wav) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseGroqAsrResponse(body)
        } catch (e: Exception) {
            null
        }
    }

    private fun pcmToWav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())
        val samples = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm) samples.putShort(sample)
        out.write(samples.array())
        return out.toByteArray()
    }
}

/**
 * Maps a raw language code or name from the recognizer to one of the 12 supported
 * languages. Returns null when the code is missing, unknown, or not supported.
 * Never guesses from text.
 */
fun parseDetectedLanguageCode(raw: String?): VoiceLanguage? {
    val code = raw?.trim()?.lowercase(Locale.US).orEmpty()
    if (code.isEmpty()) return null
    return when (code) {
        "en", "english" -> VoiceLanguage.ENGLISH
        "es", "spanish" -> VoiceLanguage.SPANISH
        "fr", "french" -> VoiceLanguage.FRENCH
        "pt", "portuguese" -> VoiceLanguage.PORTUGUESE
        "de", "german" -> VoiceLanguage.GERMAN
        "tr", "turkish" -> VoiceLanguage.TURKISH
        "ar", "arabic" -> VoiceLanguage.ARABIC
        "ig", "igbo" -> VoiceLanguage.IGBO
        "yo", "yoruba" -> VoiceLanguage.YORUBA
        "ha", "hausa" -> VoiceLanguage.HAUSA
        "he", "iw", "hebrew" -> VoiceLanguage.HEBREW
        "zh", "chinese" -> VoiceLanguage.CHINESE
        else -> {
            val locale = Locale(code)
            if (voiceLanguageSupported(locale)) voiceLanguageForLocale(locale) else null
        }
    }
}
