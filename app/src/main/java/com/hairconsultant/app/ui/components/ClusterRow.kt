package com.hairconsultant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutCluster

/** One horizontally-scrolling row of haircuts for a single length x texture cluster (e.g. "Long & Wavy"). */
@Composable
fun ClusterRow(
    cluster: HaircutCluster,
    onHaircutClick: (Haircut) -> Unit,
    modifier: Modifier = Modifier,
    userFaceShape: FaceShape? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = cluster.title.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            val count = cluster.haircuts.size
            Text(
                text = if (count == 1) "1 cut" else "$count cuts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(cluster.haircuts, key = { it.id }) { haircut ->
                HaircutCard(haircut = haircut, userFaceShape = userFaceShape, onClick = onHaircutClick)
            }
        }
    }
}
