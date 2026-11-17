package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.SkinTone
import com.hairconsultant.app.domain.model.SkinUndertone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

class NoFaceDetectedException(message: String = "No face detected") : Exception(message)

/**
 * Face-shape / hair-length / hair-texture / hair-color / skin-tone detection.
 *
 * Live camera: MediaPipe Face Landmarker publishes the mesh continuously; [analyzeCameraFrame]
 * measures the face over the frames of the next few seconds and reads hair and skin from several
 * of them, combining each by median or average, so one blink, flicker or stray mask frame can't
 * change the result. Photos: the same readings from the one image.
 */
interface FaceAnalyzer {
    /** [previousShape]: the user's last confirmed face shape, kept when this scan is a near tie with it. */
    suspend fun analyzeCameraFrame(previousShape: FaceShape? = null): ScanResult
    suspend fun analyzeImage(imageUri: Uri): ScanResult
}

class MockFaceAnalyzer : FaceAnalyzer {
    override suspend fun analyzeCameraFrame(previousShape: FaceShape?): ScanResult {
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
        hairColorConfidence = (70..97).random() / 100f,
        skinTone = SkinTone.entries.random(),
        skinUndertone = SkinUndertone.entries.random(),
        skinToneConfidence = (70..97).random() / 100f,
        skinUndertoneConfidence = (40..80).random() / 100f
    )
}

/** What one camera frame (or the photo) shows of the hair and skin; a scan combines several. */
private class FrameReading(
    /** Hairline-to-chin over the mesh's length, when the hairline could be measured ([Hairline]). */
    val fullToMeshLength: Float?,
    /** Forehead skin width over cheekbone width ([Hairline.foreheadWidth]); recorded, not yet decided on. */
    val foreheadToCheek: Float?,
    val hairLength: HairLengthClassification?,
    /** Hair area over face area, to tell hair too short to read its texture. */
    val hairToFace: Float,
    /** OKLab [L, a, b] of the hair ([HairAppearanceClassifier.hairColor]). */
    val hairColor: FloatArray?,
    val textureProbabilities: Map<HairTexture, Float>?,
    /** Only when the texture model isn't available. */
    val heuristicTexture: HairTexture?,
    val skin: SkinReading?
)

