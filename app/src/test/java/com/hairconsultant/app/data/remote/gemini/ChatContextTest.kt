package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun genderLineNamesTheProfileGendersStylesPlusUnisex() {
        assertEquals(
            "User's gender (from their profile): Male. Recommend only Masculine or Unisex styles from the catalog " +
                "unless the user explicitly asks about a style tagged for another gender.",
            Gender.MALE.describeForChatContext()
        )
        assertTrue(Gender.FEMALE.describeForChatContext()!!.contains("Recommend only Feminine or Unisex styles"))
    }

    @Test
    fun noGenderLineWhenGenderShouldNotNarrowTheCatalog() {
        assertNull(Gender.NON_BINARY.describeForChatContext())
        assertNull(Gender.PREFER_NOT_TO_SAY.describeForChatContext())
        assertNull((null as Gender?).describeForChatContext())
    }

    @Test
    fun typedRequestsToSeeHairstylesCountAsTheChip() {
        assertTrue(asksToSeeHairstyles("Show My Hairstyles"))
        assertTrue(asksToSeeHairstyles("ok show me the new haircuts based on that"))
        assertTrue(asksToSeeHairstyles("Can you suggest some options for curly hair?"))
        assertTrue(asksToSeeHairstyles("I want to see my matches"))
    }

    @Test
    fun ordinaryConsultationMessagesAreNotRequestsToSeeHairstyles() {
        assertFalse(asksToSeeHairstyles("I want something shorter and easier to maintain"))
        assertFalse(asksToSeeHairstyles("show me how to style it in the morning"))
        assertFalse(asksToSeeHairstyles("which hairstyle is best for humid weather?"))
        assertFalse(asksToSeeHairstyles("I want to see how it looks with a perm"))
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
