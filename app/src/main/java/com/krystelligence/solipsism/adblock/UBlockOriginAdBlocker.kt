package com.krystelligence.solipsism.adblock

import android.app.Application
import com.krystelligence.solipsism.adblock.snapshot.AdblockSnapshotManager
import com.krystelligence.solipsism.log.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in network filter engine for uBlock Origin/EasyList-style rules.
 *
 * This intentionally implements the WebView-safe network-blocking subset. Full uBlock Origin is a
 * browser extension runtime, while this app blocks WebView requests through shouldInterceptRequest.
 *
 * Network filters load from the downloaded snapshot when present, falling back
 * to the bundled asset; the snapshot file's modification time is rechecked on
 * every lookup so refreshes apply without an app restart.
 */
@Singleton
class UBlockOriginAdBlocker @Inject constructor(
    private val application: Application,
    private val snapshotManager: AdblockSnapshotManager,
    private val logger: Logger
) : AdBlocker {

    @Volatile private var filterList: UBlockOriginFilterList? = null
    @Volatile private var loadedSnapshotModified: Long = -1L
    private val loadLock = Any()

    override fun isAd(url: String): Boolean = currentFilterList().shouldBlock(url)

    override fun isAd(url: String, pageUrl: String): Boolean =
        currentFilterList().shouldBlock(url) || UBlockOriginYoutubeRules.shouldBlock(pageUrl, url)

    private fun currentFilterList(): UBlockOriginFilterList {
        val snapshot = snapshotManager.snapshotUboFile()
        val snapshotModified = snapshot?.lastModified() ?: -1L
        filterList?.takeIf { snapshotModified == loadedSnapshotModified }?.let { return it }
        synchronized(loadLock) {
            filterList?.takeIf { snapshotModified == loadedSnapshotModified }?.let { return it }
            val loaded = snapshot?.takeIf(File::isFile)?.let(::loadSnapshotFilterList)
                ?: loadAssetFilterList()
            filterList = loaded
            loadedSnapshotModified = snapshotModified
            return loaded
        }
    }

    private fun loadAssetFilterList(): UBlockOriginFilterList {
        return try {
            application.assets.open(FILTER_ASSET).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    UBlockOriginFilterList.parse(lines)
                }
            }
        } catch (exception: Exception) {
            logger.log(TAG, "Unable to load built-in uBlock Origin filters", exception)
            UBlockOriginFilterList(emptyList(), emptyList())
        }
    }

    private fun loadSnapshotFilterList(file: File): UBlockOriginFilterList {
        return try {
            file.bufferedReader().useLines { lines ->
                UBlockOriginFilterList.parse(lines)
            }
        } catch (exception: Exception) {
            logger.log(TAG, "Unable to load snapshot uBlock Origin filters", exception)
            loadAssetFilterList()
        }
    }

    companion object {
        private const val TAG = "UBlockOriginAdBlocker"
        private const val FILTER_ASSET = "ublock_origin_filters.txt"
    }
}

object UBlockOriginYoutubeRules {

    fun shouldBlock(pageUrl: String, requestUrl: String): Boolean {
        if (!pageUrl.isYouTubePage()) {
            return false
        }

        val request = requestUrl.lowercase()
        val host = requestUrl.host().orEmpty()

        if (host == "doubleclick.net" || host.endsWith(".doubleclick.net")) {
            return true
        }

        if (host == "googleadservices.com" || host.endsWith(".googleadservices.com")) {
            return true
        }

        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtubei.googleapis.com") {
            return request.contains("/pagead/")
                || request.contains("/ptracking")
                || request.contains("/api/stats/ads")
                || request.contains("/api/stats/qoe?adformat=")
                || request.contains("/youtubei/v1/player/ad_break")
                || request.contains("adformat=")
                || request.contains("ad_type=")
                || request.contains("adunit")
                || request.contains("afv_ad_tag")
        }

