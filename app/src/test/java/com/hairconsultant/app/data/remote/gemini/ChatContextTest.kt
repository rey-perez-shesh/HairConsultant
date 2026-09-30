package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextTest {

    private val curlyWolfCut = haircut("Curly Wolf Cut", HairLength.SHORT, HairTexture.CURLY, listOf(FaceShape.ROUND, FaceShape.SQUARE))
    private val sleekLob = haircut("Sleek Lob", HairLength.MEDIUM, HairTexture.STRAIGHT, listOf(FaceShape.OVAL, FaceShape.HEART))
    private val longBob = haircut("Long Bob", HairLength.LONG, HairTexture.STRAIGHT, listOf(FaceShape.OVAL, FaceShape.HEART, FaceShape.DIAMOND))
    private val undercut = haircut("Undercut", HairLength.SHORT, HairTexture.STRAIGHT, listOf(FaceShape.OVAL))
    private val undercutCrop = haircut("Undercut Crop with Length on Top", HairLength.SHORT, HairTexture.STRAIGHT, listOf(FaceShape.OVAL))
    private val classic = haircut("Classic", HairLength.SHORT, HairTexture.STRAIGHT, listOf(FaceShape.OVAL))
    private val catalog = listOf(curlyWolfCut, sleekLob, longBob, undercut, undercutCrop, classic)

    @Test
    fun findsStylesNamedAnywhereInTheCatalogInMentionOrder() {
        val result = haircutsNamedIn("Try the \"Long Bob\", or for curls the Curly Wolf Cut.", catalog)

        assertEquals(listOf(longBob, curlyWolfCut), result)
    }

    @Test
    fun longerNameClaimsItsTextSoShorterContainedNameIsNotDoubleCounted() {
        val result = haircutsNamedIn("The Undercut Crop with Length on Top keeps the sides tight.", catalog)

        assertEquals(listOf(undercutCrop), result)
    }

    @Test
    fun shorterNameStillMatchesWhenMentionedOnItsOwn() {
        val result = haircutsNamedIn("The Undercut Crop with Length on Top or a plain Undercut both work.", catalog)

        assertEquals(listOf(undercutCrop, undercut), result)
    }

    @Test
    fun ordinaryProseDoesNotMatchAStyleName() {
        val result = haircutsNamedIn("That gives you a classic, sleek lob-like shape.", catalog)

        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyWhenNoStyleIsNamed() {
        val result = haircutsNamedIn("Rebonding usually lasts 6-12 months.", catalog)

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
