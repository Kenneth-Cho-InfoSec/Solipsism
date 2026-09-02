package com.krystelligence.solipsism.browser.engine

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.krystelligence.solipsism.constant.CHROMPATIBILITY_FALLBACK_USER_AGENT
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

internal data class BrowserMediaRequest(
    val pageUrl: String,
    val directSource: String?,
    val renewalRequest: String?,
    val cookies: String?,
    val title: String?,
) {
    companion object {
        fun fromBundle(bundle: android.os.Bundle): BrowserMediaRequest? {
            val pageUrl = bundle.getString(AntaresProtocol.KEY_MEDIA_PAGE_URL).orEmpty()
            val directSource = bundle.getString(AntaresProtocol.KEY_MEDIA_DIRECT_SOURCE)
                ?.takeIf(String::isNotBlank)
            val renewalRequest = bundle.getString(AntaresProtocol.KEY_MEDIA_RENEWAL_REQUEST)
                ?.takeIf(String::isNotBlank)
            if (pageUrl.isBlank() || directSource == null && renewalRequest == null) return null
            return BrowserMediaRequest(
                pageUrl = pageUrl,
                directSource = directSource,
                renewalRequest = renewalRequest,
                cookies = bundle.getString(AntaresProtocol.KEY_MEDIA_COOKIES)
                    ?.takeIf(String::isNotBlank),
                title = bundle.getString(AntaresProtocol.KEY_MEDIA_TITLE)
                    ?.takeIf(String::isNotBlank),
            )
        }
    }
}

internal data class ResolvedMediaSource(
    val url: String,
    val headers: Map<String, String>,
    val mimeType: String?,
)

internal object AntaresMediaSourceResolver {
    /**
     * Converts a source discovered by the page's own media pipeline into a Media3 request.
     *
     * The Antares bridge observes source changes and media-related network responses in the
     * document, so short-lived signed URLs stay owned by the page that created them. This class
     * deliberately makes no requests to individual video services.
     */
    fun resolve(
        pageUrl: String,
        directSource: String?,
        renewalRequest: String?,
        cookies: String?,
    ): ResolvedMediaSource {
        if (!renewalRequest.isNullOrBlank()) {
            return resolveRenewal(pageUrl, renewalRequest, cookies)
        }
        require(!directSource.isNullOrBlank()) { "The page did not provide a media source." }
        val resolvedUrl = resolveUrl(pageUrl, directSource)
        val headers = playbackHeaders(pageUrl, cookies)
        return ResolvedMediaSource(
            url = resolvedUrl,
            headers = headers,
            mimeType = resolveAdaptiveMimeType(resolvedUrl, headers),
        )
    }

