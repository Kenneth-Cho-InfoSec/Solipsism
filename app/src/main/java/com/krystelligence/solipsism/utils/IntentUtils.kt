package com.krystelligence.solipsism.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.webkit.WebView
import com.krystelligence.solipsism.DefaultBrowserActivity
import com.krystelligence.solipsism.R
import java.net.URISyntaxException

/**
 * Open an http(s) URL in Solipsism's own browser window instead of the system
 * default browser. All in-app links must go through this so tapping them never
 * escapes to an external browser.
 */
fun Context.openUrlInOwnBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        setClass(this@openUrlInOwnBrowser, DefaultBrowserActivity::class.java)
        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }
    startActivity(intent)
}

class IntentUtils(private val activity: Activity) {

    /**
     * Handle `intent://` URLs carrying http(s) destinations.
     *
     * Web destinations always stay inside Solipsism's WebView: the underlying
     * https URL is loaded directly instead of being handed to the system
     * resolver (which would open the default browser). Returns true when the
     * navigation was consumed here.
     */
    fun startActivityForUrl(tab: WebView?, url: String): Boolean {
        if (!url.startsWith("intent://")) return false
        val parsedIntent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: URISyntaxException) {
            return false
        }

        val target = parsedIntent.data?.toString() ?: return false
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return false
        }

        if (tab == null) {
            return false
        }
        tab.loadUrl(target)
        return true
    }

    /**
     * Shares a URL to the system.
     */
    fun shareUrl(url: String?, title: String?) {
        if (url != null && !url.isSpecialUrl()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (title != null) {
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }
                putExtra(Intent.EXTRA_TEXT, url)
            }
            activity.startActivity(
                Intent.createChooser(shareIntent, activity.getString(R.string.dialog_title_share))
            )
        }
    }
}
