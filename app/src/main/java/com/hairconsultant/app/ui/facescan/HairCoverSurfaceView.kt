package com.hairconsultant.app.ui.facescan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.hairconsultant.app.data.analysis.FaceOverlayFrame
import com.hairconsultant.app.data.analysis.FaceShapeClassifier
import com.hairconsultant.app.data.analysis.HairMaskRefiner
import com.hairconsultant.app.data.analysis.HairRemover
import com.hairconsultant.app.data.analysis.SoftHairMask
import kotlin.math.max

/**
 * Draws a soft, hair-only replacement veil between PreviewView and SceneView.
 *
 * Uses MediaPipe hair segmentation (refined) — never a giant face/head plate.
 * SceneView GLB composites on top; face punch-through keeps facial features visible.
 */
class HairCoverSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var coverEnabled: Boolean = false
    private var debugMode: ArTryOnDebugMode = ArTryOnDebugMode.NORMAL
    private var frame: FaceOverlayFrame = FaceOverlayFrame.idle()
    private var blurSource: Bitmap? = null
    private var coverBitmap: Bitmap? = null
    private var surfaceReady: Boolean = false
    private var frameCounter: Int = 0
    private var cachedScalpColor: Int = Color.rgb(205, 170, 145)
    private var lastHairPixels: Int = 0
    private var lastTryOnPixels: Int = 0
    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        setZOrderOnTop(false)
        setZOrderMediaOverlay(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setCoverEnabled(enabled: Boolean) {
        if (coverEnabled == enabled) return
        coverEnabled = enabled
        if (!enabled) {
            HairMaskRefiner.resetTemporal()
            coverBitmap?.recycle()
            coverBitmap = null
            clearSurface()
        } else {
            rebuildAndDraw()
        }
    }

    fun setDebugMode(mode: ArTryOnDebugMode) {
        if (debugMode == mode) return
        debugMode = mode
        if (coverEnabled) rebuildAndDraw()
    }

    fun updateFrame(frame: FaceOverlayFrame, source: Bitmap?) {
        this.frame = frame
        this.blurSource = source
        if (!coverEnabled) return
        frameCounter++
        // Soft refine is heavier — every other overlay tick is enough with temporal EMA.
        if (frameCounter % 2 == 0 || coverBitmap == null) {
            rebuildAndDraw()
        }
    }

    fun hairPixelCounts(): Pair<Int, Int> = lastHairPixels to lastTryOnPixels

    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        surfaceReady = true
        if (coverEnabled) rebuildAndDraw() else clearSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        if (coverEnabled) rebuildAndDraw() else clearSurface()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    private fun rebuildAndDraw() {
        val mask = frame.hairMask
        if (mask == null) {
            lastHairPixels = 0
            lastTryOnPixels = 0
            coverBitmap?.recycle()
            coverBitmap = null
            clearSurface()
            return
        }

        lastHairPixels = HairRemover.countHairPixels(mask)
        val points = if (frame.faceDetected && frame.points.size > FaceShapeClassifier.CHIN) {
            frame.points
        } else {
            emptyList()
        }
        val soft = HairMaskRefiner.refineForTryOn(mask, points)
        lastTryOnPixels = HairRemover.countSoftPixels(soft)

        val previous = coverBitmap
        coverBitmap = when (debugMode) {
            ArTryOnDebugMode.MASK -> soft.toDebugMaskBitmap()
            ArTryOnDebugMode.NORMAL,
            ArTryOnDebugMode.REMOVAL,
            ArTryOnDebugMode.FACE_OCCLUSION -> {
                val source = blurSource
                if (source != null && !source.isRecycled) {
                    if (frameCounter % 15 == 0 || frameCounter <= 3) {
                        cachedScalpColor = HairRemover.estimateScalpColor(source, mask)
                    }
                }
                HairRemover.tryOnSoftCover(soft, source, cachedScalpColor)
            }
        }
        if (previous != null && previous !== coverBitmap && !previous.isRecycled) {
            previous.recycle()
        }

        drawCoverToSurface()
        if (frameCounter % 30 == 0) {
            Log.d(
                TAG,
                "hairCover softPx=$lastTryOnPixels rawPx=$lastHairPixels " +
                    "mode=$debugMode surface=${width}x$height"
            )
        }
    }

    private fun drawCoverToSurface() {
        if (!surfaceReady || width <= 0 || height <= 0) return
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val bitmap = coverBitmap ?: return
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
        private const val TAG = "HairCoverSurface"
        const val VIEW_TAG = "hair_cover_surface"
    }
}

private fun SoftHairMask.toDebugMaskBitmap(): Bitmap {
    val pixels = IntArray(width * height)
    for (i in alpha.indices) {
        val a = alphaAt(i)
        if (a > 0) pixels[i] = Color.argb(a.coerceAtMost(220), 255, 0, 255)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
