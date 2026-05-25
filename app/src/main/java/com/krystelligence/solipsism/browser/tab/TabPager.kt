package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.browser.di.Browser2Scope
import com.krystelligence.solipsism.browser.view.WebViewLongPressHandler
import com.krystelligence.solipsism.browser.view.WebViewScrollCoordinator
import com.krystelligence.solipsism.browser.view.targetUrl.LongPress
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.children
import javax.inject.Inject

/**
 * A sort of coordinator that manages the relationship between [WebViews][WebView] and the container
 * the views are placed in.
 */
@Browser2Scope
class TabPager @Inject constructor(
    private val container: FrameLayout,
    private val webViewScrollCoordinator: WebViewScrollCoordinator,
    private val webViewLongPressHandler: WebViewLongPressHandler
) {

    private val webViews: MutableMap<Int, Lazy<WebView>> = mutableMapOf()
    private var transitionCurrentId: Int? = null
    private var transitionTargetId: Int? = null

    var longPressListener: ((id: Int, longPress: LongPress) -> Unit)? = null

    /**
     * Select the tab with the provided [id] to be displayed by the pager.
     */
    fun selectTab(id: Int) {
        container.removeWebViews(excludeId = id)
        val webView = webViews[id]!!.value
        if (webView.parent != container) {
            container.addView(
                webView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webViewScrollCoordinator.configure(webView)
        webViewLongPressHandler.configure(webView, onLongClick = {
            longPressListener?.invoke(id, it)
        })
    }

    fun previewVerticalTabSwitch(currentId: Int, targetId: Int, direction: Int, progress: Float) {
        if (currentId == targetId) return
        val current = webViews[currentId]?.value ?: return
        val target = webViews[targetId]?.value ?: return
        val distance = container.height.takeIf { it > 0 }?.toFloat() ?: current.height.toFloat()
        if (distance <= 0f) return

        if (transitionCurrentId != currentId || transitionTargetId != targetId) {
            resetTransitionViews()
            transitionCurrentId = currentId
            transitionTargetId = targetId
            ensureInContainer(current)
            ensureInContainer(target)
            target.bringToFront()
        }

        val clampedProgress = progress.coerceIn(0f, 1f)
        val incomingStart = -direction.sign() * distance
        current.translationY = direction.sign() * distance * clampedProgress
        current.alpha = 1f - (0.16f * clampedProgress)
        target.translationY = incomingStart * (1f - clampedProgress)
        target.alpha = 0.72f + (0.28f * clampedProgress)
    }

    fun commitVerticalTabSwitch(targetId: Int, direction: Int, onComplete: () -> Unit) {
        val current = transitionCurrentId?.let(webViews::get)?.value
        val target = transitionTargetId?.let(webViews::get)?.value ?: webViews[targetId]?.value
        val distance = container.height.takeIf { it > 0 }?.toFloat() ?: target?.height?.toFloat() ?: 0f
        val signedDirection = direction.sign()

        if (current == null || target == null || distance <= 0f) {
            onComplete()
            return
        }

        current.animate().cancel()
        target.animate().cancel()
        current.animate()
            .translationY(signedDirection * distance)
            .alpha(0.84f)
            .setDuration(180L)
            .withEndAction {
                current.translationY = 0f
                current.alpha = 1f
            }
            .start()
        target.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220L)
            .withEndAction {
                transitionCurrentId = null
                transitionTargetId = null
                onComplete()
            }
            .start()
    }

    fun cancelVerticalTabSwitch() {
        val current = transitionCurrentId?.let(webViews::get)?.value
        val target = transitionTargetId?.let(webViews::get)?.value
        current?.animate()?.cancel()
        target?.animate()?.cancel()
        current?.animate()
            ?.translationY(0f)
            ?.alpha(1f)
            ?.setDuration(140L)
            ?.start()
        target?.animate()
            ?.alpha(0f)
            ?.setDuration(120L)
            ?.withEndAction {
                resetTransitionViews()
            }
            ?.start()
    }

    /**
     * Clear the container of the [WebView] currently shown.
     */
    fun clearTab() {
        container.removeWebViews()
    }

    /**
     * Add a [WebView] to the list of views shown by this pager.
     */
    fun addTab(id: Int, webView: Lazy<WebView>) {
        webViews[id] = webView
    }

    /**
     * Show the toolbar/search box if it is currently hidden.
     */
    fun showToolbar() {
        webViewScrollCoordinator.showToolbar()
    }

    fun isBottomTabDrawerOpen() = webViewScrollCoordinator.isBottomTabDrawerOpen()

    fun openBottomTabDrawer() {
        webViewScrollCoordinator.openBottomTabDrawer()
    }

    fun closeBottomTabDrawer() {
        webViewScrollCoordinator.closeBottomTabDrawer()
    }

    private fun FrameLayout.removeWebViews(excludeId: Int = -1) {
        children
            .filterIsInstance<WebView>()
            .filter { it.id != excludeId }
            .forEach(container::removeView)
    }

    private fun ensureInContainer(webView: WebView) {
        if (webView.parent != container) {
            container.addView(
                webView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun resetTransitionViews() {
        container.children.filterIsInstance<WebView>().forEach {
            it.animate().cancel()
            it.translationY = 0f
            it.alpha = 1f
        }
        transitionTargetId?.let { targetId ->
            transitionCurrentId?.let { currentId ->
                if (targetId != currentId) {
                    webViews[targetId]?.value?.let(container::removeView)
                }
            }
        }
        transitionCurrentId = null
        transitionTargetId = null
    }

    private fun Int.sign(): Int = if (this >= 0) 1 else -1

}
