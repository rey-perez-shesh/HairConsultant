package com.hairconsultant.app.ui.facescan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.hairconsultant.app.data.analysis.FaceOverlayFrame
import com.hairconsultant.app.data.analysis.FaceShapeClassifier
import com.hairconsultant.app.data.analysis.PortraitBackgroundBlur
import kotlin.math.max

/**
 * Draws a portrait-style blurred background between PreviewView and hair cover / SceneView.
 * Foreground (face/head/shoulders) stays sharp; GLB renders above this layer.
 */
class BackgroundBlurSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var blurEnabled: Boolean = false
    private var frame: FaceOverlayFrame = FaceOverlayFrame.idle()
    private var sourceBitmap: Bitmap? = null
    private var blurBitmap: Bitmap? = null
    private var surfaceReady: Boolean = false
    private var frameCounter: Int = 0
    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        setZOrderOnTop(false)
        setZOrderMediaOverlay(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setBlurEnabled(enabled: Boolean) {
        if (blurEnabled == enabled) return
        blurEnabled = enabled
        if (!enabled) {
            blurBitmap?.recycle()
            blurBitmap = null
            clearSurface()
        } else {
            rebuildAndDraw()
        }
    }

    fun updateFrame(frame: FaceOverlayFrame, source: Bitmap?) {
        this.frame = frame
        this.sourceBitmap = source
        if (!blurEnabled) return
        frameCounter++
        if (frameCounter % 2 == 0 || blurBitmap == null) {
            rebuildAndDraw()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        surfaceReady = true
        if (blurEnabled) rebuildAndDraw() else clearSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        if (blurEnabled) rebuildAndDraw() else clearSurface()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    private fun rebuildAndDraw() {
        val source = sourceBitmap
        if (source == null || source.isRecycled) {
            blurBitmap?.recycle()
            blurBitmap = null
            clearSurface()
            return
        }

        val points = if (frame.faceDetected && frame.points.size > FaceShapeClassifier.CHIN) {
            frame.points
        } else {
            emptyList()
        }

        val previous = blurBitmap
        blurBitmap = if (points.isEmpty()) {
            null
        } else {
            PortraitBackgroundBlur.compose(source, points)
        }
        if (previous != null && previous !== blurBitmap && !previous.isRecycled) {
            previous.recycle()
        }
        if (blurBitmap == null) {
            clearSurface()
            return
        }
        drawBlurToSurface()
    }

    private fun drawBlurToSurface() {
        if (!surfaceReady || width <= 0 || height <= 0) return
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val bitmap = blurBitmap ?: return
            val imgW = frame.imageWidth.toFloat().coerceAtLeast(1f)
            val imgH = frame.imageHeight.toFloat().coerceAtLeast(1f)
            val scale = max(width / imgW, height / imgH)
            val offsetX = (width - imgW * scale) / 2f
            val offsetY = (height - imgH * scale) / 2f
            val dst = Rect(
                offsetX.toInt(),
                offsetY.toInt(),
                (offsetX + imgW * scale).toInt(),
                (offsetY + imgH * scale).toInt()
            )
            canvas.drawBitmap(bitmap, null, dst, drawPaint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun clearSurface() {
        if (!surfaceReady) return
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    companion object {
        const val VIEW_TAG = "background_blur_surface"
    }
}
