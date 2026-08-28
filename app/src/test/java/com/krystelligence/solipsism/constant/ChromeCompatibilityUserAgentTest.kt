package com.krystelligence.solipsism.constant

import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeCompatibilityUserAgentTest {
    @Test
    fun `uses installed provider major and Chrome reduced Android shape`() {
        val provider = "Mozilla/5.0 (Linux; Android 17; sdk_gphone64_x86_64 Build/ABC; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/145.0.7632.218 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
            chromeCompatibilityUserAgent(provider),
        )
    }

    @Test
    fun `does not invent a version when provider has no Chromium product`() {
        val provider = "ExampleBrowser/1.0"

        assertEquals(provider, chromeCompatibilityUserAgent(provider))
    }

    @Test
    fun `extracts matching Chromium major and full versions`() {
        assertEquals(
            ChromiumVersion("145", "145.0.7632.218"),
            chromiumVersion(
                "Mozilla/5.0 Version/4.0 Chrome/145.0.7632.218 Mobile Safari/537.36"
            ),
        )
    }
}
