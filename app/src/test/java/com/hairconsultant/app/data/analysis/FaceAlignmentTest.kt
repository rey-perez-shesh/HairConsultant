package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.FaceShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceAlignmentTest {

    // --- Head angles from MediaPipe's transformation matrix (4x4, column-major) ---

    @Test
    fun identityMatrixFacesTheCamera() {
        val angles = HeadAngles.fromTransformationMatrix(matrix(identity()))!!
        assertEquals(0f, angles.yawDeg, 0.01f)
        assertEquals(0f, angles.pitchDeg, 0.01f)
        assertEquals(0f, angles.rollDeg, 0.01f)
    }

    @Test
    fun readsEachRotationAxisSeparately() {
        val turned = HeadAngles.fromTransformationMatrix(matrix(rotationY(20.0)))!!
        assertEquals(20f, abs(turned.yawDeg), 0.1f)
        assertEquals(0f, turned.pitchDeg, 0.1f)

        val nodded = HeadAngles.fromTransformationMatrix(matrix(rotationX(20.0)))!!
        assertEquals(20f, abs(nodded.pitchDeg), 0.1f)
        assertEquals(0f, nodded.yawDeg, 0.1f)

        val tilted = HeadAngles.fromTransformationMatrix(matrix(rotationZ(30.0)))!!
        assertEquals(30f, abs(tilted.rollDeg), 0.1f)
        assertEquals(0f, tilted.yawDeg, 0.1f)
    }

    @Test
    fun uniformScaleInTheMatrixDoesNotChangeTheAngles() {
        val scaled = rotationY(20.0).map { column -> column.map { it * 3.0 }.toDoubleArray() }
        assertEquals(20f, abs(HeadAngles.fromTransformationMatrix(matrix(scaled))!!.yawDeg), 0.1f)
    }

    // --- Facing-the-camera check ---

    @Test
    fun smallAnglesPassAndLargeOnesAreRejectedWithTheRightHint() {
        assertTrue(FaceAlignment.isFacingCamera(HeadAngles(10f, -10f, 20f)))
        assertNull(FaceAlignment.adjustmentHint(HeadAngles(10f, -10f, 20f)))

        assertFalse(FaceAlignment.isFacingCamera(HeadAngles(25f, 0f, 0f)))
        assertTrue(FaceAlignment.adjustmentHint(HeadAngles(25f, 0f, 0f))!!.contains("turned"))
        assertTrue(FaceAlignment.adjustmentHint(HeadAngles(0f, -22f, 0f))!!.contains("up or down"))
        assertTrue(FaceAlignment.adjustmentHint(HeadAngles(0f, 0f, 40f))!!.contains("sideways"))
    }

    @Test
    fun unknownPoseDoesNotBlockTheScan() {
        assertTrue(FaceAlignment.isFacingCamera(null))
        assertNull(FaceAlignment.adjustmentHint(null))
    }

    // --- Live guide warning ---

    @Test
    fun warningStartsPastTheLimitAndOnlyClearsWellInsideIt() {
        assertTrue(FaceAlignment.shouldWarn(HeadAngles(16f, 0f, 0f), currentlyWarning = false))
        // Back at 14°: inside the limit, but within the clear margin, so a shown warning stays up.
        assertTrue(FaceAlignment.shouldWarn(HeadAngles(14f, 0f, 0f), currentlyWarning = true))
        assertFalse(FaceAlignment.shouldWarn(HeadAngles(14f, 0f, 0f), currentlyWarning = false))
        assertFalse(FaceAlignment.shouldWarn(HeadAngles(11f, 0f, 0f), currentlyWarning = true))
    }

    @Test
    fun aSingleJitteryFrameDoesNotTripTheWarning() {
        var smoothed: HeadAngles? = null
        repeat(10) { smoothed = FaceAlignment.smooth(smoothed, HeadAngles(5f, 0f, 0f)) }
        smoothed = FaceAlignment.smooth(smoothed, HeadAngles(30f, 0f, 0f))
        assertFalse(FaceAlignment.shouldWarn(smoothed, currentlyWarning = false))
    }

    @Test
    fun liveHintsAreShortAndNameTheWorstAxis() {
        assertEquals("Face the camera straight on", FaceAlignment.liveGuideHint(HeadAngles(25f, 10f, 0f)))
        assertEquals("Keep your chin level", FaceAlignment.liveGuideHint(HeadAngles(5f, -20f, 0f)))
        assertEquals("Keep your head upright", FaceAlignment.liveGuideHint(HeadAngles(0f, 0f, 40f)))
    }

    // --- Multi-frame aggregation ---

    @Test
    fun medianIgnoresASingleOutlierFrame() {
        val frames = List(9) { sample(ROUND) } + sample(OVAL)
        val result = FaceAlignment.aggregate(frames)!!
        assertEquals(FaceShape.ROUND, result.classification.shape)
        assertEquals(10, result.framesUsed)
        assertEquals(0.9f, result.agreement, 0.001f)
    }

    @Test
    fun framesWithTheHeadTurnedAreLeftOut() {
        val turned = List(10) { sample(OVAL, HeadAngles(30f, 0f, 0f)) }
        val facing = List(4) { sample(ROUND) }
        val result = FaceAlignment.aggregate(turned + facing)!!
        assertEquals(FaceShape.ROUND, result.classification.shape)
        assertEquals(4, result.framesUsed)
    }

    @Test
    fun tooFewFramesFacingTheCameraGivesNoResult() {
        val frames = List(10) { sample(ROUND, HeadAngles(0f, 25f, 0f)) } + sample(ROUND) + sample(ROUND)
        assertNull(FaceAlignment.aggregate(frames))
    }

    @Test
    fun usesOnlyTheMostRecentTargetFrames() {
        val old = List(10) { sample(OVAL) }
        val recent = List(FaceAlignment.TARGET_FRAMES) { sample(ROUND) }
        val result = FaceAlignment.aggregate(old + recent)
        assertNotNull(result)
        assertEquals(FaceShape.ROUND, result!!.classification.shape)
    }

    @Test
    fun lowAgreementCapsConfidence() {
        assertEquals(0.6f, FaceAlignment.adjustForAgreement(0.85f, agreement = 0.5f), 0.001f)
        assertEquals(0.85f, FaceAlignment.adjustForAgreement(0.85f, agreement = 0.8f), 0.001f)
    }

    private fun sample(metrics: FaceShapeMetrics, angles: HeadAngles? = HeadAngles(0f, 0f, 0f)) =
        FaceFrameSample(metrics, angles, atElapsedMillis = 0L)

    /** Columns of a 3x3 rotation, padded into a 4x4 column-major matrix like MediaPipe returns. */
    private fun matrix(columns: List<DoubleArray>): FloatArray = FloatArray(16).also { m ->
        columns.forEachIndexed { c, column -> column.forEachIndexed { r, v -> m[c * 4 + r] = v.toFloat() } }
        m[15] = 1f
    }

    private fun identity() = listOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0))

    private fun rotationY(deg: Double): List<DoubleArray> {
        val r = Math.toRadians(deg)
        return listOf(doubleArrayOf(cos(r), 0.0, -sin(r)), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(sin(r), 0.0, cos(r)))
    }

    private fun rotationX(deg: Double): List<DoubleArray> {
        val r = Math.toRadians(deg)
        return listOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, cos(r), sin(r)), doubleArrayOf(0.0, -sin(r), cos(r)))
    }

    private fun rotationZ(deg: Double): List<DoubleArray> {
        val r = Math.toRadians(deg)
        return listOf(doubleArrayOf(cos(r), sin(r), 0.0), doubleArrayOf(-sin(r), cos(r), 0.0), doubleArrayOf(0.0, 0.0, 1.0))
    }

    private companion object {
        val ROUND = FaceShapeMetrics(lengthToWidth = 1.10f, jawToCheek = 0.88f, foreheadToJaw = 1.045f, foreheadToCheek = 0.92f)
        val OVAL = FaceShapeMetrics(lengthToWidth = 1.40f, jawToCheek = 0.88f, foreheadToJaw = 1.045f, foreheadToCheek = 0.92f)
    }
}
