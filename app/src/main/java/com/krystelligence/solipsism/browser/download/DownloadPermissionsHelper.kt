package com.krystelligence.solipsism.browser.download

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.text.format.Formatter
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.DatabaseScheduler
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.di.NetworkScheduler
import com.krystelligence.solipsism.database.downloads.DownloadEntry
import com.krystelligence.solipsism.database.downloads.DownloadsRepository
import com.krystelligence.solipsism.dialog.BrowserDialog.setDialogSize
import com.krystelligence.solipsism.download.DownloadHandler
import com.krystelligence.solipsism.download.DownloadFilenameResolver
import com.krystelligence.solipsism.download.DownloadMetadataResolver
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.preference.SitePermissionDecision
import com.krystelligence.solipsism.preference.SitePermissionKey
import com.krystelligence.solipsism.preference.SitePermissionStore
import com.krystelligence.solipsism.virustotal.VirusTotalCancellationSignal
import com.krystelligence.solipsism.virustotal.VirusTotalDownloadCoordinator
import com.krystelligence.solipsism.virustotal.VirusTotalDownloadRequest
import com.krystelligence.solipsism.virustotal.VirusTotalDownloadResult
import com.krystelligence.solipsism.virustotal.VirusTotalException
import com.krystelligence.solipsism.virustotal.VirusTotalFilePolicy
import com.krystelligence.solipsism.virustotal.VirusTotalScanNotification
import com.krystelligence.solipsism.haptics.HapticFeedbackController
import com.permissionx.guolindev.PermissionX
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.SerialDisposable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class DownloadPermissionsHelper @Inject constructor(
    private val downloadHandler: DownloadHandler,
    private val downloadMetadataResolver: DownloadMetadataResolver,
    private val userPreferences: UserPreferences,
    private val sitePermissionStore: SitePermissionStore,
    private val logger: Logger,
    private val downloadsRepository: DownloadsRepository,
    private val virusTotalCoordinator: VirusTotalDownloadCoordinator,
    private val virusTotalNotification: VirusTotalScanNotification,
    private val hapticFeedback: HapticFeedbackController,
    @DatabaseScheduler private val databaseScheduler: Scheduler,
    @NetworkScheduler private val networkScheduler: Scheduler,
    @MainScheduler private val mainScheduler: Scheduler
) {

    private val disposables = CompositeDisposable()

    fun download(
        activity: FragmentActivity,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        origin: String? = null,
        blobData: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestDownload(activity, url, userAgent, contentDisposition, mimeType, contentLength, origin, blobData)
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
                    requestDownload(activity, url, userAgent, contentDisposition, mimeType, contentLength, origin, blobData)
                } else {
                    logger.log(TAG, "Download permission denied")
                }
            }
    }

    private fun requestDownload(
        activity: FragmentActivity,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        origin: String?,
        blobData: String?
    ) {
        if (!needsMetadataResolution(url, contentDisposition, mimeType, blobData)) {
            showDownloadDialog(activity, url, userAgent, contentDisposition, mimeType, contentLength, origin, blobData)
            return
        }

        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        downloadMetadataResolver.resolve(url, userAgent, cookie)
            .subscribeOn(networkScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(
                onSuccess = { resolved ->
                    logger.log(TAG, "Resolved download metadata: mime=${resolved.mimeType}, disposition=${resolved.contentDisposition}")
                    showDownloadDialog(
                        activity,
                        url,
                        userAgent,
                        resolved.contentDisposition ?: contentDisposition,
                        resolved.mimeType ?: mimeType,
                        resolved.contentLength.takeIf { it > 0 } ?: contentLength,
                        origin,
                        blobData
                    )
                },
                onError = {
                    logger.log(TAG, "Unable to resolve download metadata", it)
                    showDownloadDialog(activity, url, userAgent, contentDisposition, mimeType, contentLength, origin, blobData)
                }
            ).also(disposables::add)
    }

    private fun needsMetadataResolution(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        blobData: String?
    ): Boolean {
        if (blobData != null || !mimeType.isNullOrBlank()) return false
        val guessed = URLUtil.guessFileName(url, contentDisposition, null).lowercase()
        return guessed.endsWith(".bin") || !guessed.contains('.')
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
        origin: String?,
        blobData: String?
    ) {
        val siteDecision = origin?.let {
            sitePermissionStore.decision(it, SitePermissionKey.AUTOMATIC_DOWNLOADS)
        } ?: SitePermissionDecision.DEFAULT
        if (siteDecision == SitePermissionDecision.DENY) {
            activity.snackbar(R.string.site_permission_download_blocked)
            return
        }
        val normalizedMimeType = mimeType?.substringBefore(';')?.trim()?.lowercase()
        val guessedFileName = URLUtil.guessFileName(url, contentDisposition, normalizedMimeType)
        val convertImages = userPreferences.saveImagesAsJpeg && (
            DownloadFilenameResolver.isRasterImage(normalizedMimeType) ||
                DownloadFilenameResolver.isRasterImageFileName(guessedFileName)
            )
        val fileName = DownloadFilenameResolver.resolve(url, contentDisposition, normalizedMimeType, convertImages)
        val downloadSize: String = if (contentLength > 0) {
            Formatter.formatFileSize(activity, contentLength)
        } else {
            activity.getString(R.string.unknown_size)
        }

        val directDownload = {
            val dispatched = if (userPreferences.customDownloadManagerEnabled &&
                blobData == null && !convertImages
            ) {
                CustomDownloadManager.dispatch(
                    activity,
                    userPreferences.customDownloadManagerPackage,
                    url,
                    normalizedMimeType,
                    fileName
                )
            } else false
            if (dispatched) {
                activity.snackbar(R.string.download_pending)
            } else {
                downloadWithoutScanning(
                    activity, url, userAgent, contentDisposition, normalizedMimeType, downloadSize,
                    blobData, fileName, convertImages
                )
            }
        }
        if (siteDecision == SitePermissionDecision.ALLOW) {
            directDownload()
            return
        }
        val scanEligible = shouldScan(normalizedMimeType, fileName)

        val builder = MaterialAlertDialogBuilder(activity)
        val message = activity.getString(R.string.dialog_download, downloadSize) +
            "\n\n" + activity.getString(R.string.download_donation_message)
        builder.setTitle(fileName)
            .setMessage(message)
            .setPositiveButton(R.string.action_download) { _, _ ->
                if (scanEligible) {
                    scanDownload(
                        activity, url, userAgent, contentDisposition, normalizedMimeType,
                        downloadSize, blobData, fileName, convertImages
                    )
                } else {
                    directDownload()
                }
            }
            .setNeutralButton(R.string.action_donate) { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, KO_FI_URL.toUri()))
            }
        if (scanEligible) {
            builder.setNegativeButton(R.string.download_skip_scanning) { _, _ -> directDownload() }
        } else {
            builder.setNegativeButton(R.string.action_cancel, null)
        }
        val dialog: Dialog = builder.show()
        setDialogSize(activity, dialog)
        logger.log(TAG, "Downloading: $fileName")
    }

    private fun shouldScan(mimeType: String?, fileName: String): Boolean {
        return VirusTotalFilePolicy.shouldScan(
            scanningEnabled = userPreferences.virusTotalScanningEnabled,
            scanImages = userPreferences.virusTotalScanImages,
            scanVideos = userPreferences.virusTotalScanVideos,
            mimeType = mimeType,
            fileName = fileName
        )
    }

    private fun downloadWithoutScanning(
        activity: FragmentActivity,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        downloadSize: String,
        blobData: String?,
        fileName: String,
        convertImages: Boolean
    ) {
        if (blobData != null) {
            downloadHandler.downloadBlob(
                activity, userPreferences, url, contentDisposition, mimeType, blobData
            ).subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribeBy(
                    onSuccess = { storedUrl ->
                        saveDownload(storedUrl, fileName, downloadSize)
                        activity.snackbar(R.string.download_pending)
                    },
                    onError = {
                        logger.log(TAG, "error saving blob download", it)
                        activity.snackbar(R.string.cannot_download)
                    }
                ).also(disposables::add)
        } else if (convertImages) {
            downloadHandler.downloadImageAsJpeg(activity, userPreferences, url, userAgent, fileName)
                .subscribeOn(networkScheduler).observeOn(mainScheduler)
                .subscribeBy(
                    onSuccess = { storedUrl -> saveDownload(storedUrl, fileName, downloadSize); activity.snackbar(R.string.download_pending) },
                    onError = { logger.log(TAG, "error converting image to JPEG", it); activity.snackbar(R.string.cannot_download) }
                ).also(disposables::add)
        } else {
            downloadHandler.onDownloadStart(
                activity, userPreferences, url, userAgent, contentDisposition, mimeType, downloadSize
            )
            saveDownload(url, fileName, downloadSize)
        }
    }

    private fun scanDownload(
        activity: FragmentActivity,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        downloadSize: String,
        blobData: String?,
        fileName: String,
        convertImages: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionX.init(activity)
                .permissions(Manifest.permission.POST_NOTIFICATIONS)
                .request { _, _, _ ->
                    beginMalwareScan(
                        activity, url, userAgent, mimeType, downloadSize, blobData, fileName, convertImages
                    )
                }
            return
        }
        beginMalwareScan(activity, url, userAgent, mimeType, downloadSize, blobData, fileName, convertImages)
    }

    private fun beginMalwareScan(
        activity: FragmentActivity,
        url: String,
        userAgent: String?,
        mimeType: String?,
        downloadSize: String,
        blobData: String?,
        fileName: String,
        convertImages: Boolean
    ) {
        val progress = LinearProgressIndicator(activity).apply {
            isIndeterminate = true
            val margin = activity.resources.getDimensionPixelSize(R.dimen.material_grid_margin)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(margin, margin, margin, margin) }
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(progress)
        }
        val scanningDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.virus_total_scanning)
            .setMessage(activity.getString(R.string.virus_total_scanning_file, fileName))
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setCancelable(false)
            .create()
        scanningDialog.show()
        setDialogSize(activity, scanningDialog)
        progress.show()
        virusTotalNotification.showScanning(fileName)

        val serialDisposable = SerialDisposable()
        val cancellation = VirusTotalCancellationSignal()
        scanningDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            cancellation.cancel()
            serialDisposable.dispose()
            virusTotalNotification.hide()
            scanningDialog.dismiss()
        }
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        serialDisposable.set(
            virusTotalCoordinator.scanAndSave(
                activity,
                userPreferences,
                VirusTotalDownloadRequest(
                    url = url,
                    fileName = fileName,
                    userAgent = userAgent,
                    cookie = cookie,
                    mimeType = mimeType,
                    blobData = blobData,
                    convertToJpeg = convertImages
                ),
                cancellation
            )
                .subscribeOn(networkScheduler)
                .observeOn(mainScheduler)
                .subscribeBy(
                    onSuccess = { result ->
                        virusTotalNotification.hide()
                        if (result is VirusTotalDownloadResult.Blocked) {
                            virusTotalNotification.showBlocked(
                                fileName,
                                result.stats?.malicious
                            )
                        }
                        if (activity.isFinishing || activity.isDestroyed) return@subscribeBy
                        scanningDialog.dismiss()
                        when (result) {
                            is VirusTotalDownloadResult.Saved -> {
                                hapticFeedback.success(HapticFeedbackController.Category.DOWNLOADS)
                                saveDownload(result.storedUrl, fileName, downloadSize)
                                activity.snackbar(R.string.virus_total_clean_downloaded)
                            }
                            is VirusTotalDownloadResult.Blocked -> {
                                hapticFeedback.warning(HapticFeedbackController.Category.DOWNLOADS)
                                showBlockedDialog(activity, fileName, result)
                            }
                        }
                    },
                    onError = { error ->
                        virusTotalNotification.hide()
                        logger.log(TAG, "Malware scan failed", error)
                        if (activity.isFinishing || activity.isDestroyed) return@subscribeBy
                        scanningDialog.dismiss()
                        showScanError(activity, error)
                    }
                )
        )
    }

    private fun showBlockedDialog(
        activity: FragmentActivity,
        fileName: String,
        result: VirusTotalDownloadResult.Blocked
    ) {
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.virus_total_download_blocked)
            .setMessage(
                when (result.source) {
                    VirusTotalDownloadResult.DetectionSource.LOCAL_DATABASE ->
                        activity.getString(R.string.malware_local_blocked_message, fileName)
                    VirusTotalDownloadResult.DetectionSource.VIRUS_TOTAL ->
                        activity.getString(
                            R.string.virus_total_blocked_message,
                            fileName,
                            result.stats?.malicious ?: 0
                        )
                }
            )
            .setPositiveButton(R.string.action_ok, null)
        if (result.source == VirusTotalDownloadResult.DetectionSource.VIRUS_TOTAL) {
            builder.setNeutralButton(R.string.virus_total_view_report) { _, _ ->
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://www.virustotal.com/gui/file/${result.sha256}".toUri()
                    )
                )
            }
        }
        builder.show()
    }

    private fun showScanError(activity: FragmentActivity, error: Throwable) {
        val message = when ((error as? VirusTotalException)?.reason) {
            VirusTotalException.Reason.INVALID_API_KEY ->
                activity.getString(R.string.virus_total_error_api_key)
            VirusTotalException.Reason.RATE_LIMITED ->
                activity.getString(R.string.virus_total_error_rate_limit)
            VirusTotalException.Reason.FILE_TOO_LARGE ->
                activity.getString(R.string.virus_total_error_too_large)
            VirusTotalException.Reason.ANALYSIS_TIMEOUT ->
                activity.getString(R.string.virus_total_error_timeout)
            else -> activity.getString(R.string.virus_total_error_generic)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.virus_total_scan_failed)
            .setMessage(message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun saveDownload(storedUrl: String, fileName: String, downloadSize: String) {
        downloadsRepository.addDownloadIfNotExists(
            DownloadEntry(url = storedUrl, title = fileName, contentSize = downloadSize)
        ).subscribeOn(databaseScheduler).subscribeBy {
            if (!it) logger.log(TAG, "error saving download to database")
        }.also(disposables::add)
    }

    companion object {
        private const val TAG = "DownloadPermissionsHelper"
        private const val KO_FI_URL = "https://ko-fi.com/kennethchoinfosec"
    }
}
