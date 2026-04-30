package com.krystelligence.solipsism.search.engine

import com.krystelligence.solipsism.R

/**
 * The DuckDuckGo search engine.
 *
 * See https://duckduckgo.com/assets/logo_homepage.normal.v101.png for the icon.
 */
class DuckSearch : BaseSearchEngine(
    "file:///android_asset/duckduckgo.png",
    "https://duckduckgo.com/?t=solipsism&q=",
    R.string.search_engine_duckduckgo
)
