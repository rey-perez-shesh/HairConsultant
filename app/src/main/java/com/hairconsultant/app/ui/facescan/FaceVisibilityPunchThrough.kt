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
 * Uses a landmark-based ellipse (not a rectangle or polygon cutout).
 * Anti-aliased clip gives a soft natural edge. Does not change tracking or GLB transforms.
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
        // Match PreviewView FILL_CENTER.
        val scale = max(size.width / imgW, size.height / imgH)
        val ox = (size.width - imgW * scale) / 2f
        val oy = (size.height - imgH * scale) / 2f

        fun mapX(nx: Float) = nx * imgW * scale + ox
        fun mapY(ny: Float) = ny * imgH * scale + oy

        val left = points[FaceShapeClassifier.LEFT_CHEEK]
        val right = points[FaceShapeClassifier.RIGHT_CHEEK]
        val top = points[FaceShapeClassifier.FOREHEAD_TOP]
        val chin = points[FaceShapeClassifier.CHIN]
        val faceW = (right.x - left.x).coerceAtLeast(0.08f)
        val faceH = (chin.y - top.y).coerceAtLeast(0.08f)
        val cx = (left.x + right.x) * 0.5f
        val cy = (top.y + chin.y) * 0.52f
        // Slightly expanded ellipse; forehead inset keeps crown/bangs from GLB visible.
        val rx = faceW * FACE_ELLIPSE_RX
        val ry = faceH * FACE_ELLIPSE_RY
        val topBound = top.y + faceH * FOREHEAD_INSET

        val faceClip = Path().apply {
            addOval(
                Rect(
                    left = mapX(cx - rx),
                    top = mapY(maxOf(cy - ry, topBound)),
                    right = mapX(cx + rx),
                    bottom = mapY(cy + ry * FACE_CHIN_EXPAND)
                )
            )
        }

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

/** Ellipse half-width as fraction of cheek span (includes glasses margin). */
private const val FACE_ELLIPSE_RX = 0.58f

/** Ellipse half-height as fraction of forehead–chin span. */
private const val FACE_ELLIPSE_RY = 0.64f

/** Push oval top below hairline so virtual bangs can show above the brow. */
private const val FOREHEAD_INSET = 0.10f

/** Extend slightly below chin so jawline stays sharp. */
private const val FACE_CHIN_EXPAND = 1.04f
