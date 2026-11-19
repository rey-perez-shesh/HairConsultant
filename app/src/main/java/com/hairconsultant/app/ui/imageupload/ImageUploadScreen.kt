package com.hairconsultant.app.ui.imageupload

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ImageUploadScreen(viewModel: ImageUploadViewModel, onOpenColorStudio: (Uri) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val lookSaved by viewModel.isLookSaved.collectAsState()
    val scanPhotoUri by viewModel.scanPhotoUri.collectAsState()
    val sheetState = rememberChatBotSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::onImagePicked) }
    val pickPhoto = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val openChat = { viewModel.chatBot.setOpen(true) }
    val useScanPhoto: () -> Unit = { scanPhotoUri?.let(viewModel::onImagePicked) }

    // Saveable: MIUI can kill the app while the camera app is open, and the shot lands here.
    var cameraShotUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved -> cameraShotUri?.takeIf { saved }?.let(viewModel::onImagePicked) }
    val openCamera: () -> Unit = {
        val shot = File(File(context.cacheDir, "camera").apply { mkdirs() }, "upload_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shot)
        cameraShotUri = uri
        try {
            takePictureLauncher.launch(uri)
        } catch (error: ActivityNotFoundException) {
            scope.launch { snackbars.showSnackbar("No camera app on this phone. Choose a photo instead.") }
        }
    }
    // The app declares CAMERA, so the system camera won't open for it until that's granted.
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA) { granted ->
        if (granted) {
            openCamera()
        } else {
            scope.launch {
                val result = snackbars.showSnackbar("Allow camera access to take a photo.", actionLabel = "Settings")
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
            }
        }
    }
    val takePhoto: () -> Unit = {
        if (cameraPermission.status.isGranted) openCamera() else cameraPermission.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.sourceImageUri == null) {
            UploadEmptyState(
                scanPhotoUri = scanPhotoUri,
                onUseScanPhoto = useScanPhoto,
                onChoosePhoto = pickPhoto,
                onTakePhoto = takePhoto,
                onOpenChat = openChat
            )
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
                Box {
                    var sourceMenuOpen by remember { mutableStateOf(false) }
                    OverlayButton(text = "Change photo", onClick = { sourceMenuOpen = true })
                    DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                        scanPhotoUri?.let { uri ->
                            DropdownMenuItem(
                                text = { Text("Use my scan photo") },
                                leadingIcon = { ScanThumbnail(uri, Modifier.size(width = 24.dp, height = 32.dp)) },
                                onClick = { sourceMenuOpen = false; useScanPhoto() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Choose photo") },
                            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                            onClick = { sourceMenuOpen = false; pickPhoto() }
                        )
                        DropdownMenuItem(
                            text = { Text("Take a photo") },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                            onClick = { sourceMenuOpen = false; takePhoto() }
                        )
                    }
                }
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
                // Try again after a preview or a failed one; Save look only for a generated preview,
                // since there's nothing of the user's own to keep before one.
                val failed = uiState.generationError != null && uiState.selectedHaircut != null && !uiState.isGenerating
                if (uiState.lookId != null || failed) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlayButton(text = "Try again", icon = Icons.Filled.Refresh, onClick = viewModel::regeneratePreview)
                        if (uiState.lookId != null) {
                            OverlayButton(
                                text = if (lookSaved) "Saved to Favorites" else "Save look",
                                icon = if (lookSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                onClick = viewModel::toggleSaveLook
                            )
                        }
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

        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
        )
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

/**
 * Before a photo is picked: the shell header, an oval guide, what makes a good photo, then where
 * the photo comes from: the last Face Scan's frame (once there is one), the gallery as the one
 * oxblood action, or the camera.
 */
@Composable
private fun UploadEmptyState(
    scanPhotoUri: Uri?,
    onUseScanPhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenChat: () -> Unit
) {
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
                // Hair length is read from how the strands lie (HairFlow), like on Face Scan.
                PhotoTip("Hair combed neatly to one side, strands in order, so its length can be measured")
                PhotoTip("Even light, no strong shadows")
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (scanPhotoUri != null) ScanPhotoTile(scanPhotoUri, onUseScanPhoto)
                Button(
                    onClick = onChoosePhoto,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Choose photo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                }
                OutlinedButton(
                    onClick = onTakePhoto,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    // Ink, not oxblood: oxblood belongs to the one primary action above.
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Take a photo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** The last Face Scan's frame as a one-tap source, shown with the picture itself so it's clear which photo it is. */
@Composable
private fun ScanPhotoTile(uri: Uri, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScanThumbnail(uri, Modifier.size(width = 42.dp, height = 56.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Use my scan photo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "From your last Face Scan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Decorative: the label beside it names the action. The scan frame is portrait, so it's cropped into a 3:4 tile. */
@Composable
private fun ScanThumbnail(uri: Uri, modifier: Modifier) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small).background(LocalShopColors.current.imageWell)
    )
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
