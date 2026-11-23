package xyz.fruitslice.prognos.data

/**
 * Opt-in anonymous failed-phrase reporting (Flint, 22 Nov 2026).
 * Round 1 of the voice training program needs real phrases the
 * voice failed on, from real phones, to train the next round. The
 * user opts in with one toggle in voice settings, off by default,
 * explained in plain words. When on, the app sends back only the
 * text of phrases the voice failed to understand, so the voice can
 * be improved. Nothing is sent while it is off.
 *
 * What leaves the phone, per report: the phrase text, the language
 * code, and the app version. No audio, no name, no email, no wallet
 * address, no device identifier, no IP address stored server-side.
 * The send is best-effort in the background: it never blocks the
 * voice session, never retries in a way the user notices, and any
 * network failure is silent.
 *
 * Pure Kotlin except for the sender, so the report shape and the
 * privacy rules run as JVM unit tests. The manual tester-reporting
 * route is not built: Flint picked this one only.
 */

data class VoiceFeedbackReport(
    val phrase: String,
    val language: String,
    val appVersion: String
)

/** The longest phrase text ever sent. Longer phrases are cut. */
const val VOICE_FEEDBACK_MAX_PHRASE = 200

/** Builds a report, or null when there is nothing to send. The
 * phrase is capped at 200 characters. */
fun buildVoiceFeedbackReport(phrase: String, language: String, appVersion: String): VoiceFeedbackReport? {
    val clean = phrase.trim()
    if (clean.isEmpty()) return null
    return VoiceFeedbackReport(
        phrase = clean.take(VOICE_FEEDBACK_MAX_PHRASE),
        language = language.take(8),
        appVersion = appVersion.take(16)
    )
}

/** True when a parse result is a failure worth reporting: the voice
 * did not understand the phrase (ask again) or the question was out
 * of scope. Orders, answers and navigation are not failures. */
fun shouldReportVoiceFeedback(result: VoiceParseResult): Boolean =
    result is VoiceParseResult.AskAgain || result is VoiceParseResult.OutOfScope

private fun jsonEscape(s: String): String {
    val out = StringBuilder()
    for (ch in s) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (ch.code < 0x20) out.append(String.format("\\u%04x", ch.code)) else out.append(ch)
        }
    }
    return out.toString()
}

/** The exact JSON the app posts. Built by hand so the pure function
 * runs on the JVM in unit tests with no Android JSON stub. The keys
 * are exactly phrase, language, version: nothing else. */
fun voiceFeedbackJson(report: VoiceFeedbackReport): String =
    "{\"phrase\":\"${jsonEscape(report.phrase)}\"," +
        "\"language\":\"${jsonEscape(report.language)}\"," +
        "\"version\":\"${jsonEscape(report.appVersion)}\"}"

/**
 * Sends reports, best-effort, in the background. The [httpPost]
 * posts the JSON to the endpoint; the production one posts with
 * OkHttp from the view model. A failure is swallowed: a report
 * must never disturb a voice session, and there is no retry the
 * user could notice.
 */
class VoiceFeedbackReporter(private val httpPost: (String, String) -> Unit) {
    fun report(report: VoiceFeedbackReport?, enabled: Boolean) {
        if (!enabled || report == null) return
        try {
            httpPost(AppConfig.VOICE_FEEDBACK_URL, voiceFeedbackJson(report))
        } catch (e: Exception) {
            // Best effort only: silence any network failure.
        }
    }
}
