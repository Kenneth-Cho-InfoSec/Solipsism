package com.krystelligence.solipsism.browser.engine

import android.content.Context
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BrowserCoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("browser_core", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `first install defaults to WebView without completing onboarding`() {
        val preferences = BrowserCorePreferences(context)

        assertThat(preferences.selectedCore).isEqualTo(BrowserCore.WEBVIEW)
        assertThat(preferences.onboardingComplete).isFalse()
    }

    @Test
    fun `unknown persisted core falls back to WebView`() {
        context.getSharedPreferences("browser_core", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_core", "unknown")
            .commit()

        assertThat(BrowserCorePreferences(context).selectedCore).isEqualTo(BrowserCore.WEBVIEW)
    }

    @Test
    fun `selection is global and persisted`() {
        BrowserCorePreferences(context).select(BrowserCore.WEBVIEW)

        val restored = BrowserCorePreferences(context)
        assertThat(restored.selectedCore).isEqualTo(BrowserCore.WEBVIEW)
        assertThat(restored.onboardingComplete).isTrue()
    }

}
