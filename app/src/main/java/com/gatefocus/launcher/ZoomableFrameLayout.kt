package com.gatefocus.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout

class ZoomableFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var isPenActive: Boolean = false

    var scaleFactor: Float = 1.0f
        private set

    private val minScale = 1.0f
    private val maxScale = 4.5f

    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val targetView: View?
        get() = if (childCount > 0) getChildAt(0) else null

    var onZoomChanged: ((Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val newScale = (scaleFactor * scale).coerceIn(minScale, maxScale)
            if (newScale != scaleFactor) {
                val focusX = detector.focusX
                val focusY = detector.focusY

                val child = targetView ?: return true
                val deltaScale = newScale / scaleFactor

                // Adjust translation to keep the pinch focus point stationary
                child.translationX += (child.translationX - (focusX - width / 2f)) * (deltaScale - 1f)
                child.translationY += (child.translationY - (focusY - height / 2f)) * (deltaScale - 1f)

                scaleFactor = newScale
                child.scaleX = scaleFactor
                child.scaleY = scaleFactor

                clampTranslation()
                onZoomChanged?.invoke(scaleFactor)
            }
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isPenActive) return false
            if (scaleFactor > 1.2f) {
                resetZoom()
            } else {
                zoomTo(2.5f, e.x, e.y)
            }
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Feed events to gesture detectors
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        val child = targetView

        // 1. Two or more fingers: Always handle pinch zoom and 2-finger panning
        if (ev.pointerCount >= 2) {
            val focusX = (ev.getX(0) + ev.getX(1)) / 2f
            val focusY = (ev.getY(0) + ev.getY(1)) / 2f

            when (ev.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastFocusX = focusX
                    lastFocusY = focusY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaleFactor > 1.0f && child != null) {
                        val dx = focusX - lastFocusX
                        val dy = focusY - lastFocusY
                        child.translationX += dx
                        child.translationY += dy
                        clampTranslation()
                    }
                    lastFocusX = focusX
                    lastFocusY = focusY
                }
            }
            // In two-finger mode, consume event so pen doesn't draw stray marks
            return true
        }

        // 2. Single finger when NOT in pen mode: Pan/drag the document when zoomed in
        if (!isPenActive && scaleFactor > 1.0f && child != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = ev.x
                    lastTouchY = ev.y
                    isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = ev.x - lastTouchX
                        val dy = ev.y - lastTouchY
                        child.translationX += dx
                        child.translationY += dy
                        clampTranslation()
                        lastTouchX = ev.x
                        lastTouchY = ev.y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
            return true
        }

        // 3. Single finger in pen mode: pass through directly to DrawingView
        return super.dispatchTouchEvent(ev)
    }

    private fun clampTranslation() {
        val child = targetView ?: return
        val maxTransX = ((width * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
        val maxTransY = ((height * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
        child.translationX = child.translationX.coerceIn(-maxTransX, maxTransX)
        child.translationY = child.translationY.coerceIn(-maxTransY, maxTransY)
    }

    fun resetZoom() {
        val child = targetView ?: return
        child.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(200)
            .setUpdateListener {
                scaleFactor = child.scaleX
                onZoomChanged?.invoke(scaleFactor)
            }
            .start()
        scaleFactor = 1.0f
        onZoomChanged?.invoke(1.0f)
    }

    fun zoomIn() {
        val newScale = (scaleFactor * 1.35f).coerceAtMost(maxScale)
        applyZoom(newScale)
    }

    fun zoomOut() {
        val newScale = (scaleFactor / 1.35f).coerceAtLeast(minScale)
        applyZoom(newScale)
    }

    fun zoomTo(targetScale: Float, pivotX: Float = width / 2f, pivotY: Float = height / 2f) {
        val child = targetView ?: return
        val clamped = targetScale.coerceIn(minScale, maxScale)

        val deltaScale = clamped / scaleFactor
        val newTransX = child.translationX + (child.translationX - (pivotX - width / 2f)) * (deltaScale - 1f)
        val newTransY = child.translationY + (child.translationY - (pivotY - height / 2f)) * (deltaScale - 1f)

        scaleFactor = clamped
        val maxTransX = ((width * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
        val maxTransY = ((height * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
        val targetTransX = newTransX.coerceIn(-maxTransX, maxTransX)
        val targetTransY = newTransY.coerceIn(-maxTransY, maxTransY)

        child.animate()
            .scaleX(clamped)
            .scaleY(clamped)
            .translationX(targetTransX)
            .translationY(targetTransY)
            .setDuration(220)
            .setUpdateListener {
                onZoomChanged?.invoke(child.scaleX)
            }
            .start()
        onZoomChanged?.invoke(clamped)
    }

    private fun applyZoom(targetScale: Float) {
        val child = targetView ?: return
        scaleFactor = targetScale

        val maxTransX = ((width * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
        val maxTransY = ((height * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
        val targetTransX = child.translationX.coerceIn(-maxTransX, maxTransX)
        val targetTransY = child.translationY.coerceIn(-maxTransY, maxTransY)

        child.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationX(targetTransX)
            .translationY(targetTransY)
            .setDuration(180)
            .setUpdateListener {
                onZoomChanged?.invoke(child.scaleX)
            }
            .start()
        onZoomChanged?.invoke(targetScale)
    }
}
