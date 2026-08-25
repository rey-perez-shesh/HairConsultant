package com.hairconsultant.app.ui.facescan

import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.data.analysis.LiveFaceLandmarker
import com.hairconsultant.app.data.analysis.LiveHairSegmenter
import java.util.concurrent.Executors

/**
 * Full-screen live front-camera feed with MediaPipe face mesh + hair mask.
 *
 * Try-on stack: PreviewView (normal camera) → SceneView (GLB via [FaceArSceneAttachment]).
 */
@Composable
fun CameraPreview(
    landmarkStore: FaceLandmarkStore,
    hairRemovalEnabled: Boolean = false,
    suppressOverlayDrawing: Boolean = false,
    faceArAttachment: FaceArSceneAttachment? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayFrame by landmarkStore.overlay.collectAsState()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val hairSegmenter = remember { LiveHairSegmenter(context.applicationContext, landmarkStore) }
    val landmarker = remember {
        LiveFaceLandmarker(context.applicationContext, landmarkStore, hairSegmenter)
    }

    DisposableEffect(lifecycleOwner, landmarker, hairSegmenter, faceArAttachment) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            landmarker.close()
            hairSegmenter.close()
            analysisExecutor.shutdown()
            landmarkStore.publishEmpty()
            faceArAttachment?.setSceneView(null)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // PERFORMANCE = internal SurfaceView. Required so SceneView (also SurfaceView)
                // can composite transparently on top. COMPATIBLE/TextureView underneath always
                // shows black in the transparent pixels of an overlay SurfaceView.
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val overlayView = FaceLandmarkOverlayView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val root = FrameLayout(ctx).apply {
                addView(previewView)
                addView(overlayView)
            }
            faceArAttachment?.bindFrameLayout(root)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                landmarker.detect(imageProxy, isFrontCamera = true)
                            }
                        }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            analysis
                        )
                    }
                    if (!landmarker.isReady) {
                        landmarkStore.publishEmpty("Face guide failed to start")
                    }
                },
                ContextCompat.getMainExecutor(ctx)
            )
            root
        },
        update = { root ->
            faceArAttachment?.bindFrameLayout(root)
            faceArAttachment?.sceneView?.let { sv ->
                if (sv.parent === root) {
                    root.bringChildToFront(sv)
                }
            }
            val overlayView = (0 until root.childCount).firstNotNullOfOrNull { i ->
                root.getChildAt(i) as? FaceLandmarkOverlayView
            }
            overlayView?.apply {
                visibility = if (suppressOverlayDrawing) View.GONE else View.VISIBLE
                if (!suppressOverlayDrawing) {
                    setBlurSource(landmarkStore.peekPreviewBitmap())
                    setHairRemovalEnabled(hairRemovalEnabled)
                    setFrame(overlayFrame)
                }
            }
        }
    )
}
