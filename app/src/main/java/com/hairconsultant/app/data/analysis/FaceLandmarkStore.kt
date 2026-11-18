package com.hairconsultant.app.data.analysis

import android.graphics.Bitmap
import android.os.SystemClock
import com.hairconsultant.app.domain.model.HairLength
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FaceOverlayFrame(
    val points: List<LandmarkPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val faceDetected: Boolean,
    val statusMessage: String,
    val hairMask: HairMask? = null,
    val estimatedHairLength: HairLength? = null,
    /** Set while a face is found but outside the guide oval or not facing the camera; the guide shows it as a warning. */
    val alignmentHint: String? = null,
    /** Where the forehead meets the hair, above the mesh's top point ([Hairline]); null when it can't be seen. */
    val hairline: LandmarkPoint? = null
) {
    companion object {
        fun idle(message: String = "Align your face with the outline") = FaceOverlayFrame(
            points = emptyList(),
            imageWidth = 1,
            imageHeight = 1,
            faceDetected = false,
            statusMessage = message
        )
    }
}

data class FaceLandmarkSnapshot(
    val points: List<LandmarkPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val bitmap: Bitmap?,
    val hairMask: HairMask? = null
)

/**
 * Shared live-camera mesh and hair mask between the overlay and [LandmarkFaceAnalyzer].
 * Camera frames publish here; tapping Scan reads a short-lived snapshot.
 */
class FaceLandmarkStore {
    private val lock = Any()
    private val _overlay = MutableStateFlow(FaceOverlayFrame.idle())
    val overlay: StateFlow<FaceOverlayFrame> = _overlay.asStateFlow()

    private var latestPoints: List<LandmarkPoint>? = null
    private var latestBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var latestWidth: Int = 0
    private var latestHeight: Int = 0
    private var latestAtElapsed: Long = 0L
    private var latestHair: HairMask? = null
    private var latestHairAtElapsed: Long = 0L
    private var latestAngles: HeadAngles? = null
    private var latestMouthOpen = false
    private var latestLighting: Lighting = Lighting.GOOD
    private var framesSinceLightingCheck = 0
    private var smoothedAngles: HeadAngles? = null
    private var warningShown = false
    private var guideWidth = 0
    private var guideHeight = 0

    /** Recent frames' face measurements, oldest first, so a scan can combine several (see [FaceAlignment]). */
    private val recentSamples = ArrayDeque<FaceFrameSample>()

    /** The live camera's focal length over the frame's long side, set once the camera is bound; for undoing perspective. */
    @Volatile var focalToLongSide: Float = FaceShapeClassifier.TYPICAL_FOCAL_TO_LONG_SIDE

    fun publishFrame(
        points: List<LandmarkPoint>,
        bitmap: Bitmap?,
        width: Int,
        height: Int,
        angles: HeadAngles? = null
    ) {
        val metrics = FaceShapeClassifier.measure(points, width, height, angles, focalToLongSide)
        val mouthOpen = FaceShapeClassifier.isMouthOpen(points, width, height)
        synchronized(lock) {
            latestPoints = points
            latestAngles = angles
            latestMouthOpen = mouthOpen
            smoothedAngles = FaceAlignment.smooth(smoothedAngles, angles)
            warningShown = FaceAlignment.shouldWarn(smoothedAngles, warningShown)
            if (bitmap != null && bitmap !== latestBitmap) {
                latestBitmap?.recycle()
                latestBitmap = bitmap
                previewBitmap?.recycle()
                previewBitmap = downscaleForBlur(bitmap)
            }
            // Read under the lock: the next frame recycles this bitmap.
            if (bitmap != null && ++framesSinceLightingCheck >= LIGHTING_CHECK_EVERY) {
                framesSinceLightingCheck = 0
                latestLighting = SkinToneAnalyzer.read(bitmap, points)?.lighting ?: Lighting.GOOD
            }
            latestWidth = width
            latestHeight = height
            latestAtElapsed = SystemClock.elapsedRealtime()
            // Scan is only enabled inside the oval, but the user can drift during the scan window;
            // frames outside it (or too far away) aren't measured.
            val inGuide = FaceGuide.positionHint(points, width, height, guideWidth, guideHeight) == null
            if (metrics != null && inGuide) {
                recentSamples.addLast(FaceFrameSample(metrics, angles, latestAtElapsed, mouthOpen))
                while (recentSamples.size > MAX_SAMPLES) recentSamples.removeFirst()
            }
        }
        emitOverlay()
    }

    /** Size of the live guide view, so frames can be checked against its oval ([FaceGuide]). */
    fun setGuideSize(width: Int, height: Int) {
        synchronized(lock) {
            guideWidth = width
            guideHeight = height
        }
    }

    /** Frames measured at or after [elapsedMillis] ([SystemClock.elapsedRealtime] time), oldest first. */
    fun samplesSince(elapsedMillis: Long): List<FaceFrameSample> = synchronized(lock) {
        recentSamples.filter { it.atElapsedMillis >= elapsedMillis }
    }

    /** The latest frame's head angles, for telling the user what to fix when a scan can't use their pose. */
    fun latestAngles(): HeadAngles? = synchronized(lock) { latestAngles }

