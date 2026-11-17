package com.hairconsultant.app.data.analysis

import kotlin.math.hypot

/**
 * The real hairline. MediaPipe's face mesh stops partway up the forehead (landmark 10 sits a
 * bit under halfway from the brow to the hairline on its canonical face), so this walks on up
 * from there through a mask until the forehead skin ends: the multiclass segmenter's face-skin
 * class during a scan ([FaceParsing]), or "not hair" on the live hair mask for the camera guide.
 */
object Hairline {

    private const val GLABELLA = 9
    private const val SUBNASALE = 2
    private const val LEFT_FOREHEAD = 109
    private const val RIGHT_FOREHEAD = 338

    /** How far above landmark 10 a walk may go, as a share of the mesh's face length. */
    private const val MAX_WALK = 0.5f

    /** Non-forehead samples in a row that end the forehead, as a share of the face length, so a stray strand doesn't. */
    private const val END_RUN = 0.02f

    /**
     * Plausible brow-to-hairline height relative to nose-to-chin (the facial thirds are about
     * equal). Outside it the "hairline" is really a fringe lying on the forehead or the top of a
     * bald head, and is ignored.
     */
    private const val MIN_UPPER_THIRD = 0.6f
    private const val MAX_UPPER_THIRD = 1.5f

    /**
     * Where the forehead ends above the face, as a normalized image point, or null when that
     * can't be told: hair over the forehead, a bald or shaved head, or the head leaving the frame.
     *
     * Walks three parallel lines (from the mesh's top point and from either side of it) along the
     * face's own up direction, so a tilted head is followed along its forehead, and takes their
     * median. [isForehead] says whether a normalized image point is still forehead skin;
     * [resolution] is the width of the mask behind it, which sets the step.
     */
    fun find(
        points: List<LandmarkPoint>,
        imageWidth: Int,
        imageHeight: Int,
        resolution: Int,
        isForehead: (Float, Float) -> Boolean
    ): LandmarkPoint? {
        if (points.size <= RIGHT_FOREHEAD || imageWidth <= 0 || imageHeight <= 0 || resolution <= 0) return null
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val chin = points[FaceShapeClassifier.CHIN]
        val upX = (top.x - chin.x) * w
        val upY = (top.y - chin.y) * h
        val length = hypot(upX, upY)
        if (length < 8f) return null
        val dx = upX / length
        val dy = upY / length
        val step = (w / resolution).coerceAtLeast(1f)
        val endRun = (length * END_RUN / step).toInt().coerceAtLeast(2)

        val heights = listOf(FaceShapeClassifier.FOREHEAD_TOP, LEFT_FOREHEAD, RIGHT_FOREHEAD).mapNotNull { index ->
            val sx = points[index].x * w
            val sy = points[index].y * h
            // This line's start, measured along the up direction from landmark 10.
            val offset = (sx - top.x * w) * dx + (sy - top.y * h) * dy
            walk(sx, sy, dx, dy, step, length * MAX_WALK, endRun, w, h, isForehead)?.let { offset + it }
        }.sorted()
        if (heights.size < 2) return null
        val height = if (heights.size % 2 == 1) heights[heights.size / 2] else (heights[0] + heights[1]) / 2f
        if (height <= 0f) return null

        val glabella = points[GLABELLA]
        val browToTop = (top.x - glabella.x) * w * dx + (top.y - glabella.y) * h * dy
        val lowerThird = hypot((points[SUBNASALE].x - chin.x) * w, (points[SUBNASALE].y - chin.y) * h)
        if (lowerThird <= 0f) return null
        val upperThird = (browToTop + height) / lowerThird
        if (upperThird !in MIN_UPPER_THIRD..MAX_UPPER_THIRD) return null
        return LandmarkPoint(top.x + dx * height / w, top.y + dy * height / h)
    }

    /**
     * Forehead width over cheekbone width, measured across the face halfway between the mesh's
     * top point and [hairline]: how far forehead skin reaches to either side. Null when either side
     * never ends inside the frame. Hair falling over the temples narrows it, so it's recorded for
     * calibration rather than used to decide the shape yet.
     */
    fun foreheadWidth(
        points: List<LandmarkPoint>,
        hairline: LandmarkPoint,
        imageWidth: Int,
        imageHeight: Int,
        resolution: Int,
        isForehead: (Float, Float) -> Boolean
    ): Float? {
        if (points.size <= FaceShapeClassifier.RIGHT_CHEEK || resolution <= 0) return null
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val upX = (hairline.x - top.x) * w
        val upY = (hairline.y - top.y) * h
        val up = hypot(upX, upY)
        if (up <= 0f) return null
        val left = points[FaceShapeClassifier.LEFT_CHEEK]
        val right = points[FaceShapeClassifier.RIGHT_CHEEK]
        val cheek = hypot((right.x - left.x) * w, (right.y - left.y) * h)
        if (cheek < 8f) return null
        val acrossX = -upY / up
        val acrossY = upX / up
        val cx = (top.x + hairline.x) / 2f * w
        val cy = (top.y + hairline.y) / 2f * h
        val step = (w / resolution).coerceAtLeast(1f)
        val endRun = (cheek * END_RUN / step).toInt().coerceAtLeast(2)
        val one = walk(cx, cy, acrossX, acrossY, step, cheek, endRun, w, h, isForehead) ?: return null
        val other = walk(cx, cy, -acrossX, -acrossY, step, cheek, endRun, w, h, isForehead) ?: return null
        return (one + other) / cheek
    }

    /** Hairline-to-chin over landmark-10-to-chin: how much longer the whole face is than the mesh. */
    fun fullToMeshLength(points: List<LandmarkPoint>, hairline: LandmarkPoint, imageWidth: Int, imageHeight: Int): Float {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val chin = points[FaceShapeClassifier.CHIN]
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val mesh = hypot((top.x - chin.x) * w, (top.y - chin.y) * h)
        return if (mesh <= 0f) 1f else hypot((hairline.x - chin.x) * w, (hairline.y - chin.y) * h) / mesh
    }

    /** Distance walked before the forehead ends, or null if it never ends inside the frame and [maxDistance]. */
    private fun walk(
        startX: Float,
        startY: Float,
        dx: Float,
        dy: Float,
        step: Float,
        maxDistance: Float,
        endRun: Int,
        w: Float,
        h: Float,
        isForehead: (Float, Float) -> Boolean
    ): Float? {
        var t = 0f
        var run = 0
        var runStart = 0f
        while (t <= maxDistance) {
            val x = startX + dx * t
            val y = startY + dy * t
            if (x < 0f || y < 0f || x >= w || y >= h) return null
            if (isForehead(x / w, y / h)) {
                run = 0
            } else {
                if (run == 0) runStart = t
                run++
                if (run >= endRun) return runStart
            }
            t += step
        }
        return null
    }
}
