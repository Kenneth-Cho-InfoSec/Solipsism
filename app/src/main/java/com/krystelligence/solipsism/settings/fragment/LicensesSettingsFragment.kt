package com.krystelligence.solipsism.settings.fragment

import android.os.Bundle
import com.krystelligence.solipsism.R

class LicensesSettingsFragment : AbstractSettingsFragment() {
    override fun providePreferencesXmlResource() = R.xml.preference_licenses
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
    }
}
