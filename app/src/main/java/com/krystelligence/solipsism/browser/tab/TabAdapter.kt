package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.DiskScheduler
import com.krystelligence.solipsism.adblock.custom.ElementPickerController
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.download.PendingDownload
import com.krystelligence.solipsism.browser.image.IconFreeze
import com.krystelligence.solipsism.browser.view.setCompositeOnFocusChangeListener
import com.krystelligence.solipsism.browser.view.setCompositeTouchListener
import com.krystelligence.solipsism.constant.DESKTOP_USER_AGENT
import com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
import com.krystelligence.solipsism.constant.chromiumVersion
import com.krystelligence.solipsism.ids.ViewIdGenerator
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.html.homepage.HomepageSource
import com.krystelligence.solipsism.preference.userAgent
import com.krystelligence.solipsism.preview.PreviewModel
import com.krystelligence.solipsism.ssl.SslCertificateInfo
import com.krystelligence.solipsism.ssl.SslState
import com.krystelligence.solipsism.utils.Option
import com.krystelligence.solipsism.utils.value
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.JavascriptInterface
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.json.JSONTokener
import androidx.activity.result.ActivityResult
import androidx.core.os.BundleCompat
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.Optional
import java.util.concurrent.TimeUnit


/**
 * Creates the adaptation between a [WebView] and the [TabModel] interface used by the browser.
 */
