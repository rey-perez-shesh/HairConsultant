package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import kotlinx.coroutines.tasks.await

/** Second-opinion face mesh used only on Scan / photo — not on every camera frame. */
class MlKitFaceMeshVerifier(context: Context) : AutoCloseable {

    private val detector = FaceMeshDetection.getClient(
        FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build()
    )

    suspend fun classify(bitmap: Bitmap): FaceShapeClassification? {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val meshes = detector.process(image).await()
            val mesh = meshes.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
            val points = MutableList(468) { LandmarkPoint(0f, 0f) }
            val width = bitmap.width.toFloat().coerceAtLeast(1f)
            val height = bitmap.height.toFloat().coerceAtLeast(1f)
            for (point in mesh.allPoints) {
                val index = point.index
                if (index in points.indices) {
                    val pos = point.position
                    points[index] = LandmarkPoint(pos.x / width, pos.y / height)
                }
            }
            FaceShapeClassifier.classify(points, bitmap.width, bitmap.height)
        }.onFailure { error ->
            Log.w(TAG, "ML Kit face mesh failed", error)
        }.getOrNull()
    }

    override fun close() {
        detector.close()
    }

    companion object {
        private const val TAG = "MlKitFaceMeshVerifier"
    }
}
