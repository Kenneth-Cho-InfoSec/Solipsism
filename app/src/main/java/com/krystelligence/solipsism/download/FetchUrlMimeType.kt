/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.download

import android.app.DownloadManager
import android.util.Log
import android.webkit.MimeTypeMap
import com.krystelligence.solipsism.utils.Utils
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Probes an unknown download MIME type before handing the request to Android. */
class FetchUrlMimeType(
    private val downloadManager: DownloadManager,
    private val request: DownloadManager.Request,
    private val uri: String,
    private val cookies: String?,
    private val userAgent: String?
) {

    fun create(): Single<Result> = Single.create { emitter ->
        var mimeType: String? = null
        var connection: HttpURLConnection? = null
        try {
            connection = URL(uri).openConnection() as HttpURLConnection
            if (!cookies.isNullOrEmpty()) connection?.addRequestProperty("Cookie", cookies)
            if (!userAgent.isNullOrEmpty()) connection?.setRequestProperty("User-Agent", userAgent)
            connection?.connect()
            if (connection?.responseCode == HttpURLConnection.HTTP_OK) {
                mimeType = connection?.getHeaderField("Content-Type")?.substringBefore(';')
            }
        } catch (exception: IllegalArgumentException) {
            connection?.disconnect()
        } catch (exception: IOException) {
            connection?.disconnect()
        } finally {
            connection?.disconnect()
        }

        if (!mimeType.isNullOrEmpty()) {
            if (mimeType.equals("text/plain", ignoreCase = true) ||
                mimeType.equals("application/octet-stream", ignoreCase = true)
            ) {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(Utils.guessFileExtension(uri))
                    ?.let(request::setMimeType)
            }
        }

        try {
            downloadManager.enqueue(request)
            emitter.onSuccess(Result.SUCCESS)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Unable to enqueue request", exception)
            emitter.onSuccess(Result.FAILURE_ENQUEUE)
        } catch (exception: SecurityException) {
            emitter.onSuccess(Result.FAILURE_LOCATION)
        }
    }

    enum class Result {
        FAILURE_ENQUEUE,
        FAILURE_LOCATION,
        SUCCESS
    }

    private companion object {
        const val TAG = "FetchUrlMimeType"
    }
}
