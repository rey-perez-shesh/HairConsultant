package com.hairconsultant.app.ui.imageupload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hairconsultant.app.ui.chatbot.ChatBotSheet
import com.hairconsultant.app.ui.chatbot.rememberChatBotSheetState
import com.hairconsultant.app.ui.components.ChatIconButton
import com.hairconsultant.app.ui.components.OverlayButton
import com.hairconsultant.app.ui.components.OverlayIconButton
import com.hairconsultant.app.ui.components.StatusBarScrim
import com.hairconsultant.app.ui.facescan.TryOnStrip
import com.hairconsultant.app.ui.theme.CameraScrim
import com.hairconsultant.app.ui.theme.LocalShopColors
import com.hairconsultant.app.ui.theme.OnShell
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageUploadScreen(viewModel: ImageUploadViewModel, onOpenColorStudio: (Uri) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val lookSaved by viewModel.isLookSaved.collectAsState()
    val sheetState = rememberChatBotSheetState()
    val scope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::onImagePicked) }
    val pickPhoto = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val openChat = { viewModel.chatBot.setOpen(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.sourceImageUri == null) {
            UploadEmptyState(onChoosePhoto = pickPhoto, onOpenChat = openChat)
            StatusBarScrim()
        } else {
            // On a failed generation, ImageUploadViewModel's chat message tells the user it's
            // "showing the style's reference photo instead" — this fallback is what makes that
            // true, rather than silently redisplaying their original, unstyled photo.
            val fallbackUri = uiState.selectedHaircut?.takeIf { uiState.generationError != null }
                ?.let { runCatching { Uri.parse(it.imageUrl) }.getOrNull() }
                ?: uiState.sourceImageUri
            val displayUri = uiState.generatedImageUri ?: fallbackUri
            AsyncImage(
                model = displayUri,
                contentDescription = "Uploaded photo",
                modifier = Modifier.fillMaxSize().background(LocalShopColors.current.imageWell),
                // Fit, not Crop: on a tall screen Crop zooms into the face and cuts the hair off,
                // which is the one thing this screen is for.
                contentScale = ContentScale.Fit
            )

            if (uiState.isGenerating) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(CameraScrim, MaterialTheme.shapes.large)
                        .padding(24.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = OnShell)
                    Text(
                        text = uiState.selectedHaircut?.let { "Generating ${it.name}…" } ?: "Generating…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnShell
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayButton(text = "Change photo", onClick = pickPhoto)
                val hasInitialAnalysis = uiState.stage != ImageUploadStage.IDLE && uiState.stage != ImageUploadStage.ANALYZING
                if (hasInitialAnalysis) {
                    OverlayButton(text = "Rescan", icon = Icons.Filled.Refresh, onClick = viewModel::rescan)
                }
                Box(modifier = Modifier.weight(1f))
                // The user's own picture only (their photo, or the generated cut on it), never the
                // catalog's reference photo shown when a generation fails.
                val colorUri = uiState.generatedImageUri ?: uiState.sourceImageUri
                if (colorUri != null && !uiState.isGenerating) {
                    OverlayIconButton(
                        icon = Icons.Filled.Palette,
                        contentDescription = "Try a hair color on this photo",
                        onClick = { onOpenColorStudio(colorUri) }
                    )
                }
                ChatIconButton(onClick = openChat, onScrim = true)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Only for a generated preview: there's nothing of the user's own to keep before one.
                if (uiState.lookId != null) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlayButton(text = "Try again", icon = Icons.Filled.Refresh, onClick = viewModel::regeneratePreview)
                        OverlayButton(
                            text = if (lookSaved) "Saved to Favorites" else "Save look",
                            icon = if (lookSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            onClick = viewModel::toggleSaveLook
                        )
                    }
                }
                if (uiState.suggestions.isNotEmpty()) {
                    TryOnStrip(
                        suggestions = uiState.suggestions,
                        selectedHaircut = uiState.selectedHaircut,
                        onSelect = viewModel::onHaircutSelected
                    )
                }
            }
        }
    }

    if (chatState.isOpen) {
        ChatBotSheet(
            state = chatState,
            sheetState = sheetState,
            onDismiss = { viewModel.chatBot.setOpen(false) },
            onInputChange = viewModel.chatBot::updateInput,
            onSend = { scope.launch { viewModel.chatBot.sendCurrentInput() } },
            onHaircutSelected = viewModel::onHaircutSelected,
            onQuickReplySelected = viewModel::onQuickReply
        )
    }
}

/** Before a photo is picked: the shell header, an oval guide, what makes a good photo, and the one oxblood action. */
@Composable
private fun UploadEmptyState(onChoosePhoto: () -> Unit, onOpenChat: () -> Unit) {
    val shop = LocalShopColors.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(shop.shell)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UPLOAD A PHOTO",
                    style = MaterialTheme.typography.headlineMedium,
                    color = shop.onShell,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "We'll read your face shape and hair, then preview cuts on you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = shop.onShellMuted
                )
            }
            ChatIconButton(onClick = onOpenChat)
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OvalGuideCard()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoTip("Face the camera, chin level")
                PhotoTip("Hair visible, nothing covering it")
                PhotoTip("Even light, no strong shadows")
            }
            Button(
                onClick = onChoosePhoto,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Choose photo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun OvalGuideCard() {
    val outline = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val fit = LocalShopColors.current.fit
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 140.dp, height = 170.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx()))
            drawRoundRect(color = outline, style = Stroke(width = 2.dp.toPx(), pathEffect = dash), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
            val inset = 10.dp.toPx()
            drawOval(color = fit, topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(width = 3.dp.toPx(), pathEffect = dash))
            val head = 22.dp.toPx()
            drawCircle(color = ink, radius = head, center = Offset(size.width / 2, size.height * 0.42f), style = Stroke(width = 2.5.dp.toPx()))
            drawArc(
                color = ink, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(size.width / 2 - 36.dp.toPx(), size.height * 0.66f),
                size = Size(72.dp.toPx(), 40.dp.toPx()), style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Text("One clear, front-facing photo", style = MaterialTheme.typography.bodyMedium, color = ink)
    }
}

@Composable
private fun PhotoTip(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // A bare tick, not a filled box: these are tips, not checkboxes the user already ticked.
        Icon(Icons.Filled.Check, contentDescription = null, tint = LocalShopColors.current.fit, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
