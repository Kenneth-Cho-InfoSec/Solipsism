/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.servo.servoview

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.Surface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Servo(
    args: String?,
    url: String?,
    size: Size,
    density: Float,
    logStr: String?,
    experimentalMode: Boolean,
    userAgent: String,
    darkTheme: Boolean,
    blockAds: Boolean,
    blockGifs: Boolean,
    contentBlockingPolicy: String,
    private val runCallback: RunCallback,
    client: Client,
    context: Context,
    surface: Surface,
) {
    private val jni = JNIServo()
    private val servoCallbacks = Callbacks(client, jni, runCallback)
    private val frameQueued = AtomicBoolean(false)
    private val enginePanicCount = AtomicInteger(0)
    private val rendererUnhealthy = AtomicBoolean(false)
    private val evalCallbacks = ConcurrentHashMap<Int, (String) -> Unit>()
    private val nextEvalRequestId = AtomicInteger(1)

    private fun runOnEngine(action: () -> Unit) {
        runCallback.inGLThread {
            action()
            if (!servoCallbacks.suspended && jni.needsVsync()) {
                runCallback.requestVsync()
            }
        }
    }

    init {
        runOnEngine {
            // Configure filtering before init consumes the initial URL. Applying it afterwards
            // permits the first document's early subresources to escape the policy.
            jni.setContentBlocking(blockAds, blockGifs, contentBlockingPolicy)
            jni.init(
                context,
                args,
                url,
                logStr,
                experimentalMode,
                userAgent,
                darkTheme,
                servoCallbacks,
            )
            jni.addPlatformWindow(size, density, surface)
        }
    }

    fun version(): String {
        return jni.version()
    }

    fun performUpdates() {
        runOnEngine { jni.performUpdates() }
    }

    fun resize(size: Size) {
        runOnEngine { jni.resize(size) }
    }

    fun reload() {
        markRendererRecovering()
        runOnEngine { jni.reload() }
    }

    fun stop() {
        runOnEngine { jni.stop() }
    }

    fun goBack() {
        runOnEngine { jni.goBack() }
    }

    fun goForward() {
        runOnEngine { jni.goForward() }
    }

    fun loadUri(uri: String) {
        markRendererRecovering()
        runOnEngine { jni.loadUri(uri) }
    }

    fun evaluateJavascript(script: String) {
        runOnEngine { jni.evaluateJavascript(script) }
    }

    /**
     * Evaluate and deliver the JSON result to [onResult] on the UI thread.
     * Callbacks are one-shot; entries for destroyed renderers are dropped.
     */
    fun evaluateJavascript(script: String, onResult: (String) -> Unit) {
        val requestId = nextEvalRequestId.getAndIncrement()
        evalCallbacks[requestId] = onResult
        runOnEngine { jni.evaluateJavascriptWithCallback(script, requestId) }
    }

    private fun deliverEvalResult(requestId: Int, resultJson: String) {
        evalCallbacks.remove(requestId)?.invoke(resultJson)
    }

    fun setUserAgent(userAgent: String) {
        runOnEngine { jni.setUserAgent(userAgent) }
    }

    /** Switches the Java callback sink when the shared Android renderer is reattached to a tab. */
    fun setClient(client: Client) {
        servoCallbacks.client = client
    }


    fun setTheme(darkTheme: Boolean) {
        runOnEngine { jni.setTheme(darkTheme) }
    }

    fun setContentBlocking(blockAds: Boolean, blockGifs: Boolean, policy: String) {
        runOnEngine { jni.setContentBlocking(blockAds, blockGifs, policy) }
    }

    fun scroll(dx: Int, dy: Int, x: Int, y: Int) {
        runOnEngine { jni.scroll(dx, dy, x, y) }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent) {
        runOnEngine { jni.keydown(keyCode, event.unicodeChar) }
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent) {
        runOnEngine { jni.keyup(keyCode, event.unicodeChar) }
    }

    fun imeInsertText(text: String) {
        runOnEngine { jni.imeInsertText(text) }
    }

    fun imeDismissed() {
        runOnEngine { jni.imeDismissed() }
    }

    fun touchDown(x: Float, y: Float, pointerId: Int) {
        runOnEngine { jni.touchDown(x, y, pointerId) }
    }

    fun touchMove(x: Float, y: Float, pointerId: Int) {
        runOnEngine { jni.touchMove(x, y, pointerId) }
    }

    fun touchUp(x: Float, y: Float, pointerId: Int) {
        runOnEngine { jni.touchUp(x, y, pointerId) }
    }

    fun touchCancel(x: Float, y: Float, pointerId: Int) {
        runOnEngine { jni.touchCancel(x, y, pointerId) }
    }

    fun pinchZoomStart(factor: Float, x: Float, y: Float) {
        runOnEngine { jni.pinchZoomStart(factor, x, y) }
    }

    fun pinchZoom(factor: Float, x: Float, y: Float) {
        runOnEngine { jni.pinchZoom(factor, x, y) }
    }

    fun pinchZoomEnd(factor: Float, x: Float, y: Float) {
        runOnEngine { jni.pinchZoomEnd(factor, x, y) }
    }

    fun click(x: Float, y: Float) {
        runOnEngine { jni.click(x, y) }
    }

    fun pausePainting() {
        runOnEngine { jni.pausePainting() }
    }

    fun resumePainting(surface: Surface, size: Size) {
        runOnEngine { jni.resumePainting(surface, size) }
    }

    fun suspend(suspended: Boolean) {
        servoCallbacks.suspended = suspended
        if (!suspended) runOnEngine { }
    }

    fun mediaSessionAction(action: Int) {
        runOnEngine { jni.mediaSessionAction(action) }
    }

    fun setExperimentalMode(enable: Boolean) {
        runOnEngine { jni.setExperimentalMode(enable) }
    }

    fun onDoFrame(frameTimeNanos: Long) {
        // Choreographer keeps producing vsync callbacks even when a complex page takes longer
        // than one frame to update. Keep at most one frame job queued so touch, keyboard and
        // navigation work cannot be starved behind stale frame requests.
        if (rendererUnhealthy.get() || !frameQueued.compareAndSet(false, true)) {
            return
        }
        runCallback.inGLThread {
            try {
                jni.doFrame(frameTimeNanos)
                enginePanicCount.set(0)
            } catch (panic: RuntimeException) {
                // A Rust panic surfaces here (e.g. GL OOM mid-frame during core switch).
                // Never let it kill the host app; stop driving frames and let an explicit
                // reload/resume attempt recovery.
                val count = enginePanicCount.incrementAndGet()
                Log.w(TAG, "Engine frame panicked ($count); halting vsync drive", panic)
                if (count >= MAX_CONSECUTIVE_FRAME_PANICS) {
                    rendererUnhealthy.set(true)
                    Log.w(TAG, "Engine marked unhealthy after repeated frame panics")
                }
            } finally {
                frameQueued.set(false)
            }
            if (!rendererUnhealthy.get() && !servoCallbacks.suspended && jni.needsVsync()) {
                runCallback.requestVsync()
            }
        }
    }

    /** Allow fresh frames again, e.g. after an explicit reload or resume. */
    fun markRendererRecovering() {
        enginePanicCount.set(0)
        rendererUnhealthy.set(false)
    }

    interface Client {
        fun onAlert(message: String)

        fun onLoadStarted()

        fun onLoadEnded()

        fun onTitleChanged(title: String)

        fun onUrlChanged(url: String)

        fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean)

        fun onConsoleMessage(level: Int, message: String)

        fun onImeShow()

        fun onImeHide()

        fun onMediaSessionMetadata(title: String, artist: String, album: String)

        fun onMediaSessionPlaybackStateChange(state: Int)

        fun onMediaSessionSetPositionState(duration: Float, position: Float, playbackRate: Float)
    }

    interface RunCallback {
        fun inGLThread(f: Runnable)

        fun inUIThread(f: Runnable)

        fun requestVsync()
    }

    private inner class Callbacks(
        var client: Client,
        private val jni: JNIServo,
        private val runCallback: RunCallback,
    ) : JNIServo.Callbacks, Client {
        var suspended: Boolean = false
        // Servo can wake the embedder repeatedly while a busy page is loading. Coalesce
        // callbacks so the GL looper cannot accumulate an unbounded performUpdates queue.
        private val updateQueued = AtomicBoolean(false)

        override fun wakeup() {
            if (!suspended && updateQueued.compareAndSet(false, true)) {
                runCallback.inGLThread {
                    try {
                        jni.performUpdates()
                    } finally {
                        updateQueued.set(false)
                    }
                    if (!suspended && jni.needsVsync()) {
                        runCallback.requestVsync()
                    }
                }
            }
        }

        override fun onAlert(message: String) {
            runCallback.inUIThread { client.onAlert(message) }
        }

        override fun onImeShow() {
            runCallback.inUIThread { client.onImeShow() }
        }

        override fun onImeHide() {
            runCallback.inUIThread { client.onImeHide() }
        }

        override fun onLoadStarted() {
            runCallback.inUIThread { client.onLoadStarted() }
        }

        override fun onLoadEnded() {
            runCallback.inUIThread { client.onLoadEnded() }
        }

        override fun onTitleChanged(title: String) {
            runCallback.inUIThread { client.onTitleChanged(title) }
        }

        override fun onUrlChanged(url: String) {
            runCallback.inUIThread { client.onUrlChanged(url) }
        }

        override fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) {
            runCallback.inUIThread { client.onHistoryChanged(canGoBack, canGoForward) }
        }

        override fun onConsoleMessage(level: Int, message: String) {
            runCallback.inUIThread { client.onConsoleMessage(level, message) }
        }

        override fun onEvalResult(requestId: Int, resultJson: String) {
            runCallback.inUIThread { deliverEvalResult(requestId, resultJson) }
        }

        override fun onMediaSessionMetadata(title: String, artist: String, album: String) {
            runCallback.inUIThread { client.onMediaSessionMetadata(title, artist, album) }
        }

        override fun onMediaSessionPlaybackStateChange(state: Int) {
            runCallback.inUIThread { client.onMediaSessionPlaybackStateChange(state) }
        }

        override fun onMediaSessionSetPositionState(duration: Float, position: Float, playbackRate: Float) {
            runCallback.inUIThread { client.onMediaSessionSetPositionState(duration, position, playbackRate) }
        }
    }

    private companion object {
        private const val TAG = "ServoFrame"
        private const val MAX_CONSECUTIVE_FRAME_PANICS = 3
    }
}
