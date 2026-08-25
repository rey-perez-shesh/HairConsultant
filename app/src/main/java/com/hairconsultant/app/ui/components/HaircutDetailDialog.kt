package com.hairconsultant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.hairconsultant.app.domain.model.Haircut

/**
 * Quick-look preview shown when a haircut card is tapped while just browsing. "Try It On" is the
 * only path into the live camera filter — closes this preview and hands off to that separate flow.
 */
@Composable
fun HaircutDetailDialog(haircut: Haircut, onDismiss: () -> Unit, onTryOn: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    AsyncImage(
                        model = haircut.imageUrl,
                        contentDescription = haircut.name,
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(haircut.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${haircut.length.displayName} • ${haircut.texture.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            haircut.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = onDismiss) { Text("Close") }
                            Button(onClick = onTryOn) { Text("Try It On") }
                        }
                    }
                }
            }
        }
    }
}
