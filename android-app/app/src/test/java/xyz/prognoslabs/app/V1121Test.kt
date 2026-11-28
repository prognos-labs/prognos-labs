package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import xyz.prognoslabs.app.ui.CrashReporter

/**
 * v1.1.21: the fresh install closed on the first enrollment mic
 * tap, before the microphone permission box ever appeared. The
 * permission launcher lived in the Compose scaffold; it now lives
 * on the activity, registered once, and a launch that fails shows
 * a plain message instead of closing the app. A crash report is
 * also saved on the device, so the next launch can show it in
 * Settings and the user can copy it out.
 *
 * These tests run on the JVM under Robolectric. There is no real
 * permission dialog here, so the launcher move is proven by the
 * source, and the reporter is proven through its file round-trip.
 */
@RunWith(RobolectricTestRunner::class)
class V1121Test {

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

    private fun appContext() =
        RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun versionIs1123() {
        val gradle = readSource("app/build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 41"))
        assertTrue(gradle.contains("versionName = \"1.1.31\""))
    }

    @Test
    fun permissionLauncherLivesOnActivity() {
        val main = readSource(
            "app/src/main/java/xyz/prognoslabs/app/MainActivity.kt"
        )
        // Registered once on the activity, the safe pattern.
        assertTrue(
            main.contains(
                "registerForActivityResult(ActivityResultContracts.RequestPermission())"
            )
        )
        // Never again in the Compose scaffold: that placement
        // closed the app on the first enrollment mic tap.
        assertFalse(main.contains("rememberLauncherForActivityResult"))
        // A launch that fails falls back to a plain message.
        assertTrue(main.contains("vm.onVoicePermissionResult(false)"))
    }

    @Test
    fun crashReportRoundTrip() {
        val context = appContext()
        CrashReporter.clear(context)
        assertNull(CrashReporter.readLastCrash(context))
        val file = File(context.filesDir, "crash.log")
        file.writeText("Prognos Labs crash, test\nThread: main\n\njava.lang.RuntimeException: test\n")
        val read = CrashReporter.readLastCrash(context)
        assertTrue(read != null && read.contains("RuntimeException: test"))
        CrashReporter.clear(context)
        assertNull(CrashReporter.readLastCrash(context))
    }

    @Test
    fun crashReportIsSavedByTheHandler() {
        val context = appContext()
        CrashReporter.clear(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            CrashReporter.install(context)
            val t = Thread { throw RuntimeException("v1.1.21 probe") }
            t.start()
            t.join()
            val read = CrashReporter.readLastCrash(context)
            assertTrue(read != null && read.contains("v1.1.21 probe"))
            assertTrue(read!!.contains("Prognos Labs crash"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            CrashReporter.clear(context)
        }
    }

    @Test
    fun crashReportStateStartsEmpty() {
        // No report file means nothing to show in Settings.
        CrashReporter.clear(appContext())
        assertNull(CrashReporter.readLastCrash(appContext()))
    }
}
