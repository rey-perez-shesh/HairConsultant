package com.hairconsultant.app.data.analysis

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How long hair lying flat runs, read from the direction its strands flow. Hair combed over or
 * swept to the side barely rises above the skull, so its height says nothing about its length,
 * but its strands still show as streaks. The structure tensor (smoothed products of the brightness
 * gradients) gives the streaks' direction at every hair pixel, and following that direction from
 * seeds in the hair above the brows traces each strand's visible path, the way single-view hair
 * modelling builds strands from an orientation map. Only the part facing the camera is seen, so
 * hair running back over the crown reads short.
 */
object HairFlow {

    // shortcut: thresholds chosen on synthetic stripes, not real hair; tune them on the phone
    // (each scan logs its readings and sends hairFlowMm to Firestore) before trusting this reading.
    /** Window the gradient products are averaged over, in pixels either side. */
    private const val SMOOTH_RADIUS = 3
    /** Pixels this close to the hair's outline are skipped: the outline's own edge isn't a strand. */
    private const val EDGE_MARGIN = 3
    /** How strongly one direction must dominate (0 = none, 1 = perfect stripes) to follow it. */
    private const val MIN_COHERENCE = 0.3f
    /** Average squared gradient below which there's no visible texture to read. */
    private const val MIN_ENERGY = 1e-5f
    /** A traced strand stops if it turns more than about 20 degrees in one pixel. */
    private const val MAX_TURN_COS = 0.94f
    private const val SEED_STEP = 4
    private const val MIN_SEEDS = 12
    /** The 90th-percentile path, so a few runaway traces can't set the length. */
    private const val PERCENTILE = 0.9f
    /** Photos are traced at no more than this size, like a live camera frame. */
    private const val MAX_SIDE = 640

    /**
     * The longest strand path in [bitmap]'s pixels, through hair the [mask] marks (less what
     * [isNotHair] says is skin or clothing), seeded above the brows. Null when too little hair
     * shows a readable direction.
     */
    fun longestStrandPx(
        bitmap: Bitmap,
        mask: HairMask,
        points: List<LandmarkPoint>,
        isNotHair: ((Float, Float) -> Boolean)?
    ): Float? {
        if (points.size <= GLABELLA || bitmap.isRecycled) return null
        val scale = min(1f, MAX_SIDE / max(bitmap.width, bitmap.height).toFloat())
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val source = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        if (source !== bitmap) source.recycle()
        val gray = FloatArray(w * h) { i ->
            val c = pixels[i]
            (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        val hair = BooleanArray(w * h) { i ->
            val nx = (i % w + 0.5f) / w
            val ny = (i / w + 0.5f) / h
            mask.isHairAt(nx, ny) && isNotHair?.invoke(nx, ny) != true
        }
        val seedBottom = (points[GLABELLA].y * h).toInt()
        return longestStrandPx(gray, w, h, hair, seedBottom)?.let { it / scale }
    }

    /** [longestStrandPx] on a grayscale image (0..1, row by row) with its hair pixels marked. */
    internal fun longestStrandPx(gray: FloatArray, width: Int, height: Int, hair: BooleanArray, seedBottomRow: Int): Float? {
        val n = width * height
        if (gray.size != n || hair.size != n || width < 3 || height < 3) return null
        val jxx = FloatArray(n)
        val jyy = FloatArray(n)
        val jxy = FloatArray(n)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val gx = (gray[i + 1] - gray[i - 1]) * 0.5f
                val gy = (gray[i + width] - gray[i - width]) * 0.5f
                jxx[i] = gx * gx
                jyy[i] = gy * gy
                jxy[i] = gx * gy
            }
        }
        val sxx = boxBlur(jxx, width, height)
        val syy = boxBlur(jyy, width, height)
        val sxy = boxBlur(jxy, width, height)

        // The strand direction is across the strongest gradient: (-sin, cos) of the gradient's angle.
        val dirX = FloatArray(n)
        val dirY = FloatArray(n)
        val usable = BooleanArray(n)
        for (i in 0 until n) {
            if (!isInterior(hair, i, width, height)) continue
            val a = sxx[i]
            val b = syy[i]
            val c = sxy[i]
            val energy = a + b
            if (energy < MIN_ENERGY) continue
            if (sqrt((a - b) * (a - b) + 4f * c * c) / energy < MIN_COHERENCE) continue
            val gradientAngle = 0.5f * atan2(2f * c, a - b)
            dirX[i] = -sin(gradientAngle)
            dirY[i] = cos(gradientAngle)
            usable[i] = true
        }

        fun trace(startX: Int, startY: Int, initialX: Float, initialY: Float): Float {
            var x = startX + 0.5f
            var y = startY + 0.5f
            var px = initialX
            var py = initialY
            var length = 0f
            repeat(width + height) {
                val nx = x + px
                val ny = y + py
                val xi = nx.toInt()
                val yi = ny.toInt()
                if (nx < 0f || ny < 0f || xi >= width || yi >= height) return length
                val j = yi * width + xi
                if (!usable[j]) return length
                var dx = dirX[j]
                var dy = dirY[j]
                // A direction field has no arrowheads: keep heading the way the trace is going.
                if (dx * px + dy * py < 0f) {
                    dx = -dx
                    dy = -dy
                }
                if (dx * px + dy * py < MAX_TURN_COS) return length
                x = nx
                y = ny
                px = dx
                py = dy
                length += 1f
            }
            return length
        }

        val lengths = mutableListOf<Float>()
        for (y in 0..min(seedBottomRow, height - 1) step SEED_STEP) {
            for (x in 0 until width step SEED_STEP) {
                val i = y * width + x
                if (!usable[i]) continue
                lengths += trace(x, y, dirX[i], dirY[i]) + trace(x, y, -dirX[i], -dirY[i])
            }
        }
        if (lengths.size < MIN_SEEDS) return null
        lengths.sort()
        return lengths[((lengths.size - 1) * PERCENTILE).toInt()]
    }

    private fun isInterior(hair: BooleanArray, i: Int, width: Int, height: Int): Boolean {
        val x = i % width
        val y = i / width
        val m = EDGE_MARGIN
        return hair[i] && x >= m && y >= m && x < width - m && y < height - m &&
            hair[i - m] && hair[i + m] && hair[i - m * width] && hair[i + m * width]
    }

    /** Mean over a (2r+1)-square window, from an integral image. */
    private fun boxBlur(src: FloatArray, width: Int, height: Int, r: Int = SMOOTH_RADIUS): FloatArray {
        val stride = width + 1
        val sum = DoubleArray(stride * (height + 1))
        for (y in 0 until height) {
            var row = 0.0
            for (x in 0 until width) {
                row += src[y * width + x]
                sum[(y + 1) * stride + x + 1] = sum[y * stride + x + 1] + row
            }
        }
        return FloatArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val x0 = max(0, x - r)
            val x1 = min(width, x + r + 1)
            val y0 = max(0, y - r)
            val y1 = min(height, y + r + 1)
            ((sum[y1 * stride + x1] - sum[y0 * stride + x1] - sum[y1 * stride + x0] + sum[y0 * stride + x0]) /
                ((x1 - x0) * (y1 - y0))).toFloat()
        }
    }

    private const val GLABELLA = 9
}
