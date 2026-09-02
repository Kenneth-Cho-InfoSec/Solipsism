package com.krystelligence.solipsism.browser.engine

import androidx.media3.common.MimeTypes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AntaresMediaSourceResolverTest {
    @Test
    fun `resolves a relative direct media source against its page`() {
        val result = AntaresMediaSourceResolver.resolve(
            pageUrl = "https://media.example/episodes/one/",
            directSource = "../video.mp4",
            renewalRequest = null,
            cookies = null,
        )

        assertThat(result.url).isEqualTo("https://media.example/episodes/video.mp4")
        assertThat(result.headers["Referer"]).isEqualTo("https://media.example/episodes/one/")
    }

    @Test
    fun `rejects a non-network direct media source`() {
        assertThatThrownBy {
            AntaresMediaSourceResolver.resolve(
                pageUrl = "https://media.example/watch",
                directSource = "javascript:alert(1)",
                renewalRequest = null,
                cookies = null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects a non-network discovered source`() {
        assertThatThrownBy {
            AntaresMediaSourceResolver.resolve(
                pageUrl = "https://media.example/watch",
                directSource = "blob:https://media.example/temporary",
                renewalRequest = null,
                cookies = null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `extracts a protocol relative stream URL from a generic renewal response`() {
        val source = AntaresMediaSourceResolver.findNetworkUrl(
            """{"payload":{"stream":"//media.example/signed-stream?token=abc"}}""",
        )

        assertThat(source).isEqualTo("//media.example/signed-stream?token=abc")
    }

    @Test
    fun `recognises an HLS source from its standard extension`() {
        val mimeType = AntaresMediaSourceResolver.inferAdaptiveMimeType(
            sourceUrl = "https://media.example/stream/master.m3u8?token=abc",
            contentType = null,
            contentPrefix = null,
        )

        assertThat(mimeType).isEqualTo(MimeTypes.APPLICATION_M3U8)
    }

    @Test
    fun `recognises a signed HLS source from its response type`() {
        val mimeType = AntaresMediaSourceResolver.inferAdaptiveMimeType(
            sourceUrl = "https://media.example/signed-stream?token=abc",
            contentType = "application/vnd.apple.mpegurl; charset=utf-8",
            contentPrefix = null,
        )

        assertThat(mimeType).isEqualTo(MimeTypes.APPLICATION_M3U8)
    }

    @Test
    fun `recognises a signed HLS source from its playlist header`() {
        val mimeType = AntaresMediaSourceResolver.inferAdaptiveMimeType(
            sourceUrl = "https://media.example/signed-stream?token=abc",
            contentType = "application/octet-stream",
            contentPrefix = "\uFEFF#EXTM3U\n#EXT-X-VERSION:3",
        )

        assertThat(mimeType).isEqualTo(MimeTypes.APPLICATION_M3U8)
    }

    @Test
    fun `does not force progressive media into the HLS source factory`() {
        val mimeType = AntaresMediaSourceResolver.inferAdaptiveMimeType(
            sourceUrl = "https://media.example/signed-video?token=abc",
            contentType = "video/mp4",
            contentPrefix = "binary content",
        )

        assertThat(mimeType).isNull()
    }
}
