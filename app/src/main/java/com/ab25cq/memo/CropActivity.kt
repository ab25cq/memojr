package com.ab25cq.memo

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

class CropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.crop_title)

        val imagePath = intent.getStringExtra("image_path") ?: run { finish(); return }
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: run {
            Toast.makeText(this, getString(R.string.image_load_error), Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val cropView = CropView(this, bitmap)

        val btnCancel = android.widget.Button(this).apply {
            text = getString(R.string.cancel)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = 8 }
            setOnClickListener { finish() }
        }
        val btnCrop = android.widget.Button(this).apply {
            text = getString(R.string.crop_title)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val cropped = cropView.getCroppedBitmap()
                val out = ByteArrayOutputStream()
                cropped.compress(WEBP_FORMAT, 75, out)
                runCatching {
                    val f = File(cacheDir, "crop_output.webp")
                    f.writeBytes(out.toByteArray())
                    setResult(RESULT_OK, Intent().putExtra("cropped_path", f.absolutePath))
                }.onFailure {
                    Toast.makeText(this@CropActivity, getString(R.string.crop_error), Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }

        val btnBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.BLACK)
            addView(btnCancel)
            addView(btnCrop)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(cropView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(btnBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }
}

class CropView(context: Context, private val src: Bitmap) : View(context) {

    // image area in screen coords
    private val imgRect  = RectF()
    // crop region in image coords (0..bitmapW, 0..bitmapH)
    private val cropImg  = RectF()
    // crop region in screen coords (synced from cropImg)
    private val cropScr  = RectF()

    private val overlayPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val borderPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val gridPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val handleFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val handleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2"); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }

    private val HANDLE_R = 26f
    private val MIN_PX   = 40f   // min crop size in image pixels

    private enum class Mode { NONE, TL, TR, BL, BR, MOVE }
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w <= 0 || h <= 0) return
        val scale = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val bw = src.width * scale
        val bh = src.height * scale
        imgRect.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f)
        cropImg.set(0f, 0f, src.width.toFloat(), src.height.toFloat())
        syncScr()
    }

    private fun syncScr() {
        val sx = imgRect.width()  / src.width
        val sy = imgRect.height() / src.height
        cropScr.set(
            imgRect.left + cropImg.left   * sx,
            imgRect.top  + cropImg.top    * sy,
            imgRect.left + cropImg.right  * sx,
            imgRect.top  + cropImg.bottom * sy
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(src, null, imgRect, null)

        // dark overlay outside crop
        canvas.drawRect(imgRect.left,  imgRect.top,    imgRect.right, cropScr.top,    overlayPaint)
        canvas.drawRect(imgRect.left,  cropScr.bottom, imgRect.right, imgRect.bottom, overlayPaint)
        canvas.drawRect(imgRect.left,  cropScr.top,    cropScr.left,  cropScr.bottom, overlayPaint)
        canvas.drawRect(cropScr.right, cropScr.top,    imgRect.right, cropScr.bottom, overlayPaint)

        // rule-of-thirds grid
        val w3 = cropScr.width() / 3f
        val h3 = cropScr.height() / 3f
        canvas.drawLine(cropScr.left + w3,   cropScr.top,    cropScr.left + w3,   cropScr.bottom, gridPaint)
        canvas.drawLine(cropScr.left + w3*2, cropScr.top,    cropScr.left + w3*2, cropScr.bottom, gridPaint)
        canvas.drawLine(cropScr.left,        cropScr.top + h3,   cropScr.right, cropScr.top + h3,   gridPaint)
        canvas.drawLine(cropScr.left,        cropScr.top + h3*2, cropScr.right, cropScr.top + h3*2, gridPaint)

        // border
        canvas.drawRect(cropScr, borderPaint)

        // corner handles
        listOf(
            cropScr.left  to cropScr.top,
            cropScr.right to cropScr.top,
            cropScr.left  to cropScr.bottom,
            cropScr.right to cropScr.bottom
        ).forEach { (cx, cy) ->
            canvas.drawCircle(cx, cy, HANDLE_R, handleFill)
            canvas.drawCircle(cx, cy, HANDLE_R, handleBorder)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode  = hitTest(e.x, e.y)
                lastX = e.x; lastY = e.y
                return mode != Mode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                applyDrag(e.x - lastX, e.y - lastY)
                lastX = e.x; lastY = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = Mode.NONE
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Mode {
        val r = HANDLE_R * 2.5f
        if (dst(x, y, cropScr.left,  cropScr.top)    < r) return Mode.TL
        if (dst(x, y, cropScr.right, cropScr.top)    < r) return Mode.TR
        if (dst(x, y, cropScr.left,  cropScr.bottom) < r) return Mode.BL
        if (dst(x, y, cropScr.right, cropScr.bottom) < r) return Mode.BR
        if (cropScr.contains(x, y))                       return Mode.MOVE
        return Mode.NONE
    }

    private fun dst(x1: Float, y1: Float, x2: Float, y2: Float) =
        sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2))

    private fun applyDrag(dxScr: Float, dyScr: Float) {
        val sx = src.width  / imgRect.width()
        val sy = src.height / imgRect.height()
        val dx = dxScr * sx
        val dy = dyScr * sy
        val bw = src.width.toFloat()
        val bh = src.height.toFloat()
        when (mode) {
            Mode.TL -> {
                cropImg.left = (cropImg.left + dx).coerceIn(0f, cropImg.right  - MIN_PX)
                cropImg.top  = (cropImg.top  + dy).coerceIn(0f, cropImg.bottom - MIN_PX)
            }
            Mode.TR -> {
                cropImg.right = (cropImg.right + dx).coerceIn(cropImg.left + MIN_PX, bw)
                cropImg.top   = (cropImg.top   + dy).coerceIn(0f, cropImg.bottom - MIN_PX)
            }
            Mode.BL -> {
                cropImg.left   = (cropImg.left   + dx).coerceIn(0f, cropImg.right - MIN_PX)
                cropImg.bottom = (cropImg.bottom + dy).coerceIn(cropImg.top + MIN_PX, bh)
            }
            Mode.BR -> {
                cropImg.right  = (cropImg.right  + dx).coerceIn(cropImg.left + MIN_PX, bw)
                cropImg.bottom = (cropImg.bottom + dy).coerceIn(cropImg.top  + MIN_PX, bh)
            }
            Mode.MOVE -> {
                val w = cropImg.width(); val h = cropImg.height()
                cropImg.left   = (cropImg.left + dx).coerceIn(0f, bw - w)
                cropImg.top    = (cropImg.top  + dy).coerceIn(0f, bh - h)
                cropImg.right  = cropImg.left + w
                cropImg.bottom = cropImg.top  + h
            }
            Mode.NONE -> {}
        }
        syncScr()
    }

    fun getCroppedBitmap(): Bitmap {
        val l = cropImg.left.toInt().coerceIn(0, src.width - 1)
        val t = cropImg.top.toInt().coerceIn(0, src.height - 1)
        val r = cropImg.right.toInt().coerceAtMost(src.width)
        val b = cropImg.bottom.toInt().coerceAtMost(src.height)
        return Bitmap.createBitmap(src, l, t, (r - l).coerceAtLeast(1), (b - t).coerceAtLeast(1))
    }
}
