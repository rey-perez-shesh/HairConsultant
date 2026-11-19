package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.data.HaircutLengths
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.MeasuredHairLength
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.inches

/**
 * Inches and the Short / Medium / Long labels, which mean different lengths for men and women.
 *  - Men's scale goes by length on top: Short under 2 in, Medium 2–5 in, Long over 5 in.
 *  - The where-it-falls scale (women, and everyone without a male/female profile) goes by where
 *    the hair lands: Short up to chin length, Medium chin to shoulder, Long past the shoulder.
 *    Hairfinder counts chin length as short; on the straight-hair chart chin is 8–10 in and the
 *    shoulder 12–14 in.
 */
object HairLengthScale {

    /** One Fix-menu choice: how long the hair looks, in inches. */
    data class Option(val label: String, val minIn: Float, val maxIn: Float, val section: String, val atLeast: Boolean = false)

    private const val MENS_SHORT_UNDER = 2f
    private const val MENS_MEDIUM_UP_TO = 5f
    private const val FALLS_SHORT_UNDER = 10f
    private const val FALLS_MEDIUM_UP_TO = 14f

    private const val MM_PER_INCH = 25.4f
    /** The iris scale's error (MediaPipe Iris: 4.3% mean), with some margin. */
    private const val SCALE_ERROR = 0.08f
    /** How far off the method itself can be: tighter for hair on top, looser for hair hanging from the crown. */
    private const val SHORT_ERROR_IN = 0.5f
    private const val FALL_ERROR_IN = 1f
    /** Traced strands wander and only show the part facing the camera, so they get hanging hair's wider margin. */
    private const val FLOW_ERROR_IN = 1f
    private const val SHORTEST_IN = 0.1f

    private const val TOP = "on top"
    private const val FRINGE = "at the fringe"
    private const val OVERALL = "overall"

    /** The label [inches] reads as on [gender]'s scale. */
    fun label(inches: Float, gender: Gender?): HairLength = if (gender == Gender.MALE) {
        when {
            inches < MENS_SHORT_UNDER -> HairLength.SHORT
            inches <= MENS_MEDIUM_UP_TO -> HairLength.MEDIUM
            else -> HairLength.LONG
        }
    } else {
        when {
            inches < FALLS_SHORT_UNDER -> HairLength.SHORT
            inches <= FALLS_MEDIUM_UP_TO -> HairLength.MEDIUM
            else -> HairLength.LONG
        }
    }

    /** The user's label: men's scale by the stretched length, where-it-falls by how long it looks. */
    fun label(measured: MeasuredHairLength, gender: Gender?): HairLength =
        label(if (gender == Gender.MALE) (measured.minIn + measured.maxIn) / 2f else measured.visibleIn, gender)

    /** A catalog style's label on [gender]'s scale: men's by its inches, where-it-falls by where it lands once curls shrink. */
    fun label(haircut: Haircut, gender: Gender?): HairLength {
        val need = HaircutLengths.forHaircut(haircut)
        val middle = (need.minIn + need.maxIn) / 2f
        return label(if (gender == Gender.MALE) middle else middle * (1f - typicalShrinkage(haircut.texture)), gender)
    }

    /** "men's standard" or "where-it-falls standard", for saying which scale a label is on. */
    fun scaleName(gender: Gender?): String =
        if (gender == Gender.MALE) "men's standard (length on top)" else "where it falls (chin, shoulder)"

    /**
     * The scan's reading in inches: the longest of the top, fringe, hanging and traced-strand
     * lengths. Null when none was measured.
     */
    fun measure(reading: HairLengthClassification, texture: HairTexture?): MeasuredHairLength? {
        val (mm, section, methodError) = listOfNotNull(
            reading.topMm?.let { Triple(it, TOP, SHORT_ERROR_IN) },
            reading.fringeMm?.let { Triple(it, FRINGE, SHORT_ERROR_IN) },
            reading.fallMm?.let { Triple(it, OVERALL, FALL_ERROR_IN) },
            reading.flowMm?.let { Triple(it, TOP, FLOW_ERROR_IN) }
        ).maxByOrNull { it.first } ?: return null
        val visible = mm / MM_PER_INCH
        return fromVisible(
            minIn = visible * (1f - SCALE_ERROR) - methodError,
            maxIn = visible * (1f + SCALE_ERROR) + methodError,
            visibleIn = visible,
            texture = texture,
            section = section,
            atLeast = reading.reachesFrameBottom && section == OVERALL
        )
    }

