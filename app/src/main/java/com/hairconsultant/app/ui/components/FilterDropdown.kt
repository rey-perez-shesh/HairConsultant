package com.hairconsultant.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hairconsultant.app.ui.theme.LocalShopColors

/**
 * A single "All / one of [options]" filter chip with a dropdown, used to let Home catalog browsing
 * go beyond what's defaulted/recommended for the signed-in user (see
 * [com.hairconsultant.app.ui.home.HomeViewModel]). `null` always means "All" for that category:
 * the chip then shows [label]; with a value chosen it shows that value, filled with the shell colour.
 */
@Composable
fun <T> FilterDropdown(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val shop = LocalShopColors.current
    Box(modifier = modifier) {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            // The chip shows only the chosen value, so say which filter it is for screen readers.
            modifier = Modifier.semantics { contentDescription = "$label: ${selected?.let(optionLabel) ?: "All"}" },
            label ={ Text(selected?.let(optionLabel) ?: label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = MaterialTheme.shapes.small,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                // Oxblood (primary) is kept for each screen's one main action, not chip chrome.
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = shop.shell,
                selectedLabelColor = shop.onShell,
                selectedTrailingIconColor = shop.onShell
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected != null,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = shop.shell,
                borderWidth = 1.5.dp,
                selectedBorderWidth = 1.5.dp
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelected(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}
