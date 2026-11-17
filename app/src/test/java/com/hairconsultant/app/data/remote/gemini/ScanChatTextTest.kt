package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.SkinTone
import com.hairconsultant.app.domain.model.SkinUndertone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanChatTextTest {

    private val sure = ScanResult(
        faceShape = FaceShape.OVAL,
        hairLength = HairLength.SHORT,
        hairTexture = HairTexture.STRAIGHT,
        hairColor = HairColor.BLACK,
        faceShapeConfidence = 0.6f,
        hairLengthConfidence = 0.7f,
        hairTextureConfidence = 0.7f,
        hairColorConfidence = 0.7f,
        skinTone = SkinTone.TAN,
        skinUndertone = SkinUndertone.WARM,
        skinToneConfidence = 0.8f,
        skinUndertoneConfidence = 0.7f
    )

    @Test
    fun namesOnlyTheResultsTheScanIsUnsureOf() {
        assertNull(sure.unsureSentence("Fix"))
        assertEquals(
            "I'm not sure about your face shape and hair texture, so check them with \"Fix\".",
            sure.copy(faceShapeConfidence = 0.3f, hairTextureConfidence = 0.2f).unsureSentence("Fix")
        )
        // Bald: nothing to say about texture.
        assertNull(sure.copy(hairLength = HairLength.BALD, hairTextureConfidence = 0f).unsureSentence("Fix"))
    }

    @Test
    fun anUnsureUndertoneComesWithTheQuestionsToSettleIt() {
        val known = sure.describeColoringForChatContext()!!
        assertTrue(known.contains("Tan, warm undertone"))
        assertFalse(known.contains("jewelry"))

        val unsure = sure.copy(skinUndertoneConfidence = 0.4f).describeColoringForChatContext()!!
        assertTrue(unsure.contains("leaning warm"))
        assertTrue(unsure.contains("jewelry"))
    }

    @Test
    fun poorlyReadSkinIsNotStatedAsFact() {
        val result = sure.copy(skinToneConfidence = 0.3f)
        assertNull(result.skinScanSentence())
        assertTrue(result.describeColoringForChatContext()!!.contains("not read reliably"))
    }

    @Test
    fun fashionDyedHairIsNamed() {
        assertTrue(sure.copy(hairColor = HairColor.FASHION).describeColoringForChatContext()!!.contains("Fashion-dyed"))
    }
}
