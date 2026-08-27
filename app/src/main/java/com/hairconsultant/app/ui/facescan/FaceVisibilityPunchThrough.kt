package com.hairconsultant.app.ui.facescan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.hairconsultant.app.data.analysis.FaceShapeClassifier
import com.hairconsultant.app.data.analysis.LandmarkPoint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Restores the live camera face above SceneView so virtual hair cannot cover
 * eyes, glasses, nose, mouth, or cheeks.
 *
 * Clip follows the MediaPipe face oval (facial shape — not a rectangle).
 * Soft AA clip edge. Does not change tracking or GLB transforms.
 */
@Composable
fun FaceVisibilityPunchThrough(
    points: List<LandmarkPoint>,
    imageWidth: Int,
    imageHeight: Int,
    cameraBitmap: Bitmap?
) {
    if (cameraBitmap == null || cameraBitmap.isRecycled) return
    if (points.size <= FaceShapeClassifier.CHIN) return
    if (imageWidth < 2 || imageHeight < 2) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val imgW = imageWidth.toFloat()
        val imgH = imageHeight.toFloat()
        val scale = max(size.width / imgW, size.height / imgH)
        val ox = (size.width - imgW * scale) / 2f
        val oy = (size.height - imgH * scale) / 2f

        fun mapX(nx: Float) = nx * imgW * scale + ox
        fun mapY(ny: Float) = ny * imgH * scale + oy

        val faceClip = buildFaceOcclusionPath(points, ::mapX, ::mapY) ?: return@Canvas

        val dstW = (imgW * scale).roundToInt().coerceAtLeast(1)
        val dstH = (imgH * scale).roundToInt().coerceAtLeast(1)
        val imageBitmap = cameraBitmap.asImageBitmap()

        clipPath(faceClip, clipOp = ClipOp.Intersect) {
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize(dstW, dstH)
            )
        }
    }
}

/** MediaPipe 468-mesh face oval — interior is face skin; exterior leaves hair free. */
private val FACE_OVAL_INDICES = intArrayOf(
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
    397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
    172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
)

/**
 * Softly expanded face oval. Slight forehead inset so virtual bangs stay visible.
 * Falls back to a cheek/forehead ellipse if the mesh is incomplete.
 */
private fun buildFaceOcclusionPath(
    points: List<LandmarkPoint>,
    mapX: (Float) -> Float,
    mapY: (Float) -> Float
): Path? {
    val chin = points.getOrNull(FaceShapeClassifier.CHIN) ?: return null
    val top = points.getOrNull(FaceShapeClassifier.FOREHEAD_TOP) ?: return null
    val left = points.getOrNull(FaceShapeClassifier.LEFT_CHEEK) ?: return null
    val right = points.getOrNull(FaceShapeClassifier.RIGHT_CHEEK) ?: return null
    val faceH = (chin.y - top.y).coerceAtLeast(0.08f)
    val faceW = (right.x - left.x).coerceAtLeast(0.08f)
    val cx = (left.x + right.x) * 0.5f
    val cy = (top.y + chin.y) * 0.52f
    // Keep bangs/crown free for the GLB.
    val topInsetY = top.y + faceH * FOREHEAD_INSET

    if (FACE_OVAL_INDICES.all { it < points.size }) {
        val path = Path()
        var started = false
        for (idx in FACE_OVAL_INDICES) {
            val p = points[idx]
            // Mild radial expand so glasses / cheeks stay covered without a hard box.
            val nx = cx + (p.x - cx) * OVAL_EXPAND
            val ny = maxOf(cy + (p.y - cy) * OVAL_EXPAND, topInsetY)
            val x = mapX(nx)
            val y = mapY(ny)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return path
    }

    return Path().apply {
        addOval(
            Rect(
                left = mapX(cx - faceW * 0.58f),
                top = mapY(topInsetY),
                right = mapX(cx + faceW * 0.58f),
                bottom = mapY(chin.y + faceH * 0.04f)
            )
        )
    }
}

/** Expand oval slightly past cheek landmarks (glasses margin). */
private const val OVAL_EXPAND = 1.05f

/** Push oval top below hairline so virtual bangs can show. */
private const val FOREHEAD_INSET = 0.10f
