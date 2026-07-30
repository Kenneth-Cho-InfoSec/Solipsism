package com.krystelligence.solipsism.extensions

import com.krystelligence.solipsism.dialog.BrowserDialog
import android.app.Dialog
import android.widget.FrameLayout
import android.view.View
import kotlin.math.roundToInt
import androidx.appcompat.app.AlertDialog

/**
 * Show single choice items.
 *
 * @param items A list of items and their user readable string description.
 * @param checkedItem The item that will be checked when the dialog is displayed.
 * @param onClick Called when an item is clicked. The item clicked is provided.
 */
fun <T> AlertDialog.Builder.withSingleChoiceItems(
    items: List<Pair<T, String>>,
    checkedItem: T,
    onClick: (T) -> Unit
) {
    val checkedIndex = items.map(Pair<T, String>::first).indexOf(checkedItem)
    val titles = items.map(Pair<T, String>::second).toTypedArray()
    setSingleChoiceItems(titles, checkedIndex) { _, which ->
        onClick(items[which].first)
    }
}

/**
 * Ensures that the dialog is appropriately sized and displays it.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun AlertDialog.Builder.resizeAndShow(): Dialog =
    show().also { BrowserDialog.setDialogSize(context, it) }

/** Adds the same 24dp horizontal inset used by Material dialog text content. */
fun AlertDialog.Builder.setViewWithDialogMargins(view: View): AlertDialog.Builder {
    val margin = (24f * context.resources.displayMetrics.density).roundToInt()
    return setView(FrameLayout(context).apply {
        setPadding(margin, 0, margin, 0)
        addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
    })
}
