package com.hairconsultant.app.ui.profile

import com.hairconsultant.app.domain.model.ArType
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.arFilterFavoriteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which History hearts favorite a try-on filter rather than the whole session. */
class ArTryOnHaircutTest {

    private val mullet = haircut(ArType.DEEPAR, "https://example.test/modern_mullet.deepar")

    @Test
    fun aFaceScanTryOnWithALiveFilterIsThatFilter() {
        assertEquals(mullet, session("scan-1", ConsultationSource.FACE_SCAN, mullet).arTryOnHaircut)
    }

    @Test
    fun uploadsScansWithoutATryOnAndStylesWithoutAFilterStaySessions() {
        assertNull(session("upload-1", ConsultationSource.IMAGE_UPLOAD, mullet).arTryOnHaircut)
        assertNull(session("scan-2", ConsultationSource.FACE_SCAN, null).arTryOnHaircut)
        assertNull(session("scan-3", ConsultationSource.FACE_SCAN, haircut(ArType.AUTO, null)).arTryOnHaircut)
    }

    @Test
    fun aSavedFilterIsNotTreatedAsASessionAgain() {
        assertNull(session(arFilterFavoriteId("u1", mullet.id), ConsultationSource.FACE_SCAN, mullet).arTryOnHaircut)
    }

    private fun haircut(arType: ArType, arAssetUrl: String?) = Haircut(
        id = "MEDIUM_WAVY_7",
        name = "Modern Mullet",
        imageUrl = "",
        length = HairLength.MEDIUM,
        texture = HairTexture.WAVY,
        recommendedFaceShapes = listOf(FaceShape.OVAL),
        arType = arType,
        arAssetUrl = arAssetUrl
    )

    private fun session(id: String, source: ConsultationSource, haircut: Haircut?) = Consultation(
        id = id,
        userId = "u1",
        source = source,
        scanResult = ScanResult(FaceShape.OVAL, HairLength.MEDIUM, HairTexture.WAVY),
        selectedHaircut = haircut,
        sourceImageUrl = null,
        resultImageUrl = null,
        isFavorite = true
    )
}
