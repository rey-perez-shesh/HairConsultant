package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

class LiveFaceLandmarker(
    context: Context,
    private val store: FaceLandmarkStore,
    private val hairSegmenter: LiveHairSegmenter? = null
) : AutoCloseable {

    private val busy = AtomicBoolean(false)
    @Volatile private var lastBitmap: Bitmap? = null

    private val faceLandmarker: FaceLandmarker? = runCatching {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::onResult)
            .setErrorListener { error ->
                busy.set(false)
                Log.e(TAG, "Live Face Landmarker error", error)
                store.publishEmpty("Face guide error — try again")
            }
            .build()
        FaceLandmarker.createFromOptions(context, options)
    }.onFailure { error ->
        Log.e(TAG, "Failed to create live Face Landmarker", error)
        store.publishEmpty("Face guide failed to start")
    }.getOrNull()

    val isReady: Boolean get() = faceLandmarker != null

    fun detect(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val landmarker = faceLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        try {
            val frameTime = SystemClock.uptimeMillis()
            val bitmap = imageProxy.toBitmap()
            val rotated = rotateAndMirror(bitmap, imageProxy.imageInfo.rotationDegrees, isFrontCamera)
            if (rotated !== bitmap) bitmap.recycle()
            lastBitmap = rotated
            hairSegmenter?.detect(rotated, frameTime)
            landmarker.detectAsync(BitmapImageBuilder(rotated).build(), frameTime)
        } catch (t: Throwable) {
            busy.set(false)
            Log.e(TAG, "detect() failed", t)
        } finally {
            imageProxy.close()
        }
    }

    private fun onResult(result: FaceLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        busy.set(false)
        val landmarks = result.faceLandmarks().firstOrNull()
        if (landmarks.isNullOrEmpty()) {
            store.publishEmpty()
            return
        }
        val points = landmarks.map { LandmarkPoint(it.x(), it.y()) }
        store.publishFrame(points, lastBitmap, input.width, input.height)
    }

    override fun close() {
        faceLandmarker?.close()
        busy.set(false)
    }

    companion object {
        private const val TAG = "LiveFaceLandmarker"
        const val MODEL_ASSET = "face_landmarker.task"
    }
}

class StillImageFaceLandmarker(context: Context) : AutoCloseable {

    private val faceLandmarker: FaceLandmarker? = runCatching {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(LiveFaceLandmarker.MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        FaceLandmarker.createFromOptions(context, options)
    }.onFailure { error ->
        Log.e("StillFaceLandmarker", "Failed to create still Face Landmarker", error)
    }.getOrNull()

    fun detect(bitmap: Bitmap): List<LandmarkPoint>? {
        val result = faceLandmarker?.detect(BitmapImageBuilder(bitmap).build()) ?: return null
        val landmarks = result.faceLandmarks().firstOrNull() ?: return null
        if (landmarks.isEmpty()) return null
        return landmarks.map { LandmarkPoint(it.x(), it.y()) }
    }

    override fun close() {
        faceLandmarker?.close()
    }
}

internal fun rotateAndMirror(source: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
    if (rotationDegrees == 0 && !mirror) return source
    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
        if (mirror) {
            postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        }
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
