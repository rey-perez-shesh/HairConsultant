package com.hairconsultant.app.data.analysis

import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.ScanResult
import kotlinx.coroutines.delay

/**
 * Face-shape / hair-length / hair-texture detection. [analyzeCameraFrame] and [analyzeImage] are
 * placeholders — swap the body for a real ML Kit Face Detection pass plus a custom hair
 * length/texture classifier (e.g. a TFLite model bundled in assets/) once one is trained.
 */
interface FaceAnalyzer {
    suspend fun analyzeCameraFrame(): ScanResult
    suspend fun analyzeImage(imageUri: android.net.Uri): ScanResult
}

class MockFaceAnalyzer : FaceAnalyzer {
    override suspend fun analyzeCameraFrame(): ScanResult {
        delay(1200) // simulate on-device inference latency
        return randomResult()
    }

    override suspend fun analyzeImage(imageUri: android.net.Uri): ScanResult {
        delay(1200)
        return randomResult()
    }

    private fun randomResult() = ScanResult(
        faceShape = FaceShape.entries.random(),
        hairLength = HairLength.entries.random(),
        hairTexture = HairTexture.entries.random(),
        faceShapeConfidence = (70..97).random() / 100f,
        hairLengthConfidence = (70..97).random() / 100f,
        hairTextureConfidence = (70..97).random() / 100f
    )
}
