package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.BuildConfig
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.BrowserActivity
import com.krystelligence.solipsism.browser.di.IncognitoMode
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.utils.IntentUtils
import com.krystelligence.solipsism.utils.NavigationSecurity
import com.krystelligence.solipsism.utils.Utils
import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.net.MailTo
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import javax.inject.Inject

/**
 * Handle URLs loaded by the [WebView] and determine if they should be loaded by the browser or
 * another app.
 */
class UrlHandler @Inject constructor(
    private val activity: Activity,
    private val logger: Logger,
    private val intentUtils: IntentUtils,
    @IncognitoMode private val incognitoMode: Boolean
) {

    /**
     * Return true if the [url] should be loaded by another app or in another way, false if the
     * browser can let the [view] continue loading as it wants.
     */
    fun shouldOverrideLoading(
        view: WebView,
        url: String,
        headers: Map<String, String>
    ): Boolean {
        if (!NavigationSecurity.isTrustedInternalFileUrl(url, trustedInternalRoots())) {
            // The generated Solipsism pages are the only pages that need file access. Reset this
            // before every other top-level navigation so a local page cannot retain the exception.
            view.settings.allowFileAccess = false
        }
        if (url == HISTORY_CLEAR_URL) {
            (activity as? BrowserActivity)?.clearAllHistoryFromHistoryPage()
            return true
        }
        if (url == HISTORY_DECOY_URL) {
            (activity as? BrowserActivity)?.showHistoryDecoyModePrompt()
            return true
        }
        if (url == DOWNLOADS_CLEAR_URL) {
            (activity as? BrowserActivity)?.clearAllDownloadsFromDownloadsPage()
            return true
        }
        if (url == DOWNLOADS_DECOY_URL) {
            (activity as? BrowserActivity)?.showDownloadDecoyModePrompt()
            return true
        }
        if (url.startsWith(DECOY_DOWNLOAD_URL_PREFIX)) return true
        if (incognitoMode) {
            // If we are in incognito, immediately load, we don't want the url to leave the app
            return continueLoadingUrl(view, url, headers)
        }
        if (URLUtil.isAboutUrl(url)) {
            // If this is an about page, immediately load, we don't need to leave the app
            return continueLoadingUrl(view, url, headers)
        }

        return if (isMailOrIntent(url, view) || intentUtils.startActivityForUrl(view, url)) {
            // If it was a mailto: link, or an intent, or could be launched elsewhere, do that
            true
        } else {
            // If none of the special conditions was met, continue with loading the url
            continueLoadingUrl(view, url, headers)
        }
    }

    private fun continueLoadingUrl(
        webView: WebView,
        url: String,
        headers: Map<String, String>
    ): Boolean {
        if (!NavigationSecurity.isAllowedTopLevelNavigation(url, trustedInternalRoots())) {
            webView.stopLoading()
            return true
        }
        return when {
            headers.isEmpty() -> false
            else -> {
                webView.loadUrl(url, headers)
                true
            }
        }
    }

    private fun isMailOrIntent(url: String, view: WebView): Boolean {
        if (url.startsWith("mailto:")) {
            val mailTo = MailTo.parse(url)
            val i = Utils.newEmailIntent(mailTo.to, mailTo.subject, mailTo.body, mailTo.cc)
            activity.startActivity(i)
            view.reload()
            return true
        } else if (url.startsWith("intent://")) {
            // All intent:// URLs must go through IntentUtils, which rejects unsafe data schemes.
            return intentUtils.startActivityForUrl(view, url)
        } else if (URLUtil.isFileUrl(url)) {
            if (NavigationSecurity.isTrustedInternalFileUrl(url, trustedInternalRoots())) {
                return false
            }
            val path = runCatching { url.toUri().path }.getOrNull()
            val file = path?.let(::File)

            if (file?.isFile == true) {
                val downloadsRoot = runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .canonicalFile
                }.getOrNull()
                val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
                val isDownload = downloadsRoot != null && canonicalFile != null &&
                    (canonicalFile == downloadsRoot ||
                        canonicalFile.path.startsWith(downloadsRoot.path + File.separator))
                if (!isDownload) return true

                try {
                    val newMimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(Utils.guessFileExtension(file.toString()))
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    val contentUri = FileProvider.getUriForFile(
                        activity,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        canonicalFile
                    )
                    intent.setDataAndType(contentUri, newMimeType)

                    activity.startActivity(intent)
                } catch (e: Exception) {
                    logger.log(TAG, "Unable to open downloaded file", e)
                }

            } else {
                activity.snackbar(R.string.message_open_download_fail)
            }
            return true
        }
        return false
    }

    private fun trustedInternalRoots(): List<File> = listOf(
        File(activity.filesDir, "generated-html"),
        File(activity.filesDir, "homepage")
    )

    companion object {
        private const val TAG = "UrlHandler"
        private const val HISTORY_CLEAR_URL = "solipsism://clear-history"
        private const val HISTORY_DECOY_URL = "solipsism://decoy-mode"
        private const val DOWNLOADS_CLEAR_URL = "solipsism://clear-download-history"
        private const val DOWNLOADS_DECOY_URL = "solipsism://download-decoy-mode"
        private const val DECOY_DOWNLOAD_URL_PREFIX = "solipsism://decoy-download/"
    }
}
