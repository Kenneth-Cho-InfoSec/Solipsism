package com.krystelligence.solipsism.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptMetadataParserTest {
    @Test
    fun parsesRepeatedMetadataAndDefaults() {
        val metadata = UserScriptMetadataParser.parse(
            """
            // ==UserScript==
            // @name        Example
            // @namespace   https://example.test
            // @match       https://example.test/*
            // @exclude     https://example.test/private/*
            // @grant       none
            // @run-at      document-start
            // ==/UserScript==
            """.trimIndent()
        )

        requireNotNull(metadata)
        assertEquals("Example", metadata.name)
        assertEquals(listOf("https://example.test/*"), metadata.matches)
        assertEquals(UserScriptRunAt.DOCUMENT_START, metadata.runAt)
        assertTrue(metadata.isUnprivileged)
    }

    @Test
    fun rejectsScriptsWithoutAWebsitePattern() {
        assertNull(
            UserScriptMetadataParser.parse(
                """
                // ==UserScript==
                // @name Invalid
                // ==/UserScript==
                """.trimIndent()
            )
        )
    }

    @Test
    fun nativeGrantIsNotConsideredUnprivileged() {
        val metadata = UserScriptMetadataParser.parse(
            """
            // ==UserScript==
            // @name Example
            // @match https://example.test/*
            // @grant GM_setValue
            // ==/UserScript==
            """.trimIndent()
        )

        requireNotNull(metadata)
        assertFalse(metadata.isUnprivileged)
    }

    @Test
    fun parsesRequireDependenciesInOrder() {
        val metadata = UserScriptMetadataParser.parse(
            """
            // ==UserScript==
            // @name Uses dependency
            // @match https://example.test/*
            // @require https://cdn.example.test/one.js
            // @require https://cdn.example.test/two.js
            // ==/UserScript==
            """.trimIndent()
        )

        requireNotNull(metadata)
        assertEquals(
            listOf(
                "https://cdn.example.test/one.js",
                "https://cdn.example.test/two.js"
            ),
            metadata.requires
        )
    }
}
