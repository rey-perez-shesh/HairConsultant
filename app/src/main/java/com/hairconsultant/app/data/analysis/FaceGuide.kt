package com.hairconsultant.app.data.analysis

import kotlin.math.max

/**
 * The dashed oval on the live camera guide. The overlay draws [ovalBounds]; [FaceLandmarkStore]
 * uses [positionHint] so Scan stays disabled until the whole face mesh sits inside that oval.
 */
object FaceGuide {

    private const val WIDTH_FRACTION = 0.72f
    private const val HEIGHT_TO_WIDTH = 1.28f

    /** Slack past the drawn line, so a jaw just touching the dashes still counts as inside. */
    private const val TOLERANCE = 1.05f

    /** The oval as left, top, right, bottom in view pixels. */
    fun ovalBounds(viewWidth: Int, viewHeight: Int): FloatArray {
        val width = viewWidth * WIDTH_FRACTION
        val height = width * HEIGHT_TO_WIDTH
        val top = (viewHeight - height) / 2.4f
        return floatArrayOf((viewWidth - width) / 2f, top, (viewWidth + width) / 2f, top + height)
    }

    /**
     * What to tell the user when any landmark falls outside the oval, or null when the face fits.
     * [points] are normalized to the camera image, mapped to the view with the same center-crop
     * the overlay draws the mesh with. Null too while the view hasn't been measured yet.
     */
    fun positionHint(
        points: List<LandmarkPoint>,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): String? {
        if (points.isEmpty() || viewWidth <= 0 || viewHeight <= 0) return null
        val (left, top, right, bottom) = ovalBounds(viewWidth, viewHeight)
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val radiusX = (right - left) / 2f * TOLERANCE
        val radiusY = (bottom - top) / 2f * TOLERANCE

        val scale = max(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        val offsetX = (viewWidth - imageWidth * scale) / 2f
        val offsetY = (viewHeight - imageHeight * scale) / 2f
        val xs = points.map { it.x * imageWidth * scale + offsetX }
        val ys = points.map { it.y * imageHeight * scale + offsetY }

        val outside = xs.indices.any { i ->
            val dx = (xs[i] - centerX) / radiusX
            val dy = (ys[i] - centerY) / radiusY
            dx * dx + dy * dy > 1f
        }
        val faceWidth = xs.max() - xs.min()
        if (!outside) {
            return if (faceWidth < viewWidth * MIN_FACE_WIDTH_FRACTION) "Move closer so your face fills the oval" else null
        }
        val tooBig = faceWidth > 2 * radiusX || ys.max() - ys.min() > 2 * radiusY
        return if (tooBig) "Move back so your face fits inside the oval" else "Center your face inside the oval"
    }

    /**
     * Why an uploaded photo's face can't be measured from where it sits in the frame, or null when
     * it's fine: the photo counterpart of [positionHint]. [points] are normalized to the image.
     */
    fun photoRejection(points: List<LandmarkPoint>, imageWidth: Int): String? {
        if (points.isEmpty()) return null
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val problem = when {
            minX < 0f || maxX > 1f || minY < 0f || maxY > 1f -> "Part of your face is cut off at the edge of that photo"
            (maxX - minX) * imageWidth < MIN_PHOTO_FACE_WIDTH_PX -> "Your face is too small in that photo"
            (minX + maxX) / 2f !in PHOTO_CENTER_RANGE || (minY + maxY) / 2f !in PHOTO_CENTER_RANGE ->
                "Your face is too close to the edge of that photo"
            else -> return null
        }
        return "$problem, so I can't measure your face shape reliably. " +
            "Please upload one with your whole face near the middle, taken from closer up."
    }

    /** A face narrower than this share of the view is too far away to place landmarks precisely. */
    private const val MIN_FACE_WIDTH_FRACTION = 0.35f

    /** About the face width in pixels that [MIN_FACE_WIDTH_FRACTION] works out to on a live 480px-wide frame. */
    private const val MIN_PHOTO_FACE_WIDTH_PX = 120f

    /** Where a photo's face center may sit; outside it, lens stretch near the edges skews the widths. */
    private val PHOTO_CENTER_RANGE = 0.15f..0.85f
}
