package com.hairconsultant.app.ui.profile

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.User
import com.hairconsultant.app.ui.components.StarRatingBar
import com.hairconsultant.app.ui.components.StatusBarScrim
import com.hairconsultant.app.ui.theme.LocalShopColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLoggedOut: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onDeepArCurtainsTest: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ProfileHeader(
                    user = uiState.user,
                    historyCount = uiState.history.size,
                    favoritesCount = uiState.favorites.size,
                    selectedTab = uiState.selectedTab,
                    onTabSelected = viewModel::onTabSelected,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onDeepArCurtainsTest = onDeepArCurtainsTest,
                    onClearHistory = viewModel::clearHistory,
                    onClearFavorites = viewModel::clearFavorites,
                    onLogout = {
                        viewModel.logout()
                        onLoggedOut()
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            val list = if (uiState.selectedTab == ProfileTab.HISTORY) uiState.history else uiState.favorites
            if (list.isEmpty()) {
                item { EmptyHistoryCard(selectedTab = uiState.selectedTab) }
            } else {
                items(list, key = { it.id }) { consultation ->
                    ConsultationRow(consultation = consultation, onToggleFavorite = { viewModel.toggleFavorite(consultation) })
                }
            }

            item {
                FeedbackCard(
                    rating = uiState.rating,
                    comment = uiState.feedbackComment,
                    submitted = uiState.feedbackSubmitted,
                    onRatingChanged = viewModel::onRatingChanged,
                    onCommentChanged = viewModel::onFeedbackCommentChanged,
                    onSubmit = viewModel::submitFeedback
                )
            }
        }
        StatusBarScrim()
    }
}

/** The shop-front header (black, grey in dark mode): who's signed in, their counts, the overflow menu, and the History / Favorites tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeader(
    user: User?,
    historyCount: Int,
    favoritesCount: Int,
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onDeepArCurtainsTest: () -> Unit,
    onClearHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    onLogout: () -> Unit
) {
    val shop = LocalShopColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(shop.shell)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(shop.indicator, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials(user),
                    style = MaterialTheme.typography.headlineMedium,
                    color = shop.onShell
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = (user?.username?.ifBlank { null } ?: "Your account").uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = shop.onShell,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = headerSubtitle(user),
                    style = MaterialTheme.typography.bodySmall,
                    color = shop.onShellMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Shows the mode a tap switches to, like the moon/sun toggles in popular apps.
            IconButton(onClick = onToggleTheme) {
                Crossfade(targetState = isDarkTheme, label = "theme icon") { dark ->
                    Icon(
                        imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode",
                        tint = shop.onShell
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Settings", tint = shop.onShell)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("DeepAR curtains test") },
                        leadingIcon = { Icon(Icons.Filled.Face, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDeepArCurtainsTest()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear history") },
                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                        onClick = { onClearHistory(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete saved favorites") },
                        leadingIcon = { Icon(Icons.Filled.FavoriteBorder, contentDescription = null) },
                        onClick = { onClearFavorites(); menuExpanded = false }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuExpanded = false
                            onLogout()
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(value = historyCount, label = "Consultations", modifier = Modifier.weight(1f))
            StatTile(value = favoritesCount, label = "Favorites", modifier = Modifier.weight(1f))
        }

        val tabs = listOf(ProfileTab.HISTORY to "History", ProfileTab.FAVORITES to "Favorites")
        val selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = shop.shell,
            contentColor = shop.onShell,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = false),
                    height = 3.dp,
                    color = shop.onShell
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, (tab, label) ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onTabSelected(tab) },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    selectedContentColor = shop.onShell,
                    unselectedContentColor = shop.onShellMuted
                )
            }
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    val shop = LocalShopColors.current
    Column(
        modifier = modifier
            .border(1.5.dp, shop.shellLine, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = shop.onShell)
        Text(label, style = MaterialTheme.typography.bodySmall, color = shop.onShellMuted)
    }
}

private fun initials(user: User?): String {
    val name = user?.username?.trim().orEmpty()
    val parts = name.split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
        parts.size == 1 -> parts[0].first().toString()
        else -> user?.email?.firstOrNull()?.toString() ?: "?"
    }.uppercase()
}

private fun headerSubtitle(user: User?): String {
    val preferences = listOfNotNull(
        user?.preferredHairLength?.displayName,
        user?.preferredHairTexture?.displayName,
        user?.preferredTreatment?.takeIf { it.name != "NONE" }?.displayName
    )
    val joinDate = user?.createdAtEpochMillis?.takeIf { it > 0 }?.let(::formatJoinDate)
    return listOfNotNull(
        preferences.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        joinDate?.let { "Since $it" }
    ).joinToString(" · ").ifBlank { user?.email.orEmpty() }
}

private fun formatJoinDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM yyyy"))

private fun formatConsultationDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d"))

@Composable
private fun EmptyHistoryCard(selectedTab: ProfileTab) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (selectedTab == ProfileTab.HISTORY) Icons.Filled.PhotoCamera else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (selectedTab == ProfileTab.HISTORY) {
                    "No consultations yet — try a face scan or upload a photo."
                } else {
                    "No favorites yet — tap the heart on a consultation to save it here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A past consultation, stamped with how far it got: scanned, a cut picked, a preview generated. */
@Composable
private fun ConsultationRow(consultation: Consultation, onToggleFavorite: () -> Unit) {
    val shop = LocalShopColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val image = consultation.selectedHaircut?.imageUrl ?: consultation.resultImageUrl ?: consultation.sourceImageUrl
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small).background(shop.imageWell),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(64.dp).background(shop.imageWell, MaterialTheme.shapes.small))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        consultation.selectedHaircut?.name ?: "${consultation.scanResult.faceShape.displayName} scan",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (consultation.createdAtEpochMillis > 0) {
                        Text(
                            formatConsultationDate(consultation.createdAtEpochMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Text(
                    text = consultationDetail(consultation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StageStamp("Scanned", reached = true)
                    StageStamp("Picked", reached = consultation.selectedHaircut != null)
                    StageStamp("Previewed", reached = consultation.resultImageUrl != null)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (consultation.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (consultation.isFavorite) "Remove from favorites" else "Add to favorites",
                    // The fit mark, not oxblood: oxblood stays on the screen's one primary action.
                    tint = if (consultation.isFavorite) shop.fit else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun consultationDetail(consultation: Consultation): String {
    val source = when (consultation.source) {
        ConsultationSource.FACE_SCAN -> "Face Scan"
        ConsultationSource.IMAGE_UPLOAD -> "Upload"
    }
    val scan = consultation.scanResult
    val hair = if (scan.hairLength == HairLength.BALD) scan.hairLength.displayName else "${scan.hairLength.displayName}, ${scan.hairTexture.displayName.lowercase()}"
    return "$source · ${scan.faceShape.displayName} face · $hair"
}

/** Solid in the fit mark once a stage was reached, dashed and muted when it wasn't: the state reads without colour too. */
@Composable
private fun StageStamp(label: String, reached: Boolean) {
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

@Composable
private fun FeedbackCard(
    rating: Int,
    comment: String,
    submitted: Boolean,
    onRatingChanged: (Int) -> Unit,
    onCommentChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("How satisfied are you?", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            StarRatingBar(rating = rating, onRatingChanged = onRatingChanged)
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChanged,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                placeholder = { Text("What worked well? What could be better?") },
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSubmit,
                enabled = rating > 0,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Submit feedback")
            }
            if (submitted) {
                Text(
                    "Thanks for your feedback!",
                    color = LocalShopColors.current.fit,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
