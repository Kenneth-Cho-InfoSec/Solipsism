package com.krystelligence.solipsism

import android.content.Context
import android.os.Build

/** Accent palettes supported by the appearance picker. */
enum class AccentPalette(
    val previewColor: Int,
    private val lightOverlay: Int,
    private val darkOverlay: Int,
    private val blackOverlay: Int
) {
    TEAL(0xFF006A6A.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Teal_Light, R.style.ThemeOverlay_Solipsism_Accent_Teal_Dark, R.style.ThemeOverlay_Solipsism_Accent_Teal_Black),
    BLUE(0xFF2563EB.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Blue_Light, R.style.ThemeOverlay_Solipsism_Accent_Blue_Dark, R.style.ThemeOverlay_Solipsism_Accent_Blue_Black),
    INDIGO(0xFF4F46E5.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Indigo_Light, R.style.ThemeOverlay_Solipsism_Accent_Indigo_Dark, R.style.ThemeOverlay_Solipsism_Accent_Indigo_Black),
    PURPLE(0xFF7C3AED.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Purple_Light, R.style.ThemeOverlay_Solipsism_Accent_Purple_Dark, R.style.ThemeOverlay_Solipsism_Accent_Purple_Black),
    PINK(0xFFDB2777.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Pink_Light, R.style.ThemeOverlay_Solipsism_Accent_Pink_Dark, R.style.ThemeOverlay_Solipsism_Accent_Pink_Black),
    RED(0xFFDC2626.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Red_Light, R.style.ThemeOverlay_Solipsism_Accent_Red_Dark, R.style.ThemeOverlay_Solipsism_Accent_Red_Black),
    ORANGE(0xFFEA580C.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Orange_Light, R.style.ThemeOverlay_Solipsism_Accent_Orange_Dark, R.style.ThemeOverlay_Solipsism_Accent_Orange_Black),
    GREEN(0xFF16A34A.toInt(), R.style.ThemeOverlay_Solipsism_Accent_Green_Light, R.style.ThemeOverlay_Solipsism_Accent_Green_Dark, R.style.ThemeOverlay_Solipsism_Accent_Green_Black);

    fun overlayFor(theme: AppTheme): Int = when (theme) {
        AppTheme.LIGHT -> lightOverlay
        AppTheme.DARK -> darkOverlay
        AppTheme.BLACK -> blackOverlay
    }

    companion object {
        fun fromValue(value: Int): AccentPalette = entries.getOrNull(value) ?: TEAL

        fun overlayFor(theme: AppTheme, paletteValue: Int, matchSystem: Boolean): Int {
            if (matchSystem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return when (theme) {
                    AppTheme.LIGHT -> R.style.ThemeOverlay_Solipsism_Accent_System_Light
                    AppTheme.DARK -> R.style.ThemeOverlay_Solipsism_Accent_System_Dark
                    AppTheme.BLACK -> R.style.ThemeOverlay_Solipsism_Accent_System_Black
                }
            }
            return fromValue(paletteValue).overlayFor(theme)
        }

        fun systemAccentFingerprint(context: Context): Int? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getColor(android.R.color.system_accent1_600)
            } else null
    }
}
