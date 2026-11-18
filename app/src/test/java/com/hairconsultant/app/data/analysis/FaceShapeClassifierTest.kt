package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.data.analysis.FaceShapeClassifier.POPULATION_MEAN
import com.hairconsultant.app.data.analysis.FaceShapeClassifier.POPULATION_SD
import com.hairconsultant.app.domain.model.FaceShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceShapeClassifierTest {

    // --- Deciding the shape ---

    @Test
    fun theModelsMostLikelyShapeWinsWithoutAPreviousScan() {
        val result = FaceShapeClassifier.decide(POPULATION_MEAN, probabilities(oval = 0.5f, heart = 0.3f))
        assertEquals(FaceShape.OVAL, result.shape)
        assertEquals(0.5f, result.confidence, 0.001f)
    }

    @Test
    fun aNearTieKeepsThePreviousScansShape() {
        val result = FaceShapeClassifier.decide(POPULATION_MEAN, probabilities(oval = 0.42f, heart = 0.35f), FaceShape.HEART)
        assertEquals(FaceShape.HEART, result.shape)
        assertEquals(0.35f, result.confidence, 0.001f)
    }

    @Test
    fun aClearlyDifferentScanReplacesThePreviousShape() {
        val result = FaceShapeClassifier.decide(POPULATION_MEAN, probabilities(oval = 0.6f, heart = 0.2f), FaceShape.HEART)
        assertEquals(FaceShape.OVAL, result.shape)
    }

    @Test
    fun theAverageFaceIsNotDiamondButNarrowTemplesAndJawAre() {
        val probabilities = probabilities(oval = 0.6f, heart = 0.2f)
        assertEquals(FaceShape.OVAL, FaceShapeClassifier.decide(POPULATION_MEAN, probabilities).shape)
        assertEquals(FaceShape.DIAMOND, FaceShapeClassifier.decide(diamondish(z = -1.8f), probabilities).shape)
        // Only one of the two narrow is not Diamond.
        val narrowJawOnly = POPULATION_MEAN.copy(jawToCheek = POPULATION_MEAN.jawToCheek - 2f * POPULATION_SD.jawToCheek)
        assertEquals(FaceShape.OVAL, FaceShapeClassifier.decide(narrowJawOnly, probabilities).shape)
    }

    @Test
    fun aDiamondFaceStaysDiamondUntilItMovesClearlyBack() {
        val probabilities = probabilities(oval = 0.6f, heart = 0.2f)
        val borderline = diamondish(z = -1.3f)
        assertEquals(FaceShape.OVAL, FaceShapeClassifier.decide(borderline, probabilities).shape)
        assertEquals(FaceShape.DIAMOND, FaceShapeClassifier.decide(borderline, probabilities, FaceShape.DIAMOND).shape)
    }

    @Test
    fun withoutTheModelTheFallbackReadsTheMostUnusualRatio() {
        val wideJaw = POPULATION_MEAN.copy(jawToCheek = POPULATION_MEAN.jawToCheek + 1.5f * POPULATION_SD.jawToCheek)
        assertEquals(FaceShape.SQUARE, FaceShapeClassifier.decide(wideJaw, probabilities = null).shape)
        assertEquals(FaceShape.OVAL, FaceShapeClassifier.decide(POPULATION_MEAN, probabilities = null).shape)
    }

    // --- Measuring ---

    @Test
    fun aTurnedHeadIsMeasuredAsIfFacingTheCamera() {
        val points = face()
        val straight = FaceShapeClassifier.measure(points, 1000, 1000)!!
        val turned = FaceShapeClassifier.measure(points, 1000, 1000, HeadAngles(yawDeg = 12f, pitchDeg = 0f, rollDeg = 0f))!!
        assertEquals(straight.lengthToWidth * cos(Math.toRadians(12.0)).toFloat(), turned.lengthToWidth, 0.0001f)
        assertEquals(straight.jawToCheek, turned.jawToCheek, 0.0001f)
    }

    @Test
    fun aNodMeasuresTheSameOnceItsPerspectiveIsUndone() {
        val level = FaceShapeClassifier.measure(rendered(pitchDeg = 0f), 1000, 1000, HeadAngles(0f, 0f, 0f))!!
        for (pitch in listOf(-6f, 6f)) {
            val points = rendered(pitch)
            val uncorrected = FaceShapeClassifier.measure(points, 1000, 1000)!!
            val corrected = FaceShapeClassifier.measure(points, 1000, 1000, HeadAngles(0f, pitch, 0f), focalToLongSide = 0.75f)!!
            // Uncorrected, a 6° nod moves the jaw by over half the spread between people.
            assertTrue(abs(uncorrected.jawToCheek - level.jawToCheek) > 0.5f * POPULATION_SD.jawToCheek)
            assertEquals(level.jawToCheek, corrected.jawToCheek, 0.1f * POPULATION_SD.jawToCheek)
            assertEquals(level.foreheadToJaw, corrected.foreheadToJaw, 0.1f * POPULATION_SD.foreheadToJaw)
            assertEquals(level.lengthToWidth, corrected.lengthToWidth, 0.2f * POPULATION_SD.lengthToWidth)
        }
    }

    @Test
    fun aWideLowJawHasASmallerJawAngleAndAPointedChinASmallerChinWidth() {
        val face = face()
        val square = face.toMutableList().apply {
            this[FaceShapeClassifier.LEFT_JAW] = LandmarkPoint(0.26f, 0.74f)
            this[FaceShapeClassifier.RIGHT_JAW] = LandmarkPoint(0.74f, 0.74f)
        }
        val base = FaceShapeClassifier.measure(face, 1000, 1000)!!
        assertTrue(FaceShapeClassifier.measure(square, 1000, 1000)!!.jawAngleDeg < base.jawAngleDeg)

        val wideChin = face.toMutableList().apply {
            this[FaceShapeClassifier.LEFT_CHIN] = LandmarkPoint(0.40f, 0.78f)
            this[FaceShapeClassifier.RIGHT_CHIN] = LandmarkPoint(0.60f, 0.78f)
        }
        assertEquals(0.2f / 0.4f, FaceShapeClassifier.measure(wideChin, 1000, 1000)!!.chinToJaw, 0.001f)
    }

    @Test
    fun anOpenMouthIsSpotted() {
        val closed = face()
        assertFalse(FaceShapeClassifier.isMouthOpen(closed, 1000, 1000))
        val open = closed.toMutableList().apply { this[FaceShapeClassifier.LOWER_LIP_INNER] = LandmarkPoint(0.5f, 0.70f) }
        assertTrue(FaceShapeClassifier.isMouthOpen(open, 1000, 1000))
    }

    @Test
    fun anAverageForeheadLeavesTheLengthAloneAndATallOneLengthensItALittle() {
        val average = FaceShapeClassifier.withHairline(POPULATION_MEAN, FaceShapeClassifier.AVERAGE_FULL_TO_MESH_LENGTH)
        assertEquals(POPULATION_MEAN.lengthToWidth, average.lengthToWidth, 0.0001f)
        val tall = FaceShapeClassifier.withHairline(POPULATION_MEAN, 1.30f)
        assertTrue(tall.lengthToWidth > POPULATION_MEAN.lengthToWidth)
        // Capped: hair over the forehead moves between sessions, so it can't swing the shape far.
        val extreme = FaceShapeClassifier.withHairline(POPULATION_MEAN, 2.0f)
        assertEquals(POPULATION_MEAN.lengthToWidth * 1.04f, extreme.lengthToWidth, 0.0001f)
    }

    private fun probabilities(oval: Float, heart: Float): Map<FaceShape, Float> {
        val rest = (1f - oval - heart) / 2f
        return mapOf(FaceShape.OVAL to oval, FaceShape.HEART to heart, FaceShape.ROUND to rest, FaceShape.SQUARE to rest)
    }

    /** Temples and jaw both [z] standard deviations narrower than average. */
    private fun diamondish(z: Float) = POPULATION_MEAN.copy(
        jawToCheek = POPULATION_MEAN.jawToCheek + z * POPULATION_SD.jawToCheek,
        foreheadToCheek = POPULATION_MEAN.foreheadToCheek + z * POPULATION_SD.foreheadToCheek
    )

    /**
     * MediaPipe's canonical face (cm, +y up, +z toward the camera) 35 cm from a lens whose focal
     * length is 0.75 of a 1000 px frame, nodded by [pitchDeg]: positive brings the chin closer.
     */
    private fun rendered(pitchDeg: Float): List<LandmarkPoint> {
        val canonical = mapOf(
            FaceShapeClassifier.FOREHEAD_TOP to Triple(0.00, 8.26, 4.48),
            FaceShapeClassifier.CHIN to Triple(0.00, -9.40, 4.26),
            FaceShapeClassifier.LEFT_CHEEK to Triple(-7.66, 0.67, -2.44),
            FaceShapeClassifier.RIGHT_CHEEK to Triple(7.66, 0.67, -2.44),
            FaceShapeClassifier.LEFT_JAW to Triple(-5.94, -6.22, -0.63),
            FaceShapeClassifier.RIGHT_JAW to Triple(5.94, -6.22, -0.63),
            FaceShapeClassifier.LEFT_TEMPLE to Triple(-7.74, 2.36, -2.01),
            FaceShapeClassifier.RIGHT_TEMPLE to Triple(7.74, 2.36, -2.01),
            FaceShapeClassifier.LEFT_CHIN to Triple(-3.21, -8.53, 2.80),
            FaceShapeClassifier.RIGHT_CHIN to Triple(3.21, -8.53, 2.80)
        )
        val angle = Math.toRadians(pitchDeg.toDouble())
        val points = MutableList(478) { LandmarkPoint(0.5f, 0.5f) }
        for ((index, position) in canonical) {
            val (x, y, z) = position
            val turnedY = y * cos(angle) + z * sin(angle)
            val turnedZ = z * cos(angle) - y * sin(angle)
            val depth = 35.0 - turnedZ
            points[index] = LandmarkPoint((0.5 + 0.75 * x / depth).toFloat(), (0.5 - 0.75 * turnedY / depth).toFloat())
        }
        return points
    }

    private fun face(): List<LandmarkPoint> {
        val points = MutableList(478) { LandmarkPoint(0.5f, 0.5f) }
        points[FaceShapeClassifier.FOREHEAD_TOP] = LandmarkPoint(0.5f, 0.20f)
        points[FaceShapeClassifier.CHIN] = LandmarkPoint(0.5f, 0.80f)
        points[FaceShapeClassifier.LEFT_CHEEK] = LandmarkPoint(0.25f, 0.50f)
        points[FaceShapeClassifier.RIGHT_CHEEK] = LandmarkPoint(0.75f, 0.50f)
        points[FaceShapeClassifier.LEFT_JAW] = LandmarkPoint(0.30f, 0.70f)
        points[FaceShapeClassifier.RIGHT_JAW] = LandmarkPoint(0.70f, 0.70f)
        points[FaceShapeClassifier.LEFT_TEMPLE] = LandmarkPoint(0.25f, 0.35f)
        points[FaceShapeClassifier.RIGHT_TEMPLE] = LandmarkPoint(0.75f, 0.35f)
        points[FaceShapeClassifier.UPPER_LIP_INNER] = LandmarkPoint(0.5f, 0.66f)
        points[FaceShapeClassifier.LOWER_LIP_INNER] = LandmarkPoint(0.5f, 0.665f)
        return points
    }
}
