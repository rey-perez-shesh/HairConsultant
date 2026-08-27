package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.hairconsultant.app.domain.model.HairTexture
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class HairTypeCnnResult(val texture: HairTexture, val confidence: Float)

/**
 * MobileNetV2 transfer-learning classifier trained (see ml/train_classifiers.ipynb) on the
 * kavyasreeb Kaggle hair-type dataset: Straight/Wavy/Curly/Coily (its Kinky class was renamed to
 * Coily; its Dreadlocks class was dropped as a hairstyle rather than a texture).
 *
 * Labels are hardcoded in the exact alphabetical order Keras's `image_dataset_from_directory`
 * assigns them at training time (folder names match [HairTexture] enum names exactly), rather
 * than relying on model metadata, since the notebook exports a plain Keras -> TFLite model.
 *
 * The asset is optional: until hair_type_classifier.tflite is dropped into assets/, [isReady] is
 * false and [classify] returns null so callers keep using the pixel-heuristic [HairAppearanceClassifier].
 */
class HairTypeTfliteClassifier(context: Context) : AutoCloseable {

    /** TEMPORARY diagnostic: the load failure reason, surfaced in-chat since Logcat isn't visible. */
    var loadError: String? = null
        private set

    private val interpreter: Interpreter? = runCatching {
        Interpreter(loadModelFile(context, MODEL_ASSET))
    }.onFailure { error ->
        loadError = "${error::class.qualifiedName}: ${error.message}"
        Log.i(TAG, "$MODEL_ASSET not bundled; using pixel heuristic only", error)
    }.getOrNull()?.also { model ->
        val inShape = runCatching { model.getInputTensor(0).shape()?.joinToString() }.getOrNull() ?: "?"
        val outShape = runCatching { model.getOutputTensor(0).shape()?.joinToString() }.getOrNull() ?: "?"
        Log.i(TAG, "$MODEL_ASSET loaded: input=[$inShape] output=[$outShape] labels=${LABELS.toList()}")
    }

    val isReady: Boolean get() = interpreter != null

    fun classify(bitmap: Bitmap, mask: HairMask): HairTypeCnnResult? {
        val model = interpreter ?: return null
        val crop = cropToHair(bitmap, mask) ?: return null
        val input = bitmapToInputBuffer(crop)
        val output = Array(1) { FloatArray(LABELS.size) }
        runCatching { model.run(input, output) }
            .onFailure { error -> Log.w(TAG, "classify() failed", error) }
            .getOrElse { return null }

        val scores = output[0]
        val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: return null
        val texture = runCatching { HairTexture.valueOf(LABELS[bestIndex]) }.getOrNull() ?: return null
        Log.d(TAG, "scores=${scores.toList()} -> $texture (${scores[bestIndex]})")
        return HairTypeCnnResult(texture, scores[bestIndex])
    }

    private fun cropToHair(bitmap: Bitmap, mask: HairMask): Bitmap? {
        var minX = mask.width; var maxX = -1
        var minY = mask.height; var maxY = -1
        for (y in 0 until mask.height) {
            val row = y * mask.width
            for (x in 0 until mask.width) {
                if (!mask.isHair(row + x)) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX || maxY < minY) return null

        val left = (minX * bitmap.width / mask.width).coerceIn(0, bitmap.width - 1)
        val top = (minY * bitmap.height / mask.height).coerceIn(0, bitmap.height - 1)
        val right = (maxX * bitmap.width / mask.width + 1).coerceIn(left + 1, bitmap.width)
        val bottom = (maxY * bitmap.height / mask.height + 1).coerceIn(top + 1, bitmap.height)
        return runCatching { Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top) }.getOrNull()
    }

    override fun close() {
        interpreter?.close()
    }

    companion object {
        private const val TAG = "HairTypeTfliteClassifier"
        const val MODEL_ASSET = "hair_type_classifier.tflite"

        /** Must match the alphabetical class order the training notebook's dataset folders produce. */
        private val LABELS = arrayOf("COILY", "CURLY", "STRAIGHT", "WAVY")
    }
}

/**
 * Resizes to 224x224 and scales pixels to [-1, 1], matching Keras's
 * `mobilenet_v2.preprocess_input` used when training this classifier.
 */
internal fun bitmapToInputBuffer(bitmap: Bitmap, inputSize: Int = 224): ByteBuffer {
    val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
    val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
    val pixels = IntArray(inputSize * inputSize)
    resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
    for (pixel in pixels) {
        buffer.putFloat((((pixel shr 16) and 0xFF) / 127.5f) - 1f)
        buffer.putFloat((((pixel shr 8) and 0xFF) / 127.5f) - 1f)
        buffer.putFloat(((pixel and 0xFF) / 127.5f) - 1f)
    }
    buffer.rewind()
    return buffer
}
