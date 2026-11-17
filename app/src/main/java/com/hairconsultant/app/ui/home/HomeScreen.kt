package com.hairconsultant.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.ui.chatbot.ChatBotSheet
import com.hairconsultant.app.ui.chatbot.rememberChatBotSheetState
import com.hairconsultant.app.ui.components.ChatFab
import com.hairconsultant.app.ui.components.ClusterRow
import com.hairconsultant.app.ui.components.FaceFitTiles
import com.hairconsultant.app.ui.components.StatusBarScrim
import com.hairconsultant.app.ui.theme.LocalShopColors
import com.hairconsultant.app.ui.components.FilterDropdown
import com.hairconsultant.app.ui.components.HaircutDetailDialog
import com.hairconsultant.app.ui.deepar.DeepArCurtainsActivity
import com.hairconsultant.app.ui.facescan.HairstyleArCatalog
import com.hairconsultant.app.ui.haircolor.HairShades
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenColorStudio: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val sheetState = rememberChatBotSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading && uiState.clusters.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                item { HomeHeader(userFaceShape = uiState.userFaceShape) }
                item { HairColorTile(onClick = onOpenColorStudio) }
                item {
                    CatalogFilterRow(
                        uiState = uiState,
                        onGenderSelected = viewModel::setGenderFilter,
                        onFaceShapeSelected = viewModel::setFaceShapeFilter,
                        onHairLengthSelected = viewModel::setHairLengthFilter,
                        onHairTextureSelected = viewModel::setHairTextureFilter
                    )
                }
                if (uiState.clusters.isEmpty()) {
                    item {
                        Text(
                            text = "No cuts match these filters. Set one back to All to see more.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                items(uiState.clusters, key = { it.length.name + it.texture.name }) { cluster ->
                    ClusterRow(
                        cluster = cluster,
                        onHaircutClick = viewModel::onHaircutSelected,
                        userFaceShape = uiState.userFaceShape
                    )
                }
            }
        }

        StatusBarScrim()
        ChatFab(
            onClick = { viewModel.chatBot.setOpen(true) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (chatState.isOpen) {
        ChatBotSheet(
            state = chatState,
            sheetState = sheetState,
            onDismiss = { viewModel.chatBot.setOpen(false) },
            onInputChange = viewModel.chatBot::updateInput,
            onSend = { scope.launch { viewModel.chatBot.sendCurrentInput() } },
            onHaircutSelected = viewModel::onHaircutSelected
        )
    }

    uiState.selectedHaircut?.let { haircut ->
        HaircutDetailDialog(
            haircut = haircut,
            onDismiss = viewModel::dismissHaircutDetail,
            onTryOn = { viewModel.onTryOnClicked(haircut) }
        )
    }

    uiState.cameraTryOnHaircut?.let { haircut ->
        if (HairstyleArCatalog.shouldUseDeepAr(haircut)) {
            // DeepAR needs a real Activity + SurfaceView — Compose Dialog breaks init.
            LaunchedEffect(haircut.id) {
                val effect = HairstyleArCatalog.resolveDeepArEffect(context, haircut)
                if (effect != null) {
                    context.startActivity(
                        DeepArCurtainsActivity.intent(context, effect, haircut.name, haircut.id)
                    )
                } else {
                    Toast.makeText(context, "Couldn't download this try-on — check your connection", Toast.LENGTH_SHORT).show()
                }
                viewModel.closeCameraTryOn()
            }
        } else {
            Dialog(
                onDismissRequest = viewModel::closeCameraTryOn,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                CameraTryOnScreen(haircut = haircut, onClose = viewModel::closeCameraTryOn)
            }
        }
    }
}

/**
 * The shop-front header (black, grey in dark mode): signage, the tagline, and the face-shape legend for the fit tiles
 * on every card, with the user's own shape ringed once a consultation has measured it.
 */
@Composable
private fun HomeHeader(userFaceShape: FaceShape?) {
    val shop = LocalShopColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(shop.shell)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp)
    ) {
        Text(
            text = "HAIRCONSULTANT",
            style = MaterialTheme.typography.headlineMedium,
            color = shop.onShell,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Find your next haircut",
            style = MaterialTheme.typography.bodyMedium,
            color = shop.onShellMuted
        )
        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = userFaceShape?.let { "Your face shape: ${it.displayName}" } ?: "Tiles show the face shapes each cut suits",
                style = MaterialTheme.typography.bodySmall,
                color = shop.onShell,
                modifier = Modifier.weight(1f, fill = false)
            )
            FaceFitTiles(suits = listOfNotNull(userFaceShape), userShape = userFaceShape, onShell = true)
        }
    }
}

/**
 * The way into the Color Studio, which has no tab of its own: a plate in the shell's color with a
 * few of the shades on it. Never oxblood; that stays on the chat button.
 */
@Composable
private fun HairColorTile(onClick: () -> Unit) {
    val shop = LocalShopColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = shop.shell
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("TRY A HAIR COLOR", style = MaterialTheme.typography.titleLarge, color = shop.onShell)
                Text(
                    "See a new shade on your own hair",
                    style = MaterialTheme.typography.bodySmall,
                    color = shop.onShellMuted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TILE_SHADE_IDS.mapNotNull(HairShades::byId).forEach { shade ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(shade.argb), MaterialTheme.shapes.extraSmall)
                            .border(1.dp, shop.shellLine, MaterialTheme.shapes.extraSmall)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = shop.onShellMuted
            )
        }
    }
}

private val TILE_SHADE_IDS = listOf("copper", "golden_blonde", "rose", "blue")

/**
 * Home defaults to the signed-in user's gender (masculine/unisex or feminine/unisex styles); these
 * four filter chips let them look past that default at any other gender, face shape, hair type, or
 * hair length in the catalog.
 */
@Composable
private fun CatalogFilterRow(
    uiState: HomeUiState,
    onGenderSelected: (Gender?) -> Unit,
    onFaceShapeSelected: (FaceShape?) -> Unit,
    onHairLengthSelected: (HairLength?) -> Unit,
    onHairTextureSelected: (HairTexture?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterDropdown(
            label = "Gender",
            selected = uiState.genderFilter,
            options = listOf(Gender.MALE, Gender.FEMALE),
            optionLabel = { genderFilterLabel(it) },
            onSelected = onGenderSelected
        )
        FilterDropdown(
            label = "Face shape",
            selected = uiState.faceShapeFilter,
            options = FaceShape.entries,
            optionLabel = { it.displayName },
            onSelected = onFaceShapeSelected
        )
        FilterDropdown(
            label = "Hair type",
            selected = uiState.hairTextureFilter,
            options = HairTexture.entries,
            optionLabel = { it.displayName },
            onSelected = onHairTextureSelected
        )
        FilterDropdown(
            label = "Length",
            selected = uiState.hairLengthFilter,
            options = HairLength.entries - HairLength.BALD,
            optionLabel = { it.displayName },
            onSelected = onHairLengthSelected
        )
    }
}

/** A signed-in "Non-binary"/"Prefer not to say" user has no dedicated dropdown entry, so their default shows as "All". */
private fun genderFilterLabel(gender: Gender): String = when (gender) {
    Gender.NON_BINARY, Gender.PREFER_NOT_TO_SAY -> "All"
    else -> gender.displayName
}
