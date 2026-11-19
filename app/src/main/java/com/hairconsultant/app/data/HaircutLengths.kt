package com.hairconsultant.app.data

import android.util.Log
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutGenderStyle
import com.hairconsultant.app.domain.model.cm
import com.hairconsultant.app.domain.model.inches

/**
 * How much hair each catalog style needs: the stretched length of its longest section, in inches,
 * as barbers and stylists quote it. Researched 2026-11-19 from barbering and stylist guides
 * (Wahl, Gentleman's Gazette, Fashionbeans, luxe.digital, Uppercut, John Frieda, L'Oréal, …).
 * Styles that sources describe by where they land rather than in inches (bobs, lobs, long cuts)
 * use the standard length chart: chin 8–10, shoulder 12–14, collarbone 14–16, armpit 16–18,
 * bra strap 18–20, mid-back 20–24 in. Curly styles defined by where they land are divided by
 * 0.75 for curl shrinkage, wavy ones by about 0.85.
 *
 * Keyed by style name (case-insensitive) because the catalog lives in Firestore and is edited from
 * the admin dashboard; a style missing here gets a default for its label and is logged once.
 */
object HaircutLengths {

    data class Need(val minIn: Float, val maxIn: Float, val section: String, val note: String = "") {
        /** E.g. "needs 3–5 in (8–13 cm) on top". */
        fun describe(): String =
            if (inches(minIn) == "0") "needs up to ${inches(maxIn)} in (${cm(maxIn)} cm) $section"
            else "needs ${inches(minIn)}–${inches(maxIn)} in (${cm(minIn)}–${cm(maxIn)} cm) $section"
    }

    private const val TOP = "on top"
    private const val OVERALL = "overall"

