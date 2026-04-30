package com.krystelligence.solipsism.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.krystelligence.solipsism.R
import kotlin.math.abs
import kotlin.math.roundToInt

object DrawableUtils {

    /**
     * Creates a white rounded drawable with an inset image of a different color.
     */
    @JvmStatic
    fun createImageInsetInRoundedSquare(context: Context, @DrawableRes drawableRes: Int): Bitmap {
        val icon = ThemeUtils.getBitmapFromVectorDrawable(context, drawableRes)
        val image = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        val radius = Utils.dpToPx(6f).toFloat()
        val padding = Utils.dpToPx(2f)

        val outer = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        canvas.drawRoundRect(outer, radius, radius, paint)

        val dest = Rect(
            (outer.left + padding.toFloat()).roundToInt(),
            (outer.top + padding.toFloat()).roundToInt(),
            (outer.right - padding.toFloat()).roundToInt(),
            (outer.bottom - padding.toFloat()).roundToInt()
        )
        canvas.drawBitmap(icon, null, dest, paint)

        return image
    }

    /**
     * Creates a rounded square of a certain color with a character imprinted in white on it.
     */
    @JvmStatic
    fun createRoundedLetterImage(character: Char, width: Int, height: Int, color: Int): Bitmap {
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint().apply {
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = Utils.dpToPx(14f).toFloat()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val radius = Utils.dpToPx(6f).toFloat()

        val outer = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        canvas.drawRoundRect(outer, radius, radius, paint)

        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)

        paint.color = Color.WHITE
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        canvas.drawText(character.toString(), xPos, yPos, paint)

        return image
    }

    /**
     * Hashes a character to one of four colors: blue, green, red, or orange.
     */
    @JvmStatic
    @ColorInt
    fun characterToColorHash(character: Char, app: Application): Int {
        val smallHash = Character.getNumericValue(character) % 4
        return when (abs(smallHash)) {
            0 -> ContextCompat.getColor(app, R.color.bookmark_default_blue)
            1 -> ContextCompat.getColor(app, R.color.bookmark_default_green)
            2 -> ContextCompat.getColor(app, R.color.bookmark_default_red)
            3 -> ContextCompat.getColor(app, R.color.bookmark_default_orange)
            else -> Color.BLACK
        }
    }

    @JvmStatic
    fun mixColor(fraction: Float, startValue: Int, endValue: Int): Int {
        val startA = startValue shr 24 and 0xff
        val startR = startValue shr 16 and 0xff
        val startG = startValue shr 8 and 0xff
        val startB = startValue and 0xff

        val endA = endValue shr 24 and 0xff
        val endR = endValue shr 16 and 0xff
        val endG = endValue shr 8 and 0xff
        val endB = endValue and 0xff

        return ((startA + (fraction * (endA - startA)).toInt()) shl 24) or
            ((startR + (fraction * (endR - startR)).toInt()) shl 16) or
            ((startG + (fraction * (endG - startG)).toInt()) shl 8) or
            (startB + (fraction * (endB - startB)).toInt())
    }
}
