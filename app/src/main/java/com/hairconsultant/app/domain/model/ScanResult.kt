package com.hairconsultant.app.domain.model

/** Output of the face/hair analysis, either from the live camera scan or an uploaded photo. */
data class ScanResult(
    val faceShape: FaceShape,
    val hairLength: HairLength,
    val hairTexture: HairTexture,
    val hairColor: HairColor = HairColor.OTHER,
    val faceShapeConfidence: Float = 0f,
    val hairLengthConfidence: Float = 0f,
    val hairTextureConfidence: Float = 0f,
    val hairColorConfidence: Float = 0f,
    val verifierFaceShape: FaceShape? = null,
    val sourcesAgreed: Boolean = true,
    val analysisNote: String? = null,
    /** Null when the scan couldn't read the skin (e.g. no usable cheek or forehead pixels). */
    val skinTone: SkinTone? = null,
    val skinUndertone: SkinUndertone? = null,
    val skinToneConfidence: Float = 0f,
    val skinUndertoneConfidence: Float = 0f,
    /**
     * The raw measurements behind the result (face ratios, jaw angle, chin and forehead width,
     * hairline, skin ITA and hue), sent with the consultation so future thresholds and model
     * retraining can be calibrated against the shapes users confirm.
     */
    val measurements: Map<String, Float> = emptyMap(),
    /** The hair's longest section in inches; null when bald or it couldn't be measured. */
    val measuredHairLength: MeasuredHairLength? = null
)

/**
 * Hair length as a stretched strand of the longest section, the way barbers quote it ("3–5 in on
 * top"). [minIn]..[maxIn] already allow for measurement error and curl shrinkage; [visibleIn] is
 * how long it looks as worn, which the where-it-falls label scale reads.
 */
data class MeasuredHairLength(
    val minIn: Float,
    val maxIn: Float,
    val visibleIn: Float,
    /** "on top", "at the fringe" or "overall". */
    val section: String,
    /** The hair ran past the bottom of the picture, so this is a floor. */
    val atLeast: Boolean = false
) {
    /** E.g. "about 3–4 in (8–10 cm) on top", or "at least 16 in (41 cm) overall". */
    fun describe(): String = when {
        atLeast -> "at least ${inches(minIn)} in (${cm(minIn)} cm) $section"
        inches(minIn) == "0" -> "under ${inches(maxIn)} in (${cm(maxIn)} cm) $section"
        inches(minIn) == inches(maxIn) -> "about ${inches(minIn)} in (${cm(minIn)} cm) $section"
        else -> "about ${inches(minIn)}–${inches(maxIn)} in (${cm(minIn)}–${cm(maxIn)} cm) $section"
    }
}

/** Half-inch steps under 6 in, whole inches above: finer than that would claim more precision than there is. */
internal fun inches(value: Float): String {
    val rounded = if (value < 6f) Math.round(value * 2f) / 2f else Math.round(value).toFloat()
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

internal fun cm(inches: Float): Int = Math.round(inches * 2.54f)

/** Below these the scan's skin reading isn't stated as fact; undertone needs more, as the camera's colour balance shifts it. */
const val SKIN_TONE_SURE = 0.5f
const val UNDERTONE_SURE = 0.6f

/** The skin tone the scan was sure enough of to state, or null. Saved history keeps only these. */
val ScanResult.confidentSkinTone: SkinTone? get() = skinTone?.takeIf { skinToneConfidence >= SKIN_TONE_SURE }
val ScanResult.confidentUndertone: SkinUndertone? get() = skinUndertone?.takeIf { skinUndertoneConfidence >= UNDERTONE_SURE }
