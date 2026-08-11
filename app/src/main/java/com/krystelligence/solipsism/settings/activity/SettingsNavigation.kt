/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.settings.activity

import android.content.Context
import android.content.Intent
import com.krystelligence.solipsism.DefaultBrowserActivity
import com.krystelligence.solipsism.IncognitoBrowserActivity

internal object SettingsNavigation {
    const val EXTRA_INCOGNITO = "settings_return_incognito"

    fun createBrowserIntent(context: Context, incognito: Boolean): Intent {
        val target = if (incognito) {
            IncognitoBrowserActivity::class.java
        } else {
            DefaultBrowserActivity::class.java
        }

        return Intent(context, target).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
    }
}
