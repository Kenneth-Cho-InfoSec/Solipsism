package com.krystelligence.solipsism.adblock.custom

import android.webkit.WebView
import com.krystelligence.solipsism.preference.UserPreferences
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CosmeticFilterRuntime @Inject constructor(
    private val repository: CustomFilterRepository,
    private val userPreferences: UserPreferences
) {
    fun injectAfterPageFinished(view: WebView, url: String) {
        if (!userPreferences.adBlockEnabled || !userPreferences.cosmeticFiltersEnabled) return
        val filters = repository.cosmeticFor(url)
        if (filters.isEmpty()) return
        val hide = JSONArray()
        val exceptions = JSONArray()
        filters.filterNot { it.exception }.forEach { hide.put(it.selector) }
        filters.filter { it.exception }.forEach { exceptions.put(it.selector) }
        val source = """
            (function() {
              'use strict';
              var hide = $hide;
              var exceptions = $exceptions;
              var key = 'solipsism-cosmetic-filters';
              var style = document.getElementById(key);
              if (!style) { style = document.createElement('style'); style.id = key; (document.head || document.documentElement).appendChild(style); }
              function apply() {
                var exceptionSet = {};
                exceptions.forEach(function(selector) { try { document.querySelectorAll(selector).forEach(function(node) { exceptionSet[selector + ':' + node] = true; }); } catch (_) {} });
                var rules = [];
                hide.forEach(function(selector) {
                  try { document.querySelectorAll(selector).forEach(function(node) { if (!exceptionSet[selector + ':' + node]) rules.push(selector); }); } catch (_) {}
                });
                style.textContent = Array.from(new Set(rules)).map(function(selector) { return selector + '{display:none !important;}'; }).join('');
              }
              apply();
              if (!window.__solipsismCosmeticObserver) {
                window.__solipsismCosmeticObserver = new MutationObserver(apply);
                window.__solipsismCosmeticObserver.observe(document.documentElement, {childList:true, subtree:true, attributes:true});
              }
            })();
        """.trimIndent()
        view.evaluateJavascript(source, null)
    }
}
