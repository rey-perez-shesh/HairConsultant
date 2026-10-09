package com.hairconsultant.app.data.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * How far the head is turned away from facing the camera, in degrees. Only magnitudes matter to
 * the face-shape scan, so signs aren't normalized (the front camera image is mirrored anyway).
 *  - [yawDeg]: turned left/right
 *  - [pitchDeg]: tilted up/down (nodding)
 *  - [rollDeg]: tilted sideways (ear toward shoulder)
 */
data class HeadAngles(val yawDeg: Float, val pitchDeg: Float, val rollDeg: Float) {
    companion object {
        /**
         * Reads the angles from MediaPipe's facial transformation matrix (4x4, column-major),
         * which maps the canonical face model, facing +z with +y up, into camera space. The
         * model's forward axis (third column) gives yaw and pitch; its up axis (second column)
         * gives roll. Uses atan2 on ratios, so any uniform scale in the matrix cancels out, and
         * |z|/|y| so a flipped axis convention can't read as a 180° turn.
         */
        fun fromTransformationMatrix(m: FloatArray): HeadAngles? {
            if (m.size < 16) return null
            val forwardX = m[8]
            val forwardY = m[9]
            val forwardZ = m[10]
            val upX = m[4]
            val upY = m[5]
            if (forwardZ == 0f && forwardX == 0f) return null
            val yaw = Math.toDegrees(atan2(forwardX, abs(forwardZ)).toDouble()).toFloat()
            val pitch = Math.toDegrees(atan2(forwardY, hypot(forwardX, forwardZ)).toDouble()).toFloat()
            val roll = Math.toDegrees(atan2(upX, abs(upY)).toDouble()).toFloat()
            return HeadAngles(yaw, pitch, roll)
        }
    }
}

/** One live camera frame's face measurements, kept so a scan can use several frames instead of one. */
data class FaceFrameSample(
    val metrics: FaceShapeMetrics,
    val angles: HeadAngles?,
    val atElapsedMillis: Long
)

/** The face shape decided from several frames, and how consistently those frames agreed on it. */
data class AggregatedFaceShape(
    val classification: FaceShapeClassification,
    /** Share of the used frames whose own measurements gave the same shape, 0..1. */
    val agreement: Float,
    val framesUsed: Int
)

/**
 * The head-tilt check and multi-frame averaging behind a Face Scan. Turning or nodding the head
 * changes the measured widths and length (a face turned 20° looks narrower; a nod shortens it),
 * which can flip a borderline shape, and a single frame can also catch a blink or mid-movement
 * jitter. So only frames where the head faces the camera are used, and their ratios are combined
 * by median, which ignores the odd outlier frame instead of averaging it in.
 *
 * Sideways tilt ([HeadAngles.rollDeg]) barely matters, since the measurements are distances
 * between points and stay the same when the face is rotated within the image; its limit only
 * rejects extreme tilts where the landmarks themselves get less reliable.
 */
object FaceAlignment {

    const val MAX_YAW_DEG = 15f
    const val MAX_PITCH_DEG = 15f
    const val MAX_ROLL_DEG = 25f

    /** Frames a scan aims to collect; it stops early once it has this many facing the camera. */
    const val TARGET_FRAMES = 10

    /** Fewest frames facing the camera a scan will still decide from. */
    const val MIN_FRAMES = 3

    /** Below this agreement between frames, the shape is borderline and its confidence is capped. */
    const val LOW_AGREEMENT = 0.6f
    const val LOW_AGREEMENT_CONFIDENCE_CAP = 0.6f

    /**
     * How far back inside the limits a head must come before the live warning clears, so a head
     * hovering right at a limit doesn't make the warning flicker on and off.
     */
    const val WARNING_CLEAR_MARGIN_DEG = 3f

    /** Weight of the newest frame when smoothing the live guide's angles (see [smooth]). */
    const val LIVE_SMOOTHING = 0.4f

    /**
     * Null angles mean the pose couldn't be read; such frames are accepted rather than blocking
     * the scan. [marginDeg] tightens every limit, for clearing a warning that's already showing.
     */
    fun isFacingCamera(angles: HeadAngles?, marginDeg: Float = 0f): Boolean =
        angles == null || (abs(angles.yawDeg) <= MAX_YAW_DEG - marginDeg &&
            abs(angles.pitchDeg) <= MAX_PITCH_DEG - marginDeg &&
            abs(angles.rollDeg) <= MAX_ROLL_DEG - marginDeg)