    /** A Fix-menu choice as a length, stretched for the user's texture the same way a scan is. */
    fun fromOption(option: Option, texture: HairTexture?): MeasuredHairLength =
        fromVisible(option.minIn, option.maxIn, (option.minIn + option.maxIn) / 2f, texture, option.section, option.atLeast)

    /** Curls and waves look shorter than they are: the stretched range allows for the least and most shrinkage. */
    private fun fromVisible(
        minIn: Float,
        maxIn: Float,
        visibleIn: Float,
        texture: HairTexture?,
        section: String,
        atLeast: Boolean
    ): MeasuredHairLength {
        val (least, most) = shrinkage(texture)
        return MeasuredHairLength(
            minIn = (minIn / (1f - least)).coerceAtLeast(SHORTEST_IN),
            maxIn = (maxIn / (1f - most)).coerceAtLeast(SHORTEST_IN),
            visibleIn = visibleIn,
            section = section,
            atLeast = atLeast
        )
    }

    /** The Fix menu's length choices (besides Bald) on [gender]'s scale. */
    fun fixOptions(gender: Gender?): List<Option> = if (gender == Gender.MALE) {
        listOf(
            Option("Under 1 in", SHORTEST_IN, 1f, TOP),
            Option("1–2 in", 1f, 2f, TOP),
            Option("2–3 in", 2f, 3f, TOP),
            Option("3–4 in", 3f, 4f, TOP),
            Option("4–6 in", 4f, 6f, TOP),
            Option("6–10 in", 6f, 10f, OVERALL),
            Option("Past the chin", 10f, 14f, OVERALL, atLeast = true)
        )
    } else {
        listOf(
            Option("Pixie (under 4 in)", 1f, 4f, TOP),
            Option("Ear length (4–7 in)", 4f, 7f, OVERALL),
            Option("Chin (8–10 in)", 8f, 10f, OVERALL),
            Option("Shoulder (12–14 in)", 12f, 14f, OVERALL),
            Option("Past shoulders (14–18 in)", 14f, 18f, OVERALL),
            Option("Mid-back (18 in+)", 18f, 24f, OVERALL, atLeast = true)
        )
    }

    /**
     * Where [haircut] ends on the head and body, for the image prompt: given a picture alone, image
     * models keep the person's own length and only restyle the top, so the cut is spelled out in
     * landmarks they can see (ears, chin, shoulders) from the researched [HaircutLengths].
     */
    fun cutInstructions(haircut: Haircut): String {
        val need = HaircutLengths.forHaircut(haircut)
        val note = need.note.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return when {
            hasShortSides(need) -> "Length: about ${span(need)} on top$note. The sides and back are short: the ears " +
                "show fully, and no hair hangs below the ears or onto the neck."
            need.section == TOP -> "Length: about ${span(need)} on top$note."
            need.maxIn < ALL_OVER_SHORT_IN -> "Length: about ${span(need)} all over$note, so the ears show fully and no " +
                "hair reaches the neck."
            else -> {
                val shrink = 1f - typicalShrinkage(haircut.texture)
                val shortest = landmark(need.minIn * shrink)
                val longest = landmark(need.maxIn * shrink)
                val where = if (shortest == longest) "at $shortest" else "between $shortest and $longest"
                "Length: the longest hair ends $where$note."
            }
        }
    }

