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
    val estimatedHairLength: HairLength? = null
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
    private var latestWidth: Int = 0
    private var latestHeight: Int = 0
    private var latestAtElapsed: Long = 0L
    private var latestHair: HairMask? = null
    private var latestHairAtElapsed: Long = 0L

    fun publishFrame(points: List<LandmarkPoint>, bitmap: Bitmap?, width: Int, height: Int) {
        synchronized(lock) {
            latestPoints = points
            if (bitmap != null && bitmap !== latestBitmap) {
                latestBitmap?.recycle()
                latestBitmap = bitmap
            }
            latestWidth = width
            latestHeight = height
            latestAtElapsed = SystemClock.elapsedRealtime()
        }
        emitOverlay()
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
            latestAtElapsed = 0L
            latestHair = null
            latestHairAtElapsed = 0L
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
        synchronized(lock) {
            points = latestPoints ?: emptyList()
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
        val status = when {
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
            estimatedHairLength = hairLength
        )
    }
}
