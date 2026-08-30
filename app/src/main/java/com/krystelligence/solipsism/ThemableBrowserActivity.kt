package com.krystelligence.solipsism

import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.browser.ui.TabConfiguration
import com.krystelligence.solipsism.browser.ui.SolipsismRailPosition
import com.krystelligence.solipsism.preference.DeveloperPreferences
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.i18n.TranslationOverrides
import com.krystelligence.solipsism.utils.ThemeUtils
import com.krystelligence.solipsism.utils.CustomFontManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.Menu
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.iterator
import javax.inject.Inject

/**
 * A theme aware activity that updates its theme based on the user preferences.
 */
abstract class ThemableBrowserActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(TranslationOverrides.wrap(newBase))
    }

    @Inject
    internal lateinit var userPreferences: UserPreferences

    @Inject
    internal lateinit var developerPreferences: DeveloperPreferences

    private var themeId: AppTheme = AppTheme.LIGHT
    private var tabConfiguration: TabConfiguration = TabConfiguration.DRAWER_BOTTOM
    private var solipsismRailSize: Int = 72
    private var solipsismRailOnLeft: Boolean = false
    private var solipsismRailPosition: SolipsismRailPosition = SolipsismRailPosition.RIGHT
    private var shouldRunOnResumeActions = false
    private var appliedSystemAccent: Int? = null

    /**
     * Override this to provide an alternate theme that should be set for every instance of this
     * activity regardless of the user's preference.
     */
    @StyleRes
    protected open fun provideThemeOverride(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        injector.inject(this)
        themeId = userPreferences.useTheme
        tabConfiguration = userPreferences.tabConfiguration
        solipsismRailSize = userPreferences.solipsismRailSize
        solipsismRailOnLeft = userPreferences.solipsismRailOnLeft
        solipsismRailPosition = activeSolipsismRailPosition()
        val effectiveTheme = userPreferences.useTheme.effective(this)

        // set the theme
        setTheme(
            provideThemeOverride() ?: when (effectiveTheme) {
                AppTheme.LIGHT -> R.style.Theme_LightTheme
                AppTheme.DARK -> R.style.Theme_DarkTheme
                AppTheme.BLACK -> R.style.Theme_BlackTheme
                AppTheme.SYSTEM -> error("System theme must be resolved before applying it")
            }
        )
        theme.applyStyle(
            AccentPalette.overlayFor(
                effectiveTheme,
                userPreferences.accentPalette,
                userPreferences.matchSystemAccent
            ),
            true
        )
        appliedSystemAccent = AccentPalette.systemAccentFingerprint(this)
        super.onCreate(savedInstanceState)

        resetPreferences()
        CustomFontManager.applyToViewTree(window.decorView, userPreferences.customFontPath)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        withStyledAttributes(attrs = intArrayOf(R.attr.iconColorState)) {
            val iconTintList = getColorStateList(0)
            menu.iterator().forEach { menuItem ->
                menuItem.icon?.let {
                    DrawableCompat.setTintList(
                        DrawableCompat.wrap(it),
                        iconTintList
                    )
                }
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun resetPreferences() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (userPreferences.useBlackStatusBar || userPreferences.tabConfiguration == TabConfiguration.DESKTOP) {
                window.statusBarColor = Color.BLACK
            } else {
                window.statusBarColor = ThemeUtils.getStatusBarColor(this)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && shouldRunOnResumeActions) {
            shouldRunOnResumeActions = false
            onWindowVisibleToUserAfterResume()
        }
    }

    /**
     * Called after the activity is resumed
     * and the UI becomes visible to the user.
     * Called by onWindowFocusChanged only if
     * onResume has been called.
     */
    protected open fun onWindowVisibleToUserAfterResume() = Unit

    override fun onResume() {
        super.onResume()
        val systemAccent = AccentPalette.systemAccentFingerprint(this)
        if (userPreferences.matchSystemAccent && systemAccent != appliedSystemAccent) {
            recreate()
            return
        }
        resetPreferences()
        CustomFontManager.applyToViewTree(window.decorView, userPreferences.customFontPath)
        shouldRunOnResumeActions = true
        val nextTabConfiguration = userPreferences.tabConfiguration
        val nextRailPosition = activeSolipsismRailPosition()
        if (
            themeId != userPreferences.useTheme ||
            tabConfiguration != nextTabConfiguration ||
            solipsismRailSize != userPreferences.solipsismRailSize ||
            solipsismRailOnLeft != userPreferences.solipsismRailOnLeft ||
            solipsismRailPosition != nextRailPosition
        ) {
            restart()
        }
    }

    protected fun activeSolipsismRailPosition(): SolipsismRailPosition =
        userPreferences.solipsismRailPosition

    protected fun restart() {
        finish()
        startActivity(Intent(this, javaClass))
    }
}
