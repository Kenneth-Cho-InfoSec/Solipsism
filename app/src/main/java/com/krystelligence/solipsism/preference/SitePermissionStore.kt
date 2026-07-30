package com.krystelligence.solipsism.preference

import android.content.SharedPreferences
import com.krystelligence.solipsism.browser.di.UserPrefs
import org.json.JSONObject
import java.net.URI
import java.net.IDN
import javax.inject.Inject
import javax.inject.Singleton

enum class SitePermissionDecision {
    DEFAULT,
    ALLOW,
    ASK,
    DENY
}

enum class SitePermissionKey {
    LOCATION,
    CAMERA,
    MICROPHONE,
    NOTIFICATIONS,
    CLIPBOARD,
    MOTION_SENSORS,
    PROTECTED_CONTENT,
    EMBEDDED_CONTENT,
    LOCAL_NETWORK,
    AUTOMATIC_DOWNLOADS,
    NFC,
    USB,
    SERIAL,
    FILE_EDITING,
    VIRTUAL_REALITY,
    AUGMENTED_REALITY,
    DEVICE_USE,
    APPS_ON_DEVICE,
    JAVASCRIPT_JIT
}

/** Stores per-origin website decisions without changing the browser-wide preferences. */
@Singleton
class SitePermissionStore @Inject constructor(
    @UserPrefs private val preferences: SharedPreferences
) {

    fun decision(origin: String, permission: SitePermissionKey): SitePermissionDecision {
        val normalized = normalizeOrigin(origin) ?: return SitePermissionDecision.DEFAULT
        val value = read().optJSONObject(normalized)?.optString(permission.name)
        return value?.let { runCatching { SitePermissionDecision.valueOf(it) }.getOrNull() }
            ?: SitePermissionDecision.DEFAULT
    }

    fun setDecision(origin: String, permission: SitePermissionKey, decision: SitePermissionDecision) {
        val normalized = normalizeOrigin(origin) ?: return
        val root = read()
        val site = root.optJSONObject(normalized) ?: JSONObject()
        if (decision == SitePermissionDecision.DEFAULT) {
            site.remove(permission.name)
        } else {
            site.put(permission.name, decision.name)
        }
        if (site.length() == 0) root.remove(normalized) else root.put(normalized, site)
        preferences.edit().putString(STORAGE_KEY, root.toString()).apply()
    }

    /**
     * Returns only origins that still satisfy the current validation rules.
     * This also hides malformed profiles written by older builds or interrupted input.
     */
    fun savedOrigins(): List<String> = read().keys().asSequence()
        .filter { normalizeOrigin(it) != null }
        .toList()
        .sorted()

    fun ensureOrigin(origin: String): Boolean {
        val normalized = normalizeOrigin(origin) ?: return false
        val root = read()
        if (!root.has(normalized)) {
            root.put(normalized, JSONObject())
            preferences.edit().putString(STORAGE_KEY, root.toString()).apply()
        }
        return true
    }

    fun clearOrigin(origin: String) {
        normalizeOrigin(origin)?.let {
            val root = read().also { json -> json.remove(it) }
            preferences.edit().putString(STORAGE_KEY, root.toString()).apply()
        }
    }

    fun clearAll() = preferences.edit().remove(STORAGE_KEY).apply()

    companion object {
        private const val STORAGE_KEY = "sitePermissionProfiles"
        private val HTTP_SCHEME_SUFFIX = Regex("(?i)(?:https?|http)$")

        fun normalizeOrigin(value: String): String? = runCatching {
            val input = value.trim()
            if (input.isEmpty() || input.any(Char::isWhitespace)) return null
            val uri = URI(input)
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
            if (uri.rawUserInfo != null || uri.fragment != null) return null

            // A common keyboard/ADB input error appends the scheme to the hostname,
            // e.g. "https://example.comhttps". Do not persist that as a real origin.
            val host = IDN.toASCII(uri.host).lowercase()
            if (HTTP_SCHEME_SUFFIX.containsMatchIn(host)) return null

            buildString {
                append(uri.scheme.lowercase())
                append("://")
                append(host)
                if (uri.port != -1) append(':').append(uri.port)
            }
        }.getOrNull()
    }

    private fun read(): JSONObject = runCatching {
        JSONObject(preferences.getString(STORAGE_KEY, "{}") ?: "{}")
    }.getOrElse { JSONObject() }
}
