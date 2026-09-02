package com.krystelligence.solipsism.browser.tab

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.krystelligence.solipsism.browser.homepage.NativeHomepageView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

/** Owns the mutually exclusive native and engine surfaces for one browser tab. */
class TabContentHost private constructor(
    activity: Activity,
    private val tab: TabModel,
    private val engineView: Lazy<View>,
    private val homepageFactory: NativeHomepageView.Factory,
) : FrameLayout(activity) {
    private val disposables = CompositeDisposable()
    private var homepageView: NativeHomepageView? = null
    private var engineAttachedListener: ((View) -> Unit)? = null

    init {
        // Render immediately so a newly selected tab cannot expose an empty host for one frame.
        render(tab.contentKind)
        disposables += tab.contentKindChanges()
            .skip(1)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::render)
    }

    fun setEngineAttachedListener(listener: (View) -> Unit) {
        engineAttachedListener = listener
        currentEngineView()?.let(listener)
    }

    fun currentEngineView(): View? = engineView.takeIf(Lazy<View>::isInitialized)?.value

    fun isShowingNativeHomepage(): Boolean = tab.contentKind == TabContentKind.NATIVE_HOMEPAGE

    fun refreshHomepage() = homepageView?.refresh()

    fun dispose() {
        disposables.dispose()
        homepageView?.dispose()
        removeAllViews()
        homepageView = null
    }

    private fun render(kind: TabContentKind) {
        when (kind) {
            TabContentKind.NATIVE_HOMEPAGE -> showHomepage()
            TabContentKind.ENGINE -> showEngine()
        }
    }

    private fun showHomepage() {
        tab.setContentVisible(false)
        currentEngineView()?.let(::removeIfAttached)
        val homepage = homepageView ?: homepageFactory.create(context, tab::loadUrl).also {
            homepageView = it
        }
        attach(homepage)
        homepage.refresh()
    }

    private fun showEngine() {
        homepageView?.let(::removeIfAttached)
        val engine = engineView.value
        attach(engine)
        tab.setContentVisible(true)
        engineAttachedListener?.invoke(engine)
    }

    private fun attach(view: View) {
        if (view.parent === this) return
        (view.parent as? ViewGroup)?.removeView(view)
        addView(
            view,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    private fun removeIfAttached(view: View) {
        if (view.parent === this) removeView(view)
    }

    class Factory @Inject constructor(
        private val activity: Activity,
        private val homepageFactory: NativeHomepageView.Factory,
    ) {
        fun create(tab: TabModel, engineView: Lazy<View>): TabContentHost =
            TabContentHost(activity, tab, engineView, homepageFactory)
    }
}
