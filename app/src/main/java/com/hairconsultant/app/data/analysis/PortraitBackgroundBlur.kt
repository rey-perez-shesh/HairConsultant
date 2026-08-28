package com.hairconsultant.app.data.analysis

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Keeps the portrait foreground (face/head) sharp and blurs only the background.
 * Runs on a downscaled preview bitmap for real-time mobile performance.
 */
object PortraitBackgroundBlur {

    fun compose(
        source: Bitmap,
        points: List<LandmarkPoint>,
        blurRadius: Int = 12
    ): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0 || source.isRecycled) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val original = IntArray(w * h)
        source.getPixels(original, 0, w, 0, 0, w, h)
        val keepSharp = HairMaskRefiner.buildPortraitForegroundMask(points, w, h)

        // No face yet — return original frame unchanged (no full-frame blur).
        if (points.size <= FaceShapeClassifier.CHIN) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
                ?: Bitmap.createBitmap(original, w, h, Bitmap.Config.ARGB_8888)
        }

        val blurred = boxBlurArgb(original, w, h, blurRadius.coerceIn(2, 24))

        for (i in original.indices) {
            val t = keepSharp[i].toInt() and 0xFF
            if (t >= 255) continue
            if (t <= 0) {
                original[i] = blurred[i]
                continue
            }
            original[i] = blendArgb(original[i], blurred[i], t / 255f)
        }

        return Bitmap.createBitmap(original, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blendArgb(fg: Int, bg: Int, fgWeight: Float): Int {
        val w = fgWeight.coerceIn(0f, 1f)
        val iw = 1f - w
        return Color.argb(
            255,
            (Color.red(fg) * w + Color.red(bg) * iw).toInt().coerceIn(0, 255),
            (Color.green(fg) * w + Color.green(bg) * iw).toInt().coerceIn(0, 255),
            (Color.blue(fg) * w + Color.blue(bg) * iw).toInt().coerceIn(0, 255)
        )
    }

    private fun boxBlurArgb(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return pixels.copyOf()
        val tmp = IntArray(pixels.size)
        val out = IntArray(pixels.size)

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dx in -radius..radius) {
                    val sx = (x + dx).coerceIn(0, w - 1)
                    val p = pixels[row + sx]
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                    count++
                }
                tmp[row + x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -radius..radius) {
                    val sy = (y + dy).coerceIn(0, h - 1)
                    val p = tmp[sy * w + x]
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                    count++
                }
                out[y * w + x] = Color.rgb(r / count, g / count, b / count)
            }
        }
        return out
    }
}
