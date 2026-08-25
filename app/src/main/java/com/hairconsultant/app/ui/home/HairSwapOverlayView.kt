package com.hairconsultant.app.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.hairconsultant.app.data.analysis.HairMask
import kotlin.math.max
import kotlin.random.Random

/**
 * Draws a smooth, procedurally shaded scalp fill — a radial highlight plus a subtle stipple
 * texture, not a copy of the live camera pixels — clipped to the live hair-segmentation mask, so
 * the user's real hair is only ever covered exactly where it was just detected. The mask itself
 * carries soft per-pixel confidence (not a hard 0/1 cutout), so the silhouette edge fades out at
 * the hairline instead of cutting sharply, moving frame to frame with the mask.
 */
class HairSwapOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var mask: HairMask? = null
    private var silhouetteBitmap: Bitmap? = null
    private val dst = RectF()
    private val coverPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = COVER_ALPHA }
    private val punchPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun setMask(newMask: HairMask?) {
        if (mask === newMask) return
        mask = newMask
        val previous = silhouetteBitmap
        silhouetteBitmap = newMask?.toSilhouetteBitmap()
        if (previous != null && previous !== silhouetteBitmap && !previous.isRecycled) {
            previous.recycle()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val gradient = RadialGradient(
            w / 2f, h * 0.35f, max(w, h) * 0.75f,
            intArrayOf(HIGHLIGHT_TONE, SKIN_TONE, SHADOW_TONE),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val noise = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        coverPaint.shader = ComposeShader(gradient, noise, PorterDuff.Mode.MULTIPLY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (coverPaint.shader == null) return
        val silhouette = silhouetteBitmap ?: return
        val currentMask = mask ?: return
        if (currentMask.width <= 0 || currentMask.height <= 0) return

        val scale = max(width / currentMask.width.toFloat(), height / currentMask.height.toFloat())
        val scaledWidth = currentMask.width * scale
        val scaledHeight = currentMask.height * scale
        val offsetX = (width - scaledWidth) / 2f
        val offsetY = (height - scaledHeight) / 2f
        dst.set(offsetX, offsetY, offsetX + scaledWidth, offsetY + scaledHeight)

        val layer = canvas.saveLayer(dst, null)
        canvas.drawRect(dst, coverPaint)
        canvas.drawBitmap(silhouette, null, dst, punchPaint)
        canvas.restoreToCount(layer)
    }

    private companion object {
        /** 0-255; fully opaque so the scalp fill completely covers the hair (soft mask edges still fade the boundary). */
        const val COVER_ALPHA = 255
        const val SKIN_TONE = 0xFFE2F0FE.toInt()
        val HIGHLIGHT_TONE = adjustLightness(SKIN_TONE, 0.18f)
        val SHADOW_TONE = adjustLightness(SKIN_TONE, -0.18f)
        val noiseBitmap: Bitmap by lazy { createNoiseBitmap() }
    }
}

private fun adjustLightness(color: Int, delta: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[2] = (hsv[2] + delta).coerceIn(0f, 1f)
    return Color.HSVToColor(hsv)
}

/** Small tileable brightness-jitter texture, multiplied over the gradient for a stipple look. */
private fun createNoiseBitmap(): Bitmap {
    val size = 64
    val random = Random(4242)
    val pixels = IntArray(size * size) {
        val v = 200 + random.nextInt(56)
        Color.rgb(v, v, v)
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** Soft, hair-confidence-shaped alpha mask used purely as a [PorterDuff.Mode.DST_IN] punch. */
private fun HairMask.toSilhouetteBitmap(): Bitmap {
    val pixels = IntArray(width * height)
    for (index in pixels.indices) {
        val alpha = hairAlpha(index)
        if (alpha > 0) pixels[index] = (alpha shl 24) or 0x00FFFFFF
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
