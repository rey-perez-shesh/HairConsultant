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
    /** Set for an uploaded photo whose head is turned or tilted too far for a reliable face-shape read. */
    val faceAngleWarning: String? = null,
    val analysisNote: String? = null
)