        if (host == "googlevideo.com" || host.endsWith(".googlevideo.com")) {
            return request.contains("/videoplayback")
                && (request.contains("&oad=")
                || request.contains("?oad=")
                || request.contains("ctier=a")
                || request.contains("adformat=")
                || request.contains("ad_type=")
                || request.contains("adunit")
                || request.contains("afv_ad_tag"))
        }

        return false
    }

    private fun String.isYouTubePage(): Boolean {
        val host = host().orEmpty()
        return host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    }
}

class UBlockOriginFilterList(
    private val blockRules: List<UBlockOriginRule>,
    private val exceptionRules: List<UBlockOriginRule>
) {

    /** Total network rules held (block + exception), for snapshot validation. */
    val ruleCount: Int get() = blockRules.size + exceptionRules.size

    fun shouldBlock(url: String): Boolean {
        val host = url.host() ?: return false
        if (exceptionRules.any { it.matches(url, host) }) {
            return false
        }
        return blockRules.any { it.matches(url, host) }
    }

    companion object {
        fun parse(lines: Sequence<String>): UBlockOriginFilterList {
            val blockRules = mutableListOf<UBlockOriginRule>()
            val exceptionRules = mutableListOf<UBlockOriginRule>()

            lines.map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { it.startsWith("!") || it.startsWith("[") }
                .filterNot { it.contains("##") || it.contains("#@#") || it.contains("#?#") }
                .forEach { line ->
                    val isException = line.startsWith("@@")
                    val rule = UBlockOriginRule.from(line.removePrefix("@@")) ?: return@forEach
                    if (isException) {
                        exceptionRules += rule
                    } else {
                        blockRules += rule
                    }
                }

            return UBlockOriginFilterList(blockRules, exceptionRules)
        }
    }
}

private fun String.host(): String? = try {
    URI(this).host?.lowercase()
} catch (exception: URISyntaxException) {
    null
}

sealed class UBlockOriginRule {

    abstract fun matches(url: String, host: String): Boolean

    data class DomainRule(private val domain: String) : UBlockOriginRule() {
        override fun matches(url: String, host: String): Boolean =
            host == domain || host.endsWith(".$domain")
    }

    data class UrlContainsRule(private val needle: String) : UBlockOriginRule() {
        override fun matches(url: String, host: String): Boolean =
            url.contains(needle, ignoreCase = true)
    }

    data class PrefixRule(private val prefix: String) : UBlockOriginRule() {
        override fun matches(url: String, host: String): Boolean =
            url.startsWith(prefix, ignoreCase = true)
    }

    data class WildcardRule(private val regex: Regex) : UBlockOriginRule() {
        override fun matches(url: String, host: String): Boolean = regex.containsMatchIn(url)
    }

    companion object {
        fun from(input: String): UBlockOriginRule? {
            val rule = input.substringBefore('$').trim()
            if (rule.isBlank()) {
                return null
            }
            // A rule reduced to bare wildcards (e.g. `*` left over from
            // `@@*$ghide,domain=...`) would match every URL. This subset engine
            // cannot scope rules by their options, so dropping the rule is the
            // only safe choice; keeping it would neuter the whole list.
            if (rule.trimStart('|').all { it == '*' || it == '^' }) {
                return null
            }

            if (rule.startsWith("||")) {
                val domain = rule.removePrefix("||")
                    .takeWhile { it != '^' && it != '/' && it != '*' }
                    .lowercase()
                return DomainRule(domain).takeIf { domain.isNotBlank() }
            }

            if (rule.startsWith("|http://") || rule.startsWith("|https://")) {
                return PrefixRule(rule.removePrefix("|"))
            }

            if (rule.contains('*') || rule.contains('^')) {
                val regex = rule
                    .removePrefix("|")
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("^", "[^A-Za-z0-9_%.:-]")
                return WildcardRule(Regex(regex, RegexOption.IGNORE_CASE))
            }

            return UrlContainsRule(rule)
        }
    }
}
