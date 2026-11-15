package com.hairconsultant.app.data.remote

import com.hairconsultant.app.domain.model.ArType
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArAssetCacheTest {

    @Test
    fun inferTypeFromUrl_readsDeeparAndGlb() {
        assertEquals(ArType.DEEPAR, ArAssetCache.inferTypeFromUrl("https://x/y/effect.deepar"))
        assertEquals(ArType.DEEPAR, ArAssetCache.inferTypeFromUrl("https://x/y/effect.deepar?token=1"))
        assertEquals(ArType.GLB, ArAssetCache.inferTypeFromUrl("https://x/y/wig.glb"))
        assertNull(ArAssetCache.inferTypeFromUrl("https://x/y/thumb.png"))
    }

    @Test
    fun resolvedArType_prefersExplicitType_andInfersAutoFromUrl() {
        assertEquals(
            ArType.DEEPAR,
            ArAssetCache.resolvedArType(
                style(arType = ArType.DEEPAR, url = "https://x/a.deepar")
            )
        )
        assertEquals(
            ArType.DEEPAR,
            ArAssetCache.resolvedArType(
                style(arType = ArType.AUTO, url = "https://x/a.deepar")
            )
        )
        assertEquals(
            ArType.GLB,
            ArAssetCache.resolvedArType(
                style(arType = ArType.AUTO, url = "https://x/a.glb")
            )
        )
        assertNull(ArAssetCache.resolvedArType(style(arType = ArType.AUTO, url = null)))
        assertNull(ArAssetCache.resolvedArType(style(arType = ArType.NONE, url = "https://x/a.deepar")))
    }

    private fun style(arType: ArType, url: String?) = Haircut(
        id = "ID",
        name = "Test",
        imageUrl = "",
        length = HairLength.SHORT,
        texture = HairTexture.STRAIGHT,
        recommendedFaceShapes = listOf(FaceShape.OVAL),
        arType = arType,
        arAssetUrl = url
    )
}
