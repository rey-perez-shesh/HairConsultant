package com.hairconsultant.app.data.analysis

import android.content.Context
import android.net.Uri
import android.os.SystemClock
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

    /**
     * Measures the face over the frames that arrive in the next few seconds instead of a single
     * one: only frames where the head faces the camera count, and their ratios are combined by
     * median (see [FaceAlignment]). Stops early once [FaceAlignment.TARGET_FRAMES] usable frames
     * are in, so a steady user waits well under a second at typical frame rates.
     */
    override suspend fun analyzeCameraFrame(): ScanResult {
        val scanStartedAt = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - scanStartedAt < SCAN_WINDOW_MS) {
            val usable = store.samplesSince(scanStartedAt).count { FaceAlignment.isFacingCamera(it.angles) }
            if (usable >= FaceAlignment.TARGET_FRAMES) break
            delay(100)
        }
        val samples = store.samplesSince(scanStartedAt)
        val measured = FaceAlignment.aggregate(samples)
            ?: throw NoFaceDetectedException(
                when {
                    samples.isEmpty() -> "Hold still and center your face in the outline, then tap Scan."
                    else -> FaceAlignment.adjustmentHint(store.latestAngles())?.let { "$it Then tap Scan again." }
                        ?: "Hold still and face the camera for a moment, then tap Scan again."
                }
            )
        android.util.Log.d(
            TAG,
            "Face scan: ${measured.framesUsed}/${samples.size} frames facing the camera, " +
                "${measured.classification.shape} with ${(measured.agreement * 100).toInt()}% frame agreement, " +
                "median ${measured.classification.metrics}, last pose ${store.latestAngles()}"
        )
        val frame = store.snapshot()
            ?: throw NoFaceDetectedException("Hold still and center your face in the outline, then tap Scan.")
        return try {
            classify(
                points = frame.points,
                width = frame.imageWidth,
                height = frame.imageHeight,
                bitmapForVerifier = frame.bitmap,
                liveHair = frame.hairMask,
                measured = measured
            )
        } finally {
            frame.bitmap?.recycle()
        }
    }

    override suspend fun analyzeImage(imageUri: Uri): ScanResult {
        val bitmap = ScanBitmapLoader.fromUri(context, imageUri)
        try {
            val detection = stillLandmarker.detect(bitmap)
                ?: throw NoFaceDetectedException("I couldn't find a face in that photo. Try a clearer front-facing shot.")
            return classify(
                detection.points,
                bitmap.width,
                bitmap.height,
                bitmap,
                liveHair = null,
                photoAngleHint = FaceAlignment.adjustmentHint(detection.angles)
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun classify(
        points: List<LandmarkPoint>,
        width: Int,
        height: Int,
        bitmapForVerifier: android.graphics.Bitmap?,
        liveHair: HairMask?,
        /** The live scan's multi-frame measurement; null for a photo, which is measured from [points]. */
        measured: AggregatedFaceShape? = null,
        /** Set when an uploaded photo's head is turned or tilted too far to measure reliably. */
        photoAngleHint: String? = null
    ): ScanResult {
        val heuristicShape = measured?.classification
            ?: FaceShapeClassifier.classify(points, width, height)
            ?: throw NoFaceDetectedException("I found a face but couldn't measure its shape. Move a bit closer and try again.")
        // faceShapeCnn takes the same landmark ratios the heuristic just computed, not pixels, so
        // it can run whenever a face was measured at all (no bitmap needed). It only knows
        // Heart/Oval/Round/Square (no usable training data for Diamond), so a low- or
        // no-confidence CNN result falls back to the geometric heuristic, which is the only path
        // that can still surface Diamond.
        val cnnShape = faceShapeCnn.classify(heuristicShape.metrics)
        val useCnnShape = cnnShape != null && cnnShape.confidence >= CNN_CONFIDENCE_FLOOR
        val resolvedShape = if (useCnnShape) cnnShape!!.shape else heuristicShape.shape
        val resolvedShapeConfidence = (if (useCnnShape) cnnShape!!.confidence else heuristicShape.confidence)
            .let { confidence -> measured?.let { FaceAlignment.adjustForAgreement(confidence, it.agreement) } ?: confidence }
            .let { confidence -> if (photoAngleHint != null) minOf(confidence, ANGLED_PHOTO_CONFIDENCE_CAP) else confidence }

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
            faceShapeConfidence = if (agreed && verifier != null && photoAngleHint == null) {
                ((resolvedShapeConfidence + verifier.confidence) / 2f).coerceAtMost(0.95f)
            } else {
                resolvedShapeConfidence.coerceAtMost(if (useCnnShape) 0.9f else 0.7f)
            },
            hairLengthConfidence = hair?.confidence ?: 0.35f,
            hairTextureConfidence = if (isBald) 0f else (appearance?.textureConfidence ?: 0.35f),
            hairColorConfidence = if (isBald) 0f else (appearance?.colorConfidence ?: 0.35f),
            verifierFaceShape = verifier?.shape,
            sourcesAgreed = agreed,
            faceAngleWarning = photoAngleHint,
            analysisNote = listOfNotNull(shapeNote).joinToString(" ").ifBlank { null }?.plus(hairNote)
                ?: hairNote.trim()
        )
    }

    companion object {
        /** Softmax confidence a CNN result needs to be trusted over the heuristic fallback. */
        private const val TAG = "LandmarkFaceAnalyzer"
        private const val CNN_CONFIDENCE_FLOOR = 0.5f

        /** How long a live scan collects frames before deciding from what it has. */
        private const val SCAN_WINDOW_MS = 3_000L

        /** Face-shape confidence ceiling for a photo whose head is turned or tilted too far. */
        private const val ANGLED_PHOTO_CONFIDENCE_CAP = 0.5f
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
