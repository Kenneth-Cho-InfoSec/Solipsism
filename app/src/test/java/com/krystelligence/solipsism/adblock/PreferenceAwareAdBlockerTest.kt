package com.krystelligence.solipsism.adblock

import com.krystelligence.solipsism.preference.UserPreferences
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class PreferenceAwareAdBlockerTest {
    private val preferences = mock(UserPreferences::class.java)
    private val hostsBlocker = mock(BloomFilterAdBlocker::class.java)
    private val uBlockBlocker = mock(UBlockOriginAdBlocker::class.java)
    private val blocker = PreferenceAwareAdBlocker(preferences, hostsBlocker, uBlockBlocker)

    @Test
    fun `disabled ad blocking does not consult any engine`() {
        `when`(preferences.adBlockEnabled).thenReturn(false)

        assertThat(blocker.isAd("https://ads.example/banner.js")).isFalse()
        verifyNoInteractions(hostsBlocker, uBlockBlocker)
    }

    @Test
    fun `enabling ad blocking takes effect on the next request`() {
        `when`(preferences.adBlockEnabled).thenReturn(false, true)
        `when`(hostsBlocker.isAd("https://ads.example/banner.js")).thenReturn(true)

        assertThat(blocker.isAd("https://ads.example/banner.js")).isFalse()
        assertThat(blocker.isAd("https://ads.example/banner.js")).isTrue()
    }

    @Test
    fun `uBlock rules are only consulted when their preference is enabled`() {
        `when`(preferences.adBlockEnabled).thenReturn(true)
        `when`(preferences.uBlockOriginEnabled).thenReturn(false)
        `when`(hostsBlocker.isAd("https://cdn.example/ads/banner.js", "https://example.com"))
            .thenReturn(false)

        assertThat(
            blocker.isAd("https://cdn.example/ads/banner.js", "https://example.com"),
        ).isFalse()
        verify(uBlockBlocker, never()).isAd(
            "https://cdn.example/ads/banner.js",
            "https://example.com",
        )
    }
}
