package xyz.prognoslabs.app.data

import android.content.SharedPreferences

/**
 * A SharedPreferences that lives only in memory: nothing is ever
 * written to disk. Used when the encrypted store cannot be created
 * (a broken Keystore on the device): tokens and the voice profile are
 * too sensitive to fall back to plain text on disk, so on such a
 * device they last only until the app process dies and the user signs
 * in again. It behaves like SharedPreferences otherwise: apply() and
 * commit() both write at once, and a second instance does not see the
 * first one's values, exactly like a fresh process.
 */
@Suppress("UNCHECKED_CAST")
class MemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    @Synchronized
    override fun getAll(): Map<String, *> = HashMap(data)

    @Synchronized
    override fun getString(key: String, defValue: String?): String? =
        (data[key] as? String) ?: defValue

    @Synchronized
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (data[key] as? Set<String>) ?: defValues

    @Synchronized
    override fun getInt(key: String, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    @Synchronized
    override fun getLong(key: String, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    @Synchronized
    override fun getFloat(key: String, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    @Synchronized
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    @Synchronized
    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = MemoryEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            pending[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            synchronized(this@MemorySharedPreferences) {
                if (clearAll) data.clear()
                for (k in removals) data.remove(k)
                data.putAll(pending)
            }
        }
    }
}
