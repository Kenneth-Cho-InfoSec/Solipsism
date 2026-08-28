/*
 * Copyright 2014 A.C.R. Development
 */
@file:JvmName("Constants")

package com.krystelligence.solipsism.constant

// Hardcoded user agents
const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36"
const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; Pixel Build/QP1A.190711.019; wv) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Mobile Safari/537.36"
/** A mobile Chromium identity for foldable-aware responsive sites. */
const val FOLDING_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel Fold Build/UQ1A.240205.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.81 Mobile Safari/537.36"
const val CHROMPATIBILITY_FALLBACK_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"

private val CHROMIUM_FULL_VERSION = Regex("(?:Chrome|Chromium)/(\\d+(?:\\.\\d+){0,3})")

data class ChromiumVersion(val major: String, val full: String)

fun chromiumVersion(providerUserAgent: String): ChromiumVersion? {
    val full = CHROMIUM_FULL_VERSION.find(providerUserAgent)?.groupValues?.getOrNull(1)
        ?: return null
    return ChromiumVersion(major = full.substringBefore('.'), full = full)
}

/**
 * Builds the reduced Android identity used by the matching Chrome generation instead of claiming
 * a permanently hard-coded future browser version. Android System WebView and Chrome normally
 * update together, so the installed provider's major version is the most reliable local source.
 */
fun chromeCompatibilityUserAgent(providerUserAgent: String): String {
    val major = chromiumVersion(providerUserAgent)?.major
        ?: return providerUserAgent.ifBlank { CHROMPATIBILITY_FALLBACK_USER_AGENT }
    return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
}

// URL Schemes
const val HTTP = "http://"
const val HTTPS = "https://"
const val FILE = "file://"
const val ABOUT = "about:"
const val FOLDER = "folder://"

// Custom local page schemes
const val SCHEME_HOMEPAGE = "${ABOUT}home"
const val SCHEME_BLANK = "${ABOUT}blank"
const val SCHEME_BOOKMARKS = "${ABOUT}bookmarks"
/** Stable display/state sentinel for the generated homepage hosted by the Antares process. */
const val SCHEME_ANTARES_HOMEPAGE = "solipsism://offline-home"

const val UTF8 = "UTF-8"

// Default text encoding we will use
const val DEFAULT_ENCODING = UTF8

// Allowable text encodings for the WebView
@JvmField
val TEXT_ENCODINGS =
    arrayOf("ISO-8859-1", UTF8, "GBK", "Big5", "ISO-2022-JP", "SHIFT_JS", "EUC-JP", "EUC-KR")

const val INTENT_ORIGIN = "URL_INTENT_ORIGIN"
