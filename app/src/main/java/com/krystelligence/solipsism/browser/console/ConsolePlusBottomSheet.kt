package com.krystelligence.solipsism.browser.console

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.engine.BrowserCore
import io.reactivex.rxjava3.disposables.CompositeDisposable

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
    private val consoleAdapter = ConsoleLogAdapter()
    private val domAdapter = SimpleTextAdapter()
    private val netAdapter = SimpleTextAdapter()
    private val kvAdapter = SimpleTextAdapter()

    private fun host(): ConsoleHost? = activity as? ConsoleHost

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_console_plus, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabId = arguments?.getInt(ARG_TAB_ID, -1) ?: -1
        val tabs = view.findViewById<TabLayout>(R.id.console_plus_tabs)
        val container = view.findViewById<ViewGroup>(R.id.console_plus_container)
        val close = view.findViewById<View>(R.id.console_plus_close)

        val panelIds = listOf(
            R.layout.dialog_console,
            R.layout.dialog_console_plus_elements,
            R.layout.dialog_console_plus_network,
            R.layout.dialog_console_plus_storage
        )
        val panels = panelIds.map { layoutInflater.inflate(it, container, false) }
        panels.forEach { container.addView(it) }
        panels.forEachIndexed { i, v -> v.visibility = if (i == 0) View.VISIBLE else View.GONE }

        tabs.addTab(tabs.newTab().setText("Console"))
        tabs.addTab(tabs.newTab().setText("Elements"))
        tabs.addTab(tabs.newTab().setText("Network"))
        tabs.addTab(tabs.newTab().setText("Storage"))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val i = tab?.position ?: 0
                panels.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        close.setOnClickListener { dismiss() }

        // Console panel wiring
        wireConsole(panels[0])
        // Elements
        wireElements(panels[1])
        // Network
        wireNetwork(panels[2])
        // Storage
        wireStorage(panels[3])
    }

    private fun wireConsole(v: View) {
        val list = v.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.console_list)
        list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        list.adapter = consoleAdapter
        v.findViewById<View>(R.id.console_clear).setOnClickListener { host()?.consoleStore(tabId)?.clear() }
        v.findViewById<View>(R.id.console_close).setOnClickListener { dismiss() }
        v.findViewById<View>(R.id.console_run).setOnClickListener {
            val input = v.findViewById<android.widget.EditText>(R.id.console_input)
            val code = input.text.toString()
            if (code.isNotBlank()) {
                host()?.evaluateConsole(tabId, code)
                input.text.clear()
            }
        }
        host()?.consoleStore(tabId)?.entries()?.let { obs ->
            obs.observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe { list -> consoleAdapter.submitList(list) }
                .let { disposables.add(it) }
        }
    }

    private fun wireElements(v: View) {
        val toggle = v.findViewById<Button>(R.id.inspect_toggle)
        val hint = v.findViewById<TextView>(R.id.inspect_hint)
        val tree = v.findViewById<RecyclerView>(R.id.dom_tree)
        val props = v.findViewById<TextView>(R.id.props_rows)
        tree.layoutManager = LinearLayoutManager(requireContext())
        tree.adapter = domAdapter

        val sampleTree = listOf(
            "html", "  head", "  body", "    div.hero", "      h1 (Welcome to Demo Shop)", "      p (Inspect this page…)",
            "    div.card", "      strong (Featured: Wireless Headphones)", "      p ($129.00 — In stock)",
            "      button.btn (Buy now)", "    div.card", "      input#emailInput", "      button.btn (Subscribe)"
        )
        domAdapter.submit(sampleTree)

        toggle.setOnClickListener {
            val on = toggle.text.toString() == "Inspect"
            toggle.text = if (on) "Stop" else "Inspect"
            hint.text = if (on) "Tap an element on the page" else "Inspect mode off"
            if (on) injectInspectScript()
        }

        props.text = "width: 96px\nheight: 40px\ncolor: #FFFFFF\nbackground-color: #6750A4\nfont-size: 14px\nborder-radius: 20px\ndisplay: inline-block"
    }

    private fun injectInspectScript() {
        host()?.currentWebView()?.evaluateJavascript(
            "(function(){ if(window.__cp) return; window.__cp=true; " +
            "document.addEventListener('click', function(e){ e.preventDefault(); e.stopPropagation();" +
            "var el=e.target; var r=el.getBoundingClientRect();" +
            "window.ConsolePlusBridge && window.ConsolePlusBridge.onInspect(el.tagName, el.className, r.left, r.top, r.width, r.height);" +
            "}, true); })();", null
        )
    }

    private fun wireNetwork(v: View) {
        val list = v.findViewById<RecyclerView>(R.id.net_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = netAdapter
        val sample = listOf(
            "200  GET  /demo  4.2KB  120ms",
            "200  GET  /app.css  18.4KB  95ms",
            "200  GET  /app.js  62.1KB  140ms",
            "404  GET  /api/recommendations  0.3KB  310ms",
            "200  GET  /hero.jpg  184KB  420ms"
        )
        netAdapter.submit(sample)
        v.findViewById<Button>(R.id.thr_none).setOnClickListener { }
        v.findViewById<Button>(R.id.thr_slow).setOnClickListener { }
        v.findViewById<Button>(R.id.thr_off).setOnClickListener { }
    }

    private fun wireStorage(v: View) {
        val list = v.findViewById<RecyclerView>(R.id.kv_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = kvAdapter
        val sample = listOf(
            "session_id = abc123xyz",
            "theme = light",
            "consent = accepted",
            "cart = {\"items\":1}",
            "lastVisit = 2026-11-14"
        )
        kvAdapter.submit(sample)
        v.findViewById<Button>(R.id.store_cookies).setOnClickListener { kvAdapter.submit(sample) }
        v.findViewById<Button>(R.id.store_local).setOnClickListener { kvAdapter.submit(listOf("cart = {\"items\":1}", "lastVisit = 2026-11-14")) }
        v.findViewById<Button>(R.id.store_session).setOnClickListener { kvAdapter.submit(listOf("scrollY = 120")) }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    companion object {
        private const val ARG_TAB_ID = "tab_id"
        fun newInstance(tabId: Int) = ConsolePlusBottomSheet().apply {
            arguments = Bundle().apply { putInt(ARG_TAB_ID, tabId) }
        }
    }
}

class SimpleTextAdapter : RecyclerView.Adapter<SimpleTextAdapter.VH>() {
    private val items = mutableListOf<String>()
    fun submit(list: List<String>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = android.widget.TextView(parent.context).apply {
            setPadding(8, 6, 8, 6); textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE
        }
        return VH(tv)
    }
    override fun onBindViewHolder(holder: VH, position: Int) { (holder.itemView as android.widget.TextView).text = items[position] }
    class VH(v: View) : RecyclerView.ViewHolder(v)
}
