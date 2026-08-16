package com.hairconsultant.app.ui.facescan

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hairconsultant.app.domain.model.Haircut

/**
 * Stand-in for the real AR try-on renderer: overlays the haircut's reference photo, blended, on
 * top of the live camera feed. Replace with an actual face-mesh-anchored hair segmentation/render
 * pipeline (e.g. ARCore + a hair asset, or a segmentation model swapping in a rendered strand mesh).
 */
@Composable
fun BoxScope.ArTryOnOverlay(haircut: Haircut) {
    AsyncImage(
        model = haircut.imageUrl,
        contentDescription = "${haircut.name} AR preview",
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .align(Alignment.TopCenter)
            .graphicsLayer(alpha = 0.55f),
        contentScale = ContentScale.Crop
    )
    Surface(
        modifier = Modifier.align(Alignment.TopCenter),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Text(
            text = "AR preview (placeholder) — ${haircut.name}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
