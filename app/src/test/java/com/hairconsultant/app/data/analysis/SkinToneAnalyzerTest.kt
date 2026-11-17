package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.SkinTone
import com.hairconsultant.app.domain.model.SkinUndertone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinToneAnalyzerTest {

    @Test
    fun cielabOfWhiteAndOfATypicalSkinColour() {
        val white = SkinToneAnalyzer.cielab(0xFFFFFFFF.toInt())
        assertEquals(100f, white[0], 0.1f)
        assertEquals(0f, white[1], 0.5f)
        assertEquals(0f, white[2], 0.5f)
        val skin = SkinToneAnalyzer.cielab(0xFFC69678.toInt())
        assertEquals(66f, skin[0], 1.5f)
        assertTrue(skin[1] > 5f && skin[2] > 15f)
    }

    @Test
    fun depthFollowsTheStandardItaBands() {
        assertEquals(SkinTone.VERY_LIGHT, tone(l = 75f, b = 14f))
        assertEquals(SkinTone.LIGHT, tone(l = 68f, b = 18f))
        assertEquals(SkinTone.MEDIUM, tone(l = 65f, b = 20f))
        assertEquals(SkinTone.TAN, tone(l = 56f, b = 20f))
        assertEquals(SkinTone.BROWN, tone(l = 42f, b = 18f))
        assertEquals(SkinTone.DEEP, tone(l = 30f, b = 12f))
    }

    @Test
    fun undertoneFollowsTheHueAngle() {
        assertEquals(SkinUndertone.WARM, combine(SkinReading(60f, a = 9f, b = 20f, Lighting.GOOD)).undertone)
        assertEquals(SkinUndertone.COOL, combine(SkinReading(60f, a = 16f, b = 15f, Lighting.GOOD)).undertone)
        assertEquals(SkinUndertone.NEUTRAL, combine(SkinReading(60f, a = 12f, b = 16f, Lighting.GOOD)).undertone)
    }

    @Test
    fun wellLitFramesWinAndPoorLightLowersConfidence() {
        val dim = SkinReading(30f, 12f, 12f, Lighting.DIM)
        val lit = SkinReading(65f, 12f, 20f, Lighting.GOOD)
        assertEquals(SkinTone.MEDIUM, combine(dim, lit, lit).tone)

        val onlyDim = combine(dim, dim)
        assertEquals(Lighting.DIM, onlyDim.lighting)
        assertTrue(onlyDim.toneConfidence <= 0.4f)
        assertTrue(onlyDim.undertoneConfidence <= 0.35f)
    }

    @Test
    fun undertoneConfidenceStaysBelowCertain() {
        val clear = combine(SkinReading(60f, a = 5f, b = 25f, Lighting.GOOD))
        assertTrue(clear.undertoneConfidence <= 0.75f)
    }

    @Test
    fun noReadingsNoResult() {
        assertNull(SkinToneAnalyzer.combine(emptyList()))
    }

    private fun tone(l: Float, b: Float) = combine(SkinReading(l, a = 12f, b = b, Lighting.GOOD)).tone

    private fun combine(vararg readings: SkinReading) = SkinToneAnalyzer.combine(readings.toList())!!
}
