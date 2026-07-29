package com.krystelligence.solipsism.html.homepage

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StaticHomepageSanitizerTest {

    @Test
    fun removesExecutableAndRemoteElements() {
        val result = StaticHomepageSanitizer.sanitize(
            """
            <h1>Hello</h1>
            <script>alert(1)</script>
            <img src="https://example.com/tracker.gif">
            <iframe src="https://evil.example"></iframe>
            <a href="javascript:alert(1)">bad</a>
            <a href="https://example.com">safe</a>
            """.trimIndent()
        )

        assertThat(result).contains("Hello", "https://example.com")
        assertThat(result).doesNotContain("script", "iframe", "tracker.gif", "javascript:")
    }

    @Test
    fun removesCssAndUnsafeFunctions() {
        val result = StaticHomepageSanitizer.sanitize(
            "<p style=\"color: red; background-image: url(https://evil.example/x)\">Text</p>"
        )
        assertThat(result).contains("Text")
        assertThat(result).doesNotContain("url(", "background-image", "style=")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHtmlOverDoubledLimit() {
        StaticHomepageSanitizer.sanitize("x".repeat(StaticHomepageSanitizer.MAX_HTML_BYTES + 1))
    }
}
