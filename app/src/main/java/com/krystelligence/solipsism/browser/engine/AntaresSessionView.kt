package com.krystelligence.solipsism.browser.engine

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceControlViewHost
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import com.krystelligence.antares.protocol.IAntaresSession
import com.krystelligence.antares.protocol.IAntaresSessionCallback
import com.krystelligence.solipsism.browser.tab.AntaresInitialUrlResolver
import java.util.concurrent.Executors

/** Surface owned by Solipsism that displays and controls one remote Antares session. */
class AntaresSessionView(
    context: Context,
    private val connection: AntaresEngineConnection,
    initialUrl: String,
    initialUserAgent: String,
    initialTheme: Int,
    private val contentBlockingPolicy: AntaresContentBlockingPolicy,
    initialBlockAds: Boolean,
    initialBlockGifs: Boolean,
    private val listener: Listener,
) : SurfaceView(context), SurfaceHolder.Callback {
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val binderExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AntaresSessionBinder").apply { isDaemon = true }
    }
    private var pendingUrl = initialUrl
    @Volatile private var userAgent = initialUserAgent
    @Volatile private var contentTheme = initialTheme
    @Volatile private var blockAds = initialBlockAds
    @Volatile private var blockGifs = initialBlockGifs
    @Volatile private var session: IAntaresSession? = null
    @Volatile private var surfaceReady = false
    @Volatile private var foreground = false
    @Volatile private var contentVisible = true
    @Volatile private var attaching = false
    @Volatile private var destroyed = false
    private var browserChromeOverlayVisible = false
    private var reconnectAttempts = 0
    private var reconnectScheduled = false
    private var needsInputReattach = false
    private var imeActive = false
    private val composingText = SpannableStringBuilder()

    private val sessionDeathRecipient = IBinder.DeathRecipient {
        onMain { recoverSession("Antares Engine stopped unexpectedly") }
    }

    private val callback = object : IAntaresSessionCallback.Stub() {
        override fun onReady() = onMain {
            reconnectAttempts = 0
            listener.onReady()
        }
        override fun onLoadStarted() = onMain(listener::onLoadStarted)
        override fun onLoadEnded() = onMain(listener::onLoadEnded)
        override fun onTitleChanged(title: String?) = onMain { listener.onTitleChanged(title.orEmpty()) }
        override fun onUrlChanged(url: String?) = onMain {
            pendingUrl = url.orEmpty()
            listener.onUrlChanged(pendingUrl)
        }
        override fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
            onMain { listener.onHistoryChanged(canGoBack, canGoForward) }
        override fun onImeShow() = onMain(::showHostInputMethod)
        override fun onImeHide() = onMain { hideHostInputMethod(notifyRenderer = false) }
        override fun onAlert(message: String?) = onMain { listener.onAlert(message.orEmpty()) }
        override fun onMediaRequest(request: Bundle?) = onMain {
            request?.let(listener::onMediaRequest)
        }
        override fun onElementProbeResult(requestId: Int, descriptor: String?) = onMain {
            listener.onElementProbeResult(requestId, descriptor.orEmpty())
        }
        override fun onEngineTerminated(reason: String?) =
            onMain { recoverSession(reason ?: "Antares Engine stopped") }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // The remote SurfaceControl package is deliberately render-only.  Solipsism owns the
        // gesture stream, just as it owns the rail and address chrome, and relays it to the
        // engine session below.  Marking this host clickable makes ViewGroup dispatch retain
        // the complete DOWN -> MOVE -> UP stream even when a SurfaceControl package is present.
        isClickable = true
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            // Keep the host surface, its input-transfer token, and the attached remote hierarchy
            // alive while another activity temporarily covers the browser. The default
            // visibility-driven lifecycle destroys the surface in that situation; recreating it
            // can leave a retained Antares document visible but unable to receive touch input.
            setSurfaceLifecycle(SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT)
        }
        // Keep Antares in SurfaceView's normal layer. The sibling input overlay and all browser
        // chrome then remain above it in Solipsism's view hierarchy, matching the input ownership
        // model used by Chromium's Android content host.
        setZOrderMediaOverlay(false)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (android.os.Build.VERSION.SDK_INT >= AntaresProtocol.MIN_ANDROID_API) {
            connectOrAttach()
        } else {
            listener.onEngineError("Antares requires Android 13 or newer")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        executeOnBinder { session?.resize(width, height) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            clearChildSurfacePackage()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            // Browser chrome stays in the activity's normal view hierarchy and does not change
            // window focus. A real focus loss therefore means the activity was covered and the
            // retained remote input package may need to be reattached when it returns.
            if (!browserChromeOverlayVisible) needsInputReattach = true
            return
        }
        if (needsInputReattach && surfaceReady && !destroyed &&
            android.os.Build.VERSION.SDK_INT >= AntaresProtocol.MIN_ANDROID_API
        ) {
            needsInputReattach = false
            // Re-parent the existing package first. Recreating SurfaceControlViewHost here tears
            // down Servo's nested SurfaceView and causes a visible black frame.
            post(::reattachExistingSurface)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return relayTouchEvent(event)
    }

    override fun onCheckIsTextEditor(): Boolean = imeActive

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!imeActive || destroyed || !contentVisible || browserChromeOverlayVisible) return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        outAttrs.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return AntaresInputConnection()
    }

    /**
     * Sends a gesture from Solipsism's ordinary view hierarchy to the remote renderer.
     *
     * The embedded SurfaceControl package does not expose a reliable input receiver on all
     * Android 16/17 builds.  [AntaresInputOverlay] therefore owns the gesture stream and calls
     * this method while the SurfaceView remains responsible only for compositing Antares.
     */
    fun relayTouchEvent(event: MotionEvent): Boolean {
        if (!contentVisible || browserChromeOverlayVisible || destroyed) return false
        requestFocus()
        val forwardedEvent = MotionEvent.obtain(event)
        executeOnBinder {
            try {
                session?.dispatchTouchEvent(forwardedEvent)
            } finally {
                forwardedEvent.recycle()
            }
        }
        return true
    }

    fun onHostResumed() {
        foreground = true
        if (!contentVisible || !surfaceReady || destroyed ||
            android.os.Build.VERSION.SDK_INT < AntaresProtocol.MIN_ANDROID_API
        ) {
            return
        }
        post {
            if (session == null) connectOrAttach() else reattachExistingSurface()
        }
    }

    fun loadUrl(url: String) {
        pendingUrl = url
        executeOnBinder { session?.loadUrl(url) }
    }

    fun goBack() = callSession(IAntaresSession::goBack)
    fun goForward() = callSession(IAntaresSession::goForward)
    fun reload() = callSession(IAntaresSession::reload)
    fun stopLoading() = callSession(IAntaresSession::stop)
    fun click(x: Float, y: Float) = executeOnBinder { session?.click(x, y) }
    fun scroll(dx: Int, dy: Int, x: Int, y: Int) =
        executeOnBinder { session?.scroll(dx, dy, x, y) }
    fun probeElement(requestId: Int, x: Float, y: Float) =
        executeOnBinder { session?.probeElement(requestId, x, y) }

    fun setUserAgent(value: String) {
        userAgent = value
        executeOnBinder { session?.setUserAgent(value) }
    }

    fun setTheme(value: Int) {
        contentTheme = value
        executeOnBinder { session?.setTheme(value) }
    }

    fun setContentBlocking(blockAds: Boolean, blockGifs: Boolean) {
        this.blockAds = blockAds
        this.blockGifs = blockGifs
        executeOnBinder { session?.let(::applyContentBlocking) }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        executeOnBinder { session?.setForeground(value && contentVisible) }
    }

    fun setContentVisible(visible: Boolean) {
        contentVisible = visible
        visibility = if (visible) VISIBLE else INVISIBLE
        if (!visible) hideHostInputMethod(notifyRenderer = true)
        executeOnBinder {
            session?.let { target ->
                target.setInputEnabled(effectiveInputEnabled())
                target.setForeground(foreground && contentVisible)
            }
        }
        if (!visible && android.os.Build.VERSION.SDK_INT >= 36) {
            clearChildSurfacePackage()
        } else if (visible && surfaceReady && !destroyed &&
            android.os.Build.VERSION.SDK_INT >= AntaresProtocol.MIN_ANDROID_API
        ) {
            post(::reattachExistingSurface)
        }
    }

    fun setBrowserChromeOverlayVisible(visible: Boolean, onApplied: () -> Unit = {}) {
        browserChromeOverlayVisible = visible
        if (visible) {
            // Relinquish host-window focus before the local address editor asks for the IME. The
            // embedded hierarchy otherwise remains a competing IME client and can immediately
            // dismiss the keyboard requested by Solipsism.
            hideHostInputMethod(notifyRenderer = true)
        }
        if (destroyed) {
            onApplied()
            return
        }
        runCatching {
            binderExecutor.execute {
                runCatching { session?.setInputEnabled(effectiveInputEnabled()) }
                    .onFailure { error -> onMain { listener.onEngineError(error.message.orEmpty()) } }
                onMain(onApplied)
            }
        }.onFailure { onMain(onApplied) }
    }

    fun destroySession() {
        destroyed = true
        hideHostInputMethod(notifyRenderer = false)
        mainHandler.removeCallbacksAndMessages(null)
        val oldSession = session
        session = null
        binderExecutor.execute {
            oldSession?.asBinder()?.unlinkToDeath(sessionDeathRecipient, 0)
            runCatching { oldSession?.close() }
            binderExecutor.shutdown()
        }
    }

    @RequiresApi(AntaresProtocol.MIN_ANDROID_API)
    private fun connectOrAttach() {
        if (destroyed || attaching) return
        if (!surfaceReady || width <= 0 || height <= 0 || !isAttachedToWindow) {
            postDelayed(::connectOrAttach, ATTACH_RETRY_DELAY_MS)
            return
        }
        val hostConfiguration = createHostConfiguration() ?: run {
            postDelayed(::connectOrAttach, ATTACH_RETRY_DELAY_MS)
            return
        }
        attaching = true
        connection.withEngine { engineResult ->
            engineResult.onSuccess { engine ->
                executeOnBinder {
                    runCatching {
                        val currentSession = session ?: engine.createSession(
                            Bundle().apply {
                                putString(AntaresProtocol.KEY_INITIAL_URL, pendingUrl)
                                putString(AntaresProtocol.KEY_USER_AGENT, userAgent)
                                putInt(AntaresProtocol.KEY_THEME, contentTheme)
                                putBoolean(AntaresProtocol.KEY_EXPERIMENTAL, true)
                            },
                            callback,
                        ).also {
                            it.asBinder().linkToDeath(sessionDeathRecipient, 0)
                            session = it
                        }
                        applyContentBlocking(currentSession)
                        currentSession.setTheme(contentTheme)
                        val result = currentSession.attachSurface(
                            display.displayId,
                            hostConfiguration,
                            width,
                            height,
                        )
                        result.getString(AntaresProtocol.KEY_ERROR)?.let(::error)
                        result.getParcelable(
                            AntaresProtocol.KEY_SURFACE_PACKAGE,
                            SurfaceControlViewHost.SurfacePackage::class.java,
                        ) ?: error("Antares returned no surface")
                    }.onSuccess { surfacePackage ->
                        runCatching { session?.setInputEnabled(effectiveInputEnabled()) }
                        runCatching { session?.setForeground(foreground && contentVisible) }
                        onMain {
                            attaching = false
                            reconnectAttempts = 0
                            setChildSurfacePackage(surfacePackage)
                            if (browserChromeOverlayVisible) clearFocus()
                        }
                    }.onFailure { error ->
                        onMain {
                            attaching = false
                            recoverSession(error.message ?: "Unable to create Antares session")
                        }
                    }
                }
            }.onFailure { error ->
                onMain {
                    attaching = false
                    recoverSession(error.message ?: "Unable to connect to Antares Engine")
                }
            }
        }
    }

    private fun recoverSession(reason: String) {
        if (destroyed) return
        val failedSession = session
        session = null
        attaching = false
        failedSession?.asBinder()?.unlinkToDeath(sessionDeathRecipient, 0)
        if (failedSession != null) {
            binderExecutor.execute { runCatching { failedSession.close() } }
        }
        listener.onEngineError(reason)
        if (!surfaceReady || reconnectScheduled || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectScheduled = true
        val delay = ATTACH_RETRY_DELAY_MS * (1L shl reconnectAttempts)
        reconnectAttempts += 1
        mainHandler.postDelayed({
            reconnectScheduled = false
            if (android.os.Build.VERSION.SDK_INT >= AntaresProtocol.MIN_ANDROID_API) {
                connectOrAttach()
            }
        }, delay)
    }

    private fun callSession(action: (IAntaresSession) -> Unit) {
        executeOnBinder { session?.let(action) }
    }

    @RequiresApi(AntaresProtocol.MIN_ANDROID_API)
    private fun reattachExistingSurface() {
        val currentSession = session ?: return connectOrAttach()
        executeOnBinder {
            runCatching {
                val result = currentSession.surface()
                result.getString(AntaresProtocol.KEY_ERROR)?.let(::error)
                result.getParcelable(
                    AntaresProtocol.KEY_SURFACE_PACKAGE,
                    SurfaceControlViewHost.SurfacePackage::class.java,
                ) ?: error("Antares returned no retained surface")
            }.onSuccess { surfacePackage ->
                runCatching { currentSession.setInputEnabled(effectiveInputEnabled()) }
                runCatching { currentSession.setTheme(contentTheme) }
                runCatching { currentSession.setForeground(foreground && contentVisible) }
                onMain {
                    if (destroyed || !surfaceReady) return@onMain
                    setChildSurfacePackage(surfacePackage)
                }
            }.onFailure {
                onMain(::connectOrAttach)
            }
        }
    }

    private fun applyContentBlocking(target: IAntaresSession) {
        contentBlockingPolicy.openFileDescriptor().use { descriptor ->
            target.setContentBlocking(descriptor, blockAds, blockGifs)
        }
    }

    private fun effectiveInputEnabled(): Boolean = contentVisible && !browserChromeOverlayVisible

    private fun showHostInputMethod() {
        if (destroyed || !effectiveInputEnabled()) return
        imeActive = true
        requestFocus()
        val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
        inputMethodManager?.restartInput(this)
        post { inputMethodManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideHostInputMethod(notifyRenderer: Boolean) {
        val wasActive = imeActive
        imeActive = false
        composingText.clear()
        context.getSystemService(InputMethodManager::class.java)?.let { manager ->
            manager.hideSoftInputFromWindow(windowToken, 0)
            manager.restartInput(this)
        }
        clearFocus()
        if (notifyRenderer && wasActive) executeOnBinder { session?.dismissIme() }
    }

    private inner class AntaresInputConnection : BaseInputConnection(this@AntaresSessionView, true) {
        override fun getEditable(): Editable = composingText

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val committed = text?.toString().orEmpty()
            composingText.clear()
            if (committed.isNotEmpty()) executeOnBinder { session?.commitText(committed) }
            return true
        }

        override fun finishComposingText(): Boolean {
            val committed = composingText.toString()
            composingText.clear()
            if (committed.isNotEmpty()) executeOnBinder { session?.commitText(committed) }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (composingText.isNotEmpty()) {
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
            executeOnBinder {
                session?.deleteSurroundingText(
                    beforeLength.coerceAtLeast(0),
                    afterLength.coerceAtLeast(0),
                )
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                executeOnBinder { session?.sendKey(event.keyCode) }
            }
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            executeOnBinder { session?.sendKey(KeyEvent.KEYCODE_ENTER) }
            return true
        }

        override fun closeConnection() {
            composingText.clear()
            super.closeConnection()
        }
    }

    /**
     * A tab can be selected once more while a core migration is destroying it.  The UI must not
     * submit work to the executor after [destroySession] has shut it down.
     */
    private fun executeOnBinder(action: () -> Unit) {
        if (destroyed) return
        runCatching {
            binderExecutor.execute {
                if (!destroyed) {
                    runCatching(action).onFailure { error ->
                        Log.e(TAG, "Antares session call failed", error)
                        if (error is RemoteException) {
                            onMain {
                                recoverSession(error.message ?: "Antares Engine connection was lost")
                            }
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Ignored work submitted after the Antares tab closed", it) }
    }

    @RequiresApi(AntaresProtocol.MIN_ANDROID_API)
    private fun createHostConfiguration(): Bundle? {
        return if (android.os.Build.VERSION.SDK_INT >= 35) {
            val token = rootSurfaceControl?.inputTransferToken ?: return null
            Bundle().apply { putParcelable(AntaresProtocol.KEY_INPUT_TRANSFER_TOKEN, token) }
        } else {
            val token = runCatching {
                SurfaceView::class.java.getMethod("getHostToken").invoke(this) as? android.os.IBinder
            }.getOrNull() ?: return null
            Bundle().apply { putBinder(AntaresProtocol.KEY_HOST_TOKEN, token) }
        }
    }

    private fun onMain(action: () -> Unit) {
        mainHandler.post(action)
    }

    private companion object {
        const val TAG = "AntaresSessionView"
        const val ATTACH_RETRY_DELAY_MS = 500L
        const val MAX_RECONNECT_ATTEMPTS = 3
    }
}
