package com.krystelligence.solipsism.utils

import android.app.Activity
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.krystelligence.solipsism.database.history.HistoryRepository
import io.reactivex.rxjava3.core.Scheduler

object WebUtils {

    @JvmStatic
    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
    }

    @JvmStatic
    fun clearWebStorage() {
        WebStorage.getInstance().deleteAllData()
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun clearHistory(
        context: Context,
        historyRepository: HistoryRepository,
        databaseScheduler: Scheduler
    ) {
        historyRepository.deleteHistory()
            .subscribeOn(databaseScheduler)
            .subscribe()
        val webViewDatabase = WebViewDatabase.getInstance(context)
        webViewDatabase.clearFormData()
        webViewDatabase.clearHttpAuthUsernamePassword()
        Utils.trimCache(context)
    }

    @JvmStatic
    fun clearCache(activity: Activity) {
        val webView = WebView(activity)
        webView.clearCache(true)
        webView.destroy()
    }
}
