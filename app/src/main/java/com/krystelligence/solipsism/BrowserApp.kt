package com.krystelligence.solipsism

import com.krystelligence.solipsism.browser.di.AppComponent
import com.krystelligence.solipsism.browser.di.DaggerAppComponent
import com.krystelligence.solipsism.browser.di.DatabaseScheduler
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.browser.proxy.ProxyAdapter
import com.krystelligence.solipsism.database.bookmark.BookmarkExporter
import com.krystelligence.solipsism.database.bookmark.BookmarkRepository
import com.krystelligence.solipsism.device.BuildInfo
import com.krystelligence.solipsism.device.BuildType
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.migration.Cleanup
import com.krystelligence.solipsism.utils.FileUtils
import com.krystelligence.solipsism.utils.LeakCanaryUtils
import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.webkit.WebView
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * The browser application.
 */
class BrowserApp : Application() {

    @Inject
    internal lateinit var leakCanaryUtils: LeakCanaryUtils

    @Inject
    internal lateinit var bookmarkModel: BookmarkRepository

    @Inject
    @DatabaseScheduler
    internal lateinit var databaseScheduler: Scheduler

    @Inject
    internal lateinit var logger: Logger

    @Inject
    internal lateinit var buildInfo: BuildInfo

    @Inject
    internal lateinit var proxyAdapter: ProxyAdapter

    @Inject
    internal lateinit var cleanup: Cleanup

    lateinit var applicationComponent: AppComponent

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        MainScope().launch {
            cleanup.cleanup()
        }

        if (Build.VERSION.SDK_INT >= 28) {
            if (getProcessName() == "$packageName:incognito") {
                File(dataDir, "app_webview_incognito").deleteRecursively()
                WebView.setDataDirectorySuffix("incognito")
            }
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            if (BuildConfig.DEBUG) {
                FileUtils.writeCrashToStorage(this, ex)
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex)
            } else {
                exitProcess(2)
            }
        }

        RxJavaPlugins.setErrorHandler { throwable: Throwable? ->
            if (BuildConfig.DEBUG && throwable != null) {
                FileUtils.writeCrashToStorage(this, throwable)
                throw throwable
            }
        }

        applicationComponent = DaggerAppComponent.builder()
            .application(this)
            .buildInfo(createBuildInfo())
            .build()
        injector.inject(this)

        Single.fromCallable(bookmarkModel::count)
            .filter { it == 0L }
            .flatMapCompletable {
                val assetsBookmarks = BookmarkExporter.importBookmarksFromAssets(this@BrowserApp)
                bookmarkModel.addBookmarkList(assetsBookmarks)
            }
            .andThen(migrateLegacyDefaultBookmarks())
            .subscribeOn(databaseScheduler)
            .subscribe()

        if (buildInfo.buildType == BuildType.DEBUG) {
            leakCanaryUtils.setup()
        }

        registerActivityLifecycleCallbacks(proxyAdapter)
    }

    /**
     * Create the [BuildType] from the [BuildConfig].
     */
    private fun createBuildInfo() = BuildInfo(
        when {
            BuildConfig.DEBUG -> BuildType.DEBUG
            else -> BuildType.RELEASE
        }
    )

    /**
     * Replaces the two stale bookmarks inherited from the Lightning Browser defaults.
     * Only exact legacy URLs are migrated, so user-created bookmarks are not affected.
     */
    private fun migrateLegacyDefaultBookmarks(): Completable =
        bookmarkModel.getAllBookmarksSorted().flatMapCompletable { bookmarks ->
            Completable.concat(
                bookmarks.mapNotNull { bookmark ->
                    legacyBookmarkReplacements[bookmark.url]?.let { replacement ->
                        bookmarkModel.deleteBookmark(bookmark)
                            .ignoreElement()
                            .andThen(
                                bookmarkModel.addBookmarkIfNotExists(
                                    bookmark.copy(
                                        url = replacement.url,
                                        title = replacement.title
                                    )
                                ).ignoreElement()
                            )
                    }
                }
            )
        }

    private data class BookmarkReplacement(val url: String, val title: String)

    companion object {
        private const val TAG = "BrowserApp"

        private val legacyBookmarkReplacements = mapOf(
            "https://github.com/anthonycr/Lightning-Browser/releases" to BookmarkReplacement(
                url = "https://github.com/Kenneth-Cho-InfoSec/Solipsism/releases",
                title = "Changelog"
            ),
            "https://twitter.com/RestainoAnthony" to BookmarkReplacement(
                url = "https://github.com/Kenneth-Cho-InfoSec",
                title = "Contact Me"
            )
        )
    }
}
