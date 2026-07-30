package com.krystelligence.solipsism.browser.ui

import com.krystelligence.solipsism.preference.IntEnum

/** Placement of the Solipsism rail. Top and bottom are deliberately experimental. */
enum class SolipsismRailPosition(override val value: Int) : IntEnum {
    RIGHT(0),
    LEFT(1),
    TOP(2),
    BOTTOM(3);

    val isExperimental: Boolean
        get() = this == TOP || this == BOTTOM
}
