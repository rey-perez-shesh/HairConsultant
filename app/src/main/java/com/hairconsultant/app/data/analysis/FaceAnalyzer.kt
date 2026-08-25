package com.hairconsultant.app.data.analysis

import android.content.Context
import android.net.Uri
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.ScanResult
import kotlinx.coroutines.delay

class NoFaceDetectedException(message: String = "No face detected") : Exception(message)

/**
 * Face-shape / hair-length / hair-texture / hair-color detection.
 *
 * Live camera: MediaPipe Face Landmarker publishes the mesh continuously; [analyzeCameraFrame]
 * classifies that mesh, uses the live hair mask when it is fresh, and asks ML Kit Face Mesh for a
 * second opinion on shape.
 * Photos: MediaPipe IMAGE mode + Hair Segmenter + the same ML Kit check.
 */
interface FaceAnalyzer {
    suspend fun analyzeCameraFrame(): ScanResult
    suspend fun analyzeImage(imageUri: Uri): ScanResult
}

class MockFaceAnalyzer : FaceAnalyzer {
    override suspend fun analyzeCameraFrame(): ScanResult {
        delay(1200)
        return randomResult()
    }

    override suspend fun analyzeImage(imageUri: Uri): ScanResult {
        delay(1200)
        return randomResult()
    }

    private fun randomResult() = ScanResult(
        faceShape = FaceShape.entries.random(),
        hairLength = HairLength.entries.random(),
        hairTexture = HairTexture.entries.random(),
        hairColor = HairColor.entries.random(),
        faceShapeConfidence = (70..97).random() / 100f,
        hairLengthConfidence = (70..97).random() / 100f,
        hairTextureConfidence = (70..97).random() / 100f,
        hairColorConfidence = (70..97).random() / 100f
    )
}