    /** Exponential moving average of the head angles, so single-frame jitter doesn't toggle the live warning. */
    fun smooth(previous: HeadAngles?, next: HeadAngles?): HeadAngles? {
        if (previous == null || next == null) return next
        val a = LIVE_SMOOTHING
        val b = 1f - a
        return HeadAngles(
            yawDeg = previous.yawDeg * b + next.yawDeg * a,
            pitchDeg = previous.pitchDeg * b + next.pitchDeg * a,
            rollDeg = previous.rollDeg * b + next.rollDeg * a
        )
    }

    /**
     * Whether the live guide should warn, given whether it already is: it starts warning once
     * the head passes a limit, and only stops once it's [WARNING_CLEAR_MARGIN_DEG] back inside.
     */
    fun shouldWarn(angles: HeadAngles?, currentlyWarning: Boolean): Boolean =
        if (currentlyWarning) !isFacingCamera(angles, WARNING_CLEAR_MARGIN_DEG) else !isFacingCamera(angles)

    /** Short form of [adjustmentHint] that fits on one line under the live camera guide. */
    fun liveGuideHint(angles: HeadAngles?): String? = when (worstAxis(angles)) {
        Axis.YAW -> "Face the camera straight on"
        Axis.PITCH -> "Keep your chin level"
        Axis.ROLL -> "Keep your head upright"
        null -> null
    }

    /** What to tell the user to fix, naming the angle that's furthest past its limit; null when facing the camera. */
    fun adjustmentHint(angles: HeadAngles?): String? {
        if (angles == null || isFacingCamera(angles)) return null
        return when (worstAxis(angles)) {
            Axis.YAW -> "Your head is turned to the side — face the camera straight on."
            Axis.PITCH -> "Your head is tilted up or down — keep your chin level with the camera."
            Axis.ROLL, null -> "Your head is tilted sideways — keep it upright."
        }
    }

    private enum class Axis { YAW, PITCH, ROLL }

    /** The axis furthest past its limit relative to that limit, or null when the pose is unknown. */
    private fun worstAxis(angles: HeadAngles?): Axis? {
        if (angles == null) return null
        val yawExcess = abs(angles.yawDeg) / MAX_YAW_DEG
        val pitchExcess = abs(angles.pitchDeg) / MAX_PITCH_DEG
        val rollExcess = abs(angles.rollDeg) / MAX_ROLL_DEG
        return when (maxOf(yawExcess, pitchExcess, rollExcess)) {
            yawExcess -> Axis.YAW
            pitchExcess -> Axis.PITCH
            else -> Axis.ROLL
        }
    }

    /**
     * Combines [samples] (oldest first) into one face shape, using only frames facing the camera.
     * Returns null when fewer than [MIN_FRAMES] such frames exist, so the caller can ask the user
     * to straighten up instead of guessing from a turned head.
     */
    fun aggregate(samples: List<FaceFrameSample>): AggregatedFaceShape? {
        val usable = samples.filter { isFacingCamera(it.angles) }.takeLast(TARGET_FRAMES)
        if (usable.size < MIN_FRAMES) return null
        val median = FaceShapeMetrics(
            lengthToWidth = usable.map { it.metrics.lengthToWidth }.median(),
            jawToCheek = usable.map { it.metrics.jawToCheek }.median(),
            foreheadToJaw = usable.map { it.metrics.foreheadToJaw }.median(),
            foreheadToCheek = usable.map { it.metrics.foreheadToCheek }.median()
        )
        val classification = FaceShapeClassifier.classify(median)
        val agreement = usable.count { FaceShapeClassifier.classify(it.metrics).shape == classification.shape }
            .toFloat() / usable.size
        return AggregatedFaceShape(classification, agreement, usable.size)
    }

    /** Lowers [confidence] when the frames disagreed, i.e. the face sits on the border between two shapes. */
    fun adjustForAgreement(confidence: Float, agreement: Float): Float =
        if (agreement < LOW_AGREEMENT) minOf(confidence, LOW_AGREEMENT_CONFIDENCE_CAP) else confidence

    private fun List<Float>.median(): Float {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
