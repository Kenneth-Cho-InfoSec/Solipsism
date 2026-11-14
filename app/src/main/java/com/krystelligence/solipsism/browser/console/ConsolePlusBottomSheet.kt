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
        props.text = "Tap \"Inspect\" then tap an element on the page"

        toggle.setOnClickListener {
            val on = toggle.text.toString() == "Inspect"
            toggle.text = if (on) "Stop" else "Inspect"
            hint.text = if (on) "Tap an element on the page to inspect" else "Inspect mode off"
            if (on) {
                enableInspectMode()
            } else {
                disableInspectMode()
            }
        }
    }

    private fun enableInspectMode() {
        val wv = host()?.currentWebView() ?: return
        // Inject bridge
        wv.addJavascriptInterface(InspectBridge { json ->
            activity?.runOnUiThread {
                try {
                    val o = org.json.JSONObject(json)
                    val tag = o.optString("tag")
                    val cls = o.optString("cls")
                    val rect = o.optJSONObject("rect")
                    val styles = o.optJSONObject("styles")
                    val props = view?.findViewById<TextView>(R.id.props_rows)
                    val sb = StringBuilder()
                    sb.append("<$tag${if (cls.isNotEmpty()) " class=\"$cls\"" else ""}>\n")
                    if (rect != null) sb.append("rect: ${rect.optInt("w")}×${rect.optInt("h")} @ (${rect.optInt("x")},${rect.optInt("y")})\n")
                    if (styles != null) {
                        val keys = styles.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            sb.append("$k: ${styles.optString(k)}\n")
                        }
                    }
                    props?.text = sb.toString().trim()
                    showToast("Inspected: <$tag>")
                } catch (e: Exception) {
                    showToast("Inspect failed: ${e.message}")
                }
            }
        }, "ConsolePlusBridge")
        wv.evaluateJavascript(INSPECT_SCRIPT, null)
    }

    private fun disableInspectMode() {
        host()?.currentWebView()?.evaluateJavascript("window.__cpInspect=false;", null)
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
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
        fun refresh() {
            val items = NetworkLog.all().map { e ->
                val short = e.url.removePrefix("https://").removePrefix("http://").take(40)
                "${e.status}  $short  ${e.sizeBytes}B  ${e.durationMs}ms"
            }
            netAdapter.submit(if (items.isEmpty()) listOf("(no requests yet)") else items)
        }
        refresh()
        v.findViewById<Button>(R.id.thr_none).setOnClickListener { refresh() }
        v.findViewById<Button>(R.id.thr_slow).setOnClickListener { refresh() }
        v.findViewById<Button>(R.id.thr_off).setOnClickListener { refresh() }
    }

    private fun wireStorage(v: View) {
        val list = v.findViewById<RecyclerView>(R.id.kv_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = kvAdapter
        val wv = host()?.currentWebView()

        fun loadCookies() {
            val url = wv?.url ?: "https://example.com"
            val cookieStr = android.webkit.CookieManager.getInstance().getCookie(url) ?: ""
            val items = cookieStr.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            kvAdapter.submit(if (items.isEmpty()) listOf("(no cookies)") else items)
        }
        fun loadLocal() {
            wv?.evaluateJavascript("(function(){try{return JSON.stringify(Object.keys(localStorage).map(function(k){return k+' = '+localStorage.getItem(k);}));}catch(e){return '[]';}})();") { res ->
            activity?.runOnUiThread {
                val cleaned = res?.trim('"')?.replace("\\\"", "\"") ?: "[]"
                val arr = try { org.json.JSONArray(cleaned) } catch (e: Exception) { org.json.JSONArray() }
                val items = (0 until arr.length()).map { arr.optString(it) }
                kvAdapter.submit(if (items.isEmpty()) listOf("(empty)") else items)
            }
        }
        }
        fun loadSession() {
            wv?.evaluateJavascript("(function(){try{return JSON.stringify(Object.keys(sessionStorage).map(function(k){return k+' = '+sessionStorage.getItem(k);}));}catch(e){return '[]';}})();") { res ->
            activity?.runOnUiThread {
                val cleaned = res?.trim('"')?.replace("\\\"", "\"") ?: "[]"
                val arr = try { org.json.JSONArray(cleaned) } catch (e: Exception) { org.json.JSONArray() }
                val items = (0 until arr.length()).map { arr.optString(it) }
                kvAdapter.submit(if (items.isEmpty()) listOf("(empty)") else items)
            }
        }
        }
        loadCookies()
        v.findViewById<Button>(R.id.store_cookies).setOnClickListener { loadCookies() }
        v.findViewById<Button>(R.id.store_local).setOnClickListener { loadLocal() }
        v.findViewById<Button>(R.id.store_session).setOnClickListener { loadSession() }
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

private const val INSPECT_SCRIPT = "(function(){ if(window.__cpInspect) return; window.__cpInspect=true; " +
    "document.addEventListener('click', function(e){ e.preventDefault(); e.stopPropagation();" +
    "var el=e.target; var r=el.getBoundingClientRect(); var s=getComputedStyle(el);" +
    "var payload={tag:el.tagName,cls:el.className,rect:{x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height)}," +
    "styles:{width:s.width,height:s.height,color:s.color,'background-color':s.backgroundColor,'font-size':s.fontSize,'border-radius':s.borderRadius,display:s.display}};" +
    "if(window.ConsolePlusBridge) window.ConsolePlusBridge.onInspect(JSON.stringify(payload));" +
    "}, true); })();"

class InspectBridge(private val cb: (String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onInspect(json: String) = cb(json)
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
