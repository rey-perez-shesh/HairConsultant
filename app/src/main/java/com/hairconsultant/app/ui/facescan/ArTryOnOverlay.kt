package com.hairconsultant.app.ui.facescan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.data.analysis.FaceShapeClassifier
import com.hairconsultant.app.data.analysis.HeadPose
import com.hairconsultant.app.data.analysis.HeadPoseEstimator
import com.hairconsultant.app.data.analysis.LandmarkPoint
import com.hairconsultant.app.domain.model.Haircut
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

private enum class TryOnMode { Loading, StaticGlb, TrackingGlb, Failed }

/** Live camera face composited above SceneView so GLB cannot cover facial features. */
private data class FacePunchFrame(
    val points: List<LandmarkPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val bitmap: Bitmap?
)

/** Debug HUD â€” headYaw is estimator yaw (â‰ˆ0 frontal); modelYaw includes MODEL_YAW_OFFSET. */
private data class ArDebugSnapshot(
    val points: List<LandmarkPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val headCenterX: Float,
    val headCenterY: Float,
    val crownX: Float,
    val crownY: Float,
    val headWidth: Float,
    val headYaw: Float,
    val headPitch: Float,
    val headRoll: Float,
    val modelYaw: Float,
    val modelYawOffset: Float,
    val scale: Float,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val glbMinY: Float,
    val glbMaxY: Float,
    val glbWidth: Float,
    val mirrorX: Boolean
)

/**
 * Transparent SceneView + prepared crown-origin GLB over the normal camera.
 * Tracking uses HeadPoseEstimator + single HAIR_MIRROR_X.
 * No background blur / face cutout compositing.
 */
