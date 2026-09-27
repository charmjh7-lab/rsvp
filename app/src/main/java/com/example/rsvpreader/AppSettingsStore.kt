package com.example.rsvpreader

import android.content.Context

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        wpm = prefs.getInt("wpm", 240).coerceIn(60, 1200),
        focusColorArgb = prefs.getLong("focusColor", 0xFFE65100),
        showFocusColor = prefs.getBoolean("showFocusColor", true),
        contextFontSp = prefs.getFloat("contextFontSp", 20f).coerceIn(14f, 34f),
        rsvpFontSp = prefs.getFloat("rsvpFontSp", 46f).coerceIn(28f, 80f),
        darkMode = prefs.getBoolean("darkMode", false),
        punctuationDelay = prefs.getBoolean("punctuationDelay", true),
        longWordDelay = prefs.getBoolean("longWordDelay", false)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt("wpm", settings.wpm.coerceIn(60, 1200))
            .putLong("focusColor", settings.focusColorArgb)
            .putBoolean("showFocusColor", settings.showFocusColor)
            .putFloat("contextFontSp", settings.contextFontSp)
            .putFloat("rsvpFontSp", settings.rsvpFontSp)
            .putBoolean("darkMode", settings.darkMode)
            .putBoolean("punctuationDelay", settings.punctuationDelay)
            .putBoolean("longWordDelay", settings.longWordDelay)
            .apply()
    }
}
