package com.hairconsultant.app.ui.home

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.ui.facescan.ArTryOnOverlay
import com.hairconsultant.app.ui.facescan.CameraPreview
import com.hairconsultant.app.ui.facescan.FaceArSceneAttachment
import com.hairconsultant.app.ui.facescan.HairColorPreset
import com.hairconsultant.app.ui.facescan.HairColorStrip
import com.hairconsultant.app.ui.facescan.HairstyleArCatalog

/**
 * Live camera try-on launched from a Home catalog card.
 *
 * - Cards with a dedicated GLB in [HairstyleArCatalog] (Slicked Back, Buzz Cut, â€¦)
 *   use the same Face Scan AR path: CameraX + MediaPipe + HeadPoseEstimator + SceneView GLB.
 * - Other cards keep the existing 2D hair-swap preview until their GLB is mapped.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraTryOnScreen(haircut: Haircut, onClose: () -> Unit) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            if (HairstyleArCatalog.hasDedicatedGlb(haircut)) {
                CatalogGlbArTryOn(haircut = haircut)
            } else {
                HairSwapCameraPreview(haircut = haircut, modifier = Modifier.fillMaxSize())
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
                    "Camera access is needed to try this style on.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text("Grant camera permission") }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close try-on")
            }
        }
    }
}

/**
 * Same compositing stack as Face Scan try-on:
 * PreviewView → background blur → hair cover → SceneView (GLB) → face punch-through.
 * Hair mask and background blur are active automatically when try-on opens.
 */
@Composable
private fun CatalogGlbArTryOn(haircut: Haircut) {
    val landmarkStore = remember { FaceLandmarkStore() }
    val faceArAttachment = remember { FaceArSceneAttachment() }
    var hairColor by remember { mutableStateOf(HairColorPreset.NATURAL) }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            landmarkStore = landmarkStore,
            hairRemovalEnabled = true,
            backgroundBlurEnabled = true,
            suppressOverlayDrawing = true,
            faceArAttachment = faceArAttachment,
            modifier = Modifier.fillMaxSize()
        )
        ArTryOnOverlay(
            haircut = haircut,
            landmarkStore = landmarkStore,
            faceArAttachment = faceArAttachment,
            hairColor = hairColor
        )
        HairColorStrip(
            selected = hairColor,
            onSelect = { hairColor = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}


