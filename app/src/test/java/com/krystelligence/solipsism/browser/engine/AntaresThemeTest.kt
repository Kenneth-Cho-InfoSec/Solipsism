package com.krystelligence.solipsism.browser.engine

import com.krystelligence.solipsism.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AntaresThemeTest {
    @Test
    fun `light maps to light`() {
        assertEquals(AntaresProtocol.THEME_LIGHT, AppTheme.LIGHT.toResolvedAntaresTheme())
    }

    @Test
    fun `dark and AMOLED black map to dark`() {
        assertEquals(AntaresProtocol.THEME_DARK, AppTheme.DARK.toResolvedAntaresTheme())
        assertEquals(AntaresProtocol.THEME_DARK, AppTheme.BLACK.toResolvedAntaresTheme())
    }

    @Test
    fun `system must be resolved before protocol mapping`() {
        assertThrows(IllegalStateException::class.java) {
            AppTheme.SYSTEM.toResolvedAntaresTheme()
        }
    }
}
