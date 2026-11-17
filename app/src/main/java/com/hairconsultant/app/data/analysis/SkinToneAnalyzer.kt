package com.hairconsultant.app.data.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.hairconsultant.app.domain.model.SkinTone
import com.hairconsultant.app.domain.model.SkinUndertone
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Whether a frame's light lets skin and hair colour be read; [hint] is what to tell the user otherwise. */
enum class Lighting(val hint: String?) {
    GOOD(null),
    DIM("More light would help"),
    BACKLIT("Bright light behind you — turn toward it"),
    HARSH("The light is too harsh"),
    UNEVEN("Face the light evenly")
}

/** One frame's skin: the median CIELAB colour of the cheeks and forehead, and how well lit they were. */
data class SkinReading(val l: Float, val a: Float, val b: Float, val lighting: Lighting) {
    val ita: Float get() = SkinToneAnalyzer.ita(l, b)
    val hue: Float get() = SkinToneAnalyzer.hue(a, b)
}

data class SkinToneResult(
    val tone: SkinTone,
    val toneConfidence: Float,
    val undertone: SkinUndertone,
    val undertoneConfidence: Float,
    /** [Lighting.GOOD] when at least one reading was well lit; otherwise the most common problem. */
    val lighting: Lighting,
    val ita: Float,
    val hue: Float
)

/**
 * Skin tone from the camera frame. Samples flat skin on both cheeks and mid-forehead (away from
 * brows, eyes, lips and nostrils), drops shine and shadow, and reads the median in CIELAB:
 *  - depth from the Individual Typology Angle, ITA = atan((L* - 50) / b*), in Chardon's six bands;
 *  - undertone from the hue angle atan2(b*, a*): pinker reads cool, yellower reads warm.
 * Phone cameras get depth roughly right in even light; undertone is easily shifted by white
 * balance, so its confidence stays low and the chat confirms it with the user.
 */
object SkinToneAnalyzer {

    private const val LEFT_CHEEK = 205
    private const val RIGHT_CHEEK = 425
    private const val FOREHEAD = 151

    /** ITA band edges, light to deep (degrees). */
    private val ITA_EDGES = floatArrayOf(55f, 41f, 28f, 10f, -30f)
    private val TONES = SkinTone.entries

    /** Hue angles (degrees) between cool and neutral, and neutral and warm. */
    private const val COOL_BELOW = 50f
    private const val WARM_ABOVE = 60f

    private const val MIN_PIXELS = 60

    /** Mean frame brightness (0-255) below which the camera is starved of light. */
    private const val DIM_FRAME_LUMA = 45f

    /** Share of skin pixels blown out to white above which the light is too harsh. */
    private const val HARSH_CLIPPED = 0.2f

    /** Relative brightness difference between the two cheeks above which the light comes from one side. */
    private const val UNEVEN_SIDES = 0.25f

    /**
     * Share of the frame's edges blown out to white above which a window or lamp is behind the
     * user. Read from clipping rather than from how much brighter the background is than the
     * face, which would also flag deep skin in front of a plain light wall.
     */
    private const val BACKLIT_CLIPPED = 0.3f

