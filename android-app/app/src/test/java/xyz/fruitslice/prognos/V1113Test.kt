package xyz.fruitslice.prognos

import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fruitslice.prognos.voice.WhisperModelManager
import xyz.fruitslice.prognos.voice.WhisperRecognizer
import xyz.fruitslice.prognos.voice.WhisperStatus

/**
 * v1.1.16: the speech library is the ONLY recognizer, and its
 * model ships inside the APK. The phone's built-in recognizer is
 * gone: there is no fallback to it, no chooser between the two,
 * and no download. On first use the bundled copy is copied into
 * the app's private files and checked by size and hash. A file
 * that does not match both is not used.
 *
 * These tests run on the JVM. There is no microphone and no native
 * library here, so the recognizer itself is tested through its
 * pure helpers, and the model manager through a fake bundled copy
 * supplied in memory. Live recognition can only be proven on a
 * real phone.
 */
class V1113Test {

    private fun managerFor(dir: File, bytes: ByteArray?, size: Long, sha: String) =
        WhisperModelManager(
            dir,
            bundledModel = bytes?.let { { it.inputStream() } },
            expectedSizeBytes = size,
            expectedSha256 = sha
        )

    @Test
    fun versionIs116() {
        assertEquals(26, BuildConfig.VERSION_CODE)
        assertEquals("1.1.16", BuildConfig.VERSION_NAME)
    }

    @Test
    fun multilingualLanguageSelection() {
        assertEquals("en", WhisperRecognizer.whisperLanguageCode(Locale("en")))
        assertEquals("es", WhisperRecognizer.whisperLanguageCode(Locale("es")))
        assertEquals("fr", WhisperRecognizer.whisperLanguageCode(Locale("fr")))
        assertEquals("pt", WhisperRecognizer.whisperLanguageCode(Locale("pt")))
        assertEquals("de", WhisperRecognizer.whisperLanguageCode(Locale("de")))
        assertEquals("tr", WhisperRecognizer.whisperLanguageCode(Locale("tr")))
        assertEquals("ar", WhisperRecognizer.whisperLanguageCode(Locale("ar")))
        // Anything else falls back to English, including a locale
        // with a region.
        assertEquals("en", WhisperRecognizer.whisperLanguageCode(Locale("ja")))
        assertEquals("en", WhisperRecognizer.whisperLanguageCode(Locale("zh", "CN")))
        assertEquals("es", WhisperRecognizer.whisperLanguageCode(Locale("es", "MX")))
    }

    @Test
    fun silenceAndFinalizeRules() {
        // Silence below the threshold after speech ends the segment.
        assertTrue(WhisperRecognizer.shouldFinalize(true, 1_200L, 5_000L))
        assertTrue(WhisperRecognizer.shouldFinalize(true, 5_000L, 5_000L))
        // A short pause does not end it: the user may still be talking.
        assertFalse(WhisperRecognizer.shouldFinalize(true, 400L, 5_000L))
        // No speech heard yet: silence alone never finalizes.
        assertFalse(WhisperRecognizer.shouldFinalize(false, 10_000L, 5_000L))
        // The length cap ends the segment even while speaking.
        assertTrue(WhisperRecognizer.shouldFinalize(true, 0L, 30_000L))
        assertTrue(WhisperRecognizer.shouldFinalize(false, 0L, 30_000L))
        // RMS: quiet is silence, speech is not.
        assertTrue(WhisperRecognizer.isSilent(WhisperRecognizer.rmsOf(shortArrayOf(0, 10, -20, 5))))
        assertFalse(WhisperRecognizer.isSilent(WhisperRecognizer.rmsOf(shortArrayOf(0, 3000, -3000, 1000))))
        assertEquals(0.0, WhisperRecognizer.rmsOf(shortArrayOf()), 0.0)
    }

    @Test
    fun bundledModelCopiesAndVerifies() {
        runBlocking {
            val bytes = "tiny-model-bytes".toByteArray()
            val dir = File(System.getProperty("java.io.tmpdir"), "v116-model-ok-${System.nanoTime()}")
            dir.mkdirs()
            var opens = 0
            val manager = WhisperModelManager(
                dir,
                bundledModel = { opens++; bytes.inputStream() },
                expectedSizeBytes = bytes.size.toLong(),
                expectedSha256 = WhisperModelManager.sha256Hex(bytes)
            )
            assertEquals(WhisperStatus.Missing, manager.status.value)
            assertTrue(manager.ensureModel())
            assertEquals(WhisperStatus.Ready, manager.status.value)
            assertTrue(manager.isModelReady())
            assertEquals(bytes.size.toLong(), manager.modelFile.length())
            // A second call does not copy again.
            assertTrue(manager.ensureModel())
            assertEquals(1, opens)
            dir.deleteRecursively()
        }
    }

