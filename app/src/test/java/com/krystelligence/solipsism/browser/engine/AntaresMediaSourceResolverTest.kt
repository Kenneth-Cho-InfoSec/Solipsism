package com.krystelligence.solipsism.browser.engine

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
}
