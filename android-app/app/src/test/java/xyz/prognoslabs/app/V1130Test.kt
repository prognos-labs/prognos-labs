package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.data.SUPPORTED_VOICE_LANGUAGES
import xyz.prognoslabs.app.data.VoiceLanguage

/**
 * v1.1.30: Flint tested v1.1.29 on his phone (two screenshots,
 * 27 Nov ~09:15 WAT) and two things were wrong with the language
 * screens.
 *
 * 1. The settings row reads "English (Cloud ASR)". The engine
 *    name does not belong in front of the user, so the row now
 *    shows just the language name, with no engine note at all.
 * 2. The language picker is a plain white list with plain text
 *    rows and a "Selected" word. It now shows each language with
 *    its own name underneath, the picked one marked with a lime
 *    check on a lime tint, on a cream sheet with one thin black
 *    line under the title, and a black Close button. The same rows
 *    are used on the enrollment screen.
 *
 * The picker itself is Compose, so it is proven here by source
 * checks plus the pure language data. The real proof is still
 * Flint's phone.
 */
class V1130Test {

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
    fun versionIs1130() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 41"))
        assertTrue(gradle.contains("versionName = \"1.1.31\""))
    }

    @Test
    fun everyLanguageHasANativeName() {
        assertEquals(12, SUPPORTED_VOICE_LANGUAGES.size)
        for (lang in SUPPORTED_VOICE_LANGUAGES) {
            assertTrue(lang.nativeName.isNotBlank())
        }
        assertEquals("Español", VoiceLanguage.SPANISH.nativeName)
        assertEquals("Français", VoiceLanguage.FRENCH.nativeName)
        assertEquals("Português", VoiceLanguage.PORTUGUESE.nativeName)
        assertEquals("Deutsch", VoiceLanguage.GERMAN.nativeName)
        assertEquals("Türkçe", VoiceLanguage.TURKISH.nativeName)
        assertEquals("العربية", VoiceLanguage.ARABIC.nativeName)
        assertEquals("Yorùbá", VoiceLanguage.YORUBA.nativeName)
        assertEquals("עברית", VoiceLanguage.HEBREW.nativeName)
        assertEquals("中文", VoiceLanguage.CHINESE.nativeName)
    }

    @Test
    fun noEngineSuffixAnywhereInSettings() {
        val screens = readSource("app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt")
        assertFalse(screens.contains("(Cloud ASR)"))
        assertFalse(screens.contains("(On-device)"))
        assertFalse(screens.contains("Cloud ASR"))
    }

    @Test
    fun settingsPickerIsDesigned() {
        val screens = readSource("app/src/main/java/xyz/prognoslabs/app/ui/Screens.kt")
        assertTrue(screens.contains("lang.nativeName"))
        assertTrue(screens.contains("Icons.Filled.Check"))
        assertTrue(screens.contains("Voice listens and answers in this language."))
        assertTrue(screens.contains("Color(0xFFF5F2EB)"))
        assertFalse(screens.contains("Text(\"Selected\", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Lime)"))
    }

    @Test
    fun enrollPickerUsesTheSameRows() {
        val enroll = readSource("app/src/main/java/xyz/prognoslabs/app/ui/VoiceScreens.kt")
        assertTrue(enroll.contains("lang.nativeName"))
        assertTrue(enroll.contains("Icons.Filled.Check"))
        assertFalse(enroll.contains("\"Selected\""))
    }
}
