package com.hairconsultant.app.data.analysis

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Head pose from MediaPipe face mesh landmarks for anchoring a 3D wig in SceneView.
 *
 * Landmarks are already front-camera mirrored in [LiveFaceLandmarker]
 * (image x: 0 = screen left, 1 = screen right; y: 0 = top).
 */
data class HeadPose(
    val positionX: Float,
    val positionY: Float,
    val positionZ: Float,
    val scale: Float,
    val yawDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float
)

object HeadPoseEstimator {

    private const val NOSE_TIP = 1
    private const val LEFT_EYE = 33
    private const val RIGHT_EYE = 263

    fun estimate(points: List<LandmarkPoint>): HeadPose? {
        if (points.size <= FaceShapeClassifier.RIGHT_CHEEK) return null
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val chin = points[FaceShapeClassifier.CHIN]
        val left = points[FaceShapeClassifier.LEFT_CHEEK]
        val right = points[FaceShapeClassifier.RIGHT_CHEEK]
        val nose = points.getOrNull(NOSE_TIP)
            ?: LandmarkPoint((left.x + right.x) / 2f, (top.y + chin.y) / 2f)
        val leftEye = points.getOrNull(LEFT_EYE) ?: left
        val rightEye = points.getOrNull(RIGHT_EYE) ?: right

        val midCheekX = (left.x + right.x) / 2f
        val midFaceY = (top.y + chin.y) / 2f
        val faceH = (chin.y - top.y).coerceAtLeast(0.08f)
        val faceW = (right.x - left.x).coerceAtLeast(0.08f)

        // SceneView camera looks down -Z; keep wig slightly closer than the background plane (z=0).
        // X uses (0.5 - midX): when face is on screen-right (midX>0.5), positionX is negative.
        // Overlay applies HAIR_MIRROR_X once to map that into SceneView +X = screen right.
        val positionX = (0.5f - midCheekX) * 0.42f
        val crownY = top.y - faceH * 0.38f
        val positionY = (0.38f - crownY) * 0.95f
        val positionZ = (0.08f - faceH * 0.18f).coerceIn(-0.08f, 0.18f)

        val scale = (0.35f + faceH * 0.55f + faceW * 0.12f).coerceIn(0.28f, 0.62f)

        // Already in degrees (±35). Frontal face → yawDeg ≈ 0 (NOT 180).
        val yawDeg = ((nose.x - midCheekX) / faceW * 55f).coerceIn(-35f, 35f)
        val pitchDeg = ((nose.y - midFaceY) / faceH * 40f).coerceIn(-25f, 25f)
        val rollDeg = Math.toDegrees(
            atan2((rightEye.y - leftEye.y).toDouble(), (rightEye.x - leftEye.x).toDouble())
        ).toFloat().coerceIn(-30f, 30f)

        return HeadPose(
            positionX = positionX,
            positionY = positionY,
            positionZ = positionZ,
            scale = scale,
            yawDeg = yawDeg,
            pitchDeg = pitchDeg,
            // Front camera is mirrored; flip roll so the wig stays level with the face.
            rollDeg = -rollDeg
        )
    }

    /** Exponential moving average to reduce landmark jitter on the wig. */
    fun smooth(previous: HeadPose?, next: HeadPose, alpha: Float = 0.35f): HeadPose {
        if (previous == null) return next
        val a = alpha.coerceIn(0.05f, 1f)
        val b = 1f - a
        return HeadPose(
            positionX = previous.positionX * b + next.positionX * a,
            positionY = previous.positionY * b + next.positionY * a,
            positionZ = previous.positionZ * b + next.positionZ * a,
            scale = previous.scale * b + next.scale * a,
            yawDeg = previous.yawDeg * b + next.yawDeg * a,
            pitchDeg = previous.pitchDeg * b + next.pitchDeg * a,
            rollDeg = previous.rollDeg * b + next.rollDeg * a
        )
    }

    fun faceSpan(points: List<LandmarkPoint>): Float {
        if (points.size <= FaceShapeClassifier.CHIN) return 0f
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val chin = points[FaceShapeClassifier.CHIN]
        return hypot(chin.x - top.x, chin.y - top.y)
    }
}
