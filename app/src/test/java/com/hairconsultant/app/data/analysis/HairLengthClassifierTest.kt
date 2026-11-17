package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.HairLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HairLengthClassifierTest {

    @Test
    fun shortHairEndsNearChin() {
        val result = classify(hairMaxY = 48)
        assertEquals(HairLength.SHORT, result.length)
    }

    @Test
    fun mediumHairPastShoulders() {
        val result = classify(hairMaxY = 75)
        assertEquals(HairLength.MEDIUM, result.length)
    }

    @Test
    fun longHairReachesBottom() {
        val result = classify(hairMaxY = 95)
        assertEquals(HairLength.LONG, result.length)
    }

    @Test
    fun almostNoHairCountsAsBald() {
        val mask = HairMask(ByteArray(100 * 100), 100, 100)
        val result = HairLengthClassifier.classify(mask, facePoints())
        assertNotNull(result)
        assertEquals(HairLength.BALD, result!!.length)
    }

    @Test
    fun aBeardBelowTheChinIsNotHairLength() {
        val labels = ByteArray(100 * 100)
        fill(labels, rows = 10..30, columns = 30..70)
        fill(labels, rows = 40..70, columns = 40..60)
        val points = facePoints().toMutableList().apply {
            this[FaceShapeClassifier.LEFT_JAW] = LandmarkPoint(0.35f, 0.45f)
            this[FaceShapeClassifier.RIGHT_JAW] = LandmarkPoint(0.65f, 0.45f)
            this[2] = LandmarkPoint(0.5f, 0.40f)
        }
        assertEquals(HairLength.SHORT, HairLengthClassifier.classify(HairMask(labels, 100, 100), points)!!.length)
    }

    @Test
    fun hairRunningOffTheFrameIsAtLeastMediumAndUnsure() {
        val labels = ByteArray(100 * 100)
        fill(labels, rows = 10..99, columns = 20..30)
        val nearBottom = facePoints(chinY = 0.95f, topY = 0.65f)
        val result = HairLengthClassifier.classify(HairMask(labels, 100, 100), nearBottom)!!
        assertTrue(result.reachesFrameBottom)
        assertEquals(HairLength.MEDIUM, result.length)
        assertTrue(result.confidence <= 0.45f)
    }

    @Test
    fun combiningFramesTakesTheMedianReach() {
        val combined = HairLengthClassifier.combine(listOf(reading(-0.1f), reading(0.1f), reading(0.5f)))!!
        assertEquals(HairLength.SHORT, combined.length)
        assertEquals(0.1f, combined.belowChinRatio, 0.0001f)
    }

    @Test
    fun mostFramesBaldMeansBald() {
        val bald = HairLengthClassification(HairLength.BALD, 0.78f, 0.001f, 0f)
        assertEquals(HairLength.BALD, HairLengthClassifier.combine(listOf(bald, bald, reading(0.5f)))!!.length)
    }

    private fun reading(belowChin: Float) = HairLengthClassification(HairLength.MEDIUM, 0.6f, 0.1f, belowChin)

    private fun classify(hairMaxY: Int): HairLengthClassification {
        val labels = ByteArray(100 * 100)
        fill(labels, rows = 10..hairMaxY, columns = 35..65)
        val result = HairLengthClassifier.classify(HairMask(labels, 100, 100), facePoints())
        assertNotNull(result)
        return result!!
    }

    private fun fill(labels: ByteArray, rows: IntRange, columns: IntRange) {
        for (y in rows) for (x in columns) labels[y * 100 + x] = 1
    }

    private fun facePoints(chinY: Float = 0.50f, topY: Float = 0.20f): List<LandmarkPoint> {
        val points = MutableList(478) { LandmarkPoint(0.5f, 0.5f) }
        points[FaceShapeClassifier.FOREHEAD_TOP] = LandmarkPoint(0.5f, topY)
        points[FaceShapeClassifier.CHIN] = LandmarkPoint(0.5f, chinY)
        return points
    }
}
