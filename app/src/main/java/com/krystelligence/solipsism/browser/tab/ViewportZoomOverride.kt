package com.krystelligence.solipsism.browser.tab

import android.webkit.WebView

/** Applies the optional accessibility override for pages that disable pinch zoom. */
object ViewportZoomOverride {

    private val SCRIPT = """
        (function() {
          'use strict';
          var marker = 'data-solipsism-zoom-override';
          function apply() {
            var head = document.head || document.getElementsByTagName('head')[0];
            if (!head) return;
            var meta = head.querySelector('meta[name="viewport"]');
            if (!meta) {
              meta = document.createElement('meta');
              meta.name = 'viewport';
              meta.content = 'width=device-width, user-scalable=yes, maximum-scale=10';
              meta.setAttribute(marker, '1');
              head.appendChild(meta);
              return;
            }
            var directives = meta.getAttribute('content') || '';
            var parts = directives.split(',');
            var foundUser = false;
            var foundMaximum = false;
            for (var i = 0; i < parts.length; i++) {
              var pair = parts[i].trim().split('=');
              var name = pair[0].trim().toLowerCase();
              if (name === 'user-scalable') {
                parts[i] = 'user-scalable=yes';
                foundUser = true;
              } else if (name === 'maximum-scale') {
                parts[i] = 'maximum-scale=10';
                foundMaximum = true;
              }
            }
            if (!foundUser) parts.push('user-scalable=yes');
            if (!foundMaximum) parts.push('maximum-scale=10');
            meta.setAttribute('content', parts.join(', '));
            meta.setAttribute(marker, '1');
          }
          apply();
          if (document.head && window.MutationObserver) {
            var observer = new MutationObserver(function(records) {
              for (var i = 0; i < records.length; i++) {
                if (records[i].type === 'childList') { apply(); break; }
              }
            });
            observer.observe(document.head, { childList: true });
            window.setTimeout(function() { observer.disconnect(); }, 30000);
          }
        })();
    """.trimIndent()

    fun applyIfEnabled(view: WebView, enabled: Boolean) {
        if (enabled) view.evaluateJavascript(SCRIPT, null)
    }

    internal fun scriptForTesting(): String = SCRIPT
}
