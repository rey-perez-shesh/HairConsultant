package com.hairconsultant.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArFilterFavoriteTest {

    private fun consultation(id: String) = Consultation(
        id = id,
        userId = "u1",
        source = ConsultationSource.FACE_SCAN,
        scanResult = ScanResult(FaceShape.OVAL, HairLength.MEDIUM, HairTexture.STRAIGHT),
        selectedHaircut = null,
        sourceImageUrl = null,
        resultImageUrl = null
    )

    @Test
    fun `an AR filter favorite is a saved look, kept out of History, and not a color look`() {
        val favorite = consultation(arFilterFavoriteId("u1", "SHORT_STRAIGHT_1"))
        assertTrue(favorite.isArFilter)
        assertTrue(favorite.isSavedLook)
        assertNull(favorite.colorLookShadeId)
    }

    @Test
    fun `the same user and style always get the same id`() {
        assertEquals(arFilterFavoriteId("u1", "a-b"), arFilterFavoriteId("u1", "a-b"))
    }

    @Test
    fun `other saved looks and sessions are not AR filters`() {
        assertFalse(consultation(SAVED_LOOK_ID_PREFIX + "1234").isArFilter)
        assertFalse(consultation(COLOR_LOOK_ID_PREFIX + "copper-1234").isArFilter)
        assertFalse(consultation("session-1").isArFilter)
    }
}
