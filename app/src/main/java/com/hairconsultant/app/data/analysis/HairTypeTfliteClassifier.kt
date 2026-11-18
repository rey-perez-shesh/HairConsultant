package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.hairconsultant.app.domain.model.HairTexture
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MobileNetV2 transfer-learning classifier trained (see ml/train_classifiers.ipynb) on the
 * kavyasreeb Kaggle hair-type dataset: Straight/Wavy/Curly (its Kinky class is merged into Curly
 * rather than kept as its own Coily class — the app doesn't distinguish them; its Dreadlocks
 * class was dropped as a hairstyle rather than a texture).
 *
 * Labels are hardcoded in the exact alphabetical order Keras's `image_dataset_from_directory`
 * assigns them at training time (folder names match [HairTexture] enum names exactly), rather
 * than relying on model metadata, since the notebook exports a plain Keras -> TFLite model.
 *
 * The asset is optional: until hair_type_classifier.tflite is dropped into assets/, [isReady] is
 * false and [probabilities] returns null so callers keep using the pixel-heuristic [HairAppearanceClassifier].
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

    /**
     * How likely each texture is (summing to 1) for the hair in [mask], or null without the model
     * or any hair. The hair's bounding box also holds the face and background, about as large as
     * the hair itself, so the model reads it twice, as cropped and with everything but hair greyed
     * out, and the two are averaged: the plain crop is closest to how the training photos were
     * framed, the masked one leaves only the hair to judge.
     */
    fun probabilities(bitmap: Bitmap, mask: HairMask): Map<HairTexture, Float>? {
        val model = interpreter ?: return null
        val box = hairBox(bitmap, mask) ?: return null
        val crop = runCatching { Bitmap.createBitmap(bitmap, box.left, box.top, box.width(), box.height()) }.getOrNull()
            ?: return null
        val plain = run(model, bitmapToInputBuffer(crop)) ?: return null
        val masked = run(model, bitmapToInputBuffer(crop) { x, y ->
            mask.isHairAt(
                (box.left + (x + 0.5f) * box.width() / INPUT_SIZE) / bitmap.width,
                (box.top + (y + 0.5f) * box.height() / INPUT_SIZE) / bitmap.height
            )
        })
        // createBitmap hands back the frame itself when the box covers all of it.
        if (crop !== bitmap) crop.recycle()
        return LABELS.indices.associate { i ->
            HairTexture.valueOf(LABELS[i]) to (masked?.let { (plain[i] + it[i]) / 2f } ?: plain[i])
        }
    }

    private fun run(model: Interpreter, input: ByteBuffer): FloatArray? {
        val output = Array(1) { FloatArray(LABELS.size) }
        return runCatching { synchronized(model) { model.run(input, output) } }
            .onFailure { error -> Log.w(TAG, "probabilities() failed", error) }
            .map { output[0] }
            .getOrNull()
    }

    private fun hairBox(bitmap: Bitmap, mask: HairMask): Rect? {
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
        return Rect(left, top, right, bottom)
    }

    override fun close() {
        interpreter?.close()
    }

    companion object {
        private const val TAG = "HairTypeTfliteClassifier"
        const val MODEL_ASSET = "hair_type_classifier.tflite"

        /** Must match the alphabetical class order the training notebook's dataset folders produce. */
        private val LABELS = arrayOf("CURLY", "STRAIGHT", "WAVY")

        private const val INPUT_SIZE = 224
    }
}

/**
 * Resizes to 224x224 and scales pixels to [-1, 1], matching Keras's
 * `mobilenet_v2.preprocess_input` used when training this classifier. Pixels [keep] rejects (x, y
 * in the resized image) become 0, mid-grey on that scale.
 */
internal fun bitmapToInputBuffer(bitmap: Bitmap, inputSize: Int = 224, keep: ((Int, Int) -> Boolean)? = null): ByteBuffer {
    val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
    val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
    val pixels = IntArray(inputSize * inputSize)
    resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
    if (resized !== bitmap) resized.recycle()
    pixels.forEachIndexed { index, pixel ->
        if (keep != null && !keep(index % inputSize, index / inputSize)) {
            repeat(3) { buffer.putFloat(0f) }
        } else {
            buffer.putFloat((((pixel shr 16) and 0xFF) / 127.5f) - 1f)
            buffer.putFloat((((pixel shr 8) and 0xFF) / 127.5f) - 1f)
            buffer.putFloat(((pixel and 0xFF) / 127.5f) - 1f)
        }
    }
    buffer.rewind()
    return buffer
}
