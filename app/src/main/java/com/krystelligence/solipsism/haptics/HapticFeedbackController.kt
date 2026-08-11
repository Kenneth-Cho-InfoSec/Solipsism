package com.krystelligence.solipsism.haptics

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.krystelligence.solipsism.preference.UserPreferences
import javax.inject.Inject

/** Haptic vocabulary for discrete browser actions. */
class HapticFeedbackController @Inject constructor(
    application: Application,
    private val userPreferences: UserPreferences
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            application.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ContextCompat.getSystemService(application, Vibrator::class.java)
        }
    }

    enum class Category { TABS, BOOKMARKS, QR, DOWNLOADS, ADBLOCK, PERMISSIONS, REFRESH }

    fun tap(category: Category) = vibrate(category)

    fun success(category: Category) = vibrate(category)

    fun warning(category: Category) = vibrate(category)

    private fun vibrate(category: Category) {
        val config = config(category) ?: return
        if (!enabled() || !config.first) return
        val duration = config.second.coerceIn(10, 500).toLong()
        val amplitude = (255 * config.third.coerceIn(0, 100) / 100).coerceIn(1, 255)
        vibrator?.takeIf { it.hasVibrator() }?.let {
            it.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        }
    }

    private fun enabled() = userPreferences.hapticsEnabled && userPreferences.interactionHapticsEnabled

    private fun config(category: Category): Triple<Boolean, Int, Int>? = when (category) {
        Category.TABS -> Triple(userPreferences.tabsHapticsEnabled, userPreferences.tabsHapticsDurationMs, userPreferences.tabsHapticsIntensity)
        Category.BOOKMARKS -> Triple(userPreferences.bookmarksHapticsEnabled, userPreferences.bookmarksHapticsDurationMs, userPreferences.bookmarksHapticsIntensity)
        Category.QR -> Triple(userPreferences.qrHapticsEnabled, userPreferences.qrHapticsDurationMs, userPreferences.qrHapticsIntensity)
        Category.DOWNLOADS -> Triple(userPreferences.downloadHapticsEnabled, userPreferences.downloadHapticsDurationMs, userPreferences.downloadHapticsIntensity)
        Category.ADBLOCK -> Triple(userPreferences.adblockHapticsEnabled, userPreferences.adblockHapticsDurationMs, userPreferences.adblockHapticsIntensity)
        Category.PERMISSIONS -> Triple(userPreferences.permissionsHapticsEnabled, userPreferences.permissionsHapticsDurationMs, userPreferences.permissionsHapticsIntensity)
        Category.REFRESH -> Triple(userPreferences.refreshHapticsEnabled, userPreferences.refreshHapticsDurationMs, userPreferences.refreshHapticsIntensity)
    }
}
