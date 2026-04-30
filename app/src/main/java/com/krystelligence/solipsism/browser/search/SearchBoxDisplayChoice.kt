package com.krystelligence.solipsism.browser.search

import com.krystelligence.solipsism.preference.IntEnum

/**
 * An enum representing what detail level should be displayed in the search box.
 */
enum class SearchBoxDisplayChoice(override val value: Int) : IntEnum {
    DOMAIN(0),
    URL(1),
    TITLE(2)
}
