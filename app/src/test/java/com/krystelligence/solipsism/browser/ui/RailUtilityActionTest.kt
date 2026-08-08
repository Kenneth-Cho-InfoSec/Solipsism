package com.krystelligence.solipsism.browser.ui

import com.krystelligence.solipsism.R
import org.junit.Assert.assertEquals
import org.junit.Test

class RailUtilityActionTest {

    @Test
    fun `utility actions expose the expected icons`() {
        assertEquals(R.drawable.ic_action_qr_code, RailUtilityAction.QR.iconRes)
        assertEquals(R.drawable.ic_action_vault, RailUtilityAction.VAULT.iconRes)
        assertEquals(R.drawable.ic_action_screenshot, RailUtilityAction.SCREENSHOT.iconRes)
    }

    @Test
    fun `qr remains the first default action`() {
        assertEquals(RailUtilityAction.QR, RailUtilityAction.values().first())
    }
}
