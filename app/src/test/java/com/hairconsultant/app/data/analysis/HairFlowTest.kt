package com.hairconsultant.app.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class HairFlowTest {

    @Test
    fun followsVerticalStrandsTheLengthOfTheHair() {
        // Hair rows 10..109: traced inside the 3 px edge margin, rows 13..106.
        val length = HairFlow.longestStrandPx(stripes(vertical = true), SIZE, SIZE, hair(10..109, 10..109), SIZE - 1)
        assertEquals(93f, length!!, 3f)
    }

    @Test
    fun followsHorizontalStrandsAcrossNotDown() {
        // Strands run along the streaks, across the gradient: 93 px wide, though only 43 px tall.
        val length = HairFlow.longestStrandPx(stripes(vertical = false), SIZE, SIZE, hair(10..59, 10..109), SIZE - 1)
        assertEquals(93f, length!!, 3f)
    }

    @Test
    fun hairWithNoVisibleTextureGivesNoReading() {
        val flat = FloatArray(SIZE * SIZE) { 0.3f }
        assertNull(HairFlow.longestStrandPx(flat, SIZE, SIZE, hair(10..109, 10..109), SIZE - 1))
    }

    @Test
    fun onlyHairAboveTheBrowsSeedsATrace() {
        assertNull(HairFlow.longestStrandPx(stripes(vertical = true), SIZE, SIZE, hair(10..109, 10..109), seedBottomRow = 5))
    }

    /** Streaks 6 px apart: down the image when [vertical], across it otherwise. */
    private fun stripes(vertical: Boolean) = FloatArray(SIZE * SIZE) { i ->
        val along = if (vertical) i % SIZE else i / SIZE
        0.5f + 0.3f * sin(2 * PI * along / 6).toFloat()
    }

    private fun hair(rows: IntRange, columns: IntRange) = BooleanArray(SIZE * SIZE) { i -> i / SIZE in rows && i % SIZE in columns }

    private companion object {
        const val SIZE = 120
    }
}
