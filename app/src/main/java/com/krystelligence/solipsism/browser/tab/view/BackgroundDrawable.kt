package com.krystelligence.solipsism.browser.tab.view

import com.krystelligence.solipsism.R
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import androidx.appcompat.content.res.AppCompatResources

/**
 * Create a new transition drawable with the specified list of layers. At least
 * 2 layers are required for this drawable to work properly.
 */
class BackgroundDrawable(
    context: Context
) : TransitionDrawable(
    arrayOf<Drawable>(
        requireNotNull(AppCompatResources.getDrawable(context, R.drawable.tab_background)),
        requireNotNull(AppCompatResources.getDrawable(context, R.drawable.tab_background_selected))
    )
) {

    private var isSelected: Boolean = false

    override fun startTransition(durationMillis: Int) {
        if (!isSelected) {
            super.startTransition(durationMillis)
        }
        isSelected = true
    }

    override fun reverseTransition(duration: Int) {
        if (isSelected) {
            super.reverseTransition(duration)
        }
        isSelected = false
    }

}
