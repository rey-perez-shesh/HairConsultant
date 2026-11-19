package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.HairLength
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
    val reachesFrameBottom: Boolean = false,
    /** Millimetres per image pixel at the face ([HairLengthClassifier.mmPerPixel]); null when not measured. */
    val mmPerPx: Float? = null,
    /** How far the hair stands above the estimated top of the skull, in mm. */
    val topMm: Float? = null,
    /** A fringe's length from the natural hairline, in mm; null when no fringe covers the forehead. */
    val fringeMm: Float? = null,
    /** Strand length of hair hanging from the crown, in mm; null when it doesn't hang past the ear lobes. */
    val fallMm: Float? = null,
    /** The longest strand traced along the hair's flow ([HairFlow]), in mm; for hair lying flat. */
    val flowMm: Float? = null
)

/**
 * Turns a hair mask + face landmarks into Bald / Short / Medium / Long: how far hair extends
 * below the chin, in face-heights. Hair between the jaw corners below the nose is a beard, not
 * length, so it's left out of that reach.
 *
 * Given the image size it also measures the hair in millimetres, scaled by the iris (11.7 mm
 * across in almost every adult, the figure MediaPipe Iris judges distance by): how high it
 * stands above the skull, how long a fringe is, and how far hanging hair falls from the crown.
 * [HairLengthScale.measure] turns those into inches.
 */
object HairLengthClassifier {

    const val HAIR_CLASS = 1

    private const val SUBNASALE = 2
    private const val GLABELLA = 9
    private const val MID_FOREHEAD = 151
    private const val LEFT_FOREHEAD = 109
    private const val RIGHT_FOREHEAD = 338
    /** MediaPipe's iris centres; the four points after each one ring that iris. */
    private const val RIGHT_IRIS = 468
    private const val LEFT_IRIS = 473
    private const val SHORT_BELOW_CHIN = 0.22f

    /** Horizontal visible iris diameter, 11.7 +/- 0.5 mm across adults (MediaPipe Iris). */
    private const val IRIS_MM = 11.7f

    // shortcut: the constants below are anatomical averages, not fitted yet; calibrate them against
    // tape-measured scans (the raw readings reach Firestore in ScanResult.measurements) before
    // trusting the inches, and refit if the scan distance or camera changes.
    /** Cheek-to-cheek (mesh 234-454) width an iris reading must imply, and the fallback width. */
    private const val MIN_CHEEK_MM = 110f
    private const val MAX_CHEEK_MM = 170f
    private const val FALLBACK_CHEEK_MM = 140f
    /** Skull top to chin over mesh top (10) to chin: about 23 cm over 15 cm. */
    private const val SKULL_TOP_RATIO = 1.5f
    /** How far behind the chin the skull top and the mesh top sit, in face heights, for the pitch correction. */
    private const val SKULL_TOP_DEPTH = 0.58f
    private const val FOREHEAD_DEPTH = 0.1f
    /** Hair above the skull is further from the camera than the iris, so it looks smaller than it is. */
    private const val TOP_DEPTH_SCALE = 1.15f
    /** A fringe grows from a little behind the hairline. */
    private const val FRINGE_ORIGIN_MM = 25f
    /** Hanging hair's strand over its drop from the skull top: chin length works out to ~9 in, shoulder ~13 in. */
    private const val FALL_ARC = 1f
    /** Hair only counts as hanging once it's below the ear lobes, so short sideburns aren't read as length. */
    private const val HANGING_BELOW_NOSE = 0.1f
    private const val LONG_BELOW_CHIN = 1.15f

    /** Confidence cap when the hair runs off the frame: it's at least this long, but Long can't be told from Medium. */
    private const val CUT_OFF_CONFIDENCE = 0.45f