@Composable
fun BoxScope.ArTryOnOverlay(
    haircut: Haircut,
    landmarkStore: FaceLandmarkStore,
    faceArAttachment: FaceArSceneAttachment,
    hairColor: HairColorPreset = HairColorPreset.NATURAL
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mode by remember { mutableStateOf(TryOnMode.Loading) }
    var status by remember { mutableStateOf("Try-on â€” ${haircut.name}") }
    var statusIsError by remember { mutableStateOf(false) }
    var modelNodeRef by remember { mutableStateOf<ModelNode?>(null) }
    var modelBounds by remember { mutableStateOf<WigModelBounds?>(null) }
    var faceOcclusion by remember { mutableStateOf<FaceOcclusionController?>(null) }
    var poseUpdateCount by remember { mutableIntStateOf(0) }
    var debugSnapshot by remember { mutableStateOf<ArDebugSnapshot?>(null) }
    var facePunch by remember { mutableStateOf<FacePunchFrame?>(null) }

    // SceneView stays alive across hairstyle switches â€” only the ModelNode/GLB is replaced.
    // Camera + MediaPipe + HeadPoseEstimator are owned by CameraPreview and are not restarted here.
    DisposableEffect(lifecycleOwner, faceArAttachment) {
        mode = TryOnMode.Loading
        status = "Try-on â€” ${haircut.name}"
        statusIsError = false
        modelNodeRef = null
        modelBounds = null
        faceOcclusion = null
        poseUpdateCount = 0
        debugSnapshot = null
        facePunch?.bitmap?.takeUnless { it.isRecycled }?.recycle()
        facePunch = null

        val sceneView = createOverlaySceneView(context, lifecycleOwner)
        faceArAttachment.setSceneView(sceneView)

        onDispose {
            facePunch?.bitmap?.takeUnless { it.isRecycled }?.recycle()
            facePunch = null
            faceOcclusion?.destroy()
            faceOcclusion = null
            modelNodeRef?.let { node -> sceneView.removeChildNode(node) }
            modelNodeRef = null
            modelBounds = null
            faceArAttachment.setSceneView(null)
        }
    }

    val wigAsset = remember(haircut.id, haircut.name) { HairstyleArCatalog.modelAssetFor(haircut) }
    val assetConfig = remember(haircut.id, haircut.name) { HairstyleArCatalog.configFor(haircut) }

    LaunchedEffect(haircut.id, wigAsset, faceArAttachment) {
        val sceneView = faceArAttachment.sceneView ?: return@LaunchedEffect

        // Hide/remove the previously active hairstyle before loading the next (one GLB at a time).
        mode = TryOnMode.Loading
        status = "Loading ${haircut.name}â€¦"
        statusIsError = false
        modelNodeRef?.let { node ->
            runCatching { sceneView.removeChildNode(node) }
        }
        modelNodeRef = null
        modelBounds = null
        faceOcclusion?.destroy()
        faceOcclusion = null

        val loaded = try {
            withTimeout(GLB_LOAD_TIMEOUT_MS) {
                sceneView.modelLoader.loadModelInstance(wigAsset)
            }
        } catch (_: TimeoutCancellationException) {
            status = "GLB load timed out"
            statusIsError = true
            null
        } catch (t: Throwable) {
            Log.e(TAG, "GLB load failed: $wigAsset", t)
            status = "GLB load failed (${t.message ?: "error"})"
            statusIsError = true
            null
        }

        if (loaded == null) {
            mode = TryOnMode.Failed
            sceneView.visibility = View.GONE
            return@LaunchedEffect
        }

        val bounds = WigModelBounds.from(loaded)
        Log.i(
            TAG,
            "GLB loaded $wigAsset for ${haircut.id}/${haircut.name} " +
                "AABB center=(${bounds.centerX},${bounds.centerY},${bounds.centerZ}) " +
                "size=(${bounds.width},${bounds.height},${bounds.depth})"
        )
        modelBounds = bounds

        // Depth occluder only when this asset needs face punch-through (e.g. long bangs).
        // Boy/buzz cuts skip it â€” a face-shaped depth hole looks like a second face in the hair.
        val useFaceCutout = assetConfig?.enableFaceCutout != false
        if (useFaceCutout) {
            val occluder = FaceOcclusionController(sceneView).also { it.ensureCreated() }
            faceOcclusion = occluder
        } else {
            faceOcclusion = null
        }

        val node = ModelNode(
            modelInstance = loaded,
            autoAnimate = false,
            scaleToUnits = null,
            centerOrigin = null
        ).apply {
            setCulling(false)
            isVisible = !AR_HIDE_GLB
            position = Position(0f, 0f, 0f)
            rotation = Rotation(0f, 180f, 0f)
            scale = Scale(1f)
        }
        sceneView.addChildNode(node)
        modelNodeRef = node
        Log.i(TAG, "GLB ready â€” haircut=${haircut.name} color=${hairColor.name} asset=$wigAsset")
        HairMaterialTint.apply(node, hairColor, wigAsset)
        SceneViewTransparency.configure(sceneView, WIG_CAMERA_Z)
        sceneView.visibility = View.VISIBLE
        mode = TryOnMode.TrackingGlb
        status = "AR tracking â€” ${haircut.name} Â· waiting for faceâ€¦"
        statusIsError = false
    }

    // Tint only â€” does not touch crown / pose / scale / scene recreate.
    LaunchedEffect(hairColor, modelNodeRef, mode, wigAsset) {
        if (mode != TryOnMode.TrackingGlb) return@LaunchedEffect
        val node = modelNodeRef ?: return@LaunchedEffect
        Log.i(TAG, "Applying hair color ${hairColor.name} to live ModelNode")
        HairMaterialTint.apply(node, hairColor, wigAsset)
    }

    LaunchedEffect(mode, modelNodeRef, modelBounds, faceOcclusion, landmarkStore, haircut.id, assetConfig) {
        if (mode != TryOnMode.TrackingGlb) return@LaunchedEffect
        val node = modelNodeRef ?: return@LaunchedEffect
        val bounds = modelBounds ?: return@LaunchedEffect
        val occluder = faceOcclusion
        var smooth: HeadPose? = null
        var updates = 0

        landmarkStore.overlay.collect { frame ->
            if (!frame.faceDetected || frame.points.size <= FaceShapeClassifier.CHIN) {
                node.isVisible = false
                occluder?.setVisible(false)
                facePunch?.bitmap?.takeUnless { it.isRecycled }?.recycle()
                facePunch = null
                return@collect
            }
            val raw = HeadPoseEstimator.estimate(frame.points) ?: return@collect
            smooth = HeadPoseEstimator.smooth(smooth, raw)
            val applied = applyHeadPose(node, smooth!!, frame.points, bounds, assetConfig)
            val useFaceCutout = assetConfig?.enableFaceCutout != false
            if (useFaceCutout) {
                occluder?.update(
                    pose = smooth!!,
                    landmarks = frame.points,
                    mirrorX = HAIR_MIRROR_X,
                    modelYawOffset = MODEL_YAW_OFFSET,
                    modelYawSign = 1f
                )
                // Sharp face over SceneView (store recycles live bitmaps each detect â€” copy frame).
                val src = landmarkStore.peekLatestBitmap()
                    ?.takeUnless { it.isRecycled }
                    ?: landmarkStore.peekPreviewBitmap()?.takeUnless { it.isRecycled }
                val punchBmp = src?.let { b ->
                    runCatching { b.copy(b.config ?: Bitmap.Config.ARGB_8888, false) }.getOrNull()
                }
                val previousPunch = facePunch?.bitmap
                facePunch = FacePunchFrame(
                    points = frame.points,
                    imageWidth = frame.imageWidth,
                    imageHeight = frame.imageHeight,
                    bitmap = punchBmp
                )
                if (previousPunch != null && previousPunch !== punchBmp && !previousPunch.isRecycled) {
                    previousPunch.recycle()
                }
            } else {
                occluder?.setVisible(false)
                facePunch?.bitmap?.takeUnless { it.isRecycled }?.recycle()
                facePunch = null
            }
            updates++
            poseUpdateCount = updates
            if (updates % 15 == 0) {
                status = "AR tracking â€” ${haircut.name} Â· $updates pose updates"
            }
            if (DEBUG_AR) {
                val left = frame.points[FaceShapeClassifier.LEFT_CHEEK]
                val right = frame.points[FaceShapeClassifier.RIGHT_CHEEK]
                val top = frame.points[FaceShapeClassifier.FOREHEAD_TOP]
                val chin = frame.points[FaceShapeClassifier.CHIN]
                val faceH = (chin.y - top.y).coerceAtLeast(0.08f)
                debugSnapshot = ArDebugSnapshot(
                    points = frame.points,
                    imageWidth = frame.imageWidth,
                    imageHeight = frame.imageHeight,
                    headCenterX = (left.x + right.x) * 0.5f,
                    headCenterY = (top.y + chin.y) * 0.5f,
                    crownX = (left.x + right.x) * 0.5f,
                    crownY = (top.y - faceH * 0.38f) - CROWN_VERTICAL_OFFSET / 0.95f,
                    headWidth = applied.headWidthNorm,
                    headYaw = smooth!!.yawDeg,
                    headPitch = smooth!!.pitchDeg,
                    headRoll = smooth!!.rollDeg,
                    modelYaw = applied.modelYaw,
                    modelYawOffset = MODEL_YAW_OFFSET,
                    scale = applied.scale,
                    posX = applied.posX,
                    posY = applied.posY,
                    posZ = applied.posZ,
                    glbMinY = bounds.centerY - bounds.height * 0.5f,
                    glbMaxY = bounds.centerY + bounds.height * 0.5f,
                    glbWidth = bounds.width,
                    mirrorX = HAIR_MIRROR_X
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Above SceneView: live face so 3D hair cannot block eyes/nose/mouth/cheeks.
        facePunch?.let { punch ->
            FaceVisibilityPunchThrough(
                points = punch.points,
                imageWidth = punch.imageWidth,
                imageHeight = punch.imageHeight,
                cameraBitmap = punch.bitmap
            )
        }
        if (DEBUG_AR) {
            ArDebugGuidesOnly(debugSnapshot)
            Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = if (debugSnapshot != null) {
                        val d = debugSnapshot!!
                        "headYaw=${"%.1f".format(d.headYaw)} modelYaw=${"%.0f".format(d.modelYaw)} " +
                            "off=${"%.0f".format(d.modelYawOffset)} w=${"%.2f".format(d.headWidth)} " +
                            "s=${"%.3f".format(d.scale)} glbW=${"%.2f".format(d.glbWidth)} " +
                            "origY=[${"%.2f".format(d.glbMinY)},${"%.2f".format(d.glbMaxY)}]"
                    } else {
                        status
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else if (statusIsError) {
    Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
    ) {
        Text(
                    text = status,
            style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun WigWarmupHost(enabled: Boolean) = Unit

/** Guides only â€” NEVER draws a second camera Bitmap. */
@Composable
private fun ArDebugGuidesOnly(snapshot: ArDebugSnapshot?) {
    val snap = snapshot ?: return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val imgW = snap.imageWidth.toFloat().coerceAtLeast(1f)
        val imgH = snap.imageHeight.toFloat().coerceAtLeast(1f)
        val scale = maxOf(size.width / imgW, size.height / imgH)
        val ox = (size.width - imgW * scale) / 2f
        val oy = (size.height - imgH * scale) / 2f
        fun map(nx: Float, ny: Float) = Offset(nx * imgW * scale + ox, ny * imgH * scale + oy)

        snap.points.forEach { p ->
            drawCircle(Color(0xFFF6E27A), radius = 2.5f, center = map(p.x, p.y))
        }
        drawCircle(
            Color(0xFF4FC3F7),
            radius = 10f,
            center = map(snap.headCenterX, snap.headCenterY),
            style = Stroke(3f)
        )
        drawCircle(
            Color(0xFFE91E63),
            radius = 12f,
            center = map(snap.crownX, snap.crownY),
            style = Stroke(3f)
        )
        val half = snap.headWidth * 0.5f
        drawLine(
            Color(0xFF69F0AE),
            map(snap.headCenterX - half, snap.crownY),
            map(snap.headCenterX + half, snap.crownY),
            strokeWidth = 4f
        )
    }
}

private fun createOverlaySceneView(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
): SceneView = SceneView(
    context = context,
    isOpaque = false,
    cameraManipulator = null
).apply {
    lifecycle = lifecycleOwner.lifecycle
    visibility = View.GONE
    isClickable = false
    isFocusable = false
    SceneViewTransparency.configure(this, WIG_CAMERA_Z)
}

private data class WigModelBounds(
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val width: Float,
    val height: Float,
    val depth: Float
) {
    companion object {
        fun from(modelInstance: io.github.sceneview.model.ModelInstance): WigModelBounds {
            val box = modelInstance.asset.boundingBox
            val c = box.center
            val h = box.halfExtent
            return WigModelBounds(
                centerX = c[0],
                centerY = c[1],
                centerZ = c[2],
                width = (h[0] * 2f).coerceAtLeast(1e-4f),
                height = (h[1] * 2f).coerceAtLeast(1e-4f),
                depth = (h[2] * 2f).coerceAtLeast(1e-4f)
            )
        }
    }
}

private data class AppliedHairTransform(
    val headWidthNorm: Float,
    val modelYaw: Float,
    val scale: Float,
    val posX: Float,
    val posY: Float,
    val posZ: Float
)

private fun applyHeadPose(
    node: ModelNode,
    pose: HeadPose,
    landmarks: List<LandmarkPoint>,
    bounds: WigModelBounds,
    assetConfig: HairstyleArCatalog.AssetConfig? = null
): AppliedHairTransform {
    node.isVisible = !AR_HIDE_GLB

    val left = landmarks[FaceShapeClassifier.LEFT_CHEEK]
    val right = landmarks[FaceShapeClassifier.RIGHT_CHEEK]
    val faceWidth = (right.x - left.x).coerceIn(0.08f, 0.72f)
    val headWidthNorm = estimateHeadWidth(landmarks, faceWidth)

    // Single mirror: estimator uses (0.5-midX); negate once for SceneView +X = screen right.
    val posX = if (HAIR_MIRROR_X) -pose.positionX else pose.positionX
    val posY = pose.positionY + CROWN_VERTICAL_OFFSET
    val posZ = pose.positionZ

    // Head-width scale (NOT the 0.42 position factor â€” that made the hair tiny).
    // scale = (headWidth / HAIR_REFERENCE_WIDTH) * HAIR_SCALE_MULTIPLIER * (HAIR_REFERENCE_WIDTH / glbWidth)
    //       = headWidth * HAIR_SCALE_MULTIPLIER / glbWidth
    val widthRatio = headWidthNorm / HAIR_REFERENCE_WIDTH
    val localScaleMul = assetConfig?.localScaleMul ?: 1f
    val scale = (widthRatio * HAIR_SCALE_MULTIPLIER * (HAIR_REFERENCE_WIDTH / bounds.width) *
        localScaleMul * HAIR_FIT_SCALE)
        .coerceIn(0.05f, 1.5f)

    // Optional per-asset local offset (Meshy centered origins â†’ align AABB maxY to crown).
    // Does not change the shared crown / tracking math.
    val localOx = assetConfig?.localOffsetX ?: 0f
    val localOy = localCrownAlignOffsetY(bounds, assetConfig) + (assetConfig?.localOffsetY ?: 0f)
    val localOz = assetConfig?.localOffsetZ ?: 0f
    node.position = Position(
        posX + localOx * scale,
        posY + localOy * scale,
        posZ + localOz * scale
    )
    node.scale = Scale(scale)

    // ONE asset facing correction: GLB forward is opposite SceneView camera forward.
    val modelYaw = MODEL_YAW_OFFSET + pose.yawDeg
    node.rotation = Rotation(pose.pitchDeg, modelYaw, pose.rollDeg)

    return AppliedHairTransform(headWidthNorm, modelYaw, scale, posX, posY, posZ)
}

/**
 * Model-space Y so mesh top sits on the crown node origin for centered Meshy exports.
 * Prepared crown-origin GLBs (maxY â‰ˆ 0) return 0.
 */
private fun localCrownAlignOffsetY(
    bounds: WigModelBounds,
    config: HairstyleArCatalog.AssetConfig?
): Float {
    if (config?.alignCrownToMaxY != true) return 0f
    val maxY = bounds.centerY + bounds.height * 0.5f
    return if (kotlin.math.abs(maxY) < 0.08f) 0f else -maxY
}

/**
 * Hides the inner underside of a single-mesh Meshy hair shell (gray forehead â€œhelmetâ€ plate)
 * without shrinking or fading the whole GLB. Enables back-face culling so only the outer
 * hair surface is drawn across the face opening.
 */
private fun estimateHeadWidth(landmarks: List<LandmarkPoint>, faceWidth: Float): Float {
    val foreheadWidth = if (landmarks.size > FaceShapeClassifier.RIGHT_TEMPLE) {
        (landmarks[FaceShapeClassifier.RIGHT_TEMPLE].x -
            landmarks[FaceShapeClassifier.LEFT_TEMPLE].x).coerceIn(0.08f, 0.72f)
    } else {
        faceWidth
    }
    return maxOf(faceWidth, foreheadWidth).coerceIn(0.10f, 0.72f)
}

/** Default try-on GLB when a catalog card has no dedicated mapping. */
const val WIG_ASSET = "wigs/Meshy_AI__buzzcut_texture.glb"

private const val WIG_CAMERA_Z = 1.0f
private const val GLB_LOAD_TIMEOUT_MS = 60_000L
private const val TAG = "ArTryOnOverlay"

// ---------------------------------------------------------------------------
// Calibration (unchanged)
// ---------------------------------------------------------------------------

/** Set true to hide the GLB while verifying headYaw/pitch/roll alone. */
private const val AR_HIDE_GLB = false

/**
 * SceneView +Y nudge for the shared crown anchor (debug line + GLB origin).
 * + = higher on the head. Horizontally unchanged.
 */
private const val CROWN_VERTICAL_OFFSET = 0.08f

/** Negate estimator X once so SceneView +X matches screen right. */
private const val HAIR_MIRROR_X = true

/**
 * Single asset facing correction (degrees). GLB forward faces opposite the SceneView camera,
 * so frontal headYawâ‰ˆ0 requires modelYawâ‰ˆ180. Do not add 180 anywhere else.
 */
private const val MODEL_YAW_OFFSET = 180f

/** Typical normalized cheek span (diagnosis / ratio base). Live scale uses measured head width. */
private const val HAIR_REFERENCE_WIDTH = 0.34f

/**
 * scale = (headWidth / HAIR_REFERENCE_WIDTH) * HAIR_SCALE_MULTIPLIER * (HAIR_REFERENCE_WIDTH / glbWidth)
 * â†‘ larger Â· â†“ smaller. Calibrated so wâ‰ˆ0.40 â‰ˆ head-sized for Waves_AR_Prepared (glbWâ‰ˆ1.47).
 */
private const val HAIR_SCALE_MULTIPLIER = 1.25f

/**
 * Uniform fit vs the client's head. Applied only to final ModelNode scale (and local asset offsets
 * that already scale with it). Does not change crown / pose / rotation.
 * Revert: set to `1f`. If still oversized, try `0.80f` then `0.75f`.
 */
private const val HAIR_FIT_SCALE = 0.85f


