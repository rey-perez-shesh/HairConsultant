package com.hairconsultant.app.data.analysis

/**
 * Bounding box of the live hair segmentation mask in normalized image coordinates (0–1),
 * same space as MediaPipe face landmarks (Y grows downward).
 *
 * Used as placement DATA for the 3D hairstyle — never drawn as the hairstyle itself.
 */
data class HairRegionBounds(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val centerX: Float,
    val width: Float,
    val height: Float,
    val coverage: Float,
    /** True when the mask is dense enough and roughly aligned with the face. */
    val reliable: Boolean
)

/**
 * Analyzes [HairMask] from [LiveHairSegmenter] into a head/hair region for AR anchoring.
 */
object HairRegionEstimator {

    /**
     * @param facePoints optional landmarks used to validate the mask sits on the head
     * @param step subsample step for speed on large masks (1 = every pixel)
     */
    fun estimate(
        mask: HairMask?,
        facePoints: List<LandmarkPoint>? = null,
        step: Int = 2
    ): HairRegionBounds? {
        if (mask == null || mask.width < 8 || mask.height < 8) return null

        val w = mask.width
        val h = mask.height
        val stride = step.coerceIn(1, 8)

        var hairPixels = 0
        var minX = w
        var maxX = -1
        var minY = h
        var maxY = -1
        var sumX = 0L

        var y = 0
        while (y < h) {
            val row = y * w
            var x = 0
            while (x < w) {
                if (mask.isHair(row + x)) {
                    hairPixels++
                    sumX += x
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += stride
            }
            y += stride
        }

        // Scale counts for subsampling (approx).
        val sampleFactor = stride * stride
        val coverage = (hairPixels * sampleFactor) / mask.pixelCount.toFloat()
        if (hairPixels < 25 || maxX < minX || maxY < minY || coverage < 0.008f) {
            return null
        }

        val left = minX / w.toFloat()
        val right = maxX / w.toFloat()
        val top = minY / h.toFloat()
        val bottom = maxY / h.toFloat()
        val width = (right - left).coerceAtLeast(0.04f)
        val height = (bottom - top).coerceAtLeast(0.04f)
        val centerX = (sumX / hairPixels.toFloat()) / w

        val reliable = isReliable(
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            width = width,
            height = height,
            coverage = coverage,
            facePoints = facePoints
        )

        return HairRegionBounds(
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            centerX = centerX.coerceIn(0f, 1f),
            width = width,
            height = height,
            coverage = coverage,
            reliable = reliable
        )
    }

    private fun isReliable(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        width: Float,
        height: Float,
        coverage: Float,
        facePoints: List<LandmarkPoint>?
    ): Boolean {
        if (coverage < 0.012f || width < 0.10f || height < 0.06f) return false
        if (width > 0.95f || height > 0.95f) return false

        if (facePoints == null || facePoints.size <= FaceShapeClassifier.CHIN) {
            return top < 0.55f
        }

        val faceTop = facePoints[FaceShapeClassifier.FOREHEAD_TOP].y
        val faceBottom = facePoints[FaceShapeClassifier.CHIN].y
        val faceLeft = facePoints[FaceShapeClassifier.LEFT_CHEEK].x
        val faceRight = facePoints[FaceShapeClassifier.RIGHT_CHEEK].x
        val faceHeight = (faceBottom - faceTop).coerceAtLeast(0.08f)
        val faceWidth = (faceRight - faceLeft).coerceAtLeast(0.08f)
        val faceCenterX = (faceLeft + faceRight) * 0.5f

        // Hair top should be at or above the forehead (allow small noise below).
        if (top > faceTop + faceHeight * 0.35f) return false
        // Hair region should overlap the face horizontally.
        val hairCenterX = (left + right) * 0.5f
        if (kotlin.math.abs(hairCenterX - faceCenterX) > faceWidth * 1.1f) return false
        // Width should be in a plausible range vs face.
        if (width < faceWidth * 0.55f || width > faceWidth * 3.2f) return false
        // Bottom of hair region should not be entirely above the forehead (empty).
        if (bottom < faceTop - faceHeight * 0.1f) return false

        return true
    }
}