    /**
     * Why a preview's hair, [measured] on the generated picture, is too long for [haircut], or null
     * when it fits or can't be told. Short-sided cuts must not hang below the ears at all; other
     * cuts get [LENGTH_TOLERANCE_IN] past their longest before the measurement counts against them.
     */
    fun tooLongFor(haircut: Haircut, measured: MeasuredHairLength): String? {
        val need = HaircutLengths.forHaircut(haircut)
        return when {
            hasShortSides(need) && measured.section == OVERALL -> "the hair still hangs below the ears"
            need.section == OVERALL && need.maxIn >= ALL_OVER_SHORT_IN && measured.minIn > need.maxIn + LENGTH_TOLERANCE_IN ->
                "the hair looks about ${inches(measured.visibleIn)} in long, and the cut is ${span(need)}"
            else -> null
        }
    }

    /** A cut measured on top whose sides and back are short: up to 5 in, or the notes say so (faded, tapered, clipper sides). */
    private fun hasShortSides(need: HaircutLengths.Need): Boolean {
        if (need.section != TOP) return false
        val note = need.note.lowercase()
        return need.maxIn <= SHORT_SIDES_MAX_TOP_IN || SHORT_SIDE_WORDS.any { it in note }
    }

    private fun span(need: HaircutLengths.Need): String =
        if (inches(need.minIn) == "0") "up to ${inches(need.maxIn)} in" else "${inches(need.minIn)}–${inches(need.maxIn)} in"

    /** Where straight hair this long lands (the standard length chart: chin 8–10 in, shoulder 12–14 in). */
    private fun landmark(inches: Float): String = when {
        inches < 7f -> "the ears"
        inches < 10.5f -> "the chin"
        inches < 12f -> "the neck"
        inches < 14.5f -> "the shoulders"
        inches < 16.5f -> "the collarbone"
        inches < 18.5f -> "the armpits"
        else -> "the middle of the back"
    }

    private const val SHORT_SIDES_MAX_TOP_IN = 5f
    private val SHORT_SIDE_WORDS = listOf("fade", "taper", "#", "short sides", "nape and ears", "nape and sides")
    /** Below this, a cut measured "overall" is short all over (a buzz cut, a twist-out crop). */
    private const val ALL_OVER_SHORT_IN = 4f
    /** How far past a cut's longest a preview may measure before it counts as too long: the scan's own error. */
    private const val LENGTH_TOLERANCE_IN = 1f

    /** Least and most share of the stretched length curls hide (Red Seal cutting guide: wavy 10–25%, curly 25–50%). */
    private fun shrinkage(texture: HairTexture?): Pair<Float, Float> = when (texture) {
        HairTexture.WAVY -> 0.10f to 0.25f
        HairTexture.CURLY -> 0.25f to 0.50f
        HairTexture.STRAIGHT, null -> 0f to 0f
    }

    /** The shrinkage the catalog table assumed when it turned where a style lands into inches. */
    private fun typicalShrinkage(texture: HairTexture): Float = when (texture) {
        HairTexture.STRAIGHT -> 0f
        HairTexture.WAVY -> 0.15f
        HairTexture.CURLY -> 0.25f
    }
}

/** The Fix menu's hair-length chips for [gender]: Bald, then [HairLengthScale.fixOptions]. */
fun lengthFixChoices(gender: Gender?): List<String> =
    listOf(HairLength.BALD.displayName) + HairLengthScale.fixOptions(gender).map { it.label }

/** The label and length a [lengthFixChoices] chip stands for (Bald has no length), or null when [text] isn't one. */
fun fixedLength(text: String, gender: Gender?, texture: HairTexture?): Pair<HairLength, MeasuredHairLength?>? {
    if (text.equals(HairLength.BALD.displayName, ignoreCase = true)) return HairLength.BALD to null
    val option = HairLengthScale.fixOptions(gender).firstOrNull { it.label.equals(text, ignoreCase = true) } ?: return null
    val measured = HairLengthScale.fromOption(option, texture)
    return HairLengthScale.label(measured, gender) to measured
}

/** This scan with its length label worked out from the inches on [gender]'s scale; unchanged when nothing was measured. */
fun ScanResult.withLengthLabelFor(gender: Gender?): ScanResult =
    measuredHairLength?.let { copy(hairLength = HairLengthScale.label(it, gender)) } ?: this
