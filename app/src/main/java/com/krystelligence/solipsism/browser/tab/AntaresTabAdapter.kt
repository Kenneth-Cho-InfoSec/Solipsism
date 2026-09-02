package com.krystelligence.solipsism.browser.tab

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebSettings
import android.widget.FrameLayout
import androidx.activity.result.ActivityResult
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.krystelligence.solipsism.browser.download.PendingDownload
import com.krystelligence.solipsism.browser.engine.AntaresContentBlockingPolicy
import com.krystelligence.solipsism.browser.engine.AntaresCoordinateBridge
import com.krystelligence.solipsism.browser.engine.AntaresMediaPlayerView
import com.krystelligence.solipsism.browser.engine.AntaresSessionView
import com.krystelligence.solipsism.browser.engine.toAntaresTheme
import com.krystelligence.solipsism.browser.engine.BrowserMediaRequest
import com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
import com.krystelligence.solipsism.constant.SCHEME_ANTARES_HOMEPAGE
import com.krystelligence.solipsism.ids.ViewIdGenerator
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.preference.DeveloperPreferences
import com.krystelligence.solipsism.preference.antaresUserAgent
import com.krystelligence.solipsism.constant.DESKTOP_USER_AGENT
import com.krystelligence.solipsism.ssl.SslCertificateInfo
import com.krystelligence.solipsism.ssl.SslState
import com.krystelligence.solipsism.utils.Option
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Adapts one remote Antares session to Solipsism's existing engine-neutral tab contract. */
class AntaresTabAdapter private constructor(
    tabInitializer: TabInitializer,
    override var tabType: TabModel.Type,
    private val defaultTabTitle: String,
    private val viewIdGenerator: ViewIdGenerator,
    private val initialUrlResolver: AntaresInitialUrlResolver,
    private val userPreferences: UserPreferences,
    private val developerPreferences: DeveloperPreferences,
    private val contentBlockingPolicy: AntaresContentBlockingPolicy,
) : TabModel, AntaresSessionView.Listener {
    private val contentKindSubject = BehaviorSubject.createDefault(
        initialUrlResolver.contentKind(tabInitializer),
    )
    override val contentKind: TabContentKind
        get() = requireNotNull(contentKindSubject.value)
    override fun contentKindChanges(): Observable<TabContentKind> = contentKindSubject.hide()

    override val id: Int = (tabInitializer as? IdentifiedTabInitializer)
        ?.id
        ?.takeIf { it != -1 }
        ?.also(viewIdGenerator::claimViewId)
        ?: viewIdGenerator.generateViewId()

    private var currentUrl = initialUrlResolver.resolve(tabInitializer)
    private var currentTitle = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
        initialUrlResolver.context.getString(com.krystelligence.solipsism.R.string.app_name)
    } else when (tabInitializer) {
        is EngineMigrationInitializer -> tabInitializer.title
        is FreezableBundleInitializer -> tabInitializer.bundle
            .getString(TabStateKeys.ENGINE_TITLE)
            .orEmpty()
            .ifBlank { tabInitializer.initialTitle }
        else -> defaultTabTitle
    }
    private var progress = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) 100 else 0
    private var backAvailable = false
    private var forwardAvailable = false
    private var findInPageQuery: String? = null
    private val destroyed = AtomicBoolean(false)
    private val disposables = CompositeDisposable()
    private var desktopAgentOverride = false
    private val providerUserAgent = WebSettings.getDefaultUserAgent(initialUrlResolver.context)

    val contentView: AntaresSessionView = AntaresSessionView(
        context = initialUrlResolver.context,
        initialUrl = initialUrlResolver.engineInitialUrl(tabInitializer),
        initialUserAgent = userPreferences.antaresUserAgent(providerUserAgent),
        initialTheme = userPreferences.useTheme.toAntaresTheme(initialUrlResolver.context),
        contentBlockingPolicy = contentBlockingPolicy,
        initialBlockAds = userPreferences.adBlockEnabled,
        initialBlockGifs = userPreferences.blockGifImagesEnabled,
        listener = this,
    ).apply { id = this@AntaresTabAdapter.id }

    private val coordinateBridge = if (developerPreferences.antaresCoordinateBridgeEnabled) {
        AntaresCoordinateBridge(
            context = initialUrlResolver.context,
            sessionView = contentView,
            userPreferences = userPreferences,
            initialUrl = initialUrlResolver.engineInitialUrl(tabInitializer),
        )
    } else {
        null
    }

    /**
     * An ordinary Android view above Antares's remote SurfaceControl package. It guarantees that
     * taps and drags start in Solipsism's window before being relayed to the experimental core.
     */
    val engineView: FrameLayout = FrameLayout(initialUrlResolver.context).apply {
        coordinateBridge?.referenceView?.let { referenceView ->
            addView(
                referenceView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        addView(
            contentView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            View(context).apply {
                isClickable = true
                isFocusable = true
                val inputRouter = AntaresInputRouter(
                    contentView,
                    coordinateBridge,
                    ViewConfiguration.get(context).scaledTouchSlop,
                )
                setOnTouchListener { _, event -> inputRouter.onTouch(event) }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private val urlSubject = BehaviorSubject.createDefault(currentUrl)
    private val titleSubject = BehaviorSubject.createDefault(currentTitle)
    private val progressSubject = BehaviorSubject.createDefault(progress)
    private val backSubject = BehaviorSubject.createDefault(false)
    private val forwardSubject = BehaviorSubject.createDefault(false)
    private val focusSubject = BehaviorSubject.createDefault(false)
    private val showMediaSubject = PublishSubject.create<View>()
    private val hideMediaSubject = PublishSubject.create<Unit>()
    private val loadingTracker = AntaresLoadingTracker(
        schedule = contentView::postDelayed,
        cancel = contentView::removeCallbacks,
        report = { value ->
            progress = value
            progressSubject.onNext(value)
        },
    )

    init {
        contentView.onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
            focusSubject.onNext(focused)
        }
    }

    override fun loadUrl(url: String) {
        setContentKind(TabContentKind.ENGINE)
        currentUrl = url
        urlSubject.onNext(url)
        coordinateBridge?.loadUrl(url)
        contentView.loadUrl(url)
    }
    override fun loadFromInitializer(tabInitializer: TabInitializer) {
        val kind = initialUrlResolver.contentKind(tabInitializer)
        setContentKind(kind)
        if (kind == TabContentKind.NATIVE_HOMEPAGE) return
        loadUrl(initialUrlResolver.resolve(tabInitializer))
    }
    override fun goBack() {
        if (contentKind == TabContentKind.ENGINE) contentView.goBack()
    }
    override fun canGoBack(): Boolean = contentKind == TabContentKind.ENGINE && backAvailable
    override fun canGoBackChanges(): Observable<Boolean> = backSubject.hide()
    override fun goForward() {
        if (contentKind == TabContentKind.ENGINE) contentView.goForward()
    }
    override fun canGoForward(): Boolean = contentKind == TabContentKind.ENGINE && forwardAvailable
    override fun canGoForwardChanges(): Observable<Boolean> = forwardSubject.hide()
    override fun toggleDesktopAgent() {
        desktopAgentOverride = !desktopAgentOverride
        contentView.setUserAgent(
            if (desktopAgentOverride) {
                DESKTOP_USER_AGENT
            } else {
                userPreferences.antaresUserAgent(providerUserAgent)
            },
        )
    }
    override fun applyUserAgentPreference() {
        desktopAgentOverride = false
        contentView.setUserAgent(userPreferences.antaresUserAgent(providerUserAgent))
    }
    override fun applyContentBlockingPreferences() {
        contentView.setContentBlocking(
            blockAds = userPreferences.adBlockEnabled,
            blockGifs = userPreferences.blockGifImagesEnabled,
        )
    }
    override fun applyThemePreference() {
        contentView.setTheme(userPreferences.useTheme.toAntaresTheme(contentView.context))
    }
    override fun reload() {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            contentKindSubject.onNext(TabContentKind.NATIVE_HOMEPAGE)
        } else {
            contentView.reload()
        }
    }
    override fun reloadWithJavaScriptDisabled() = Unit
    override fun captureVisiblePage(): Bitmap? = null
    override fun pickElement() = Unit
    override fun stopLoading() = contentView.stopLoading()
    override fun find(query: String) { findInPageQuery = query }
    override fun findNext() = Unit
    override fun findPrevious() = Unit
    override fun clearFindMatches() { findInPageQuery = null }
    override fun readPageText(onText: (String) -> Unit) = onText("")
    override val findQuery: String? get() = findInPageQuery
    override val favicon: Bitmap? get() = null
    override fun faviconChanges(): Observable<Option<Bitmap>> = Observable.just(Option.None)
    override val preview: Pair<String?, Long> get() = null to 0L
    override fun previewChanges(): Observable<Pair<String?, Long>> = Observable.just(preview)
    override val themeColor: Int get() = Color.TRANSPARENT
    override fun themeColorChanges(): Observable<Int> = Observable.just(themeColor)
    override val url: String get() = currentUrl
    override fun urlChanges(): Observable<String> = urlSubject.hide()
    override val title: String get() = currentTitle
    override fun titleChanges(): Observable<String> = titleSubject.hide()
    override val sslCertificateInfo: SslCertificateInfo? get() = null
    override val sslState: SslState get() = SslState.None
    override fun sslChanges(): Observable<SslState> = Observable.just(SslState.None)
    override val loadingProgress: Int get() = progress
    override fun loadingProgress(): Observable<Int> = progressSubject.hide()
    override fun downloadRequests(): Observable<PendingDownload> = Observable.never()
    override fun fileChooserRequests(): Observable<Intent> = Observable.never()
    override fun handleFileChooserResult(activityResult: ActivityResult) = Unit
    override fun showCustomViewRequests(): Observable<View> = showMediaSubject.hide()
    override fun hideCustomViewRequests(): Observable<Unit> = hideMediaSubject.hide()
    override fun hideCustomView() = hideMediaSubject.onNext(Unit)
    override fun createWindowRequests(): Observable<TabInitializer> = Observable.never()
    override fun closeWindowRequests(): Observable<Unit> = Observable.never()

    override var isForeground: Boolean = false
        set(value) {
            field = value
            coordinateBridge?.setForeground(value)
            contentView.setForeground(value)
            if (value && contentKind == TabContentKind.ENGINE) {
                contentView.activateForTab(currentUrl)
            }
        }

    override fun setBrowserChromeOverlayVisible(visible: Boolean, onApplied: () -> Unit) {
        contentView.setBrowserChromeOverlayVisible(visible, onApplied)
    }

    override fun setContentVisible(visible: Boolean) {
        contentView.setContentVisible(visible)
    }

    override fun onHostResumed() {
        // The presenter can resume an already-selected tab without running selectTab again.
        // Reassert foreground state so its diagnostic Chromium reference is available after an
        // app relaunch or package update.
        coordinateBridge?.setForeground(true)
        contentView.onHostResumed()
    }

    override val hasFocus: Boolean get() = contentView.hasFocus()
    override fun hasFocusChanges(): Observable<Boolean> = focusSubject.hide()

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        loadingTracker.dispose()
        disposables.dispose()
        coordinateBridge?.destroy()
        viewIdGenerator.releaseViewId(id)
        contentView.destroySession()
    }

    override fun freeze(): Bundle = Bundle(ClassLoader.getSystemClassLoader()).apply {
        putString(TabStateKeys.ENGINE_URL, currentUrl)
        putString(TabStateKeys.ENGINE_TITLE, currentTitle)
        putString(TabStateKeys.CONTENT_KIND, contentKind.name)
    }

    override fun onReady() = Unit
    override fun onLoadStarted() {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        loadingTracker.started()
    }
    override fun onLoadEnded() {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        loadingTracker.complete()
    }
    override fun onTitleChanged(title: String) {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        currentTitle = title.ifBlank { defaultTabTitle }
        titleSubject.onNext(currentTitle)
        if (title.isNotBlank()) loadingTracker.pageBecameOperational()
    }
    override fun onUrlChanged(url: String) {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        currentUrl = url
        coordinateBridge?.onAntaresUrlChanged(url)
        urlSubject.onNext(url)
    }
    override fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        backAvailable = canGoBack
        forwardAvailable = canGoForward
        backSubject.onNext(canGoBack)
        forwardSubject.onNext(canGoForward)
    }
    override fun onAlert(message: String) = Unit
    @UnstableApi
    override fun onMediaRequest(request: Bundle) {
        val activity = contentView.findViewTreeLifecycleOwner() as? Activity ?: return
        val mediaRequest = BrowserMediaRequest.fromBundle(request) ?: return
        showMediaSubject.onNext(
            AntaresMediaPlayerView(activity, mediaRequest) {
                hideMediaSubject.onNext(Unit)
            },
        )
    }
    override fun onElementProbeResult(requestId: Int, descriptor: String) {
        coordinateBridge?.onAntaresProbeResult(requestId, descriptor)
    }
    override fun onEngineError(message: String) {
        loadingTracker.complete()
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        currentTitle = message.ifBlank { "Antares Engine unavailable" }
        titleSubject.onNext(currentTitle)
    }

    private fun setContentKind(kind: TabContentKind) {
        if (contentKindSubject.value == kind) {
            if (kind == TabContentKind.NATIVE_HOMEPAGE) contentKindSubject.onNext(kind)
            return
        }
        if (kind == TabContentKind.NATIVE_HOMEPAGE) {
            contentView.setContentVisible(false)
            currentUrl = SCHEME_HOMEPAGE
            currentTitle = initialUrlResolver.context.getString(com.krystelligence.solipsism.R.string.app_name)
            progress = 100
            backAvailable = false
            forwardAvailable = false
            urlSubject.onNext(currentUrl)
            titleSubject.onNext(currentTitle)
            progressSubject.onNext(progress)
            backSubject.onNext(false)
            forwardSubject.onNext(false)
        }
        contentKindSubject.onNext(kind)
    }

    class Factory @Inject constructor(
        @DefaultTabTitle private val defaultTabTitle: String,
        private val viewIdGenerator: ViewIdGenerator,
        private val initialUrlResolver: AntaresInitialUrlResolver,
        private val userPreferences: UserPreferences,
        private val developerPreferences: DeveloperPreferences,
        private val contentBlockingPolicy: AntaresContentBlockingPolicy,
    ) {
        fun create(initializer: TabInitializer, tabType: TabModel.Type): AntaresTabAdapter =
            AntaresTabAdapter(
                initializer,
                tabType,
                defaultTabTitle,
                viewIdGenerator,
                initialUrlResolver,
                userPreferences,
                developerPreferences,
                contentBlockingPolicy,
            )
    }
}

