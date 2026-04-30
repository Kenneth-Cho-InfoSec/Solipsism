package com.krystelligence.solipsism.browser.ui

import com.krystelligence.solipsism.preference.IntEnum

/**
 * Supported tab display configurations.
 */
enum class TabConfiguration(override val value: Int) : IntEnum {
    DESKTOP(0),
    DRAWER_SIDE(1),
    DRAWER_BOTTOM(2)
}
