package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.hypot
import kotlin.math.max

/**
 * Whether a generated try-on kept the person's hair as it was. Image models now and then hand
 * the photo back with the hair untouched, and often recrop or zoom it on the way, so comparing the
 * two pictures pixel for pixel reads that as a change. This compares the hair itself in the face's
 * own frame instead: each picture's hair mask is sampled on a grid fixed to the face (centred
 * between the cheeks, one unit per face width, turned with the head), so framing, zoom and
 * resolution drop out and only the haircut's shape is left.
 */
class HairChangeDetector(
    private val context: Context,
    private val landmarker: StillImageFaceLandmarker,
    private val hairSegmenter: StillImageHairSegmenter
) {

    /**
     * How much of the hair in the photo at [sourceUri] and in [generated] overlaps (0..1,
     * intersection over union) once both are lined up on the face; null when either picture has
     * no face or too little hair to compare.
     */
    fun hairOverlap(sourceUri: Uri, generated: ByteArray): Float? = runCatching {
        val before = shrunk(ScanBitmapLoader.fromUri(context, sourceUri))
        val after = BitmapFactory.decodeByteArray(generated, 0, generated.size)?.let(::shrunk)
        try {
            val a = hairInFaceFrame(before) ?: return@runCatching null
            val b = after?.let(::hairInFaceFrame) ?: return@runCatching null
            overlap(a, b)
        } finally {
            before.recycle()
            after?.recycle()
        }
    }.getOrNull()

    private fun hairInFaceFrame(bitmap: Bitmap): ByteArray? {
        val points = synchronized(landmarker) { landmarker.detect(bitmap) }?.points ?: return null
        val mask = synchronized(hairSegmenter) { hairSegmenter.segment(bitmap) } ?: return null
        return sampleInFaceFrame(points, bitmap.width, bitmap.height, mask::isHairAt)
    }

    private fun shrunk(bitmap: Bitmap): Bitmap {
        val scale = MAX_SIDE / max(bitmap.width, bitmap.height).toFloat()
        if (scale >= 1f) return bitmap
        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        bitmap.recycle()
        return small
    }

    internal companion object {
        const val OUTSIDE: Byte = 0
        const val NOT_HAIR: Byte = 1
        const val HAIR: Byte = 2

        /** Both pictures are read at no more than this size: the masks are coarser than that anyway. */
        private const val MAX_SIDE = 1024

        /** The grid around the face, in face widths from the point between the cheeks: across, then down. */
        private const val U_MIN = -1.1f
        private const val U_MAX = 1.1f
        private const val V_MIN = -1.5f
        private const val V_MAX = 1.7f
        private const val GRID_W = 44
        private const val GRID_H = 64
        private const val MIN_FACE_PX = 20f
        private const val MIN_HAIR_CELLS = 40

        /**
         * The hair mask sampled on the face-fixed grid: [HAIR], [NOT_HAIR], or [OUTSIDE] where the
         * grid runs off the picture. Null when [points] lack the cheeks, forehead or chin.
         */
        fun sampleInFaceFrame(points: List<LandmarkPoint>, width: Int, height: Int, isHair: (Float, Float) -> Boolean): ByteArray? {
            if (points.size <= FaceShapeClassifier.RIGHT_CHEEK || width <= 0 || height <= 0) return null
            val left = points[FaceShapeClassifier.LEFT_CHEEK]
            val right = points[FaceShapeClassifier.RIGHT_CHEEK]
            val top = points[FaceShapeClassifier.FOREHEAD_TOP]
            val chin = points[FaceShapeClassifier.CHIN]
            val cx = (left.x + right.x) / 2f * width
            val cy = (left.y + right.y) / 2f * height
            // Across: cheek to cheek, one face width long.
            val ax = (right.x - left.x) * width
            val ay = (right.y - left.y) * height
            val faceWidth = hypot(ax, ay)
            if (faceWidth < MIN_FACE_PX) return null
            // Down: forehead to chin, scaled to one face width (so a mirrored photo isn't turned upside down).
            val downX = (chin.x - top.x) * width
            val downY = (chin.y - top.y) * height
            val downLength = hypot(downX, downY)
            if (downLength <= 0f) return null
            val bx = downX / downLength * faceWidth
            val by = downY / downLength * faceWidth
            return ByteArray(GRID_W * GRID_H) { i ->
                val u = U_MIN + (U_MAX - U_MIN) * (i % GRID_W + 0.5f) / GRID_W
                val v = V_MIN + (V_MAX - V_MIN) * (i / GRID_W + 0.5f) / GRID_H
                val x = cx + u * ax + v * bx
                val y = cy + u * ay + v * by
                when {
                    x < 0f || y < 0f || x >= width || y >= height -> OUTSIDE
                    isHair(x / width, y / height) -> HAIR
                    else -> NOT_HAIR
                }
            }
        }

        /** Intersection over union of the hair cells both grids can see; null with too little hair to judge. */
        fun overlap(a: ByteArray, b: ByteArray): Float? {
            if (a.size != b.size) return null
            var both = 0
            var either = 0
            for (i in a.indices) {
                if (a[i] == OUTSIDE || b[i] == OUTSIDE) continue
                val inA = a[i] == HAIR
                val inB = b[i] == HAIR
                if (inA && inB) both++
                if (inA || inB) either++
            }
            return if (either < MIN_HAIR_CELLS) null else both.toFloat() / either
        }
    }
}
