package com.krystelligence.solipsism.utils

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.krystelligence.solipsism.R

object ThemeUtils {

    private val typedValue = TypedValue()

    /**
     * Gets the primary color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getPrimaryColor(context: Context): Int = getColor(context, R.attr.colorPrimary)

    /**
     * Gets the primary dark color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getPrimaryColorDark(context: Context): Int = getColor(context, R.attr.colorPrimaryDark)

    /**
     * Gets the accent color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getAccentColor(context: Context): Int = getColor(context, R.attr.colorAccent)

    /**
     * Gets the color of the status bar as set in styles for the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getStatusBarColor(context: Context): Int = getColor(context, android.R.attr.statusBarColor)

    /**
     * Gets the color attribute from the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getColor(context: Context, @AttrRes resource: Int): Int {
        val a: TypedArray = context.obtainStyledAttributes(typedValue.data, intArrayOf(resource))
        return try {
            a.getColor(0, 0)
        } finally {
            a.recycle()
        }
    }

    @ColorInt
    private fun getIconLightThemeColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.icon_light_theme)

    @ColorInt
    private fun getIconDarkThemeColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.icon_dark_theme)

    /**
     * Gets the color icon for the light or dark theme.
     */
    @JvmStatic
    @ColorInt
    fun getIconThemeColor(context: Context, dark: Boolean): Int =
        if (dark) getIconDarkThemeColor(context) else getIconLightThemeColor(context)

    private fun getVectorDrawable(context: Context, drawableId: Int): Drawable =
        checkNotNull(ContextCompat.getDrawable(context, drawableId))

    // http://stackoverflow.com/a/38244327/1499541
    @JvmStatic
    fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
        val drawable = getVectorDrawable(context, drawableId)
        val bitmap = createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Gets the icon with an applied color filter for the correct theme.
     */
    @JvmStatic
    fun createThemedBitmap(context: Context, @DrawableRes res: Int, dark: Boolean): Bitmap {
        val color = if (dark) getIconDarkThemeColor(context) else getIconLightThemeColor(context)
        return createThemedBitmap(context, res, color)
    }

    @JvmStatic
    fun createThemedBitmap(context: Context, @DrawableRes res: Int, @ColorInt color: Int): Bitmap {
        val sourceBitmap = getBitmapFromVectorDrawable(context, res)
        val resultBitmap = createBitmap(
            sourceBitmap.width,
            sourceBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val paint = Paint().apply {
            val filter: ColorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            colorFilter = filter
        }
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)
        sourceBitmap.recycle()
        return resultBitmap
    }

    /**
     * Gets the edit text text color for the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getTextColor(context: Context): Int = getColor(context, android.R.attr.editTextColor)
}
