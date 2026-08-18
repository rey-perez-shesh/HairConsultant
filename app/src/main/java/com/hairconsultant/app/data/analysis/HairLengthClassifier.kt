package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.HairLength

/** Per-pixel category mask from MediaPipe Image Segmenter. Hair is usually class 1. */
data class HairMask(
    val labels: ByteArray,
    val width: Int,
    val height: Int,
    val hairClass: Int = HairLengthClassifier.HAIR_CLASS
) {
    val pixelCount: Int get() = width * height

    fun isHair(index: Int): Boolean {
        if (index !in labels.indices) return false
        val value = labels[index].toInt() and 0xFF
        return value == hairClass || value == 255
    }
}

data class HairLengthClassification(
    val length: HairLength,
    val confidence: Float,
    val coverage: Float,
    val belowChinRatio: Float
)

/**
 * Turns a hair mask + face landmarks into Short / Medium / Long.
 * Length is how far hair extends below the chin, in face-heights.
 */
object HairLengthClassifier {

    const val HAIR_CLASS = 1

    fun classify(mask: HairMask, points: List<LandmarkPoint>): HairLengthClassification? {
        if (mask.width <= 0 || mask.height <= 0) return null
        if (points.size <= FaceShapeClassifier.CHIN) return null

        var hairPixels = 0
        var minY = mask.height
        var maxY = -1
        val rowThreshold = (mask.width * 0.008f).coerceAtLeast(1f)
        for (y in 0 until mask.height) {
            var rowHair = 0
            val row = y * mask.width
            for (x in 0 until mask.width) {
                if (mask.isHair(row + x)) {
                    hairPixels++
                    rowHair++
                }
            }
            if (rowHair >= rowThreshold) {
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        val coverage = hairPixels / mask.pixelCount.toFloat()
        if (hairPixels < 40 || coverage < 0.008f || maxY < 0) {
            return HairLengthClassification(
                length = HairLength.BALD,
                confidence = if (coverage < 0.003f) 0.78f else 0.62f,
                coverage = coverage,
                belowChinRatio = 0f
            )
        }

        val hairMaxY = maxY / mask.height.toFloat()
        val foreheadY = points[FaceShapeClassifier.FOREHEAD_TOP].y
        val chinY = points[FaceShapeClassifier.CHIN].y
        val faceHeight = (chinY - foreheadY).coerceAtLeast(0.08f)
        val belowChin = (hairMaxY - chinY) / faceHeight

        val length = when {
            belowChin < 0.22f -> HairLength.SHORT
            belowChin > 1.15f || hairMaxY >= 0.90f -> HairLength.LONG
            else -> HairLength.MEDIUM
        }

        val base = when (length) {
            HairLength.BALD -> 0.7f
            HairLength.SHORT -> (0.55f + (0.22f - belowChin).coerceAtLeast(0f)).coerceIn(0.45f, 0.88f)
            HairLength.LONG -> (0.58f + (belowChin - 1.15f).coerceAtLeast(0f) * 0.2f).coerceIn(0.50f, 0.88f)
            HairLength.MEDIUM -> 0.62f
        }
        val confidence = (base * (0.75f + coverage.coerceIn(0.02f, 0.25f) * 1.2f)).coerceIn(0.4f, 0.9f)

        return HairLengthClassification(length, confidence, coverage, belowChin)
    }
}
