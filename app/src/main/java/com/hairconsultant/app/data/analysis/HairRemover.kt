package com.hairconsultant.app.data.analysis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Soft hair replacement cover for live AR try-on.
 *
 * Uses a refined [SoftHairMask] (hair region only — face cleared) and paints a
 * feathered skin-tone veil so real hair is reduced under the Blender GLB.
 * Does not draw a giant solid plate over the head/face.
 */
object HairRemover {

    /**
     * Soft cover from [soft] alphas. Peak alpha is capped so edges feather and
     * the result never reads as a hard gray rectangle.
     */
    fun tryOnSoftCover(
        soft: SoftHairMask,
        scalpColor: Int = Color.rgb(205, 170, 145)
    ): Bitmap {
        val outW = soft.width
        val outH = soft.height
        if (outW <= 0 || outH <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val sr = Color.red(scalpColor)
        val sg = Color.green(scalpColor)
        val sb = Color.blue(scalpColor)
        val pixels = IntArray(outW * outH)
        for (i in soft.alpha.indices) {
            val a = soft.alphaAt(i)
            if (a <= 0) continue
            pixels[i] = Color.argb(a, sr, sg, sb)
        }
        return Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
    }

    /**
     * Strong cover for live AR (legacy binary path). Prefer [tryOnSoftCover].
     */
    fun tryOnCoverOverlay(
        source: Bitmap?,
        mask: HairMask,
        scalpColor: Int = Color.rgb(205, 170, 145)
    ): Bitmap {
        val soft = HairMaskRefiner.refineForTryOn(mask, emptyList())
        return tryOnSoftCover(soft, scalpColor)
    }

    fun estimateScalpColor(source: Bitmap, mask: HairMask): Int {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0 || source.isRecycled) return Color.rgb(205, 170, 145)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        val yStart = (h * 0.14f).toInt()
        val yEnd = (h * 0.40f).toInt()
        for (y in yStart until yEnd) {
            val my = (y * mask.height / h).coerceIn(0, mask.height - 1)
            for (x in (w * 0.28f).toInt() until (w * 0.72f).toInt()) {
                val mx = (x * mask.width / w).coerceIn(0, mask.width - 1)
                if (mask.isHair(my * mask.width + mx)) continue
                val p = pixels[y * w + x]
                val pr = Color.red(p)
                val pg = Color.green(p)
                val pb = Color.blue(p)
                val bright = maxOf(pr, pg, pb)
                if (bright < 45 || bright > 240) continue
                r += pr
                g += pg
                b += pb
                count++
            }
        }
        if (count == 0) return Color.rgb(205, 170, 145)
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    /** Soft blur veil used only when scalp cover is unavailable. */
    fun softBlurVeil(mask: HairMask, alpha: Int = 160): Bitmap {
        val soft = HairMaskRefiner.refineForTryOn(mask, emptyList())
        val color = Color.rgb(48, 42, 40)
        return tryOnSoftCover(soft, color).also { bmp ->
            // Cap veil strength via alpha rescale if needed — tryOnSoftCover already soft.
            if (alpha < 160) {
                // leave as-is; soft mask already capped
            }
        }
    }

    fun blurHairOverlay(source: Bitmap, mask: HairMask, blurRadius: Int = 18): Bitmap {
        return tryOnCoverOverlay(source, mask)
    }

    fun removeHair(source: Bitmap, mask: HairMask, passes: Int = 3): Bitmap {
        val soft = HairMaskRefiner.refineForTryOn(mask, emptyList())
        val overlay = tryOnSoftCover(soft)
        val result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            overlay,
            Rect(0, 0, overlay.width, overlay.height),
            Rect(0, 0, result.width, result.height),
            paint
        )
        overlay.recycle()
        return result
    }

    fun countHairPixels(mask: HairMask): Int {
        var count = 0
        for (i in mask.labels.indices) {
            if (mask.isHair(i)) count++
        }
        return count
    }

    fun countSoftPixels(soft: SoftHairMask, minAlpha: Int = 40): Int {
        var count = 0
        for (i in soft.alpha.indices) {
            if (soft.alphaAt(i) >= minAlpha) count++
        }
        return count
    }

    /**
     * Clears hair-mask pixels over the inner face so try-on cover does not paint eyes/nose/mouth.
     * Prefer [HairMaskRefiner.refineForTryOn] for live try-on (oval + soft edges).
     */
    fun prepareTryOnMask(mask: HairMask, points: List<LandmarkPoint>): HairMask {
        val soft = HairMaskRefiner.refineForTryOn(mask, points)
        val labels = ByteArray(soft.alpha.size)
        for (i in soft.alpha.indices) {
            labels[i] = if (soft.alphaAt(i) >= 40) mask.hairClass.toByte() else 0
        }
        return HairMask(labels, soft.width, soft.height, mask.hairClass, mask.confidence)
    }
}
