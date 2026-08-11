package com.krystelligence.solipsism.browser

import com.krystelligence.solipsism.IncognitoBrowserActivity
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.cleanup.ExitCleanup
import com.krystelligence.solipsism.browser.di.IncognitoMode
import com.krystelligence.solipsism.browser.download.DownloadPermissionsHelper
import com.krystelligence.solipsism.browser.download.PendingDownload
import com.krystelligence.solipsism.extensions.copyToClipboard
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.qr.QrShowActivity
import com.krystelligence.solipsism.settings.activity.SettingsActivity
import com.krystelligence.solipsism.settings.activity.SettingsNavigation
import com.krystelligence.solipsism.utils.IntentUtils
import com.krystelligence.solipsism.utils.Utils
import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject

/**
 * The navigator implementation.
 */
class BrowserNavigator @Inject constructor(
    private val activity: FragmentActivity,
    private val clipboardManager: ClipboardManager,
    private val logger: Logger,
    private val downloadPermissionsHelper: DownloadPermissionsHelper,
    private val exitCleanup: ExitCleanup,
    @IncognitoMode private val incognitoMode: Boolean,
    private val activityManager: ActivityManager,
) : BrowserContract.Navigator {

    override fun openSettings() {
        activity.startActivity(
            Intent(activity, SettingsActivity::class.java).apply {
                putExtra(SettingsNavigation.EXTRA_INCOGNITO, incognitoMode)
            }
        )
    }

    override fun sharePage(url: String, title: String?) {
        IntentUtils(activity).shareUrl(url, title)
    }

    override fun copyPageLink(url: String) {
        clipboardManager.copyToClipboard(url)
        activity.snackbar(R.string.message_link_copied)
    }

    override fun closeBrowser() {
        exitCleanup.cleanUp()
        if (incognitoMode) {
            activityManager.appTasks
                .first { it.taskInfo.topActivity?.className == IncognitoBrowserActivity::class.java.name }
                .finishAndRemoveTask()
        } else {
            activity.finish()
        }
    }

    override fun addToHomeScreen(url: String, title: String, favicon: Bitmap?) {
        Utils.createShortcut(activity, url, title, favicon)
        logger.log(TAG, "Creating shortcut")
    }

    override fun download(pendingDownload: PendingDownload) {
        downloadPermissionsHelper.download(
            activity = activity,
            url = pendingDownload.url,
            userAgent = pendingDownload.userAgent,
            contentDisposition = pendingDownload.contentDisposition,
            mimeType = pendingDownload.mimeType,
            contentLength = pendingDownload.contentLength,
            origin = pendingDownload.origin,
            blobData = pendingDownload.blobData
        )
    }

    override fun backgroundBrowser() {
        if (incognitoMode) {
            exitCleanup.cleanUp()
            activityManager.appTasks
                .first { it.taskInfo.topActivity?.className == IncognitoBrowserActivity::class.java.name }
                .finishAndRemoveTask()
        } else {
            activity.moveTaskToBack(true)
        }
    }

    override fun launchIncognito(url: String?) {
        IncognitoBrowserActivity.launch(activity, url)
    }

    override fun showQrCode(url: String) {
        val intent = Intent(activity, QrShowActivity::class.java).apply {
            putExtra(QrShowActivity.EXTRA_URL, url)
        }
        activity.startActivity(intent)
    }

    companion object {
        private const val TAG = "BrowserNavigator"
    }

}