    /** Downscaled camera frame for live hair blur (caller must not recycle). */
    fun peekPreviewBitmap(): Bitmap? = synchronized(lock) {
        previewBitmap?.takeUnless { it.isRecycled }
    }

    /** Latest analysis frame for face punch-through occlusion (caller must not recycle). */
    fun peekLatestBitmap(): Bitmap? = synchronized(lock) {
        latestBitmap?.takeUnless { it.isRecycled }
    }

    fun publishHair(mask: HairMask?) {
        synchronized(lock) {
            latestHair = mask
            latestHairAtElapsed = if (mask != null) SystemClock.elapsedRealtime() else 0L
        }
        emitOverlay()
    }

    fun publishEmpty(message: String = "Align your face with the outline") {
        synchronized(lock) {
            latestPoints = null
            latestAngles = null
            latestMouthOpen = false
            latestLighting = Lighting.GOOD
            smoothedAngles = null
            warningShown = false
            latestAtElapsed = 0L
            latestHair = null
            latestHairAtElapsed = 0L
            previewBitmap?.recycle()
            previewBitmap = null
        }
        _overlay.value = FaceOverlayFrame.idle(message)
    }

    fun snapshot(maxAgeMs: Long = 2_000L): FaceLandmarkSnapshot? {
        synchronized(lock) {
            val points = latestPoints ?: return null
            val now = SystemClock.elapsedRealtime()
            if (now - latestAtElapsed > maxAgeMs) return null
            val copy = latestBitmap?.let { src ->
                if (src.isRecycled) null else src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
            }
            val hair = latestHair?.takeIf { now - latestHairAtElapsed <= maxAgeMs }
            return FaceLandmarkSnapshot(points, latestWidth, latestHeight, copy, hair)
        }
    }

    private fun emitOverlay() {
        val points: List<LandmarkPoint>
        val width: Int
        val height: Int
        val hair: HairMask?
        val angles: HeadAngles?
        val warn: Boolean
        val viewWidth: Int
        val viewHeight: Int
        val mouthOpen: Boolean
        val lighting: Lighting
        synchronized(lock) {
            points = latestPoints ?: emptyList()
            angles = smoothedAngles
            warn = warningShown
            mouthOpen = latestMouthOpen
            lighting = latestLighting
            viewWidth = guideWidth
            viewHeight = guideHeight
            width = latestWidth.coerceAtLeast(1)
            height = latestHeight.coerceAtLeast(1)
            hair = latestHair
        }
        val faceDetected = points.size >= FaceShapeClassifier.RIGHT_CHEEK + 1
        val hairLength = if (faceDetected && hair != null) {
            HairLengthClassifier.classify(hair, points)?.length
        } else {
            null
        }
        // Position first: a face half out of frame can't be fixed by straightening the head.
        val alignmentHint = if (faceDetected) {
            FaceGuide.positionHint(points, width, height, viewWidth, viewHeight)
                ?: if (warn) FaceAlignment.liveGuideHint(angles) else null
        } else {
            null
        }
        val hairline = if (faceDetected && hair != null) {
            Hairline.find(points, width, height, hair.width) { x, y -> !hair.isHairAt(x, y) }
        } else {
            null
        }
        // Neither blocks the scan: open-mouth frames are just left out of it, and poor light only
        // makes the colour reads less sure.
        // Hair across the mid-forehead hides the hairline the face length is measured to.
        val fringe = faceDetected && hair != null && hairline == null &&
            points.size > MID_FOREHEAD && hair.isHairAt(points[MID_FOREHEAD].x, points[MID_FOREHEAD].y)
        val status = when {
            alignmentHint != null -> alignmentHint
            faceDetected && mouthOpen -> "Relax your face, mouth closed"
            fringe -> "Push your hair off your forehead — tap Scan when ready"
            faceDetected && lighting.hint != null -> "${lighting.hint} — tap Scan when ready"
            faceDetected && hairLength == HairLength.BALD ->
                "Face found — bald / no hair detected — tap Scan"
            faceDetected && hairLength != null ->
                "Face and ${hairLength.displayName.lowercase()} hair found — tap Scan"
            faceDetected && hair != null ->
                "Face and hair found — tap Scan"
            faceDetected ->
                "Face found — hold still and tap Scan"
            else ->
                "Align your face with the outline"
        }
        _overlay.value = FaceOverlayFrame(
            points = points,
            imageWidth = width,
            imageHeight = height,
            faceDetected = faceDetected,
            statusMessage = status,
            hairMask = hair,
            estimatedHairLength = hairLength,
            alignmentHint = alignmentHint,
            hairline = hairline
        )
    }

    private companion object {
        /** About 2–3 seconds of frames at the live landmarker's rate. */
        const val MAX_SAMPLES = 45

        /** The live light check samples a few hundred pixels; every few frames is plenty for a hint. */
        const val LIGHTING_CHECK_EVERY = 6

        /** Mesh point midway up the forehead, between the brow and the mesh's top. */
        const val MID_FOREHEAD = 151
    }

    private fun downscaleForBlur(src: Bitmap): Bitmap? {
        if (src.isRecycled) return null
        val maxW = 240
        if (src.width <= maxW) {
            return src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val h = (src.height * maxW / src.width.toFloat()).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxW, h, true)
    }
}
