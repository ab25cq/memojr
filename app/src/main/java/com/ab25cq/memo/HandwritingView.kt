package com.ab25cq.memo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onContentChanged: (() -> Unit)? = null

    private data class Stroke(val path: Path, val widthPx: Float)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentWidthPx = dp(1f)
    private var lastX = 0f
    private var lastY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setStrokeWidthDp(widthDp: Int) {
        currentWidthPx = dp(widthDp.toFloat())
    }

    fun clear() {
        if (strokes.isEmpty() && currentPath == null) return
        strokes.clear()
        currentPath = null
        invalidate()
        onContentChanged?.invoke()
    }

    fun exportBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawStrokes(canvas)
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawStrokes(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                currentPath = Path().apply { moveTo(lastX, lastY) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val path = currentPath ?: return true
                val x = event.x
                val y = event.y
                path.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath?.let { path ->
                    path.lineTo(lastX, lastY)
                    strokes.add(Stroke(Path(path), currentWidthPx))
                    onContentChanged?.invoke()
                }
                currentPath = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun drawStrokes(canvas: Canvas) {
        strokes.forEach { stroke ->
            paint.strokeWidth = stroke.widthPx
            canvas.drawPath(stroke.path, paint)
        }
        currentPath?.let {
            paint.strokeWidth = currentWidthPx
            canvas.drawPath(it, paint)
        }
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }
}
