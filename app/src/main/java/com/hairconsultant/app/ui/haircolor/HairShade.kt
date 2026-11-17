package com.hairconsultant.app.ui.haircolor

enum class ShadeFamily(val label: String) {
    NATURAL("Natural"),
    FASHION("Fashion")
}

/**
 * One dye shade for the user's own hair (the wig try-on has its own flat presets in
 * [com.hairconsultant.app.ui.facescan.HairColorPreset]).
 *
 * @param argb the shade's mid-tone, as it looks on an evenly lit strand; also the swatch color.
 * @param contrastGain how much the broad light/dark shape is stretched when lightening. Dark hair
 *   has little contrast to begin with, so light shades need more than 1 to not look like a flat cap.
 * @param maxLift the most the hair's average OKLab lightness may be raised. Caps how far black hair
 *   is pushed toward a pale shade, which is where recolors stop being believable.
 */
data class HairShade(
    val id: String,
    val name: String,
    val family: ShadeFamily,
    val argb: Int,
    val contrastGain: Float = 1f,
    val maxLift: Float = 0.4f
) {
    private val lab = HairColorMath.oklabOf(argb)
    val l: Float get() = lab[0]
    val a: Float get() = lab[1]
    val b: Float get() = lab[2]
}

object HairShades {
    val all: List<HairShade> = listOf(
        HairShade("jet_black", "Jet Black", ShadeFamily.NATURAL, 0xFF0F0E0E.toInt()),
        HairShade("soft_black", "Soft Black", ShadeFamily.NATURAL, 0xFF2A2422.toInt()),
        HairShade("dark_brown", "Dark Brown", ShadeFamily.NATURAL, 0xFF3B2A20.toInt(), contrastGain = 1.1f),
        HairShade("chestnut", "Chestnut", ShadeFamily.NATURAL, 0xFF5A3825.toInt(), contrastGain = 1.15f),
        HairShade("light_brown", "Light Brown", ShadeFamily.NATURAL, 0xFF8A5E3C.toInt(), contrastGain = 1.25f),
        HairShade("auburn", "Auburn", ShadeFamily.NATURAL, 0xFF7A2E1E.toInt(), contrastGain = 1.15f),
        HairShade("copper", "Copper", ShadeFamily.NATURAL, 0xFFB5562B.toInt(), contrastGain = 1.3f),
        HairShade("dark_blonde", "Dark Blonde", ShadeFamily.NATURAL, 0xFFA58456.toInt(), contrastGain = 1.35f, maxLift = 0.45f),
        HairShade("golden_blonde", "Golden Blonde", ShadeFamily.NATURAL, 0xFFD2A862.toInt(), contrastGain = 1.45f, maxLift = 0.48f),
        HairShade("platinum", "Platinum", ShadeFamily.NATURAL, 0xFFE6DCC8.toInt(), contrastGain = 1.55f, maxLift = 0.5f),

        HairShade("silver", "Silver", ShadeFamily.FASHION, 0xFFC4C7CC.toInt(), contrastGain = 1.5f, maxLift = 0.48f),
        HairShade("pastel_pink", "Pastel Pink", ShadeFamily.FASHION, 0xFFF0A9C0.toInt(), contrastGain = 1.5f, maxLift = 0.48f),
        HairShade("rose", "Rose", ShadeFamily.FASHION, 0xFFC2416B.toInt(), contrastGain = 1.3f),
        HairShade("blue", "Blue", ShadeFamily.FASHION, 0xFF2F5FD0.toInt(), contrastGain = 1.25f),
        HairShade("purple", "Purple", ShadeFamily.FASHION, 0xFF7B3FB5.toInt(), contrastGain = 1.25f),
        HairShade("green", "Green", ShadeFamily.FASHION, 0xFF2E9E6B.toInt(), contrastGain = 1.3f)
    )

    fun of(family: ShadeFamily): List<HairShade> = all.filter { it.family == family }
    fun byId(id: String?): HairShade? = all.firstOrNull { it.id == id }
}
