package com.krystelligence.solipsism.dialog

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes


/**
 * An item representing a list item in a list dialog. The item has an [icon], [title], an [onClick]
 * function to be invoked when the item is clicked, and a boolean condition [isConditionMet] which
 * defaults to true and allows the consumer to control the visibility of the item in the list.
 */
class DialogItem(
    @param:DrawableRes
    val icon: Int? = null,
    @param:ColorInt
    val colorTint: Int? = null,
    @param:StringRes
    val title: Int,
    val isConditionMet: Boolean = true,
    private val onClick: () -> Unit
) {

    fun onClick() = onClick.invoke()

}
