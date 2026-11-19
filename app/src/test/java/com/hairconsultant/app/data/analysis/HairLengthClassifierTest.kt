package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.HairLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun theIrisSetsTheScaleAndHairAboveTheSkullIsMeasured() {
        val labels = ByteArray(100 * 100)
        fill(labels, rows = 10..40, columns = 35..65)
        val result = HairLengthClassifier.classify(HairMask(labels, 100, 100), scaledPoints(), null, 1000, 1000)!!
        // 20 px iris -> 11.7 / 20 mm per px; the skull top is 1.5 face heights above the chin (y 0.175).
        assertEquals(0.585f, result.mmPerPx!!, 0.0001f)
        assertEquals((0.175f - 0.10f) * 585f * 1.15f, result.topMm!!, 0.5f)
        assertNull(result.fringeMm)
        assertNull(result.fallMm)
    }

    @Test
    fun hairHangingPastTheEarLobesIsMeasuredFromTheSkullTop() {
        val labels = ByteArray(100 * 100)
        fill(labels, rows = 10..80, columns = 20..30)
        val result = HairLengthClassifier.classify(HairMask(labels, 100, 100), scaledPoints(), null, 1000, 1000)!!
        assertEquals((0.80f - 0.175f) * 585f, result.fallMm!!, 0.5f)
    }

    @Test
    fun aFringeIsMeasuredFromTheNaturalHairline() {
        val points = scaledPoints().toMutableList().apply {
            this[151] = LandmarkPoint(0.5f, 0.33f)
            this[109] = LandmarkPoint(0.42f, 0.32f)
            this[338] = LandmarkPoint(0.58f, 0.32f)
            this[9] = LandmarkPoint(0.5f, 0.40f)
            this[2] = LandmarkPoint(0.5f, 0.47f)
        }
        val labels = ByteArray(100 * 100)
        fill(labels, rows = 10..36, columns = 35..65)
        val result = HairLengthClassifier.classify(HairMask(labels, 100, 100), points, null, 1000, 1000)!!
        // Hairline one nose-to-chin above the brow: 0.40 - 0.08 = 0.32; the fringe ends at 0.37; plus 25 mm of origin.
        assertEquals(0.05f * 585f + 25f, result.fringeMm!!, 0.5f)
    }

    @Test
    fun withoutIrisPointsTheScaleFallsBackToFaceWidth() {
        val points = scaledPoints().take(468)
        assertEquals(140f / 240f, HairLengthClassifier.mmPerPixel(points, 1000, 1000)!!, 0.0001f)
    }

    @Test
    fun combiningFramesTakesTheMedianOfReadingsMostFramesHad() {
        val combined = HairLengthClassifier.combine(
            listOf(
                reading(0.1f).copy(topMm = 40f, fringeMm = 30f),
                reading(0.1f).copy(topMm = 60f),
                reading(0.1f).copy(topMm = 50f)
            )
        )!!
        assertEquals(50f, combined.topMm!!, 0.001f)
        assertNull(combined.fringeMm)
    }

    private fun reading(belowChin: Float) = HairLengthClassification(HairLength.MEDIUM, 0.6f, 0.1f, belowChin)

    /** A face with a 20 px iris and a 240 px cheek width on a 1000 px image, mesh top at 0.30 and chin at 0.55. */
    private fun scaledPoints(): List<LandmarkPoint> = facePoints(chinY = 0.55f, topY = 0.30f).toMutableList().apply {
        this[469] = LandmarkPoint(0.39f, 0.40f)
        this[470] = LandmarkPoint(0.40f, 0.393f)
        this[471] = LandmarkPoint(0.41f, 0.40f)
        this[472] = LandmarkPoint(0.40f, 0.407f)
        this[474] = LandmarkPoint(0.59f, 0.40f)
        this[475] = LandmarkPoint(0.60f, 0.393f)
        this[476] = LandmarkPoint(0.61f, 0.40f)
        this[477] = LandmarkPoint(0.60f, 0.407f)
        this[FaceShapeClassifier.LEFT_CHEEK] = LandmarkPoint(0.38f, 0.45f)
        this[FaceShapeClassifier.RIGHT_CHEEK] = LandmarkPoint(0.62f, 0.45f)
    }

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
