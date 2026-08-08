package com.krystelligence.solipsism.browser.di

import com.krystelligence.solipsism.adblock.allowlist.AllowListModel
import com.krystelligence.solipsism.adblock.allowlist.SessionAllowListModel
import com.krystelligence.solipsism.adblock.source.AssetsHostsDataSource
import com.krystelligence.solipsism.adblock.source.HostsDataSource
import com.krystelligence.solipsism.adblock.source.HostsDataSourceProvider
import com.krystelligence.solipsism.adblock.source.PreferencesHostsDataSourceProvider
import com.krystelligence.solipsism.database.adblock.HostsDatabase
import com.krystelligence.solipsism.database.adblock.HostsRepository
import com.krystelligence.solipsism.database.allowlist.AdBlockAllowListDatabase
import com.krystelligence.solipsism.database.allowlist.AdBlockAllowListRepository
import com.krystelligence.solipsism.database.bookmark.BookmarkDatabase
import com.krystelligence.solipsism.database.bookmark.BookmarkRepository
import com.krystelligence.solipsism.database.downloads.DownloadsDatabase
import com.krystelligence.solipsism.database.downloads.DownloadsRepository
import com.krystelligence.solipsism.database.history.HistoryDatabase
import com.krystelligence.solipsism.database.history.HistoryRepository
import com.krystelligence.solipsism.database.vault.VaultDatabase
import com.krystelligence.solipsism.database.vault.VaultRepository
import com.krystelligence.solipsism.ssl.SessionSslWarningPreferences
import com.krystelligence.solipsism.ssl.SslWarningPreferences
import dagger.Binds
import dagger.Module

/**
 * Dependency injection module used to bind implementations to interfaces.
 */
@Module
interface AppBindsModule {

    @Binds
    fun bindsBookmarkModel(bookmarkDatabase: BookmarkDatabase): BookmarkRepository

    @Binds
    fun bindsDownloadsModel(downloadsDatabase: DownloadsDatabase): DownloadsRepository

    @Binds
    fun bindsHistoryModel(historyDatabase: HistoryDatabase): HistoryRepository

    @Binds
    fun bindsVaultRepository(vaultDatabase: VaultDatabase): VaultRepository

    @Binds
    fun bindsAdBlockAllowListModel(adBlockAllowListDatabase: AdBlockAllowListDatabase): AdBlockAllowListRepository

    @Binds
    fun bindsAllowListModel(sessionAllowListModel: SessionAllowListModel): AllowListModel

    @Binds
    fun bindsSslWarningPreferences(sessionSslWarningPreferences: SessionSslWarningPreferences): SslWarningPreferences

    @Binds
    fun bindsHostsDataSource(assetsHostsDataSource: AssetsHostsDataSource): HostsDataSource

    @Binds
    fun bindsHostsRepository(hostsDatabase: HostsDatabase): HostsRepository

    @Binds
    fun bindsHostsDataSourceProvider(preferencesHostsDataSourceProvider: PreferencesHostsDataSourceProvider): HostsDataSourceProvider
}
