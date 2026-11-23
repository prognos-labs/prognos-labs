package xyz.fruitslice.prognos.voice

/**
 * Thin JNI bridge to the speech library (v1.1.13). The native
 * library is built from the open-source speech library at build
 * time; it is not a paid service and needs no key.
 *
 * [isAvailable] is false when the native library did not load: on
 * the JVM test run there is no .so at all, and on a device where
 * the library is missing the voice is unavailable. Calling a
 * native method while [isAvailable] is false throws, so every
 * caller checks first. There is no fallback recognizer.
 */
object WhisperNative {
    val isAvailable: Boolean = try {
        System.loadLibrary("prognos_whisper")
        true
    } catch (e: Throwable) {
        false
    }

    /** Loads the model file. Returns a native handle, 0 on failure. */
    @JvmStatic
    external fun nativeInit(modelPath: String): Long

    /**
     * Transcribes 16 kHz mono audio. Returns the text, or null when
     * nothing was heard or the run failed. The caller runs this off
     * the main thread: a run takes about a second for a sentence.
     */
    @JvmStatic
    external fun nativeTranscribe(
        context: Long,
        pcm: ShortArray,
        language: String,
        threads: Int
    ): String?

    @JvmStatic
    external fun nativeFree(context: Long)
}
