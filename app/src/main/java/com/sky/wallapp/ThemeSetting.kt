package com.sky.wallapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** App theme choice (System default / Light / Dark), applied app-wide via AppCompatDelegate. */
object ThemeSetting {

    enum class Mode(val nightMode: Int, val label: Int) {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.theme_system),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO, R.string.theme_light),
        DARK(AppCompatDelegate.MODE_NIGHT_YES, R.string.theme_dark)
    }

    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "mode"

    fun current(context: Context): Mode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return Mode.entries.firstOrNull { it.name == raw } ?: Mode.SYSTEM
    }

    /** Call once at startup, before any activity is created. */
    fun apply(context: Context) = AppCompatDelegate.setDefaultNightMode(current(context).nightMode)

    /** Saves and applies; open activities are recreated in the new theme. */
    fun set(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_MODE, mode.name) }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
}