    private val LINEAR = DoubleArray(256) { i ->
        val c = i / 255.0
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /**
     * This frame's skin, or null when too little of it is visible. [isSkin] (normalized image
     * point) keeps only real skin, e.g. the face-skin class, so hair over the forehead isn't read.
     */
    fun read(bitmap: Bitmap, points: List<LandmarkPoint>, isSkin: ((Float, Float) -> Boolean)? = null): SkinReading? {
        if (points.size <= RIGHT_CHEEK || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val left = points[FaceShapeClassifier.LEFT_CHEEK]
        val right = points[FaceShapeClassifier.RIGHT_CHEEK]
        val faceWidth = hypot((left.x - right.x) * w, (left.y - right.y) * h)
        if (faceWidth < 40f) return null

        var clipped = 0
        val leftCheek = patch(bitmap, points[LEFT_CHEEK], faceWidth * 0.06f, isSkin) { clipped++ }
        val rightCheek = patch(bitmap, points[RIGHT_CHEEK], faceWidth * 0.06f, isSkin) { clipped++ }
        val forehead = patch(bitmap, points[FOREHEAD], faceWidth * 0.05f, isSkin) { clipped++ }
        val all = leftCheek + rightCheek + forehead
        if (all.size < MIN_PIXELS) return null

        // Drop the brightest and darkest 10% (shine, shadow), then take each channel's median.
        val sorted = all.sortedBy { it[0] }
        val trimmed = sorted.subList(sorted.size / 10, sorted.size - sorted.size / 10)
        val lighting = when {
            frameLuma(bitmap) < DIM_FRAME_LUMA -> Lighting.DIM
            borderClipped(bitmap) > BACKLIT_CLIPPED -> Lighting.BACKLIT
            clipped.toFloat() / (clipped + all.size) > HARSH_CLIPPED -> Lighting.HARSH
            unevenSides(leftCheek, rightCheek) -> Lighting.UNEVEN
            else -> Lighting.GOOD
        }
        return SkinReading(
            l = trimmed.map { it[0] }.median(),
            a = trimmed.map { it[1] }.median(),
            b = trimmed.map { it[2] }.median(),
            lighting = lighting
        )
    }

    /** One skin tone from several frames' readings, preferring the well-lit ones. */
    fun combine(readings: List<SkinReading>): SkinToneResult? {
        if (readings.isEmpty()) return null
        val lit = readings.filter { it.lighting == Lighting.GOOD }
        val used = lit.ifEmpty { readings }
        val l = used.map { it.l }.median()
        val a = used.map { it.a }.median()
        val b = used.map { it.b }.median()
        val ita = ita(l, b)
        val hue = hue(a, b)

        val tone = TONES[ITA_EDGES.count { ita <= it }]
        val edgeDistance = ITA_EDGES.minOf { abs(ita - it) }
        var toneConfidence = 0.55f + 0.35f * (edgeDistance / 8f).coerceIn(0f, 1f)
        val itaSpread = used.maxOf { it.ita } - used.minOf { it.ita }
        if (itaSpread > 10f) toneConfidence *= 0.85f

        val undertone = when {
            hue < COOL_BELOW -> SkinUndertone.COOL
            hue > WARM_ABOVE -> SkinUndertone.WARM
            else -> SkinUndertone.NEUTRAL
        }
        val boundaryDistance = when (undertone) {
            SkinUndertone.COOL -> COOL_BELOW - hue
            SkinUndertone.WARM -> hue - WARM_ABOVE
            SkinUndertone.NEUTRAL -> min(hue - COOL_BELOW, WARM_ABOVE - hue)
        }
        var undertoneConfidence = 0.45f + 0.3f * (boundaryDistance / 6f).coerceIn(0f, 1f)

        val lighting = if (lit.isNotEmpty()) {
            Lighting.GOOD
        } else {
            toneConfidence = min(toneConfidence, 0.4f)
            undertoneConfidence = min(undertoneConfidence, 0.35f)
            readings.groupingBy { it.lighting }.eachCount().maxBy { it.value }.key
        }
        return SkinToneResult(tone, toneConfidence, undertone, undertoneConfidence, lighting, ita, hue)
    }

    fun ita(l: Float, b: Float): Float = Math.toDegrees(atan2((l - 50f).toDouble(), b.toDouble())).toFloat()

    fun hue(a: Float, b: Float): Float = Math.toDegrees(atan2(b.toDouble(), a.toDouble())).toFloat()

    /** sRGB (D65) to CIELAB as [L*, a*, b*]. */
    fun cielab(argb: Int): FloatArray {
        val r = LINEAR[(argb shr 16) and 0xFF]
        val g = LINEAR[(argb shr 8) and 0xFF]
        val b = LINEAR[argb and 0xFF]
        val x = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
        val y = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
        val z = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
        return floatArrayOf((116 * y - 16).toFloat(), (500 * (x - y)).toFloat(), (200 * (y - z)).toFloat())
    }

    private fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0

    /** CIELAB of the unclipped pixels in a disc around [center]; [onClipped] counts the blown-out ones. */
    private fun patch(
        bitmap: Bitmap,
        center: LandmarkPoint,
        radius: Float,
        isSkin: ((Float, Float) -> Boolean)?,
        onClipped: () -> Unit
    ): List<FloatArray> {
        val w = bitmap.width
        val h = bitmap.height
        val cx = center.x * w
        val cy = center.y * h
        val step = max(1f, radius / 6f)
        val out = ArrayList<FloatArray>(160)
        var dy = -radius
        while (dy <= radius) {
            var dx = -radius
            while (dx <= radius) {
                if (dx * dx + dy * dy <= radius * radius) {
                    val px = (cx + dx).toInt()
                    val py = (cy + dy).toInt()
                    if (px in 0 until w && py in 0 until h && isSkin?.invoke(px / w.toFloat(), py / h.toFloat()) != false) {
                        val pixel = bitmap.getPixel(px, py)
                        val top = max(Color.red(pixel), max(Color.green(pixel), Color.blue(pixel)))
                        when {
                            top >= 250 -> onClipped()
                            top > 8 -> out.add(cielab(pixel))
                        }
                    }
                }
                dx += step
            }
            dy += step
        }
        return out
    }

    private fun unevenSides(left: List<FloatArray>, right: List<FloatArray>): Boolean {
        if (left.size < 20 || right.size < 20) return false
        val l = left.map { it[0] }.median()
        val r = right.map { it[0] }.median()
        val brighter = max(l, r)
        return brighter > 0f && abs(l - r) / brighter > UNEVEN_SIDES
    }

    /** Share of pixels clipped to white along the top and side edges of the frame, mostly background in a selfie. */
    private fun borderClipped(bitmap: Bitmap): Float {
        var clipped = 0
        var count = 0
        val stepX = max(1, bitmap.width / 24)
        val stepY = max(1, bitmap.height / 32)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val edge = y < bitmap.height * 0.08f || x < bitmap.width * 0.1f || x > bitmap.width * 0.9f
                if (!edge) continue
                val p = bitmap.getPixel(x, y)
                if (min(Color.red(p), min(Color.green(p), Color.blue(p))) >= 245) clipped++
                count++
            }
        }
        return if (count == 0) 0f else clipped.toFloat() / count
    }

    /** Mean brightness (0-255) of a coarse grid over the whole frame. */
    private fun frameLuma(bitmap: Bitmap): Float {
        var sum = 0f
        var count = 0
        val stepX = max(1, bitmap.width / 24)
        val stepY = max(1, bitmap.height / 32)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val p = bitmap.getPixel(x, y)
                sum += 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
                count++
            }
        }
        return if (count == 0) 0f else sum / count
    }
}

internal fun List<Float>.median(): Float {
    if (isEmpty()) return 0f
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
}
