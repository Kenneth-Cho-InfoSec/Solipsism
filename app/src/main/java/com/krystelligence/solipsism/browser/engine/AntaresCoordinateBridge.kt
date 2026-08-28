package com.krystelligence.solipsism.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.preference.UserPreferences
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optional diagnostic shadow for the experimental Antares renderer.
 *
 * A real Android WebView is laid out at the same physical size underneath Antares. It performs
 * an independent Chromium load and is never shown or trusted as browser content. Before a tap is
 * forwarded, both engines describe the nearest interactive DOM target. A disagreement is blocked
 * and logged so an Antares rendering defect cannot silently activate a different control.
 *
 * This intentionally does not copy WebView DOM or cookies into Antares. Those are independent
 * browsing contexts and pretending otherwise would create origin, state and security bugs.
 */
internal class AntaresCoordinateBridge(
    context: Context,
    private val sessionView: AntaresSessionView,
    private val userPreferences: UserPreferences,
    initialUrl: String,
) {
    private val appContext = context.applicationContext
    private val nextRequestId = AtomicInteger(1)
    private val pending = mutableMapOf<Int, PendingTap>()
    private var referenceReadyUrl: String? = null
    private var requestedUrl: String? = null
    private var foreground = false
    private var destroyed = false

    val referenceView: WebView = WebView(context).apply {
        // Keep the reference renderer alive and laid out while ensuring it is neither visible to
        // accessibility services nor eligible to receive physical focus/input.
        alpha = REFERENCE_ALPHA
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        settings.apply {
            javaScriptEnabled = userPreferences.javaScriptEnabled
            domStorageEnabled = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            useWideViewPort = userPreferences.useWideViewPortEnabled
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            userAgentString = WebSettings.getDefaultUserAgent(context)
        }
        val shadowWebView = this
        CookieManager.getInstance().apply {
            setAcceptCookie(userPreferences.cookiesEnabled)
            setAcceptThirdPartyCookies(
                shadowWebView,
                userPreferences.cookiesEnabled && !userPreferences.blockThirdPartyCookiesEnabled,
            )
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                referenceReadyUrl = null
                Log.d(TAG, "Chromium reference started ${redactUrl(url)}")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                referenceReadyUrl = url
                Log.d(TAG, "Chromium reference ready ${redactUrl(url)}")
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = !isReferenceUrlAllowed(request.url)
        }
    }

    init {
        requestedUrl = initialUrl.takeIf { isReferenceUrlAllowed(Uri.parse(it)) }
    }

    fun loadUrl(url: String) {
        if (destroyed || !isReferenceUrlAllowed(Uri.parse(url))) return
        requestedUrl = url
        if (!foreground || sameDocument(referenceReadyUrl, url)) return
        referenceReadyUrl = null
        referenceView.loadUrl(url)
    }

    fun setForeground(value: Boolean) {
        if (destroyed || foreground == value) return
        foreground = value
        if (value) {
            referenceView.onResume()
            requestedUrl?.let(::loadUrl)
        } else {
            referenceView.stopLoading()
            referenceView.onPause()
        }
    }

    fun onAntaresUrlChanged(url: String) {
        loadUrl(url)
    }

    fun scrollBy(dx: Int, dy: Int) {
        if (!destroyed) referenceView.scrollBy(dx, dy)
    }

    /** Compare both DOM targets and only then perform Antares's native primary click. */
    fun verifyTap(x: Float, y: Float) {
        if (destroyed || !foreground || referenceReadyUrl == null) {
            blockUnavailableTap("Chromium reference page was not ready")
            return
        }

        val requestId = nextRequestId.getAndIncrement()
        referenceView.evaluateJavascript(elementProbeScript(x, y)) { rawResult ->
            if (destroyed) return@evaluateJavascript
            val reference = PageElementDescriptor.fromEvaluationResult(rawResult)
            if (reference == null) {
                blockUnavailableTap("Chromium reference returned an unreadable probe")
                return@evaluateJavascript
            }
            pending[requestId] = PendingTap(x, y, reference)
            sessionView.probeElement(requestId, x, y)
            referenceView.postDelayed({ expire(requestId) }, PROBE_TIMEOUT_MS)
        }
    }

    fun onAntaresProbeResult(requestId: Int, descriptor: String) {
        val tap = pending.remove(requestId) ?: return
        val antares = PageElementDescriptor.fromJson(descriptor)
        if (antares != null && tap.reference.sameInteractiveTarget(antares)) {
            if (!tap.reference.sameGeometry(antares)) {
                Log.i(
                    TAG,
                    "Coordinate geometry differs but resolves to the same target. " +
                        "WebView=${tap.reference.toLogString()} Antares=${antares.toLogString()}",
                )
            }
            sessionView.click(tap.x, tap.y)
            return
        }

        Log.w(
            TAG,
            "Blocked coordinate mismatch at (${tap.x}, ${tap.y}). " +
                "WebView=${tap.reference.toLogString()} " +
                "Antares=${antares?.toLogString() ?: "unavailable"}",
        )
        Toast.makeText(
            appContext,
            R.string.antares_coordinate_mismatch,
            Toast.LENGTH_LONG,
        ).show()
    }

    fun destroy() {
        destroyed = true
        pending.clear()
        referenceView.stopLoading()
        referenceView.removeAllViews()
        referenceView.destroy()
    }

    private fun expire(requestId: Int) {
        pending.remove(requestId) ?: return
        blockUnavailableTap("Antares coordinate probe timed out")
    }

    private fun blockUnavailableTap(reason: String) {
        Log.w(TAG, "$reason; blocked unverified tap")
        Toast.makeText(
            appContext,
            R.string.antares_coordinate_check_unavailable,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun elementProbeScript(physicalX: Float, physicalY: Float): String {
        val x = String.format(Locale.ROOT, "%.3f", physicalX)
        val y = String.format(Locale.ROOT, "%.3f", physicalY)
        return """
            (() => {
              const dpr = window.devicePixelRatio || 1;
              const viewport = window.visualViewport;
              const viewportScale = (viewport && viewport.scale) || 1;
              const viewportWidth = Math.round((viewport && viewport.width) || innerWidth);
              const viewportHeight = Math.round((viewport && viewport.height) || innerHeight);
              const px = $x / dpr / viewportScale;
              const py = $y / dpr / viewportScale;
              const raw = document.elementFromPoint(px, py);
              const target = raw && raw.closest
                ? (raw.closest('a[href],button,input,select,textarea,[role="button"],[role="link"],[onclick],[tabindex]') || raw)
                : raw;
              const compact = value => String(value || '').replace(/\s+/g, ' ').trim().slice(0, 120);
              if (!target) return JSON.stringify({
                empty: true, x: px, y: py, viewportWidth: viewportWidth,
                viewportHeight: viewportHeight, dpr: dpr, viewportScale: viewportScale
              });
              const rect = target.getBoundingClientRect();
              return JSON.stringify({
                empty: false,
                tag: compact(target.tagName).toLowerCase(),
                id: compact(target.id),
                name: compact(target.getAttribute && target.getAttribute('name')),
                type: compact(target.getAttribute && target.getAttribute('type')).toLowerCase(),
                role: compact(target.getAttribute && target.getAttribute('role')).toLowerCase(),
                label: compact(target.getAttribute && (target.getAttribute('aria-label') || target.getAttribute('title'))),
                text: compact(target.innerText || target.value || target.textContent),
                href: compact(target.href),
                left: Math.round(rect.left), top: Math.round(rect.top),
                right: Math.round(rect.right), bottom: Math.round(rect.bottom),
                x: px, y: py, viewportWidth: viewportWidth, viewportHeight: viewportHeight,
                dpr: dpr, viewportScale: viewportScale
              });
            })();
        """.trimIndent()
    }

    private data class PendingTap(
        val x: Float,
        val y: Float,
        val reference: PageElementDescriptor,
    )

    private companion object {
        const val TAG = "AntaresCoordinateBridge"
        const val REFERENCE_ALPHA = 0.01f
        const val PROBE_TIMEOUT_MS = 1_500L

        fun isReferenceUrlAllowed(uri: Uri): Boolean =
            uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("http", ignoreCase = true)

        fun sameDocument(first: String?, second: String?): Boolean =
            first?.substringBefore('#') == second?.substringBefore('#')

        fun redactUrl(url: String?): String = runCatching {
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}${uri.path.orEmpty()}"
        }.getOrDefault("unavailable")
    }
}

internal data class PageElementDescriptor(
    val empty: Boolean,
    val tag: String,
    val id: String,
    val name: String,
    val type: String,
    val role: String,
    val label: String,
    val text: String,
    val href: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val devicePixelRatio: Double,
    val viewportScale: Double,
) {
    fun sameInteractiveTarget(other: PageElementDescriptor): Boolean {
        if (empty || other.empty) return empty == other.empty
        if (tag != other.tag) return false
        if (id.isNotBlank() && other.id.isNotBlank()) return id == other.id
        if (href.isNotBlank() && other.href.isNotBlank()) return normalise(href) == normalise(other.href)
        if (label.isNotBlank() && other.label.isNotBlank()) return normalise(label) == normalise(other.label)
        if (name.isNotBlank() && other.name.isNotBlank()) {
            return name == other.name && type == other.type
        }
        if (text.isNotBlank() && other.text.isNotBlank()) return normalise(text) == normalise(other.text)
        return role == other.role && type == other.type
    }

    fun sameGeometry(other: PageElementDescriptor): Boolean =
        kotlin.math.abs(left - other.left) <= GEOMETRY_TOLERANCE_CSS_PX &&
            kotlin.math.abs(top - other.top) <= GEOMETRY_TOLERANCE_CSS_PX &&
            kotlin.math.abs(right - other.right) <= GEOMETRY_TOLERANCE_CSS_PX &&
            kotlin.math.abs(bottom - other.bottom) <= GEOMETRY_TOLERANCE_CSS_PX &&
            kotlin.math.abs(viewportWidth - other.viewportWidth) <= GEOMETRY_TOLERANCE_CSS_PX &&
            kotlin.math.abs(viewportHeight - other.viewportHeight) <= GEOMETRY_TOLERANCE_CSS_PX &&
            kotlin.math.abs(devicePixelRatio - other.devicePixelRatio) <= SCALE_TOLERANCE &&
            kotlin.math.abs(viewportScale - other.viewportScale) <= SCALE_TOLERANCE

    fun toLogString(): String =
        "target(tag=$tag,id=$id,name=$name,type=$type,role=$role,label=$label,text=$text," +
            "href=$href,bounds=[$left,$top,$right,$bottom],viewport=${viewportWidth}x$viewportHeight," +
            "dpr=$devicePixelRatio,scale=$viewportScale)"

    companion object {
        private const val GEOMETRY_TOLERANCE_CSS_PX = 3
        private const val SCALE_TOLERANCE = 0.02

        fun fromEvaluationResult(rawResult: String?): PageElementDescriptor? {
            if (rawResult.isNullOrBlank() || rawResult == "null") return null
            val decoded = runCatching { JSONTokener(rawResult).nextValue() }.getOrNull()
            val json = when (decoded) {
                is String -> decoded
                is JSONObject -> decoded.toString()
                else -> return null
            }
            return fromJson(json)
        }

        fun fromJson(json: String?): PageElementDescriptor? = runCatching {
            val value = JSONObject(json.orEmpty())
            PageElementDescriptor(
                empty = value.optBoolean("empty", false),
                tag = value.optString("tag").lowercase(Locale.ROOT),
                id = value.optString("id"),
                name = value.optString("name"),
                type = value.optString("type").lowercase(Locale.ROOT),
                role = value.optString("role").lowercase(Locale.ROOT),
                label = value.optString("label"),
                text = value.optString("text"),
                href = value.optString("href"),
                left = value.optInt("left"),
                top = value.optInt("top"),
                right = value.optInt("right"),
                bottom = value.optInt("bottom"),
                viewportWidth = value.optInt("viewportWidth"),
                viewportHeight = value.optInt("viewportHeight"),
                devicePixelRatio = value.optDouble("dpr", 1.0),
                viewportScale = value.optDouble("viewportScale", 1.0),
            )
        }.getOrNull()

        private fun normalise(value: String): String = value
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
            .removeSuffix("/")
    }
}
