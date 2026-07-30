package com.krystelligence.solipsism.adblock.custom

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.extensions.setViewWithDialogMargins
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.extensions.toast
import java.net.URI
import javax.inject.Inject

class ElementPickerController @Inject constructor(
    private val activity: Activity,
    private val repository: CustomFilterRepository
) {
    private var webView: WebView? = null
    private var pageUrl = ""
    private var active = false
    private val pickerBridge = PickerBridge()

    fun attach(view: WebView) {
        if (view.getTag(BRIDGE_TAG) == true) return
        view.addJavascriptInterface(pickerBridge, BRIDGE_NAME)
        view.setTag(BRIDGE_TAG, true)
    }

    fun start(view: WebView, url: String) {
        stop()
        attach(view)
        webView = view
        pageUrl = url
        active = true
        view.evaluateJavascript(PICKER_SCRIPT, null)
    }

    fun stop() {
        webView?.let { view ->
            view.evaluateJavascript("window.__solipsismStopPicker && window.__solipsismStopPicker();", null)
            view.removeJavascriptInterface(BRIDGE_NAME)
        }
        active = false
        webView = null
    }

    private fun confirm(selector: String) {
        val host = runCatching { URI(pageUrl).host }.getOrNull().orEmpty()
        if (host.isBlank()) return stop()
        val input = android.widget.EditText(activity).apply { setText(selector); setSingleLine(true); selectAll() }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.block_element)
            .setMessage(activity.getString(R.string.block_element_host, host))
            .setViewWithDialogMargins(input)
            .setNegativeButton(R.string.action_cancel) { _, _ -> stop() }
            .setPositiveButton(R.string.action_ok) { _, _ ->
                runCatching { repository.add("$host##${input.text.toString().trim()}", CustomFilterSource.PICKER) }
                    .onSuccess {
                        val target = webView
                        stop()
                        activity.toast(R.string.block_element_saved)
                        target?.reload()
                    }
                    .onFailure {
                        android.widget.Toast.makeText(
                            activity,
                            it.message ?: activity.getString(R.string.custom_filters_invalid, 1),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        stop()
                    }
            }
            .setOnCancelListener { stop() }
            .show()
    }

    private inner class PickerBridge {
        @JavascriptInterface fun selected(selector: String) {
            if (active) activity.runOnUiThread { confirm(selector) }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "SolipsismElementPicker"
        const val BRIDGE_TAG = 0x534f4c50
        const val PICKER_SCRIPT = """
            (function(){
              'use strict'; if(window.__solipsismPickerActive)return; window.__solipsismPickerActive=true;
              var hovered=null, box=document.createElement('div'); box.id='__solipsism_picker_overlay';
              box.style.cssText='position:fixed;z-index:2147483646;pointer-events:none;border:3px solid #ffb300;background:rgba(255,179,0,.16);display:none;box-sizing:border-box;';
              document.documentElement.appendChild(box);
              function selector(node){
                if(!node||node===document.body||node===document.documentElement)return null;
                if(node.id&&/^[A-Za-z][A-Za-z0-9_-]{0,80}$/.test(node.id))return '#'+CSS.escape(node.id);
                var attrs=['data-testid','data-ad','aria-label','role'];
                for(var i=0;i<attrs.length;i++){var v=node.getAttribute&&node.getAttribute(attrs[i]);if(v&&v.length<80){var a=node.tagName.toLowerCase()+'['+attrs[i]+'="'+CSS.escape(v)+'"]';try{if(document.querySelectorAll(a).length===1)return a;}catch(e){}}}
                var classes=node.classList?Array.prototype.filter.call(node.classList,function(c){return /^[A-Za-z_-][A-Za-z0-9_-]{0,40}$/.test(c)&&!/[0-9a-f]{8,}/i.test(c);}).slice(0,3):[];
                if(classes.length){var c=node.tagName.toLowerCase()+'.'+classes.map(CSS.escape).join('.');try{if(document.querySelectorAll(c).length===1)return c;}catch(e){}}
                var parts=[],cur=node;while(cur&&cur.nodeType===1&&cur!==document.body&&parts.length<5){var p=cur.tagName.toLowerCase(),s=cur.parentElement?Array.prototype.filter.call(cur.parentElement.children,function(x){return x.tagName===cur.tagName;}):[];if(s.length>1)p+=':nth-of-type('+(s.indexOf(cur)+1)+')';parts.unshift(p);cur=cur.parentElement;}return parts.join(' > ');
              }
              function move(e){var n=document.elementFromPoint(e.clientX,e.clientY);if(n===box)return;hovered=n;if(!n){box.style.display='none';return;}var r=n.getBoundingClientRect();box.style.display='block';box.style.left=r.left+'px';box.style.top=r.top+'px';box.style.width=r.width+'px';box.style.height=r.height+'px';}
              function choose(e){e.preventDefault();e.stopPropagation();var s=selector(hovered);if(s)window.SolipsismElementPicker.selected(s);}
              document.addEventListener('mousemove',move,true);document.addEventListener('touchmove',function(e){if(e.touches[0])move(e.touches[0]);},true);document.addEventListener('click',choose,true);
              window.__solipsismStopPicker=function(){document.removeEventListener('mousemove',move,true);document.removeEventListener('click',choose,true);box.remove();window.__solipsismPickerActive=false;};
            })();
        """
    }
}
