package com.krystelligence.solipsism

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppThemeTest {

    @Test
    @Config(qualifiers = "notnight")
    fun `system theme follows a light system configuration`() {
        assertThat(AppTheme.SYSTEM.effective(RuntimeEnvironment.getApplication()))
            .isEqualTo(AppTheme.LIGHT)
    }

    @Test
    @Config(qualifiers = "night")
    fun `system theme follows a dark system configuration`() {
        assertThat(AppTheme.SYSTEM.effective(RuntimeEnvironment.getApplication()))
            .isEqualTo(AppTheme.DARK)
    }

    @Test
    fun `explicit themes are not changed by the system configuration`() {
        val context = RuntimeEnvironment.getApplication()

        assertThat(AppTheme.LIGHT.effective(context)).isEqualTo(AppTheme.LIGHT)
        assertThat(AppTheme.DARK.effective(context)).isEqualTo(AppTheme.DARK)
        assertThat(AppTheme.BLACK.effective(context)).isEqualTo(AppTheme.BLACK)
    }
}
