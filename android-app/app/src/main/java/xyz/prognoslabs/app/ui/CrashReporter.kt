package xyz.prognoslabs.app.ui

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash report (v1.1.21): when the app dies on an uncaught error,
 * the stack trace is saved to the app's private files before the
 * process ends, so the next launch can show it in Settings and the
 * user can copy it out. Nothing leaves the phone on its own: no
 * network, no identifiers, just the trace text on the device.
 * Added after v1.1.20 closed on the first enrollment mic tap,
 * before the permission box appeared, with no trace to read.
 */
object CrashReporter {
    private const val FILE_NAME = "crash.log"
    private const val MAX_CHARS = 12_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = "Prognos Labs crash, $stamp\nThread: ${thread.name}\n\n$sw\n"
                File(appContext.filesDir, FILE_NAME).writeText(text.take(MAX_CHARS))
            } catch (e: Exception) {
                // Never crash while reporting a crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.isFile) file.readText().takeIf { it.isNotBlank() } else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (e: Exception) {
            // Nothing to clear.
        }
    }
}
