package com.example.rsvpreader

import android.content.Context

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        wpm = prefs.getInt("wpm", WPM_DEFAULT).coerceIn(WPM_MIN, WPM_MAX),
        focusColorArgb = prefs.getLong("focusColor", 0xFFE65100),
        showFocusColor = prefs.getBoolean("showFocusColor", true),
        contextFontSp = prefs.getFloat("contextFontSp", 20f).coerceIn(14f, 34f),
        rsvpFontSp = prefs.getFloat("rsvpFontSp", 46f).coerceIn(28f, 80f),
        darkMode = prefs.getBoolean("darkMode", false),
        lightBackground = runCatching {
            LightBackground.valueOf(
                prefs.getString("lightBackground", LightBackground.WHITE.name) ?: LightBackground.WHITE.name
            )
        }.getOrDefault(LightBackground.WHITE),
        punctuationDelay = prefs.getBoolean("punctuationDelay", true),
        longWordDelay = prefs.getBoolean("longWordDelay", false),
        ttsRate = prefs.getFloat("ttsRate", 1.0f).coerceIn(0.5f, 2.0f),
        ttsEnginePackage = prefs.getString("ttsEnginePackage", null),
        ttsVoiceName = prefs.getString("ttsVoiceName", null),
        lastTtsMode = prefs.getBoolean("lastTtsMode", false),
        rsvpAtTop = prefs.getBoolean("rsvpAtTop", false),
        focusBeforeCenter = prefs.getBoolean("focusBeforeCenter", false)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt("wpm", settings.wpm.coerceIn(WPM_MIN, WPM_MAX))
            .putLong("focusColor", settings.focusColorArgb)
            .putBoolean("showFocusColor", settings.showFocusColor)
            .putFloat("contextFontSp", settings.contextFontSp)
            .putFloat("rsvpFontSp", settings.rsvpFontSp)
            .putBoolean("darkMode", settings.darkMode)
            .putString("lightBackground", settings.lightBackground.name)
            .putBoolean("punctuationDelay", settings.punctuationDelay)
            .putBoolean("longWordDelay", settings.longWordDelay)
            .putFloat("ttsRate", settings.ttsRate.coerceIn(0.5f, 2.0f))
            .putBoolean("lastTtsMode", settings.lastTtsMode)
            .putBoolean("rsvpAtTop", settings.rsvpAtTop)
            .putBoolean("focusBeforeCenter", settings.focusBeforeCenter)
            .apply {
                if (settings.ttsEnginePackage == null) remove("ttsEnginePackage")
                else putString("ttsEnginePackage", settings.ttsEnginePackage)
                if (settings.ttsVoiceName == null) remove("ttsVoiceName")
                else putString("ttsVoiceName", settings.ttsVoiceName)
            }
            .apply()
    }
}
