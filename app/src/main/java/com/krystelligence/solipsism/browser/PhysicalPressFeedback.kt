package com.krystelligence.solipsism.browser

import android.animation.TimeInterpolator
import android.view.MotionEvent
import android.view.View

/**
 * Adds the browser chrome's press animation while leaving click dispatch to [View].
 *
 * Returning `false` lets Android deliver the gesture to the view's normal click handler. Calling
 * `performClick()` here as well would dispatch the same action twice for one tap.
 */
internal fun View.applyPhysicalPressFeedback(
    pressInterpolator: TimeInterpolator,
    releaseInterpolator: TimeInterpolator
) {
    setOnTouchListener { touchedView, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchedView.animate()
                .scaleX(PRESSED_SCALE)
                .scaleY(PRESSED_SCALE)
                .setDuration(PRESS_FEEDBACK_DURATION_MS)
                .setInterpolator(pressInterpolator)
                .start()

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> touchedView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(RELEASE_FEEDBACK_DURATION_MS)
                .setInterpolator(releaseInterpolator)
                .start()
        }
        false
    }
}

private const val PRESSED_SCALE = 0.94f
private const val PRESS_FEEDBACK_DURATION_MS = 95L
private const val RELEASE_FEEDBACK_DURATION_MS = 260L
