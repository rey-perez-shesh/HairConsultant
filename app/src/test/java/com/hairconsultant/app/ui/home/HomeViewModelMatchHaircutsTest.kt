package com.hairconsultant.app.ui.home

import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelMatchHaircutsTest {

    private val curlyWolfCut = haircut("Curly Wolf Cut", HairLength.SHORT, HairTexture.CURLY, listOf(FaceShape.ROUND, FaceShape.SQUARE))
    private val sleekLob = haircut("Sleek Lob", HairLength.MEDIUM, HairTexture.STRAIGHT, listOf(FaceShape.OVAL, FaceShape.HEART))
    private val longBob = haircut("Long Bob", HairLength.LONG, HairTexture.STRAIGHT, listOf(FaceShape.OVAL, FaceShape.HEART, FaceShape.DIAMOND))
    private val catalog = listOf(curlyWolfCut, sleekLob, longBob)

    @Test
    fun matchesByExactStyleName() {
        val result = matchHaircuts(catalog, "tell me more about the Curly Wolf Cut")

        assertEquals(listOf(curlyWolfCut), result)
    }

    @Test
    fun matchesByLength() {
        val result = matchHaircuts(catalog, "what's a good long style?")

        assertEquals(listOf(longBob), result)
    }

    @Test
    fun matchesByTexture() {
        val result = matchHaircuts(catalog, "I want something straight and easy to maintain")

        assertEquals(listOf(sleekLob, longBob), result)
    }

    @Test
    fun matchesByFaceShape() {
        val result = matchHaircuts(catalog, "what suits a round face?")

        assertEquals(listOf(curlyWolfCut), result)
    }

    @Test
    fun returnsEmptyWhenNothingMatches() {
        val result = matchHaircuts(catalog, "what's the weather like today?")

        assertTrue(result.isEmpty())
    }

    private fun haircut(name: String, length: HairLength, texture: HairTexture, faceShapes: List<FaceShape>) = Haircut(
        id = name,
        name = name,
        imageUrl = "https://example.test/$name",
        length = length,
        texture = texture,
        recommendedFaceShapes = faceShapes
    )
}
