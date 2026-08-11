package com.krystelligence.solipsism.userscript

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.krystelligence.solipsism.browser.di.IncognitoMode
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import org.json.JSONObject
import javax.inject.Inject

/**
 * Executes the deliberately small, unprivileged userscript MVP.
 *
 * Scripts requesting native grants are stored but not executed until the isolated-world
 * capability broker is implemented. This prevents a userscript from becoming an unrestricted
 * native bridge merely because it contains a @grant line.
 */
class UserScriptRuntime @Inject constructor(
    private val manager: UserScriptManager,
    private val userPreferences: UserPreferences,
    private val logger: Logger,
    @IncognitoMode private val incognitoMode: Boolean
) {
    private val handlers = mutableMapOf<WebView, MutableList<ScriptHandler>>()
    private val hasNativeDocumentEnd = mutableSetOf<WebView>()

    fun attach(webView: WebView) {
        if (!userPreferences.userscriptsEnabled || incognitoMode || !userPreferences.javaScriptEnabled) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            logger.log(TAG, "Userscripts disabled: document-start injection is unavailable")
            return
        }
        handlers.remove(webView)?.forEach(ScriptHandler::remove)
        hasNativeDocumentEnd.remove(webView)
        val newHandlers = mutableListOf<ScriptHandler>()
        val originRules = setOf("*")
        val startSource = dispatcher(manager.matchingSource(UserScriptRunAt.DOCUMENT_START))
        if (startSource.isNotBlank() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            newHandlers += WebViewCompat.addDocumentStartJavaScript(webView, startSource, originRules)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
            val endSource = dispatcher(manager.matchingSource(UserScriptRunAt.DOCUMENT_END))
            if (endSource.isNotBlank()) {
                val pageWorld = WebViewCompat.getExecutionWorld(
                    webView,
                    JavaScriptExecutionWorld.PAGE_WORLD_NAME
                )
                newHandlers += WebViewCompat.addJavaScriptOnEvent(
                    webView,
                    endSource,
                    WebViewCompat.INJECTION_EVENT_DOCUMENT_END,
                    originRules,
                    pageWorld
                )
                hasNativeDocumentEnd += webView
            }
        }
        if (newHandlers.isNotEmpty()) handlers[webView] = newHandlers
    }

    fun injectAfterPageFinished(webView: WebView, url: String) {
        if (!userPreferences.userscriptsEnabled || incognitoMode || !userPreferences.javaScriptEnabled) return
        if (webView !in hasNativeDocumentEnd) {
            inject(webView, url, UserScriptRunAt.DOCUMENT_END)
        }
        webView.postDelayed({ inject(webView, url, UserScriptRunAt.DOCUMENT_IDLE) }, IDLE_DELAY_MS)
    }

    private fun inject(webView: WebView, url: String, runAt: UserScriptRunAt) {
        val scripts = manager.matching(url, runAt)
        if (scripts.isEmpty()) return
        scripts.forEach { script ->
            webView.evaluateJavascript(
                    "try { (0, eval)(${JSONObject.quote(script.executableSource + "\n//# sourceURL=solipsism-userscript-${script.id}.user.js")}); } " +
                    "catch (error) { console.error('Solipsism userscript ${script.id} failed', error); }",
                null
            )
        }
    }

    private fun dispatcher(scripts: List<UserScript>): String {
        if (scripts.isEmpty()) return ""
        val entries = scripts.joinToString(",") { script ->
            "{patterns:${jsonArray(script.metadata.matches)},includes:${jsonArray(script.metadata.includes)}," +
                "excludes:${jsonArray(script.metadata.excludes)},source:${JSONObject.quote(script.executableSource)}," +
                "id:${JSONObject.quote(script.id)}}"
        }
        return """
            (function() {
              if (location.protocol !== 'http:' && location.protocol !== 'https:') return;
              if (window.top !== window.self) return;
              var scripts = [$entries];
              var glob = function(pattern, value) {
                if (pattern.length > 2048 || value.length > 32768) return false;
                var escaped = pattern.split('*').map(function(part) {
                  return part.replace(/[.*+?^${'$'}()|[\]\\]/g, '\\${'$'}&');
                }).join('.*').replace(/\\?/g, '.');
                return new RegExp('^' + escaped + '${'$'}').test(value);
              };
              var match = function(pattern, url) {
                var separator = pattern.indexOf('://');
                if (separator < 1) return false;
                var scheme = pattern.substring(0, separator).toLowerCase();
                var rest = pattern.substring(separator + 3);
                var slash = rest.indexOf('/');
                var hostPattern = slash < 0 ? rest : rest.substring(0, slash);
                var pathPattern = slash < 0 ? '/*' : rest.substring(slash);
                var parsed;
                try { parsed = new URL(url); } catch (e) { return false; }
                if (scheme !== '*' && scheme !== parsed.protocol.slice(0, -1).toLowerCase()) return false;
                var host = parsed.hostname.toLowerCase();
                var hostMatches = hostPattern === '*' ||
                  (hostPattern.indexOf('*.') === 0 &&
                    host !== hostPattern.slice(2) && host.endsWith(hostPattern.slice(1))) ||
                  host === hostPattern.toLowerCase();
                return hostMatches && glob(pathPattern, parsed.pathname || '/');
              };
              scripts.forEach(function(script) {
                var included = script.patterns.some(function(pattern) { return match(pattern, location.href); }) ||
                  script.includes.some(function(pattern) { return glob(pattern, location.href); });
                var excluded = script.excludes.some(function(pattern) {
                  return match(pattern, location.href) || glob(pattern, location.href);
                });
                if (!included || excluded) return;
                try {
                  (0, eval)(script.source + '\\n//# sourceURL=solipsism-userscript-' + script.id + '.user.js');
                } catch (error) {
                  console.error('Solipsism userscript ' + script.id + ' failed', error);
                }
              });
            })();
        """.trimIndent()
    }

    private fun jsonArray(values: List<String>) = values.joinToString(",", "[", "]", transform = JSONObject::quote)

    private companion object {
        const val TAG = "UserScriptRuntime"
        const val IDLE_DELAY_MS = 50L
    }
}

private fun UserScriptManager.matchingSource(runAt: UserScriptRunAt): List<UserScript> =
    all().filter { it.enabled && it.metadata.isUnprivileged && it.metadata.runAt == runAt }
