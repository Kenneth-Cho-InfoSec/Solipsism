package com.krystelligence.solipsism.utils

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.webkit.WebView
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.constant.INTENT_ORIGIN
import java.net.URISyntaxException
import java.util.regex.Pattern

class IntentUtils(private val activity: Activity) {

    fun startActivityForUrl(tab: WebView?, url: String): Boolean {
        if (!url.startsWith("intent://")) return false
        val parsedIntent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: URISyntaxException) {
            return false
        }

        var intent = parsedIntent.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
        }

        val dataScheme = intent.data?.scheme?.lowercase(java.util.Locale.ROOT)
        if (dataScheme !in setOf("http", "https")) {
            return false
        }

        if (activity.packageManager.resolveActivity(intent, 0) == null) {
            val packageName = intent.`package`
            if (packageName != null) {
                intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:$packageName"))
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                activity.startActivity(intent)
                return true
            }
            return false
        }

        if (tab != null) {
            intent.putExtra(INTENT_ORIGIN, tab.hashCode())
        }

        val matcher = ACCEPTED_URI_SCHEMA.matcher(url)
        if (matcher.matches() && !isSpecializedHandlerAvailable(intent)) {
            return false
        }

        return try {
            activity.startActivityIfNeeded(intent, -1)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Search for intent handlers that are specific to this URL aka, specialized apps like google
     * maps or youtube.
     */
    private fun isSpecializedHandlerAvailable(intent: Intent): Boolean {
        val handlers: List<ResolveInfo> =
            activity.packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
        if (handlers.isEmpty()) {
            return false
        }

        for (resolveInfo in handlers) {
            val filter: IntentFilter = resolveInfo.filter ?: continue
            if (filter.countDataAuthorities() == 0) {
                continue
            }
            return true
        }
        return false
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

    private companion object {
        val ACCEPTED_URI_SCHEMA: Pattern = Pattern.compile(
            "(?i)((?:http|https|file)://|(?:inline|data|about|javascript):|(?:.*:.*@))(.*)"
        )
    }
}
