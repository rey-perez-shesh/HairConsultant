package com.hairconsultant.app.ui.facescan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.ui.components.HaircutCard

/**
 * Quick haircut switcher shown above the bottom nav while trying styles on, so the user doesn't
 * have to reopen the chat sheet to compare cuts.
 */
@Composable
fun TryOnStrip(
    suggestions: List<Haircut>,
    selectedHaircut: Haircut?,
    onSelect: (Haircut) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 90.dp)
    ) {
        items(suggestions, key = { it.id }) { haircut ->
            HaircutCard(
                haircut = haircut,
                width = 90.dp,
                height = 110.dp,
                isSelected = haircut.id == selectedHaircut?.id,
                onClick = onSelect
            )
        }
    }
}
