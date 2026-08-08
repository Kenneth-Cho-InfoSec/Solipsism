package com.krystelligence.solipsism.list

import android.view.View
import com.krystelligence.solipsism.R

/** Applies the expressive grouped-row radius model to dialog option rows. */
fun View.applyDialogRowShape(position: Int, itemCount: Int) {
    val background = when {
        itemCount <= 1 -> R.drawable.dialog_group_item_background_single
        position == 0 -> R.drawable.dialog_group_item_background_top
        position == itemCount - 1 -> R.drawable.dialog_group_item_background_bottom
        else -> R.drawable.dialog_group_item_background_middle
    }
    setBackgroundResource(background)
}
