package xyz.prognoslabs.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * The voice history: what was said and what happened to each one.
 * Pure functions, so unit tests can run on the JVM. Stored as one
 * JSON string in the encrypted voice store, newest first, capped so
 * the list stays short.
 */
enum class VoiceHistoryOutcome {
    TRADED,
    ANSWERED,
    CANCELLED,
    /** Voice opened a market or a screen in the app. */
    NAVIGATED
}

data class VoiceHistoryEntry(
    val text: String,
    val outcome: VoiceHistoryOutcome,
    val atMs: Long
)

const val VOICE_HISTORY_MAX = 50

private val gson = Gson()

fun voiceHistoryToJson(entries: List<VoiceHistoryEntry>): String =
    gson.toJson(entries)

fun voiceHistoryFromJson(json: String?): List<VoiceHistoryEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<VoiceHistoryEntry>>() {}.type
        gson.fromJson<List<VoiceHistoryEntry>>(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/** Adds one entry at the front, newest first, capped at [VOICE_HISTORY_MAX]. */
fun addVoiceHistory(
    entries: List<VoiceHistoryEntry>,
    entry: VoiceHistoryEntry,
    max: Int = VOICE_HISTORY_MAX
): List<VoiceHistoryEntry> = (listOf(entry) + entries).take(max)

/** Plain label for the history list. */
fun VoiceHistoryOutcome.label(): String = when (this) {
    VoiceHistoryOutcome.TRADED -> "Traded"
    VoiceHistoryOutcome.ANSWERED -> "Answered"
    VoiceHistoryOutcome.CANCELLED -> "Cancelled"
    VoiceHistoryOutcome.NAVIGATED -> "Opened"
}
