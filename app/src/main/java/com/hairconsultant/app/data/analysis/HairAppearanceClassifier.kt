package com.hairconsultant.app.data.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.domain.model.HairTexture
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class HairAppearanceClassification(
    val texture: HairTexture,
    val textureConfidence: Float,
    val color: HairColor,
    val colorConfidence: Float
)

/**
 * Estimates hair texture and color from pixels inside the hair mask.
 * Best-effort only — lighting and camera quality affect accuracy.
 */
object HairAppearanceClassifier {

    private const val MIN_HAIR_PIXELS = 80

    fun classify(bitmap: Bitmap, mask: HairMask): HairAppearanceClassification? {
        if (bitmap.width <= 0 || bitmap.height <= 0 || mask.pixelCount <= 0) return null

        val samples = sampleHairPixels(bitmap, mask)
        if (samples.size < MIN_HAIR_PIXELS) return null

        val color = classifyColor(samples)
        val texture = classifyTexture(bitmap, mask)
        return HairAppearanceClassification(
            texture = texture.first,
            textureConfidence = texture.second,
            color = color.first,
            colorConfidence = color.second
        )
    }

    private data class HairSample(val r: Int, val g: Int, val b: Int, val x: Int, val y: Int)

    private fun sampleHairPixels(bitmap: Bitmap, mask: HairMask): List<HairSample> {
        val samples = ArrayList<HairSample>(512)
        val step = max(1, mask.width / 128)
        for (my in 0 until mask.height step step) {
            val row = my * mask.width
            for (mx in 0 until mask.width step step) {
                if (!mask.isHair(row + mx)) continue
                val bx = (mx * bitmap.width / mask.width).coerceIn(0, bitmap.width - 1)
                val by = (my * bitmap.height / mask.height).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(bx, by)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val brightness = maxC / 255f
                val saturation = if (maxC == 0) 0f else (maxC - minC) / maxC.toFloat()
                if (brightness > 0.97f && saturation < 0.12f) continue
                if (brightness < 0.04f) continue
                samples.add(HairSample(r, g, b, bx, by))
            }
        }
        return samples
    }

    private fun classifyColor(samples: List<HairSample>): Pair<HairColor, Float> {
        var hueSum = 0f
        var satSum = 0f
        var valSum = 0f
        samples.forEach { sample ->
            val hsv = FloatArray(3)
            Color.RGBToHSV(sample.r, sample.g, sample.b, hsv)
            hueSum += hsv[0]
            satSum += hsv[1]
            valSum += hsv[2]
        }
        val count = samples.size.toFloat()
        val hue = hueSum / count
        val sat = satSum / count
        val value = valSum / count

        val color = when {
            value < 0.22f -> HairColor.BLACK
            sat < 0.12f && value > 0.45f -> HairColor.GRAY
            hue in 5f..45f && sat > 0.18f && value > 0.45f -> HairColor.BLONDE
            (hue <= 18f || hue >= 340f) && sat > 0.28f -> HairColor.RED
            value > 0.72f && sat < 0.35f -> HairColor.BLONDE
            else -> HairColor.BROWN
        }
        val confidence = when (color) {
            HairColor.BLACK -> (0.28f - value).coerceIn(0.08f, 0.35f) + 0.55f
            HairColor.GRAY -> (0.18f - sat).coerceIn(0.05f, 0.25f) + 0.55f
            HairColor.BLONDE -> (value - 0.55f).coerceIn(0.05f, 0.35f) + 0.52f
            HairColor.RED -> (sat - 0.25f).coerceIn(0.05f, 0.35f) + 0.52f
            HairColor.BROWN -> 0.58f
            HairColor.OTHER -> 0.4f
        }.coerceIn(0.45f, 0.82f)
        return color to confidence
    }

    private fun classifyTexture(
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
