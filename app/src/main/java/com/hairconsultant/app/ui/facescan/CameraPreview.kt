package com.hairconsultant.app.ui.facescan

import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.data.analysis.LiveFaceLandmarker
import com.hairconsultant.app.data.analysis.LiveHairSegmenter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen live front-camera feed with MediaPipe face mesh + hair mask.
 *
 * Try-on stack (bottom → top):
 * PreviewView → background blur → hair cover → SceneView (GLB) → face punch-through.
 *
 * [cameraEnabled] pauses CameraX so DeepAR can take the camera. On return we bind
 * **preview-only first**, then attach MediaPipe analysis after a short delay — keeps
 * the live feed snappy instead of lagging on a full cold restart.
 */
@Composable
fun CameraPreview(
    landmarkStore: FaceLandmarkStore,
    hairRemovalEnabled: Boolean = false,
    backgroundBlurEnabled: Boolean = false,
    suppressOverlayDrawing: Boolean = false,
    faceArAttachment: FaceArSceneAttachment? = null,
    cameraEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayFrame by landmarkStore.overlay.collectAsState()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val hairSegmenter = remember { LiveHairSegmenter(context.applicationContext, landmarkStore::publishHair) }
    val landmarker = remember {
        LiveFaceLandmarker(context.applicationContext, landmarkStore, hairSegmenter)
    }
    val binder = remember(lifecycleOwner, landmarker) {
        FaceScanCameraBinder(
            lifecycleOwner = lifecycleOwner,
            landmarker = landmarker,
            analysisExecutor = analysisExecutor,
            landmarkStore = landmarkStore
        )
    }

    DisposableEffect(lifecycleOwner, landmarker, hairSegmenter, faceArAttachment, binder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (binder.wantCamera.get()) {
                        binder.requestBind(previewOnlyFirst = true, reason = "onResume")
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { landmarker.close() }
            runCatching { hairSegmenter.close() }
            landmarkStore.publishEmpty()
            faceArAttachment?.setSceneView(null)
            binder.release()
            analysisExecutor.shutdownNow()
        }
    }

    DisposableEffect(cameraEnabled) {
        binder.wantCamera.set(cameraEnabled)
        if (cameraEnabled) {
            binder.requestBind(previewOnlyFirst = true, reason = "cameraEnabled=true")
        } else {
            binder.unbind("cameraEnabled=false")
        }
        onDispose { }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val backgroundBlurView = BackgroundBlurSurfaceView(ctx).apply {
                tag = BackgroundBlurSurfaceView.VIEW_TAG
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val hairCoverView = HairCoverSurfaceView(ctx).apply {
                tag = HairCoverSurfaceView.VIEW_TAG
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
                addView(backgroundBlurView)
                addView(hairCoverView)
                addView(overlayView)
            }
            faceArAttachment?.bindFrameLayout(root)
            binder.attachPreviewView(previewView)
            binder.wantCamera.set(cameraEnabled)
            if (cameraEnabled) {
                binder.requestBind(previewOnlyFirst = false, reason = "factory")
            }
            root
        },
        update = { root ->
            faceArAttachment?.bindFrameLayout(root)
            faceArAttachment?.sceneView?.let { sv ->
                if (sv.parent === root) {
                    root.bringChildToFront(sv)
                }
            }
            val previewBitmap = landmarkStore.peekPreviewBitmap()
            val backgroundBlurView = root.findViewWithTag<BackgroundBlurSurfaceView>(
                BackgroundBlurSurfaceView.VIEW_TAG
            )
            backgroundBlurView?.apply {
                setBlurEnabled(backgroundBlurEnabled && overlayFrame.faceDetected)
                updateFrame(overlayFrame, previewBitmap)
            }
            val hairCoverView = root.findViewWithTag<HairCoverSurfaceView>(
                HairCoverSurfaceView.VIEW_TAG
            )
            hairCoverView?.apply {
                setCoverEnabled(hairRemovalEnabled && overlayFrame.hairMask != null)
                updateFrame(overlayFrame, previewBitmap)
            }
            val overlayView = (0 until root.childCount).firstNotNullOfOrNull { i ->
                root.getChildAt(i) as? FaceLandmarkOverlayView
            }
            overlayView?.apply {
                visibility = if (suppressOverlayDrawing) View.GONE else View.VISIBLE
                if (!suppressOverlayDrawing) {
                    setBlurSource(previewBitmap)
                    setHairRemovalEnabled(hairRemovalEnabled)
                    setFrame(overlayFrame)
                }
            }
        }
    )
}

/**
 * Owns CameraX bind/unbind so Face Scan can pause for DeepAR and recover quickly.
 *
 * After DeepAR: bind Preview alone first (live feed), then attach ImageAnalysis
 * (MediaPipe) after [ANALYSIS_ATTACH_DELAY_MS] so return does not hitch.
 */
