package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.browser.di.Browser2Scope
import com.krystelligence.solipsism.browser.view.WebViewLongPressHandler
import com.krystelligence.solipsism.browser.view.WebViewScrollCoordinator
import com.krystelligence.solipsism.browser.view.targetUrl.LongPress
import android.view.ViewGroup
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.children
import kotlin.math.max
import kotlin.math.min
import java.util.Locale
import android.os.Debug
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

    private val tabViews: MutableMap<Int, Lazy<View>> = mutableMapOf()
    private var transitionCurrentId: Int? = null
    private var transitionTargetId: Int? = null

    var longPressListener: ((id: Int, longPress: LongPress) -> Unit)? = null

    /**
     * Select the tab with the provided [id] to be displayed by the pager.
     */
    fun selectTab(id: Int) {
        container.removeTabViews(excludeId = id)
        val tabView = tabViews[id]!!.value
        if (tabView.parent != container) {
            container.addView(
                tabView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        if (tabView is TabContentHost) {
            tabView.setEngineAttachedListener { configureEngineView(id, it) }
        } else {
            configureEngineView(id, tabView)
        }
    }

    fun previewVerticalTabSwitch(currentId: Int, targetId: Int, direction: Int, progress: Float) {
        if (currentId == targetId) return
        val current = tabViews[currentId]?.value ?: return
        val target = tabViews[targetId]?.value ?: return
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
        val current = transitionCurrentId?.let(tabViews::get)?.value
        val target = transitionTargetId?.let(tabViews::get)?.value ?: tabViews[targetId]?.value
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
        val current = transitionCurrentId?.let(tabViews::get)?.value
        val target = transitionTargetId?.let(tabViews::get)?.value
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
        container.removeTabViews()
    }

    /**
     * Add a [WebView] to the list of views shown by this pager.
     */
    fun addTab(id: Int, tabView: Lazy<View>) {
        tabViews[id] = tabView
    }

    fun removeTab(id: Int) {
        tabViews.remove(id)?.takeIf { it.isInitialized() }?.value?.let { view ->
            container.removeView(view)
            (view as? TabContentHost)?.dispose()
        }
    }

    fun replaceTabs(replacements: Map<Int, Lazy<View>>) {
        resetTransitionViews()
        tabViews.values
            .filter(Lazy<View>::isInitialized)
            .map(Lazy<View>::value)
            .filterIsInstance<TabContentHost>()
            .forEach(TabContentHost::dispose)
        container.removeAllViews()
        tabViews.clear()
        tabViews.putAll(replacements)
    }

    /**
     * Returns a conservative per-tab RAM estimate for the tab-management title.
     * Android WebView does not expose exact per-tab renderer memory, so this combines the shared
     * process PSS with the tab's rendered surface footprint and deliberately marks the value as an
     * estimate in the UI.
     */
    fun estimatedMemoryForTab(id: Int): String {
        val registeredView = tabViews[id]?.takeIf { it.isInitialized() }?.value
        val tabView = (registeredView as? TabContentHost)?.currentEngineView() ?: registeredView
        val processShareKb = (Debug.getPss() / tabViews.size.coerceAtLeast(1)).coerceAtLeast(0L)
        val renderedSurfaceKb = tabView?.let {
            val width = max(it.width, it.resources.displayMetrics.widthPixels)
            val height = max(it.height, it.resources.displayMetrics.heightPixels)
            val contentHeight = if (it is WebView) {
                min((it.contentHeight * it.scale).toLong(), MAX_ESTIMATED_CONTENT_HEIGHT_PX)
            } else {
                height.toLong()
            }
            max(width.toLong() * height, width.toLong() * contentHeight) * 4L / 1024L
        } ?: 0L
        val estimateMb = max(processShareKb, renderedSurfaceKb) / 1024.0
        return String.format(Locale.US, "~%.0f MB", estimateMb.coerceAtLeast(1.0))
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

    private fun FrameLayout.removeTabViews(excludeId: Int = -1) {
        val excludedView = tabViews[excludeId]?.takeIf { it.isInitialized() }?.value
        children
            .filter { it !== excludedView }
            .forEach(container::removeView)
    }

    private fun configureEngineView(id: Int, view: View) {
        if (view !is WebView) return
        webViewScrollCoordinator.configure(view)
        webViewLongPressHandler.configure(view, onLongClick = {
            longPressListener?.invoke(id, it)
        })
    }

    private fun ensureInContainer(tabView: View) {
        if (tabView.parent != container) {
            container.addView(
                tabView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun resetTransitionViews() {
        container.children.forEach {
            it.animate().cancel()
            it.translationY = 0f
            it.alpha = 1f
        }
        transitionTargetId?.let { targetId ->
            transitionCurrentId?.let { currentId ->
                if (targetId != currentId) {
                    tabViews[targetId]?.value?.let(container::removeView)
                }
            }
        }
        transitionCurrentId = null
        transitionTargetId = null
    }

    private companion object {
        private const val MAX_ESTIMATED_CONTENT_HEIGHT_PX = 16_777_216L
    }

    private fun Int.sign(): Int = if (this >= 0) 1 else -1

}
