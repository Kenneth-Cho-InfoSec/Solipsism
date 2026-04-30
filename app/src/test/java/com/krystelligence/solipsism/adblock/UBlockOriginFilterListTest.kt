package com.krystelligence.solipsism.adblock

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class UBlockOriginFilterListTest {

    @Test
    fun `domain rules block matching host and subdomains`() {
        val filterList = UBlockOriginFilterList.parse(sequenceOf("||doubleclick.net^"))

        assertThat(filterList.shouldBlock("https://doubleclick.net/ad.js")).isTrue()
        assertThat(filterList.shouldBlock("https://secure.doubleclick.net/ad.js")).isTrue()
        assertThat(filterList.shouldBlock("https://example.com/ad.js")).isFalse()
    }

    @Test
    fun `exception rules override block rules`() {
        val filterList = UBlockOriginFilterList.parse(
            sequenceOf(
                "||example.com^",
                "@@||allowed.example.com^"
            )
        )

        assertThat(filterList.shouldBlock("https://example.com/ads/script.js")).isTrue()
        assertThat(filterList.shouldBlock("https://allowed.example.com/ads/script.js")).isFalse()
    }

    @Test
    fun `wildcard rules block matching request paths`() {
        val filterList = UBlockOriginFilterList.parse(sequenceOf("*://*/tracking/*"))

        assertThat(filterList.shouldBlock("https://example.com/tracking/pixel.gif")).isTrue()
        assertThat(filterList.shouldBlock("https://example.com/assets/pixel.gif")).isFalse()
    }

    @Test
    fun `youtube rules block ad-tagged googlevideo requests only on youtube pages`() {
        val pageUrl = "https://m.youtube.com/watch?v=test"
        val adRequest = "https://rr1---sn.googlevideo.com/videoplayback?id=1&oad=1&ctier=A"
        val videoRequest = "https://rr1---sn.googlevideo.com/videoplayback?id=1&mime=video/mp4"

        assertThat(UBlockOriginYoutubeRules.shouldBlock(pageUrl, adRequest)).isTrue()
        assertThat(UBlockOriginYoutubeRules.shouldBlock(pageUrl, videoRequest)).isFalse()
        assertThat(UBlockOriginYoutubeRules.shouldBlock("https://example.com", adRequest)).isFalse()
    }

    @Test
    fun `youtube rules block youtube ad telemetry and pagead requests`() {
        val pageUrl = "https://www.youtube.com/watch?v=test"

        assertThat(
            UBlockOriginYoutubeRules.shouldBlock(
                pageUrl,
                "https://www.youtube.com/api/stats/ads?ver=2"
            )
        ).isTrue()
        assertThat(
            UBlockOriginYoutubeRules.shouldBlock(
                pageUrl,
                "https://youtubei.googleapis.com/youtubei/v1/player/ad_break?key=test"
            )
        ).isTrue()
    }
}
