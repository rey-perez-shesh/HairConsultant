package com.hairconsultant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.ui.theme.LocalShopColors

/**
 * One small tile per face shape (O R S H D), filled where [suits] includes it, so which faces a
 * cut suits reads at a glance. [userShape]'s tile gets a ring so the user finds their own.
 * [onShell] switches the empty tiles to colours that read on the shell header; [large] is the
 * Profile header's size, where the row stands for the user rather than annotating a card.
 */
@Composable
fun FaceFitTiles(
    suits: Collection<FaceShape>,
    userShape: FaceShape?,
    modifier: Modifier = Modifier,
    onShell: Boolean = false,
    large: Boolean = false
) {
    val shop = LocalShopColors.current
    val emptyLine = if (onShell) shop.shellLine else MaterialTheme.colorScheme.outline
    val emptyText = if (onShell) shop.onShellMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val ring = if (onShell) shop.onShell else MaterialTheme.colorScheme.primary
    // Filled tiles are the strongest ink on their background; their letter takes that background's colour.
    val fill = if (onShell) shop.onShell else shop.fit
    val onFill = if (onShell) shop.shell else MaterialTheme.colorScheme.surface
    val description = fitDescription(suits, userShape)
    val slot = if (large) 28.dp else SLOT
    val tile = if (large) 20.dp else TILE
    val letter = if (large) 11.sp else 9.sp
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        FaceShape.entries.forEach { shape ->
            val filled = shape in suits
            val tileShape = RoundedCornerShape(3.dp)
            // Every tile sits in the same slot so the ringed one doesn't shift the row.
            Box(
                modifier = Modifier
                    .size(slot)
                    .then(if (shape == userShape) Modifier.border(2.dp, ring, RoundedCornerShape(5.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(tile)
                        .background(if (filled) fill else Color.Transparent, tileShape)
                        .border(1.5.dp, if (filled) fill else emptyLine, tileShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shape.displayName.take(1),
                        style = TextStyle(fontSize = letter, lineHeight = letter, fontWeight = FontWeight.Bold),
                        color = if (filled) onFill else emptyText
                    )
                }
            }
        }
    }
}

private val SLOT = 22.dp
private val TILE = 16.dp

private fun fitDescription(suits: Collection<FaceShape>, userShape: FaceShape?): String {
    val names = FaceShape.entries.filter { it in suits }.map { it.displayName }
    val base = if (names.isEmpty()) "No face shape listed" else "Suits ${names.joinToString(", ")} faces"
    return if (userShape != null && userShape in suits) "$base, including yours" else base
}
