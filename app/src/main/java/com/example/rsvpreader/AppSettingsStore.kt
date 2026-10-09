package com.example.rsvpreader

import android.content.Context

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        eyeMode = runCatching { EyeReadingMode.valueOf(prefs.getString("eyeMode", null) ?: "ALTERNATING") }.getOrDefault(EyeReadingMode.ALTERNATING),
        eyeUnit = runCatching { EyeReadingUnit.valueOf(prefs.getString("eyeUnit", null) ?: "WORD") }.getOrDefault(EyeReadingUnit.WORD),
        keepPrevious = prefs.getBoolean("keepPrevious", true),
        wordRate = prefs.getInt("wordRate", 100).coerceIn(EYE_RATE_MIN, EYE_RATE_MAX),
        characterRate = prefs.getInt("characterRate", 120).coerceIn(EYE_RATE_MIN, EYE_RATE_MAX),
        wpm = prefs.getInt("wpm", WPM_DEFAULT).coerceIn(WPM_MIN, WPM_MAX),
        focusColorArgb = prefs.getLong("focusColor", 0xFFE65100),
        showFocusColor = prefs.getBoolean("showFocusColor", true),
        contextFontSp = prefs.getFloat("contextFontSp", 20f).coerceIn(14f, 34f),
        rsvpFontSp = prefs.getFloat("rsvpFontSp", 30f).coerceIn(18f, 60f),
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
        rsvpAtTop = prefs.getBoolean("rsvpAtTop", false)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("eyeMode", settings.eyeMode.name)
            .putString("eyeUnit", settings.eyeUnit.name)
            .putBoolean("keepPrevious", settings.keepPrevious)
            .putInt("wordRate", settings.wordRate)
            .putInt("characterRate", settings.characterRate)
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
            .apply {
                if (settings.ttsEnginePackage == null) remove("ttsEnginePackage")
                else putString("ttsEnginePackage", settings.ttsEnginePackage)
                if (settings.ttsVoiceName == null) remove("ttsVoiceName")
                else putString("ttsVoiceName", settings.ttsVoiceName)
            }
            .apply()
    }
}
