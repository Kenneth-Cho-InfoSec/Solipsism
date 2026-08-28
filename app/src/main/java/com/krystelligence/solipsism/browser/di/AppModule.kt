package com.krystelligence.solipsism.browser.di

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.tab.DefaultTabTitle
import com.krystelligence.solipsism.concurrency.CoroutineDispatcherProvider
import com.krystelligence.solipsism.concurrency.CoroutineDispatchers
import com.krystelligence.solipsism.device.BuildInfo
import com.krystelligence.solipsism.device.BuildType
import com.krystelligence.solipsism.favicon.FaviconCleanup
import com.krystelligence.solipsism.html.ListPageReader
import com.krystelligence.solipsism.html.bookmark.BookmarkCleanup
import com.krystelligence.solipsism.html.bookmark.BookmarkPageReader
import com.krystelligence.solipsism.html.download.DownloadCleanup
import com.krystelligence.solipsism.html.history.HistoryCleanup
import com.krystelligence.solipsism.html.homepage.HomeCleanup
import com.krystelligence.solipsism.js.InvertPage
import com.krystelligence.solipsism.js.TextReflow
import com.krystelligence.solipsism.js.ThemeColor
import com.krystelligence.solipsism.log.AndroidLogger
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.log.NoOpLogger
import com.krystelligence.solipsism.migration.Cleanup
import com.krystelligence.solipsism.search.suggestions.RequestFactory
import com.krystelligence.solipsism.utils.FileUtils
import com.krystelligence.solipsism.utils.ThreadSafeFileProvider
import android.app.ActivityManager
import android.app.Application
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import com.anthonycr.mezzanine.mezzanine
import dagger.Module
import dagger.Provides
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
class AppModule {

    @Provides
    @MainHandler
    fun provideMainHandler() = Handler(Looper.getMainLooper())

    @Provides
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    @UserPrefs
    fun provideUserPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences("settings", 0)

    @Provides
    @DevPrefs
    fun provideDebugPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences("developer_settings", 0)

    @Provides
    @AdBlockPrefs
    fun provideAdBlockPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences("ad_block_settings", 0)

    @Provides
    fun providesAssetManager(application: Application): AssetManager = application.assets

    @Provides
    fun providesClipboardManager(application: Application) =
        application.getSystemService<ClipboardManager>()!!

    @Provides
    fun providesInputMethodManager(application: Application) =
        application.getSystemService<InputMethodManager>()!!

    @Provides
    fun providesDownloadManager(application: Application) =
        application.getSystemService<DownloadManager>()!!

    @Provides
    fun providesConnectivityManager(application: Application) =
        application.getSystemService<ConnectivityManager>()!!

    @Provides
    fun providesNotificationManager(application: Application) =
        application.getSystemService<NotificationManager>()!!

    @Provides
    fun providesWindowManager(application: Application) =
        application.getSystemService<WindowManager>()!!

    @Provides
    fun providesShortcutManager(application: Application) =
        application.getSystemService<ShortcutManager>()!!

    @Provides
    fun providesActivityManager(application: Application) =
        application.getSystemService<ActivityManager>()!!

