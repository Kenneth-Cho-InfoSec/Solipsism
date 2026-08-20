/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.download

import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.appcompat.app.AlertDialog
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.di.NetworkScheduler
import com.krystelligence.solipsism.constant.FILE
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.FileUtils
import com.krystelligence.solipsism.utils.Utils
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Handles WebView download requests and app-managed image/blob downloads. */
@Singleton
class DownloadHandler @Inject constructor(
    private val downloadManager: DownloadManager,
    @NetworkScheduler private val networkScheduler: Scheduler,
    @MainScheduler private val mainScheduler: Scheduler,
    private val logger: Logger
) {

    fun onDownloadStart(
        context: Activity,
        manager: UserPreferences,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentSize: String
    ) {
        logger.log(TAG, "DOWNLOAD: Trying to download from URL: $url")
        onDownloadStartNoStream(context, manager, url, userAgent, contentDisposition, mimeType, contentSize)
    }

    /** Saves bytes extracted from a WebView-owned blob URL. */
    fun downloadBlob(
        context: Activity,
        preferences: UserPreferences,
        sourceUrl: String,
        contentDisposition: String?,
        mimeType: String?,
        base64Data: String
    ): Single<String> = Single.fromCallable {
        if (base64Data.length > MAX_BLOB_BASE64_CHARS) {
            throw IOException("Blob download exceeds the safety limit")
        }
        var filename = FileUtils.sanitizeFileName(
            URLUtil.guessFileName(sourceUrl, contentDisposition, mimeType)
        )
        var contentType = if (TextUtils.isEmpty(mimeType)) {
            "application/octet-stream"
        } else {
            mimeType
        }
        var bytes = Base64.decode(base64Data, Base64.DEFAULT)
        if (bytes.size > MAX_BLOB_BYTES) {
            throw IOException("Blob download exceeds the safety limit")
        }
        if (preferences.saveImagesAsJpeg) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                filename = filename.replace(Regex("(?i)\\.[^.]+$"), ".jpg")
                val converted = ByteArrayOutputStream()
                try {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, converted)
                    bytes = converted.toByteArray()
                    contentType = "image/jpeg"
                } finally {
                    bitmap.recycle()
                }
            }
        }

        val location = FileUtils.addNecessarySlashes(preferences.downloadDirectory)
        val defaultPath = FileUtils.addNecessarySlashes(FileUtils.DEFAULT_DOWNLOAD_PATH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && location.equals(defaultPath, ignoreCase = true)) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, contentType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create download entry")
            try {
                resolver.openOutputStream(uri).use { stream ->
                    if (stream == null) throw IOException("Unable to open download entry")
                    stream.write(bytes)
                }
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
                uri.toString()
            } catch (exception: Exception) {
                resolver.delete(uri, null, null)
                throw exception
            }
        } else {
            val directory = File(location)
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Unable to create download directory")
            }
            val outputFile = File(directory, filename)
            FileOutputStream(outputFile).use { it.write(bytes) }
            outputFile.toURI().toString()
        }
    }

    /** Downloads and converts a raster image without routing it through DownloadManager. */
    fun downloadImageAsJpeg(
        context: Activity,
        preferences: UserPreferences,
        url: String,
        userAgent: String?,
        filename: String
    ): Single<String> = Single.fromCallable {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        val cookie = CookieManager.getInstance().getCookie(url)
        if (!TextUtils.isEmpty(cookie)) connection.setRequestProperty(COOKIE_REQUEST_HEADER, cookie)
        if (!TextUtils.isEmpty(userAgent)) connection.setRequestProperty(USER_AGENT_REQUEST_HEADER, userAgent)
        try {
            connection.inputStream.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                    ?: throw IOException("Unable to decode image")
                val temp = File.createTempFile("jpeg_", ".jpg", context.cacheDir)
                try {
                    FileOutputStream(temp).use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                            throw IOException("Unable to encode JPEG")
                        }
                    }
                    val jpgName = filename.replace(Regex("(?i)\\.[^.]+$"), ".jpg")
                    try {
                        publishScannedFile(context, preferences, temp, jpgName, "image/jpeg")
                    } finally {
                        temp.delete()
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Publishes bytes that have already passed a security scan. */
    @Throws(IOException::class)
    fun publishScannedFile(
        context: Activity,
        preferences: UserPreferences,
        source: File,
        filename: String,
        mimeType: String?
    ): String {
        val contentType = if (TextUtils.isEmpty(mimeType)) "application/octet-stream" else mimeType
        val location = FileUtils.addNecessarySlashes(preferences.downloadDirectory)
        val defaultPath = FileUtils.addNecessarySlashes(FileUtils.DEFAULT_DOWNLOAD_PATH)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && location.equals(defaultPath, ignoreCase = true)) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, contentType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create download entry")
            try {
                resolver.openOutputStream(uri).use { stream ->
                    FileInputStream(source).use { input ->
                        if (stream == null) throw IOException("Unable to open download entry")
                        copy(input, stream)
                    }
                }
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
                return uri.toString()
            } catch (exception: Exception) {
                resolver.delete(uri, null, null)
                throw if (exception is IOException) exception else IOException(exception)
            }
        }

        val directory = File(location)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create download directory")
        }
        val outputFile = availableFile(directory, filename)
        FileInputStream(source).use { input ->
            FileOutputStream(outputFile).use { output -> copy(input, output) }
        }
        return outputFile.toURI().toString()
    }

    private fun onDownloadStartNoStream(
        context: Activity,
        preferences: UserPreferences,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentSize: String
    ) {
        val filename = DownloadFilenameResolver.resolve(url, contentDisposition, mimetype, false)
        val status = Environment.getExternalStorageState()
        if (status != Environment.MEDIA_MOUNTED) {
            val title = if (status == Environment.MEDIA_SHARED) {
                R.string.download_sdcard_busy_dlg_title
            } else {
                R.string.download_no_sdcard_dlg_title
            }
            val message = if (status == Environment.MEDIA_SHARED) {
                R.string.download_sdcard_busy_dlg_msg
            } else {
                R.string.download_no_sdcard_dlg_msg
            }
            val dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(message)
                .setPositiveButton(R.string.action_ok, null)
                .show()
            BrowserDialog.setDialogSize(context, dialog)
            return
        }

        val webAddress = try {
            WebAddress(url).also { it.path = encodePath(it.path) }
        } catch (exception: Exception) {
            context.snackbar(R.string.problem_download)
            return
        }
        val addressString = webAddress.toString()
        val request = try {
            DownloadManager.Request(Uri.parse(addressString))
        } catch (exception: IllegalArgumentException) {
            context.snackbar(R.string.cannot_download)
            return
        }

        val location = preferences.downloadDirectory
        val slashedDefaultPath = FileUtils.addNecessarySlashes(FileUtils.DEFAULT_DOWNLOAD_PATH)
        val slashedLocation = FileUtils.addNecessarySlashes(location)
        val isDefaultPath = slashedLocation.equals(slashedDefaultPath, ignoreCase = true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !isDefaultPath) {
            if (!isWriteAccessAvailable(Uri.parse(slashedLocation))) {
                context.snackbar(R.string.problem_location_download)
                return
            }
        }

        var newMimeType = mimetype
        if (!TextUtils.isEmpty(newMimeType)) {
            val semicolonIndex = newMimeType!!.indexOf(';')
            if (semicolonIndex != -1) newMimeType = newMimeType.substring(0, semicolonIndex).trim()
        }
        if (TextUtils.isEmpty(newMimeType)) {
            newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Utils.guessFileExtension(filename))
        }
        if (!TextUtils.isEmpty(newMimeType)) request.setMimeType(newMimeType)
        request.setTitle(filename)
        if (isDefaultPath) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        } else {
            request.setDestinationUri(Uri.parse(FILE + slashedLocation + filename))
        }
        request.setVisibleInDownloadsUi(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) request.allowScanningByMediaScanner()
        request.setDescription(webAddress.host)
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!TextUtils.isEmpty(cookies)) request.addRequestHeader(COOKIE_REQUEST_HEADER, cookies)
        if (!TextUtils.isEmpty(userAgent)) request.addRequestHeader(USER_AGENT_REQUEST_HEADER, userAgent)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        if (mimetype == null) {
            if (addressString.isEmpty()) return
            FetchUrlMimeType(downloadManager, request, addressString, cookies, userAgent)
                .create()
                .subscribeOn(networkScheduler)
                .observeOn(mainScheduler)
                .subscribe { result ->
                    when (result) {
                        FetchUrlMimeType.Result.FAILURE_ENQUEUE -> context.snackbar(R.string.cannot_download)
                        FetchUrlMimeType.Result.FAILURE_LOCATION -> context.snackbar(R.string.problem_location_download)
                        FetchUrlMimeType.Result.SUCCESS -> context.snackbar(R.string.download_pending)
                    }
                }
        } else {
            try {
                downloadManager.enqueue(request)
                context.snackbar(context.getString(R.string.download_pending) + ' ' + filename)
            } catch (exception: Exception) {
                logger.log(TAG, "Unable to enqueue request", exception)
                context.snackbar(R.string.cannot_download)
            }
        }
    }

    private fun encodePath(path: String): String {
        if (!path.any { it == '[' || it == ']' || it == '|' }) return path
        return buildString {
            path.forEach { character ->
                if (character == '[' || character == ']' || character == '|') {
                    append('%')
                    append(character.code.toString(16))
                } else {
                    append(character)
                }
            }
        }
    }

    private fun isWriteAccessAvailable(fileUri: Uri): Boolean {
        val path = fileUri.path ?: return false
        val file = File(path)
        if (!file.isDirectory && !file.mkdirs()) return false
        return try {
            val testFile = File(file, "test_write_access_${System.currentTimeMillis()}")
            if (testFile.createNewFile()) {
                testFile.delete()
                true
            } else {
                false
            }
        } catch (exception: IOException) {
            false
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            output.write(buffer, 0, count)
        }
    }

    private fun availableFile(directory: File, filename: String): File {
        val requested = File(directory, filename)
        if (!requested.exists()) return requested
        val dot = filename.lastIndexOf('.')
        val stem = if (dot > 0) filename.substring(0, dot) else filename
        val extension = if (dot > 0) filename.substring(dot) else ""
        var suffix = 1
        var candidate: File
        do {
            candidate = File(directory, "$stem ($suffix)$extension")
            suffix++
        } while (candidate.exists())
        return candidate
    }

    private companion object {
        const val TAG = "DownloadHandler"
        const val MAX_BLOB_BYTES = 16 * 1024 * 1024
        const val MAX_BLOB_BASE64_CHARS = ((MAX_BLOB_BYTES + 2) / 3) * 4
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val COOKIE_REQUEST_HEADER = "Cookie"
        const val USER_AGENT_REQUEST_HEADER = "User-Agent"
    }
}
