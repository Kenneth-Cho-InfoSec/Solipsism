package com.krystelligence.solipsism.search.engine

import com.krystelligence.solipsism.R

/**
 * The Ask search engine.
 */
class AskSearch : BaseSearchEngine(
    "file:///android_asset/ask.png",
    "https://www.ask.com/web?qsrc=0&o=0&l=dir&qo=SolipsismBrowser&q=",
    R.string.search_engine_ask
)
