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
    val measurements: Map<String, Float> = emptyMap()
)

/** Below these the scan's skin reading isn't stated as fact; undertone needs more, as the camera's colour balance shifts it. */
const val SKIN_TONE_SURE = 0.5f
const val UNDERTONE_SURE = 0.6f

/** The skin tone the scan was sure enough of to state, or null. Saved history keeps only these. */
val ScanResult.confidentSkinTone: SkinTone? get() = skinTone?.takeIf { skinToneConfidence >= SKIN_TONE_SURE }
val ScanResult.confidentUndertone: SkinUndertone? get() = skinUndertone?.takeIf { skinUndertoneConfidence >= UNDERTONE_SURE }
