package com.hairconsultant.app.ui.facescan

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.hairconsultant.app.ui.chatbot.ChatBotSheet
import com.hairconsultant.app.ui.components.ChatFab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FaceScanScreen(viewModel: FaceScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(modifier = Modifier.fillMaxSize())
            uiState.triedOnHaircut?.let { haircut -> ArTryOnOverlay(haircut) }
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

        if (uiState.triedOnHaircut != null) {
            FloatingActionButton(
                onClick = viewModel::clearTryOn,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
            ) { Icon(Icons.Filled.Close, contentDescription = "Stop trying on") }
        }

        if (uiState.stage == FaceScanStage.ANALYZING) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.large)
                    .padding(24.dp)
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        if (uiState.suggestions.isNotEmpty()) {
            TryOnStrip(
                suggestions = uiState.suggestions,
                selectedHaircut = uiState.triedOnHaircut,
                onSelect = viewModel::onHaircutTryOn,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        if (cameraPermissionState.status.isGranted && uiState.stage == FaceScanStage.IDLE) {
            FloatingActionButton(
                onClick = viewModel::startScan,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) { Icon(Icons.Filled.CameraAlt, contentDescription = "Scan face") }
        }

        ChatFab(
            onClick = { viewModel.chatBot.setOpen(true) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
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