class TabAdapter @AssistedInject constructor(
    @Assisted private val tabInitializer: TabInitializer,
    @Assisted private val webViewLazy: Lazy<WebView>,
    @Assisted private val requestHeaders: Map<String, String>,
    @Assisted private val tabWebViewClient: TabWebViewClient,
    @Assisted override var tabType: TabModel.Type,
    private val tabWebChromeClient: TabWebChromeClient,
    private val userPreferences: UserPreferences,
    @DefaultUserAgent private val defaultUserAgent: String,
    @DefaultTabTitle private val defaultTabTitle: String,
    @IconFreeze private val iconFreeze: Bitmap,
    private val viewIdGenerator: ViewIdGenerator,
    private val previewModel: PreviewModel,
    @DiskScheduler private val diskScheduler: Scheduler,
    @MainScheduler private val mainScheduler: Scheduler,
    private val elementPickerController: ElementPickerController,
) : TabModel {

    private val contentKindSubject = BehaviorSubject.createDefault(tabInitializer.contentKind)
    private var engineContentVisible = tabInitializer.contentKind == TabContentKind.ENGINE

    override val contentKind: TabContentKind
        get() = requireNotNull(contentKindSubject.value)

    override fun contentKindChanges(): Observable<TabContentKind> = contentKindSubject.hide()

    @AssistedFactory
    interface Factory {

        fun create(
            tabInitializer: TabInitializer,
            webView: Lazy<WebView>,
            requestHeaders: Map<String, String>,
            tabWebViewClient: TabWebViewClient,
            tabType: TabModel.Type,
        ): TabAdapter
    }

    private var latentInitializer: FreezableBundleInitializer? = null

    /**
     * WebView restores its page title and favicon asynchronously. Keep the last values in the
     * tab snapshot so the tab switcher is complete immediately after an activity or process
     * recreation, rather than briefly falling back to an untitled generic tab.
     */
    private var restoredTitle: String? = (tabInitializer as? FreezableBundleInitializer)
        ?.initialTitle
        ?.takeIf(String::isNotBlank)
    private var restoredFavicon: Bitmap? = (tabInitializer as? FreezableBundleInitializer)
        ?.bundle
        ?.let { bundle ->
            BundleCompat.getParcelable(bundle, TabStateKeys.ENGINE_FAVICON, Bitmap::class.java)
        }

    private var findInPageQuery: String? = null
    private var toggleDesktop: Boolean = false
    private var javaScriptStateToRestore: Boolean? = null
    private var defaultUserAgentMetadata: UserAgentMetadata? = null
    private var capturedDefaultUserAgentMetadata = false
    private val downloadsSubject = PublishSubject.create<PendingDownload>()
    private val focusObservable = BehaviorSubject.createDefault(false)

    private var previewGeneratedTime = System.currentTimeMillis()

    override val id: Int = if (tabInitializer is IdentifiedTabInitializer) {
        latentInitializer = tabInitializer as? FreezableBundleInitializer
        val frozenId = tabInitializer.id.takeIf { it != -1 } ?: viewIdGenerator.generateViewId()
        viewIdGenerator.claimViewId(frozenId)
        frozenId
    } else {
        viewIdGenerator.generateViewId()
    }

    private val webView: WebView
        get() = webViewLazy.value.apply {
            elementPickerController.attach(this)
            webViewClient = tabWebViewClient
            webChromeClient = tabWebChromeClient
            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                if (isRestrictedHomepageDownload()) {
                    Log.w(TAG, "Blocked homepage download: $url")
                    return@setDownloadListener
                }
                if (url.startsWith(BLOB_SCHEME)) {
                    extractBlobDownload(
                        webView = this,
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimetype
                    )
                } else {
                    downloadsSubject.onNext(
                        PendingDownload(
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimetype,
                            contentLength = contentLength,
                            origin = this.url
                        )
                    )
                }
            }
            id = this@TabAdapter.id

            setCompositeOnFocusChangeListener("focus_change") { _, hasFocus ->
                focusObservable.onNext(hasFocus)
            }

            setCompositeTouchListener("focus") { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (!view.hasFocus()) {
                        view.requestFocus()
                    }
                    focusObservable.onNext(true)
                }
                if (event.action == MotionEvent.ACTION_UP && view.isClickable) {
                    view.performClick()
                }
                false
            }
        }

    private fun extractBlobDownload(
        webView: WebView,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val bridgeName = "solipsismBlob_${id}_${System.nanoTime()}"
        val bridge = BlobDownloadBridge(
            onComplete = { data, extractedMimeType, contentLength ->
                webView.post {
                    webView.removeJavascriptInterface(bridgeName)
                    downloadsSubject.onNext(
                        PendingDownload(
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = extractedMimeType ?: mimeType,
                            contentLength = contentLength,
                            origin = webView.url,
                            blobData = data
                        )
                    )
                }
            },
            onErrorCallback = {
                webView.post { webView.removeJavascriptInterface(bridgeName) }
                Log.e(TAG, "Unable to extract blob download: $it")
            }
        )
        webView.addJavascriptInterface(bridge, bridgeName)
        webView.evaluateJavascript(
            """
            (function() {
                fetch(${JSONObject.quote(url)})
                    .then(function(response) { return response.blob(); })
                    .then(function(blob) {
                        if (blob.size > ${MAX_BLOB_BYTES}) {
                            ${bridgeName}.onError('Blob download exceeds the safety limit');
                            return;
                        }
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var result = String(reader.result);
                            var comma = result.indexOf(',');
                            ${bridgeName}.onMetadata(
                                result.substring(5, comma).split(';')[0],
                                blob.size
                            );
                            var data = result.substring(comma + 1);
                            for (var offset = 0; offset < data.length; offset += ${BLOB_CHUNK_SIZE}) {
                                ${bridgeName}.onChunk(data.substring(offset, offset + ${BLOB_CHUNK_SIZE}));
                            }
                            ${bridgeName}.onComplete();
                        };
                        reader.readAsDataURL(blob);
                    })
                    .catch(function(error) { ${bridgeName}.onError(String(error)); });
            })();
            """.trimIndent(),
            null
        )
    }

    private fun isRestrictedHomepageDownload(): Boolean {
        val currentUrl = webView.url.orEmpty()
        return currentUrl.startsWith("https://appassets.androidplatform.net/custom-homepage/") ||
            (userPreferences.homepageSource == HomepageSource.DOMAIN.value &&
                currentUrl == userPreferences.homepage)
    }

    private class BlobDownloadBridge(
        private val onComplete: (String, String?, Long) -> Unit,
        private val onErrorCallback: (String) -> Unit
    ) {
        private val data = StringBuilder()
        private var mimeType: String? = null
        private var contentLength = 0L
        private var finished = false

        @JavascriptInterface
        fun onMetadata(mimeType: String?, contentLength: Long) {
            this.mimeType = mimeType?.takeIf(String::isNotBlank)
            this.contentLength = contentLength
        }

        @JavascriptInterface
        fun onChunk(chunk: String) {
            if (finished) return
            if (chunk.length > MAX_BLOB_BASE64_CHARS - data.length) {
                finished = true
                onErrorCallback("Blob download exceeds the safety limit")
                return
            }
            data.append(chunk)
        }

        @JavascriptInterface
        fun onComplete() {
            if (finished) return
            finished = true
            onComplete(data.toString(), mimeType, contentLength)
        }

        @JavascriptInterface
        fun onError(message: String) {
            if (finished) return
            finished = true
            onErrorCallback(message)
        }
    }

    init {
        applyWebViewUserAgentPreference()
        if (tabInitializer !is FreezableBundleInitializer &&
            tabInitializer.contentKind == TabContentKind.ENGINE
        ) {
            loadFromInitializer(tabInitializer)
        }
    }

    private var previewPath: String? = null
    private val previewPathSingle = previewModel.previewForId(id).cache()

    override fun loadUrl(url: String) {
        latentInitializer = null
        restoredTitle = null
        restoredFavicon = null
        setContentKind(TabContentKind.ENGINE)
        webView.loadUrl(url, requestHeaders)
    }

    override fun loadFromInitializer(tabInitializer: TabInitializer) {
        if (tabInitializer !is FreezableBundleInitializer) {
            restoredTitle = null
            restoredFavicon = null
        }
        latentInitializer = null
        setContentKind(tabInitializer.contentKind)
        if (tabInitializer.contentKind == TabContentKind.NATIVE_HOMEPAGE) return
        tabInitializer.initialize(webView, requestHeaders)
    }

    override fun goBack() {
        if (contentKind == TabContentKind.ENGINE) webView.goBack()
    }

    override fun canGoBack(): Boolean = contentKind == TabContentKind.ENGINE && webView.canGoBack()

    override fun canGoBackChanges(): Observable<Boolean> = Observable.merge(
        tabWebViewClient.goBackObservable.filter { contentKind == TabContentKind.ENGINE },
        contentKindSubject.map { kind -> kind == TabContentKind.ENGINE && webView.canGoBack() },
    )

    override fun goForward() {
        if (contentKind == TabContentKind.ENGINE) webView.goForward()
    }

    override fun canGoForward(): Boolean = contentKind == TabContentKind.ENGINE && webView.canGoForward()

    override fun canGoForwardChanges(): Observable<Boolean> = Observable.merge(
        tabWebViewClient.goForwardObservable.filter { contentKind == TabContentKind.ENGINE },
        contentKindSubject.map { kind -> kind == TabContentKind.ENGINE && webView.canGoForward() },
    )

    override fun toggleDesktopAgent() {
        if (!toggleDesktop) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
            restoreDefaultUserAgentMetadata()
        } else {
            applyWebViewUserAgentPreference()
        }

        toggleDesktop = !toggleDesktop
    }

    override fun applyUserAgentPreference() {
        applyWebViewUserAgentPreference()
        toggleDesktop = false
    }

    private fun applyWebViewUserAgentPreference() {
        val settings = webView.settings
        captureDefaultUserAgentMetadata(settings)
        settings.userAgentString = userPreferences.userAgent(defaultUserAgent)
        if (userPreferences.userAgentChoice == 1 && userPreferences.chrompatibilityModeEnabled) {
            val version = chromiumVersion(defaultUserAgent)
                ?: return restoreDefaultUserAgentMetadata()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                WebSettingsCompat.setUserAgentMetadata(
                    settings,
                    UserAgentMetadata.Builder()
                        .setBrandVersionList(
                            listOf(
                                chromeBrand("Not:A-Brand", "99", "99.0.0.0"),
                                chromeBrand("Google Chrome", version.major, version.full),
                                chromeBrand("Chromium", version.major, version.full),
                            )
                        )
                        .setFullVersion(version.full)
                        .setPlatform("Android")
                        .setMobile(true)
                        .build(),
                )
            }
        } else {
            restoreDefaultUserAgentMetadata()
        }
    }

    private fun captureDefaultUserAgentMetadata(settings: android.webkit.WebSettings) {
        if (capturedDefaultUserAgentMetadata) return
        capturedDefaultUserAgentMetadata = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            defaultUserAgentMetadata = WebSettingsCompat.getUserAgentMetadata(settings)
        }
    }

    private fun restoreDefaultUserAgentMetadata() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
        defaultUserAgentMetadata?.let { WebSettingsCompat.setUserAgentMetadata(webView.settings, it) }
    }

    private fun chromeBrand(
        brand: String,
        major: String,
        full: String,
    ): UserAgentMetadata.BrandVersion = UserAgentMetadata.BrandVersion.Builder()
        .setBrand(brand)
        .setMajorVersion(major)
        .setFullVersion(full)
        .build()

    override fun applyContentBlockingPreferences() = Unit

    // Android WebView derives prefers-color-scheme from this activity's resolved theme.
    override fun applyThemePreference() = Unit

    override fun reload() {
        if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            contentKindSubject.onNext(TabContentKind.NATIVE_HOMEPAGE)
        } else {
            webView.reload()
        }
    }

    override fun reloadWithJavaScriptDisabled() {
        val view = webView
        if (javaScriptStateToRestore != null) return

        javaScriptStateToRestore = view.settings.javaScriptEnabled
        view.settings.javaScriptEnabled = false
        view.reload()
        view.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                view.post {
                    javaScriptStateToRestore?.let { enabled ->
                        view.settings.javaScriptEnabled = enabled
                        javaScriptStateToRestore = null
                    }
                }
            }
        })
    }

    override fun captureVisiblePage(): Bitmap? {
        val view = webView
        if (view.width <= 0 || view.height <= 0 || !view.isLaidOut) return null

        return runCatching {
            createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                view.draw(Canvas(bitmap))
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to capture visible page", error)
        }.getOrNull()
    }

    override fun pickElement() {
        elementPickerController.start(webView, url)
    }

    override fun stopLoading() {
        webView.stopLoading()
    }

    override fun find(query: String) {
        webView.findAllAsync(query)
        findInPageQuery = query
    }

    override fun findNext() {
        webView.findNext(true)
    }

    override fun findPrevious() {
        webView.findNext(false)
    }

    override fun clearFindMatches() {
        webView.clearMatches()
        findInPageQuery = null
    }

    override fun readPageText(onText: (String) -> Unit) {
        val script = """
            (function() {
                var body = document.body;
                if (!body) return '';
                return body.innerText || body.textContent || '';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            val text = runCatching { JSONTokener(result).nextValue() as? String }
                .getOrNull()
                .orEmpty()
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_READ_ALOUD_CHARACTERS)
            onText(text)
        }
    }

    override val preview: Pair<String?, Long>
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            null to previewGeneratedTime
        } else {
            previewPath to previewGeneratedTime
        }

    override fun previewChanges(): Observable<Pair<String?, Long>> = Observable.merge(
        enginePreviewChanges().filter { contentKind == TabContentKind.ENGINE },
        contentKindSubject.map { preview },
    )

    private fun enginePreviewChanges(): Observable<Pair<String?, Long>> =
        tabWebViewClient.finishedObservable
            .debounce(100, TimeUnit.MILLISECONDS)
            .observeOn(mainScheduler)
            .mapOptional { Optional.ofNullable(renderViewToBitmap(webView)) }
            .observeOn(diskScheduler)
            .flatMapSingle { bitmap ->
                previewModel.cachePreviewForId(id, bitmap)
                    .andThen(previewPathSingle)
                    .map<Pair<String?, Long>> { path -> path to System.currentTimeMillis() }
            }
            .startWith(
                previewPathSingle.ignoreElement()
                    .andThen(previewPathSingle)
                    .map { path -> path to System.currentTimeMillis() }
            )
            .doOnNext { (path, time) ->
                previewPath = path
                previewGeneratedTime = time
            }
            .observeOn(mainScheduler)

    override val findQuery: String?
        get() = findInPageQuery

    override val favicon: Bitmap?
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            null
        } else {
            restoredFavicon
                ?: latentInitializer?.let { iconFreeze }
                ?: tabWebChromeClient.faviconObservable.value?.value()
        }

    override fun faviconChanges(): Observable<Option<Bitmap>> = Observable.merge(
        tabWebChromeClient.faviconObservable
            .filter { contentKind == TabContentKind.ENGINE }
            .map { receivedIcon ->
                receivedIcon.value()?.let { icon ->
                    restoredFavicon = icon
                    Option.Some(icon)
                } ?: Option.fromNullable(restoredFavicon)
            },
        contentKindSubject.map { kind ->
            if (kind == TabContentKind.NATIVE_HOMEPAGE) Option.None else Option.fromNullable(favicon)
        },
    )

    override val themeColor: Int
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            Color.TRANSPARENT
        } else {
            requireNotNull(tabWebChromeClient.colorChangeObservable.value)
        }

    override fun themeColorChanges(): Observable<Int> = Observable.merge(
        tabWebChromeClient.colorChangeObservable.filter { contentKind == TabContentKind.ENGINE },
        contentKindSubject.map { themeColor },
    )

    override val url: String
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            SCHEME_HOMEPAGE
        } else {
            webView.url.orEmpty()
        }

    override fun urlChanges(): Observable<String> = Observable.merge(
        tabWebViewClient.urlObservable.filter { contentKind == TabContentKind.ENGINE },
        contentKindSubject.map { kind ->
            if (kind == TabContentKind.NATIVE_HOMEPAGE) SCHEME_HOMEPAGE else webView.url.orEmpty()
        },
    )

    override val title: String
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            webView.context.getString(R.string.app_name)
        } else {
            restoredOrWebViewTitle()
        }

    override fun titleChanges(): Observable<String> = Observable.merge(
        tabWebChromeClient.titleObservable
            .filter { contentKind == TabContentKind.ENGINE }
            .map { receivedTitle ->
                receivedTitle.takeIf(String::isNotBlank)?.also { restoredTitle = it }
                    ?: restoredOrWebViewTitle()
            },
        contentKindSubject.map { kind ->
            if (kind == TabContentKind.NATIVE_HOMEPAGE) {
                webView.context.getString(R.string.app_name)
            } else {
                restoredOrWebViewTitle()
            }
        },
    )

    override val sslCertificateInfo: SslCertificateInfo?
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) null else webView.certificate?.let {
            SslCertificateInfo(
                issuedByCommonName = it.issuedBy.cName,
                issuedToCommonName = it.issuedTo.cName,
                issuedToOrganizationName = it.issuedTo.oName,
                issueDate = it.validNotBeforeDate,
                expireDate = it.validNotAfterDate,
                sslState = sslState
            )
        }

    override val sslState: SslState
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) {
            SslState.None
        } else {
            tabWebViewClient.sslState
        }

    override fun sslChanges(): Observable<SslState> = Observable.merge(
        tabWebViewClient.sslStateObservable.filter { contentKind == TabContentKind.ENGINE },
        contentKindSubject.map { sslState },
    )

    override val loadingProgress: Int
        get() = if (contentKind == TabContentKind.NATIVE_HOMEPAGE) 100 else webView.progress

    override fun loadingProgress(): Observable<Int> = Observable.merge(
        tabWebChromeClient.progressObservable.filter { contentKind == TabContentKind.ENGINE },
        contentKindSubject.map { kind -> if (kind == TabContentKind.NATIVE_HOMEPAGE) 100 else webView.progress },
    )

    override fun downloadRequests(): Observable<PendingDownload> = downloadsSubject.hide()

    override fun fileChooserRequests(): Observable<Intent> =
        tabWebChromeClient.fileChooserObservable.hide()

    override fun handleFileChooserResult(activityResult: ActivityResult) {
        tabWebChromeClient.onResult(activityResult)
    }

    override fun showCustomViewRequests(): Observable<View> =
        tabWebChromeClient.showCustomViewObservable.hide()

    override fun hideCustomViewRequests(): Observable<Unit> =
        tabWebChromeClient.hideCustomViewObservable.hide()

    override fun hideCustomView() {
        tabWebChromeClient.hideCustomView()
    }

    override fun createWindowRequests(): Observable<TabInitializer> =
        tabWebChromeClient.createWindowObservable.hide()

    override fun closeWindowRequests(): Observable<Unit> =
        tabWebChromeClient.closeWindowObservable.hide()

    override var isForeground: Boolean = false
        set(value) {
            field = value
            if (field && engineContentVisible) {
                webView.onResume()
                webView.settings.offscreenPreRaster = true
                latentInitializer?.let(::loadFromInitializer)
                latentInitializer = null
            } else {
                webView.onPause()
                webView.settings.offscreenPreRaster = false
            }
        }

    override fun setContentVisible(visible: Boolean) {
        engineContentVisible = visible
        webView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (visible && isForeground) {
            webView.onResume()
        } else {
            webView.onPause()
        }
    }

    override val hasFocus: Boolean
        get() = webView.hasFocus()

    override fun hasFocusChanges(): Observable<Boolean> = focusObservable.hide()

    override fun destroy() {
        viewIdGenerator.releaseViewId(id)
        previewModel.prune()
        webView.stopLoading()
        webView.onPause()
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    override fun freeze(): Bundle = (latentInitializer?.bundle
        ?: Bundle(ClassLoader.getSystemClassLoader()).also(webView::saveState)).apply {
        putString(TabStateKeys.ENGINE_URL, url)
        putString(TabStateKeys.ENGINE_TITLE, title)
        favicon?.let { putParcelable(TabStateKeys.ENGINE_FAVICON, it) }
            ?: remove(TabStateKeys.ENGINE_FAVICON)
        putString(TabStateKeys.CONTENT_KIND, contentKind.name)
    }

    private fun restoredOrWebViewTitle(): String =
        webView.title?.takeIf(String::isNotBlank)
            ?: restoredTitle
            ?: latentInitializer?.initialTitle?.takeIf(String::isNotBlank)
            ?: defaultTabTitle

    private fun setContentKind(kind: TabContentKind) {
        if (contentKindSubject.value == kind) {
            if (kind == TabContentKind.NATIVE_HOMEPAGE) contentKindSubject.onNext(kind)
            return
        }
        if (kind == TabContentKind.NATIVE_HOMEPAGE) setContentVisible(false)
        contentKindSubject.onNext(kind)
    }

    private fun renderViewToBitmap(
        view: View,
        width: Int = view.width,
        height: Int = view.height
    ): Bitmap? {
        // Ensure the view has been laid out
        if (width == 0 || height == 0) {
            return null
        }

        // Create a Bitmap with the specified dimensions and ARGB_8888 configuration
        val bitmap = createBitmap(width / 3, height / 3)

        // Create a Canvas to draw on the Bitmap
        val canvas = Canvas(bitmap)

        canvas.scale(0.33F, 0.33F)

        canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())

        // Layout the view if it hasn't been laid out yet
        view.layout(0, 0, width, height)

        // Draw the view onto the canvas
        view.draw(canvas)

        return bitmap
    }

    companion object {
        private const val TAG = "TabAdapter"
        private const val BLOB_SCHEME = "blob:"
        private const val BLOB_CHUNK_SIZE = 32 * 1024
        private const val MAX_BLOB_BYTES = 16L * 1024L * 1024L
        private const val MAX_BLOB_BASE64_CHARS = ((MAX_BLOB_BYTES + 2L) / 3L * 4L).toInt()
        private const val MAX_READ_ALOUD_CHARACTERS = 120_000
    }
}
