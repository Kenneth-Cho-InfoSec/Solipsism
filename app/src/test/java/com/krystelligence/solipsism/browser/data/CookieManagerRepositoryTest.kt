package com.krystelligence.solipsism.browser.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CookieManagerRepositoryTest {

    @Test
    fun `parses values containing equals`() {
        val cookies = CookieManagerRepository.parseCookieHeader("session=abc=123; theme=dark")

        assertThat(cookies).containsExactly(
            BrowserCookie("session", "abc=123"),
            BrowserCookie("theme", "dark")
        )
    }

    @Test
    fun `ignores malformed cookie pairs`() {
        val cookies = CookieManagerRepository.parseCookieHeader("; malformed; valid=value")

        assertThat(cookies).containsExactly(BrowserCookie("valid", "value"))
    }

    @Test
    fun `recognizes only http and https urls`() {
        assertThat(CookieManagerRepository.isManageableUrl("https://example.com/path")).isTrue()
        assertThat(CookieManagerRepository.isManageableUrl("http://localhost:8080")).isTrue()
        assertThat(CookieManagerRepository.isManageableUrl("file:///tmp/page.html")).isFalse()
        assertThat(CookieManagerRepository.isManageableUrl("not a url")).isFalse()
    }
}
