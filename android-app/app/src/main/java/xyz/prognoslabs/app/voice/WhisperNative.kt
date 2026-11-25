package xyz.prognoslabs.app.voice

/**
 * Thin JNI bridge to the on-device speech library (whisper.cpp v1.7.6).
 * The native library is built from source at build time; it is not a paid
 * service and needs no key.
 *
 * [isAvailable] is false when the native library did not load (for example
 * on JVM unit test runs). Every caller checks [isAvailable] first.
 */
object WhisperNative {
    val isAvailable: Boolean = try {
        System.loadLibrary("prognos_whisper")
        true
    } catch (e: Throwable) {
        false
    }

    /** Loads the model file. Returns a native handle, or 0 on failure. */
    @JvmStatic
    external fun nativeInit(modelPath: String): Long

    /**
     * Transcribes 16 kHz mono PCM audio. Returns the text, or null when
     * nothing was heard or the run failed. Call off the main thread.
     */
    @JvmStatic
    external fun nativeTranscribe(
        context: Long,
        pcm: ShortArray,
        language: String,
        threads: Int,
        initialPrompt: String? = null
    ): String?

    @JvmStatic
    external fun nativeFree(context: Long)
}

