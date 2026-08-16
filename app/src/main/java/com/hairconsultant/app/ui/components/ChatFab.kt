package com.hairconsultant.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Sits above the bottom navigation bar on Home / Face Scan / Image Upload to open the AI chatbot. */
@Composable
fun ChatFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Filled.Chat, contentDescription = "Open AI hairstylist chat")
    }
}
