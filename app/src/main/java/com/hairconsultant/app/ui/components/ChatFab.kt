package com.hairconsultant.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hairconsultant.app.ui.theme.CameraScrim
import com.hairconsultant.app.ui.theme.LocalShopColors

private const val CHAT_DESCRIPTION = "Open AI hairstylist chat"

/** Home's one primary action: opens the AI chatbot, in oxblood above the bottom navigation bar. */
@Composable
fun ChatFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = CHAT_DESCRIPTION)
    }
}

/**
 * The chat opener for screens whose primary action is something else (Scan face, Choose photo):
 * a plain icon button in the top bar, so each screen keeps a single floating action.
 * [onScrim] gives it a translucent black backing for use over a camera feed or photo.
 */
@Composable
fun ChatIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, onScrim: Boolean = false) {
    val shop = LocalShopColors.current
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (onScrim) CameraScrim else Color.Transparent,
            contentColor = shop.onShell
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = CHAT_DESCRIPTION)
    }
}