private class FaceScanCameraBinder(
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val landmarker: LiveFaceLandmarker,
    private val analysisExecutor: java.util.concurrent.ExecutorService,
    private val landmarkStore: FaceLandmarkStore
) {
    val wantCamera = AtomicBoolean(true)
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var released = false
    private var boundWithAnalysis = false
    private var boundPreviewOnly = false
    private var lastBindRequestAt = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var analysisAttachRunnable: Runnable? = null
    private var pendingBindRunnable: Runnable? = null

    fun attachPreviewView(view: PreviewView) {
        previewView = view
        val ctx = view.context
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            if (released) return@addListener
            cameraProvider = future.get()
            if (wantCamera.get()) requestBind(previewOnlyFirst = false, reason = "providerReady")
        }, ContextCompat.getMainExecutor(ctx))
    }

    /**
     * Debounced bind. When [previewOnlyFirst] is true (return from DeepAR / resume),
     * show camera immediately then attach MediaPipe shortly after.
     */
    fun requestBind(previewOnlyFirst: Boolean, reason: String) {
        if (released || !wantCamera.get()) return
        val now = android.os.SystemClock.uptimeMillis()
        // Collapse onResume + cameraEnabled=true into one bind.
        if (now - lastBindRequestAt < BIND_DEBOUNCE_MS && (boundPreviewOnly || boundWithAnalysis)) {
            Log.d(TAG, "Skip duplicate bind ($reason)")
            return
        }
        lastBindRequestAt = now

        pendingBindRunnable?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            if (previewOnlyFirst) {
                bindPreviewOnly(reason)
                scheduleAnalysisAttach()
            } else {
                bindFull(reason)
            }
        }
        pendingBindRunnable = task
        mainHandler.post(task)
    }

    private fun scheduleAnalysisAttach() {
        analysisAttachRunnable?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            if (released || !wantCamera.get()) return@Runnable
            if (boundWithAnalysis) return@Runnable
            bindFull("analysisAttach")
        }
        analysisAttachRunnable = task
        mainHandler.postDelayed(task, ANALYSIS_ATTACH_DELAY_MS)
    }

    private fun bindPreviewOnly(reason: String) {
        if (released || !wantCamera.get()) return
        val view = previewView ?: return
        val provider = cameraProvider ?: return
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Log.d(TAG, "Skip preview bind ($reason) — lifecycle=${lifecycleOwner.lifecycle.currentState}")
            return
        }
        if (boundPreviewOnly && !boundWithAnalysis) {
            Log.d(TAG, "Already preview-only ($reason)")
            return
        }
        runCatching {
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview
            )
            boundPreviewOnly = true
            boundWithAnalysis = false
            Log.i(TAG, "Preview-only bound ($reason)")
        }.onFailure {
            Log.e(TAG, "Preview bind failed ($reason): ${it.message}")
            // Retry once shortly — camera may still be releasing from DeepAR.
            mainHandler.postDelayed({
                if (wantCamera.get() && !released) bindPreviewOnly("retry")
            }, 180L)
        }
    }

    private fun bindFull(reason: String) {
        if (released || !wantCamera.get()) return
        val view = previewView ?: return
        val provider = cameraProvider ?: return
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Log.d(TAG, "Skip full bind ($reason) — lifecycle=${lifecycleOwner.lifecycle.currentState}")
            return
        }
        if (boundWithAnalysis) {
            Log.d(TAG, "Already full-bound ($reason)")
            return
        }
        runCatching {
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
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
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
            boundPreviewOnly = true
            boundWithAnalysis = true
            Log.i(TAG, "Full camera bound ($reason)")
            if (!landmarker.isReady) {
                landmarkStore.publishEmpty("Face guide failed to start")
            }
        }.onFailure {
            Log.e(TAG, "Full bind failed ($reason): ${it.message}")
            mainHandler.postDelayed({
                if (wantCamera.get() && !released && !boundWithAnalysis) {
                    bindFull("retry")
                }
            }, 200L)
        }
    }

    fun unbind(reason: String) {
        analysisAttachRunnable?.let { mainHandler.removeCallbacks(it) }
        analysisAttachRunnable = null
        pendingBindRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingBindRunnable = null
        runCatching {
            cameraProvider?.unbindAll()
            boundPreviewOnly = false
            boundWithAnalysis = false
            Log.i(TAG, "Camera unbound ($reason)")
        }
    }

    fun release() {
        released = true
        wantCamera.set(false)
        unbind("release")
        cameraProvider = null
        previewView = null
    }

    companion object {
        private const val TAG = "FaceScanCamera"
        private const val BIND_DEBOUNCE_MS = 250L
        /** Let the live preview appear before MediaPipe/hair analysis load the CPU. */
        private const val ANALYSIS_ATTACH_DELAY_MS = 320L
    }
}
