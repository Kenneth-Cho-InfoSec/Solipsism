package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.DiskScheduler
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.constant.SCHEME_BOOKMARKS
import com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.html.HtmlPageFactory
import com.krystelligence.solipsism.html.bookmark.BookmarkPageFactory
import com.krystelligence.solipsism.html.download.DownloadPageFactory
import com.krystelligence.solipsism.html.history.HistoryPageFactory
import com.krystelligence.solipsism.html.homepage.HomepageSource
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.NavigationSecurity
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Message
import android.webkit.WebView
import android.webkit.URLUtil
import java.io.File
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

/**
 * An initializer that is run on a [WebView] after it is created.
 */
interface TabInitializer {

    /** The presentation surface required by this initializer. */
    val contentKind: TabContentKind
        get() = TabContentKind.ENGINE

    /**
     * Initialize the [WebView] instance held by the tab. If a url is loaded, the
     * provided [headers] should be used to load the url.
     */
    fun initialize(webView: WebView, headers: Map<String, String>)

}

/** Initializers carrying a stable tab identity across engine reconstruction. */
interface IdentifiedTabInitializer {
    val id: Int
}

/**
 * An initializer that loads a [url].
 */
class UrlInitializer(val url: String) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        webView.loadUrl(url, headers)
    }

}

/**
 * An initializer that displays the page set as the user's homepage preference.
 */
@Reusable
class HomePageInitializer @Inject constructor(
    private val userPreferences: UserPreferences,
    private val bookmarkPageInitializer: BookmarkPageInitializer,
    private val staticHomepageInitializer: StaticHomepageInitializer,
    private val restrictedDomainHomepageInitializer: RestrictedDomainHomepageInitializer
) : TabInitializer {

    override val contentKind: TabContentKind
        get() = when (HomepageSource.fromValue(userPreferences.homepageSource)) {
            HomepageSource.BUILT_IN -> if (userPreferences.homepage == SCHEME_HOMEPAGE) {
                TabContentKind.NATIVE_HOMEPAGE
            } else {
                TabContentKind.ENGINE
            }
            HomepageSource.STATIC_HTML -> if (userPreferences.homepageHtmlPath
                    ?.let(::File)
                    ?.takeIf(File::isFile)
                    ?.takeIf { runCatching { it.length() in 1..MAX_STATIC_HOMEPAGE_BYTES }.getOrDefault(false) }
                    != null
            ) {
                TabContentKind.ENGINE
            } else {
                TabContentKind.NATIVE_HOMEPAGE
            }
            HomepageSource.DOMAIN -> if (
                URLUtil.isHttpUrl(userPreferences.homepage) || URLUtil.isHttpsUrl(userPreferences.homepage)
            ) {
                TabContentKind.ENGINE
            } else {
                TabContentKind.NATIVE_HOMEPAGE
            }
        }

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        if (HomepageSource.fromValue(userPreferences.homepageSource) == HomepageSource.STATIC_HTML) {
            staticHomepageInitializer.initialize(webView, headers)
            return
        }
        if (HomepageSource.fromValue(userPreferences.homepageSource) == HomepageSource.DOMAIN) {
            restrictedDomainHomepageInitializer.initialize(webView, headers)
            return
        }

        val homepage = userPreferences.homepage

        when (homepage) {
            SCHEME_HOMEPAGE -> NoOpInitializer()
            SCHEME_BOOKMARKS -> bookmarkPageInitializer
            else -> UrlInitializer(homepage)
        }.initialize(webView, headers)
    }

    private companion object {
        const val MAX_STATIC_HOMEPAGE_BYTES = 512L * 1024L
    }

}

/** Loads sanitized HTML with a deliberately restricted WebView configuration. */
@Reusable
class StaticHomepageInitializer @Inject constructor(
    private val userPreferences: UserPreferences,
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        val path = userPreferences.homepageHtmlPath?.let(::File)
        val html = path?.takeIf(File::isFile)?.readText()
        if (html.isNullOrBlank()) {
            return
        }

        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
        webView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/custom-homepage/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }
}

/** Loads a user-selected website with JavaScript, storage, permissions, and popups disabled. */
@Reusable
class RestrictedDomainHomepageInitializer @Inject constructor(
    private val userPreferences: UserPreferences,
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        val homepage = userPreferences.homepage
        if (!URLUtil.isHttpUrl(homepage) && !URLUtil.isHttpsUrl(homepage)) {
            return
        }
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
        webView.loadUrl(homepage, headers)
    }
}

/**
 * An initializer that always displays Solipsism's visual start page, ignoring the configurable
 * homepage shortcut preference.
 */
