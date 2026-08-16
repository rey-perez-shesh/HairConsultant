package com.hairconsultant.app.domain.model

/** Output of the face/hair analysis, either from the live camera scan or an uploaded photo. */
data class ScanResult(
    val faceShape: FaceShape,
    val hairLength: HairLength,
    val hairTexture: HairTexture,
    val faceShapeConfidence: Float = 0f,
    val hairLengthConfidence: Float = 0f,
    val hairTextureConfidence: Float = 0f
)
