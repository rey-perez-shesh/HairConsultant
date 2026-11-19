package com.hairconsultant.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

class HaircutLengthsTest {

    /** The live Firestore catalog's style names as of 2026-11-19 (from the admin dashboard). */
    private val liveCatalog = listOf(
        "Buzz Cut", "Curly Lob", "Curly Pixie", "Curtain Bangs Wavy Lob", "Long Curly Layers", "Long Layers",
        "Sleek Lob", "Wavy Pixie", "Curly Bob", "Curtains", "Layered Curly Shag", "Long Curly Shag",
        "Long Curtain Bangs", "Long Wavy Hair", "Soft Layered Waves", "V-cut Layers", "Wavy Bob with Side Bangs",
        "Beach Wave Crop", "Curly Curtain Bangs", "Curly Curtain Bangs Long", "Curtain Bangs Lob", "Long Wavy Shag",
        "Side-Swept Fringe Crop", "Wavy Lob with Side Part", "Beachy Wavy Shag", "Curly Wolf Cut", "Long Shag",
        "Short Feathered Shag", "V-Cut Wavy Layers", "Wavy Shag", "Curly Fringe Crop", "Face Framing Layered Cut",
        "Face-Framing Wavy Layers", "Long Defined Ringlets with Side Part", "Sleek Straight with Side Part",
        "Undercut Crop with Length on Top", "Wavy Face-Framing Layers", "Wavy Fringe Crop", "Crew Cut", "Curly Quiff",
        "Long Wavy Slick Back", "Textured Quiff", "Textured Side Part", "Classic Blunt Bob",
        "Curly Shoulder Length Layers", "Curly Undercut", "Long Bob", "Medium Layered", "Surfer Shag",
        "Textured Crop Medium Length", "Modern Mullet", "Modern Pompadour", "Shaggy Wolfcut", "Slicked Back",
        "Curtains with Layer", "Long Layered Side", "Side Bangs", "Undercut", "French Crop", "Textured Pixie",
        "Blunt Clavicut", "Butterfly cut", "Edgar cut", "Fohawk", "French Bob", "Modern Warrior Cut", "Quiff",
        "Side part with Slicked back", "Textured Crop"
    )

    @Test
    fun everyLiveAndBundledStyleHasAResearchedLength() {
        val missing = (liveCatalog + SampleData.allHaircuts.map { it.name }).filterNot(HaircutLengths::hasEntry)
        assertTrue("No length for: $missing", missing.isEmpty())
    }

    @Test
    fun everyRangeRunsShortToLong() {
        SampleData.allHaircuts.forEach { haircut ->
            val need = HaircutLengths.forHaircut(haircut)
            assertTrue(haircut.name, need.minIn in 0f..need.maxIn)
        }
    }
}
