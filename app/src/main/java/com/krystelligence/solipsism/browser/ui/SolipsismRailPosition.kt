package com.krystelligence.solipsism.browser.ui

import com.krystelligence.solipsism.preference.IntEnum

/** Placement of the Solipsism rail. */
enum class SolipsismRailPosition(override val value: Int) : IntEnum {
    RIGHT(0),
    LEFT(1),
    TOP(2),
    BOTTOM(3);

    /** Internal layout selector. Top and bottom use the horizontal implementation. */
    val isExperimental: Boolean
        get() = this == TOP || this == BOTTOM
}
