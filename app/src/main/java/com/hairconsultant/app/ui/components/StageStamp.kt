package com.hairconsultant.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hairconsultant.app.ui.theme.LocalShopColors

/** Solid in the fit mark once a stage was reached, dashed and muted when it wasn't: the state reads without colour too. */
@Composable
fun StageStamp(label: String, reached: Boolean) {
    val shop = LocalShopColors.current
    val outline = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = Modifier
            .then(
                if (reached) {
                    Modifier.border(1.5.dp, shop.fit, shape)
                } else {
                    Modifier.drawDashedBorder(outline)
                }
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = if (reached) label else "$label: not yet" }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (reached) shop.fit else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Modifier.drawDashedBorder(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(
        Modifier.drawBehind {
            drawRoundRect(
                color = color,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
        }
    )
