package com.krystelligence.solipsism.browser.download

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.net.Uri
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
import com.krystelligence.solipsism.extensions.snackbar
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
        contentLength: Long,
        blobData: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showDownloadDialog(activity, url, userAgent, contentDisposition, mimeType, contentLength, blobData)
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
                    showDownloadDialog(activity, url, userAgent, contentDisposition, mimeType, contentLength, blobData)
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
        contentLength: Long,
        blobData: String?
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
                    val saveDownload = { storedUrl: String ->
                        downloadsRepository.addDownloadIfNotExists(
                            DownloadEntry(
                                url = storedUrl,
                                title = fileName,
                                contentSize = downloadSize
                            )
                        ).subscribeOn(databaseScheduler)
                            .subscribeBy {
                                if (!it) logger.log(TAG, "error saving download to database")
                            }
                    }
                    if (blobData != null) {
                        downloadHandler.downloadBlob(
                            activity,
                            userPreferences,
                            url,
                            contentDisposition,
                            mimeType,
                            blobData
                        ).subscribeOn(databaseScheduler)
                            .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                            .subscribeBy(
                                onSuccess = { storedUrl ->
                                    saveDownload(storedUrl)
                                    activity.snackbar(R.string.download_pending)
                                },
                                onError = {
                                    logger.log(TAG, "error saving blob download", it)
                                    activity.snackbar(R.string.cannot_download)
                                }
                            )
                    } else {
                        downloadHandler.onDownloadStart(
                            activity,
                            userPreferences,
                            url,
                            userAgent,
                            contentDisposition,
                            mimeType,
                            downloadSize
                        )
                        saveDownload(url)
                    }
                }
                android.content.DialogInterface.BUTTON_NEGATIVE -> {}
            }
        }

        val builder = MaterialAlertDialogBuilder(activity)
        val message = activity.getString(R.string.dialog_download, downloadSize) +
            "\n\n" + activity.getString(R.string.download_donation_message)
        val dialog: Dialog = builder.setTitle(fileName)
            .setMessage(message)
            .setPositiveButton(
                activity.resources.getString(R.string.action_download),
                dialogClickListener
            )
            .setNegativeButton(
                activity.resources.getString(R.string.action_cancel),
                dialogClickListener
            )
            .setNeutralButton(R.string.action_donate) { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KO_FI_URL)))
            }
            .show()
        setDialogSize(activity, dialog)
        logger.log(TAG, "Downloading: $fileName")
    }

    companion object {
        private const val TAG = "DownloadPermissionsHelper"
        private const val KO_FI_URL = "https://ko-fi.com/kennethchoinfosec"
    }
}
