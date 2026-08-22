package com.krystelligence.solipsism.browser

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PhysicalPressFeedbackTest {

    @Test
    fun `one tap dispatches exactly one click`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 100, 100)
        }
        activity.setContentView(view)
        var clickCount = 0
        view.setOnClickListener { clickCount++ }
        view.applyPhysicalPressFeedback(LinearInterpolator(), LinearInterpolator())

        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        val up = MotionEvent.obtain(0, 16, MotionEvent.ACTION_UP, 50f, 50f, 0)
        try {
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            down.recycle()
            up.recycle()
        }

        assertThat(clickCount).isEqualTo(1)
    }
}
