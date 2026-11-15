package com.gatefocus.launcher

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var drawPath = Path()
    private var drawPaint = Paint()
    private var canvasPaint = Paint(Paint.DITHER_FLAG)
    private var drawCanvas: Canvas? = null
    private var canvasBitmap: Bitmap? = null

    private var paintColor = Color.parseColor("#38BDF8") // Accent blue
    private var strokeWidth = 5f
    private var isEraser = false

    init {
        setupPaint()
    }

    private fun setupPaint() {
        drawPaint.color = paintColor
        drawPaint.isAntiAlias = true
        drawPaint.strokeWidth = strokeWidth
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val oldBitmap = canvasBitmap
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap!!)
            if (oldBitmap != null) {
                drawCanvas?.drawBitmap(oldBitmap, 0f, 0f, canvasPaint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, canvasPaint)
        }
        if (!isEraser) {
            canvas.drawPath(drawPath, drawPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.moveTo(touchX, touchY)
            }
            MotionEvent.ACTION_MOVE -> {
                drawPath.lineTo(touchX, touchY)
                if (isEraser) {
                    drawCanvas?.drawPath(drawPath, drawPaint)
                }
            }
            MotionEvent.ACTION_UP -> {
                drawCanvas?.drawPath(drawPath, drawPaint)
                drawPath.reset()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    fun setPenColor(color: Int) {
        isEraser = false
        paintColor = color
        drawPaint.xfermode = null
        drawPaint.color = color
        drawPaint.strokeWidth = 5f
    }

    fun setEraserMode() {
        isEraser = true
        drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        drawPaint.strokeWidth = 35f
    }

    fun clearCanvas() {
        canvasBitmap?.let {
            drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            invalidate()
        }
    }

    fun getBitmap(): Bitmap? = canvasBitmap

    fun setBitmap(bitmap: Bitmap?) {
        if (bitmap != null && width > 0 && height > 0) {
            canvasBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            drawCanvas = Canvas(canvasBitmap!!)
            invalidate()
        } else {
            clearCanvas()
        }
    }
}
