package com.hairconsultant.app.ui.facescan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.data.analysis.FaceShapeClassifier
import com.hairconsultant.app.data.analysis.HeadPose
import com.hairconsultant.app.data.analysis.HeadPoseEstimator
import com.hairconsultant.app.data.analysis.LandmarkPoint
import com.hairconsultant.app.domain.model.Haircut
import kotlin.math.max
import kotlin.math.sin

/**
 * Temporary 2D transparent PNG hairstyle overlay on the existing CameraPreview stack.
 *
 * Reuses MediaPipe landmarks via [FaceLandmarkStore] and [HeadPoseEstimator] for crown
 * attach, scale, roll, and smooth tracking. Does not touch SceneView / GLB / hair mask /
 * background blur — those stay owned by [CameraPreview] and [ArTryOnOverlay].
 *
 * Swap path later: same haircut id → [HairstyleArCatalog.AssetConfig] GLB instead of PNG.
 */
@Composable
fun BoxScope.PngTryOnOverlay(
    haircut: Haircut,
    landmarkStore: FaceLandmarkStore
) {
    val context = LocalContext.current
    val config = remember(haircut.id, haircut.name, haircut.imageUrl) {
        HairstyleArCatalog.pngConfigFor(haircut)
    }
    var hairBitmap by remember(config?.drawableName) { mutableStateOf<Bitmap?>(null) }
    var smoothPose by remember { mutableStateOf<HeadPose?>(null) }
    var crown by remember { mutableStateOf<CrownAnchor?>(null) }

    DisposableEffect(config?.drawableName) {
        val cfg = config
        var loaded: Bitmap? = null
        if (cfg != null) {
            val resId = HairstyleArCatalog.resolvePngDrawableRes(context, cfg)
            if (resId != 0) {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                loaded = BitmapFactory.decodeResource(context.resources, resId, opts)
            }
        }
        hairBitmap = loaded
        onDispose {
            loaded?.takeUnless { it.isRecycled }?.recycle()
            if (hairBitmap === loaded) hairBitmap = null
        }
    }

    LaunchedEffect(haircut.id, config) {
        if (config == null) return@LaunchedEffect
        smoothPose = null
        landmarkStore.overlay.collect { frame ->
            if (!frame.faceDetected || frame.points.size <= FaceShapeClassifier.CHIN) {
                crown = null
                return@collect
            }
            val raw = HeadPoseEstimator.estimate(frame.points) ?: return@collect
            val smoothed = HeadPoseEstimator.smooth(smoothPose, raw)
            smoothPose = smoothed
            crown = crownFromLandmarks(frame.points, smoothed)
        }
    }

    val bmp = hairBitmap
    val anchor = crown
    val fit = config
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isFilterBitmap = true }
    }
    val matrix = remember { Matrix() }

    if (bmp == null || bmp.isRecycled || anchor == null || fit == null) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val headWidthPx = anchor.headWidthNorm * w
        val drawWidth = (headWidthPx * fit.localScaleMul).coerceIn(w * 0.25f, w * 1.35f)
        val aspect = bmp.height.toFloat() / max(bmp.width.toFloat(), 1f)
        val drawHeight = drawWidth * aspect

        val yawScaleX = (1f - kotlin.math.abs(anchor.yawDeg) / 90f * 0.35f).coerceIn(0.55f, 1f)
        val scaledW = drawWidth * yawScaleX
        val scaledH = drawHeight

        val cx = anchor.normX * w + fit.localOffsetX * headWidthPx
        val cy = anchor.normY * h + fit.localOffsetY * (anchor.faceHeightNorm * h)
        val pivotY = scaledH * fit.anchorY.coerceIn(0f, 1f)

        val sx = scaledW / max(bmp.width.toFloat(), 1f)
        val sy = scaledH / max(bmp.height.toFloat(), 1f)
        matrix.reset()
        matrix.setScale(sx, sy)
        matrix.postTranslate(-scaledW / 2f, -pivotY)
        matrix.postRotate(anchor.rollDeg)
        matrix.postTranslate(cx, cy)

        drawContext.canvas.nativeCanvas.drawBitmap(bmp, matrix, paint)
    }
}

private data class CrownAnchor(
    val normX: Float,
    val normY: Float,
    val headWidthNorm: Float,
    val faceHeightNorm: Float,
    val rollDeg: Float,
    val yawDeg: Float
)

private fun crownFromLandmarks(
    points: List<LandmarkPoint>,
    pose: HeadPose
): CrownAnchor {
    val top = points[FaceShapeClassifier.FOREHEAD_TOP]
    val chin = points[FaceShapeClassifier.CHIN]
    val left = points[FaceShapeClassifier.LEFT_CHEEK]
    val right = points[FaceShapeClassifier.RIGHT_CHEEK]
    val faceH = (chin.y - top.y).coerceAtLeast(0.08f)
    val faceW = (right.x - left.x).coerceAtLeast(0.08f)
    val templeW = if (points.size > FaceShapeClassifier.RIGHT_TEMPLE) {
        (points[FaceShapeClassifier.RIGHT_TEMPLE].x - points[FaceShapeClassifier.LEFT_TEMPLE].x)
            .coerceIn(0.08f, 0.72f)
    } else {
        faceW
    }
    val headWidth = max(faceW, templeW)

    // Same crown idea as HeadPoseEstimator / ArTryOnOverlay debug guides.
    val crownX = (left.x + right.x) * 0.5f
    val crownY = top.y - faceH * 0.38f

    // Small yaw/pitch shifts so the overlay follows left/right and up/down turns.
    val yawShift = sin(Math.toRadians(pose.yawDeg.toDouble())).toFloat() * headWidth * 0.12f
    val pitchShift = -sin(Math.toRadians(pose.pitchDeg.toDouble())).toFloat() * faceH * 0.08f

    return CrownAnchor(
        normX = (crownX + yawShift).coerceIn(0f, 1f),
        normY = (crownY + pitchShift).coerceIn(0f, 1f),
        headWidthNorm = headWidth,
        faceHeightNorm = faceH,
        rollDeg = pose.rollDeg,
        yawDeg = pose.yawDeg
    )
}
