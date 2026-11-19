package com.hairconsultant.app.ui.facescan

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.hairconsultant.app.data.analysis.FaceOverlayFrame
import com.hairconsultant.app.ui.chatbot.ChatBotSheet
import com.hairconsultant.app.ui.chatbot.rememberChatBotSheetState
import com.hairconsultant.app.ui.components.ChatIconButton
import com.hairconsultant.app.ui.components.OverlayButton
import com.hairconsultant.app.ui.components.OverlayIconButton
import com.hairconsultant.app.ui.deepar.DeepArCurtainsActivity
import com.hairconsultant.app.ui.theme.CameraScrim
import com.hairconsultant.app.ui.theme.OnShell
import com.hairconsultant.app.ui.theme.OnShellMuted
import com.hairconsultant.app.ui.theme.WarnAmberDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FaceScanScreen(viewModel: FaceScanViewModel, onOpenColorStudio: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val sheetState = rememberChatBotSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val faceArAttachment = remember { FaceArSceneAttachment() }

    // Keep CameraPreview mounted; only pause CameraX while DeepAR Activity owns the camera.
    var deepArCameraPaused by remember { mutableStateOf(false) }

    val deepArLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // DeepAR frees the camera in onPause/close; brief settle then re-enable preview.
        scope.launch {
            delay(80)
            deepArCameraPaused = false
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Launch DeepAR without tearing down CameraPreview (that caused the black screen).
    LaunchedEffect(uiState.triedOnHaircut?.id) {
        val haircut = uiState.triedOnHaircut ?: return@LaunchedEffect
        if (!HairstyleArCatalog.shouldUseDeepAr(haircut)) return@LaunchedEffect

        val effect = HairstyleArCatalog.resolveDeepArEffect(context, haircut)
        if (effect == null) {
            Toast.makeText(context, "Couldn't download this try-on — check your connection", Toast.LENGTH_SHORT).show()
            viewModel.clearTryOn()
            return@LaunchedEffect
        }

        deepArCameraPaused = true
        // Let CameraX unbind before DeepAR grabs the camera.
        delay(120)
        deepArLauncher.launch(
            DeepArCurtainsActivity.intent(context, effect, haircut.name, haircut.id)
        )
        viewModel.clearTryOn()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(
                landmarkStore = viewModel.landmarkStore,
                hairRemovalEnabled = uiState.triedOnHaircut != null,
                backgroundBlurEnabled = uiState.triedOnHaircut != null,
                suppressOverlayDrawing = uiState.triedOnHaircut != null,
                faceArAttachment = faceArAttachment,
                cameraEnabled = !deepArCameraPaused,
                lockExposure = uiState.stage == FaceScanStage.ANALYZING,
                modifier = Modifier.fillMaxSize()
            )
            // Warm the Meshy GLB in the background once suggestions exist (before tap).
            WigWarmupHost(enabled = uiState.suggestions.isNotEmpty() && !deepArCameraPaused)
            uiState.triedOnHaircut?.let { haircut ->
                if (HairstyleArCatalog.shouldUseDeepAr(haircut)) {
                    // DeepAR Activity handles try-on; no SceneView/PNG overlay.
                } else if (HairstyleArCatalog.hasDedicatedGlb(haircut)) {
                    ArTryOnOverlay(
                        haircut = haircut,
                        landmarkStore = viewModel.landmarkStore,
                        faceArAttachment = faceArAttachment,
                        hairColor = uiState.tryOnHairColor
                    )
                } else if (HairstyleArCatalog.hasPngTryOn(haircut)) {
                    PngTryOnOverlay(
                        haircut = haircut,
                        landmarkStore = viewModel.landmarkStore
                    )
                } else {
                    ArTryOnOverlay(
                        haircut = haircut,
                        landmarkStore = viewModel.landmarkStore,
                        faceArAttachment = faceArAttachment,
                        hairColor = uiState.tryOnHairColor
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp))
                Text(
                    "Camera access is needed to scan your face shape and hair.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text("Grant camera permission") }
            }
        }

        val guide by viewModel.landmarkStore.overlay.collectAsState()
        val scanning = cameraPermissionState.status.isGranted && uiState.stage == FaceScanStage.IDLE && !deepArCameraPaused
        val hasInitialAnalysis = uiState.stage != FaceScanStage.IDLE && uiState.stage != FaceScanStage.ANALYZING
        val tryingOn = uiState.triedOnHaircut != null && !HairstyleArCatalog.shouldUseDeepAr(uiState.triedOnHaircut!!)

        // Top bar over the camera: the scan guidance sign (and the hair tip) while scanning,
        // otherwise the try-on control; the chat opener sits at its end either way.
        if (!deepArCameraPaused) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        scanning -> ScanGuideSign(guide = guide, modifier = Modifier.weight(1f))
                        else -> Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tryingOn) {
                                OverlayButton(text = "Stop try-on", icon = Icons.Filled.Close, onClick = viewModel::clearTryOn)
                            }
                        }
                    }
                    // Icon only: the scan guidance sign beside it needs the width.
                    OverlayIconButton(icon = Icons.Filled.Palette, contentDescription = "Try a hair color", onClick = onOpenColorStudio)
                    ChatIconButton(onClick = { viewModel.chatBot.setOpen(true) }, onScrim = true)
                }
                if (scanning) HairCombTip()
            }
        }

        if (uiState.stage == FaceScanStage.ANALYZING) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(CameraScrim, MaterialTheme.shapes.large)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = OnShell)
                Text("Hold still", style = MaterialTheme.typography.bodyMedium, color = OnShell)
            }
        }

        // Bottom: the try-on strips once there are picks, above the main action, which stays at
        // the bottom centre: Scan face before a scan, Rescan after it.
        if (!deepArCameraPaused) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.suggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.triedOnHaircut != null) {
                            HairColorStrip(
                                selected = uiState.tryOnHairColor,
                                onSelect = viewModel::onTryOnHairColor
                            )
                        }
                        TryOnStrip(
                            suggestions = uiState.suggestions,
                            selectedHaircut = uiState.triedOnHaircut,
                            onSelect = viewModel::onHaircutTryOn
                        )
                    }
                }
                if (scanning) {
                    // Scanning is only allowed once the guide shows a face inside the oval, facing the camera; the guide's
                    // own text says what to fix, so a disabled tap just does nothing.
                    val canScan = guide.faceDetected && guide.alignmentHint == null
                    ExtendedFloatingActionButton(
                        onClick = { if (canScan) viewModel.startScan() },
                        icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        text = { Text("Scan face") },
                        containerColor = if (canScan) MaterialTheme.colorScheme.primary else OnShell.copy(alpha = 0.10f),
                        contentColor = if (canScan) MaterialTheme.colorScheme.onPrimary else OnShell.copy(alpha = 0.45f),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = if (canScan) 6.dp else 0.dp),
                        modifier = Modifier.semantics {
                            contentDescription = if (canScan) "Scan face" else "Scan face — ${guide.alignmentHint ?: "line up your face with the oval first"}"
                            if (!canScan) disabled()
                        }
                    )
                } else if (hasInitialAnalysis) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::rescan,
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        text = { Text("Rescan") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
            onHaircutSelected = viewModel::onHaircutTryOn,
            onQuickReplySelected = viewModel::onQuickReply
        )
    }
}

