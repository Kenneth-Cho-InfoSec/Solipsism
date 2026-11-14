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
        fun elementsHtmlBeautify(): Boolean
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
        val selectedColor = 0xFFFFFFFF.toInt()
        val normalColor = 0xB3FFFFFF.toInt() // white, ~70% opacity when unfocused
        tabs.setTabTextColors(normalColor, selectedColor)
        tabs.setSelectedTabIndicatorColor(selectedColor)
        tabs.isTabIndicatorFullWidth = true
        tabs.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        var elementsLoaded = false
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val i = tab?.position ?: 0
                panels.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
                if (i == 1 && !elementsLoaded) {
                    elementsLoaded = true
                    loadElementsHtml(panels[1])
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        close.setOnClickListener { dismiss() }

        // Console panel wiring
        // Hide duplicated chrome: Console+ provides the drag handle, title, and close.
        val consolePanel = panels[0] as? ViewGroup
        consolePanel?.getChildAt(0)?.visibility = View.GONE // drag handle
        (consolePanel?.getChildAt(1) as? ViewGroup)?.let { header ->
            // title TextView (index 0), context (index 1), spacer (index 2), clear (index 3), close (index 4)
            header.getChildAt(0)?.visibility = View.GONE
            header.findViewById<View>(R.id.console_context)?.visibility = View.GONE
            header.findViewById<View>(R.id.console_clear)?.visibility = View.GONE
            header.findViewById<View>(R.id.console_close)?.visibility = View.GONE
        }
        wireConsole(panels[0])
        // Elements (lazy-loaded when tab selected)
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
        fun runCommand() {
            val input = v.findViewById<android.widget.EditText>(R.id.console_input)
            val code = input.text.toString()
            val trimmed = code.trim()
            if (trimmed.isEmpty()) return
            if (trimmed == "clear") {
                host()?.consoleStore(tabId)?.clear()
            } else {
                host()?.evaluateConsole(tabId, code)
            }
            input.text.clear()
        }
        v.findViewById<View>(R.id.console_run).setOnClickListener { runCommand() }
        v.findViewById<android.widget.EditText>(R.id.console_input).setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                runCommand(); true
            } else false
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
        val emptyView = v.findViewById<View>(R.id.console_empty)
        host()?.consoleStore(tabId)?.entries()?.let { obs ->
            obs.observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe { list ->
                    consoleAdapter.submitList(list)
                    emptyView?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
                .let { disposables.add(it) }
        }
    }

    private fun wireElements(v: View) {
        val tree = v.findViewById<RecyclerView>(R.id.dom_tree)
        val props = v.findViewById<TextView>(R.id.props_rows)
        tree.layoutManager = LinearLayoutManager(requireContext())
        tree.adapter = domAdapter
        domAdapter.submit(listOf("(select Elements to load page HTML)"))
        props?.text = ""
    }

    private fun loadElementsHtml(v: View) {
        val props = v.findViewById<TextView>(R.id.props_rows)
        domAdapter.submit(listOf("(loading page HTML…)"))
        props?.text = ""

        val js = "(function(){ try { return document.documentElement.outerHTML; } catch(e){ return '<error: '+e.message+'>'; } })();"
        val beautify = host()?.elementsHtmlBeautify() ?: true
        val cb: (String?) -> Unit = { res ->
            Thread {
                val raw = res?.let {
                    try { org.json.JSONArray("[$it]").optString(0) } catch (_: Exception) { it.trim('"') }
                } ?: ""
                val max = 20_000
                val truncated = raw.length > max
                var html = if (truncated) raw.take(max) else raw
                if (beautify) html = beautifyHtmlCode(html)
                if (truncated) html += "\n\n… (truncated, ${raw.length} chars total)"
                val highlighted: CharSequence = try {
                    highlightHtmlWithEmbedded(html)
                } catch (_: Throwable) {
                    html
                }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    try {
                        domAdapter.submit(listOf(if (truncated) "Full page HTML (truncated, beautified=${beautify})" else "Full page HTML (beautified=${beautify})"))
                        props?.text = highlighted
                    } catch (_: Throwable) {
                        domAdapter.submit(listOf("(failed to display page HTML)"))
                    }
                }
            }.start()
        }
        val wv = host()?.currentWebView()
        if (wv != null) wv.evaluateJavascript(js, android.webkit.ValueCallback { r -> cb(r) })
        else domAdapter.submit(listOf("(page HTML unavailable for this tab)"))
    }

    private fun beautifyHtmlCode(input: String): String {
        val s = input.replace("\r\n", "\n")
        val voidTags = setOf("area","base","br","col","embed","hr","img","input","link","meta","param","source","track","wbr")
        val out = StringBuilder(s.length + 256)
        var i = 0
        var indent = 0
        fun appendIndent() { repeat(indent.coerceAtLeast(0)) { out.append("  ") } }
        while (i < s.length) {
            when {
                s.startsWith("<!--", i) -> {
                    val end = s.indexOf("-->", i + 4).let { if (it == -1) s.length else it + 3 }
                    appendIndent(); out.append(s.substring(i, end)).append('\n')
                    i = end
                }
                s.startsWith("<?", i) -> {
                    val end = s.indexOf("?>", i + 2).let { if (it == -1) s.length else it + 2 }
                    val block = s.substring(i, end)
                    block.lines().forEach { line ->
                        appendIndent(); out.append(line.trimEnd()).append('\n')
                    }
                    i = end
                }
                s.startsWith("</", i) -> {
                    val end = s.indexOf('>', i).let { if (it == -1) s.length else it + 1 }
                    indent = (indent - 1).coerceAtLeast(0)
                    appendIndent(); out.append(s.substring(i, end).trim()).append('\n')
                    i = end
                }
                s[i] == '<' -> {
                    val end = s.indexOf('>', i).let { if (it == -1) s.length else it + 1 }
                    val tag = s.substring(i, end)
                    val nameMatch = Regex("^<\\s*([a-zA-Z0-9:-]+)").find(tag)
                    val name = nameMatch?.groupValues?.get(1)?.lowercase() ?: ""
                    val selfClosing = tag.trimEnd().endsWith("/>") || name in voidTags
                    appendIndent(); out.append(tag.trim()).append('\n')
                    if (name == "script" || name == "style") {
                        val closeTag = "</$name>"
                        val closeIdx = s.indexOf(closeTag, end, ignoreCase = true)
                        val contentEnd = if (closeIdx == -1) s.length else closeIdx
                        val content = s.substring(end, contentEnd)
                        if (content.isNotBlank()) {
                            indent++
                            content.trim('\n', '\r').lines().forEach { line ->
                                if (line.isBlank()) out.append('\n') else { appendIndent(); out.append(line.trimEnd()).append('\n') }
                            }
                            indent = (indent - 1).coerceAtLeast(0)
                        }
                        if (closeIdx != -1) {
                            val cEnd = s.indexOf('>', closeIdx).let { if (it == -1) s.length else it + 1 }
                            appendIndent(); out.append(s.substring(closeIdx, cEnd).trim()).append('\n')
                            i = cEnd
                        } else {
                            i = contentEnd
                        }
                        continue
                    }
                    if (!selfClosing && name.isNotEmpty() && !name.startsWith("!")) indent++
                    i = end
                }
                else -> {
                    val next = s.indexOf('<', i).let { if (it == -1) s.length else it }
                    val text = s.substring(i, next)
                    if (text.isNotBlank()) {
                        text.trim('\n', '\r').lines().forEach { line ->
                            if (line.isNotBlank()) { appendIndent(); out.append(line.trim()).append('\n') }
                        }
                    }
                    i = next
                }
            }
        }
        return out.toString().trimEnd()
    }

    private fun highlightHtmlWithEmbedded(html: String): CharSequence {
        val span = android.text.SpannableString(html)
        // Base HTML highlighting
        Regex("</?[a-zA-Z][a-zA-Z0-9:-]*").findAll(html).forEach { m ->
            span.setSpan(android.text.style.ForegroundColorSpan(0xFF4FC3F7.toInt()), m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Regex("\\s([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=").findAll(html).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            span.setSpan(android.text.style.ForegroundColorSpan(0xFFCE93D8.toInt()), g.range.first, g.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Regex("=\\s*(\"[^\"]*\"|'[^']*')").findAll(html).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            span.setSpan(android.text.style.ForegroundColorSpan(0xFFA5D6A7.toInt()), g.range.first, g.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).findAll(html).forEach { m ->
            span.setSpan(android.text.style.ForegroundColorSpan(0xFF9E9E9E.toInt()), m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // PHP blocks
        Regex("<\\?(?:php|=)?[\\s\\S]*?\\?>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            highlightPhp(span, html, m.range.first, m.range.last + 1)
        }
        // <style> blocks
        Regex("<style\\b[^>]*>([\\s\\S]*?)</style>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            highlightCss(span, html, g.range.first, g.range.last + 1)
        }
        // <script> blocks
        Regex("<script\\b[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            highlightJs(span, html, g.range.first, g.range.last + 1)
        }
        return span
    }

    private fun highlightCss(span: android.text.SpannableString, html: String, start: Int, end: Int) {
        val region = html.substring(start, end)
        fun color(regex: String, c: Int, group: Int = 0) {
            Regex(regex, setOf(RegexOption.DOT_MATCHES_ALL)).findAll(region).forEach { m ->
                val g = if (group == 0) m.range else (m.groups[group]?.range ?: return@forEach)
                span.setSpan(android.text.style.ForegroundColorSpan(c), start + g.first, start + g.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        color("/\\*.*?\\*/", 0xFF9E9E9E.toInt()) // comments
        color("\"[^\"]*\"|'[^']*'", 0xFFA5D6A7.toInt()) // strings
        color("#[0-9a-fA-F]{3,8}\\b", 0xFFFFCC80.toInt()) // colors
        color("\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms)?\\b", 0xFFFFCC80.toInt()) // numbers/units
        color("\\b[a-zA-Z-]+(?=\\s*:)", 0xFF80DEEA.toInt()) // properties
        color("[.#]?[a-zA-Z_][\\w-]*(?=\\s*[{,])", 0xFFFFF59D.toInt()) // selectors (rough)
    }

    private fun highlightJs(span: android.text.SpannableString, html: String, start: Int, end: Int) {
        val region = html.substring(start, end)
        fun color(regex: String, c: Int, group: Int = 0) {
            Regex(regex, setOf(RegexOption.MULTILINE)).findAll(region).forEach { m ->
                val g = if (group == 0) m.range else (m.groups[group]?.range ?: return@forEach)
                span.setSpan(android.text.style.ForegroundColorSpan(c), start + g.first, start + g.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        color("//.*", 0xFF9E9E9E.toInt()) // single-line comments
        color("/\\*[\\s\\S]*?\\*/", 0xFF9E9E9E.toInt()) // block comments
        color("\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|`(?:\\\\.|[^`])*`", 0xFFA5D6A7.toInt()) // strings
        color("\\b(function|var|let|const|if|else|return|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|class|extends|import|export|default|async|await|typeof|instanceof|in|of|true|false|null|undefined|this)\\b", 0xFFCE93D8.toInt()) // keywords
        color("\\b\\d+(?:\\.\\d+)?\\b", 0xFFFFCC80.toInt()) // numbers
        color("\\b([a-zA-Z_$][\\w$]*)(?=\\s*\\()", 0xFF80DEEA.toInt(), 1) // function calls
    }

    private fun highlightPhp(span: android.text.SpannableString, html: String, start: Int, end: Int) {
        val region = html.substring(start, end)
        fun color(regex: String, c: Int, group: Int = 0, opts: Set<RegexOption> = setOf(RegexOption.MULTILINE)) {
            Regex(regex, opts).findAll(region).forEach { m ->
                val g = if (group == 0) m.range else (m.groups[group]?.range ?: return@forEach)
                span.setSpan(android.text.style.ForegroundColorSpan(c), start + g.first, start + g.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        color("//.*", 0xFF9E9E9E.toInt())
        color("#.*", 0xFF9E9E9E.toInt())
        color("/\\*[\\s\\S]*?\\*/", 0xFF9E9E9E.toInt(), 0, setOf(RegexOption.DOT_MATCHES_ALL))
        color("\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'", 0xFFA5D6A7.toInt())
        color("\\$[a-zA-Z_\\x7f-\\xff][a-zA-Z0-9_\\x7f-\\xff]*", 0xFFFFCC80.toInt()) // variables
        color("\\b(echo|print|function|return|if|else|elseif|foreach|for|while|switch|case|break|continue|class|public|private|protected|static|new|use|namespace|require|include|require_once|include_once|array|true|false|null)\\b", 0xFFCE93D8.toInt(), 0, setOf(RegexOption.IGNORE_CASE))
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
            val entries = NetworkLog.all()
            val items = entries.map { e ->
                val short = e.url.removePrefix("https://").removePrefix("http://").take(48)
                val status = if (e.status <= 0) "—" else e.status.toString()
                "$status  $short  ${e.sizeBytes}B  ${e.durationMs}ms"
            }
            netAdapter.submit(if (items.isEmpty()) listOf("(no intercepted requests yet — reload the page to capture)") else items)
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

        fun currentWv(): WebView? = host()?.currentWebView()

        fun loadCookies() {
            val url = host()?.currentUrl() ?: currentWv()?.url ?: ""
            if (url.isBlank()) {
                kvAdapter.submit(listOf("(no URL available for cookies)"))
                return
            }
            val cookieStr = android.webkit.CookieManager.getInstance().getCookie(url) ?: ""
            val items = cookieStr.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            kvAdapter.submit(if (items.isEmpty()) listOf("(no cookies for $url)") else items)
        }
        fun loadJsonStorage(kind: String) {
            val wv = currentWv()
            if (wv == null) {
                kvAdapter.submit(listOf("($kind unavailable: no WebView for this tab)"))
                return
            }
            val js = "(function(){try{return JSON.stringify(Object.keys($kind).map(function(k){return k+' = '+$kind.getItem(k);}));}catch(e){return JSON.stringify(['<error: '+e.message+'>']);}})();"
            wv.evaluateJavascript(js) { res ->
                activity?.runOnUiThread {
                    val arrJson = try { org.json.JSONArray("[$res]").optString(0, "[]") } catch (_: Exception) { "[]" }
                    val arr = try { org.json.JSONArray(arrJson) } catch (_: Exception) { org.json.JSONArray() }
                    val items = (0 until arr.length()).map { arr.optString(it) }
                    kvAdapter.submit(if (items.isEmpty()) listOf("(empty)") else items)
                }
            }
        }
        loadCookies()
        v.findViewById<Button>(R.id.store_cookies).setOnClickListener { loadCookies() }
        v.findViewById<Button>(R.id.store_local).setOnClickListener { loadJsonStorage("localStorage") }
        v.findViewById<Button>(R.id.store_session).setOnClickListener { loadJsonStorage("sessionStorage") }
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
            val typedValue = android.util.TypedValue()
            val theme = context.theme
            val resolved = theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                || theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            if (resolved) {
                setTextColor(typedValue.data)
            } else {
                val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                setTextColor(if (night) 0xFFFFFFFF.toInt() else 0xFF1C1B1F.toInt())
            }
            // Ensure good contrast in dark-themed sheets
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (night) setTextColor(0xFFECECEC.toInt())
        }
        return VH(tv)
    }
    override fun onBindViewHolder(holder: VH, position: Int) { (holder.itemView as android.widget.TextView).text = items[position] }
    class VH(v: View) : RecyclerView.ViewHolder(v)
}
