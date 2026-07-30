package com.krystelligence.solipsism.utils

import android.net.Uri
import android.webkit.URLUtil
import java.io.File
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

    fun isAllowedTopLevelNavigation(
        url: String,
        trustedInternalRoots: Collection<File> = emptyList()
    ): Boolean {
        val sanitized = sanitizeUserInput(url)
        val scheme = Uri.parse(sanitized).scheme?.lowercase(Locale.ROOT)

        if (scheme in blockedTopLevelSchemes) {
            return false
        }

        return URLUtil.isNetworkUrl(sanitized)
            || URLUtil.isAboutUrl(sanitized)
            || isTrustedInternalFileUrl(sanitized, trustedInternalRoots)
    }

    /**
     * Allows only app-private generated pages. A filename such as homepage.html is not enough:
     * public storage can be replaced by another app and must never become executable browser
     * content.
     */
    fun isTrustedInternalFileUrl(url: String, trustedInternalRoots: Collection<File>): Boolean {
        if (trustedInternalRoots.isEmpty() || !URLUtil.isFileUrl(url)) return false
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return false
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        return trustedInternalRoots.any { rootCandidate ->
            val root = runCatching { rootCandidate.canonicalFile }.getOrNull() ?: return@any false
            target == root || target.path.startsWith(root.path + File.separator)
        }
    }
}
