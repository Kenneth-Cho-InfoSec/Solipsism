package com.krystelligence.solipsism.utils

import com.krystelligence.solipsism.constant.SCHEME_ANTARES_HOMEPAGE
import com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsHomepageTest {
    @Test
    fun `native homepage is a special start page`() {
        assertTrue(SCHEME_HOMEPAGE.isSpecialUrl())
        assertTrue(SCHEME_HOMEPAGE.isStartPageUrl())
    }

    @Test
    fun `Antares homepage is a generated special page`() {
        assertTrue(SCHEME_ANTARES_HOMEPAGE.isSpecialUrl())
        assertTrue(SCHEME_ANTARES_HOMEPAGE.isStartPageUrl())
    }

    @Test
    fun `ordinary Antares web URLs remain visible and navigable`() {
        assertFalse("https://video.example/episode/29905".isSpecialUrl())
        assertFalse("https://example.com/".isStartPageUrl())
    }

    @Test
    fun `legacy generated homepage restores as a start page`() {
        val legacyUrl = "file:///data/user/0/app/files/generated-html/homepage.html"

        assertTrue(legacyUrl.isSpecialUrl())
        assertTrue(legacyUrl.isStartPageUrl())
    }
}
