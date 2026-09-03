/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.servo.servoview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean

open class ServoView : SurfaceView, Servo.RunCallback, Choreographer.FrameCallback {
    private val glThread: GLThread
    private val surfaceHolderCallback: SurfaceHolderCallback
    private var servo: Servo? = null
    private var servoArgs: String? = null
    private var initialUri: String? = null
    private var userAgent: String = ""
    private var darkTheme = false
    private var blockAds = false
    private var blockGifs = false
    private var contentBlockingPolicy = ""
    private var hostManagedInputMethod = false
    private val frameCallbackScheduled = AtomicBoolean(false)

    private var experimentalMode = false

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        addTouchables(arrayListOf(this))
        glThread = GLThread()
        surfaceHolderCallback = SurfaceHolderCallback(this)
        holder.addCallback(surfaceHolderCallback)
        glThread.start()
    }

    fun setClient(client: Servo.Client) {
        surfaceHolderCallback.client = client
    }

    /**
     * Makes this tab the active owner of the process-wide renderer. Servo's Android embedder
     * exposes one native App per process, so selecting another tab must explicitly move the
     * callback sink and navigate the shared document to that tab's URL.
     */
    open fun activateForTab(url: String) {
        val shared = sharedServo ?: return
        val client = surfaceHolderCallback.client ?: return
        Log.d(
            LOGTAG,
            "activateForTab url=$url localSurface=${holder.surface.isValid} " +
                "sharedSurface=$sharedSurfaceValid clientChanged=${sharedClient !== client}",
        )
        // Views are constructed eagerly for all tabs. Do not let construction steal the native
        // callback sink; foreground activation is the authoritative hand-off point.
        shared.setClient(client)
        sharedClient = client
        shared.setUserAgent(userAgent)
        shared.setTheme(darkTheme)
        shared.setContentBlocking(blockAds, blockGifs, contentBlockingPolicy)
        shared.loadUri(url)
    }

    /**
     * Leaves Android editor ownership with a separate embedding view while Servo continues to
     * receive web touch and key events through its explicit bridge.
     */
    fun setHostManagedInputMethod(enabled: Boolean) {
        hostManagedInputMethod = enabled
        if (enabled) clearFocus()
    }

    fun setServoArgs(args: String?, log: String?, experimentalMode: Boolean) {
        servoArgs = args
        surfaceHolderCallback.servoLog = log
        this.experimentalMode = experimentalMode
    }

    override fun inGLThread(f: Runnable) {
        glThread.glLooperHandler!!.post(f)
    }

    override fun inUIThread(f: Runnable) {
        post(f)
    }

    override fun requestVsync() {
        post {
            if (servo != null && frameCallbackScheduled.compareAndSet(false, true)) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) {
            return rendererForDisplay()?.let {
                it.onKeyDown(keyCode, event)
                true
            } ?: false
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) {
            return rendererForDisplay()?.let {
                it.onKeyUp(keyCode, event)
                true
            } ?: false
        }
        return false
    }

    fun commitText(text: String) {
        if (text.isNotEmpty()) rendererForDisplay()?.imeInsertText(text)
    }

    fun sendKey(keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        rendererForDisplay()?.let {
            it.onKeyDown(keyCode, down)
            it.onKeyUp(keyCode, up)
        }
    }

    fun dismissIme() {
        rendererForDisplay()?.imeDismissed()
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        if (!hostManagedInputMethod) requestFocus()

        val action = motionEvent.actionMasked
        val pointerIndex = motionEvent.actionIndex
        val pointerId = motionEvent.getPointerId(pointerIndex)
        val x = motionEvent.getX(pointerIndex)
        val y = motionEvent.getY(pointerIndex)

        val renderer = rendererForDisplay() ?: return false
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> renderer.touchDown(x, y, pointerId)
            MotionEvent.ACTION_MOVE -> renderer.touchMove(x, y, pointerId)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> renderer.touchUp(x, y, pointerId)
            MotionEvent.ACTION_CANCEL -> renderer.touchCancel(x, y, pointerId)
        }

        return true
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCallbackScheduled.set(false)
        servo?.onDoFrame(frameTimeNanos)
    }

    fun onPause() {
        Choreographer.getInstance().removeFrameCallback(this)
        frameCallbackScheduled.set(false)
        // Logical Antares tabs use detached ServoViews around one persistent renderer surface.
        // Pausing through the local field leaves behaviour dependent on which tab owns the host.
        sharedServo?.suspend(true)
        Log.d(LOGTAG, "renderer suspended")
    }

    fun onResume() {
        // The newly selected logical tab is normally detached from the persistent SurfaceView,
        // so resume the same process-wide renderer that the old tab suspended.
        sharedServo?.suspend(false)
        Log.d(LOGTAG, "renderer resumed")
    }

    fun reload() {
        rendererForDisplay()?.reload()
    }

    fun goBack() {
        rendererForDisplay()?.goBack()
    }

    fun goForward() {
        rendererForDisplay()?.goForward()
    }

    fun stop() {
        rendererForDisplay()?.stop()
    }

    fun click(x: Float, y: Float) {
        rendererForDisplay()?.click(x, y)
    }

    fun scroll(dx: Int, dy: Int, x: Int, y: Int) {
        rendererForDisplay()?.scroll(dx, dy, x, y)
    }

    fun loadUri(uri: String) {
        initialUri = uri
        val servo = servo
        if (servo != null) {
            servo.loadUri(uri)
        }
    }

    /** Navigates the active process-wide renderer without allowing eager background-tab setup to
     * replace the visible document. Initial construction continues to use [loadUri]. */
    fun navigateShared(uri: String) {
        initialUri = uri
        rendererForDisplay()?.loadUri(uri)
    }

    fun evaluateJavascript(script: String) {
        rendererForDisplay()?.evaluateJavascript(script)
    }

    fun setUserAgent(value: String) {
        userAgent = value
        activeRendererForClient()?.setUserAgent(value)
    }

    fun setTheme(dark: Boolean) {
        darkTheme = dark
        activeRendererForClient()?.setTheme(dark)
    }

    fun setContentBlocking(blockAds: Boolean, blockGifs: Boolean, policy: String) {
        this.blockAds = blockAds
        this.blockGifs = blockGifs
        contentBlockingPolicy = policy
        activeRendererForClient()?.setContentBlocking(blockAds, blockGifs, policy)
    }

    fun mediaSessionAction(action: Int) {
        rendererForDisplay()?.mediaSessionAction(action)
    }

    fun setExperimentalMode(enable: Boolean) {
        activeRendererForClient()?.setExperimentalMode(enable)
    }

    private fun rendererForDisplay(): Servo? = sharedServo ?: servo

    private fun activeRendererForClient(): Servo? {
        val shared = sharedServo
        if (shared != null) {
            return shared.takeIf { sharedClient === surfaceHolderCallback.client }
        }
        return servo
    }

    private class GLThread : Thread() {
        var glLooperHandler: Handler? = null

        override fun run() {
            Looper.prepare()

            glLooperHandler = Handler(Looper.myLooper()!!)

            Looper.loop()
        }
    }

    private class SurfaceHolderCallback(private val servoView: ServoView) : SurfaceHolder.Callback {
        var client: Servo.Client? = null
        var servoLog: String? = null
        private var paused = false

        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(LOGTAG, "GLThread::surfaceCreated initialUri=${servoView.initialUri}")
            sharedSurfaceValid = true

            val size = Size(servoView.width, servoView.height)

            val surface = holder.surface

            if (servoView.servo == null && !paused) {
                val existing = sharedServo
                if (existing != null) {
                    servoView.servo = existing
                    val client = servoView.surfaceHolderCallback.client!!
                    existing.setClient(client)
                    sharedClient = client
                    existing.resumePainting(surface, size)
                    servoView.initialUri?.let(existing::loadUri)
                } else {
                    servoView.servo = Servo(
                    servoView.servoArgs,
                    servoView.initialUri,
                    size,
                    servoView.resources.displayMetrics.density,
                    servoLog,
                    servoView.experimentalMode,
                    servoView.userAgent,
                    servoView.darkTheme,
                    servoView.blockAds,
                    servoView.blockGifs,
                    servoView.contentBlockingPolicy,
                    servoView,
                    client!!,
                    servoView.context,
                    surface,
                    ).also {
                        sharedServo = it
                        sharedClient = servoView.surfaceHolderCallback.client
                    }
                }
            } else {
                paused = false
                servoView.servo!!.resumePainting(surface, size)
            }

            servoView.requestVsync()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(LOGTAG, "GLThread::surfaceChanged")
            servoView.servo!!.resize(Size(width, height))
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(LOGTAG, "GLThread::surfaceDestroyed")
            sharedSurfaceValid = false
            paused = true
            Choreographer.getInstance().removeFrameCallback(servoView)
            servoView.frameCallbackScheduled.set(false)
            servoView.servo!!.pausePainting()
        }
    }

    private companion object {
        private const val LOGTAG = "ServoView"
        /** Servo's Android embedder owns one App per process; reuse it across tab surfaces. */
        private var sharedServo: Servo? = null
        private var sharedClient: Servo.Client? = null
        @Volatile private var sharedSurfaceValid = false
    }
}
