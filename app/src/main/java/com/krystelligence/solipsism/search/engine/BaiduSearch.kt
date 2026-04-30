package com.krystelligence.solipsism.search.engine

import com.krystelligence.solipsism.R

/**
 * The Baidu search engine.
 *
 * See http://www.baidu.com/img/bdlogo.gif for the icon.
 */
class BaiduSearch : BaseSearchEngine(
    "file:///android_asset/baidu.png",
    "https://www.baidu.com/s?wd=",
    R.string.search_engine_baidu
)
