package com.hairconsultant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.ui.theme.LocalShopColors

/**
 * A catalog cut: its cutout in a dark "mirror" well, name, length · texture, and the face-shape
 * [FaceFitTiles]. [imageHeight] sizes the well; the text block below it adds its own height.
 */
@Composable
fun HaircutCard(
    haircut: Haircut,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
    imageHeight: Dp = 128.dp,
    userFaceShape: FaceShape? = null,
    onClick: (Haircut) -> Unit
) {
    val shop = LocalShopColors.current
    Card(
        onClick = { onClick(haircut) },
        modifier = modifier.width(width),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        AsyncImage(
            model = haircut.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .background(shop.imageWell),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = haircut.name,
                style = MaterialTheme.typography.titleMedium,
                // Two lines so long names ("Side Part Comb Over Medium") stay readable; minLines keeps a row's cards level.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${haircut.length.displayName} · ${haircut.texture.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FaceFitTiles(
                suits = haircut.recommendedFaceShapes,
                userShape = userFaceShape,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
