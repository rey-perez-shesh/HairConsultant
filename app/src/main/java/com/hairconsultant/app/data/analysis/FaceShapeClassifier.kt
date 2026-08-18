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
 * Maps MediaPipe (478) / ML Kit (468) face-mesh points to a [FaceShape] using classic
 * length/width/jaw/forehead ratios. Neither Google API outputs a shape name on its own.
 *
 * Landmark indices are shared across both meshes for the first 468 points:
 *  10 forehead top, 152 chin, 234/454 cheeks, 172/397 jaw, 54/284 forehead sides.
 */
object FaceShapeClassifier {

    const val FOREHEAD_TOP = 10
    const val CHIN = 152
    const val LEFT_CHEEK = 234
    const val RIGHT_CHEEK = 454
    const val LEFT_JAW = 172
    const val RIGHT_JAW = 397
    const val LEFT_FOREHEAD = 54
    const val RIGHT_FOREHEAD = 284

    private val required = intArrayOf(
        FOREHEAD_TOP, CHIN, LEFT_CHEEK, RIGHT_CHEEK, LEFT_JAW, RIGHT_JAW, LEFT_FOREHEAD, RIGHT_FOREHEAD
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
        val forehead = dist(points[LEFT_FOREHEAD], points[RIGHT_FOREHEAD], w, h)
        if (length < 8f || cheek < 8f || jaw < 8f || forehead < 8f) return null

        val metrics = FaceShapeMetrics(
            lengthToWidth = length / cheek,
            jawToCheek = jaw / cheek,
            foreheadToJaw = forehead / jaw,
            foreheadToCheek = forehead / cheek
        )
        val shape = decide(metrics)
        return FaceShapeClassification(shape, confidence(shape, metrics), metrics)
    }

    private fun decide(m: FaceShapeMetrics): FaceShape {
        val lw = m.lengthToWidth
        val jc = m.jawToCheek
        val fj = m.foreheadToJaw
        val fc = m.foreheadToCheek
        return when {
            lw >= 1.58f -> FaceShape.LONG
            jc >= 1.02f && fj <= 0.92f -> FaceShape.TRIANGLE
            fc <= 0.82f && jc <= 0.86f && lw in 1.15f..1.55f -> FaceShape.DIAMOND
            fj >= 1.18f && jc <= 0.88f -> FaceShape.HEART
            lw <= 1.06f && jc >= 0.90f && fc >= 0.88f -> FaceShape.ROUND
            lw <= 1.28f && jc >= 0.93f && kotlin.math.abs(fj - 1f) <= 0.14f -> FaceShape.SQUARE
            else -> FaceShape.OVAL
        }
    }

    private fun confidence(shape: FaceShape, m: FaceShapeMetrics): Float {
        val score = when (shape) {
            FaceShape.LONG -> ((m.lengthToWidth - 1.58f) / 0.35f).coerceIn(0.55f, 0.93f)
            FaceShape.TRIANGLE -> ((m.jawToCheek - 1.02f) / 0.2f).coerceIn(0.55f, 0.9f)
            FaceShape.DIAMOND -> (0.82f - m.foreheadToCheek).coerceIn(0.55f, 0.9f)
            FaceShape.HEART -> ((m.foreheadToJaw - 1.18f) / 0.3f).coerceIn(0.55f, 0.9f)
            FaceShape.ROUND -> (1.12f - m.lengthToWidth).coerceIn(0.55f, 0.88f)
            FaceShape.SQUARE -> (m.jawToCheek).coerceIn(0.55f, 0.88f)
            FaceShape.OVAL -> 0.72f
        }
        return score
    }

    private fun dist(a: LandmarkPoint, b: LandmarkPoint, width: Float, height: Float): Float =
        hypot((a.x - b.x) * width, (a.y - b.y) * height)
}
