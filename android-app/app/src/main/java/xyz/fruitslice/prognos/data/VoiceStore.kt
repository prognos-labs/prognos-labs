package xyz.fruitslice.prognos.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * The voice store: the enrolled voice profile and the voice history,
 * kept on the phone in the same encrypted store as the login token.
 * Nothing about the voice ever leaves the device. If encryption is
 * unavailable the values are kept in memory only and never written
 * to disk in plain text; [encrypted] reports which one is in use,
 * exactly like TokenStore.
 *
 * `encryptedFactory` is injectable so tests can simulate a broken
 * Keystore without one.
 */
class VoiceStore(
    context: Context,
    encryptedFactory: (Context, String) -> SharedPreferences? = ::createEncryptedPrefs
) {
    private val gson = Gson()
    private val prefs: SharedPreferences
    val encrypted: Boolean

    init {
        val enc = encryptedFactory(context, "prognos_voice")
        encrypted = enc != null
        prefs = enc ?: MemorySharedPreferences()
    }

    fun loadProfile(): VoiceProfile? {
        val json = prefs.getString("voice_profile", null) ?: return null
        return try {
            gson.fromJson(json, StoredVoiceProfile::class.java)?.toProfile()
        } catch (e: Exception) {
            null
        }
    }

    fun saveProfile(profile: VoiceProfile) {
        prefs.edit().putString("voice_profile", gson.toJson(profile.toStored())).apply()
    }

    fun clearProfile() {
        prefs.edit().remove("voice_profile").apply()
    }

    /**
     * The three enrollment phrases assigned to this user, picked at
     * random from the pool. Stored so a user who leaves enrollment
     * halfway gets the same phrases back, and so the assignment
     * survives an app restart.
     */
    fun saveEnrollPhrases(phrases: List<String>) {
        prefs.edit().putString("voice_enroll_phrases", gson.toJson(phrases)).apply()
    }

    fun loadEnrollPhrases(): List<String>? {
        val json = prefs.getString("voice_enroll_phrases", null) ?: return null
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            null
        }
    }

    fun clearEnrollPhrases() {
        prefs.edit().remove("voice_enroll_phrases").apply()
    }

    fun loadHistory(): List<VoiceHistoryEntry> =
        voiceHistoryFromJson(prefs.getString("voice_history", null))

    fun saveHistory(entries: List<VoiceHistoryEntry>) {
        prefs.edit().putString("voice_history", voiceHistoryToJson(entries)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("voice_history").apply()
    }

    fun saveVoiceLanguage(code: String) {
        prefs.edit().putString("voice_language", code).apply()
    }

    fun loadVoiceLanguage(): VoiceLanguage? {
        val code = prefs.getString("voice_language", null) ?: return null
        return VoiceLanguage.entries.firstOrNull { it.code == code }
    }
}
