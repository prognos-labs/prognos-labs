package xyz.fruitslice.prognos.voice

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import xyz.fruitslice.prognos.ui.AsrProvider

/**
 * Groq Whisper Large V3 Turbo, reached only through the Prognos Labs server
 * proxy at https://prognoslabs.xyz/asr.php.
 *
 * The app never holds a Groq key and never calls Groq directly. It sends
 * 16 kHz mono WAV audio to the proxy, and the proxy calls Groq with the key
 * from config.php on the server. No audio is stored by the app, no device
 * identifier is sent, only the language code and the audio.
 *
 * Routing, automatic, no user toggle: VoiceEngine sends a language to this
 * provider only when that language is marked requiresCloudAsr in
 * VoiceLanguage (Yoruba, Hausa, Hebrew in v1.1.17). Everything else stays
 * on-device. Igbo is never sent here: Whisper has no Igbo, so transcribe
 * returns null for "ig" and the caller falls back to on-device English.
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
    }

    override suspend fun transcribe(
        pcm: ShortArray,
        sampleRate: Int,
        languageCode: String
    ): String? {
        if (languageCode !in SUPPORTED) return null
        if (pcm.isEmpty() || sampleRate <= 0) return null
        return try {
            val wav = pcmToWav(pcm, sampleRate)
            val connection = (URL(proxyUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "audio/wav")
                setRequestProperty("X-Prognos-Language", languageCode)
            }
            connection.outputStream.use { it.write(wav) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val text = JSONObject(body).optString("text", "").trim()
            if (text.isEmpty()) null else text.take(MAX_TEXT_CHARS)
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
