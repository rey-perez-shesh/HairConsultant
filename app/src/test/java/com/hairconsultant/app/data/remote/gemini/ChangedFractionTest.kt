package com.hairconsultant.app.data.remote.gemini

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangedFractionTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val nearWhite = 0xFFF0F0F0.toInt()

    @Test
    fun reEncodingNoiseDoesNotCountAsChange() {
        assertEquals(0f, changedFraction(IntArray(100) { white }, IntArray(100) { nearWhite }), 0f)
    }

    @Test
    fun countsOnlyThePixelsThatMovedBetweenDarkAndLight() {
        val before = IntArray(100) { white }
        val after = IntArray(100) { if (it < 5) black else white }

        assertEquals(0.05f, changedFraction(before, after), 0.0001f)
    }

    @Test
    fun picturesThatCannotBeComparedCountAsChanged() {
        assertEquals(1f, changedFraction(IntArray(4), IntArray(9)), 0f)
    }
}
