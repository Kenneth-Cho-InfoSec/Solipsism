package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.adblock.AdBlocker
import com.krystelligence.solipsism.adblock.allowlist.AllowListModel
import com.krystelligence.solipsism.adblock.custom.CosmeticFilterRuntime
import com.krystelligence.solipsism.adblock.custom.CustomFilterRepository
import com.krystelligence.solipsism.databinding.DialogAuthRequestBinding
import com.krystelligence.solipsism.databinding.DialogSslWarningBinding
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.js.TextReflow
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.ssl.SslState
import com.krystelligence.solipsism.ssl.SslWarningPreferences
import com.krystelligence.solipsism.userscript.UserScriptRuntime
import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.LayoutInflater
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import kotlin.math.abs

/**
 * A [WebViewClient] that supports the tab adaptation.
 */
class TabWebViewClient @AssistedInject constructor(
    private val application: Application,
    private val adBlocker: AdBlocker,
    private val customFilterRepository: CustomFilterRepository,
    private val cosmeticFilterRuntime: CosmeticFilterRuntime,
    private val allowListModel: AllowListModel,
    private val urlHandler: UrlHandler,
    @Assisted private val headers: Map<String, String>,
    private val userPreferences: UserPreferences,
    private val sslWarningPreferences: SslWarningPreferences,
    private val textReflow: TextReflow,
    private val userScriptRuntime: UserScriptRuntime,
    private val sitePermissionRuntime: SitePermissionRuntime,
    private val logger: Logger,
    @Assisted("cache") private val cacheStoragePathHandler: InternalStoragePathHandler,
    @Assisted("files") private val filesStoragePathHandler: InternalStoragePathHandler,
) : WebViewClient() {

    private val cache by lazy {
        File(application.cacheDir, "favicon-cache")
    }

    private val files by lazy {
        File(application.filesDir, "generated-html")
    }

    /**
     * Emits changes to the current URL.
     */
    val urlObservable: PublishSubject<String> = PublishSubject.create()

    /**
     * Emits changes to the current SSL state.
     */
    val sslStateObservable: PublishSubject<SslState> = PublishSubject.create()

    /**
     * Emits changes to the can go back state of the browser.
     */
    val goBackObservable: PublishSubject<Boolean> = PublishSubject.create()

    /**
     * Emits changes to the can go forward state of the browser.
     */
    val goForwardObservable: PublishSubject<Boolean> = PublishSubject.create()

    /**
     * Emit when the tab has finished rendering its content.
     */
    val finishedObservable = PublishSubject.create<Unit>()

    /**
     * The current SSL state of the page.
     */
    var sslState: SslState = SslState.None
        private set

    private var currentUrl: String = ""
    private var isReflowRunning: Boolean = false
    private var zoomScale: Float = 0.0F
    private var urlWithSslError: String? = null

    private fun shouldBlockRequest(pageUrl: String, requestUrl: String) =
        !allowListModel.isUrlAllowedAds(pageUrl) &&
            (adBlocker.isAd(requestUrl, pageUrl) ||
                customFilterRepository.shouldBlockNetwork(requestUrl) ||
                (userPreferences.blockGifImagesEnabled && requestUrl.isGifResource()))

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentUrl = url
        urlObservable.onNext(url)
        if (urlWithSslError != url) {
            urlWithSslError = null
            sslState = if (URLUtil.isHttpsUrl(url)) {
                SslState.Valid
            } else {
                SslState.None
            }
        }
        sslStateObservable.onNext(sslState)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        userScriptRuntime.injectAfterPageFinished(view, url)
        cosmeticFilterRuntime.injectAfterPageFinished(view, url)
        sitePermissionRuntime.injectAfterPageFinished(view, url)
        urlObservable.onNext(url)
        goBackObservable.onNext(view.canGoBack())
        goForwardObservable.onNext(view.canGoForward())
        view.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                finishedObservable.onNext(Unit)
            }
        })
        if (userPreferences.adBlockEnabled && userPreferences.uBlockOriginEnabled && url.isYouTubeUrl()) {
            view.evaluateJavascript(YOUTUBE_AD_SCRIPT, null)
        }
    }


    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        if (view.isShown && userPreferences.textReflowEnabled) {
            if (isReflowRunning)
                return
            val changeInPercent = abs(100 - 100 / zoomScale * newScale)
            if (changeInPercent > 2.5f && !isReflowRunning) {
                isReflowRunning = view.postDelayed({
                    zoomScale = newScale
                    view.evaluateJavascript(textReflow.provideJs()) { isReflowRunning = false }
                }, 100)
            }

        }
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String
    ) {
        val context = view.context
        MaterialAlertDialogBuilder(context).apply {
            val dialogView = DialogAuthRequestBinding.inflate(LayoutInflater.from(context))

            val realmLabel = dialogView.authRequestRealmTextview
            val name = dialogView.authRequestUsernameEdittext
            val password = dialogView.authRequestPasswordEdittext

            realmLabel.text = context.getString(R.string.label_realm, realm)

            setView(dialogView.root)
            setTitle(R.string.title_sign_in)
            setCancelable(true)
            setPositiveButton(R.string.title_sign_in) { _, _ ->
                val user = name.text.toString()
                val pass = password.text.toString()
                handler.proceed(user.trim(), pass.trim())
                logger.log(TAG, "Attempting HTTP Authentication")
            }
            setNegativeButton(R.string.action_cancel) { _, _ ->
                handler.cancel()
            }
        }.resizeAndShow()
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        val context = view.context
        MaterialAlertDialogBuilder(context).apply {
            setTitle(context.getString(R.string.title_form_resubmission))
            setMessage(context.getString(R.string.message_form_resubmission))
            setCancelable(true)
            setPositiveButton(context.getString(R.string.action_yes)) { _, _ ->
                resend.sendToTarget()
            }
            setNegativeButton(context.getString(R.string.action_no)) { _, _ ->
                dontResend.sendToTarget()
            }
        }.resizeAndShow()
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(webView: WebView, handler: SslErrorHandler, error: SslError) {
        val context = webView.context
        urlWithSslError = webView.url

        sslState = SslState.Invalid(error)
        sslStateObservable.onNext(sslState)
        sslState = SslState.Invalid(error)

        when (sslWarningPreferences.recallBehaviorForDomain(webView.url)) {
            SslWarningPreferences.Behavior.PROCEED -> return handler.proceed()
            SslWarningPreferences.Behavior.CANCEL -> return handler.cancel()
            null -> Unit
        }

        val errorCodeMessageCodes = error.getAllSslErrorMessageCodes()

        val stringBuilder = StringBuilder()
        for (messageCode in errorCodeMessageCodes) {
            stringBuilder.append(" - ").append(context.getString(messageCode)).append('\n')
        }
        val alertMessage =
            context.getString(R.string.message_insecure_connection, stringBuilder.toString())

        MaterialAlertDialogBuilder(context).apply {
            val view = DialogSslWarningBinding.inflate(LayoutInflater.from(context))
            val dontAskAgain = view.checkBoxDontAskAgain
            setTitle(context.getString(R.string.title_warning))
            setMessage(alertMessage)
            setCancelable(true)
            setView(view.root)
            setOnCancelListener { handler.cancel() }
            setPositiveButton(context.getString(R.string.action_yes)) { _, _ ->
                handler.proceed()
            }
            setNegativeButton(context.getString(R.string.action_no)) { _, _ ->
                if (dontAskAgain.isChecked) {
                    sslWarningPreferences.rememberBehaviorForDomain(
                        webView.url.orEmpty(),
                        SslWarningPreferences.Behavior.CANCEL
                    )
                }
                handler.cancel()
            }
        }.resizeAndShow()
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return urlHandler.shouldOverrideLoading(view, url, headers) ||
            super.shouldOverrideUrlLoading(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return urlHandler.shouldOverrideLoading(view, request.url.toString(), headers) ||
            super.shouldOverrideUrlLoading(view, request)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (shouldBlockRequest(currentUrl, request.url.toString())) {
            val empty = ByteArrayInputStream(emptyResponseByteArray)
            return WebResourceResponse(BLOCKED_RESPONSE_MIME_TYPE, BLOCKED_RESPONSE_ENCODING, empty)
        }
        return if (request.url.path?.startsWith(files.path) == true) {
            filesStoragePathHandler.handle(request.url.path!!.substring(files.path.length))
        } else if (request.url.path?.startsWith(cache.path) == true) {
            cacheStoragePathHandler.handle(request.url.path!!.substring(cache.path.length))
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

    private fun SslError.getAllSslErrorMessageCodes(): List<Int> {
        val errorCodeMessageCodes = ArrayList<Int>(1)

        if (hasError(SslError.SSL_DATE_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_date_invalid)
        }
        if (hasError(SslError.SSL_EXPIRED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_expired)
        }
        if (hasError(SslError.SSL_IDMISMATCH)) {
            errorCodeMessageCodes.add(R.string.message_certificate_domain_mismatch)
        }
        if (hasError(SslError.SSL_NOTYETVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_not_yet_valid)
        }
        if (hasError(SslError.SSL_UNTRUSTED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_untrusted)
        }
        if (hasError(SslError.SSL_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_invalid)
        }

        return errorCodeMessageCodes
    }

    /**
     * The factory for constructing the client.
     */
    @AssistedFactory
    interface Factory {

        /**
         * Create the client.
         */
        fun create(
            headers: Map<String, String>,
            @Assisted("cache") cacheStoragePathHandler: InternalStoragePathHandler,
            @Assisted("files") filesStoragePathHandler: InternalStoragePathHandler,
        ): TabWebViewClient
    }

    companion object {
        private const val TAG = "TabWebViewClient"

        private val emptyResponseByteArray: ByteArray = byteArrayOf()

        private const val BLOCKED_RESPONSE_MIME_TYPE = "text/plain"
        private const val BLOCKED_RESPONSE_ENCODING = "utf-8"

        private fun String.isGifResource(): Boolean = runCatching {
            URI(this).path?.lowercase()?.endsWith(".gif") == true
        }.getOrDefault(false)

        private fun String.isYouTubeUrl(): Boolean = try {
            val host = URI(this).host?.lowercase().orEmpty()
            host == "youtube.com" ||
                host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" ||
                host.endsWith(".youtube-nocookie.com")
        } catch (exception: Exception) {
            false
        }

        private const val YOUTUBE_AD_SCRIPT = """
            (function () {
                'use strict';
                if (window.__solipsismUboYoutube) {
                    return;
                }
                window.__solipsismUboYoutube = true;
                var removeSelectors = [
                    '.ytp-ad-module',
                    '.video-ads',
                    'ytd-ad-slot-renderer',
                    'ytd-promoted-sparkles-web-renderer',
                    'ytd-display-ad-renderer',
                    'ytd-companion-slot-renderer',
                    'ytd-action-companion-ad-renderer',
                    'ytd-in-feed-ad-layout-renderer',
                    'ytd-ad-inline-playback-renderer',
                    '#player-ads',
                    '#masthead-ad'
                ];
                var skipSelectors = [
                    '.ytp-ad-skip-button',
                    '.ytp-ad-skip-button-modern',
                    '.ytp-skip-ad-button',
                    'button[class*="skip"][class*="ad"]'
                ];
                function visible(element) {
                    return element && element.offsetParent !== null;
                }
                function removeAds() {
                    removeSelectors.forEach(function (selector) {
                        document.querySelectorAll(selector).forEach(function (element) {
                            element.remove();
                        });
                    });
                }
                function skipAds() {
                    skipSelectors.forEach(function (selector) {
                        document.querySelectorAll(selector).forEach(function (button) {
                            if (visible(button)) {
                                button.click();
                            }
                        });
                    });
                    var player = document.querySelector('.html5-video-player');
                    var video = document.querySelector('video');
                    if (player && video && player.classList.contains('ad-showing')) {
                        video.muted = true;
                        video.playbackRate = 16;
                        if (isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = Math.max(video.currentTime, video.duration - 0.1);
                        }
                    }
                }
                function tick() {
                    removeAds();
                    skipAds();
                }
                tick();
                setInterval(tick, 250);
                new MutationObserver(tick).observe(document.documentElement, {
                    childList: true,
                    subtree: true
                });
            }());
        """
    }
}
