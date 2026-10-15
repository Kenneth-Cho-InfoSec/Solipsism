package com.krystelligence.solipsism.adblock.snapshot

import com.krystelligence.solipsism.adblock.parser.HostsFileParser
import com.krystelligence.solipsism.adblock.source.HostsDataSource
import com.krystelligence.solipsism.adblock.source.HostsResult
import com.krystelligence.solipsism.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

/**
 * A [HostsDataSource] reading the downloaded filter snapshot. Falls back to
 * failure (leaving the previous bloom filter in place) when no snapshot is
 * installed; the provider then serves bundled assets instead.
 */
class SnapshotHostsDataSource @Inject constructor(
    private val snapshotManager: AdblockSnapshotManager,
    private val hostsFileParserProvider: Provider<HostsFileParser>,
    private val logger: Logger
) : HostsDataSource {

    override suspend fun loadHosts(): HostsResult = withContext(Dispatchers.IO) {
        val file: File = snapshotManager.snapshotHostsFile()
            ?: return@withContext HostsResult.Failure(
                IllegalStateException("No hosts snapshot installed")
            )
        return@withContext try {
            val domains = file.reader().use { reader ->
                hostsFileParserProvider.get().parseInput(reader)
            }
            logger.log(TAG, "Loaded ${domains.size} domains from snapshot")
            HostsResult.Success(domains)
        } catch (exception: Exception) {
            logger.log(TAG, "Unable to load hosts snapshot", exception)
            HostsResult.Failure(exception)
        }
    }

    override suspend fun identifier(): String {
        val updatedAt = snapshotManager.status().hostsUpdatedAt
        return "$IDENTIFIER_PREFIX$updatedAt"
    }

    companion object {
        private const val TAG = "SnapshotHostsDataSource"
        private const val IDENTIFIER_PREFIX = "snapshot:"
    }
}
