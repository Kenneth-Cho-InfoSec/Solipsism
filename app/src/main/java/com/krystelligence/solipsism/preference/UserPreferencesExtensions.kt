package com.krystelligence.solipsism.preference

import com.krystelligence.solipsism.constant.chromeCompatibilityUserAgent
import com.krystelligence.solipsism.constant.DESKTOP_USER_AGENT
import com.krystelligence.solipsism.constant.MOBILE_USER_AGENT
import com.krystelligence.solipsism.constant.FOLDING_USER_AGENT
import android.app.Application
import android.webkit.WebSettings

/**
 * Return the user agent chosen by the user or the custom user agent entered by the user.
 */
fun UserPreferences.userAgent(application: Application): String =
    when (val choice = userAgentChoice) {
        1 -> if (chrompatibilityModeEnabled) {
            chromeCompatibilityUserAgent(WebSettings.getDefaultUserAgent(application))
        } else {
            WebSettings.getDefaultUserAgent(application)
        }
        2 -> DESKTOP_USER_AGENT
        3 -> MOBILE_USER_AGENT
        4 -> userAgentString.takeIf(String::isNotEmpty) ?: " "
        5 -> FOLDING_USER_AGENT
        else -> throw UnsupportedOperationException("Unknown userAgentChoice: $choice")
    }

fun UserPreferences.userAgent(defaultUserAgent: String): String =
    when (val choice = userAgentChoice) {
        1 -> if (chrompatibilityModeEnabled) {
            chromeCompatibilityUserAgent(defaultUserAgent)
        } else {
            defaultUserAgent
        }
        2 -> DESKTOP_USER_AGENT
        3 -> MOBILE_USER_AGENT
        4 -> userAgentString.takeIf(String::isNotEmpty) ?: " "
        5 -> FOLDING_USER_AGENT
        else -> throw UnsupportedOperationException("Unknown userAgentChoice: $choice")
    }

/**
 * Returns the user-agent override sent to Antares. An empty value means that Servo should retain
 * its own Android identity rather than inheriting the installed WebView provider's identity.
 */
fun UserPreferences.antaresUserAgent(providerUserAgent: String): String =
    when (val choice = userAgentChoice) {
        1 -> if (chrompatibilityModeEnabled) {
            chromeCompatibilityUserAgent(providerUserAgent)
        } else {
            ""
        }
        2 -> DESKTOP_USER_AGENT
        3 -> MOBILE_USER_AGENT
        4 -> userAgentString.takeIf(String::isNotEmpty) ?: " "
        5 -> FOLDING_USER_AGENT
        else -> throw UnsupportedOperationException("Unknown userAgentChoice: $choice")
    }
