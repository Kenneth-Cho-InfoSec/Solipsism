package com.krystelligence.solipsism.download

import io.reactivex.rxjava3.core.Single
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class DownloadMetadataResolver @Inject constructor() {
    fun resolve(url: String, userAgent: String?, cookie: String?): Single<ResolvedDownloadMetadata> =
        Single.fromCallable {
            val head = request(url, "HEAD", userAgent, cookie)
            if (head != null && head.hasUsefulMetadata()) head
            else request(url, "GET", userAgent, cookie, "bytes=0-0")
                ?: ResolvedDownloadMetadata(null, null, 0)
        }

    private fun request(
        url: String,
        method: String,
        userAgent: String?,
        cookie: String?,
        range: String? = null
    ): ResolvedDownloadMetadata? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            userAgent?.takeIf(String::isNotBlank)?.let { setRequestProperty("User-Agent", it) }
            cookie?.takeIf(String::isNotBlank)?.let { setRequestProperty("Cookie", it) }
            range?.let { setRequestProperty("Range", it) }
        }
        return try {
            if (connection.responseCode !in 200..399) null else ResolvedDownloadMetadata(
                mimeType = connection.contentType?.substringBefore(';')?.trim()?.lowercase(),
                contentDisposition = connection.getHeaderField("Content-Disposition"),
                contentLength = connection.contentLengthLong.coerceAtLeast(0)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun ResolvedDownloadMetadata.hasUsefulMetadata(): Boolean =
        !mimeType.isNullOrBlank() || !contentDisposition.isNullOrBlank() || contentLength > 0

    private companion object { const val TIMEOUT_MS = 8_000 }
}