    /**
     * [isNotHair] (normalized image point) drops pixels the mask claims that are really skin or
     * clothing. Millimetres are only measured when [imageWidth] and [imageHeight] are given;
     * [pitchDeg] is the head's nod ([HeadAngles.pitchDeg]), which moves where the skull top projects.
     */
    fun classify(
        mask: HairMask,
        points: List<LandmarkPoint>,
        isNotHair: ((Float, Float) -> Boolean)? = null,
        imageWidth: Int = 0,
        imageHeight: Int = 0,
        pitchDeg: Float = 0f
    ): HairLengthClassification? {
        if (mask.width <= 0 || mask.height <= 0) return null
        if (points.size <= FaceShapeClassifier.RIGHT_JAW) return null

        val jawLeft = min(points[FaceShapeClassifier.LEFT_JAW].x, points[FaceShapeClassifier.RIGHT_JAW].x)
        val jawRight = max(points[FaceShapeClassifier.LEFT_JAW].x, points[FaceShapeClassifier.RIGHT_JAW].x)
        val noseY = points[SUBNASALE].y
        var hairPixels = 0
        var minY = -1
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
            if (rowHair >= rowThreshold) {
                if (minY < 0) minY = y
                maxY = y
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
        val cutOff = maxY >= mask.height - 1 - (mask.height * 0.02f).toInt()
        val reading = decide(coverage, (hairMaxY - chinY) / faceHeight, cutOff)
        if (imageWidth <= 0 || imageHeight <= 0) return reading
        val mmPerPx = mmPerPixel(points, imageWidth, imageHeight) ?: return reading
        // Vertical image distances (normalized) to millimetres at the face.
        val toMm = imageHeight * mmPerPx
        val skullTopY = chinY - faceHeight * skullTopRatio(pitchDeg)
        val hangs = hairMaxY > noseY + HANGING_BELOW_NOSE * faceHeight
        return reading.copy(
            mmPerPx = mmPerPx,
            topMm = ((skullTopY - minY / mask.height.toFloat()) * toMm * TOP_DEPTH_SCALE).coerceAtLeast(0f),
            fringeMm = fringeEndY(mask, points, isNotHair)?.let { end ->
                val hairlineY = naturalHairlineY(points)
                if (end > hairlineY) (end - hairlineY) * toMm + FRINGE_ORIGIN_MM else null
            },
            fallMm = if (hangs) (hairMaxY - skullTopY) * toMm * FALL_ARC else null
        )
    }

    /**
     * Millimetres per image pixel at the face, from the iris's width (the larger of its two
     * diameters, so an eyelid covering part of it doesn't shrink the reading). Falls back to an
     * average face width when there are no iris points (a 468-point mesh) or the iris reading
     * implies an impossible face.
     */
    internal fun mmPerPixel(points: List<LandmarkPoint>, width: Int, height: Int): Float? {
        if (points.size <= FaceShapeClassifier.RIGHT_CHEEK) return null
        fun px(a: Int, b: Int) = hypot((points[a].x - points[b].x) * width, (points[a].y - points[b].y) * height)
        val cheekPx = px(FaceShapeClassifier.LEFT_CHEEK, FaceShapeClassifier.RIGHT_CHEEK)
        if (cheekPx <= 0f) return null
        val irisPx = if (points.size > LEFT_IRIS + 4) {
            (max(px(RIGHT_IRIS + 1, RIGHT_IRIS + 3), px(RIGHT_IRIS + 2, RIGHT_IRIS + 4)) +
                max(px(LEFT_IRIS + 1, LEFT_IRIS + 3), px(LEFT_IRIS + 2, LEFT_IRIS + 4))) / 2f
        } else {
            0f
        }
        val fromIris = (IRIS_MM / irisPx).takeIf { irisPx > 0f && cheekPx * it in MIN_CHEEK_MM..MAX_CHEEK_MM }
        return fromIris ?: (FALLBACK_CHEEK_MM / cheekPx)
    }

    /**
     * How far above the chin the skull's top projects, in face heights (mesh top to chin). The
     * skull top sits well behind the face, so a raised chin (positive pitch) drops it in the image.
     */
    private fun skullTopRatio(pitchDeg: Float): Float {
        val t = Math.toRadians(pitchDeg.toDouble())
        return ((SKULL_TOP_RATIO * cos(t) - SKULL_TOP_DEPTH * sin(t)) / (cos(t) - FOREHEAD_DEPTH * sin(t))).toFloat()
    }

    /** Facial thirds are about equal, so the natural hairline sits one nose-to-chin above the brow. */
    private fun naturalHairlineY(points: List<LandmarkPoint>): Float =
        points[GLABELLA].y - (points[FaceShapeClassifier.CHIN].y - points[SUBNASALE].y)

    /**
     * The bottom edge of the hair across the forehead when hair covers the mid-forehead, else null.
     * Rows count when at least half the forehead's width is hair, so a stray strand doesn't.
     */
    private fun fringeEndY(mask: HairMask, points: List<LandmarkPoint>, isNotHair: ((Float, Float) -> Boolean)?): Float? {
        if (points.size <= RIGHT_FOREHEAD) return null
        fun hairAt(x: Float, y: Float) = mask.isHairAt(x, y) && isNotHair?.invoke(x, y) != true
        val mid = points[MID_FOREHEAD]
        if (!hairAt(mid.x, mid.y)) return null
        val left = min(points[LEFT_FOREHEAD].x, points[RIGHT_FOREHEAD].x)
        val right = max(points[LEFT_FOREHEAD].x, points[RIGHT_FOREHEAD].x)
        val x0 = (left * mask.width).toInt().coerceIn(0, mask.width - 1)
        val x1 = (right * mask.width).toInt().coerceIn(x0, mask.width - 1)
        val y0 = (mid.y * mask.height).toInt().coerceIn(0, mask.height - 1)
        val y1 = (points[GLABELLA].y * mask.height).toInt().coerceIn(y0, mask.height - 1)
        var end: Int? = null
        for (y in y0..y1) {
            val ny = (y + 0.5f) / mask.height
            val hair = (x0..x1).count { x -> hairAt((x + 0.5f) / mask.width, ny) }
            if (hair * 2 >= x1 - x0 + 1) end = y
        }
        return end?.let { (it + 1f) / mask.height }
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
        ).copy(
            mmPerPx = hairy.map { it.mmPerPx }.medianOfMost(),
            topMm = hairy.map { it.topMm }.medianOfMost(),
            fringeMm = hairy.map { it.fringeMm }.medianOfMost(),
            fallMm = hairy.map { it.fallMm }.medianOfMost(),
            flowMm = hairy.map { it.flowMm }.medianOfMost()
        )
    }

    /** The median of a reading most frames had; null when most didn't, so one stray frame can't add a reading. */
    private fun List<Float?>.medianOfMost(): Float? =
        filterNotNull().takeIf { it.isNotEmpty() && it.size * 2 >= size }?.median()

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
