package com.hairconsultant.app.ui.facescan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a soft hair-only scalp veil in the Compose layer beneath SceneView.
 *
 * SceneView is a transparent media-overlay SurfaceView: where the GLB does not draw,
 * these pixels show through and hide the client's original hair. Face oval is already
 * cleared in the bitmap — eyes/skin/background stay visible.
 */
@Composable
fun HairCoverComposeLayer(
    coverBitmap: Bitmap?,
    imageWidth: Int,
    imageHeight: Int
) {
    if (coverBitmap == null || coverBitmap.isRecycled) return
    if (imageWidth < 2 || imageHeight < 2) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val imgW = imageWidth.toFloat()
        val imgH = imageHeight.toFloat()
        val scale = max(size.width / imgW, size.height / imgH)
        val ox = (size.width - imgW * scale) / 2f
        val oy = (size.height - imgH * scale) / 2f
        val dstW = (imgW * scale).roundToInt().coerceAtLeast(1)
        val dstH = (imgH * scale).roundToInt().coerceAtLeast(1)
        drawImage(
            image = coverBitmap.asImageBitmap(),
            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
            dstSize = IntSize(dstW, dstH)
        )
    }
}
