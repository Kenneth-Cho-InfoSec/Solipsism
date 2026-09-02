package com.krystelligence.solipsism.utils

import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.proxy.ProxyChoice
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.preference.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyUtils @Inject constructor(
    private val userPreferences: UserPreferences
) {

    /*
     * If Orbot/Tor is installed, prompt the user if they want to enable proxying for this session.
     */
    fun checkForProxy(activity: Activity) {
        // Proxy selection is applied deterministically from persisted preferences. External
        // proxy discovery is avoided because Android does not expose a reliable Orbot API.
        updateProxySettings(activity)
    }

    /*
     * Initialize WebKit Proxying
     */
    private fun initializeProxy(activity: Activity) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val host: String
        val port: Int

        when (userPreferences.proxyChoice) {
            ProxyChoice.NONE -> {
                // We shouldn't be here
                return
            }
            ProxyChoice.ORBOT -> {
                host = "localhost"
                port = 8118
            }
            ProxyChoice.MANUAL -> {
                host = userPreferences.proxyHost
                port = userPreferences.proxyPort
            }
        }

        val proxyConfig = ProxyConfig.Builder()
            .addProxyRule("$host:$port", ProxyConfig.MATCH_ALL_SCHEMES)
            .build()
        ProxyController.getInstance().setProxyOverride(
            proxyConfig,
            ContextCompat.getMainExecutor(activity),
            Runnable {}
        )
    }

    fun updateProxySettings(activity: Activity) {
        if (userPreferences.proxyChoice != ProxyChoice.NONE) {
            initializeProxy(activity)
        } else {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(
                    ContextCompat.getMainExecutor(activity),
                    Runnable {}
                )
            }
        }
    }

    fun onStart() = Unit

    companion object {
        @JvmStatic
        fun sanitizeProxyChoice(choice: ProxyChoice, activity: Activity): ProxyChoice =
            when (choice) {
                ProxyChoice.ORBOT -> {
                    activity.snackbar(R.string.install_orbot)
                    ProxyChoice.NONE
                }
                ProxyChoice.MANUAL,
                ProxyChoice.NONE -> choice
            }
    }
}
