package com.hairconsultant.app.ui.facescan

import android.util.Log
import com.google.android.filament.MaterialInstance
import com.hairconsultant.app.data.analysis.FaceShapeClassifier
import com.hairconsultant.app.data.analysis.HeadPose
import com.hairconsultant.app.data.analysis.LandmarkPoint
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.colorOf
import io.github.sceneview.material.setColor
import io.github.sceneview.node.SphereNode

/**
 * Depth-only face occluder so the real face (camera under SceneView) appears to sit
 * in front of virtual hair where the GLB would otherwise cover eyes/nose/mouth.
 *
 * Uses Filament [MaterialInstance.setColorWrite] / [setDepthWrite]:
 * - NORMAL: invisible depth writer
 * - FACE_OCCLUSION debug: visible translucent cyan volume
 */
class FaceOcclusionController(
    private val sceneView: SceneView
) {
    private var node: SphereNode? = null
    private var material: MaterialInstance? = null

    fun ensureCreated() {
        if (node != null) return
        val mat = sceneView.materialLoader.createColorInstance(
            // Opaque material template (alpha=1) so Filament enables depthWrite;
            // colorWrite is then disabled for an invisible occluder.
            color = colorOf(0.15f, 0.95f, 0.95f, 1.0f)
        )
        applyMaterialMode(mat)
        material = mat
        val sphere = SphereNode(
            engine = sceneView.engine,
            radius = 1f,
            materialInstance = mat
        ).apply {
            isVisible = false
            // Render with other opaque geometry; depth writes occlude later hair fragments.
        }
        sceneView.addChildNode(sphere)
        node = sphere
        Log.i(TAG, "Face occlusion sphere created (depthWrite=true)")
    }

    fun setDebugVisible(visibleVolume: Boolean) {
        val mat = material ?: return
        applyMaterialMode(mat, visibleVolume)
    }

    fun update(
        pose: HeadPose,
        landmarks: List<LandmarkPoint>,
        mirrorX: Boolean,
        modelYawOffset: Float,
        modelYawSign: Float
    ) {
        ensureCreated()
        val sphere = node ?: return
        if (landmarks.size <= FaceShapeClassifier.CHIN) {
            sphere.isVisible = false
            return
        }

        val left = landmarks[FaceShapeClassifier.LEFT_CHEEK]
        val right = landmarks[FaceShapeClassifier.RIGHT_CHEEK]
        val top = landmarks[FaceShapeClassifier.FOREHEAD_TOP]
        val chin = landmarks[FaceShapeClassifier.CHIN]

        val midX = (left.x + right.x) * 0.5f
        val midY = (top.y + chin.y) * 0.52f
        val faceW = (right.x - left.x).coerceAtLeast(0.08f)
        val faceH = (chin.y - top.y).coerceAtLeast(0.08f)

        // Same image→scene mapping as HeadPoseEstimator / applyHeadPose.
        val rawX = (0.5f - midX) * 0.42f
        val posX = if (mirrorX) -rawX else rawX
        val posY = (0.38f - midY) * 0.95f
        // Slightly toward camera vs hair root so bangs behind this volume fail the depth test.
        val posZ = pose.positionZ + FACE_OCCLUDE_Z_BIAS

        val modelYaw = modelYawOffset + modelYawSign * pose.yawDeg
        sphere.position = Position(posX, posY, posZ)
        sphere.rotation = Rotation(pose.pitchDeg, modelYaw, pose.rollDeg)

        // Ellipsoid covering cheeks / eyes / nose / mouth; leaves scalp & side hair free.
        val rx = (faceW * FACE_WORLD_SCALE_X).coerceIn(0.07f, 0.30f)
        val ry = (faceH * FACE_WORLD_SCALE_Y).coerceIn(0.08f, 0.34f)
        val rz = (rx * FACE_DEPTH_RATIO).coerceIn(0.05f, 0.20f)
        sphere.scale = Scale(rx, ry, rz)
        sphere.isVisible = true
    }

    fun setVisible(visible: Boolean) {
        node?.isVisible = visible
    }

    fun destroy() {
        node?.let { sceneView.removeChildNode(it) }
        node = null
        material?.let { runCatching { sceneView.materialLoader.destroyMaterialInstance(it) } }
        material = null
    }

    private fun applyMaterialMode(mat: MaterialInstance, visibleVolume: Boolean = false) {
        if (visibleVolume) {
            // Debug: show the occluder volume (still writes depth).
            mat.setColorWrite(true)
            mat.setDepthWrite(true)
            mat.setDepthCulling(true)
            runCatching {
                mat.setColor(colorOf(0.15f, 0.95f, 0.95f, 0.45f))
            }
        } else {
            mat.setColorWrite(false)
            mat.setDepthWrite(true)
            mat.setDepthCulling(true)
        }
    }

    companion object {
        private const val TAG = "FaceOcclusion"
        /** World scale from normalized face width → ellipsoid radius. */
        private const val FACE_WORLD_SCALE_X = 0.58f
        private const val FACE_WORLD_SCALE_Y = 0.72f
        private const val FACE_DEPTH_RATIO = 0.80f
        private const val FACE_OCCLUDE_Z_BIAS = 0.05f
    }
}
