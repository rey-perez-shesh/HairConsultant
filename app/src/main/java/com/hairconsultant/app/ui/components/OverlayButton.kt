package com.hairconsultant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hairconsultant.app.ui.theme.CameraScrim
import com.hairconsultant.app.ui.theme.OnShell

/** A secondary action over a camera feed or photo (Rescan, Change photo): translucent black, never oxblood. */
@Composable
fun OverlayButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CameraScrim, contentColor = OnShell),
        border = BorderStroke(1.5.dp, OnShell.copy(alpha = 0.35f))
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(text, modifier = if (icon != null) Modifier.padding(start = 6.dp) else Modifier)
    }
}

/** [OverlayButton] without a label, for a top bar that has no room for one; [contentDescription] stands in for it. */
@Composable
fun OverlayIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(containerColor = CameraScrim, contentColor = OnShell)
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
