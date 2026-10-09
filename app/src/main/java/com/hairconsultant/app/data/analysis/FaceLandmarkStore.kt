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
    /** Set while a face is found but not facing the camera; the guide shows it as a warning. */
    val alignmentHint: String? = null
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
    private var smoothedAngles: HeadAngles? = null
    private var warningShown = false

    /** Recent frames' face measurements, oldest first, so a scan can combine several (see [FaceAlignment]). */
    private val recentSamples = ArrayDeque<FaceFrameSample>()

    fun publishFrame(
        points: List<LandmarkPoint>,
        bitmap: Bitmap?,
        width: Int,
        height: Int,
        angles: HeadAngles? = null
    ) {
        val metrics = FaceShapeClassifier.measure(points, width, height)
        synchronized(lock) {
            latestPoints = points
            latestAngles = angles
            smoothedAngles = FaceAlignment.smooth(smoothedAngles, angles)
            warningShown = FaceAlignment.shouldWarn(smoothedAngles, warningShown)
            if (bitmap != null && bitmap !== latestBitmap) {
                latestBitmap?.recycle()
                latestBitmap = bitmap
                previewBitmap?.recycle()
                previewBitmap = downscaleForBlur(bitmap)
            }
            latestWidth = width
            latestHeight = height
            latestAtElapsed = SystemClock.elapsedRealtime()
            if (metrics != null) {
                recentSamples.addLast(FaceFrameSample(metrics, angles, latestAtElapsed))
                while (recentSamples.size > MAX_SAMPLES) recentSamples.removeFirst()
            }
        }
        emitOverlay()
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
        synchronized(lock) {
            points = latestPoints ?: emptyList()
            angles = smoothedAngles
            warn = warningShown
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
        val alignmentHint = if (faceDetected && warn) FaceAlignment.liveGuideHint(angles) else null
        val status = when {
            alignmentHint != null -> alignmentHint
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
            alignmentHint = alignmentHint
        )
    }

    private companion object {
        /** About 2–3 seconds of frames at the live landmarker's rate. */
        const val MAX_SAMPLES = 45
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