/**
 * Hair length is read from how the strands lie ([com.hairconsultant.app.data.analysis.HairFlow]),
 * so ask for them combed into order before the scan.
 */
@Composable
private fun HairCombTip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(CameraScrim, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = OnShell, modifier = Modifier.size(18.dp))
        Text(
            "Comb your hair neatly to one side so the strands lie in order. That's how I measure your hair length.",
            style = MaterialTheme.typography.bodySmall,
            color = OnShell
        )
    }
}

/**
 * The one instruction that matters right now, on a dark sign over the camera: amber while the
 * face is off-centre or turned (the overlay oval goes broken amber too), solid white once it's locked in.
 */
@Composable
private fun ScanGuideSign(guide: FaceOverlayFrame, modifier: Modifier = Modifier) {
    val hint = guide.alignmentHint
    val (title, subtitle, color) = when {
        !guide.faceDetected -> Triple("Line up your face with the oval", "Hold the phone at eye level, in good light", OnShell)
        hint != null -> Triple(hint, "Scan unlocks once you're lined up", WarnAmberDark)
        else -> Triple("Face locked in", "Hold still, then tap Scan face", OnShell)
    }
    val icon = when {
        !guide.faceDetected -> Icons.Filled.Face
        hint != null -> Icons.Filled.PriorityHigh
        else -> Icons.Filled.Check
    }
    Row(
        modifier = modifier
            .background(CameraScrim, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).border(2.dp, color, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnShellMuted)
        }
    }
}
