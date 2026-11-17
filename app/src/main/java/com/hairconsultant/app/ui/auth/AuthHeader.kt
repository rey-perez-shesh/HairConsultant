package com.hairconsultant.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hairconsultant.app.ui.theme.LocalShopColors

/**
 * The shop-front header of the signed-out screens, the same signage as Home's. The scaffold
 * already paints the shell behind the status bar, so this starts below it.
 */
@Composable
fun AuthHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    val shop = LocalShopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(shop.shell)
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to log in", tint = shop.onShell)
            }
        }
        // Lines up the title's cap height with the back arrow's 48dp target.
        Column(modifier = Modifier.padding(top = if (onBack != null) 7.dp else 0.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = shop.onShell,
                modifier = Modifier.semantics { heading() }
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = shop.onShellMuted)
        }
    }
}
