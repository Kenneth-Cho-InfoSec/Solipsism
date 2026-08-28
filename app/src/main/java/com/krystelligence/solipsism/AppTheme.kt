package com.krystelligence.solipsism

import android.content.Context
import android.content.res.Configuration
import com.krystelligence.solipsism.preference.IntEnum

/**
 * The available app themes.
 */
enum class AppTheme(override val value: Int) : IntEnum {
    LIGHT(0),
    DARK(1),
    BLACK(2),
    SYSTEM(3);

    /** Resolves the system-following choice to the theme required for this configuration. */
    fun effective(context: Context): AppTheme {
        if (this != SYSTEM) return this
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
    }
}
