package com.hairconsultant.app.data.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.ui.haircolor.HairColorMath
import com.hairconsultant.app.ui.haircolor.HairShades
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hair colour and (fallback) texture from the pixels inside the hair mask.
 *
 * Colour is read in OKLab, the perceptual space the Color Studio dyes in: the trimmed median of
 * confidently-hair pixels away from the mask's edge, matched to the nearest natural Color Studio
 * shade. Hue is never averaged directly; red hair straddles 0°/360°, where a plain average of
 * hues lands on cyan.
 */
object HairAppearanceClassifier {

    private const val MIN_HAIR_PIXELS = 80

    /** Hair-mask confidence (0-255) a pixel needs to count toward the colour. */
    private const val CONFIDENT_HAIR = 204

    /** Chroma above which a hue outside natural hair's range reads as a dyed fashion colour. */
    private const val FASHION_CHROMA = 0.06f
    private val NATURAL_HUES = 20f..110f

    /** Below this chroma and above this lightness, hair reads as gray, silver or white. */
    private const val GRAY_CHROMA = 0.025f
    private const val GRAY_LIGHTNESS = 0.45f

    /** The natural Color Studio shades as colour references; their swatches are each shade's evenly lit mid-tone. */
    private val ANCHORS: List<Pair<HairColor, FloatArray>> = listOf(
        "jet_black" to HairColor.BLACK,
        "soft_black" to HairColor.BLACK,
        "dark_brown" to HairColor.BROWN,
        "chestnut" to HairColor.BROWN,
        "light_brown" to HairColor.BROWN,
        "auburn" to HairColor.RED,
        "copper" to HairColor.RED,
        "dark_blonde" to HairColor.BLONDE,
        "golden_blonde" to HairColor.BLONDE,
        "platinum" to HairColor.BLONDE
    ).map { (id, color) -> color to HairColorMath.oklabOf(HairShades.byId(id)!!.argb) }

