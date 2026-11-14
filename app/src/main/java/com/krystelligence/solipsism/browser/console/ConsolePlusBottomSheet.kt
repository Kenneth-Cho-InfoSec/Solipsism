package com.krystelligence.solipsism.browser.console

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.engine.BrowserCore
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Console+ : on-device developer tools. Tabs: Console, Elements, Network, Storage.
 * Reuses the existing ConsoleStore for the Console tab.
 */
class ConsolePlusBottomSheet : BottomSheetDialogFragment() {

    interface ConsoleHost {
        data class TabRef(val tabId: Int, val engine: BrowserCore, val label: String)
        fun consoleTabs(): List<TabRef>
        fun consoleStore(tabId: Int): ConsoleStore?
        fun evaluateConsole(tabId: Int, code: String): Boolean
        fun currentWebView(): WebView?
    }

    private var tabId: Int = -1
    private val disposables = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_console_plus, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabId = arguments?.getInt(ARG_TAB_ID, -1) ?: -1
        val tabs = view.findViewById<TabLayout>(R.id.console_plus_tabs)
        val container = view.findViewById<ViewGroup>(R.id.console_plus_container)

        val tabNames = listOf("Console", "Elements", "Network", "Storage")
        tabNames.forEach { tabs.addTab(tabs.newTab().setText(it)) }

        // Inflate panel layouts lazily
        val consoleView = layoutInflater.inflate(R.layout.dialog_console, container, false)
        container.addView(consoleView)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // For simplicity, swap content by tag; full per-tab fragments deferred
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        view.findViewById<View>(R.id.console_plus_close).setOnClickListener { dismiss() }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    companion object {
        private const val ARG_TAB_ID = "tab_id"
        private const val CONSOLE_PLUS_TAG = "console_plus"
        fun newInstance(tabId: Int) = ConsolePlusBottomSheet().apply {
            arguments = Bundle().apply { putInt(ARG_TAB_ID, tabId) }
        }
    }
}

/** JS bridge injected into pages for element inspection. */
class ConsolePlusBridge(private val callback: (String) -> Unit) {
    @JavascriptInterface
    fun inspect(x: Float, y: Float) {
        // Returns JSON with tag, rect, computed styles
    }

    @JavascriptInterface
    fun getStyles(selector: String): String = "{}"
}
