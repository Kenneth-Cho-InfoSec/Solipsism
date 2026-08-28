package com.krystelligence.solipsism.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageElementDescriptorTest {
    @Test
    fun `same link is recognised when layout differs`() {
        val webView = descriptor(
            tag = "a",
            text = "Sign in securely",
            href = "https://www.amazon.co.uk/ap/signin/",
            left = 10,
        )
        val antares = descriptor(
            tag = "a",
            text = "Sign in securely",
            href = "https://www.amazon.co.uk/ap/signin",
            left = 42,
        )

        assertTrue(webView.sameInteractiveTarget(antares))
        assertFalse(webView.sameGeometry(antares))
    }

    @Test
    fun `different controls are rejected even at the same coordinate`() {
        val webView = descriptor(tag = "button", id = "accept", text = "Accept all")
        val antares = descriptor(tag = "button", id = "reject", text = "Reject all")

        assertFalse(webView.sameInteractiveTarget(antares))
    }

    @Test
    fun `matching accessible label identifies icon buttons`() {
        val webView = descriptor(tag = "button", label = "Open basket")
        val antares = descriptor(tag = "button", label = "open basket")

        assertTrue(webView.sameInteractiveTarget(antares))
    }

    private fun descriptor(
        tag: String,
        id: String = "",
        text: String = "",
        href: String = "",
        label: String = "",
        left: Int = 10,
    ) = PageElementDescriptor(
        empty = false,
        tag = tag,
        id = id,
        name = "",
        type = "",
        role = "",
        label = label,
        text = text,
        href = href,
        left = left,
        top = 20,
        right = 100,
        bottom = 60,
        viewportWidth = 360,
        viewportHeight = 800,
        devicePixelRatio = 3.0,
        viewportScale = 1.0,
    )
}