    @Test
    fun bundledModelMissingFails() {
        runBlocking {
            val dir = File(System.getProperty("java.io.tmpdir"), "v116-model-missing-${System.nanoTime()}")
            dir.mkdirs()
            val manager = managerFor(dir, null, 100L, "00")
            assertFalse(manager.ensureModel())
            assertEquals(WhisperStatus.Failed, manager.status.value)
            assertFalse(manager.isModelReady())
            assertFalse(manager.modelFile.exists())
            dir.deleteRecursively()
        }
    }

    @Test
    fun bundledModelHashMismatchIsRejected() {
        runBlocking {
            val bytes = "not-the-model".toByteArray()
            val dir = File(System.getProperty("java.io.tmpdir"), "v116-model-bad-${System.nanoTime()}")
            dir.mkdirs()
            // Right size, wrong hash: the file must not be used.
            val manager = managerFor(
                dir,
                bytes,
                bytes.size.toLong(),
                "0000000000000000000000000000000000000000000000000000000000000000"
            )
            assertFalse(manager.ensureModel())
            assertEquals(WhisperStatus.Failed, manager.status.value)
            assertFalse(manager.modelFile.exists())
            dir.deleteRecursively()
        }
    }

    @Test
    fun bundledModelSizeMismatchIsRejected() {
        runBlocking {
            val bytes = "tiny-model-bytes".toByteArray()
            val dir = File(System.getProperty("java.io.tmpdir"), "v116-model-size-${System.nanoTime()}")
            dir.mkdirs()
            // Right hash, wrong size: the file must not be used.
            val manager = managerFor(
                dir,
                bytes,
                bytes.size.toLong() + 1,
                WhisperModelManager.sha256Hex(bytes)
            )
            assertFalse(manager.ensureModel())
            assertEquals(WhisperStatus.Failed, manager.status.value)
            assertFalse(manager.modelFile.exists())
            dir.deleteRecursively()
        }
    }

    @Test
    fun noModelDownloadCode() {
        // The model ships in the APK. There is no download anywhere
        // in the voice code: no URL, no HTTP client, no progress.
        val root = findAppFile()
        val model = File(root, "src/main/java/xyz/fruitslice/prognos/voice/WhisperModel.kt").readText()
        for (word in listOf("huggingface", "MODEL_URL", "OkHttp", "Downloading", "http")) {
            assertFalse("WhisperModel.kt contains $word", model.contains(word))
        }
        val engine = File(root, "src/main/java/xyz/fruitslice/prognos/ui/VoiceEngine.kt").readText()
        for (word in listOf("huggingface", "MODEL_URL", "OkHttp", "Downloading", "Getting the voice model")) {
            assertFalse("VoiceEngine.kt contains $word", engine.contains(word))
        }
    }

    @Test
    fun noAiToolNamesInVoiceCode() {
        // The standing rule: no AI-tool names in shipped code,
        // comments, READMEs or commit messages. The speech library
        // files and the voice package are scanned here.
        val banned = listOf("Muse", "Claude", "ChatGPT", "OpenAI")
        val root = findAppFile()
        val files = listOf(
            File(root, "src/main/java/xyz/fruitslice/prognos/voice/WhisperNative.kt"),
            File(root, "src/main/java/xyz/fruitslice/prognos/voice/WhisperModel.kt"),
            File(root, "src/main/java/xyz/fruitslice/prognos/voice/WhisperRecognizer.kt"),
            File(root, "src/main/cpp/CMakeLists.txt"),
            File(root, "src/main/cpp/prognos_whisper_jni.cpp")
        )
        for (file in files) {
            assertTrue("missing: ${file.path}", file.exists())
            val text = file.readText()
            for (word in banned) {
                assertFalse("${file.name} contains $word", text.contains(word))
            }
        }
        // The gradle file must not name the version in a comment
        // the user can see either; it only sets the numbers.
        val gradle = File(root, "build.gradle.kts").readText()
        for (word in banned) {
            assertFalse("build.gradle.kts contains $word", gradle.contains(word))
        }
    }

    @Test
    fun whisperJniSymbolsPresent() {
        // The JNI layer names the methods the Kotlin bridge calls.
        val jni = File(findAppFile(), "src/main/cpp/prognos_whisper_jni.cpp").readText()
        assertTrue(jni.contains("Java_xyz_fruitslice_prognos_voice_WhisperNative_nativeInit"))
        assertTrue(jni.contains("Java_xyz_fruitslice_prognos_voice_WhisperNative_nativeTranscribe"))
        assertTrue(jni.contains("Java_xyz_fruitslice_prognos_voice_WhisperNative_nativeFree"))
        // The native side must not hard-code a model path: the
        // model is copied from the bundled asset into the app's
        // files and its path comes in.
        assertFalse(jni.contains("ggml-tiny.bin"))
        assertFalse(jni.contains("/sdcard"))
    }

    private fun findAppFile(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "app")
            if (File(candidate, "build.gradle.kts").exists()) return candidate
            dir = dir.parentFile
        }
        // Robolectric runs with the module dir as the working dir.
        val direct = File("build.gradle.kts")
        if (direct.exists()) return File(".")
        throw AssertionError("app module not found from ${System.getProperty("user.dir")}")
    }
}
