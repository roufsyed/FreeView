package com.rouf.freediumcfd

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

class AppPreferences(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** The service used to open Medium articles. */
    val selectedService: MediumService
        get() = MediumService.fromId(prefs.getString(KEY_SERVICE, MediumService.DEFAULT.id))

    /** The [AppCompatDelegate] night-mode constant for the chosen theme. */
    val nightMode: Int
        get() = when (prefs.getString(KEY_THEME, THEME_SYSTEM)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

    /** WebView text zoom percentage for article rendering. */
    val textZoom: Int
        get() = prefs.getString(KEY_TEXT_ZOOM, DEFAULT_TEXT_ZOOM)?.toIntOrNull() ?: 100

    /** Max number of history entries to keep; 0 disables history. */
    val historySize: Int
        get() = prefs.getString(KEY_HISTORY_SIZE, DEFAULT_HISTORY_SIZE)?.toIntOrNull() ?: 50

    companion object {
        const val KEY_SERVICE = "reading_service"
        const val KEY_THEME = "theme"
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_CLEAR_CACHE = "clear_cache"
        const val KEY_ABOUT = "about_version"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val DEFAULT_TEXT_ZOOM = "100"

        const val KEY_HISTORY_SIZE = "history_size"
        const val KEY_VIEW_HISTORY = "view_history"
        const val KEY_CLEAR_HISTORY = "clear_history"
        const val DEFAULT_HISTORY_SIZE = "50"
    }
}
