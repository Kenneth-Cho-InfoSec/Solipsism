/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.R
import android.os.Bundle
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.preference.UserPreferences
import javax.inject.Inject

class AboutSettingsFragment : AbstractSettingsFragment() {

    @Inject lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_about

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)
        togglePreference(
            preference = RELEASE_NOTES_ENABLED,
            isChecked = userPreferences.releaseNotesEnabled,
            onCheckChange = { userPreferences.releaseNotesEnabled = it }
        )
        togglePreference(
            preference = UPDATE_NOTIFICATIONS_ENABLED,
            isChecked = userPreferences.updateNotificationsEnabled,
            onCheckChange = { userPreferences.updateNotificationsEnabled = it }
        )
    }

    companion object {
        private const val RELEASE_NOTES_ENABLED = "release_notes_enabled"
        private const val UPDATE_NOTIFICATIONS_ENABLED = "update_notifications_enabled"
    }
}
