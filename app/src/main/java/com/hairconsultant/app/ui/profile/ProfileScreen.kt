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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.User
import com.hairconsultant.app.domain.model.colorLookShadeId
import com.hairconsultant.app.domain.model.isArFilter
import com.hairconsultant.app.ui.components.FaceFitTiles
import com.hairconsultant.app.ui.components.OverlayIconButton
import com.hairconsultant.app.ui.components.StarRatingBar
import com.hairconsultant.app.ui.components.StatusBarScrim
import com.hairconsultant.app.ui.deepar.DeepArCurtainsTestScreen
import com.hairconsultant.app.ui.facescan.HairstyleArCatalog
import com.hairconsultant.app.ui.haircolor.HairShades
import com.hairconsultant.app.ui.theme.LocalShopColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLoggedOut: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var openLook by remember { mutableStateOf<Consultation?>(null) }
    var tryOnFilter by remember { mutableStateOf<Haircut?>(null) }
    // A filter whose style or AR file the dashboard has since removed can't be tried on, so it isn't shown.
    val filters = uiState.favorites.mapNotNull { favorite ->
        favorite.selectedHaircut?.takeIf { favorite.isArFilter && HairstyleArCatalog.shouldUseDeepAr(it) }?.let { favorite to it }
    }
    val looks = uiState.favorites.filterNot { it.isArFilter }
    // Newest first in both lists; a saved look still carries its session's scan once History is cleared.
    val latestScan = (uiState.history.firstOrNull() ?: looks.firstOrNull())?.scanResult

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ProfileHeader(
                    user = uiState.user,
                    scan = latestScan,
                    historyCount = uiState.history.size,
                    favoritesCount = filters.size + looks.size,
                    selectedTab = uiState.selectedTab,
                    onTabSelected = viewModel::onTabSelected,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onClearHistory = viewModel::clearHistory,
                    onClearFavorites = viewModel::clearFavorites,
                    onLogout = {
                        viewModel.logout()
                        onLoggedOut()
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            val isHistory = uiState.selectedTab == ProfileTab.HISTORY
            if (if (isHistory) uiState.history.isEmpty() else filters.isEmpty() && looks.isEmpty()) {
                item { EmptyHistoryCard(selectedTab = uiState.selectedTab) }
            } else if (isHistory) {
                items(uiState.history, key = { it.id }) { consultation ->
                    ConsultationRow(consultation = consultation, onToggleFavorite = { viewModel.toggleFavorite(consultation) })
                }
            } else {
                if (filters.isNotEmpty()) {
                    item(key = "filters") {
                        SectionHead("Try-on filters", filters.size)
                        LazyRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(filters, key = { it.first.id }) { (favorite, haircut) ->
                                FilterTile(
                                    haircut = haircut,
                                    onTryOn = { tryOnFilter = haircut },
                                    onRemove = { viewModel.toggleFavorite(favorite) }
                                )
                            }
                        }
                    }
                    // The grid only needs naming once it shares the tab with the shelf.
                    if (looks.isNotEmpty()) {
                        item(key = "looks-head") { SectionHead("Saved looks", looks.size, modifier = Modifier.padding(top = 10.dp)) }
                    }
                }
                // Two to a row, so looks sit side by side for comparing.
                items(looks.chunked(2), key = { row -> row.first().id }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { look ->
                            FavoriteLookCard(
                                consultation = look,
                                onOpen = { openLook = look },
                                onRemove = { viewModel.toggleFavorite(look) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
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

    openLook?.let { look -> LookDialog(consultation = look, onDismiss = { openLook = null }) }
    // Downloads the effect if needed and opens the live try-on, the same way Home does.
    tryOnFilter?.let { haircut -> DeepArCurtainsTestScreen(haircut = haircut, onClose = { tryOnFilter = null }) }
}

/** Names a group inside the Favorites tab, with how many it holds, like the catalog's section heads on Home. */
@Composable
private fun SectionHead(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

/** A favorited AR filter: tap the tile to put it back on your face, the heart to drop it. */
@Composable
private fun FilterTile(haircut: Haircut, onTryOn: () -> Unit, onRemove: () -> Unit) {
    val shop = LocalShopColors.current
    Card(
        onClick = onTryOn,
        modifier = Modifier.width(124.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box {
            AsyncImage(
                model = haircut.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(shop.imageWell),
                contentScale = ContentScale.Crop
            )
            // On the dark well in both themes, so the camera screens' overlay button, not the ink heart.
            OverlayIconButton(
                icon = Icons.Filled.Favorite,
                contentDescription = "Remove ${haircut.name} from favorites",
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        Text(
            text = haircut.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/**
 * The shop-front header (black, grey in dark mode) as the shop's card on this client: who they are,
 * the face shape and hair their latest scan measured, the overflow menu, and the History /
 * Favorites tabs carrying their counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeader(
    user: User?,
    scan: ScanResult?,
    historyCount: Int,
    favoritesCount: Int,
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
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
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scan?.let { "${it.faceShape.displayName} face".uppercase() } ?: "NO SCAN YET",
                    style = MaterialTheme.typography.headlineMedium,
                    color = shop.onShell
                )
                Text(
                    text = scan?.let { if (it.hairLength == HairLength.BALD) hairSummary(it) else "${hairSummary(it)} hair" }
                        ?: "Scan your face to see which cuts suit you",
                    style = MaterialTheme.typography.bodySmall,
                    color = shop.onShellMuted
                )
            }
            // The same tiles as every haircut card, so the user learns which one to look for. The line beside them already says the shape.
            FaceFitTiles(
                suits = listOfNotNull(scan?.faceShape),
                userShape = scan?.faceShape,
                modifier = Modifier.clearAndSetSemantics { },
                onShell = true,
                large = true
            )
        }

        val tabs = listOf(
            Triple(ProfileTab.HISTORY, "History", historyCount),
            Triple(ProfileTab.FAVORITES, "Favorites", favoritesCount)
        )
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
            tabs.forEachIndexed { index, (tab, label, count) ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            Text(count.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    },
                    selectedContentColor = shop.onShell,
                    unselectedContentColor = shop.onShellMuted
                )
            }
        }
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

private fun headerSubtitle(user: User?): String =
    user?.createdAtEpochMillis?.takeIf { it > 0 }?.let { "Since ${formatJoinDate(it)}" } ?: user?.email.orEmpty()

/** "Short, straight", or just "Bald": hair as a scan measured it. */
private fun hairSummary(scan: ScanResult): String =
    if (scan.hairLength == HairLength.BALD) scan.hairLength.displayName
    else "${scan.hairLength.displayName}, ${scan.hairTexture.displayName.lowercase()}"

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
                    "No favorites yet — tap the heart in an AR try-on, Save look on a preview in Upload, or the heart on a consultation."
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

/** What a favorite shows: the user's own generated preview when there is one, else the style, else their photo. */
private fun Consultation.lookImage(): String? = resultImageUrl ?: selectedHaircut?.imageUrl ?: sourceImageUrl

private fun Consultation.lookTitle(): String = selectedHaircut?.name
    ?: HairShades.byId(colorLookShadeId)?.let { "${it.name} hair color" }
    ?: "${scanResult.faceShape.displayName} scan"

/** One favorite in the comparison grid: the whole picture, never cropped, since the hair is the point. */
@Composable
private fun FavoriteLookCard(
    consultation: Consultation,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shop = LocalShopColors.current
    Card(
        onClick = onOpen,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        AsyncImage(
            model = consultation.lookImage(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(LOOK_ASPECT_RATIO).background(shop.imageWell),
            contentScale = ContentScale.Fit
        )
        Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    consultation.lookTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (consultation.createdAtEpochMillis > 0) {
                    Text(
                        formatConsultationDate(consultation.createdAtEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Remove ${consultation.lookTitle()} from favorites",
                    tint = shop.fit
                )
            }
        }
    }
}

/** A favorite opened large, for a closer look than the grid gives. */
@Composable
private fun LookDialog(consultation: Consultation, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = consultation.lookImage(),
                contentDescription = "${consultation.lookTitle()} preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(LOOK_ASPECT_RATIO)
                    .background(LocalShopColors.current.imageWell),
                contentScale = ContentScale.Fit
            )
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        consultation.lookTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        consultationDetail(consultation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

/** Portrait, close to the shape of the photos people upload; generated previews can also be square. */
private const val LOOK_ASPECT_RATIO = 4f / 5f

private fun consultationDetail(consultation: Consultation): String {
    val source = when (consultation.source) {
        ConsultationSource.FACE_SCAN -> "Face Scan"
        ConsultationSource.IMAGE_UPLOAD -> "Upload"
    }
    val scan = consultation.scanResult
    return "$source · ${scan.faceShape.displayName} face · ${hairSummary(scan)}"
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
