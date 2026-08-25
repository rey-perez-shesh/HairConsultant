package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live-camera hair mask via MediaPipe Hair Segmenter (class 0 = background, 1 = hair). Runs
 * independently of the face landmarker so a slow mask frame does not drop the mesh, and is
 * decoupled from [FaceLandmarkStore] so it can also drive a standalone hair-swap preview (e.g.
 * [com.hairconsultant.app.ui.home.HairSwapCameraPreview]) that has no face-mesh scan involved.
 */
class LiveHairSegmenter(
    context: Context,
    private val onMask: (HairMask?) -> Unit
) : AutoCloseable {

    private val busy = AtomicBoolean(false)

    private val segmenter: ImageSegmenter? = runCatching {
        ImageSegmenter.createFromOptions(context, liveOptions())
    }.onFailure { error ->
        Log.e(TAG, "Failed to create live Hair Segmenter", error)
    }.getOrNull()

    private val hairClass: Int = segmenter?.let { resolveHairClass(it) } ?: HairLengthClassifier.HAIR_CLASS

    val isReady: Boolean get() = segmenter != null

    fun detect(bitmap: Bitmap, timestampMs: Long = SystemClock.uptimeMillis()) {
        val imageSegmenter = segmenter ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            imageSegmenter.segmentAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
        } catch (t: Throwable) {
            busy.set(false)
            Log.e(TAG, "detect() failed", t)
        }
    }

    private fun liveOptions() = ImageSegmenter.ImageSegmenterOptions.builder()
        .setBaseOptions(
            BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .setDelegate(Delegate.CPU)
                .build()
        )
        .setRunningMode(RunningMode.LIVE_STREAM)
        .setOutputCategoryMask(true)
        .setOutputConfidenceMasks(true)
        .setResultListener(::onResult)
        .setErrorListener { error ->
            busy.set(false)
            Log.e(TAG, "Live Hair Segmenter error", error)
        }
        .build()

    private fun onResult(result: ImageSegmenterResult, input: MPImage) {
        busy.set(false)
        onMask(result.toHairMask(hairClass))
    }

    override fun close() {
        segmenter?.close()
        busy.set(false)
    }

    companion object {
        private const val TAG = "LiveHairSegmenter"
        const val MODEL_ASSET = "hair_segmenter.tflite"
    }
}

class StillImageHairSegmenter(context: Context) : AutoCloseable {

    private val segmenter: ImageSegmenter? = runCatching {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(LiveHairSegmenter.MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()
        ImageSegmenter.createFromOptions(context, options)
    }.onFailure { error ->
        Log.e("StillHairSegmenter", "Failed to create still Hair Segmenter", error)
    }.getOrNull()

    private val hairClass: Int = segmenter?.let { resolveHairClass(it) } ?: HairLengthClassifier.HAIR_CLASS

    fun segment(bitmap: Bitmap): HairMask? {
        val result = segmenter?.segment(BitmapImageBuilder(bitmap).build()) ?: return null
        return result.toHairMask(hairClass)
    }

    override fun close() {
        segmenter?.close()
    }
}

private fun resolveHairClass(segmenter: ImageSegmenter): Int {
    val labels = runCatching { segmenter.labels }.getOrNull().orEmpty()
    val index = labels.indexOfFirst { it.equals("hair", ignoreCase = true) }
    return if (index >= 0) index else HairLengthClassifier.HAIR_CLASS
}

private fun ImageSegmenterResult.toHairMask(hairClass: Int): HairMask? {
    val image = categoryMask().orElse(null) ?: return null
    val buffer = ByteBufferExtractor.extract(image)
    buffer.rewind()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    if (bytes.isEmpty() || image.width <= 0 || image.height <= 0) return null

    // Soft, per-pixel confidence for the hair class (when requested) instead of the binary
    // category mask, so the silhouette edge can fade out at the hairline rather than cut hard.
    val confidence = confidenceMasks().orElse(null)?.getOrNull(hairClass)?.let { hairConfidence ->
        runCatching {
            val confBuffer = ByteBufferExtractor.extract(hairConfidence, MPImage.IMAGE_FORMAT_ALPHA)
            confBuffer.rewind()
            ByteArray(confBuffer.remaining()).also { confBuffer.get(it) }
        }.getOrNull()
    }

    return HairMask(bytes, image.width, image.height, hairClass, confidence)
}
