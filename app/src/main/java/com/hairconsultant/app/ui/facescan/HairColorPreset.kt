package com.hairconsultant.app.ui.facescan

/**
 * Try-on hair color presets applied to Filament glTF `baseColorFactor` (sRGB).
 * Shape / tracking / scale are unchanged — only the hair albedo color updates.
 */
enum class HairColorPreset(
    val label: String,
    val r: Float,
    val g: Float,
    val b: Float
) {
    /** Restores authored baseColorTexture. */
    NATURAL("Natural", 1f, 1f, 1f),
    BLACK("Black", 0.08f, 0.07f, 0.07f),
    DARK_BROWN("Dark Brown", 0.28f, 0.15f, 0.08f),
    LIGHT_BROWN("Light Brown", 0.62f, 0.40f, 0.22f),
    BLONDE("Blonde", 0.95f, 0.82f, 0.52f),
    RED("Red", 0.78f, 0.14f, 0.10f),
    PURPLE("Purple", 0.48f, 0.18f, 0.68f),
    BLUE("Blue", 0.16f, 0.34f, 0.82f);

    companion object {
        val TRY_ON_OPTIONS: List<HairColorPreset> = entries
    }
}
