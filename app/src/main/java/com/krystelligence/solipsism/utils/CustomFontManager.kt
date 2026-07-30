package com.krystelligence.solipsism.utils

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

/** Applies a validated user font to native app UI without changing WebView page content. */
object CustomFontManager {
    fun load(path: String?): Typeface? = path
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }

    fun applyToViewTree(root: View, path: String?) {
        val typeface = load(path) ?: return
        apply(root, typeface)
    }

    private fun apply(view: View, typeface: Typeface) {
        if (view is TextView) {
            view.typeface = Typeface.create(typeface, view.typeface?.style ?: Typeface.NORMAL)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) apply(view.getChildAt(index), typeface)
        }
    }
}
