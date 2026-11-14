package com.krystelligence.solipsism.browser.console

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * In-memory log of network requests intercepted by the WebView client.
 * Entries are added from [com.krystelligence.solipsism.browser.tab.TabWebViewClient.shouldInterceptRequest]
 * and read by the Console+ Network panel.
 */
object NetworkLog {
    data class Entry(val url: String, val status: Int, val sizeBytes: Long, val durationMs: Long, val timestamp: Long)

    private val entries = ConcurrentLinkedQueue<Entry>()

    fun record(url: String, status: Int, sizeBytes: Long, durationMs: Long) {
        entries.add(Entry(url, status, sizeBytes, durationMs, System.currentTimeMillis()))
        // keep last 200
        while (entries.size > 200) entries.poll()
    }

    fun all(): List<Entry> = entries.toList()

    fun clear() = entries.clear()
}
