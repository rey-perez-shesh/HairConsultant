package com.hairconsultant.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Modern 1-10 satisfaction rating rendered as ten tappable stars. */
@Composable
fun StarRatingBar(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxStars: Int = 10
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (star in 1..maxStars) {
            val filled = star <= rating
            val color by animateColorAsState(
                if (filled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                label = "starColor"
            )
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "Rate $star out of $maxStars",
                tint = color,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onRatingChanged(star) }
            )
        }
    }
}
