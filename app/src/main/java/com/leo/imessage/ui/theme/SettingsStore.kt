package com.leo.imessage.ui.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * Makes preferences survive the process.
 *
 * Everything here used to live only in memory, which is why a chat
 * background chosen one evening was gone the next time the app started -
 * the setting was never wrong, it just had nowhere to be written down.
 *
 * Writes are `apply()`, not `commit()`: this is called from the UI thread on
 * every toggle, and blocking a frame on a disk write to save a boolean is
 * exactly the kind of thing that costs you the smoothness everywhere else.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("imessage_settings", Context.MODE_PRIVATE)

    fun getString(key: String, fallback: String): String =
        prefs.getString(key, fallback) ?: fallback

    fun getBoolean(key: String, fallback: Boolean): Boolean = prefs.getBoolean(key, fallback)

    fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun put(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    /** Per-chat backgrounds, stored as one "chatId=backgroundId" list. */
    fun backgrounds(): Map<String, String> =
        prefs.getStringSet(KEY_BACKGROUNDS, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

    fun putBackgrounds(map: Map<String, String>) {
        prefs.edit()
            .putStringSet(KEY_BACKGROUNDS, map.map { "${it.key}=${it.value}" }.toSet())
            .apply()
    }

    private companion object {
        const val KEY_BACKGROUNDS = "chat_backgrounds"
    }
}
