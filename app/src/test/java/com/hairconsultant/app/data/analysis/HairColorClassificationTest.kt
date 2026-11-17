package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.ui.haircolor.HairColorMath
import com.hairconsultant.app.ui.haircolor.HairShades
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HairColorClassificationTest {

    @Test
    fun eachColorStudioSwatchReadsAsItsOwnColor() {
        mapOf(
            "jet_black" to HairColor.BLACK,
            "soft_black" to HairColor.BLACK,
            "dark_brown" to HairColor.BROWN,
            "chestnut" to HairColor.BROWN,
            "light_brown" to HairColor.BROWN,
            "auburn" to HairColor.RED,
            "copper" to HairColor.RED,
            "golden_blonde" to HairColor.BLONDE,
            "silver" to HairColor.GRAY,
            "blue" to HairColor.FASHION,
            "rose" to HairColor.FASHION,
            "green" to HairColor.FASHION
        ).forEach { (id, expected) ->
            val lab = HairColorMath.oklabOf(HairShades.byId(id)!!.argb)
            assertEquals(id, expected, HairAppearanceClassifier.classifyColor(lab).first)
        }
    }

    @Test
    fun aSlightlyDarkerPhotoOfBrownHairIsStillBrown() {
        val chestnut = HairColorMath.oklabOf(HairShades.byId("chestnut")!!.argb)
        val dimmer = floatArrayOf(chestnut[0] - 0.04f, chestnut[1], chestnut[2])
        assertEquals(HairColor.BROWN, HairAppearanceClassifier.classifyColor(dimmer).first)
    }

    @Test
    fun aHueJustUnder360IsHandledAsRedVioletNotCyan() {
        // The old classifier averaged hues as plain numbers; 350° and 10° came out as 180°.
        val lab = oklab(lightness = 0.45f, chroma = 0.12f, hueDeg = 355f)
        assertEquals(HairColor.FASHION, HairAppearanceClassifier.classifyColor(lab).first)
    }

    @Test
    fun confidenceIsHigherWhenTheColourIsClearlyOneThing() {
        val clear = HairAppearanceClassifier.classifyColor(HairColorMath.oklabOf(HairShades.byId("jet_black")!!.argb)).second
        val soft = HairColorMath.oklabOf(HairShades.byId("soft_black")!!.argb)
        val brown = HairColorMath.oklabOf(HairShades.byId("dark_brown")!!.argb)
        val between = FloatArray(3) { (soft[it] + brown[it]) / 2f }
        assertTrue(clear > HairAppearanceClassifier.classifyColor(between).second)
    }

    private fun oklab(lightness: Float, chroma: Float, hueDeg: Float): FloatArray {
        val h = Math.toRadians(hueDeg.toDouble())
        return floatArrayOf(lightness, (chroma * cos(h)).toFloat(), (chroma * sin(h)).toFloat())
    }
}
