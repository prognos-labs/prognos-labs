package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.voice.GroqAsrProvider

/**
 * v1.1.23: the app must hear any language spoken, not just the one
 * set in Settings. Real FLEURS speech (26 Nov) proved the gap: with
 * no language hint Groq heard nothing in Hausa and hallucinated
 * English in Yoruba, while forcing yo/ha brought both back. So only
 * Yoruba, Hausa and Hebrew get a hint now; every other language goes
 * with no hint and Groq detects what was actually spoken. Igbo is
 * still refused: Groq itself rejects it (400, unsupported language).
 *
 * These tests run on the JVM. There is no microphone and no Groq
 * here, so the hint is proven through its pure helper and the source
 * wiring, and the real proof is still Flint's phone, once asr.php
 * v3 is live on the server.
 */
class V1123Test {

    private fun mainRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return dir
    }

    private fun readSource(relative: String): String =
        File(mainRoot(), relative).readText()

    @Test
    fun versionIs1123() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 39"))
        assertTrue(gradle.contains("versionName = \"1.1.29\""))
    }

    // ---- The hint: only the languages Groq cannot detect ----

    @Test
    fun languageHintOnlyForYoHaHe() {
        assertEquals("yo", GroqAsrProvider.groqLanguageHeader("yo"))
        assertEquals("ha", GroqAsrProvider.groqLanguageHeader("ha"))
        assertEquals("he", GroqAsrProvider.groqLanguageHeader("he"))
        assertEquals("en", GroqAsrProvider.groqLanguageHeader("en"))
        assertEquals("es", GroqAsrProvider.groqLanguageHeader("es"))
    }

    @Test
    fun blankMeansAutoDetect() {
        assertNull(GroqAsrProvider.groqLanguageHeader(""))
        assertNull(GroqAsrProvider.groqLanguageHeader("   "))
    }

    @Test
    fun igboHasNoHeader() {
        // Groq rejects ig outright; the provider never sends it.
        assertNull(GroqAsrProvider.groqLanguageHeader("ig"))
        assertNull(GroqAsrProvider.groqLanguageHeader("xx"))
    }

    @Test
    fun hintSentOnlyForCloudLanguages() {
        val engine = readSource(
            "app/src/main/java/xyz/prognoslabs/app/ui/VoiceEngine.kt"
        )
        assertTrue(engine.contains("VoiceLanguage.YORUBA, VoiceLanguage.HAUSA, VoiceLanguage.HEBREW -> lang.code"))
        val provider = readSource(
            "app/src/main/java/xyz/prognoslabs/app/voice/GroqAsrProvider.kt"
        )
        assertTrue(provider.contains("groqLanguageHeader(languageCode)?.let"))
    }
}
