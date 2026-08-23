package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.FaceShape
import kotlin.math.hypot

data class LandmarkPoint(val x: Float, val y: Float)

data class FaceShapeMetrics(
    val lengthToWidth: Float,
    val jawToCheek: Float,
    val foreheadToJaw: Float,
    val foreheadToCheek: Float
)

data class FaceShapeClassification(
    val shape: FaceShape,
    val confidence: Float,
    val metrics: FaceShapeMetrics
)

/**
 * Maps MediaPipe (478) / ML Kit (468) face-mesh points to a [FaceShape] using five measured
 * zones: forehead/temple width, cheekbone width (usually the widest point on the face), jaw
 * width, chin tip, and forehead top (a hairline approximation, since the mesh doesn't extend
 * past the hairline). Neither Google API outputs a shape name on its own.
 *
 * Landmark indices are shared across both meshes for the first 468 points, using the
 * community-standard conventions: 10 forehead-top, 152 chin, 234/454 cheekbone edges,
 * 127/356 temples, 172/397 jaw corners.
 */
object FaceShapeClassifier {

    const val FOREHEAD_TOP = 10
    const val CHIN = 152
    const val LEFT_CHEEK = 234
    const val RIGHT_CHEEK = 454
    const val LEFT_JAW = 172
    const val RIGHT_JAW = 397
    const val LEFT_TEMPLE = 127
    const val RIGHT_TEMPLE = 356

    private val required = intArrayOf(
        FOREHEAD_TOP, CHIN, LEFT_CHEEK, RIGHT_CHEEK, LEFT_JAW, RIGHT_JAW, LEFT_TEMPLE, RIGHT_TEMPLE
    )

    fun classify(
        points: List<LandmarkPoint>,
        imageWidth: Int,
        imageHeight: Int
    ): FaceShapeClassification? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (required.any { it >= points.size }) return null

        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val length = dist(points[FOREHEAD_TOP], points[CHIN], w, h)
        val cheek = dist(points[LEFT_CHEEK], points[RIGHT_CHEEK], w, h)
        val jaw = dist(points[LEFT_JAW], points[RIGHT_JAW], w, h)
        val temple = dist(points[LEFT_TEMPLE], points[RIGHT_TEMPLE], w, h)
        if (length < 8f || cheek < 8f || jaw < 8f || temple < 8f) return null

        val metrics = FaceShapeMetrics(
            lengthToWidth = length / cheek,
            jawToCheek = jaw / cheek,
            foreheadToJaw = temple / jaw,
            foreheadToCheek = temple / cheek
        )
        val shape = decide(metrics)
        return FaceShapeClassification(shape, confidence(shape, metrics), metrics)
    }

    /**
     * Oval/round/square/heart/diamond only — decided from how the temple, cheekbone and jaw
     * widths compare, plus overall face length. Cheekbones are treated as the reference width
     * since they're usually the widest zone.
     */
    private fun decide(m: FaceShapeMetrics): FaceShape {
        val lengthToCheek = m.lengthToWidth
        val jawToCheek = m.jawToCheek
        val foreheadToCheek = m.foreheadToCheek
        val foreheadToJaw = m.foreheadToJaw
        return when {
            // Cheekbones clearly the widest zone; forehead and jaw both narrow relative to them.
            foreheadToCheek <= 0.90f && jawToCheek <= 0.85f -> FaceShape.DIAMOND
            // Forehead/temples clearly wider than the jaw, tapering down to the chin.
            foreheadToJaw >= 1.12f && jawToCheek <= 0.88f -> FaceShape.HEART
            // Jaw nearly as wide as the cheekbones and forehead, on a shorter face: a strong,
            // boxy jawline.
            jawToCheek >= 0.94f && foreheadToCheek >= 0.90f && lengthToCheek <= 1.35f -> FaceShape.SQUARE
            // Similar widths all around but a shorter face and a softer jaw.
            lengthToCheek <= 1.15f && jawToCheek >= 0.82f -> FaceShape.ROUND
            // Longer face that gently tapers from cheekbones to jaw.
            else -> FaceShape.OVAL
        }
    }

    private fun confidence(shape: FaceShape, m: FaceShapeMetrics): Float {
        val score = when (shape) {
            FaceShape.DIAMOND -> (0.88f - m.foreheadToCheek).coerceIn(0.55f, 0.9f)
            FaceShape.HEART -> ((m.foreheadToJaw - 1.12f) / 0.4f).coerceIn(0.55f, 0.9f)
            FaceShape.SQUARE -> m.jawToCheek.coerceIn(0.55f, 0.9f)
            FaceShape.ROUND -> (1.15f - m.lengthToWidth).coerceIn(0.55f, 0.88f)
            FaceShape.OVAL -> 0.72f
        }
        return score
    }

    private fun dist(a: LandmarkPoint, b: LandmarkPoint, width: Float, height: Float): Float =
        hypot((a.x - b.x) * width, (a.y - b.y) * height)
}
