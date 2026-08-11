package com.krystelligence.solipsism.settings.activity

import android.content.Intent
import com.krystelligence.solipsism.DefaultBrowserActivity
import com.krystelligence.solipsism.IncognitoBrowserActivity
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsNavigationTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `normal settings return targets default browser`() {
        val intent = SettingsNavigation.createBrowserIntent(context, incognito = false)

        assertThat(intent.component?.className).isEqualTo(DefaultBrowserActivity::class.java.name)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotZero()
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP).isNotZero()
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP).isNotZero()
    }

    @Test
    fun `incognito settings return targets incognito browser`() {
        val intent = SettingsNavigation.createBrowserIntent(context, incognito = true)

        assertThat(intent.component?.className).isEqualTo(IncognitoBrowserActivity::class.java.name)
    }
}