/**
 * Maps normal Android gestures onto Servo's reliable primitive input operations.
 *
 * Servo's experimental Android touch synthesis can lose mouse-compatible clicks on complex
 * sites. A tap therefore uses its native primary-click entry point, while a single-finger drag
 * uses its native scroll entry point. Multi-touch continues through the regular touch stream so
 * pinch gestures retain their semantics.
 */
private class AntaresInputRouter(
    private val sessionView: AntaresSessionView,
    private val coordinateBridge: AntaresCoordinateBridge?,
    private val touchSlop: Int,
) {
    private var downEvent: MotionEvent? = null
    private var lastX = 0f
    private var lastY = 0f
    private var scrolling = false
    private var multiTouch = false

    fun onTouch(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            clear()
            downEvent = MotionEvent.obtain(event)
            lastX = event.x
            lastY = event.y
            true
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            beginMultiTouch(event)
            true
        }

        MotionEvent.ACTION_MOVE -> {
            if (multiTouch || event.pointerCount > 1) {
                beginMultiTouch(event)
                sessionView.relayTouchEvent(event)
            } else {
                scrollIfNeeded(event)
            }
            true
        }

        MotionEvent.ACTION_POINTER_UP -> {
            if (multiTouch) sessionView.relayTouchEvent(event)
            true
        }

        MotionEvent.ACTION_UP -> {
            when {
                multiTouch -> sessionView.relayTouchEvent(event)
                scrolling -> Unit
                else -> coordinateBridge?.verifyTap(event.x, event.y)
                    ?: sessionView.click(event.x, event.y)
            }
            clear()
            true
        }

        MotionEvent.ACTION_CANCEL -> {
            if (multiTouch) sessionView.relayTouchEvent(event)
            clear()
            true
        }

        else -> true
    }

    private fun beginMultiTouch(event: MotionEvent) {
        if (multiTouch) return
        downEvent?.let(sessionView::relayTouchEvent)
        downEvent?.recycle()
        downEvent = null
        multiTouch = true
    }

    private fun scrollIfNeeded(event: MotionEvent) {
        val totalX = event.x - lastX
        val totalY = event.y - lastY
        if (!scrolling && totalX * totalX + totalY * totalY < touchSlop * touchSlop) return
        scrolling = true
        sessionView.scroll(
            dx = (lastX - event.x).toInt(),
            dy = (lastY - event.y).toInt(),
            x = event.x.toInt(),
            y = event.y.toInt(),
        )
        coordinateBridge?.scrollBy(
            dx = (lastX - event.x).toInt(),
            dy = (lastY - event.y).toInt(),
        )
        lastX = event.x
        lastY = event.y
    }

    private fun clear() {
        downEvent?.recycle()
        downEvent = null
        scrolling = false
        multiTouch = false
    }
}

