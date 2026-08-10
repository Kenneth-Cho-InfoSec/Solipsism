package com.krystelligence.solipsism.release

import com.krystelligence.solipsism.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ReleaseRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetchExact(version: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val tag = ReleaseVersion.tag(version)
        runCatching {
            fetchManifest()?.firstOrNull { it.tagName == tag }
                ?: fetchJson("https://api.github.com/repos/Kenneth-Cho-InfoSec/Solipsism/releases/tags/$tag")?.let(::parseRelease)
        }.getOrNull()
    }

    suspend fun fetchLatestStable(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            fetchManifest()?.filter { !it.prerelease }
                ?.maxWithOrNull { left, right ->
                    ReleaseVersion.compare(left.tagName, right.tagName)
                        .takeIf { it != 0 }
                        ?: (left.publishedAt ?: "").compareTo(right.publishedAt ?: "")
                }
                ?: fetchJson("https://api.github.com/repos/Kenneth-Cho-InfoSec/Solipsism/releases/latest")?.let(::parseRelease)
        }.getOrNull()
    }

    private fun fetchManifest(): List<ReleaseInfo>? {
        val root = fetchJson("${BuildConfig.RELEASE_SITE_URL}release-manifest.json") ?: return null
        return root.optJSONArray("releases")?.let { array ->
            (0 until array.length()).mapNotNull { index -> parseRelease(array.optJSONObject(index)) }
        }
    }

    private fun fetchJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Solipsism-Browser")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()?.let(::JSONObject)
        }
    }

    private fun parseRelease(root: JSONObject): ReleaseInfo? {
        val tag = root.optString("tag_name")
        val page = root.optString("html_url")
        if (tag.isBlank() || !isTrustedUrl(page)) return null
        val assets = root.optJSONArray("assets") ?: return null
        val parsedAssets = (0 until assets.length()).mapNotNull { index ->
            assets.optJSONObject(index)?.let { asset ->
                val url = asset.optString("browser_download_url")
                if (url.isBlank() || !isTrustedUrl(url)) null else ReleaseAsset(
                    name = asset.optString("name"),
                    size = asset.optLong("size", 0L),
                    browserDownloadUrl = url,
                )
            }
        }
        return ReleaseInfo(
            tagName = tag,
            name = root.optString("name").ifBlank { tag },
            body = root.optString("body"),
            htmlUrl = page,
            publishedAt = root.optString("published_at").ifBlank { null },
            prerelease = root.optBoolean("prerelease", false),
            assets = parsedAssets,
        )
    }

    private fun isTrustedUrl(value: String): Boolean = runCatching {
        java.net.URI(value).host in setOf("github.com", "objects.githubusercontent.com")
    }.getOrDefault(false)
}
