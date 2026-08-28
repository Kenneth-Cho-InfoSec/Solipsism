package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.R
import android.os.Bundle

/**
 * The root settings list.
 */
class RootSettingsFragment : AbstractSettingsFragment() {
    override fun providePreferencesXmlResource(): Int = R.xml.preference_root

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
    }
}
