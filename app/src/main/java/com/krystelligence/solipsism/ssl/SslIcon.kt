package com.krystelligence.solipsism.ssl

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.utils.DrawableUtils
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable

/**
 * Creates the proper [Drawable] to represent the [SslState].
 */
fun Context.createSslDrawableForState(sslState: SslState): Drawable? = when (sslState) {
    is SslState.None -> null
    is SslState.Valid -> {
        val bitmap = DrawableUtils.createImageInsetInRoundedSquare(this, R.drawable.ic_secured)
        val securedDrawable = bitmap.toDrawable(resources)
        securedDrawable
    }

    is SslState.Invalid -> {
        val bitmap = DrawableUtils.createImageInsetInRoundedSquare(this, R.drawable.ic_unsecured)
        val unsecuredDrawable = bitmap.toDrawable(resources)
        unsecuredDrawable
    }
}
