package com.krystelligence.solipsism

import android.app.Activity

/** Applies the same persisted theme and accent used by the main browser activity. */
object ThemeApplication {

    private const val PREFERENCES = "settings"
    private const val THEME = "Theme"
    private const val ACCENT_PALETTE = "accentPalette"
    private const val MATCH_SYSTEM_ACCENT = "matchSystemAccent"

    fun applySavedTheme(activity: Activity) {
        val preferences = activity.getSharedPreferences(PREFERENCES, Activity.MODE_PRIVATE)
        val appTheme = AppTheme.entries.firstOrNull {
            it.value == preferences.getInt(THEME, AppTheme.LIGHT.value)
        } ?: AppTheme.LIGHT
        val effectiveTheme = appTheme.effective(activity)

        activity.setTheme(
            when (effectiveTheme) {
                AppTheme.LIGHT -> R.style.Theme_LightTheme
                AppTheme.DARK -> R.style.Theme_DarkTheme
                AppTheme.BLACK -> R.style.Theme_BlackTheme
                AppTheme.SYSTEM -> error("System theme must be resolved before applying it")
            }
        )
        activity.theme.applyStyle(
            AccentPalette.overlayFor(
                effectiveTheme,
                preferences.getInt(ACCENT_PALETTE, AccentPalette.TEAL.ordinal),
                preferences.getBoolean(MATCH_SYSTEM_ACCENT, false)
            ),
            true
        )
    }
}
