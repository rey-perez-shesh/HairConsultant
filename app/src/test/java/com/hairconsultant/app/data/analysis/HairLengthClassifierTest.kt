package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.HairLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun classify(hairMaxY: Int): HairLengthClassification {
        val labels = ByteArray(100 * 100)
        for (y in 10..hairMaxY) {
            for (x in 35..65) {
                labels[y * 100 + x] = 1
            }
        }
        val result = HairLengthClassifier.classify(HairMask(labels, 100, 100), facePoints())
        assertNotNull(result)
        return result!!
    }

    private fun facePoints(): List<LandmarkPoint> {
        val points = MutableList(478) { LandmarkPoint(0.5f, 0.5f) }
        points[FaceShapeClassifier.FOREHEAD_TOP] = LandmarkPoint(0.5f, 0.20f)
        points[FaceShapeClassifier.CHIN] = LandmarkPoint(0.5f, 0.50f)
        return points
    }
}
