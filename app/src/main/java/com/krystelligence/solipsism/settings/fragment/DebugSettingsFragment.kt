package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.preference.DeveloperPreferences
import android.os.Bundle
import android.content.Intent
import com.krystelligence.solipsism.browser.engine.BrowserCore
import com.krystelligence.solipsism.browser.engine.BrowserCoreChooserActivity
import com.krystelligence.solipsism.browser.engine.BrowserCorePreferences
import javax.inject.Inject

class DebugSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var developerPreferences: DeveloperPreferences
    private lateinit var browserCorePreferences: BrowserCorePreferences

    override fun providePreferencesXmlResource() = R.xml.preference_debug

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        browserCorePreferences = BrowserCorePreferences(requireContext().applicationContext)
        clickablePreference(
            preference = BROWSER_CORE,
            summary = browserCoreSummary(),
            onClick = {
                startActivity(
                    Intent(requireContext(), BrowserCoreChooserActivity::class.java)
                        .putExtra(BrowserCoreChooserActivity.EXTRA_MANAGE_ONLY, true)
                )
            }
        )

        togglePreference(
            preference = EXPERIMENTAL_RAIL_LAYOUTS,
            isChecked = developerPreferences.experimentalRailLayoutsEnabled,
            onCheckChange = { change ->
                developerPreferences.experimentalRailLayoutsEnabled = change
            }
        )
        togglePreference(
            preference = ANTARES_COORDINATE_BRIDGE,
            isChecked = developerPreferences.antaresCoordinateBridgeEnabled,
            onCheckChange = { change ->
                developerPreferences.antaresCoordinateBridgeEnabled = change
                activity?.snackbar(R.string.debug_antares_coordinate_bridge_applies_to_new_tabs)
            },
        )
        togglePreference(
            preference = LEAK_CANARY,
            isChecked = developerPreferences.useLeakCanary,
            onCheckChange = { change ->
                activity?.snackbar(R.string.app_restart)
                developerPreferences.useLeakCanary = change
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (::browserCorePreferences.isInitialized) {
            findPreference<androidx.preference.Preference>(BROWSER_CORE)?.summary = browserCoreSummary()
        }
    }

    private fun browserCoreSummary(): String =
        if (browserCorePreferences.selectedCore == BrowserCore.ANTARES) {
            getString(R.string.debug_browser_core_summary_antares)
        } else {
            getString(R.string.debug_browser_core_summary_webview)
        }

    companion object {
        private const val EXPERIMENTAL_RAIL_LAYOUTS = "experimental_rail_layouts"
        private const val ANTARES_COORDINATE_BRIDGE = "antares_coordinate_bridge"
        private const val BROWSER_CORE = "browser_core"
        private const val LEAK_CANARY = "leak_canary_enabled"
    }
}
