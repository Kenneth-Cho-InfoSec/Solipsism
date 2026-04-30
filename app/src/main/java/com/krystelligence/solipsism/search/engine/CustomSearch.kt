package com.krystelligence.solipsism.search.engine

import com.krystelligence.solipsism.R

/**
 * A custom search engine.
 */
class CustomSearch(queryUrl: String) : BaseSearchEngine(
    "file:///android_asset/solipsism.png",
    queryUrl,
    R.string.search_engine_custom
)
