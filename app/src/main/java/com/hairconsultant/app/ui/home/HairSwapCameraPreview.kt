package com.hairconsultant.app.ui.home

import android.os.SystemClock
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hairconsultant.app.data.analysis.HairMask
import com.hairconsultant.app.data.analysis.LiveHairSegmenter
import com.hairconsultant.app.data.analysis.rotateAndMirror
import com.hairconsultant.app.domain.model.Haircut
import java.util.concurrent.Executors

/**
 * Live front-camera feed with the user's real hair covered via live MediaPipe hair segmentation —
 * no face-mesh detection at all (unlike the Face Scan tab's camera), just the segmenter deciding
 * which live pixels are hair, with soft per-pixel confidence, so [HairSwapOverlayView] can cover
 * them with a procedural skin-tone scalp fill instead of a sharp placeholder photo.
 */
@Composable
fun HairSwapCameraPreview(haircut: Haircut, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mask by remember { mutableStateOf<HairMask?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val hairSegmenter = remember {
        LiveHairSegmenter(context.applicationContext) { result -> mask = result }
    }

    DisposableEffect(lifecycleOwner, hairSegmenter) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            hairSegmenter.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val overlayView = HairSwapOverlayView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val root = FrameLayout(ctx).apply {
                addView(previewView)
                addView(overlayView)
            }
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
                                try {
                                    val bitmap = imageProxy.toBitmap()
                                    val rotated = rotateAndMirror(bitmap, imageProxy.imageInfo.rotationDegrees, true)
                                    if (rotated !== bitmap) bitmap.recycle()
                                    hairSegmenter.detect(rotated, SystemClock.uptimeMillis())
                                } finally {
                                    imageProxy.close()
                                }
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
                },
                ContextCompat.getMainExecutor(ctx)
            )
            root
        },
        update = { root ->
            val overlayView = root.getChildAt(1) as HairSwapOverlayView
            overlayView.setMask(mask)
        }
    )
}
