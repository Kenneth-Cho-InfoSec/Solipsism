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

    interface ConsoleHost : ConsoleBottomSheet.ConsoleHost {
        fun currentWebView(): WebView?
        fun currentUrl(): String
    }

    private var tabId: Int = -1
    private val disposables = CompositeDisposable()
    private val consoleAdapter = ConsoleLogAdapter()
    private val domAdapter = SimpleTextAdapter()
    private val netAdapter = SimpleTextAdapter()
    private val kvAdapter = SimpleTextAdapter()

    private fun host(): ConsoleHost? = activity as? ConsoleHost

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
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
        tabs.setTabTextColors(0xFF49454F.toInt(), 0xFF6750A4.toInt())
        tabs.setSelectedTabIndicatorColor(0xFF6750A4.toInt())

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
        // Syntax highlighting on the console input
        val input = v.findViewById<android.widget.EditText>(R.id.console_input)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s == null) return
                val text = s.toString()
                // remove existing spans
                s.getSpans(0, s.length, android.text.style.ForegroundColorSpan::class.java).forEach { s.removeSpan(it) }
                // keywords
                val keywords = listOf("function","var","let","const","if","else","return","for","while","true","false","null","undefined")
                for (kw in keywords) {
                    val regex = Regex("\\b$kw\\b")
                    regex.findAll(text).forEach { m ->
                        s.setSpan(android.text.style.ForegroundColorSpan(0xFF6750A4.toInt()), m.range.first, m.range.last+1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                // strings
                Regex("\"[^\"]*\"|'[^']*'").findAll(text).forEach { m ->
                    s.setSpan(android.text.style.ForegroundColorSpan(0xFF2E7D32.toInt()), m.range.first, m.range.last+1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // numbers
                Regex("\\b\\d+\\b").findAll(text).forEach { m ->
                    s.setSpan(android.text.style.ForegroundColorSpan(0xFFE65100.toInt()), m.range.first, m.range.last+1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        })
        host()?.consoleStore(tabId)?.entries()?.let { obs ->
            obs.observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe { list -> consoleAdapter.submitList(list) }
                .let { disposables.add(it) }
        }
    }

    private fun wireElements(v: View) {
        val tree = v.findViewById<RecyclerView>(R.id.dom_tree)
        val props = v.findViewById<TextView>(R.id.props_rows)
        tree.layoutManager = LinearLayoutManager(requireContext())
        tree.adapter = domAdapter
        domAdapter.submit(listOf("(full page HTML below)"))

        fun loadHtml() {
            val js = "(function(){ try { return document.documentElement.outerHTML; } catch(e){ return '<error: '+e.message+'>'; } })();"
            val cb: (String?) -> Unit = { res ->
                activity?.runOnUiThread {
                    val html = res?.let {
                        try { org.json.JSONArray("[$it]").optString(0) } catch (e: Exception) { it.trim('"') }
                    } ?: ""
                    val span = android.text.SpannableString(html)
                    Regex("</?[a-zA-Z][a-zA-Z0-9]*").findAll(html).forEach { m ->
                        span.setSpan(android.text.style.ForegroundColorSpan(0xFF1565C0.toInt()), m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    Regex("""\s[a-zA-Z-]+=""").findAll(html).forEach { m ->
                        span.setSpan(android.text.style.ForegroundColorSpan(0xFF6750A4.toInt()), m.range.first + 1, m.range.last, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    Regex("""="[^"]*"""").findAll(html).forEach { m ->
                        span.setSpan(android.text.style.ForegroundColorSpan(0xFF2E7D32.toInt()), m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).findAll(html).forEach { m ->
                        span.setSpan(android.text.style.ForegroundColorSpan(0xFF79747E.toInt()), m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    props?.text = span
                }
            }
            val wv = host()?.currentWebView()
            if (wv != null) wv.evaluateJavascript(js, android.webkit.ValueCallback { r -> cb(r) }) else host()?.evaluateConsole(tabId, js)
        }
        loadHtml()
    }

    private fun enableInspectMode() {
        val script = "(function(){ if(window.__cpInspect) return; window.__cpInspect=true; " +
            "document.addEventListener('click', function(e){ e.preventDefault(); e.stopPropagation();" +
            "var el=e.target; var r=el.getBoundingClientRect(); var s=getComputedStyle(el);" +
            "var payload={tag:el.tagName,cls:el.className,rect:{x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height)}," +
            "styles:{width:s.width,height:s.height,color:s.color,'background-color':s.backgroundColor,'font-size':s.fontSize,'border-radius':s.borderRadius,display:s.display}};" +
            "console.log('__CP_INSPECT__'+JSON.stringify(payload));" +
            "}, true); })();"
        val wv = host()?.currentWebView()
        if (wv != null) wv.evaluateJavascript(script, null) else host()?.evaluateConsole(tabId, script)
        host()?.consoleStore(tabId)?.entries()?.let { obs ->
            obs.observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe { list ->
                    val last = list.lastOrNull() ?: return@subscribe
                    if (!last.message.startsWith("__CP_INSPECT__")) return@subscribe
                    val json = last.message.removePrefix("__CP_INSPECT__")
                    try {
                        val o = org.json.JSONObject(json)
                        val tag = o.optString("tag")
                        val cls = o.optString("cls")
                        val rect = o.optJSONObject("rect")
                        val styles = o.optJSONObject("styles")
                        val sb = StringBuilder()
                        sb.append("<$tag${if (cls.isNotEmpty()) " class=\"$cls\"" else ""}>\n")
                        if (rect != null) sb.append("rect: ${rect.optInt("w")}x${rect.optInt("h")} @ (${rect.optInt("x")},${rect.optInt("y")})\n")
                        if (styles != null) {
                            val keys = styles.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                sb.append("$k: ${styles.optString(k)}\n")
                            }
                        }
                        val txt = sb.toString().trim()
                        val span = android.text.SpannableString(txt)
                        val lines = txt.split("\n")
                        var idx = 0
                        for (line in lines) {
                            if (line.startsWith("<")) {
                                val tagEnd = line.indexOfAny(charArrayOf(' ', '>'))
                                val te = if (tagEnd < 0) line.length else tagEnd
                                span.setSpan(android.text.style.ForegroundColorSpan(0xFF1565C0.toInt()), idx, idx + te, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                Regex("""(\w+)=("([^"]*)")""").findAll(line).forEach { m ->
                                    val nameStart = idx + m.range.first
                                    val nameEnd = nameStart + m.groupValues[1].length
                                    span.setSpan(android.text.style.ForegroundColorSpan(0xFF6750A4.toInt()), nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    val valStart = idx + m.range.first + m.groupValues[1].length + 1
                                    val valEnd = valStart + m.groupValues[2].length
                                    span.setSpan(android.text.style.ForegroundColorSpan(0xFF2E7D32.toInt()), valStart, valEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                                for (i in line.indices) {
                                    if (line[i] == '<' || line[i] == '>') {
                                        span.setSpan(android.text.style.ForegroundColorSpan(0xFF79747E.toInt()), idx + i, idx + i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    }
                                }
                            } else {
                                val colon = line.indexOf(":")
                                if (colon > 0) {
                                    span.setSpan(android.text.style.ForegroundColorSpan(0xFF6750A4.toInt()), idx, idx + colon, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    span.setSpan(android.text.style.ForegroundColorSpan(0xFFE65100.toInt()), idx + colon + 1, idx + line.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                            }
                            idx += line.length + 1
                        }
                        val props = view?.findViewById<TextView>(R.id.props_rows)
                        props?.text = span
                        android.widget.Toast.makeText(requireContext(), "Inspected: <$tag>", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(requireContext(), "Inspect failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.let { disposables.add(it) }
        }
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
            val url = host()?.currentUrl() ?: "https://example.com"
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
