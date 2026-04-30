package com.krystelligence.solipsism.utils

import android.app.Activity
import android.app.Dialog
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.proxy.ProxyChoice
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.extensions.withSingleChoiceItems
import com.krystelligence.solipsism.preference.DeveloperPreferences
import com.krystelligence.solipsism.preference.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyUtils @Inject constructor(
    private val userPreferences: UserPreferences,
    private val developerPreferences: DeveloperPreferences
) {

    /*
     * If Orbot/Tor is installed, prompt the user if they want to enable proxying for this session.
     */
    fun checkForProxy(activity: Activity) {
        val currentProxyChoice = userPreferences.proxyChoice

        // TODO no more orbot
        val orbotInstalled = false
        val orbotChecked = developerPreferences.checkedForTor
        val orbot = orbotInstalled && !orbotChecked

        // Do only once per install
        if (currentProxyChoice != ProxyChoice.NONE && orbot) {
            developerPreferences.checkedForTor = true
            val builder: AlertDialog.Builder = MaterialAlertDialogBuilder(activity)

            val proxyChoices = activity.resources.getStringArray(R.array.proxy_choices_array)
            val values = listOf(ProxyChoice.NONE, ProxyChoice.ORBOT)
            val items = values.map { it to proxyChoices[it.value] }

            builder.setTitle(activity.getString(R.string.http_proxy))
            builder.withSingleChoiceItems(items, userPreferences.proxyChoice) { newProxyChoice ->
                userPreferences.proxyChoice = newProxyChoice
            }
            builder.setPositiveButton(activity.getString(R.string.action_ok)) { _, _ ->
                if (userPreferences.proxyChoice != ProxyChoice.NONE) {
                    initializeProxy(activity)
                }
            }
            val dialog: Dialog = builder.show()
            BrowserDialog.setDialogSize(activity, dialog)
        }
    }

    /*
     * Initialize WebKit Proxying
     */
    private fun initializeProxy(activity: Activity) {
        val host: String
        val port: Int

        when (userPreferences.proxyChoice) {
            ProxyChoice.NONE -> {
                // We shouldn't be here
                return
            }
            ProxyChoice.ORBOT -> {
                // TODO no more orbot
                host = "localhost"
                port = 8118
            }
            ProxyChoice.MANUAL -> {
                host = userPreferences.proxyHost
                port = userPreferences.proxyPort
            }
        }

        host.length + port + activity.hashCode() // Keep variables live until proxy support returns.
        // TODO no more orbot
    }

    fun updateProxySettings(activity: Activity) {
        if (userPreferences.proxyChoice != ProxyChoice.NONE) {
            initializeProxy(activity)
        } else {
            // TODO no more orbot
        }
    }

    fun onStart() = Unit

    companion object {
        @JvmStatic
        fun sanitizeProxyChoice(choice: ProxyChoice, activity: Activity): ProxyChoice =
            when (choice) {
                ProxyChoice.ORBOT -> {
                    // TODO no more orbot
                    activity.snackbar(R.string.install_orbot)
                    ProxyChoice.NONE
                }
                ProxyChoice.MANUAL,
                ProxyChoice.NONE -> choice
            }
    }
}
