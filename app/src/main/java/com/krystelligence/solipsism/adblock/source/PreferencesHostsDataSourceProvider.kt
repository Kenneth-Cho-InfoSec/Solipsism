package com.krystelligence.solipsism.adblock.source

import com.krystelligence.solipsism.adblock.lists.FilterListKind
import com.krystelligence.solipsism.adblock.lists.FilterListRepository
import com.krystelligence.solipsism.adblock.snapshot.AdblockSnapshotManager
import com.krystelligence.solipsism.adblock.snapshot.SnapshotHostsDataSource
import com.krystelligence.solipsism.preference.UserPreferences
import dagger.Reusable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

/**
 * A [HostsDataSourceProvider] backed by [UserPreferences].
 *
 * The legacy selected source (bundled, local file, or single remote URL) is always
 * included; enabled user-added HOSTS lists from [FilterListRepository] are merged
 * on top through [CompositeHostsDataSource].
 */
@Reusable
class PreferencesHostsDataSourceProvider @Inject constructor(
    private val userPreferences: UserPreferences,
    private val assetsHostsDataSource: AssetsHostsDataSource,
    private val snapshotHostsDataSource: SnapshotHostsDataSource,
    private val snapshotManager: AdblockSnapshotManager,
    private val fileHostsDataSourceFactory: FileHostsDataSource.Factory,
    private val urlHostsDataSourceFactory: UrlHostsDataSource.Factory,
    private val filterListRepository: FilterListRepository
) : HostsDataSourceProvider {

    override fun createHostsDataSource(): HostsDataSource {
        val legacy = when (val source = userPreferences.selectedHostsSource()) {
            // Prefer the downloaded snapshot over the frozen bundled asset; the
            // snapshot identifier changes on every refresh so the bloom filter
            // repopulates without an app update.
            HostsSourceType.Default ->
                if (snapshotManager.snapshotHostsFile() != null) snapshotHostsDataSource
                else assetsHostsDataSource
            is HostsSourceType.Local -> fileHostsDataSourceFactory.create(source.file)
            is HostsSourceType.Remote -> urlHostsDataSourceFactory.create(source.httpUrl)
        }

        val extraHosts = filterListRepository.lists()
            .filter { it.enabled && it.kind == FilterListKind.HOSTS }
            .mapNotNull { entry ->
                entry.url.toHttpUrlOrNull()?.let(urlHostsDataSourceFactory::create)
            }

        if (extraHosts.isEmpty()) return legacy

        return CompositeHostsDataSource(listOf(legacy) + extraHosts) { identifier, success ->
            if (success) {
                filterListRepository.updateTimestamp(identifier, System.currentTimeMillis())
            }
        }
    }
}
