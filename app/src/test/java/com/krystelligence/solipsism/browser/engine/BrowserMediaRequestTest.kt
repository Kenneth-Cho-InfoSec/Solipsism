package com.krystelligence.solipsism.browser.engine

import android.os.Bundle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserMediaRequestTest {
    @Test
    fun `accepts a media request with a direct network source`() {
        val request = BrowserMediaRequest.fromBundle(Bundle().apply {
            putString(AntaresProtocol.KEY_MEDIA_PAGE_URL, "https://example.test/watch")
            putString(AntaresProtocol.KEY_MEDIA_DIRECT_SOURCE, "/video.mp4")
            putString(AntaresProtocol.KEY_MEDIA_TITLE, "Episode 1")
        })

        assertThat(request?.pageUrl).isEqualTo("https://example.test/watch")
        assertThat(request?.directSource).isEqualTo("/video.mp4")
        assertThat(request?.title).isEqualTo("Episode 1")
    }

    @Test
    fun `rejects an incomplete media request`() {
        val request = BrowserMediaRequest.fromBundle(Bundle().apply {
            putString(AntaresProtocol.KEY_MEDIA_PAGE_URL, "https://example.test/watch")
        })

        assertThat(request).isNull()
    }
}
