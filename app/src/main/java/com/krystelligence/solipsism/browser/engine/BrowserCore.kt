package com.krystelligence.solipsism.browser.engine

enum class BrowserCore(val preferenceValue: String) {
    WEBVIEW("webview"),
    ANTARES("antares");

    companion object {
        fun fromPreference(value: String?): BrowserCore =
            entries.firstOrNull { it.preferenceValue == value } ?: WEBVIEW
    }
}
