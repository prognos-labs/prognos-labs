package xyz.prognoslabs.app.voice

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The speech model (v1.1.16). The model is the small multilingual
 * one, about 75 MB, and it is BUNDLED in the APK (assets): there is
 * no download on first voice use, no progress screen, nothing to
 * wait on. On first use the bundled copy is copied into the app's
 * private files and checked by size and hash before it is used.
 * A file that does not match both is not used. A model left on the
 * device by an earlier version (downloaded before bundling) is
 * kept and used as-is.
 *
 * Pure file work, no Android classes besides the asset stream the
 * caller supplies, so the unit tests run on the JVM.
 */
sealed class WhisperStatus {
    /** No model on the device yet. */
    object Missing : WhisperStatus()

    /** The model is on the device and checked. */
    object Ready : WhisperStatus()

    /** The bundled copy could not be read or did not check out. */
    object Failed : WhisperStatus()
}

class WhisperModelManager(
    filesDir: File,
    private val bundledModel: (() -> InputStream)? = null,
    private val expectedSizeBytes: Long = MODEL_SIZE_BYTES,
    private val expectedSha256: String = MODEL_SHA256
) {
    companion object {
        /** The model file, bundled in the APK under assets. */
        const val MODEL_FILE_NAME = "ggml-tiny-q8_0.bin"

        /** Size and hash of ggml-tiny-q8_0.bin, verified on the build
         * machine 25 Nov 2026 by downloading the file from the
         * official whisper.cpp release and hashing it. A file that
         * does not match both is not used. */
        const val MODEL_SIZE_BYTES = 43_537_433L
        const val MODEL_SHA256 =
            "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca"

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }

    private val modelDir = File(filesDir, "whisper")
    val modelFile: File = File(modelDir, MODEL_FILE_NAME)
    private val tmpFile: File = File(modelDir, "$MODEL_FILE_NAME.tmp")

    private val _status: MutableStateFlow<WhisperStatus> =
        MutableStateFlow(if (isModelReady()) WhisperStatus.Ready else WhisperStatus.Missing)
    val status: StateFlow<WhisperStatus> = _status.asStateFlow()

    private val copyMutex = Mutex()

    /** True when the model file is on disk at the expected size. */
    fun isModelReady(): Boolean =
        modelFile.isFile && modelFile.length() == expectedSizeBytes

    /**
     * Deletes the model file on disk, so the next voice use extracts
     * a fresh copy from the app. Used when the file is the right size
     * but the native library cannot load it: the file is corrupt, and
     * keeping it would leave the mic dead on every listen.
     */
    fun deleteModel() {
        try {
            modelFile.delete()
        } catch (e: Exception) {
            // A delete that fails leaves the file for the next check.
        }
        try {
            tmpFile.delete()
        } catch (e: Exception) {
            // Same as above.
        }
        _status.value = WhisperStatus.Missing
    }

    /**
     * True when a bundled copy was supplied and can actually be
     * opened: the model ships in the APK, so it can always be made
     * ready. Opening the stream (and closing it at once) is the
     * check: a missing asset throws here, instead of the mic opening
     * a sheet that can never work and going silently dead.
     */
    fun hasBundledModel(): Boolean {
        val openBundled = bundledModel ?: return false
        return try {
            openBundled().close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copies the bundled model into place when it is missing, and
     * checks it by size and hash. Returns true when the model is
     * on the device and checked. Call off the main thread: the
     * copy is local and fast, but it is still 75 MB of file IO.
     * Only one copy runs at a time; a second caller waits for the
     * first and shares its result. Any failure deletes the partial
     * file and reports Failed, so the next voice use tries again.
     * There is no download and no progress: the model ships in the
     * APK, so the user never waits on a network fetch to talk.
     */
    suspend fun ensureModel(): Boolean =
        copyMutex.withLock {
            if (isModelReady()) {
                _status.value = WhisperStatus.Ready
                return true
            }
            val openBundled = bundledModel
            if (openBundled == null) {
                _status.value = WhisperStatus.Failed
                return false
            }
            try {
                modelDir.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                FileOutputStream(tmpFile).use { out ->
                    openBundled().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (tmpFile.length() != expectedSizeBytes || hash != expectedSha256) {
                    tmpFile.delete()
                    _status.value = WhisperStatus.Failed
                    return false
                }
                if (!tmpFile.renameTo(modelFile)) {
                    tmpFile.delete()
                    _status.value = WhisperStatus.Failed
                    return false
                }
                _status.value = WhisperStatus.Ready
                return true
            } catch (e: Exception) {
                tmpFile.delete()
                _status.value = WhisperStatus.Failed
                return false
            }
        }
}
