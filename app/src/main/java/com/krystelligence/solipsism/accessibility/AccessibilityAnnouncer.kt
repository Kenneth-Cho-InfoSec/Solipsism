package com.krystelligence.solipsism.accessibility

import android.view.View
import androidx.core.view.ViewCompat

/** Delivers important state changes to TalkBack and other accessibility services. */
object AccessibilityAnnouncer {
    fun announce(root: View, message: CharSequence, enabled: Boolean = true) {
        if (!enabled || message.isEmpty()) return
        ViewCompat.setAccessibilityLiveRegion(root, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        root.announceForAccessibility(message)
    }
}