    private fun resolveRenewal(
        pageUrl: String,
        encodedRequest: String,
        cookies: String?,
    ): ResolvedMediaSource {
        val request = runCatching { JSONObject(encodedRequest) }.getOrElse {
            throw IllegalArgumentException("The page provided an invalid media renewal request.", it)
        }
        val requestUrl = resolveUrl(pageUrl, request.getString("url"))
        val method = request.optString("method", "GET").uppercase()
        require(method in setOf("GET", "POST")) { "Unsupported media renewal method." }
        val contentType = request.optString("contentType")
        require(contentType.isBlank() || contentType.startsWith("application/x-www-form-urlencoded") ||
            contentType.startsWith("application/json")) {
            "Unsupported media renewal content type."
        }
        val body = request.optString("body")
        require(body.length <= MAX_RENEWAL_BODY_BYTES) { "Media renewal request is too large." }

        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Origin", originFor(pageUrl))
            connection.setRequestProperty("Referer", pageUrl)
            connection.setRequestProperty("User-Agent", CHROMPATIBILITY_FALLBACK_USER_AGENT)
            if (!cookies.isNullOrBlank()) connection.setRequestProperty("Cookie", cookies)
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    contentType.ifBlank { "application/x-www-form-urlencoded" },
                )
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
            require(connection.responseCode in 200..299) {
                "The media service returned HTTP ${connection.responseCode}."
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val source = findNetworkUrl(response)
                ?: throw IllegalArgumentException("The media service did not return a playable URL.")
            val renewedCookies = connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values
                .flatten()
                .map { it.substringBefore(';') }
                .filter(String::isNotBlank)
                .joinToString("; ")
                .takeIf(String::isNotBlank)
            val resolvedUrl = resolveUrl(requestUrl, source)
            val headers = playbackHeaders(
                    pageUrl,
                    listOfNotNull(cookies, renewedCookies)
                        .joinToString("; ")
                        .takeIf(String::isNotBlank),
                )
            ResolvedMediaSource(
                url = resolvedUrl,
                headers = headers,
                mimeType = resolveAdaptiveMimeType(resolvedUrl, headers),
            )
        } finally {
            connection.disconnect()
        }
    }

    internal fun findNetworkUrl(response: String): String? {
        val parsed = runCatching { JSONObject(response) }.getOrNull()
        if (parsed != null) return findNetworkUrl(parsed)
        return NETWORK_URL.find(response)?.value
    }

    /**
     * Media3 chooses an adaptive media source before it opens the URI. Signed playlist URLs often
     * have no file extension, so use a small range request to classify only otherwise-ambiguous
     * sources. The response body is never retained.
     */
    private fun resolveAdaptiveMimeType(
        sourceUrl: String,
        headers: Map<String, String>,
    ): String? {
        inferAdaptiveMimeType(sourceUrl, null, null)?.let { return it }

        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Range", "bytes=0-${MEDIA_PROBE_BYTES - 1}")
            connection.setRequestProperty("Accept-Encoding", "identity")
            headers.forEach(connection::setRequestProperty)
            if (connection.responseCode !in 200..299) return null

            val prefix = connection.inputStream.use { input ->
                val buffer = ByteArray(MEDIA_PROBE_BYTES)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read <= 0) break
                    count += read
                }
                String(buffer, 0, count, StandardCharsets.UTF_8)
            }
            inferAdaptiveMimeType(
                connection.url.toString(),
                connection.contentType,
                prefix,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    internal fun inferAdaptiveMimeType(
        sourceUrl: String,
        contentType: String?,
        contentPrefix: String?,
    ): String? {
        val path = Uri.parse(sourceUrl).path.orEmpty().lowercase()
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        val prefix = contentPrefix
            ?.trimStart { it.isWhitespace() || it == '\uFEFF' }
            .orEmpty()
        return when {
            path.endsWith(".m3u8") || type in HLS_CONTENT_TYPES ||
                prefix.startsWith("#EXTM3U", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            else -> null
        }
    }

    private fun findNetworkUrl(value: Any?): String? = when (value) {
        is JSONObject -> value.keys().asSequence().mapNotNull { key ->
            findNetworkUrl(value.opt(key))
        }.firstOrNull()
        is org.json.JSONArray -> (0 until value.length()).asSequence().mapNotNull { index ->
            findNetworkUrl(value.opt(index))
        }.firstOrNull()
        is String -> value.takeIf { NETWORK_URL.matches(it) }
        else -> null
    }

    private fun playbackHeaders(pageUrl: String, cookies: String?): Map<String, String> = buildMap {
        put("Referer", pageUrl)
        put("Origin", originFor(pageUrl))
        put("User-Agent", CHROMPATIBILITY_FALLBACK_USER_AGENT)
        if (!cookies.isNullOrBlank()) put("Cookie", cookies)
    }

    private fun resolveUrl(baseUrl: String, source: String): String {
        val resolved = if (source.startsWith("//")) "https:$source" else {
            URI(baseUrl).resolve(source).toString()
        }
        require(Uri.parse(resolved).scheme?.lowercase() in setOf("http", "https")) {
            "The page supplied an unsupported media URL."
        }
        return resolved
    }

    private fun originFor(pageUrl: String): String = Uri.parse(pageUrl).let { "${it.scheme}://${it.host}" }

    private const val MAX_RENEWAL_BODY_BYTES = 32 * 1024
    private const val MEDIA_PROBE_BYTES = 16 * 1024
    private val HLS_CONTENT_TYPES = setOf(
        "application/mpegurl",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
    )
    private val NETWORK_URL = Regex("(?:https?:)?//[^\\s\\\"'<>]+", RegexOption.IGNORE_CASE)
}
