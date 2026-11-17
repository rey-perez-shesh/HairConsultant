package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter

/**
 * Which part of the person each pixel is, from MediaPipe's multiclass selfie segmenter. Read by
 * normalized image point, so it lines up with the landmarks whatever the mask's own size.
 */
class FaceParsing(private val labels: ByteArray, val width: Int, val height: Int) {

    fun labelAt(x: Float, y: Float): Int {
        val mx = (x * width).toInt().coerceIn(0, width - 1)
        val my = (y * height).toInt().coerceIn(0, height - 1)
        return labels[my * width + mx].toInt() and 0xFF
    }

    fun isFaceSkin(x: Float, y: Float): Boolean = labelAt(x, y) == FACE_SKIN

    /** Skin or clothing, which the hair mask can claim at the face's edge or on dark clothes. */
    fun isNotHair(x: Float, y: Float): Boolean = labelAt(x, y).let { it == FACE_SKIN || it == BODY_SKIN || it == CLOTHES }

    companion object {
        // The model ships without label metadata; this is the order in MediaPipe's segmenter guide.
        const val BACKGROUND = 0
        const val HAIR = 1
        const val BODY_SKIN = 2
        const val FACE_SKIN = 3
        const val CLOTHES = 4
        const val OTHERS = 5
    }
}

/** Runs the multiclass segmenter on a scan frame or photo; only during a scan, not on every live frame. */
class FaceParsingSegmenter(context: Context) : AutoCloseable {

    private val segmenter: ImageSegmenter? = runCatching {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()
        ImageSegmenter.createFromOptions(context, options)
    }.onFailure { error ->
        Log.e(TAG, "Failed to create the multiclass segmenter", error)
    }.getOrNull()

    fun segment(bitmap: Bitmap): FaceParsing? {
        val model = segmenter ?: return null
        return runCatching {
            val result = synchronized(this) { model.segment(BitmapImageBuilder(bitmap).build()) }
            val image = result.categoryMask().orElse(null) ?: return null
            val buffer = ByteBufferExtractor.extract(image)
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            if (bytes.size < image.width * image.height) return null
            FaceParsing(bytes, image.width, image.height)
        }.onFailure { error ->
            Log.w(TAG, "segment() failed", error)
        }.getOrNull()
    }

    override fun close() {
        segmenter?.close()
    }

    companion object {
        private const val TAG = "FaceParsingSegmenter"
        const val MODEL_ASSET = "selfie_multiclass_256x256.tflite"
    }
}
