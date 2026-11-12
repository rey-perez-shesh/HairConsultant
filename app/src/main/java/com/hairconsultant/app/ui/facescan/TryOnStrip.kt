package com.hairconsultant.app.ui.facescan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.ui.theme.CameraScrim
import com.hairconsultant.app.ui.theme.OnShell
import com.hairconsultant.app.ui.theme.LocalShopColors

/**
 * Quick haircut switcher shown above the bottom nav while trying styles on, so the user doesn't
 * have to reopen the chat sheet to compare cuts. Sits on a translucent black plate over the camera
 * or photo, titled with the cut being shown.
 */
@Composable
fun TryOnStrip(
    suggestions: List<Haircut>,
    selectedHaircut: Haircut?,
    onSelect: (Haircut) -> Unit,
    modifier: Modifier = Modifier
) {
    val shop = LocalShopColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraScrim, MaterialTheme.shapes.large)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = (selectedHaircut?.name?.let { "$it on you" } ?: "Your matches").uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = shop.onShell,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Pick 1 of ${suggestions.size}",
                style = MaterialTheme.typography.bodySmall,
                color = shop.onShellMuted,
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            items(suggestions, key = { it.id }) { haircut ->
                val selected = haircut.id == selectedHaircut?.id
                Surface(
                    onClick = { onSelect(haircut) },
                    modifier = Modifier.size(64.dp).semantics { this.selected = selected },
                    shape = MaterialTheme.shapes.medium,
                    color = shop.imageWell,
                    border = if (selected) BorderStroke(3.dp, OnShell) else BorderStroke(1.5.dp, shop.shellLine)
                ) {
                    AsyncImage(
                        model = haircut.imageUrl,
                        contentDescription = haircut.name,
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
