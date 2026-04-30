package com.krystelligence.solipsism.utils

import android.net.Uri
import android.webkit.URLUtil
import java.util.Locale

/**
 * Centralized top-level navigation checks. Subresource loading remains WebView's job; these checks
 * gate URLs supplied by external intents, the address bar, and browser-level redirects.
 */
object NavigationSecurity {

    private val blockedTopLevelSchemes = setOf("javascript", "data", "inline")
    private const val MAX_URL_LENGTH = 8192

    fun sanitizeUserInput(input: String): String {
        return input
            .filterNot { it.code in 0..31 || it.code == 127 }
            .trim()
            .take(MAX_URL_LENGTH)
    }

    fun isAllowedFromExternalIntent(url: String): Boolean {
        val sanitized = sanitizeUserInput(url)
        return URLUtil.isNetworkUrl(sanitized)
    }

    fun isAllowedTopLevelNavigation(url: String): Boolean {
        val sanitized = sanitizeUserInput(url)
        val scheme = Uri.parse(sanitized).scheme?.lowercase(Locale.ROOT)

        if (scheme in blockedTopLevelSchemes) {
            return false
        }

        return URLUtil.isNetworkUrl(sanitized)
            || URLUtil.isAboutUrl(sanitized)
            || sanitized.isSpecialUrl()
    }
}
