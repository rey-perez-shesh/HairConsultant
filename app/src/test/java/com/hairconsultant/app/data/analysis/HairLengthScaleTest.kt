package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.data.SampleData
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.MeasuredHairLength
import com.hairconsultant.app.domain.model.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HairLengthScaleTest {

    @Test
    fun mensScaleGoesByInchesOnTop() {
        assertEquals(HairLength.SHORT, HairLengthScale.label(1.9f, Gender.MALE))
        assertEquals(HairLength.MEDIUM, HairLengthScale.label(2.1f, Gender.MALE))
        assertEquals(HairLength.MEDIUM, HairLengthScale.label(5f, Gender.MALE))
        assertEquals(HairLength.LONG, HairLengthScale.label(5.5f, Gender.MALE))
    }

    @Test
    fun whereItFallsScaleCountsChinLengthAsShortAndPastTheShoulderAsLong() {
        for (gender in listOf(Gender.FEMALE, Gender.NON_BINARY, Gender.PREFER_NOT_TO_SAY, null)) {
            assertEquals(HairLength.SHORT, HairLengthScale.label(9f, gender))
            assertEquals(HairLength.MEDIUM, HairLengthScale.label(13f, gender))
            assertEquals(HairLength.LONG, HairLengthScale.label(16f, gender))
        }
    }

    @Test
    fun theSameHairCanBeMediumForAManAndShortForAWoman() {
        val fourInchesOnTop = MeasuredHairLength(3.5f, 4.5f, 4f, "on top")
        assertEquals(HairLength.MEDIUM, HairLengthScale.label(fourInchesOnTop, Gender.MALE))
        assertEquals(HairLength.SHORT, HairLengthScale.label(fourInchesOnTop, Gender.FEMALE))
    }

    @Test
    fun catalogStylesAreLabelledByTheirInchesOnTheUsersScale() {
        val undercut = SampleData.allHaircuts.first { it.name == "Undercut" }
        assertEquals(HairLength.SHORT, undercut.length)
        assertEquals(HairLength.MEDIUM, HairLengthScale.label(undercut, Gender.MALE))
        val longBob = SampleData.allHaircuts.first { it.name == "Long Bob" }
        assertEquals(HairLength.MEDIUM, HairLengthScale.label(longBob, Gender.FEMALE))
    }

    @Test
    fun measureTakesTheLongestSectionAndAllowsForError() {
        val reading = classification(topMm = 50.8f, fringeMm = 30f)
        val measured = HairLengthScale.measure(reading, HairTexture.STRAIGHT)!!
        assertEquals("on top", measured.section)
        assertEquals(2f, measured.visibleIn, 0.001f)
        assertEquals(1.34f, measured.minIn, 0.01f)
        assertEquals(2.66f, measured.maxIn, 0.01f)
        assertFalse(measured.atLeast)
    }

    @Test
    fun aTracedStrandLongerThanTheHairIsTallReadsAsTheTopLength() {
        // Combed-over hair: stands 1 cm above the skull, but its strands trace 4 in across the head.
        val measured = HairLengthScale.measure(classification(topMm = 10f, flowMm = 101.6f), HairTexture.STRAIGHT)!!
        assertEquals("on top", measured.section)
        assertEquals(4f, measured.visibleIn, 0.001f)
        assertEquals(4f * 0.92f - 1f, measured.minIn, 0.01f)
    }

    @Test
    fun curlsWidenTheStretchedRange() {
        val measured = HairLengthScale.measure(classification(topMm = 50.8f), HairTexture.CURLY)!!
        assertEquals(1.34f / 0.75f, measured.minIn, 0.01f)
        assertEquals(2.66f / 0.5f, measured.maxIn, 0.01f)
    }

    @Test
    fun hangingHairCutOffByTheFrameIsAFloor() {
        val measured = HairLengthScale.measure(
            classification(topMm = 20f, fallMm = 406f, reachesFrameBottom = true),
            HairTexture.STRAIGHT
        )!!
        assertEquals("overall", measured.section)
        assertTrue(measured.atLeast)
        assertTrue(measured.describe().startsWith("at least"))
    }

    @Test
    fun nothingMeasuredGivesNoLength() {
        assertNull(HairLengthScale.measure(classification(), HairTexture.STRAIGHT))
    }

    @Test
    fun fixChipsSetTheLengthAndLabelForTheUsersScale() {
        val (length, measured) = fixedLength("Shoulder (12–14 in)", Gender.FEMALE, HairTexture.STRAIGHT)!!
        assertEquals(HairLength.MEDIUM, length)
        assertEquals(12f, measured!!.minIn, 0.001f)
        assertEquals(14f, measured.maxIn, 0.001f)

        assertEquals(HairLength.BALD, fixedLength("bald", Gender.MALE, null)!!.first)
        assertNull(fixedLength("Bald", Gender.MALE, null)!!.second)
        assertEquals(HairLength.MEDIUM, fixedLength("3–4 in", Gender.MALE, HairTexture.STRAIGHT)!!.first)
        assertNull(fixedLength("Shoulder (12–14 in)", Gender.MALE, HairTexture.STRAIGHT))
        assertEquals(lengthFixChoices(Gender.MALE).first(), "Bald")
    }

    @Test
    fun theImagePromptSaysWhereEachCutEnds() {
        val warrior = haircut("Modern Warrior Cut", HairTexture.STRAIGHT)
        val warriorLength = HairLengthScale.cutInstructions(warrior)
        assertTrue(warriorLength, warriorLength.contains("4–7 in on top"))
        assertTrue(warriorLength, warriorLength.contains("ears show fully"))

        val shag = HairLengthScale.cutInstructions(SampleData.allHaircuts.first { it.name == "Short Feathered Shag" })
        assertTrue(shag, shag.contains("ends at the chin"))
        val longLayers = HairLengthScale.cutInstructions(SampleData.allHaircuts.first { it.name == "Long Layers" })
        assertTrue(longLayers, longLayers.contains("between the collarbone and the middle of the back"))
        assertTrue(HairLengthScale.cutInstructions(haircut("Buzz Cut", HairTexture.STRAIGHT)).contains("all over"))
    }

    @Test
    fun aPreviewThatKeptTheOldLengthIsCaught() {
        val warrior = haircut("Modern Warrior Cut", HairTexture.STRAIGHT)
        // Hair hanging below the ears on a faded-sides cut.
        assertTrue(HairLengthScale.tooLongFor(warrior, MeasuredHairLength(8f, 11f, 9.5f, "overall"))!!.contains("below the ears"))
        assertNull(HairLengthScale.tooLongFor(warrior, MeasuredHairLength(4f, 6f, 5f, "on top")))

        val lob = SampleData.allHaircuts.first { it.name == "Sleek Lob" }
        assertTrue(HairLengthScale.tooLongFor(lob, MeasuredHairLength(19f, 23f, 21f, "overall"))!!.contains("12–16 in"))
        // Within the scan's own error of the cut's longest: not counted against it.
        assertNull(HairLengthScale.tooLongFor(lob, MeasuredHairLength(16.5f, 19f, 17.5f, "overall")))
    }

    private fun haircut(name: String, texture: HairTexture) = Haircut(
        id = name, name = name, imageUrl = "", length = HairLength.SHORT, texture = texture,
        recommendedFaceShapes = listOf(FaceShape.OVAL)
    )

    @Test
    fun scanLabelIsRedoneFromTheInches() {
        val scan = ScanResult(
            FaceShape.OVAL, HairLength.SHORT, HairTexture.STRAIGHT,
            measuredHairLength = MeasuredHairLength(3f, 4f, 3.5f, "on top")
        )
        assertEquals(HairLength.MEDIUM, scan.withLengthLabelFor(Gender.MALE).hairLength)
        assertEquals(HairLength.SHORT, scan.withLengthLabelFor(Gender.FEMALE).hairLength)
        val bald = ScanResult(FaceShape.OVAL, HairLength.BALD, HairTexture.STRAIGHT)
        assertEquals(HairLength.BALD, bald.withLengthLabelFor(Gender.MALE).hairLength)
    }

    private fun classification(
        topMm: Float? = null,
        fringeMm: Float? = null,
        fallMm: Float? = null,
        reachesFrameBottom: Boolean = false,
        flowMm: Float? = null
    ) = HairLengthClassification(
        HairLength.SHORT, 0.6f, 0.1f, 0f, reachesFrameBottom,
        mmPerPx = 0.5f, topMm = topMm, fringeMm = fringeMm, fallMm = fallMm, flowMm = flowMm
    )
}
