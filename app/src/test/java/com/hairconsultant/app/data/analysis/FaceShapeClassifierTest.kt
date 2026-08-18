package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.FaceShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FaceShapeClassifierTest {

    @Test
    fun oval() {
        assertEquals(
            FaceShape.OVAL,
            classify(
                topY = 0.18f,
                chinY = 0.82f,
                cheek = 0.28f to 0.72f,
                jaw = 0.32f to 0.68f,
                forehead = 0.30f to 0.70f
            )
        )
    }

    @Test
    fun round() {
        assertEquals(
            FaceShape.ROUND,
            classify(
                topY = 0.24f,
                chinY = 0.76f,
                cheek = 0.22f to 0.78f,
                jaw = 0.24f to 0.76f,
                forehead = 0.24f to 0.76f
            )
        )
    }

    @Test
    fun square() {
        assertEquals(
            FaceShape.SQUARE,
            classify(
                topY = 0.22f,
                chinY = 0.78f,
                cheek = 0.24f to 0.76f,
                jaw = 0.25f to 0.75f,
                forehead = 0.25f to 0.75f
            )
        )
    }

    @Test
    fun heart() {
        assertEquals(
            FaceShape.HEART,
            classify(
                topY = 0.18f,
                chinY = 0.84f,
                cheek = 0.28f to 0.72f,
                jaw = 0.36f to 0.64f,
                forehead = 0.22f to 0.78f
            )
        )
    }

    @Test
    fun triangle() {
        assertEquals(
            FaceShape.TRIANGLE,
            classify(
                topY = 0.20f,
                chinY = 0.82f,
                cheek = 0.28f to 0.72f,
                jaw = 0.20f to 0.80f,
                forehead = 0.34f to 0.66f
            )
        )
    }

    @Test
    fun diamond() {
        assertEquals(
            FaceShape.DIAMOND,
            classify(
                topY = 0.16f,
                chinY = 0.86f,
                cheek = 0.22f to 0.78f,
                jaw = 0.34f to 0.66f,
                forehead = 0.34f to 0.66f
            )
        )
    }

    @Test
    fun longFace() {
        assertEquals(
            FaceShape.LONG,
            classify(
                topY = 0.10f,
                chinY = 0.92f,
                cheek = 0.32f to 0.68f,
                jaw = 0.34f to 0.66f,
                forehead = 0.33f to 0.67f
            )
        )
    }

    private fun classify(
        topY: Float,
        chinY: Float,
        cheek: Pair<Float, Float>,
        jaw: Pair<Float, Float>,
        forehead: Pair<Float, Float>
    ): FaceShape {
        val points = MutableList(478) { LandmarkPoint(0.5f, 0.5f) }
        points[FaceShapeClassifier.FOREHEAD_TOP] = LandmarkPoint(0.5f, topY)
        points[FaceShapeClassifier.CHIN] = LandmarkPoint(0.5f, chinY)
        points[FaceShapeClassifier.LEFT_CHEEK] = LandmarkPoint(cheek.first, 0.50f)
        points[FaceShapeClassifier.RIGHT_CHEEK] = LandmarkPoint(cheek.second, 0.50f)
        points[FaceShapeClassifier.LEFT_JAW] = LandmarkPoint(jaw.first, 0.72f)
        points[FaceShapeClassifier.RIGHT_JAW] = LandmarkPoint(jaw.second, 0.72f)
        points[FaceShapeClassifier.LEFT_FOREHEAD] = LandmarkPoint(forehead.first, 0.28f)
        points[FaceShapeClassifier.RIGHT_FOREHEAD] = LandmarkPoint(forehead.second, 0.28f)
        val result = FaceShapeClassifier.classify(points, 1000, 1000)
        assertNotNull(result)
        return result!!.shape
    }
}
