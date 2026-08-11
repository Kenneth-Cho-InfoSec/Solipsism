package com.krystelligence.solipsism.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.max
import kotlin.math.min

class ScreenshotCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(
            com.krystelligence.solipsism.R.string.screenshot_studio_canvas_instructions
        )
    }

    private val path = Path()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        alpha = 128
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var bitmap: Bitmap? = null
    private val imageRect = RectF()
    private var imageScale = 1f
    private var drawing = false
    private var startX = 0f
    private var startY = 0f
    var hasSelection: Boolean = false
        private set

    fun setBitmap(value: Bitmap) {
        bitmap = value
        invalidate()
    }

    fun clearSelection() {
        path.reset()
        hasSelection = false
        announceForAccessibility(
            context.getString(com.krystelligence.solipsism.R.string.screenshot_studio_selection_cleared)
        )
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = ScreenshotCanvasView::class.java.name
        info.isFocusable = true
        info.isClickable = true
        if (hasSelection) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    ACTION_CLEAR_SELECTION,
                    context.getString(com.krystelligence.solipsism.R.string.screenshot_studio_clear_selection)
                )
            )
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == ACTION_CLEAR_SELECTION && hasSelection) {
            clearSelection()
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        bitmap?.let { source ->
            imageScale = min(width.toFloat() / source.width, height.toFloat() / source.height)
            val left = (width - source.width * imageScale) / 2f
            val top = (height - source.height * imageScale) / 2f
            imageRect.set(left, top, left + source.width * imageScale, top + source.height * imageScale)
            canvas.withTranslation(left, top) {
                scale(imageScale, imageScale)
                drawBitmap(source, 0f, 0f, imagePaint)
            }
        }
        if (!path.isEmpty) {
            strokePaint.color = resolveAccent()
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                path.reset()
                path.moveTo(event.x, event.y)
                startX = event.x
                startY = event.y
                drawing = true
                hasSelection = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> if (drawing) {
                path.lineTo(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (drawing) {
                // Explicitly add the closing segment before closing the Path. This keeps
                // the outline visibly connected even when the final point is far from the
                // starting point or the platform does not redraw Path.close() immediately.
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    path.lineTo(startX, startY)
                }
                path.close()
                drawing = false
                val bounds = RectF()
                path.computeBounds(bounds, true)
                hasSelection = !bounds.isEmpty
                invalidate()
                performClick()
                if (hasSelection) {
                    announceForAccessibility(
                        context.getString(com.krystelligence.solipsism.R.string.screenshot_studio_selection_created)
                    )
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    fun selectedBitmap(): Bitmap? {
        val source = bitmap ?: return null
        if (!hasSelection) return null
        val bounds = RectF()
        path.computeBounds(bounds, true)
        if (imageRect.isEmpty || imageScale <= 0f) return null
        val left = max(0, ((bounds.left - imageRect.left) / imageScale).toInt())
        val top = max(0, ((bounds.top - imageRect.top) / imageScale).toInt())
        val right = min(source.width, ((bounds.right - imageRect.left) / imageScale).toInt())
        val bottom = min(source.height, ((bounds.bottom - imageRect.top) / imageScale).toInt())
        if (right <= left || bottom <= top) return null
        val output = createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            val mask = Path().apply {
                addOval(
                    RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    Path.Direction.CW
                )
            }
            clipPath(mask)
            drawBitmap(
                source,
                null,
                RectF(-left.toFloat(), -top.toFloat(), source.width - left.toFloat(), source.height - top.toFloat()),
                imagePaint
            )
        }
        return output
    }

    private fun resolveAccent(): Int = try {
        val attrs = intArrayOf(com.krystelligence.solipsism.R.attr.colorAccent)
        val typedArray = context.obtainStyledAttributes(attrs)
        try {
            typedArray.getColor(0, Color.CYAN)
        } finally {
            typedArray.recycle()
        }
    } catch (_: Throwable) { Color.CYAN }

    private companion object {
        private const val ACTION_CLEAR_SELECTION = 0x01020001
    }
}
