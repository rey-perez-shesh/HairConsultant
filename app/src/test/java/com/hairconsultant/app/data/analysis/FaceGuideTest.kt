package com.hairconsultant.app.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGuideTest {

    // 1000x2000 view showing a 1000x2000 image (scale 1): the oval is centered at (500, ~910).

    @Test
    fun faceInsideTheOvalHasNoHint() {
        assertNull(hintFor(face(centerX = 0.5f, centerY = 0.455f, halfSize = 0.2f)))
    }

    @Test
    fun farAwayFaceIsToldToMoveCloser() {
        assertEquals(
            "Move closer so your face fills the oval",
            hintFor(face(centerX = 0.5f, centerY = 0.455f, halfSize = 0.1f))
        )
    }

    @Test
    fun photoWithACenteredLargeFaceIsAccepted() {
        assertNull(FaceGuide.photoRejection(face(0.5f, 0.4f, 0.2f), 1000))
    }

    @Test
    fun photoWithATinyFaceIsRejected() {
        assertTrue(FaceGuide.photoRejection(face(0.5f, 0.5f, 0.05f), 1000)!!.startsWith("Your face is too small"))
    }

    @Test
    fun photoWithAFaceAtTheEdgeIsRejected() {
        assertTrue(FaceGuide.photoRejection(face(0.1f, 0.5f, 0.08f), 1000)!!.startsWith("Your face is too close to the edge"))
    }

    @Test
    fun photoWithACutOffFaceIsRejected() {
        assertTrue(FaceGuide.photoRejection(face(0.1f, 0.5f, 0.2f), 1000)!!.startsWith("Part of your face is cut off"))
    }

    @Test
    fun offCenterFaceIsToldToCenter() {
        assertEquals(
            "Center your face inside the oval",
            hintFor(face(centerX = 0.5f, centerY = 0.8f, halfSize = 0.1f))
        )
    }

    @Test
    fun faceBiggerThanTheOvalIsToldToMoveBack() {
        assertEquals(
            "Move back so your face fits inside the oval",
            hintFor(face(centerX = 0.5f, centerY = 0.455f, halfSize = 0.49f))
        )
    }

    @Test
    fun unmeasuredViewDoesNotBlockScanning() {
        assertNull(FaceGuide.positionHint(face(0.5f, 0.9f, 0.1f), 1000, 2000, 0, 0))
    }

    private fun hintFor(points: List<LandmarkPoint>) = FaceGuide.positionHint(points, 1000, 2000, 1000, 2000)

    /** Four points of a square face; positions are normalized to the image. */
    private fun face(centerX: Float, centerY: Float, halfSize: Float) = listOf(
        LandmarkPoint(centerX - halfSize, centerY),
        LandmarkPoint(centerX + halfSize, centerY),
        LandmarkPoint(centerX, centerY - halfSize / 2),
        LandmarkPoint(centerX, centerY + halfSize / 2)
    )
}