@Reusable
class VisualHomePageInitializer @Inject constructor() : TabInitializer {

    override val contentKind: TabContentKind = TabContentKind.NATIVE_HOMEPAGE

    override fun initialize(webView: WebView, headers: Map<String, String>) = Unit
}

/**
 * An initializer that displays the bookmark page.
 */
@Reusable
class BookmarkPageInitializer @Inject constructor(
    bookmarkPageFactory: BookmarkPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(bookmarkPageFactory, diskScheduler, foregroundScheduler)

/**
 * An initializer that displays the download page.
 */
@Reusable
class DownloadPageInitializer @Inject constructor(
    downloadPageFactory: DownloadPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(downloadPageFactory, diskScheduler, foregroundScheduler)

/**
 * An initializer that displays the history page.
 */
@Reusable
class HistoryPageInitializer @Inject constructor(
    historyPageFactory: HistoryPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(historyPageFactory, diskScheduler, foregroundScheduler)

/**
 * An initializer that loads the url built by the [HtmlPageFactory].
 */
abstract class HtmlPageFactoryInitializer(
    private val htmlPageFactory: HtmlPageFactory,
    @DiskScheduler private val diskScheduler: Scheduler,
    @MainScheduler private val foregroundScheduler: Scheduler
) : TabInitializer {

    private val disposables = CompositeDisposable()

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        htmlPageFactory
            .buildPage()
            .subscribeOn(diskScheduler)
            .observeOn(foregroundScheduler)
            .subscribeBy(onSuccess = { pageUrl ->
                // Generated Downloads/History/Bookmarks pages are app-private files. File access
                // is disabled after normal web navigation, so grant it only for this trusted
                // internal root before loading the generated page.
                val trustedRoots = listOf(
                    File(webView.context.filesDir, "generated-html"),
                    File(webView.context.filesDir, "homepage")
                )
                webView.settings.allowFileAccess =
                    NavigationSecurity.isTrustedInternalFileUrl(pageUrl, trustedRoots)
                webView.loadUrl(pageUrl, headers)
            }).also(disposables::add)
    }

}

/**
 * An initializer that sets the [WebView] as the target of the [resultMessage]. Used for
 * `target="_blank"` links.
 */
class ResultMessageInitializer(private val resultMessage: Message) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        resultMessage.apply {
            (obj as WebView.WebViewTransport).webView = webView
        }.sendToTarget()
    }

}

/**
 * An initializer that restores the [WebView] state using the [bundle].
 */
open class BundleInitializer(open val bundle: Bundle) : TabInitializer {

    override val contentKind: TabContentKind
        get() = bundle.getString(TabStateKeys.CONTENT_KIND)
            ?.let { runCatching { TabContentKind.valueOf(it) }.getOrNull() }
            ?: TabContentKind.ENGINE

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        webView.restoreState(bundle)
    }

}

/**
 * An initializer that can be delayed until the view is attached. [initialTitle] is the title that
 * should be initially set on the tab.
 */
class FreezableBundleInitializer(
    override val bundle: Bundle,
    val initialTitle: String,
    override val id: Int
) : BundleInitializer(bundle), IdentifiedTabInitializer

/** Minimal, engine-neutral state used during an intentional global core switch. */
class EngineMigrationInitializer(
    val url: String,
    val title: String,
    override val contentKind: TabContentKind = TabContentKind.ENGINE,
) : TabInitializer {
    override fun initialize(webView: WebView, headers: Map<String, String>) {
        webView.loadUrl(url, headers)
    }
}

/**
 * An initializer that does not load anything into the [WebView].
 */
class NoOpInitializer : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) = Unit

}

/**
 * Ask the user's permission before loading the [url] and load the homepage instead if they deny
 * permission. Useful for scenarios where another app may attempt to open a malicious URL in the
 * browser via an intent.
 */
class PermissionInitializer @AssistedInject constructor(
    @Assisted private val url: String,
    private val activity: Activity,
    private val homePageInitializer: HomePageInitializer
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(R.string.title_warning)
            setMessage(R.string.message_blocked_local)
            setCancelable(false)
            setOnDismissListener {
                homePageInitializer.initialize(webView, headers)
            }
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(R.string.action_open) { _, _ ->
                UrlInitializer(url).initialize(webView, headers)
            }
        }.resizeAndShow()
    }

    /**
     * The factory for constructing the permission initializer.
     */
    @AssistedFactory
    interface Factory {

        /**
         * Creates the initializer.
         */
        fun create(url: String): PermissionInitializer

    }

}
