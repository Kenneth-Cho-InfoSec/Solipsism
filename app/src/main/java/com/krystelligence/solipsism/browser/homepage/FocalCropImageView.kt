package com.krystelligence.solipsism.browser.homepage

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

/** Centre-crop image view whose crop can be positioned using normalised focal coordinates. */
class FocalCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    var focalX: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateImageMatrix()
        }
    var focalY: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateImageMatrix()
        }

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        super.setFrame(left, top, right, bottom).also { updateImageMatrix() }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        updateImageMatrix()
    }

    private fun updateImageMatrix() {
        val image = drawable ?: return
        val drawableWidth = image.intrinsicWidth.takeIf { it > 0 } ?: return
        val drawableHeight = image.intrinsicHeight.takeIf { it > 0 } ?: return
        if (width <= 0 || height <= 0) return

        val scale = max(width / drawableWidth.toFloat(), height / drawableHeight.toFloat())
        val overflowX = drawableWidth * scale - width
        val overflowY = drawableHeight * scale - height
        imageMatrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(-overflowX * focalX, -overflowY * focalY)
        }
    }
}
