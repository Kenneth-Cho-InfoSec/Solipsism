package com.krystelligence.solipsism.system

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.ThemeUtils

/** Applies browser system-bar preferences across pre-edge-to-edge and edge-to-edge Android. */
class SystemBarsController(
    private val activity: Activity,
    private val protectionView: View,
    private val userPreferences: UserPreferences
) {

    private var immersiveHidden = false

    private val insetsController: WindowInsetsControllerCompat
        get() = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    init {
        ViewCompat.setOnApplyWindowInsetsListener(protectionView) { view, insets ->
            val statusBarHeight = insets
                .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
                .top
            view.updateLayoutParams<ViewGroup.LayoutParams> {
                height = if (userPreferences.hideStatusBarEnabled || immersiveHidden) 0 else statusBarHeight
            }
            view.isVisible = !userPreferences.hideStatusBarEnabled && !immersiveHidden && statusBarHeight > 0
            insets
        }
    }

    fun apply() {
        val controller = insetsController
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersiveHidden) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
        if (userPreferences.hideStatusBarEnabled || immersiveHidden) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        controller.isAppearanceLightStatusBars =
            userPreferences.useTheme.effective(activity) == AppTheme.LIGHT &&
            !userPreferences.useBlackStatusBar
        protectionView.setBackgroundColor(protectionColour())
        ViewCompat.requestApplyInsets(protectionView)
    }

    fun applyAfterWindowFocus() {
        apply()
    }

    fun setImmersiveHidden(hidden: Boolean) {
        immersiveHidden = hidden
        apply()
    }

    private fun protectionColour(): Int = when {
        userPreferences.useBlackStatusBar -> Color.BLACK
        else -> ThemeUtils.getStatusBarColor(activity)
    }
}