    private val table: Map<String, Need> = mapOf(
        // Short & Straight
        "Buzz Cut" to Need(0.1f, 0.5f, OVERALL, "clipper guard #1–#4 all over"),
        "Boy Cut" to Need(0.75f, 2f, TOP, "tapered sides"),
        "Curtains" to Need(3.5f, 6f, TOP, "front falls to the cheekbones"),
        "Side-Swept Fringe Crop" to Need(2f, 4f, TOP, "short sides"),
        "Short Feathered Shag" to Need(7f, 10f, OVERALL, "jaw-to-chin perimeter, 2–4 in crown layers"),
        "Undercut Crop with Length on Top" to Need(3f, 5f, TOP, "#1–#2 sides"),
        "Crew Cut" to Need(0.5f, 1.5f, TOP, "longest at the front, 0.25–0.5 in at the crown"),
        "Classic Blunt Bob" to Need(8f, 10f, OVERALL, "chin length"),
        "Modern Pompadour" to Need(3f, 5f, TOP, "up to 6 in for a high pomp; faded sides"),
        "Undercut" to Need(3f, 6f, TOP, "4–6 in to slick it back; #1–#2 sides"),
        "French Crop" to Need(1f, 3f, TOP, "fringe to mid-forehead or brow; faded sides"),
        "Classic" to Need(2f, 4f, TOP, "side part, tapered sides"),
        "Textured Pixie" to Need(2f, 4f, TOP, "nape and ears 1 in or less"),
        "Textured Crop" to Need(1f, 3f, TOP, "pushed forward; faded sides"),
        "Edgar Cut" to Need(1.5f, 3.5f, TOP, "blunt straight fringe; high fade"),
        "Fohawk" to Need(2f, 4f, TOP, "center strip; faded sides"),
        "Quiff" to Need(3f, 5f, TOP, "longest at the front; #2–#3 sides"),
        "Side Part with Slicked Back" to Need(3f, 5f, TOP, "tapered sides"),
        // Short & Wavy
        "Wavy Pixie" to Need(2f, 4f, TOP),
        "Wavy Bob with Side Bangs" to Need(9f, 12f, OVERALL, "lands at the chin once the waves dry"),
        "Beach Wave Crop" to Need(2f, 4f, TOP),
        "Wavy Shag" to Need(7f, 11f, OVERALL, "jaw-to-chin perimeter, 2–4 in crown layers"),
        "Wavy Fringe Crop" to Need(2f, 4f, TOP),
        "Textured Quiff" to Need(3f, 5f, TOP, "longest at the front; #2–#3 sides"),
        // Short & Curly
        "Curly Bixie" to Need(3f, 5f, TOP, "nape and sides 1–2 in"),
        "Curly Pixie" to Need(2f, 4f, TOP, "extra length allows for curl shrinkage"),
        "Curly Bob" to Need(10f, 14f, OVERALL, "lands at the chin once the curls dry"),
        "Wash and Go Twist Out Crop" to Need(2f, 4f, OVERALL),
        "Curly Wolf Cut" to Need(6f, 10f, OVERALL, "longest at the nape, 3–4 in crown layers"),
        "Curly Fringe Crop" to Need(2f, 4f, TOP),
        "Curly Crop Fade" to Need(2f, 4f, TOP, "about 3 in is typical; faded sides"),
        "High-Top Curl Fade" to Need(2.5f, 4f, TOP, "faded sides"),
        // Medium & Straight
        "Sleek Lob" to Need(12f, 16f, OVERALL, "shoulder to collarbone"),
        "V-cut Layers" to Need(12f, 16f, OVERALL, "V point past the shoulders"),
        "Curtain Bangs Lob" to Need(12f, 16f, OVERALL, "lob with 4–6 in curtain bangs"),
        "A-Line Bob" to Need(9f, 13f, OVERALL, "front pieces chin to collarbone, shorter back"),
        "Face Framing Layered Cut" to Need(12f, 15f, OVERALL, "shoulder length"),
        "Side Part Comb Over Medium" to Need(3f, 5f, TOP, "tapered sides"),
        "Textured Crop Medium Length" to Need(2.5f, 4f, TOP, "short sides"),
        "Slicked Back" to Need(4f, 6f, TOP),
        "Side Bangs" to Need(6f, 12f, OVERALL, "jaw length (masculine) to shoulder length (feminine)"),
        "French Bob" to Need(6f, 9f, OVERALL, "cheekbone to jaw, with a fringe"),
        "Blunt Clavicut" to Need(13f, 16f, OVERALL, "one length at the collarbone"),
        // Medium & Wavy
        "Curtain Bangs Wavy Lob" to Need(13f, 18f, OVERALL, "lands at the shoulders once the waves dry"),
        "Soft Layered Waves" to Need(13f, 18f, OVERALL, "shoulder length"),
        "Wavy Lob with Side Part" to Need(13f, 18f, OVERALL, "shoulder length"),
        "Beachy Wavy Shag" to Need(4f, 8f, TOP, "top and crown"),
        "Wavy Face-Framing Layers" to Need(13f, 18f, OVERALL, "shoulder length"),
        "Textured Side Part" to Need(3f, 5f, TOP),
        "Medium Layered" to Need(6f, 12f, OVERALL, "jaw to shoulder"),
        "Medium Modern Mullet" to Need(4f, 7f, OVERALL, "longest at the back; 2–4 in top; #2–#3 sides"),
        "Modern Mullet" to Need(4f, 7f, OVERALL, "longest at the back; 2–4 in top; #2–#3 sides"),
        "Curtains with Layer" to Need(4f, 8f, OVERALL, "front and layers to the ears or jaw"),
        "Modern Warrior Cut" to Need(4f, 7f, TOP, "long enough to tie half up; faded sides"),
        // Medium & Curly
        "Curly Lob" to Need(15f, 19f, OVERALL, "lands at the shoulders once the curls dry"),
        "Layered Curly Shag" to Need(10f, 16f, OVERALL, "chin to shoulder"),
        "Curly Curtain Bangs" to Need(14f, 18f, OVERALL, "shoulder length"),
        "Devacut" to Need(8f, 16f, OVERALL, "can be cut at most lengths"),
        "Curly V-Cut Layers" to Need(16f, 20f, OVERALL, "V point past the shoulders"),
        "Curly Quiff" to Need(3f, 5f, TOP),
        "Curly Undercut with Length on Top" to Need(3f, 6f, TOP, "#1–#2 sides"),
        "Curly Undercut" to Need(3f, 6f, TOP, "#1–#2 sides"),
        // Long & Straight
        "Long Layers" to Need(15f, 22f, OVERALL, "past the shoulders"),
        "Long Curtain Bangs" to Need(14f, 20f, OVERALL, "4–7 in curtain bangs"),
        "Long Blunt" to Need(15f, 22f, OVERALL, "one length"),
        "Long Shag" to Need(12f, 18f, OVERALL, "shoulder to collarbone and past"),
        "Sleek Straight with Side Part" to Need(9f, 14f, OVERALL, "jaw to shoulder"),
        "Long Slicked Back" to Need(6f, 10f, TOP, "reaches the nape"),
        "Long Bob" to Need(12f, 16f, OVERALL, "collarbone length"),
        "Butterfly Cut" to Need(13f, 20f, OVERALL, "shoulder length or longer; shortest layers 2–3 in below the chin"),
        // Long & Wavy
        "Wavy Lob-to-Long Curtain Fringe" to Need(14f, 20f, OVERALL, "collarbone to armpit"),
        "Long Wavy Hair" to Need(18f, 24f, OVERALL, "bra strap and longer"),
        "Long Wavy Shag" to Need(13f, 20f, OVERALL, "shoulder and longer"),
        "V-Cut Wavy Layers" to Need(16f, 22f, OVERALL, "V point"),
        "Face-Framing Wavy Layers" to Need(14f, 20f, OVERALL, "past the shoulders"),
        "Long Wavy Slick Back" to Need(6f, 10f, TOP),
        "Surfer Shag" to Need(6f, 12f, TOP, "top and crown, falls to the jaw"),
        "Shaggy Wolfcut" to Need(10f, 16f, OVERALL, "longest at the back, 3–5 in crown layers"),
        "Long Layered Side" to Need(16f, 22f, OVERALL, "past the shoulders"),
        // Long & Curly
        "Long Curly Layers" to Need(18f, 26f, OVERALL, "past the shoulders once the curls dry"),
        "Long Curly Shag" to Need(14f, 20f, OVERALL, "shoulder and longer"),
        "Curly Curtain Bangs Long" to Need(18f, 24f, OVERALL),
        "Long Curly V-Cut" to Need(18f, 26f, OVERALL),
        "Long Defined Ringlets with Side Part" to Need(18f, 26f, OVERALL),
        "Long Curly Man Bun" to Need(8f, 16f, OVERALL, "6 in is the least that ties into a knot"),
        "Curly Shoulder Length Layers" to Need(12f, 18f, OVERALL, "lands at the shoulders once the curls dry")
    ).mapKeys { it.key.lowercase() }

    private val warned = mutableSetOf<String>()

    /** What [haircut] needs; a style not in the table gets its label's default for its gender style. */
    fun forHaircut(haircut: Haircut): Need {
        table[haircut.name.trim().lowercase()]?.let { return it }
        if (warned.add(haircut.name)) Log.w(TAG, "No researched length for \"${haircut.name}\"; using its label's default")
        val masculine = haircut.genderStyle == HaircutGenderStyle.MASCULINE
        return when (haircut.length) {
            HairLength.BALD -> Need(0f, 0.5f, OVERALL)
            HairLength.SHORT -> if (masculine) Need(0.5f, 2f, TOP) else Need(2f, 9f, OVERALL)
            HairLength.MEDIUM -> if (masculine) Need(2f, 5f, TOP) else Need(9f, 14f, OVERALL)
            HairLength.LONG -> if (masculine) Need(5f, 12f, OVERALL) else Need(14f, 22f, OVERALL)
        }
    }

    /** Whether [name] has a researched entry, for the test that every catalog style is covered. */
    internal fun hasEntry(name: String): Boolean = name.trim().lowercase() in table

    private const val TAG = "HaircutLengths"
}
