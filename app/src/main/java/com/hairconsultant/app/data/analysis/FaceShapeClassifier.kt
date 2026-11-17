package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.FaceShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

data class LandmarkPoint(val x: Float, val y: Float)

data class FaceShapeMetrics(
    val lengthToWidth: Float,
    val jawToCheek: Float,
    val foreheadToJaw: Float,
    val foreheadToCheek: Float,
    /** Angle at the jaw corners between the cheekbone and the chin, degrees: smaller is a squarer jaw. */
    val jawAngleDeg: Float = FaceShapeClassifier.CANONICAL_JAW_ANGLE,
    /** Chin width over jaw width: smaller is a more pointed chin. */
    val chinToJaw: Float = FaceShapeClassifier.CANONICAL_CHIN_TO_JAW
)

data class FaceShapeClassification(
    val shape: FaceShape,
    val confidence: Float,
    val metrics: FaceShapeMetrics
)

/**
 * Measures MediaPipe (478) / ML Kit (468) face-mesh points and decides the [FaceShape].
 *
 * The four ratios come from five zones: temple width, cheekbone width (the reference width),
 * jaw width, the chin tip and the mesh's top point (landmark 10, which sits partway up the
 * forehead; [Hairline] measures the rest). Indices follow the community-standard conventions
 * shared by both meshes: 10 forehead-top, 152 chin, 234/454 cheekbone edges, 127/356 temples,
 * 172/397 jaw corners.
 *
 * Oval/Round/Square/Heart are the trained model's call ([FaceShapeTfliteClassifier]), learned
 * from ~5,000 labelled faces. Diamond has no training data, so it's decided here from how far a
 * face's ratios sit from that same population's average ([POPULATION_MEAN], [POPULATION_SD]).
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
    const val UPPER_LIP_INNER = 13
    const val LOWER_LIP_INNER = 14
    const val LEFT_CHIN = 149
    const val RIGHT_CHIN = 378

    /**
     * Jaw angle and chin width on MediaPipe's canonical face. They have no trained thresholds
     * yet: the shape model only uses them once retrained on them (ml/train_classifiers.ipynb).
     */
    const val CANONICAL_JAW_ANGLE = 132.2f
    const val CANONICAL_CHIN_TO_JAW = 0.540f

    private val required = intArrayOf(
        FOREHEAD_TOP, CHIN, LEFT_CHEEK, RIGHT_CHEEK, LEFT_JAW, RIGHT_JAW, LEFT_TEMPLE, RIGHT_TEMPLE,
        LEFT_CHIN, RIGHT_CHIN
    )

    /**
     * Mean and standard deviation of each ratio over the trained model's ~5,000 faces, read out
     * of face_shape_classifier.tflite's normalization layer. Faces differ far less than the old
     * hand-set thresholds assumed (one standard deviation of jaw width is ~2% of the cheek
     * width), which is why those thresholds called almost every face Heart.
     */
    val POPULATION_MEAN = FaceShapeMetrics(lengthToWidth = 1.193f, jawToCheek = 0.798f, foreheadToJaw = 1.266f, foreheadToCheek = 1.009f)
    val POPULATION_SD = FaceShapeMetrics(lengthToWidth = 0.059f, jawToCheek = 0.023f, foreheadToJaw = 0.045f, foreheadToCheek = 0.008f)

    /** Diamond: temples and jaw both this many standard deviations narrower than average, relative to the cheekbones. */
    private const val DIAMOND_Z = -1.5f

    /** A face last scanned as Diamond stays Diamond until its ratios move this much further back toward average. */
    private const val DIAMOND_KEEP_SLACK = 0.3f

    /**
     * How much less likely than the best shape the previous scan's shape may be and still be
     * kept. A face between two shapes otherwise flips on every scan; in a simulation over the
     * training population this cut label changes between scans roughly threefold, and the kept
     * label matched the noise-free answer at least as often.
     */
    const val KEEP_PREVIOUS_MARGIN = 0.15f

    /** Confidence of the population-based fallback used only if the trained model fails to load. */
    private const val FALLBACK_CONFIDENCE = 0.5f

    /** Lips parted by more than this share of the face length: an open mouth drops the chin and lengthens the face. */
    private const val MOUTH_OPEN_RATIO = 0.04f

    /**
     * Full face length (measured hairline to chin) over the mesh's length (landmark 10 to chin)
     * on MediaPipe's canonical face, using the equal-thirds proportion. A measured hairline is
     * read relative to this, so an average forehead leaves the length ratio unchanged.
     */
    const val AVERAGE_FULL_TO_MESH_LENGTH = 1.223f

    /** How far the measured forehead may move the length ratio, at most: hair over the forehead moves between sessions. */
    private const val HAIRLINE_MAX_EFFECT = 0.04f

    /** The four width/length ratios the shape is decided from, or null if the face is too small to measure. */
    fun measure(
        points: List<LandmarkPoint>,
        imageWidth: Int,
        imageHeight: Int,
        /** This frame's head pose, to undo how a slight turn or nod changes the face's proportions on screen. */
        angles: HeadAngles? = null
    ): FaceShapeMetrics? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (required.any { it >= points.size }) return null

        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val length = dist(points[FOREHEAD_TOP], points[CHIN], w, h)
        val cheek = dist(points[LEFT_CHEEK], points[RIGHT_CHEEK], w, h)
        val jaw = dist(points[LEFT_JAW], points[RIGHT_JAW], w, h)
        val temple = dist(points[LEFT_TEMPLE], points[RIGHT_TEMPLE], w, h)
        if (length < 8f || cheek < 8f || jaw < 8f || temple < 8f) return null

        // Turning the head narrows the face on screen by cos(yaw), nodding shortens it by
        // cos(pitch); widths shrink together, so only the length-to-width ratio needs undoing.
        val poseCorrection = angles?.let { cosDeg(it.yawDeg) / cosDeg(it.pitchDeg) } ?: 1f
        val jawAngle = (angleAt(points[LEFT_JAW], points[LEFT_CHEEK], points[CHIN], w, h) +
            angleAt(points[RIGHT_JAW], points[RIGHT_CHEEK], points[CHIN], w, h)) / 2f
        return FaceShapeMetrics(
            lengthToWidth = length / cheek * poseCorrection,
            jawToCheek = jaw / cheek,
            foreheadToJaw = temple / jaw,
            foreheadToCheek = temple / cheek,
            jawAngleDeg = jawAngle,
            chinToJaw = dist(points[LEFT_CHIN], points[RIGHT_CHIN], w, h) / jaw
        )
    }

    /** Angle in degrees at [corner] between the lines to [a] and [b]. */
    private fun angleAt(corner: LandmarkPoint, a: LandmarkPoint, b: LandmarkPoint, w: Float, h: Float): Float {
        val ax = (a.x - corner.x) * w
        val ay = (a.y - corner.y) * h
        val bx = (b.x - corner.x) * w
        val by = (b.y - corner.y) * h
        val lengths = (hypot(ax, ay) * hypot(bx, by)).coerceAtLeast(1e-6f)
        val cos = ((ax * bx + ay * by) / lengths).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos).toDouble()).toFloat()
    }

    /** Whether the lips are parted enough to change the face's measured length and jaw. */
    fun isMouthOpen(points: List<LandmarkPoint>, imageWidth: Int, imageHeight: Int): Boolean {
        if (points.size <= CHIN) return false
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val length = dist(points[FOREHEAD_TOP], points[CHIN], w, h)
        if (length <= 0f) return false
        return dist(points[UPPER_LIP_INNER], points[LOWER_LIP_INNER], w, h) / length > MOUTH_OPEN_RATIO
    }

    /**
     * Folds a measured forehead into the length ratio. [fullToMeshLength] is hairline-to-chin over
     * landmark-10-to-chin; a forehead taller than average lengthens the face, a shorter one
     * shortens it, by at most [HAIRLINE_MAX_EFFECT].
     */
    fun withHairline(metrics: FaceShapeMetrics, fullToMeshLength: Float): FaceShapeMetrics {
        val relative = fullToMeshLength / AVERAGE_FULL_TO_MESH_LENGTH - 1f
        val effect = (relative * 0.5f).coerceIn(-HAIRLINE_MAX_EFFECT, HAIRLINE_MAX_EFFECT)
        return metrics.copy(lengthToWidth = metrics.lengthToWidth * (1f + effect))
    }

    /**
     * The face shape: Diamond when the population rule says so, otherwise the trained model's most
     * likely class, unless [previous] (the user's last confirmed shape) is within
     * [KEEP_PREVIOUS_MARGIN] of it. [probabilities] null means the model isn't available.
     */
    fun decide(
        metrics: FaceShapeMetrics,
        probabilities: Map<FaceShape, Float>?,
        previous: FaceShape? = null
    ): FaceShapeClassification {
        val diamondLimit = if (previous == FaceShape.DIAMOND) DIAMOND_Z + DIAMOND_KEEP_SLACK else DIAMOND_Z
        if (diamondDepth(metrics, diamondLimit) >= 0f) {
            val confidence = (0.55f + 0.1f * diamondDepth(metrics, DIAMOND_Z)).coerceIn(0.55f, 0.8f)
            return FaceShapeClassification(FaceShape.DIAMOND, confidence, metrics)
        }
        if (probabilities.isNullOrEmpty()) {
            return FaceShapeClassification(fallbackShape(metrics), FALLBACK_CONFIDENCE, metrics)
        }
        val best = probabilities.maxBy { it.value }
        val kept = previous?.takeIf { shape ->
            probabilities[shape]?.let { it >= best.value - KEEP_PREVIOUS_MARGIN } == true
        }
        val shape = kept ?: best.key
        return FaceShapeClassification(shape, probabilities.getValue(shape), metrics)
    }

    /** How many standard deviations past [limit] the less extreme of temples and jaw is; negative means not Diamond. */
    private fun diamondDepth(m: FaceShapeMetrics, limit: Float): Float =
        limit - maxOf(z(m.foreheadToCheek, POPULATION_MEAN.foreheadToCheek, POPULATION_SD.foreheadToCheek),
            z(m.jawToCheek, POPULATION_MEAN.jawToCheek, POPULATION_SD.jawToCheek))

    /** Only if the model can't load: the shape whose defining ratio sits furthest from average. */
    private fun fallbackShape(m: FaceShapeMetrics): FaceShape {
        val zJaw = z(m.jawToCheek, POPULATION_MEAN.jawToCheek, POPULATION_SD.jawToCheek)
        val zLength = z(m.lengthToWidth, POPULATION_MEAN.lengthToWidth, POPULATION_SD.lengthToWidth)
        return when {
            zJaw >= 1f && abs(zJaw) >= abs(zLength) -> FaceShape.SQUARE
            zJaw <= -1f && abs(zJaw) >= abs(zLength) -> FaceShape.HEART
            zLength <= -1f -> FaceShape.ROUND
            else -> FaceShape.OVAL
        }
    }

    private fun z(value: Float, mean: Float, sd: Float): Float = (value - mean) / sd

    private fun cosDeg(degrees: Float): Float = cos(Math.toRadians(degrees.toDouble())).toFloat()

    private fun dist(a: LandmarkPoint, b: LandmarkPoint, width: Float, height: Float): Float =
        hypot((a.x - b.x) * width, (a.y - b.y) * height)
}
