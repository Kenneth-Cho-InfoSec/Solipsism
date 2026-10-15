package com.krystelligence.solipsism.utils

import android.app.Activity
import android.webkit.WebView
import com.krystelligence.solipsism.SDK_VERSION
import com.krystelligence.solipsism.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [IntentUtils] external-navigation gating. Web destinations must
 * always stay inside Solipsism's WebView and never start an external activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class IntentUtilsTest {

    private lateinit var activity: Activity
    private lateinit var intentUtils: IntentUtils
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        intentUtils = IntentUtils(activity)
        webView = WebView(activity)
    }

    @Test
    fun `intent url with https destination loads in webview without external activity`() {
        val handled = intentUtils.startActivityForUrl(
            webView,
            "intent://www.youtube.com/watch?v=test#Intent;scheme=https;end"
        )

        assertThat(handled).isTrue()
        assertThat(shadowOf(webView).lastLoadedUrl)
            .isEqualTo("https://www.youtube.com/watch?v=test")
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `plain https url is not consumed`() {
        val handled = intentUtils.startActivityForUrl(webView, "https://example.com/")

        assertThat(handled).isFalse()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `intent url with non-web destination is not consumed`() {
        val handled = intentUtils.startActivityForUrl(
            webView,
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        )

        assertThat(handled).isFalse()
        assertThat(shadowOf(webView).lastLoadedUrl).isNull()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `malformed intent url is not consumed`() {
        val handled = intentUtils.startActivityForUrl(webView, "intent://%%%")

        assertThat(handled).isFalse()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }
}