    /**
     * The hair's colour as OKLab [L, a, b], or null with too little hair. Skips pixels within two
     * samples of the mask's edge (they mix in skin and background) and any [exclude] marks (e.g.
     * skin or clothing the hair mask claimed), then drops the darkest and brightest 20% (shadow,
     * shine) before taking each channel's median.
     */
    fun hairColor(bitmap: Bitmap, mask: HairMask, exclude: ((Float, Float) -> Boolean)? = null): FloatArray? {
        if (bitmap.isRecycled || bitmap.width <= 0 || mask.pixelCount <= 0) return null
        val step = max(1, mask.width / 128)
        val edge = 2 * step
        val samples = ArrayList<FloatArray>(1024)
        for (my in edge until mask.height - edge step step) {
            for (mx in edge until mask.width - edge step step) {
                val index = my * mask.width + mx
                if (mask.hairAlpha(index) < CONFIDENT_HAIR) continue
                if (!mask.isHair(index - edge) || !mask.isHair(index + edge) ||
                    !mask.isHair(index - edge * mask.width) || !mask.isHair(index + edge * mask.width)
                ) continue
                val nx = (mx + 0.5f) / mask.width
                val ny = (my + 0.5f) / mask.height
                if (exclude?.invoke(nx, ny) == true) continue
                val bx = (nx * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val by = (ny * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                samples.add(HairColorMath.oklabOf(bitmap.getPixel(bx, by)))
            }
        }
        if (samples.size < MIN_HAIR_PIXELS) return null
        val sorted = samples.sortedBy { it[0] }
        val trimmed = sorted.subList(sorted.size / 5, sorted.size - sorted.size / 5)
        return floatArrayOf(trimmed.map { it[0] }.median(), trimmed.map { it[1] }.median(), trimmed.map { it[2] }.median())
    }

    /** The [HairColor] for an OKLab colour, with how clearly it beat the next-closest colour. */
    fun classifyColor(lab: FloatArray): Pair<HairColor, Float> {
        val (l, a, b) = Triple(lab[0], lab[1], lab[2])
        val chroma = hypot(a, b)
        val hue = Math.toDegrees(atan2(b.toDouble(), a.toDouble())).toFloat().let { if (it < 0f) it + 360f else it }
        if (chroma >= FASHION_CHROMA && hue !in NATURAL_HUES) {
            return HairColor.FASHION to (0.55f + (chroma - FASHION_CHROMA) * 3f).coerceAtMost(0.85f)
        }
        if (chroma < GRAY_CHROMA && l >= GRAY_LIGHTNESS) {
            return HairColor.GRAY to (0.55f + (GRAY_CHROMA - chroma) * 10f).coerceAtMost(0.8f)
        }
        val nearest = ANCHORS.groupBy({ it.first }, { distance(lab, it.second) }).mapValues { it.value.min() }
            .entries.sortedBy { it.value }
        val best = nearest[0]
        val runnerUp = nearest.getOrNull(1)?.value ?: return best.key to 0.6f
        // 0 when the two closest colours are equally near, 1 when the runner-up is much further.
        val margin = (runnerUp - best.value) / (runnerUp + best.value).coerceAtLeast(1e-4f)
        return best.key to (0.5f + 0.4f * (margin * 2f).coerceIn(0f, 1f)).coerceAtMost(0.88f)
    }

    private fun distance(p: FloatArray, q: FloatArray): Float {
        val dl = p[0] - q[0]
        val da = p[1] - q[1]
        val db = p[2] - q[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    /**
     * Texture from how often brightness changes direction along each row of hair. Only used when
     * the trained texture model ([HairTypeTfliteClassifier]) isn't available.
     */
    fun classifyTexture(
        bitmap: Bitmap,
        mask: HairMask
    ): Pair<HairTexture, Float> {
        val grid = Array(mask.height) { IntArray(mask.width) { -1 } }
        var minY = mask.height
        var maxY = -1
        var minX = mask.width
        var maxX = -1
        for (my in 0 until mask.height) {
            val row = my * mask.width
            for (mx in 0 until mask.width) {
                if (!mask.isHair(row + mx)) continue
                val bx = (mx * bitmap.width / mask.width).coerceIn(0, bitmap.width - 1)
                val by = (my * bitmap.height / mask.height).coerceIn(0, bitmap.height - 1)
                val lum = luminance(bitmap.getPixel(bx, by))
                grid[my][mx] = lum
                if (my < minY) minY = my
                if (my > maxY) maxY = my
                if (mx < minX) minX = mx
                if (mx > maxX) maxX = mx
            }
        }
        if (maxY <= minY || maxX <= minX) return HairTexture.STRAIGHT to 0.4f

        var edgeEnergy = 0f
        var edgeCount = 0
        var signChanges = 0
        var rowCount = 0
        for (my in minY..maxY) {
            var prevDeriv = 0
            var rowChanges = 0
            for (mx in (minX + 1)..maxX) {
                val current = grid[my][mx]
                val previous = grid[my][mx - 1]
                if (current < 0 || previous < 0) continue
                val deriv = current - previous
                edgeEnergy += abs(deriv)
                edgeCount++
                if (mx > minX + 1 && prevDeriv != 0 && (deriv > 0) != (prevDeriv > 0)) {
                    rowChanges++
                }
                prevDeriv = deriv
            }
            signChanges += rowChanges
            rowCount++
        }
        if (edgeCount == 0) return HairTexture.STRAIGHT to 0.4f

        val avgEdge = edgeEnergy / edgeCount
        val avgChanges = signChanges / rowCount.toFloat()

        val texture = when {
            avgChanges < 2.5f && avgEdge < 14f -> HairTexture.STRAIGHT
            avgChanges < 6f && avgEdge < 24f -> HairTexture.WAVY
            else -> HairTexture.CURLY
        }
        val confidence = when (texture) {
            HairTexture.STRAIGHT -> (0.55f + (8f - avgChanges).coerceAtLeast(0f) * 0.02f)
            HairTexture.WAVY -> 0.58f
            HairTexture.CURLY -> (0.55f + (avgChanges - 6f).coerceAtLeast(0f) * 0.02f)
        }.coerceIn(0.45f, 0.78f)
        return texture to confidence
    }

    private fun luminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
    }
}