class LandmarkFaceAnalyzer(
    private val context: Context,
    private val store: FaceLandmarkStore,
    private val stillLandmarker: StillImageFaceLandmarker,
    private val mlKitVerifier: MlKitFaceMeshVerifier,
    private val stillHairSegmenter: StillImageHairSegmenter,
    private val faceShapeCnn: FaceShapeTfliteClassifier,
    private val hairTypeCnn: HairTypeTfliteClassifier
) : FaceAnalyzer {

    override suspend fun analyzeCameraFrame(): ScanResult {
        val frame = run {
            repeat(20) {
                store.snapshot()?.let { return@run it }
                delay(100)
            }
            null
        } ?: throw NoFaceDetectedException(
            "Hold still and center your face in the outline, then tap Scan."
        )
        return try {
            classify(
                points = frame.points,
                width = frame.imageWidth,
                height = frame.imageHeight,
                bitmapForVerifier = frame.bitmap,
                liveHair = frame.hairMask
            )
        } finally {
            frame.bitmap?.recycle()
        }
    }

    override suspend fun analyzeImage(imageUri: Uri): ScanResult {
        val bitmap = ScanBitmapLoader.fromUri(context, imageUri)
        try {
            val points = stillLandmarker.detect(bitmap)
                ?: throw NoFaceDetectedException("I couldn't find a face in that photo. Try a clearer front-facing shot.")
            return classify(points, bitmap.width, bitmap.height, bitmap, liveHair = null)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun classify(
        points: List<LandmarkPoint>,
        width: Int,
        height: Int,
        bitmapForVerifier: android.graphics.Bitmap?,
        liveHair: HairMask?
    ): ScanResult {
        val heuristicShape = FaceShapeClassifier.classify(points, width, height)
            ?: throw NoFaceDetectedException("I found a face but couldn't measure its shape. Move a bit closer and try again.")
        // faceShapeCnn takes the same landmark ratios the heuristic just computed, not pixels, so
        // it can run whenever a face was measured at all (no bitmap needed). It only knows
        // Heart/Oval/Round/Square (no usable training data for Diamond), so a low- or
        // no-confidence CNN result falls back to the geometric heuristic, which is the only path
        // that can still surface Diamond.
        val cnnShape = faceShapeCnn.classify(heuristicShape.metrics)
        val useCnnShape = cnnShape != null && cnnShape.confidence >= CNN_CONFIDENCE_FLOOR
        val resolvedShape = if (useCnnShape) cnnShape!!.shape else heuristicShape.shape
        val resolvedShapeConfidence = if (useCnnShape) cnnShape!!.confidence else heuristicShape.confidence

        val verifier = bitmapForVerifier?.let { mlKitVerifier.classify(it) }
        val agreed = verifier == null || verifier.shape == resolvedShape
        val hairMask = liveHair ?: bitmapForVerifier?.let { stillHairSegmenter.segment(it) }
        val hair = hairMask?.let { HairLengthClassifier.classify(it, points) }
        val isBald = hair?.length == HairLength.BALD || hair == null
        val heuristicAppearance = if (bitmapForVerifier != null && hairMask != null && !isBald) {
            HairAppearanceClassifier.classify(bitmapForVerifier, hairMask)
        } else {
            null
        }
        val cnnTexture = if (bitmapForVerifier != null && hairMask != null && !isBald) {
            hairTypeCnn.classify(bitmapForVerifier, hairMask)
        } else {
            null
        }
        val appearance = if (cnnTexture != null && cnnTexture.confidence >= CNN_CONFIDENCE_FLOOR) {
            heuristicAppearance?.copy(texture = cnnTexture.texture, textureConfidence = cnnTexture.confidence)
        } else {
            heuristicAppearance
        }
        val shapeNote = when {
            verifier == null -> null
            agreed -> "MediaPipe and ML Kit both measured ${resolvedShape.displayName}."
            else -> "Guide mesh suggested ${resolvedShape.displayName}; ML Kit suggested ${verifier.shape.displayName}."
        }
        val hairNote = buildHairNote(hair, appearance)
        return ScanResult(
            faceShape = resolvedShape,
            hairLength = hair?.length ?: HairLength.BALD,
            hairTexture = appearance?.texture ?: HairTexture.STRAIGHT,
            hairColor = appearance?.color ?: HairColor.OTHER,
            faceShapeConfidence = if (agreed && verifier != null) {
                ((resolvedShapeConfidence + verifier.confidence) / 2f).coerceAtMost(0.95f)
            } else {
                resolvedShapeConfidence.coerceAtMost(if (useCnnShape) 0.9f else 0.7f)
            },
            hairLengthConfidence = hair?.confidence ?: 0.35f,
            hairTextureConfidence = if (isBald) 0f else (appearance?.textureConfidence ?: 0.35f),
            hairColorConfidence = if (isBald) 0f else (appearance?.colorConfidence ?: 0.35f),
            verifierFaceShape = verifier?.shape,
            sourcesAgreed = agreed,
            analysisNote = listOfNotNull(shapeNote).joinToString(" ").ifBlank { null }?.plus(hairNote)
                ?: hairNote.trim()
        )
    }

    companion object {
        /** Softmax confidence a CNN result needs to be trusted over the heuristic fallback. */
        private const val CNN_CONFIDENCE_FLOOR = 0.5f
    }

    private fun buildHairNote(
        hair: HairLengthClassification?,
        appearance: HairAppearanceClassification?
    ): String {
        if (hair?.length == HairLength.BALD) {
            return " Hair mask suggests bald or very little hair."
        }
        val parts = buildList {
            hair?.let { add("${it.length.displayName.lowercase()} length") }
            appearance?.let {
                add("${it.texture.displayName.lowercase()} texture")
                if (it.color != HairColor.OTHER) add("${it.color.displayName.lowercase()} color")
            }
        }
        return if (parts.isEmpty()) {
            " Hair details are a best guess — please confirm."
        } else {
            " Hair mask suggests ${parts.joinToString(", ")}."
        }
    }
}
