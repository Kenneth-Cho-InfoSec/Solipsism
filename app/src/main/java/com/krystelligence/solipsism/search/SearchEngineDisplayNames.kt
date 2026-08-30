package com.krystelligence.solipsism.search

import android.content.Context
import androidx.annotation.StringRes
import com.krystelligence.solipsism.R

/** Official provider names are brand names and must not be localised. */
object SearchEngineDisplayNames {
    private val names = mapOf(
        R.string.search_engine_google to "Google",
        R.string.search_engine_ask to "Ask",
        R.string.search_engine_bing to "Bing",
        R.string.search_engine_yahoo to "Yahoo",
        R.string.search_engine_startpage to "Startpage",
        R.string.search_engine_startpage_mobile to "Startpage Mobile",
        R.string.search_engine_duckduckgo to "DuckDuckGo",
        R.string.search_engine_duckduckgo_lite to "DuckDuckGo Lite",
        R.string.search_engine_baidu to "Baidu",
        R.string.search_engine_yandex to "Yandex",
        R.string.search_engine_naver to "Naver",
    )

    fun get(context: Context, @StringRes resource: Int): String =
        names[resource] ?: context.getString(resource)
}
