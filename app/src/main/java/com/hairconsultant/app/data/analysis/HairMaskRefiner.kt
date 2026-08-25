package com.hairconsultant.app.data.analysis

import kotlin.math.max
import kotlin.math.min

/**
 * Turns a raw MediaPipe hair category/confidence mask into a soft, face-safe
 * replacement weight map for try-on. Does not invent a giant head plate.
 */
object HairMaskRefiner {

    /** MediaPipe face oval — interior is face skin; hair cover must stay outside. */
    private val FACE_OVAL = intArrayOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
        397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
        172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
    )

    private var prevAlpha: ByteArray? = null
    private var prevW: Int = 0
    private var prevH: Int = 0

    fun resetTemporal() {
        prevAlpha = null
        prevW = 0
        prevH = 0
    }

    /**
     * Refine hair region only:
     * 1) start from confidence (preferred) or category mask
     * 2) clear face oval so eyes/nose/mouth/cheeks stay untouched
     * 3) morphological cleanup (remove speckles)
     * 4) feather edges
     * 5) temporal EMA to reduce flicker
     */
    fun refineForTryOn(mask: HairMask, points: List<LandmarkPoint>): SoftHairMask {
        val w = mask.width
        val h = mask.height
        if (w <= 0 || h <= 0) {
            return SoftHairMask(ByteArray(1), 1, 1)
        }

        val raw = FloatArray(w * h)
        val conf = mask.confidence
        if (conf != null && conf.size == w * h) {
            for (i in raw.indices) {
                val c = conf[i].coerceIn(0f, 1f)
                // Soft threshold: keep weak fringe for feathering, drop noise.
                raw[i] = when {
                    c < 0.22f -> 0f
                    c > 0.72f -> 1f
                    else -> (c - 0.22f) / 0.50f
                }
            }
        } else {
            for (i in raw.indices) {
                raw[i] = if (mask.isHair(i)) 1f else 0f
            }
        }

        clearFaceOval(raw, w, h, points)
        morphOpen(raw, w, h)
        morphErodeHairNearFace(raw, w, h, points)
        featherEdges(raw, w, h, radius = 2)
        val alpha = toAlphaBytes(raw)
        val smoothed = temporalSmooth(alpha, w, h)
        return SoftHairMask(smoothed, w, h)
    }

    private fun clearFaceOval(
        weights: FloatArray,
        w: Int,
        h: Int,
        points: List<LandmarkPoint>
    ) {
        if (points.size <= FaceShapeClassifier.CHIN) return
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val chin = points[FaceShapeClassifier.CHIN]
        val faceH = (chin.y - top.y).coerceAtLeast(0.08f)
        // Leave hairline free; clear from lower forehead through chin.
        val topInset = top.y + faceH * 0.10f

        val poly = if (FACE_OVAL.all { it < points.size }) {
            FACE_OVAL.map { idx ->
                val p = points[idx]
                floatArrayOf(p.x, maxOf(p.y, topInset))
            }
        } else {
            val left = points[FaceShapeClassifier.LEFT_CHEEK]
            val right = points[FaceShapeClassifier.RIGHT_CHEEK]
            listOf(
                floatArrayOf(left.x, topInset),
                floatArrayOf(right.x, topInset),
                floatArrayOf(right.x, chin.y),
                floatArrayOf(left.x, chin.y)
            )
        }

        var minX = 1f
        var maxX = 0f
        var minY = 1f
        var maxY = 0f
        for (p in poly) {
            minX = min(minX, p[0])
            maxX = max(maxX, p[0])
            minY = min(minY, p[1])
            maxY = max(maxY, p[1])
        }
        val x0 = (minX * w).toInt().coerceIn(0, w - 1)
        val x1 = (maxX * w).toInt().coerceIn(0, w - 1)
        val y0 = (minY * h).toInt().coerceIn(0, h - 1)
        val y1 = (maxY * h).toInt().coerceIn(0, h - 1)
        for (y in y0..y1) {
            val ny = (y + 0.5f) / h
            val row = y * w
            for (x in x0..x1) {
                val nx = (x + 0.5f) / w
                if (pointInPolygon(nx, ny, poly)) {
                    weights[row + x] = 0f
                }
            }
        }
    }

    private fun pointInPolygon(x: Float, y: Float, poly: List<FloatArray>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i][0]
            val yi = poly[i][1]
            val xj = poly[j][0]
            val yj = poly[j][1]
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-6f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    /** Remove isolated speckles: erode then dilate on binary view of weights. */
    private fun morphOpen(weights: FloatArray, w: Int, h: Int) {
        val bin = BooleanArray(weights.size) { weights[it] >= 0.35f }
        val eroded = BooleanArray(bin.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var keep = true
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (!bin[(y + dy) * w + (x + dx)]) {
                            keep = false
                            break@loop
                        }
                    }
                }
                eroded[y * w + x] = keep
            }
        }
        val opened = BooleanArray(bin.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var any = false
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (eroded[(y + dy) * w + (x + dx)]) {
                            any = true
                            break@loop
                        }
                    }
                }
                opened[y * w + x] = any
            }
        }
        for (i in weights.indices) {
            if (!opened[i] && bin[i]) weights[i] *= 0.15f
            if (!opened[i] && !bin[i]) weights[i] = 0f
        }
    }

    /** Extra safety: pull hair weight back from cheek/forehead rim. */
    private fun morphErodeHairNearFace(
        weights: FloatArray,
        w: Int,
        h: Int,
        points: List<LandmarkPoint>
    ) {
        if (points.size <= FaceShapeClassifier.CHIN) return
        val left = points[FaceShapeClassifier.LEFT_CHEEK]
        val right = points[FaceShapeClassifier.RIGHT_CHEEK]
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val chin = points[FaceShapeClassifier.CHIN]
        val cx = (left.x + right.x) * 0.5f
        val cy = (top.y + chin.y) * 0.48f
        val rx = ((right.x - left.x) * 0.52f).coerceAtLeast(0.08f)
        val ry = ((chin.y - top.y) * 0.52f).coerceAtLeast(0.08f)
        for (y in 0 until h) {
            val ny = (y + 0.5f) / h
            val row = y * w
            for (x in 0 until w) {
                val nx = (x + 0.5f) / w
                val dx = (nx - cx) / rx
                val dy = (ny - cy) / ry
                if (dx * dx + dy * dy <= 1f) {
                    weights[row + x] = 0f
                }
            }
        }
    }

    private fun featherEdges(weights: FloatArray, w: Int, h: Int, radius: Int) {
        if (radius <= 0) return
        val tmp = weights.copyOf()
        // Separable box blur on weights → soft fringe without hard rectangles.
        for (pass in 0 until 2) {
            // horizontal
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var sum = 0f
                    var n = 0
                    for (dx in -radius..radius) {
                        val xx = x + dx
                        if (xx in 0 until w) {
                            sum += tmp[row + xx]
                            n++
                        }
                    }
                    weights[row + x] = sum / n
                }
            }
            // vertical
            System.arraycopy(weights, 0, tmp, 0, weights.size)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0f
                    var n = 0
                    for (dy in -radius..radius) {
                        val yy = y + dy
                        if (yy in 0 until h) {
                            sum += tmp[yy * w + x]
                            n++
                        }
                    }
                    weights[y * w + x] = sum / n
                }
            }
            System.arraycopy(weights, 0, tmp, 0, weights.size)
        }
        for (i in weights.indices) {
            val v = weights[i]
            // Keep core opaque enough to hide real hair; fringe stays soft.
            weights[i] = when {
                v < 0.08f -> 0f
                v > 0.85f -> 1f
                else -> v
            }
        }
    }

    private fun toAlphaBytes(weights: FloatArray): ByteArray {
        val out = ByteArray(weights.size)
        for (i in weights.indices) {
            // Cap peak alpha so we never paint a solid gray plate; GLB covers the rest.
            val a = (weights[i] * 200f).toInt().coerceIn(0, 200)
            out[i] = a.toByte()
        }
        return out
    }

    private fun temporalSmooth(alpha: ByteArray, w: Int, h: Int): ByteArray {
        val prev = prevAlpha
        if (prev == null || prevW != w || prevH != h || prev.size != alpha.size) {
            prevAlpha = alpha.copyOf()
            prevW = w
            prevH = h
            return alpha
        }
        val out = ByteArray(alpha.size)
        for (i in alpha.indices) {
            val cur = alpha[i].toInt() and 0xFF
            val old = prev[i].toInt() and 0xFF
            out[i] = ((old * 0.55f) + (cur * 0.45f)).toInt().coerceIn(0, 255).toByte()
        }
        prevAlpha = out.copyOf()
        return out
    }
}