    @Provides
    @DatabaseScheduler
    @Singleton
    fun providesIoThread(): Scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    @Provides
    @DiskScheduler
    @Singleton
    fun providesDiskThread(): Scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    @Provides
    @NetworkScheduler
    @Singleton
    fun providesNetworkThread(): Scheduler =
        Schedulers.from(ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS, LinkedBlockingDeque()))

    @Provides
    @MainScheduler
    @Singleton
    fun providesMainThread(): Scheduler = AndroidSchedulers.mainThread()

    @Singleton
    @Provides
    fun providesSuggestionsCacheControl(): CacheControl =
        CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build()

    @Singleton
    @Provides
    fun providesSuggestionsRequestFactory(cacheControl: CacheControl): RequestFactory =
        object : RequestFactory {
            override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String): Request {
                return Request.Builder().url(httpUrl)
                    .addHeader("Accept-Charset", encoding)
                    .cacheControl(cacheControl)
                    .build()
            }
        }

    private fun createInterceptorWithMaxCacheAge(maxCacheAgeSeconds: Long) = Interceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .header("cache-control", "max-age=$maxCacheAgeSeconds, max-stale=$maxCacheAgeSeconds")
            .build()
    }

    @Singleton
    @Provides
    @SuggestionsClient
    fun providesSuggestionsHttpClient(application: Application): Single<OkHttpClient> =
        Single.fromCallable {
            val intervalDay = TimeUnit.DAYS.toSeconds(1)
            val suggestionsCache = File(application.cacheDir, "suggestion_responses")

            return@fromCallable OkHttpClient.Builder()
                .cache(Cache(suggestionsCache, FileUtils.megabytesToBytes(1)))
                .addNetworkInterceptor(createInterceptorWithMaxCacheAge(intervalDay))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        }.cache()

    @Singleton
    @Provides
    @HostsClient
    fun providesHostsHttpClient(application: Application): Single<OkHttpClient> =
        Single.fromCallable {
            val intervalWeek = TimeUnit.DAYS.toSeconds(7)
            val suggestionsCache = File(application.cacheDir, "hosts_cache")

            return@fromCallable OkHttpClient.Builder()
                .cache(Cache(suggestionsCache, FileUtils.megabytesToBytes(5)))
                .addNetworkInterceptor(createInterceptorWithMaxCacheAge(intervalWeek))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
        }.cache()

    @Provides
    @Singleton
    fun provideLogger(buildInfo: BuildInfo): Logger = if (buildInfo.buildType == BuildType.DEBUG) {
        AndroidLogger()
    } else {
        NoOpLogger()
    }

    @Provides
    fun providesListPageReader(): ListPageReader = mezzanine()

    @Provides
    fun providesBookmarkPageReader(): BookmarkPageReader = mezzanine()

    @Provides
    fun providesTextReflow(): TextReflow = mezzanine()

    @Provides
    fun providesThemeColor(): ThemeColor = mezzanine()

    @Provides
    fun providesInvertPage(): InvertPage = mezzanine()

    @DefaultTabTitle
    @Provides
    fun providesDefaultTabTitle(application: Application): String =
        application.getString(R.string.untitled)

    @Provides
    fun providesCleanupList(
        faviconCleanup: FaviconCleanup,
        bookmarkCleanup: BookmarkCleanup,
        downloadCleanup: DownloadCleanup,
        historyCleanup: HistoryCleanup,
        homeCleanup: HomeCleanup
    ): List<@JvmSuppressWildcards Cleanup.Action> =
        listOf(faviconCleanup, bookmarkCleanup, downloadCleanup, historyCleanup, homeCleanup)

    @FilesDir
    @Provides
    fun providesFilesDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        application.filesDir
    }

    @DataDir
    @Provides
    fun providesDataDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        application.dataDir
    }

    @CacheDir
    @Provides
    fun providesCacheDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        application.cacheDir
    }

    @Singleton
    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    fun providesAppCoroutineScope(): CoroutineScope = GlobalScope

    @Singleton
    @Provides
    fun providesDispatchers(): CoroutineDispatchers = CoroutineDispatcherProvider(
        main = Dispatchers.Main,
        io = Dispatchers.IO,
        default = Dispatchers.Default
    )

    @DatabaseScheduler
    @Provides
    fun providesDatabaseDispatcher(
        coroutineDispatchers: CoroutineDispatchers
    ): CoroutineDispatcher = coroutineDispatchers.io.limitedParallelism(1)

    @NetworkScheduler
    @Provides
    fun providesNetworkDispatcher(
        coroutineDispatchers: CoroutineDispatchers
    ): CoroutineDispatcher = coroutineDispatchers.io.limitedParallelism(4)
}

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class SuggestionsClient

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class HostsClient

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class MainHandler

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class UserPrefs

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class AdBlockPrefs

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class DevPrefs

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class MainScheduler

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class DiskScheduler

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class NetworkScheduler

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class DatabaseScheduler

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class FilesDir

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class DataDir

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class CacheDir
