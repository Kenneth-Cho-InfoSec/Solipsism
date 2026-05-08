package com.krystelligence.solipsism.browser.download

import android.Manifest
import android.app.Dialog
import android.os.Build
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.DatabaseScheduler
import com.krystelligence.solipsism.database.downloads.DownloadEntry
import com.krystelligence.solipsism.database.downloads.DownloadsRepository
import com.krystelligence.solipsism.dialog.BrowserDialog.setDialogSize
import com.krystelligence.solipsism.download.DownloadHandler
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.FileUtils
import com.permissionx.guolindev.PermissionX
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class DownloadPermissionsHelper @Inject constructor(
    private val downloadHandler: DownloadHandler,
    private val userPreferences: UserPreferences,
    private val logger: Logger,
    private val downloadsRepository: DownloadsRepository,
    @DatabaseScheduler private val databaseScheduler: Scheduler
) {

    fun download(
        activity: FragmentActivity,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showDownloadDialog(activity, url, userAgent, contentDisposition, mimeType, contentLength)
            return
        }

        PermissionX.init(activity)
            .permissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(deniedList, activity.getString(R.string.permission_description_storage), activity.getString(R.string.action_ok))
            }
            .request { allGranted, _, _ ->
                if (allGranted) {
                    showDownloadDialog(activity, url, userAgent, contentDisposition, mimeType, contentLength)
                } else {
                    logger.log(TAG, "Download permission denied")
                }
            }
    }

    /**
     * Legacy support for activities not using PermissionX yet
     */
    fun onRequestPermissionsResult(activity: FragmentActivity, grantResults: IntArray) {
        // No-op as we moved to PermissionX which handles its own callbacks
    }

    private fun showDownloadDialog(
        activity: FragmentActivity,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val guessedFileName = if (mimeType != null && MimeTypeMap.getSingleton().hasMimeType(mimeType)) {
            URLUtil.guessFileName(url, contentDisposition, mimeType)
        } else {
            URLUtil.guessFileName(url, contentDisposition, null)
        }
        val fileName = FileUtils.sanitizeFileName(guessedFileName)
        val downloadSize: String = if (contentLength > 0) {
            Formatter.formatFileSize(activity, contentLength)
        } else {
            activity.getString(R.string.unknown_size)
        }

        val dialogClickListener = android.content.DialogInterface.OnClickListener { _, which ->
            when (which) {
                android.content.DialogInterface.BUTTON_POSITIVE -> {
                    downloadHandler.onDownloadStart(
                        activity,
                        userPreferences,
                        url,
                        userAgent,
                        contentDisposition,
                        mimeType,
                        downloadSize
                    )
                    downloadsRepository.addDownloadIfNotExists(
                        DownloadEntry(
                            url = url,
                            title = fileName,
                            contentSize = downloadSize
                        )
                    ).subscribeOn(databaseScheduler)
                        .subscribeBy {
                            if (!it) {
                                logger.log(TAG, "error saving download to database")
                            }
                        }
                }
                android.content.DialogInterface.BUTTON_NEGATIVE -> {}
            }
        }

        val builder = MaterialAlertDialogBuilder(activity)
        val message: String = activity.getString(R.string.dialog_download, downloadSize)
        val dialog: Dialog = builder.setTitle(fileName)
            .setMessage(message)
            .setPositiveButton(
                activity.resources.getString(R.string.action_download),
                dialogClickListener
            )
            .setNegativeButton(
                activity.resources.getString(R.string.action_cancel),
                dialogClickListener
            ).show()
        setDialogSize(activity, dialog)
        logger.log(TAG, "Downloading: $fileName")
    }

    companion object {
        private const val TAG = "DownloadPermissionsHelper"
    }
}
