package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.HairLength
import kotlin.math.max
import kotlin.math.min

/** Per-pixel category mask from MediaPipe Image Segmenter. Hair is usually class 1. */
data class HairMask(
    val labels: ByteArray,
    val width: Int,
    val height: Int,
    val hairClass: Int = HairLengthClassifier.HAIR_CLASS,
    /** Optional per-pixel hair confidence (0-255), when the segmenter was asked for it. */
    val hairConfidence: ByteArray? = null
) {
    val pixelCount: Int get() = width * height

    fun hairAlpha(index: Int): Int {
        val confidence = hairConfidence
        if (confidence != null && index in confidence.indices) {
            return confidence[index].toInt() and 0xFF
        }
        return if (isHair(index)) 255 else 0
    }

    fun isHair(index: Int): Boolean {
        val lab = labels
        if (index !in lab.indices) return false
        val value = lab[index].toInt() and 0xFF
        return value == hairClass || value == 255
    }

    /** [isHair] at a normalized image point. */
    fun isHairAt(x: Float, y: Float): Boolean {
        val mx = (x * width).toInt().coerceIn(0, width - 1)
        val my = (y * height).toInt().coerceIn(0, height - 1)
        return isHair(my * width + mx)
    }
}

data class HairLengthClassification(
    val length: HairLength,
    val confidence: Float,
    val coverage: Float,
    val belowChinRatio: Float,
    /** The hair runs off the bottom of the image, so where it really ends can't be seen. */
    val reachesFrameBottom: Boolean = false
)

/**
 * Turns a hair mask + face landmarks into Bald / Short / Medium / Long: how far hair extends
 * below the chin, in face-heights. Hair between the jaw corners below the nose is a beard, not
 * length, so it's left out of that reach.
 */
object HairLengthClassifier {

    const val HAIR_CLASS = 1

    private const val SUBNASALE = 2
    private const val SHORT_BELOW_CHIN = 0.22f
    private const val LONG_BELOW_CHIN = 1.15f

    /** Confidence cap when the hair runs off the frame: it's at least this long, but Long can't be told from Medium. */
    private const val CUT_OFF_CONFIDENCE = 0.45f

    /** [isNotHair] (normalized image point) drops pixels the mask claims that are really skin or clothing. */
    fun classify(
        mask: HairMask,
        points: List<LandmarkPoint>,
        isNotHair: ((Float, Float) -> Boolean)? = null
    ): HairLengthClassification? {
        if (mask.width <= 0 || mask.height <= 0) return null
        if (points.size <= FaceShapeClassifier.RIGHT_JAW) return null

        val jawLeft = min(points[FaceShapeClassifier.LEFT_JAW].x, points[FaceShapeClassifier.RIGHT_JAW].x)
        val jawRight = max(points[FaceShapeClassifier.LEFT_JAW].x, points[FaceShapeClassifier.RIGHT_JAW].x)
        val noseY = points[SUBNASALE].y
        var hairPixels = 0
        var maxY = -1
        val rowThreshold = (mask.width * 0.008f).coerceAtLeast(1f)
        for (y in 0 until mask.height) {
            val ny = (y + 0.5f) / mask.height
            var rowHair = 0
            val row = y * mask.width
            for (x in 0 until mask.width) {
                if (!mask.isHair(row + x)) continue
                val nx = (x + 0.5f) / mask.width
                if (isNotHair?.invoke(nx, ny) == true) continue
                hairPixels++
                if (ny > noseY && nx in jawLeft..jawRight) continue
                rowHair++
            }
            if (rowHair >= rowThreshold) maxY = y
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
        val cutOff = maxY >= mask.height - 1 - (mask.height * 0.02f).toInt()
        return decide(coverage, (hairMaxY - chinY) / faceHeight, cutOff)
    }

    /**
     * One length from several frames: Bald if most frames saw (almost) no hair, otherwise the
     * median reach below the chin, cut off if most frames had the hair run off the bottom.
     */
    fun combine(readings: List<HairLengthClassification>): HairLengthClassification? {
        if (readings.isEmpty()) return null
        val (bald, hairy) = readings.partition { it.length == HairLength.BALD }
        if (bald.size > hairy.size) {
            return HairLengthClassification(
                length = HairLength.BALD,
                confidence = bald.map { it.confidence }.median(),
                coverage = bald.map { it.coverage }.median(),
                belowChinRatio = 0f
            )
        }
        return decide(
            coverage = hairy.map { it.coverage }.median(),
            belowChin = hairy.map { it.belowChinRatio }.median(),
            cutOff = hairy.count { it.reachesFrameBottom } * 2 > hairy.size
        )
    }

    private fun decide(coverage: Float, belowChin: Float, cutOff: Boolean): HairLengthClassification {
        val measured = when {
            belowChin < SHORT_BELOW_CHIN -> HairLength.SHORT
            belowChin > LONG_BELOW_CHIN -> HairLength.LONG
            else -> HairLength.MEDIUM
        }
        // Hair that runs off the bottom of the frame goes past the chin at least that far.
        val length = if (cutOff && measured == HairLength.SHORT) HairLength.MEDIUM else measured
        val base = when (length) {
            HairLength.BALD -> 0.7f
            HairLength.SHORT -> (0.55f + (SHORT_BELOW_CHIN - belowChin).coerceAtLeast(0f)).coerceIn(0.45f, 0.88f)
            HairLength.LONG -> (0.58f + (belowChin - LONG_BELOW_CHIN).coerceAtLeast(0f) * 0.2f).coerceIn(0.50f, 0.88f)
            HairLength.MEDIUM -> 0.62f
        }
        val confidence = (base * (0.75f + coverage.coerceIn(0.02f, 0.25f) * 1.2f)).coerceIn(0.4f, 0.9f)
        return HairLengthClassification(
            length = length,
            confidence = if (cutOff && length != HairLength.LONG) min(confidence, CUT_OFF_CONFIDENCE) else confidence,
            coverage = coverage,
            belowChinRatio = belowChin,
            reachesFrameBottom = cutOff
        )
    }
}
