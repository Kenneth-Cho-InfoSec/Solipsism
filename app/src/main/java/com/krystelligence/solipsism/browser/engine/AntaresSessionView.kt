package com.krystelligence.solipsism.browser.engine

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import org.servo.servoview.Servo
import org.servo.servoview.ServoView

/**
 * In-process Antares renderer. The native Servo surface is now owned directly by Solipsism,
 * eliminating the remote service and SurfaceControl/Binder hop used by earlier builds.
 */
class AntaresSessionView(
    context: Context,
    initialUrl: String,
    initialUserAgent: String,
    initialTheme: Int,
    private val contentBlockingPolicy: AntaresContentBlockingPolicy,
    initialBlockAds: Boolean,
    initialBlockGifs: Boolean,
    private val listener: Listener,
) : ServoView(context), Servo.Client {
    private var requestedUrl = initialUrl
    private val installMediaBridge = Runnable {
        evaluateJavascript(AntaresHtmlMediaBridge.installScript)
    }
    private val installCssCompatibilityBridge = Runnable {
        evaluateJavascript(AntaresCssCompatibilityBridge.installScript)
    }
    interface Listener {
        fun onReady()
        fun onLoadStarted()
        fun onLoadEnded()
        fun onTitleChanged(title: String)
        fun onUrlChanged(url: String)
        fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean)
        fun onAlert(message: String)
        fun onMediaRequest(request: Bundle)
        fun onElementProbeResult(requestId: Int, descriptor: String)
        fun onEngineError(message: String)
    }

    init {
        setClient(this)
        setServoArgs(null, null, true)
        setHostManagedInputMethod(true)
        setUserAgent(initialUserAgent)
        setTheme(initialTheme != AntaresProtocol.THEME_LIGHT)
        setContentBlocking(initialBlockAds, initialBlockGifs, contentBlockingPolicy.readText())
        loadUri(initialUrl)
    }

    override fun onAlert(message: String) = listener.onAlert(message)

    override fun onLoadStarted() {
        rendererReady = true
        listener.onLoadStarted()
    }

    override fun onLoadEnded() {
        scheduleMediaBridgeInstall()
        scheduleCssCompatibilityBridgeInstall()
        listener.onLoadEnded()
    }

    override fun onTitleChanged(title: String) {
        val request = AntaresHtmlMediaBridge.decodeTitle(title)
        if (request != null) {
            Log.d(LOG_TAG, "Forwarding HTML media request to Android media playback")
            listener.onMediaRequest(request)
        } else {
            listener.onTitleChanged(title)
        }
    }

    override fun onUrlChanged(url: String) {
        scheduleMediaBridgeInstall()
        scheduleCssCompatibilityBridgeInstall()
        listener.onUrlChanged(url)
    }

    override fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
        listener.onHistoryChanged(canGoBack, canGoForward)

    override fun onImeShow() = Unit

    override fun onImeHide() = Unit

    override fun onMediaSessionMetadata(title: String, artist: String, album: String) = Unit

    override fun onMediaSessionPlaybackStateChange(state: Int) = Unit

    override fun onMediaSessionSetPositionState(
        duration: Float,
        position: Float,
        playbackRate: Float,
    ) = Unit

    fun relayTouchEvent(event: MotionEvent): Boolean = onTouchEvent(event)

    fun loadUrl(url: String) {
        requestedUrl = url
        navigateShared(url)
    }

    override fun activateForTab(url: String) {
        requestedUrl = url
        if (rendererReady) super.activateForTab(url)
    }

    fun stopLoading() = stop()

    fun onHostResumed() = onResume()

    fun setForeground(value: Boolean) {
        if (value) onResume() else onPause()
    }

    fun setContentVisible(value: Boolean) {
        visibility = if (value) VISIBLE else INVISIBLE
    }

    fun setBrowserChromeOverlayVisible(visible: Boolean, onApplied: () -> Unit = {}) {
        if (visible) clearFocus()
        onApplied()
    }

    fun setTheme(value: Int) = setTheme(value != AntaresProtocol.THEME_LIGHT)

    fun setContentBlocking(blockAds: Boolean, blockGifs: Boolean) =
        setContentBlocking(blockAds, blockGifs, contentBlockingPolicy.readText())

    fun probeElement(requestId: Int, x: Float, y: Float) {
        evaluateJavascript(AntaresCoordinateProbe.script(requestId, x, y))
    }

    fun destroySession() {
        removeCallbacks(installMediaBridge)
        removeCallbacks(installCssCompatibilityBridge)
        onPause()
    }

    private fun scheduleMediaBridgeInstall() {
        removeCallbacks(installMediaBridge)
        postDelayed(installMediaBridge, MEDIA_BRIDGE_INSTALL_DELAY_MS)
    }

    private fun scheduleCssCompatibilityBridgeInstall() {
        removeCallbacks(installCssCompatibilityBridge)
        postDelayed(installCssCompatibilityBridge, CSS_BRIDGE_INSTALL_DELAY_MS)
    }

    private companion object {
        private const val LOG_TAG = "AntaresMediaBridge"
        private const val MEDIA_BRIDGE_INSTALL_DELAY_MS = 500L
        private const val CSS_BRIDGE_INSTALL_DELAY_MS = 600L
        @Volatile
        private var rendererReady = false
    }
}
