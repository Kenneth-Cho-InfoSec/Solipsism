/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.servo.servoview

import android.content.Context
import android.util.Size
import android.view.Surface

/**
 * Maps /ports/servoshell API
 */
internal class JNIServo {
    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("servoshell")
    }

    external fun version(): String

    external fun init(
        context: Context,
        args: String?,
        url: String?,
        logStr: String?,
        experimentalMode: Boolean,
        userAgent: String,
        darkTheme: Boolean,
        callbacks: Callbacks,
    )

    external fun addPlatformWindow(size: Size, density: Float, surface: Surface)

    external fun performUpdates()

    external fun needsVsync(): Boolean

    external fun resize(size: Size)

    external fun reload()

    external fun stop()

    external fun goBack()

    external fun goForward()

    external fun loadUri(uri: String)

    external fun evaluateJavascript(script: String)

    external fun evaluateJavascriptWithCallback(script: String, requestId: Int)

    external fun setUserAgent(userAgent: String)

    external fun setTheme(darkTheme: Boolean)

    external fun setContentBlocking(blockAds: Boolean, blockGifs: Boolean, policy: String)

    external fun scroll(dx: Int, dy: Int, x: Int, y: Int)

    external fun keydown(keycode: Int, unicode: Int)

    external fun keyup(keycode: Int, unicode: Int)

    external fun imeInsertText(text: String)

    external fun imeDismissed()

    external fun touchDown(x: Float, y: Float, pointer_id: Int)

    external fun touchMove(x: Float, y: Float, pointer_id: Int)

    external fun touchUp(x: Float, y: Float, pointer_id: Int)

    external fun touchCancel(x: Float, y: Float, pointer_id: Int)

    external fun pinchZoomStart(factor: Float, x: Float, y: Float)

    external fun pinchZoom(factor: Float, x: Float, y: Float)

    external fun pinchZoomEnd(factor: Float, x: Float, y: Float)

    external fun click(x: Float, y: Float)

    external fun pausePainting()

    external fun resumePainting(surface: Surface, size: Size)

    external fun mediaSessionAction(action: Int)

    external fun setExperimentalMode(enable: Boolean)

    external fun doFrame(frameTimeNanos: Long)

    interface Callbacks {
        fun wakeup()

        fun onAlert(message: String)

        fun onLoadStarted()

        fun onLoadEnded()

        fun onTitleChanged(title: String)

        fun onUrlChanged(url: String)

        fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean)

        fun onImeShow()

        fun onImeHide()

        fun onMediaSessionMetadata(title: String, artist: String, album: String)

        fun onMediaSessionPlaybackStateChange(state: Int)

        fun onMediaSessionSetPositionState(duration: Float, position: Float, playbackRate: Float)

        /**
         * A page script called a `console.*` API. Levels follow the engine's
         * ConsoleLogLevel order: Log=0, Debug=1, Info=2, Warn=3, Error=4,
         * Trace=5, Dir=6.
         */
        fun onConsoleMessage(level: Int, message: String)

        /**
         * An [evaluateJavascriptWithCallback] request completed. `resultJson`
         * is `{"ok":true,"value":<JSON>}` or `{"ok":false,"error":"…"}`.
         */
        fun onEvalResult(requestId: Int, resultJson: String)
    }
}
