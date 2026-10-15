package com.krystelligence.solipsism.browser.engine

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
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
    private val installConsoleBridge = Runnable {
        evaluateJavascript(AntaresConsoleBridge.installScript)
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
        fun onConsoleSignal(signal: ConsoleSignal)
        fun onConsoleMessage(level: Int, message: String)
        fun onEngineError(message: String)
    }

    /**
     * Console entry decoded from the title channel. Plain data so the public
     * listener never exposes the internal bridge type.
     */
    data class ConsoleSignal(
        val level: String,
        val message: String,
        val source: String,
        val line: Int
    )

    init {
        // A native crash below leaves this set so the next launch falls back (#49).
        BrowserCorePreferences(context).armAntaresCrashGuard()
        setClient(this)
        setServoArgs(null, null, true)
        setHostManagedInputMethod(true)
        setUserAgent(initialUserAgent)
        setTheme(initialTheme != AntaresProtocol.THEME_LIGHT)
        setContentBlocking(initialBlockAds, initialBlockGifs, contentBlockingPolicy.readText())
        loadUri(initialUrl)
    }

    override fun onAlert(message: String) = listener.onAlert(message)

    override fun onConsoleMessage(level: Int, message: String) =
        listener.onConsoleMessage(level, message)

    fun evaluateForConsole(script: String, onResult: (String) -> Unit) =
        evaluateJavascript(script, onResult)

    override fun onLoadStarted() {
        rendererReady = true
        // Console wrapper must install as early as possible: page scripts run
        // during parsing, so installing only onLoadEnded would miss them all.
        // The install is idempotent; the later onLoadEnded pass is backstop.
        scheduleConsoleBridgeInstall()
        listener.onLoadStarted()
    }

    override fun onLoadEnded() {
        scheduleMediaBridgeInstall()
        scheduleCssCompatibilityBridgeInstall()
        scheduleConsoleBridgeInstall()
        listener.onLoadEnded()
    }

    override fun onTitleChanged(title: String) {
        AntaresConsoleBridge.decodeTitle(title)?.let { decoded ->
            Log.d(LOG_TAG, "Console signal decoded: ${decoded.level} ${decoded.message.take(60)}")
            listener.onConsoleSignal(
                ConsoleSignal(
                    level = decoded.level,
                    message = decoded.message,
                    source = decoded.source,
                    line = decoded.line
                )
            )
            return
        }
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
        scheduleConsoleBridgeInstall()
        listener.onUrlChanged(url)
    }

    override fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
        listener.onHistoryChanged(canGoBack, canGoForward)

    override fun onImeShow() {
        // The engine reports that a page text field gained focus. This view owns the input
        // method (see setHostManagedInputMethod), so explicitly request focus and raise the
        // soft keyboard. Previously these callbacks were no-ops, which left page inputs
        // unfocusable with no keyboard on DuckDuckGo, Gemini and similar sites.
        if (!isFocused) requestFocus()
        context.getSystemService<InputMethodManager>()
            ?.showSoftInput(this, 0)
    }

    override fun onImeHide() {
        context.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection =
        AntaresInputConnection(this, true).apply {
            outAttrs.actionLabel = null
            outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or
                EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_FULLSCREEN
        }

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
        removeCallbacks(installConsoleBridge)
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

    private fun scheduleConsoleBridgeInstall() {
        removeCallbacks(installConsoleBridge)
        postDelayed(installConsoleBridge, CONSOLE_BRIDGE_INSTALL_DELAY_MS)
    }

    private companion object {
        private const val LOG_TAG = "AntaresMediaBridge"
        private const val MEDIA_BRIDGE_INSTALL_DELAY_MS = 500L
        private const val CSS_BRIDGE_INSTALL_DELAY_MS = 600L
        private const val CONSOLE_BRIDGE_INSTALL_DELAY_MS = 650L
        @Volatile
        private var rendererReady = false
    }
}

/**
 * Forwards Android soft-keyboard edits to the embedded Antares renderer. The engine exposes a
 * deliberately narrow text protocol ([ServoView.commitText] for insertions plus raw key events),
 * so composing text is committed directly and deletions are delivered as backspace keys.
 */
private class AntaresInputConnection(
    private val sessionView: AntaresSessionView,
    fullEditor: Boolean,
) : BaseInputConnection(sessionView, fullEditor) {

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (text.isNotEmpty()) sessionView.commitText(text.toString())
        return true
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean =
        commitText(text, newCursorPosition)

    override fun finishComposingText(): Boolean = true

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        repeat(beforeLength.coerceAtLeast(0)) {
            sessionView.sendKey(KeyEvent.KEYCODE_DEL)
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            sessionView.sendKey(event.keyCode)
        }
        return true
    }

    override fun performEditorAction(editorAction: Int): Boolean {
        if (editorAction == EditorInfo.IME_ACTION_DONE ||
            editorAction == EditorInfo.IME_ACTION_GO ||
            editorAction == EditorInfo.IME_ACTION_SEARCH ||
            editorAction == EditorInfo.IME_ACTION_SEND
        ) {
            sessionView.context.getSystemService<InputMethodManager>()
                ?.hideSoftInputFromWindow(sessionView.windowToken, 0)
            return true
        }
        return super.performEditorAction(editorAction)
    }
}
