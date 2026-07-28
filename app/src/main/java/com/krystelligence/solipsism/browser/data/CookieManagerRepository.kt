package com.krystelligence.solipsism.browser.data

import android.webkit.CookieManager
import android.webkit.ValueCallback
import androidx.annotation.MainThread
import java.net.URI
import javax.inject.Inject

/**
 * Site-scoped operations over Android WebView's shared CookieManager.
 *
 * CookieManager exposes the Cookie request header rather than a complete cookie database. The
 * returned entries therefore intentionally contain only the name and value that are visible to
 * the requested URL. Domain/path attributes must be supplied explicitly when editing.
 */
data class BrowserCookie(
    val name: String,
    val value: String
)

data class CookieDraft(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String = "/",
    val maxAgeSeconds: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: String? = null,
    val partitioned: Boolean = false
)

sealed interface CookieOperationResult {
    data object Success : CookieOperationResult
    data class Failure(val reason: String) : CookieOperationResult
}

@MainThread
class CookieManagerRepository @Inject constructor() {
    private val cookieManager = CookieManager.getInstance()

    fun listForUrl(url: String): List<BrowserCookie> =
        parseCookieHeader(cookieManager.getCookie(url))

    fun set(
        url: String,
        draft: CookieDraft,
        callback: (CookieOperationResult) -> Unit
    ) {
        val validation = validate(url, draft)
        if (validation != null) {
            callback(CookieOperationResult.Failure(validation))
            return
        }
        cookieManager.setCookie(url, draft.toSetCookieHeader()) { success ->
            cookieManager.flush()
            callback(
                if (success == true) CookieOperationResult.Success
                else CookieOperationResult.Failure("The WebView rejected this cookie.")
            )
        }
    }

    fun delete(
        url: String,
        name: String,
        domain: String? = null,
        path: String = "/",
        callback: (CookieOperationResult) -> Unit
    ) {
        val draft = CookieDraft(
            name = name,
            value = "",
            domain = domain,
            path = path,
            maxAgeSeconds = 0
        )
        set(url, draft, callback)
    }

    fun deleteVisibleCookies(
        url: String,
        callback: (CookieOperationResult) -> Unit
    ) {
        val cookies = listForUrl(url)
        if (cookies.isEmpty()) {
            callback(CookieOperationResult.Success)
            return
        }
        var remaining = cookies.size
        var failed = false
        cookies.forEach { cookie ->
            delete(url, cookie.name) { result ->
                if (result is CookieOperationResult.Failure) failed = true
                remaining -= 1
                if (remaining == 0) {
                    callback(
                        if (failed) {
                            CookieOperationResult.Failure(
                                "Some cookies could not be removed. Their path or domain may be unavailable."
                            )
                        } else {
                            CookieOperationResult.Success
                        }
                    )
                }
            }
        }
    }

    private fun validate(url: String, draft: CookieDraft): String? {
        val parsedUrl = runCatching { URI(url) }.getOrNull()
        val scheme = parsedUrl?.scheme
        if (parsedUrl == null || scheme !in setOf("http", "https") || parsedUrl.host.isNullOrBlank()) {
            return "Cookies can only be managed for HTTP(S) sites."
        }
        if (draft.name.isBlank() || draft.name.any { it.isWhitespace() || it in "();,\\\"" }) {
            return "Cookie name contains invalid characters."
        }
        if (draft.value.contains(';') || draft.value.contains('\n') || draft.value.contains('\r')) {
            return "Cookie value cannot contain semicolons or line breaks."
        }
        if (!draft.path.startsWith('/')) {
            return "Cookie path must start with /."
        }
        if (draft.domain?.contains(';') == true || draft.domain?.contains(' ') == true) {
            return "Cookie domain is invalid."
        }
        if (draft.secure && scheme != "https") {
            return "Secure cookies require an HTTPS URL."
        }
        if (draft.partitioned && !draft.secure) {
            return "Partitioned cookies require Secure."
        }
        if (draft.sameSite.equals("None", ignoreCase = true) && !draft.secure) {
            return "SameSite=None cookies require Secure."
        }
        return null
    }

    private fun CookieDraft.toSetCookieHeader(): String = buildString {
        append(name)
        append('=')
        append(value)
        domain?.takeIf(String::isNotBlank)?.let { append("; Domain=").append(it) }
        append("; Path=").append(path)
        maxAgeSeconds?.let { append("; Max-Age=").append(it) }
        if (secure) append("; Secure")
        if (httpOnly) append("; HttpOnly")
        sameSite?.takeIf(String::isNotBlank)?.let { append("; SameSite=").append(it) }
        if (partitioned) append("; Partitioned")
    }

    companion object {
        fun parseCookieHeader(header: String?): List<BrowserCookie> =
            header.orEmpty()
                .split(';')
                .mapNotNull { part ->
                    val separator = part.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    BrowserCookie(
                        name = part.substring(0, separator).trim(),
                        value = part.substring(separator + 1).trim()
                    ).takeIf { it.name.isNotEmpty() }
                }

        fun isManageableUrl(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
