package com.krystelligence.solipsism.browser.tab

import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportZoomOverrideTest {

    @Test
    fun `override enables user scaling and raises maximum scale`() {
        val script = ViewportZoomOverride.scriptForTesting()

        assertTrue(script.contains("user-scalable=yes"))
        assertTrue(script.contains("maximum-scale=10"))
        assertTrue(script.contains("MutationObserver"))
    }

    @Test
    fun `override creates viewport when page has none`() {
        val script = ViewportZoomOverride.scriptForTesting()

        assertTrue(script.contains("document.createElement('meta')"))
        assertTrue(script.contains("width=device-width"))
    }
}
