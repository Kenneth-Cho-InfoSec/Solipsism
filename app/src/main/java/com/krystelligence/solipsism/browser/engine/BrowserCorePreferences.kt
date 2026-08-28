package com.krystelligence.solipsism.browser.engine

import android.content.Context
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserCorePreferences @Inject constructor(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var selectedCore: BrowserCore
        get() = BrowserCore.fromPreference(preferences.getString(KEY_SELECTED_CORE, null))
        set(value) = preferences.edit { putString(KEY_SELECTED_CORE, value.preferenceValue) }

    var onboardingComplete: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = preferences.edit { putBoolean(KEY_ONBOARDING_COMPLETE, value) }

    fun select(core: BrowserCore) {
        preferences.edit {
            putString(KEY_SELECTED_CORE, core.preferenceValue)
            putBoolean(KEY_ONBOARDING_COMPLETE, true)
        }
    }

    private companion object {
        const val FILE_NAME = "browser_core"
        const val KEY_SELECTED_CORE = "selected_core"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