class AntaresInitialUrlResolver @Inject constructor(
    val context: android.content.Context,
    private val userPreferences: UserPreferences,
) {
    fun resolve(initializer: TabInitializer): String = when {
        contentKind(initializer) == TabContentKind.NATIVE_HOMEPAGE -> SCHEME_HOMEPAGE
        else -> when (initializer) {
        is UrlInitializer -> initializer.url
        is EngineMigrationInitializer -> initializer.url
        is FreezableBundleInitializer -> initializer.bundle
            .getString(TabStateKeys.ENGINE_URL)
            ?.takeIf(String::isNotBlank)
            ?: FALLBACK_HOME
        is NoOpInitializer -> "about:blank"
        is HomePageInitializer -> userPreferences.homepage
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: FALLBACK_HOME
        is VisualHomePageInitializer -> SCHEME_HOMEPAGE
        else -> FALLBACK_HOME
        }
    }

    fun engineInitialUrl(initializer: TabInitializer): String =
        if (contentKind(initializer) == TabContentKind.NATIVE_HOMEPAGE) "about:blank" else resolve(initializer)

    fun contentKind(initializer: TabInitializer): TabContentKind = when (initializer) {
        is EngineMigrationInitializer -> if (
            initializer.contentKind == TabContentKind.NATIVE_HOMEPAGE ||
            initializer.url == SCHEME_ANTARES_HOMEPAGE ||
            initializer.url.endsWith("/generated-html/homepage.html")
        ) {
            TabContentKind.NATIVE_HOMEPAGE
        } else {
            TabContentKind.ENGINE
        }
        is FreezableBundleInitializer -> initializer.bundle
            .getString(TabStateKeys.ENGINE_URL)
            ?.let { url ->
                if (initializer.contentKind == TabContentKind.NATIVE_HOMEPAGE ||
                    url == SCHEME_ANTARES_HOMEPAGE || url.endsWith("/generated-html/homepage.html")
                ) {
                    TabContentKind.NATIVE_HOMEPAGE
                } else {
                    TabContentKind.ENGINE
                }
            } ?: initializer.contentKind
        else -> initializer.contentKind
    }

    companion object {
        const val FALLBACK_HOME = "https://kenneth-cho-infosec.github.io/Solipsism/"
    }
}
