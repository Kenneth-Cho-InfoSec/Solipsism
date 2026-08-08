package com.krystelligence.solipsism.database.bookmark

import com.krystelligence.solipsism.preference.IntEnum

enum class BookmarkSortOrder(override val value: Int) : IntEnum {
    MANUAL(0),
    TITLE_ASC(1),
    TITLE_DESC(2),
    URL_ASC(3),
    URL_DESC(4)
}
