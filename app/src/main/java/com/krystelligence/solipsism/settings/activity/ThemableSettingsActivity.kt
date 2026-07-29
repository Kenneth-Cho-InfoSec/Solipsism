package com.krystelligence.solipsism.settings.activity

import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.AccentPalette
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.ThemeUtils
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import javax.inject.Inject

abstract class ThemableSettingsActivity : AppCompatActivity() {

    private var themeId: AppTheme = AppTheme.LIGHT
    private var appliedSystemAccent: Int? = null

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        injector.inject(this)
        themeId = userPreferences.useTheme

        // set the theme
        when (themeId) {
            AppTheme.LIGHT -> {
                setTheme(R.style.Theme_SettingsTheme)
                window.setBackgroundDrawable(ThemeUtils.getPrimaryColor(this).toDrawable())
            }

            AppTheme.DARK -> {
                setTheme(R.style.Theme_SettingsTheme_Dark)
                window.setBackgroundDrawable(ThemeUtils.getPrimaryColorDark(this).toDrawable())
            }

            AppTheme.BLACK -> {
                setTheme(R.style.Theme_SettingsTheme_Black)
                window.setBackgroundDrawable(ThemeUtils.getPrimaryColorDark(this).toDrawable())
            }
        }
        theme.applyStyle(
            AccentPalette.overlayFor(
                themeId,
                userPreferences.accentPalette,
                userPreferences.matchSystemAccent
            ),
            true
        )
        appliedSystemAccent = AccentPalette.systemAccentFingerprint(this)
        super.onCreate(savedInstanceState)

        resetPreferences()
    }

    private fun resetPreferences() {
        if (userPreferences.useBlackStatusBar) {
            window.statusBarColor = Color.BLACK
        } else {
            window.statusBarColor = ThemeUtils.getStatusBarColor(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val systemAccent = AccentPalette.systemAccentFingerprint(this)
        if (userPreferences.matchSystemAccent && systemAccent != appliedSystemAccent) {
            recreate()
            return
        }
        resetPreferences()
        if (userPreferences.useTheme != themeId) {
            recreate()
        }
    }

}
