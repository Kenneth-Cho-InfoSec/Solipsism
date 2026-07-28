package com.krystelligence.solipsism.userscript

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptUrlMatcherTest {
    private val metadata = UserScriptMetadata(
        name = "Example",
        namespace = "test",
        version = "1",
        description = "",
        matches = listOf("https://*.example.test:8443/path/*"),
        includes = emptyList(),
        excludes = listOf("https://private.example.test/*"),
        grants = listOf("none"),
        requires = emptyList(),
        runAt = UserScriptRunAt.DOCUMENT_START,
        noFrames = false,
        updateUrl = null,
        downloadUrl = null
    )

    @Test
    fun matchesSubdomainsAndExplicitPorts() {
        assertTrue(UserScriptUrlMatcher.matches(metadata, "https://www.example.test:8443/path/page"))
        assertFalse(UserScriptUrlMatcher.matches(metadata, "https://www.example.test/path/page"))
        assertFalse(UserScriptUrlMatcher.matches(metadata, "https://example.test:8443/path/page"))
    }

    @Test
    fun exclusionsOverrideMatches() {
        val broad = metadata.copy(matches = listOf("https://*.example.test/*"))
        assertFalse(UserScriptUrlMatcher.matches(broad, "https://private.example.test/account"))
    }

    @Test
    fun nonWebSchemesNeverMatch() {
        val broad = metadata.copy(matches = listOf("*://*/*"))
        assertFalse(UserScriptUrlMatcher.matches(broad, "file:///tmp/page.html"))
    }
}
