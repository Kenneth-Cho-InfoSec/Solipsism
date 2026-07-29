package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.browser.di.DiskScheduler
import com.krystelligence.solipsism.adblock.custom.ElementPickerController
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.download.PendingDownload
import com.krystelligence.solipsism.browser.image.IconFreeze
import com.krystelligence.solipsism.browser.view.setCompositeOnFocusChangeListener
import com.krystelligence.solipsism.browser.view.setCompositeTouchListener
import com.krystelligence.solipsism.constant.DESKTOP_USER_AGENT
import com.krystelligence.solipsism.ids.ViewIdGenerator
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.html.homepage.HomepageSource
import com.krystelligence.solipsism.preference.userAgent
import com.krystelligence.solipsism.preview.PreviewModel
import com.krystelligence.solipsism.ssl.SslCertificateInfo
import com.krystelligence.solipsism.ssl.SslState
import com.krystelligence.solipsism.utils.Option
import com.krystelligence.solipsism.utils.value
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.JavascriptInterface
import android.util.Log
import org.json.JSONObject
import androidx.activity.result.ActivityResult
import androidx.core.graphics.createBitmap
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
@SuppressLint("ClickableViewAccessibility")
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

    private var findInPageQuery: String? = null
    private var toggleDesktop: Boolean = false
    private var javaScriptStateToRestore: Boolean? = null
    private val downloadsSubject = PublishSubject.create<PendingDownload>()
    private val focusObservable = BehaviorSubject.createDefault(false)

    private var previewGeneratedTime = System.currentTimeMillis()

    override val id: Int = if (tabInitializer is FreezableBundleInitializer) {
        latentInitializer = tabInitializer
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
                            contentLength = contentLength
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
                            blobData = data
                        )
                    )
                }
            },
            onError = {
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
        private val onError: (String) -> Unit
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
            if (!finished) data.append(chunk)
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
            onError(message)
        }
    }

    init {
        if (tabInitializer !is FreezableBundleInitializer) {
            loadFromInitializer(tabInitializer)
        }
    }

    private var previewPath: String? = null
    private val previewPathSingle = previewModel.previewForId(id).cache()

    override fun loadUrl(url: String) {
        webView.loadUrl(url, requestHeaders)
    }

    override fun loadFromInitializer(tabInitializer: TabInitializer) {
        tabInitializer.initialize(webView, requestHeaders)
    }

    override fun goBack() {
        webView.goBack()
    }

    override fun canGoBack(): Boolean = webView.canGoBack()

    override fun canGoBackChanges(): Observable<Boolean> = tabWebViewClient.goBackObservable.hide()

    override fun goForward() {
        webView.goForward()
    }

    override fun canGoForward(): Boolean = webView.canGoForward()

    override fun canGoForwardChanges(): Observable<Boolean> =
        tabWebViewClient.goForwardObservable.hide()

    override fun toggleDesktopAgent() {
        if (!toggleDesktop) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
        } else {
            webView.settings.userAgentString = userPreferences.userAgent(defaultUserAgent)

        }

        toggleDesktop = !toggleDesktop
    }

    override fun reload() {
        webView.reload()
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
        if (view.width <= 0 || view.height <= 0) return null

        return runCatching {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                view.draw(Canvas(bitmap))
            }
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

    override val preview: Pair<String?, Long>
        get() = previewPath to previewGeneratedTime

    override fun previewChanges(): Observable<Pair<String?, Long>> =
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
        get() = latentInitializer?.let { iconFreeze }
            ?: tabWebChromeClient.faviconObservable.value?.value()

    override fun faviconChanges(): Observable<Option<Bitmap>> = tabWebChromeClient.faviconObservable

    override val themeColor: Int
        get() = requireNotNull(tabWebChromeClient.colorChangeObservable.value)

    override fun themeColorChanges(): Observable<Int> = tabWebChromeClient.colorChangeObservable

    override val url: String
        get() = webView.url.orEmpty()

    override fun urlChanges(): Observable<String> = tabWebViewClient.urlObservable.hide()

    override val title: String
        get() = latentInitializer?.initialTitle ?: webView.title?.takeIf(String::isNotBlank)
        ?: defaultTabTitle

    override fun titleChanges(): Observable<String> = tabWebChromeClient.titleObservable.hide()

    override val sslCertificateInfo: SslCertificateInfo?
        get() = webView.certificate?.let {
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
        get() = tabWebViewClient.sslState

    override fun sslChanges(): Observable<SslState> = tabWebViewClient.sslStateObservable.hide()

    override val loadingProgress: Int
        get() = webView.progress

    override fun loadingProgress(): Observable<Int> = tabWebChromeClient.progressObservable.hide()

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
            if (field) {
                webView.onResume()
                webView.settings.offscreenPreRaster = true
                latentInitializer?.let(::loadFromInitializer)
                latentInitializer = null
            } else {
                webView.settings.offscreenPreRaster = false
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

    override fun freeze(): Bundle = latentInitializer?.bundle
        ?: Bundle(ClassLoader.getSystemClassLoader()).also(webView::saveState)

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
    }
}
