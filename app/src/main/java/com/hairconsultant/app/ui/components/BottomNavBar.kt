package com.hairconsultant.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextOverflow
import com.hairconsultant.app.ui.navigation.bottomNavItems

/**
 * No fixed height: NavigationBar sizes itself and adds the system navigation-bar inset (gesture
 * handle or 3-button bar) below the items, so they're never squeezed on any device.
 *
 * Label text follows the system font size only up to [MAX_LABEL_FONT_SCALE]; past that, four
 * labels can't fit side by side and would all be cut to "Ho…", "Fac…".
 */
@Composable
fun HairConsultantBottomBar(
    currentRoute: String?,
    onItemSelected: (String) -> Unit
) {
    val density = LocalDensity.current
    val labelDensity = Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_FONT_SCALE))
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(item.screen.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = {
                    CompositionLocalProvider(LocalDensity provides labelDensity) {
                        // One line, so a very narrow screen shortens a label instead of wrapping it.
                        Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}

private const val MAX_LABEL_FONT_SCALE = 1.3f
