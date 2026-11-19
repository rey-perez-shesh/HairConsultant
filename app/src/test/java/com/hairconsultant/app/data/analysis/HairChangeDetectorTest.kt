package com.hairconsultant.app.data.analysis

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HairChangeDetectorTest {

    /** Hair as a cap over the head, in face widths from the point between the cheeks. */
    private val middlePart = { u: Float, v: Float -> sq(u / 0.75f) + sq((v + 0.45f) / 0.75f) <= 1f && v < 0.3f && (v < -0.35f || kotlin.math.abs(u) > 0.5f) }
    private val shortCrop = { u: Float, v: Float -> sq(u / 0.55f) + sq((v + 0.7f) / 0.45f) <= 1f && v < -0.35f }

    @Test
    fun theSameHairRecroppedAndZoomedStillMatches() {
        // As in the bug: the model handed the photo back cropped tighter, the face bigger and higher.
        val original = grid(cx = 500f, cy = 560f, faceWidth = 200f, style = middlePart)
        val recropped = grid(cx = 430f, cy = 400f, faceWidth = 320f, style = middlePart)
        assertTrue(HairChangeDetector.overlap(original, recropped)!! >= 0.95f)
    }

    @Test
    fun aRealRestyleDoesNotMatch() {
        val original = grid(cx = 500f, cy = 560f, faceWidth = 200f, style = middlePart)
        val restyled = grid(cx = 430f, cy = 400f, faceWidth = 320f, style = shortCrop)
        assertTrue(HairChangeDetector.overlap(original, restyled)!! < 0.75f)
    }

    @Test
    fun tooLittleHairToJudgeGivesNoAnswer() {
        val bald = grid(cx = 500f, cy = 560f, faceWidth = 200f) { _, _ -> false }
        assertNull(HairChangeDetector.overlap(bald, bald))
    }

    /** [style]'s hair drawn around a face at ([cx], [cy]) [faceWidth] px wide on a 1000 px square, sampled in the face's frame. */
    private fun grid(cx: Float, cy: Float, faceWidth: Float, style: (Float, Float) -> Boolean): ByteArray {
        val points = MutableList(478) { LandmarkPoint(0.5f, 0.5f) }
        fun at(x: Float, y: Float) = LandmarkPoint(x / SIZE, y / SIZE)
        points[FaceShapeClassifier.LEFT_CHEEK] = at(cx - faceWidth / 2f, cy)
        points[FaceShapeClassifier.RIGHT_CHEEK] = at(cx + faceWidth / 2f, cy)
        points[FaceShapeClassifier.FOREHEAD_TOP] = at(cx, cy - 0.5f * faceWidth)
        points[FaceShapeClassifier.CHIN] = at(cx, cy + 0.7f * faceWidth)
        return HairChangeDetector.sampleInFaceFrame(points, SIZE.toInt(), SIZE.toInt()) { x, y ->
            style((x * SIZE - cx) / faceWidth, (y * SIZE - cy) / faceWidth)
        }!!
    }

    private fun sq(v: Float) = v * v

    private companion object {
        const val SIZE = 1000f
    }
}
