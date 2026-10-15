package com.krystelligence.solipsism.adblock.snapshot

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.edit
import com.krystelligence.solipsism.adblock.UBlockOriginFilterList
import com.krystelligence.solipsism.adblock.parser.HostsFileParser
import com.krystelligence.solipsism.browser.di.HostsClient
import com.krystelligence.solipsism.concurrency.CoroutineDispatchers
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Current on-disk filter snapshot state for settings display.
 */
data class SnapshotStatus(
    val hostsPresent: Boolean,
    val uboPresent: Boolean,
    val hostsUpdatedAt: Long,
    val uboUpdatedAt: Long,
    val hostsCount: Int,
    val uboRuleCount: Int
)

/**
 * Downloads and maintains versioned adblock filter snapshots (hosts + uBO
 * network filters) outside the APK so lists stay current without app updates.
 *
 * Mirrors the malware definitions mechanism (LocalMalwareDatabase):
 * HTTPS conditional downloads, atomic temp-file install, streaming size caps,
 * parse-and-count validation before replacing the live files, freshness
 * intervals, and stale-serving fallback — updates never break blocking.
 */
@Singleton
class AdblockSnapshotManager @Inject constructor(
    private val application: Application,
    private val userPreferences: UserPreferences,
    private val hostsFileParserProvider: Provider<HostsFileParser>,
    private val logger: Logger,
    @HostsClient private val okHttpClient: Single<OkHttpClient>,
    private val appCoroutineScope: CoroutineScope,
    private val coroutineDispatchers: CoroutineDispatchers
) {

    private val refreshMutex = Mutex()

    fun snapshotHostsFile(): File? =
        File(snapshotDirectory(), HOSTS_FILE_NAME).takeIf(File::isFile)

    fun snapshotUboFile(): File? =
        File(snapshotDirectory(), UBO_FILE_NAME).takeIf(File::isFile)

    fun status(): SnapshotStatus {
        val hosts = snapshotHostsFile()
        val ubo = snapshotUboFile()
        return SnapshotStatus(
            hostsPresent = hosts != null,
            uboPresent = ubo != null,
            hostsUpdatedAt = preferences().getLong(KEY_HOSTS_UPDATED_AT, 0),
            uboUpdatedAt = preferences().getLong(KEY_UBO_UPDATED_AT, 0),
            hostsCount = preferences().getInt(KEY_HOSTS_COUNT, 0),
            uboRuleCount = preferences().getInt(KEY_UBO_COUNT, 0)
        )
    }

    /**
     * Refresh snapshots that are missing or older than the configured interval.
     * Returns true when live files changed and engines should repopulate.
     * Never throws: failures keep serving the previous snapshot or assets.
     *
     * When no snapshot is installed yet this returns false immediately so the
     * first populate serves bundled assets without waiting on the network; the
     * initial download is kicked off from [BloomFilterAdBlocker] init instead.
     */
    suspend fun refreshIfStale(): Boolean {
        if (!userPreferences.filterListsAutoUpdate) return false
        if (snapshotHostsFile() == null || snapshotUboFile() == null) return false
        if (isFresh()) return false
        if (userPreferences.filterListsWifiOnly && isMetered()) return false
        return refresh(force = false)
    }

    /**
     * Download and install both snapshots. With [force] the freshness and
     * network gates are bypassed (manual refresh). Returns true on change.
     */
    suspend fun refresh(force: Boolean): Boolean = refreshMutex.withLock {
        refreshLocked(force)
    }

    private suspend fun refreshLocked(force: Boolean): Boolean {
        if (!force) {
            if (!userPreferences.filterListsAutoUpdate) return false
            if (userPreferences.filterListsWifiOnly && isMetered()) return false
        }
        var changed = false
        try {
            changed = downloadFile(
                url = HOSTS_URL,
                fileName = HOSTS_FILE_NAME,
                etagKey = KEY_HOSTS_ETAG,
                updatedAtKey = KEY_HOSTS_UPDATED_AT,
                countKey = KEY_HOSTS_COUNT,
                force = force,
                validator = ::validateHosts
            ) || changed
            changed = downloadFile(
                url = UBO_URL,
                fileName = UBO_FILE_NAME,
                etagKey = KEY_UBO_ETAG,
                updatedAtKey = KEY_UBO_UPDATED_AT,
                countKey = KEY_UBO_COUNT,
                force = force,
                validator = ::validateUbo
            ) || changed
        } catch (exception: Exception) {
            logger.log(TAG, "Filter snapshot refresh failed", exception)
        }
        return changed
    }

    fun refreshAsync(force: Boolean, onDone: (Boolean) -> Unit) {
        appCoroutineScope.launch(coroutineDispatchers.io) {
            val changed = refresh(force)
            withContext(coroutineDispatchers.main) { onDone(changed) }
        }
    }

    private fun isFresh(): Boolean {
        val intervalMs = userPreferences.filterListsUpdateIntervalDays.coerceIn(1, 90) *
            24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val hostsAt = preferences().getLong(KEY_HOSTS_UPDATED_AT, 0)
        val uboAt = preferences().getLong(KEY_UBO_UPDATED_AT, 0)
        return hostsAt > 0 && uboAt > 0 && now - hostsAt < intervalMs && now - uboAt < intervalMs
    }

    private fun isMetered(): Boolean {
        val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        return try {
            connectivity.isActiveNetworkMetered
        } catch (exception: Exception) {
            false
        }
    }

    /**
     * Download one snapshot file with ETag revalidation, validate the content,
     * and atomically replace the live file. Returns true when it changed.
     */
    private fun downloadFile(
        url: String,
        fileName: String,
        etagKey: String,
        updatedAtKey: String,
        countKey: String,
        force: Boolean,
        validator: (File) -> Int
    ): Boolean {
        if (!force && isFresh()) return false
        val directory = snapshotDirectory().apply { mkdirs() }
        val current = File(directory, fileName)
        val client = okHttpClient.blockingGet()
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", USER_AGENT)
            .apply {
                preferences().getString(etagKey, null)?.takeIf(String::isNotBlank)?.let {
                    header("If-None-Match", it)
                }
            }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 304 && current.isFile) {
                touchTimestamp(updatedAtKey)
                return false
            }
            if (!response.isSuccessful) {
                logger.log(TAG, "Snapshot server returned HTTP ${response.code} for $url")
                return false
            }
            val body = response.body ?: return false
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_SNAPSHOT_BYTES) {
                logger.log(TAG, "Snapshot exceeds size limit: $url")
                return false
            }
            val temporary = File(directory, "$fileName.tmp")
            temporary.delete()
            try {
                FileOutputStream(temporary).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_SNAPSHOT_BYTES) {
                                logger.log(TAG, "Snapshot exceeds size limit while streaming: $url")
                                return false
                            }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                val parsedCount = try {
                    validator(temporary)
                } catch (exception: Exception) {
                    logger.log(TAG, "Snapshot validation failed for $url", exception)
                    return false
                }
                if (current.exists() && !current.delete()) {
                    logger.log(TAG, "Unable to replace snapshot file: $fileName")
                    return false
                }
                if (!temporary.renameTo(current)) {
                    logger.log(TAG, "Unable to install snapshot file: $fileName")
                    return false
                }
                preferences().edit {
                    putLong(updatedAtKey, System.currentTimeMillis())
                    putInt(countKey, parsedCount)
                    putString(etagKey, response.header("ETag") ?: "")
                }
                logger.log(TAG, "Installed $fileName ($parsedCount entries)")
                return true
            } finally {
                temporary.delete()
            }
        }
    }

    private fun validateHosts(file: File): Int {
        val domains = file.reader().use { reader ->
            hostsFileParserProvider.get().parseInput(reader)
        }
        if (domains.size < MIN_HOSTS_DOMAINS) {
            throw IllegalStateException("Too few hosts domains: ${domains.size}")
        }
        return domains.size
    }

    private fun validateUbo(file: File): Int {
        val filterList = file.bufferedReader().useLines { lines ->
            UBlockOriginFilterList.parse(lines)
        }
        if (filterList.ruleCount < MIN_UBO_RULES) {
            throw IllegalStateException("Too few uBO network rules: ${filterList.ruleCount}")
        }
        return filterList.ruleCount
    }

    private fun touchTimestamp(updatedAtKey: String) {
        preferences().edit { putLong(updatedAtKey, System.currentTimeMillis()) }
    }

    private fun snapshotDirectory() = File(application.filesDir, SNAPSHOT_DIRECTORY)

    private fun preferences() =
        application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)

    companion object {
        private const val TAG = "AdblockSnapshotManager"

        const val HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        const val UBO_URL =
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt"

        private const val MAX_SNAPSHOT_BYTES = 8L * 1024L * 1024L
        private const val MIN_HOSTS_DOMAINS = 40000
        private const val MIN_UBO_RULES = 1000
        private const val USER_AGENT = "SolipsismBrowser/filter-snapshots"

        private const val SNAPSHOT_DIRECTORY = "adblock/snapshots"
        private const val HOSTS_FILE_NAME = "hosts.txt"
        private const val UBO_FILE_NAME = "ubo-network.txt"
        private const val PREFERENCES_NAME = "adblock_snapshots"
        private const val KEY_HOSTS_ETAG = "etag_hosts"
        private const val KEY_UBO_ETAG = "etag_ubo"
        private const val KEY_HOSTS_UPDATED_AT = "updated_at_hosts"
        private const val KEY_UBO_UPDATED_AT = "updated_at_ubo"
        private const val KEY_HOSTS_COUNT = "count_hosts"
        private const val KEY_UBO_COUNT = "count_ubo"
    }
}
