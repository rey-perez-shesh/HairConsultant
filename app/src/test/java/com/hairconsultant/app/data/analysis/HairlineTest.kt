package com.hairconsultant.app.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HairlineTest {

    @Test
    fun findsWhereTheForeheadSkinEnds() {
        val hairline = find(foreheadEndsAtY = 0.22f)
        assertNotNull(hairline)
        assertEquals(0.5f, hairline!!.x, 0.005f)
        assertEquals(0.22f, hairline.y, 0.005f)
        assertEquals((0.80f - 0.22f) / 0.50f, Hairline.fullToMeshLength(face(), hairline, 1000, 1000), 0.01f)
    }

    @Test
    fun aFringeLyingOnTheForeheadIsNotAHairline() {
        // Hair starting just above the mesh's top point leaves an implausibly short forehead.
        assertNull(find(foreheadEndsAtY = 0.29f))
    }

    @Test
    fun aBaldHeadHasNoHairlineToFind() {
        assertNull(Hairline.find(face(), 1000, 1000, resolution = 256) { _, _ -> true })
    }

    @Test
    fun aStrandAcrossTheForeheadDoesNotEndIt() {
        val hairline = Hairline.find(face(), 1000, 1000, resolution = 256) { _, y -> y > 0.22f && y !in 0.255f..0.258f }
        assertEquals(0.22f, hairline!!.y, 0.005f)
    }

    @Test
    fun foreheadWidthIsMeasuredAcrossTheMiddleOfTheForehead() {
        // Skin from x 0.3 to 0.7 across the forehead; cheekbones 0.25 to 0.75.
        val width = Hairline.foreheadWidth(face(), LandmarkPoint(0.5f, 0.22f), 1000, 1000, resolution = 256) { x, y ->
            y > 0.22f && x in 0.3f..0.7f
        }
        assertEquals(0.4f / 0.5f, width!!, 0.03f)
    }

    private fun find(foreheadEndsAtY: Float): LandmarkPoint? =
        Hairline.find(face(), 1000, 1000, resolution = 256) { _, y -> y > foreheadEndsAtY }

    /** Mesh top at 0.30, brow at 0.40, nose base at 0.60, chin at 0.80: equal thirds put the hairline near 0.20. */
    private fun face(): List<LandmarkPoint> {
        val points = MutableList(478) { LandmarkPoint(0.5f, 0.5f) }
        points[FaceShapeClassifier.FOREHEAD_TOP] = LandmarkPoint(0.50f, 0.30f)
        points[109] = LandmarkPoint(0.45f, 0.305f)
        points[338] = LandmarkPoint(0.55f, 0.305f)
        points[9] = LandmarkPoint(0.50f, 0.40f)
        points[2] = LandmarkPoint(0.50f, 0.60f)
        points[FaceShapeClassifier.CHIN] = LandmarkPoint(0.50f, 0.80f)
        points[FaceShapeClassifier.LEFT_CHEEK] = LandmarkPoint(0.25f, 0.50f)
        points[FaceShapeClassifier.RIGHT_CHEEK] = LandmarkPoint(0.75f, 0.50f)
        return points
    }
}
