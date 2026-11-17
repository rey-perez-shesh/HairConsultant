package com.hairconsultant.app.ui.haircolor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class HairColorMathTest {

    private val blackHair = HairStats(meanL = 0.22f, stdL = 0.05f, coverage = 0.3f)
    private val blondeHair = HairStats(meanL = 0.78f, stdL = 0.07f, coverage = 0.3f)
    private val platinum = HairShades.byId("platinum")!!
    private val jetBlack = HairShades.byId("jet_black")!!
    private val blue = HairShades.byId("blue")!!

    @Test
    fun oklabRoundTripsSrgb() {
        for (argb in listOf(0xFF000000, 0xFFFFFFFF, 0xFF7A1E2C, 0xFF2F5FD0, 0xFF808080).map { it.toInt() }) {
            val lab = HairColorMath.oklabOf(argb)
            val rgb = HairColorMath.oklabToSrgb(lab[0], lab[1], lab[2])
            assertEquals((argb shr 16 and 0xFF) / 255f, rgb[0], 0.004f)
            assertEquals((argb shr 8 and 0xFF) / 255f, rgb[1], 0.004f)
            assertEquals((argb and 0xFF) / 255f, rgb[2], 0.004f)
        }
    }

    @Test
    fun zeroAmountLeavesThePixelAlone() {
        val pixel = 0xFF3B2A20.toInt()
        assertEquals(pixel, HairColorMath.recolor(pixel, blue, blackHair, amount = 0f))
    }

    @Test
    fun strandsKeepTheirOrderLightToDark() {
        // Every shade on every kind of hair: a brighter strand must stay brighter, or detail inverts.
        for (shade in HairShades.all) for (stats in listOf(blackHair, blondeHair)) {
            var previous = -1f
            var l = 0f
            while (l <= 1f) {
                val out = HairColorMath.remapL(l, l, shade, stats)
                assertTrue("${shade.id} at L=$l went from $previous to $out", out >= previous - 1e-4f)
                previous = out
                l += 0.01f
            }
        }
    }

    @Test
    fun blackHairGoesPlatinumButOnlyAsFarAsTheLiftAllows() {
        val out = HairColorMath.remapL(blackHair.meanL, blackHair.meanL, platinum, blackHair)
        assertEquals(blackHair.meanL + platinum.maxLift, out, 0.001f)
        assertTrue(out < platinum.l)
    }

    @Test
    fun blondeHairGoesBlackAtItsAverage() {
        val out = HairColorMath.remapL(blondeHair.meanL, blondeHair.meanL, jetBlack, blondeHair)
        assertEquals(jetBlack.l, out, 0.001f)
    }

    @Test
    fun shineStaysBrightAndNearNeutral() {
        val shine = 0.6f // far above black hair's average
        val newL = HairColorMath.remapL(shine, shine, jetBlack, blackHair)
        assertTrue("shine was darkened to $newL", newL >= shine)

        val onStrand = HairColorMath.chromaScale(0.5f, blackHair.meanL, blue, blackHair)
        val onShine = HairColorMath.chromaScale(0.5f, shine, blue, blackHair)
        assertTrue(onShine < onStrand * 0.5f)
    }

    @Test
    fun dyedPixelTakesTheShadesHue() {
        val out = HairColorMath.oklabOf(HairColorMath.recolor(0xFF2A2422.toInt(), blue, blackHair, amount = 1f))
        val chroma = hypot(out[1], out[2])
        assertTrue("barely colored: chroma=$chroma", chroma > 0.05f)
        // Same direction in the a/b plane as the shade itself.
        val cosine = (out[1] * blue.a + out[2] * blue.b) / (chroma * hypot(blue.a, blue.b))
        assertTrue("hue drifted: cos=$cosine", cosine > 0.95f)
    }

    @Test
    fun statsAverageOnlyConfidentHair() {
        val dark = 0xFF202020.toInt()
        val skin = 0xFFE0B090.toInt()
        // Left half confident hair, right half a faint edge over skin that must not count.
        val alpha = ByteArray(16) { if (it % 4 < 2) 255.toByte() else 60 }
        val stats = HairColorMath.hairStats(alpha, 4, 4, stride = 1) { x, _ -> if (x < 2) dark else skin }
        assertEquals(HairColorMath.oklabOf(dark)[0], stats.meanL, 0.001f)
        assertTrue(abs(stats.stdL) < 0.001f)
        assertEquals(0.5f, stats.coverage, 0.001f)
    }

    @Test
    fun noHairFallsBackToTheDefault() {
        assertEquals(HairStats.Default, HairColorMath.hairStats(ByteArray(16), 4, 4, stride = 1) { _, _ -> 0 })
    }
}
