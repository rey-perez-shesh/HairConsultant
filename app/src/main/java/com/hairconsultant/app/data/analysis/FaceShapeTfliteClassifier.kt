package com.hairconsultant.app.data.analysis

import android.content.Context
import android.util.Log
import com.hairconsultant.app.domain.model.FaceShape
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Small feedforward classifier trained (see ml/train_classifiers.ipynb) on the same four
 * landmark-ratio features the geometric [FaceShapeClassifier] heuristic already computes
 * (lengthToWidth, jawToCheek, foreheadToJaw, foreheadToCheek) — not on raw pixels. An earlier
 * pixel-based CNN version of this classifier kept producing degenerate results in the app despite
 * looking fine in isolated Colab evaluation, traced back to a framing/crop mismatch between the
 * training photos and what the app's live camera crop actually looks like. Training on these four
 * numbers instead removes that whole failure class: whoever computes them (Python during training,
 * Kotlin here at inference) is measuring the same landmark geometry, so there's no image
 * preprocessing that can drift out of sync.
 *
 * Trained on Heart/Oval/Round/Square only — Diamond has no usable training data (see the
 * notebook's notes) and is decided by [FaceShapeClassifier] from the population statistics in
 * this model's normalization layer.
 *
 * Labels are hardcoded in the exact alphabetical order Keras's `image_dataset_from_directory` (via
 * the training labels array) assigns them, matching [FaceShape] enum names exactly.
 *
 * If the asset fails to load, [isReady] is false and [probabilities] returns null, so
 * [FaceShapeClassifier.decide] uses its population-based fallback.
 */
class FaceShapeTfliteClassifier(context: Context) : AutoCloseable {

    /** TEMPORARY diagnostic: the load failure reason, surfaced in-chat since Logcat isn't visible. */
    var loadError: String? = null
        private set

    private val interpreter: Interpreter? = runCatching {
        Interpreter(loadModelFile(context, MODEL_ASSET))
    }.onFailure { error ->
        loadError = "${error::class.qualifiedName}: ${error.message}"
        Log.i(TAG, "$MODEL_ASSET not bundled; using geometric heuristic only", error)
    }.getOrNull()?.also { model ->
        val inShape = runCatching { model.getInputTensor(0).shape()?.joinToString() }.getOrNull() ?: "?"
        val outShape = runCatching { model.getOutputTensor(0).shape()?.joinToString() }.getOrNull() ?: "?"
        Log.i(TAG, "$MODEL_ASSET loaded: input=[$inShape] output=[$outShape] labels=${LABELS.toList()}")
    }

    val isReady: Boolean get() = interpreter != null

    private val inputSize: Int =
        interpreter?.let { runCatching { it.getInputTensor(0).shape().last() }.getOrNull() } ?: 4

    /** How likely each of Heart/Oval/Round/Square is (summing to 1), or null when the model isn't loaded. */
    fun probabilities(metrics: FaceShapeMetrics): Map<FaceShape, Float>? {
        val model = interpreter ?: return null
        val ratios = floatArrayOf(metrics.lengthToWidth, metrics.jawToCheek, metrics.foreheadToJaw, metrics.foreheadToCheek)
        // The bundled model takes the four ratios; one retrained by the current notebook also takes
        // jaw angle and chin width, in this order.
        val input = arrayOf(if (inputSize >= 6) ratios + floatArrayOf(metrics.jawAngleDeg, metrics.chinToJaw) else ratios)
        val output = Array(1) { FloatArray(LABELS.size) }
        runCatching { synchronized(model) { model.run(input, output) } }
            .onFailure { error -> Log.w(TAG, "probabilities() failed", error) }
            .getOrElse { return null }
        return LABELS.indices.associate { FaceShape.valueOf(LABELS[it]) to output[0][it] }
    }

    override fun close() {
        interpreter?.close()
    }

    companion object {
        private const val TAG = "FaceShapeTfliteClassifier"
        const val MODEL_ASSET = "face_shape_classifier.tflite"

        /** Must match the alphabetical class order the training notebook's dataset folders produce. */
        private val LABELS = arrayOf("HEART", "OVAL", "ROUND", "SQUARE")
    }
}

/** Reads a TFLite model out of assets/ as a memory-mapped buffer for [Interpreter]. */
internal fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
    val afd = context.assets.openFd(assetName)
    return FileInputStream(afd.fileDescriptor).use { input ->
        input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }
}
