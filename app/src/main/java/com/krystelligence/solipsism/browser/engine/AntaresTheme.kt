package com.krystelligence.solipsism.browser.engine

import android.content.Context
import com.krystelligence.solipsism.AppTheme

/** Maps Solipsism's surface themes to the standard light/dark preference exposed to web pages. */
internal fun AppTheme.toAntaresTheme(context: Context): Int =
    effective(context).toResolvedAntaresTheme()

internal fun AppTheme.toResolvedAntaresTheme(): Int = when (this) {
    AppTheme.LIGHT -> AntaresProtocol.THEME_LIGHT
    AppTheme.DARK, AppTheme.BLACK -> AntaresProtocol.THEME_DARK
    AppTheme.SYSTEM -> error("System theme must be resolved before mapping it to Antares")
}
