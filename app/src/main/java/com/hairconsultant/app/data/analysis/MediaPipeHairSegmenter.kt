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
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live-camera hair mask via MediaPipe Hair Segmenter (class 0 = background, 1 = hair).
 * Confidence masks enable soft-edge replacement. Runs independently of the face landmarker
 * so a slow mask frame does not drop the mesh, and is decoupled from [FaceLandmarkStore] so
 * it can also drive a standalone hair-swap preview (e.g.
 * [com.hairconsultant.app.ui.home.HairSwapCameraPreview]).
 */
class LiveHairSegmenter(
    context: Context,
    /** See [toHairMask]; off for the try-on cover, which was tuned on the hard mask. */
    private val softConfidence: Boolean = false,
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

    /** @return false if the frame was dropped (still busy with the last one, or no segmenter): [onMask] won't fire for it. */
    fun detect(bitmap: Bitmap, timestampMs: Long = SystemClock.uptimeMillis()): Boolean {
        val imageSegmenter = segmenter ?: return false
        if (!busy.compareAndSet(false, true)) return false
        return try {
            imageSegmenter.segmentAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
            true
        } catch (t: Throwable) {
            busy.set(false)
            Log.e(TAG, "detect() failed", t)
            false
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
            // Every accepted frame gets an answer, so a caller holding that frame can let go of it.
            onMask(null)
        }
        .build()

    private fun onResult(result: ImageSegmenterResult, input: MPImage) {
        busy.set(false)
        onMask(result.toHairMask(hairClass, softConfidence))
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

class StillImageHairSegmenter(
    context: Context,
    private val softConfidence: Boolean = false
) : AutoCloseable {

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
            .setOutputConfidenceMasks(true)
            .build()
        ImageSegmenter.createFromOptions(context, options)
    }.onFailure { error ->
        Log.e("StillHairSegmenter", "Failed to create still Hair Segmenter", error)
    }.getOrNull()

    private val hairClass: Int = segmenter?.let { resolveHairClass(it) } ?: HairLengthClassifier.HAIR_CLASS

    fun segment(bitmap: Bitmap): HairMask? {
        val result = segmenter?.segment(BitmapImageBuilder(bitmap).build()) ?: return null
        return result.toHairMask(hairClass, softConfidence)
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

/**
 * @param softConfidence read the hair confidence mask as the floats MediaPipe produces, when the
 *   8-bit read below comes back empty (it can't convert a float mask, leaving only the hard
 *   category mask). Needed wherever the edge of the hair has to fade rather than cut.
 */
private fun ImageSegmenterResult.toHairMask(hairClass: Int, softConfidence: Boolean): HairMask? {
    val image = categoryMask().orElse(null) ?: return null
    val buffer = runCatching { ByteBufferExtractor.extract(image) }.getOrNull() ?: return null
    buffer.rewind()
    val remaining = buffer.remaining().coerceAtLeast(0)
    if (remaining <= 0 || image.width <= 0 || image.height <= 0) return null
    val bytes = ByteArray(remaining)
    buffer.get(bytes)

    // Soft, per-pixel confidence for the hair class (when requested) instead of the binary
    // category mask, so the silhouette edge can fade out at the hairline rather than cut hard.
    val confidence = confidenceMasks().orElse(null)?.getOrNull(hairClass)?.let { hairConfidence ->
        runCatching {
            val confBuffer = ByteBufferExtractor.extract(hairConfidence, MPImage.IMAGE_FORMAT_ALPHA)
                ?: return@runCatching null
            confBuffer.rewind()
            val confRemaining = confBuffer.remaining().coerceAtLeast(0)
            if (confRemaining <= 0) return@runCatching null
            ByteArray(confRemaining).also { confBuffer.get(it) }
        }.getOrNull() ?: if (softConfidence) runCatching {
            // asReadOnlyBuffer() resets the byte order, so it has to be set again before reading floats.
            val floats = ByteBufferExtractor.extract(hairConfidence).order(ByteOrder.nativeOrder()).asFloatBuffer()
            ByteArray(floats.remaining()) { (floats.get(it) * 255f).toInt().coerceIn(0, 255).toByte() }
        }.getOrNull() else null
    }

    return HairMask(bytes, image.width, image.height, hairClass, confidence)
}