class LandmarkFaceAnalyzer(
    private val context: Context,
    private val store: FaceLandmarkStore,
    private val stillLandmarker: StillImageFaceLandmarker,
    private val mlKitVerifier: MlKitFaceMeshVerifier,
    private val stillHairSegmenter: StillImageHairSegmenter,
    private val faceParser: FaceParsingSegmenter,
    private val faceShapeCnn: FaceShapeTfliteClassifier,
    private val hairTypeCnn: HairTypeTfliteClassifier
) : FaceAnalyzer {

    /**
     * Measures the face over the frames that arrive in the next few seconds: only frames where the
     * head faces the camera with the mouth closed count, and their ratios are combined by median
     * (see [FaceAlignment]). Hair and skin are read from [READING_FRAMES] of those seconds' frames,
     * spaced apart so they aren't near-copies. Stops early once both are in.
     */
    override suspend fun analyzeCameraFrame(previousShape: FaceShape?): ScanResult {
        val scanStartedAt = SystemClock.elapsedRealtime()
        val readings = mutableListOf<FrameReading>()
        var lastFrame: FaceLandmarkSnapshot? = null
        var lastReadAt = 0L
        try {
            while (SystemClock.elapsedRealtime() - scanStartedAt < SCAN_WINDOW_MS) {
                val usable = store.samplesSince(scanStartedAt).count(FaceAlignment::isUsable)
                if (usable >= FaceAlignment.TARGET_FRAMES && readings.size >= READING_FRAMES) break
                if (readings.size < READING_FRAMES && SystemClock.elapsedRealtime() - lastReadAt >= READING_SPACING_MS) {
                    val frame = store.snapshot()
                    val bitmap = frame?.bitmap
                    if (bitmap != null) {
                        lastReadAt = SystemClock.elapsedRealtime()
                        readings += withContext(Dispatchers.Default) {
                            read(frame.points, frame.imageWidth, frame.imageHeight, bitmap, frame.hairMask)
                        }
                        // The newest frame is kept for the ML Kit check.
                        lastFrame?.bitmap?.recycle()
                        lastFrame = frame
                    }
                }
                delay(100)
            }
            val samples = store.samplesSince(scanStartedAt)
            val measured = FaceAlignment.aggregate(samples, ::modelShape)
                ?: throw NoFaceDetectedException(rejectionMessage(samples))
            val frame = lastFrame ?: store.snapshot().also { lastFrame = it }
                ?: throw NoFaceDetectedException("Hold still and center your face in the outline, then tap Scan.")
            Log.d(
                TAG,
                "Face scan: ${measured.framesUsed}/${samples.size} usable frames, ${readings.size} hair/skin readings, " +
                    "${(measured.agreement * 100).toInt()}% frame agreement, median ${measured.metrics}, " +
                    "last pose ${store.latestAngles()}"
            )
            // Per-frame detail for the repeat-scan consistency check: what moved between frames and scans.
            samples.filter(FaceAlignment::isUsable).takeLast(FaceAlignment.TARGET_FRAMES).forEach { sample ->
                Log.d(TAG, "Frame: pose ${sample.angles}, ${sample.metrics}")
            }
            val angles = store.latestAngles()
            return withContext(Dispatchers.Default) {
                val bitmap = frame.bitmap
                if (readings.isEmpty() && bitmap != null) {
                    readings += read(frame.points, frame.imageWidth, frame.imageHeight, bitmap, frame.hairMask)
                }
                decide(measured.metrics, measured.agreement, readings, bitmap, angles, previousShape)
            }
        } finally {
            lastFrame?.bitmap?.recycle()
        }
    }

    override suspend fun analyzeImage(imageUri: Uri): ScanResult {
        val bitmap = ScanBitmapLoader.fromUri(context, imageUri)
        try {
            val detection = stillLandmarker.detect(bitmap)
                ?: throw NoFaceDetectedException("I couldn't find a face in that photo. Try a clearer front-facing shot.")
            // The app analyzes one person: with others in the photo, the measured face and the hair
            // analysis (which covers every head in the image) could belong to someone else.
            if (detection.faceCount > 1) throw NoFaceDetectedException(MULTIPLE_FACES_MESSAGE)
            // Same rule as the live scan: a turned or tilted head gives the wrong face shape, so
            // an angled photo is refused rather than measured.
            FaceAlignment.photoRejection(detection.angles)?.let { throw NoFaceDetectedException(it) }
            FaceGuide.photoRejection(detection.points, bitmap.width)?.let { throw NoFaceDetectedException(it) }
            val metrics = FaceShapeClassifier.measure(detection.points, bitmap.width, bitmap.height, detection.angles)
                ?: throw NoFaceDetectedException("I found a face but couldn't measure its shape. Move a bit closer and try again.")
            return withContext(Dispatchers.Default) {
                val reading = read(detection.points, bitmap.width, bitmap.height, bitmap, liveHair = null)
                decide(metrics, agreement = null, listOf(reading), bitmap, detection.angles, previousShape = null)
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** The trained model's shape for a set of ratios, with no previous scan to lean on. */
    private fun modelShape(metrics: FaceShapeMetrics): FaceShape =
        FaceShapeClassifier.decide(metrics, faceShapeCnn.probabilities(metrics)).shape

    /** Hair and skin in one frame. [liveHair] is the camera's live hair mask; a photo is segmented here. */
    private fun read(
        points: List<LandmarkPoint>,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        liveHair: HairMask?
    ): FrameReading {
        val parsing = faceParser.segment(bitmap)
        val hairMask = liveHair ?: stillHairSegmenter.segment(bitmap)
        val hairline = when {
            parsing != null -> Hairline.find(points, width, height, parsing.width, parsing::isFaceSkin)
            hairMask != null -> Hairline.find(points, width, height, hairMask.width) { x, y -> !hairMask.isHairAt(x, y) }
            else -> null
        }
        val notHair: ((Float, Float) -> Boolean)? = parsing?.let { it::isNotHair }
        val length = hairMask?.let { HairLengthClassifier.classify(it, points, notHair) }
        val hair = hairMask?.takeIf { length != null && length.length != HairLength.BALD }
        val skinOnly: ((Float, Float) -> Boolean)? = parsing?.let { it::isFaceSkin }
            ?: hairMask?.let { mask -> { x: Float, y: Float -> !mask.isHairAt(x, y) } }
        if (parsing != null) logParsingCheck(parsing, points)
        Log.d(
            TAG,
            "Reading: ${if (liveHair != null) "live" else "still"} hair mask ${hairMask?.width}x${hairMask?.height}, " +
                "length $length, unfiltered ${hairMask?.let { HairLengthClassifier.classify(it, points) }}"
        )
        return FrameReading(
            fullToMeshLength = hairline?.let { Hairline.fullToMeshLength(points, it, width, height) },
            foreheadToCheek = if (parsing != null && hairline != null) {
                Hairline.foreheadWidth(points, hairline, width, height, parsing.width, parsing::isFaceSkin)
            } else {
                null
            },
            hairLength = length,
            hairToFace = (length?.coverage ?: 0f) / faceArea(points),
            hairColor = hair?.let { HairAppearanceClassifier.hairColor(bitmap, it, notHair) },
            textureProbabilities = hair?.let { hairTypeCnn.probabilities(bitmap, it) },
            heuristicTexture = hair?.takeIf { !hairTypeCnn.isReady }?.let { HairAppearanceClassifier.classifyTexture(bitmap, it).first },
            skin = SkinToneAnalyzer.read(bitmap, points, skinOnly)
        )
    }

    private suspend fun decide(
        metrics: FaceShapeMetrics,
        /** Share of the live scan's frames that agreed on the shape; null for a photo. */
        agreement: Float?,
        readings: List<FrameReading>,
        verifierBitmap: Bitmap?,
        angles: HeadAngles?,
        previousShape: FaceShape?
    ): ScanResult {
        val hairlineRatio = readings.mapNotNull { it.fullToMeshLength }.takeIf { it.isNotEmpty() }?.median()
        val faceMetrics = hairlineRatio?.let { FaceShapeClassifier.withHairline(metrics, it) } ?: metrics
        val probabilities = faceShapeCnn.probabilities(faceMetrics)
        val face = FaceShapeClassifier.decide(faceMetrics, probabilities, previousShape)
        val shapeConfidence = agreement?.let { FaceAlignment.adjustForAgreement(face.confidence, it) } ?: face.confidence
        val verifierShape = verifierBitmap?.let { mlKitVerifier.measure(it, angles) }?.let(::modelShape)
        val agreed = verifierShape == null || verifierShape == face.shape

        val length = HairLengthClassifier.combine(readings.mapNotNull { it.hairLength })
        val bald = length == null || length.length == HairLength.BALD
        val skin = SkinToneAnalyzer.combine(readings.mapNotNull { it.skin })
        val poorLight = skin != null && skin.lighting != Lighting.GOOD
        // Hair colour from the well-lit frames when there are any, like the skin.
        val colorFrames = if (bald) emptyList() else readings.filter { it.skin?.lighting == Lighting.GOOD }.ifEmpty { readings }
        val colorLab = exposureMatched(colorFrames).takeIf { it.isNotEmpty() }
            ?.let { labs -> FloatArray(3) { channel -> labs.map { it[channel] }.median() } }
        val color = colorLab?.let(HairAppearanceClassifier::classifyColor)
        val texture = if (bald) null else combineTexture(readings)
        val tooShortForTexture = !bald && length?.length == HairLength.SHORT &&
            readings.map { it.hairToFace }.median() < MIN_HAIR_TO_FACE_FOR_TEXTURE
        val foreheadWidth = readings.mapNotNull { it.foreheadToCheek }.takeIf { it.isNotEmpty() }?.median()
        val measurements = buildMap {
            put("lengthToWidth", faceMetrics.lengthToWidth)
            put("jawToCheek", faceMetrics.jawToCheek)
            put("foreheadToJaw", faceMetrics.foreheadToJaw)
            put("foreheadToCheek", faceMetrics.foreheadToCheek)
            put("jawAngleDeg", faceMetrics.jawAngleDeg)
            put("chinToJaw", faceMetrics.chinToJaw)
            hairlineRatio?.let { put("fullToMeshLength", it) }
            foreheadWidth?.let { put("foreheadSkinToCheek", it) }
            skin?.let { put("skinIta", it.ita); put("skinHue", it.hue) }
            angles?.let { put("pitchDeg", it.pitchDeg); put("yawDeg", it.yawDeg) }
        }

        Log.d(
            TAG,
            "Decided: ratios $metrics, hairline ${hairlineRatio?.let { "%.3f".format(it) }} -> $faceMetrics, " +
                "model $probabilities -> ${face.shape} ${"%.2f".format(face.confidence)} (previous $previousShape, " +
                "ML Kit $verifierShape); length $length; hair OKLab ${colorLab?.joinToString { "%.3f".format(it) }} -> $color; " +
                "texture $texture${if (tooShortForTexture) " (too short)" else ""}; skin $skin; " +
                "measurements $measurements"
        )

        val tips = buildList {
            if (!bald && readings.isNotEmpty() && hairlineRatio == null) {
                add("Your hair covers your hairline, so I measured your face without the top of your forehead; pushing it back helps.")
            }
            if (length?.reachesFrameBottom == true && length.length != HairLength.LONG) {
                add("Your hair runs past the bottom of the picture, so I can only tell it's at least medium length.")
            }
            if (tooShortForTexture) add("Your hair is too short for me to read its texture.")
            skin?.lighting?.hint?.let { add("$it, then try again for a more reliable color read.") }
        }
        return ScanResult(
            faceShape = face.shape,
            hairLength = length?.length ?: HairLength.BALD,
            hairTexture = texture?.first ?: HairTexture.STRAIGHT,
            hairColor = color?.first ?: HairColor.OTHER,
            faceShapeConfidence = if (agreed) shapeConfidence else min(shapeConfidence, 0.6f),
            hairLengthConfidence = length?.confidence ?: 0.35f,
            hairTextureConfidence = when {
                bald || texture == null -> 0f
                tooShortForTexture -> min(texture.second, 0.4f)
                else -> texture.second
            },
            hairColorConfidence = when {
                bald || color == null -> 0f
                poorLight -> min(color.second, 0.45f)
                else -> color.second
            },
            verifierFaceShape = verifierShape,
            sourcesAgreed = agreed,
            analysisNote = tips.joinToString(" ").ifBlank { null },
            skinTone = skin?.tone,
            skinUndertone = skin?.undertone,
            skinToneConfidence = skin?.toneConfidence ?: 0f,
            skinUndertoneConfidence = skin?.undertoneConfidence ?: 0f,
            measurements = measurements
        )
    }

    /**
     * Each frame's hair colour with its lightness scaled by how much brighter or darker that
     * frame's skin was than the scan's median skin, so an exposure flicker between frames isn't
     * read as a different hair colour. The reference is the same face within one scan, never a
     * fixed skin value, which would mistake deep skin for a dark exposure.
     */
    private fun exposureMatched(frames: List<FrameReading>): List<FloatArray> {
        val reference = frames.mapNotNull { frame -> frame.skin?.l?.takeIf { frame.hairColor != null } }
            .takeIf { it.isNotEmpty() }?.median()
        return frames.mapNotNull { frame ->
            val lab = frame.hairColor ?: return@mapNotNull null
            val skinL = frame.skin?.l
            val scale = if (reference != null && skinL != null && skinL > 0f) {
                (reference / skinL).coerceIn(1f - MAX_EXPOSURE_MATCH, 1f + MAX_EXPOSURE_MATCH)
            } else {
                1f
            }
            floatArrayOf(lab[0] * scale, lab[1], lab[2])
        }
    }

    /** The texture model's probabilities averaged over the frames, or a majority of the fallback's votes. */
    private fun combineTexture(readings: List<FrameReading>): Pair<HairTexture, Float>? {
        val probabilities = readings.mapNotNull { it.textureProbabilities }
        if (probabilities.isNotEmpty()) {
            val best = HairTexture.entries
                .associateWith { texture -> probabilities.map { it[texture] ?: 0f }.average().toFloat() }
                .maxBy { it.value }
            return best.key to best.value
        }
        val votes = readings.mapNotNull { it.heuristicTexture }
        if (votes.isEmpty()) return null
        val top = votes.groupingBy { it }.eachCount().maxBy { it.value }
        return top.key to (0.45f + 0.2f * top.value / votes.size)
    }

    /** Why a live scan found too few usable frames, naming what to fix. */
    private fun rejectionMessage(samples: List<FaceFrameSample>): String = when {
        samples.isEmpty() -> "Hold still and center your face in the outline, then tap Scan."
        samples.count { it.mouthOpen } * 2 > samples.size ->
            "Relax your face with your mouth closed, then tap Scan again."
        else -> FaceAlignment.adjustmentHint(store.latestAngles())?.let { "$it Then tap Scan again." }
            ?: "Hold still and face the camera for a moment, then tap Scan again."
    }

    /** Normalized face area (cheek width by mesh height), the scale hair coverage is compared against. */
    private fun faceArea(points: List<LandmarkPoint>): Float {
        val width = points[FaceShapeClassifier.RIGHT_CHEEK].x - points[FaceShapeClassifier.LEFT_CHEEK].x
        val height = points[FaceShapeClassifier.CHIN].y - points[FaceShapeClassifier.FOREHEAD_TOP].y
        return (kotlin.math.abs(width) * height).coerceAtLeast(0.01f)
    }

    /** The multiclass model has no label metadata: log what it calls the cheeks and forehead, which should be face skin. */
    private fun logParsingCheck(parsing: FaceParsing, points: List<LandmarkPoint>) {
        val labels = intArrayOf(205, 425, 151).map { parsing.labelAt(points[it].x, points[it].y) }
        Log.d(TAG, "Multiclass labels at cheeks/forehead: $labels (face skin is ${FaceParsing.FACE_SKIN}), mask ${parsing.width}x${parsing.height}")
    }

    companion object {
        private const val TAG = "LandmarkFaceAnalyzer"

        /** How long a live scan collects frames before deciding from what it has. */
        private const val SCAN_WINDOW_MS = 3_000L

        /** Frames a live scan reads hair and skin from, and the least time between two of them. */
        private const val READING_FRAMES = 4
        private const val READING_SPACING_MS = 300L

        /** Hair covering less than this share of the face area is too short to read its texture. */
        private const val MIN_HAIR_TO_FACE_FOR_TEXTURE = 0.25f

        /** The most one frame's hair lightness is rescaled to match the scan's exposure. */
        private const val MAX_EXPOSURE_MATCH = 0.15f

        private const val MULTIPLE_FACES_MESSAGE =
            "I can see more than one face in that photo. Please upload a photo with only your face in it."
    }
}
