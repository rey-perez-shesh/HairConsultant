package com.hairconsultant.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The light/dark choice from Profile's theme toggle, kept on the device. `null` until the user
 * first taps the toggle, meaning "follow the system setting".
 */
class ThemePreference(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(if (prefs.contains(KEY_DARK_MODE)) prefs.getBoolean(KEY_DARK_MODE, false) else null)
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply()
        _darkMode.value = dark
    }

    private companion object {
        const val PREFS_NAME = "appearance"
        const val KEY_DARK_MODE = "dark_mode"
    }
}
