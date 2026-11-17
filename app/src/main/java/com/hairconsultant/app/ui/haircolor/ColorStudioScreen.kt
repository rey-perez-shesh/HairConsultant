package com.hairconsultant.app.ui.haircolor

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.hairconsultant.app.BuildConfig
import com.hairconsultant.app.data.analysis.HairMask
import com.hairconsultant.app.data.analysis.LiveHairSegmenter
import com.hairconsultant.app.data.analysis.ScanBitmapLoader
import com.hairconsultant.app.data.analysis.StillImageHairSegmenter
import com.hairconsultant.app.data.analysis.rotateAndMirror
import com.hairconsultant.app.ui.components.OverlayButton
import com.hairconsultant.app.ui.components.OverlayIconButton
import com.hairconsultant.app.ui.theme.CameraScrim
import com.hairconsultant.app.ui.theme.EaseOutStrong
import com.hairconsultant.app.ui.theme.ImageWellDark
import com.hairconsultant.app.ui.theme.OnShell
import com.hairconsultant.app.ui.theme.OnShellMuted
import com.hairconsultant.app.ui.theme.WarnAmberDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/** What the hair detector currently knows; drives the sign under the top bar and the plate's hint. */
private enum class StudioStatus { STARTING, READY, NO_HAIR, LOW_LIGHT, UNAVAILABLE }

/**
 * Full-screen hair color try-on on the user's own hair: live from the front camera, or on
 * [photoUri] when the screen was opened from a photo.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ColorStudioScreen(
    viewModel: ColorStudioViewModel,
    photoUri: Uri?,
    onClose: () -> Unit,
    onViewLooks: () -> Unit
) {
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fromPhoto = photoUri != null
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val canRender = remember { context.supportsGlEs3() }

    val renderer = remember { HairColorRenderer().apply { fitInside = fromPhoto } }
    val surface = remember {
        if (!canRender) null else GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    val requestRender = remember { { surface?.requestRender(); Unit } }
    var status by remember { mutableStateOf(if (canRender) StudioStatus.STARTING else StudioStatus.UNAVAILABLE) }
    var pictureShown by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    val onPicture = remember { { frame: HairFrame -> renderer.submit(frame); requestRender(); pictureShown = true } }

    LaunchedEffect(Unit) {
        if (!fromPhoto && !cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize().background(ImageWellDark)) {
        if (surface != null) {
            HairSurface(surface, renderer)
            when {
                photoUri != null -> PhotoSource(photoUri, onPicture) { status = it }
                cameraPermission.status.isGranted -> CameraSource(onPicture) { status = it }
            }
            DyeDriver(renderer, ui, comparing, requestRender)
        }

        // Hides the empty surface until the first picture is on it, then lifts.
        val cover by animateFloatAsState(if (pictureShown) 0f else 1f, tween(150), label = "first picture")
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = cover }.background(ImageWellDark))

        if (!fromPhoto && !cameraPermission.status.isGranted) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = OnShell, modifier = Modifier.size(48.dp))
                Text(
                    "Camera access is needed to show a color on your hair.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnShell,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Button(
                    onClick = { cameraPermission.launchPermissionRequest() },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text("Grant camera permission") }
            }
        }

        val flash = remember { Animatable(0f) }
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayIconButton(icon = Icons.Filled.Close, contentDescription = "Close hair color", onClick = onClose)
                Spacer(modifier = Modifier.weight(1f))
                CompareButton(enabled = ui.shade != null, comparing = comparing, onComparing = { comparing = it })
                OverlayButton(
                    text = if (ui.saving) "Saving…" else "Save",
                    icon = Icons.Filled.FavoriteBorder,
                    onClick = {
                        val shade = ui.shade
                        if (shade == null || status == StudioStatus.STARTING) {
                            viewModel.say(StudioMessage.PICK_SHADE)
                        } else if (!ui.saving) {
                            scope.launch {
                                flash.snapTo(0.6f)
                                flash.animateTo(0f, tween(180))
                            }
                            // Called back on the GL thread with the dyed picture, uncropped.
                            renderer.capture { picture ->
                                if (picture == null) viewModel.say(StudioMessage.FAILED)
                                else viewModel.saveLook(context, picture, shade, fromPhoto)
                            }
                            requestRender()
                        }
                    }
                )
            }
            StudioSign(status, fromPhoto)
            AnimatedVisibility(
                visible = comparing,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "BEFORE",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnShell,
                    modifier = Modifier.background(CameraScrim, MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        val snackbars = remember { SnackbarHostState() }
        LaunchedEffect(ui.message) {
            val message = ui.message ?: return@LaunchedEffect
            viewModel.messageShown()
            val result = snackbars.showSnackbar(
                message = message.text,
                actionLabel = if (message == StudioMessage.SAVED_TO_LOOKS) "View" else null
            )
            if (result == SnackbarResult.ActionPerformed) onViewLooks()
        }

        val plateShown = remember { MutableTransitionState(false).apply { targetState = true } }
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            SnackbarHost(hostState = snackbars)
            AnimatedVisibility(
                visibleState = plateShown,
                enter = slideInVertically(tween(280, easing = EaseOutStrong)) { it } + fadeIn(tween(200))
            ) {
                ShadePlate(
                    ui = ui,
                    ready = status == StudioStatus.READY || status == StudioStatus.LOW_LIGHT,
                    hint = if (status == StudioStatus.STARTING) "Finding your hair…" else null,
                    onFamily = viewModel::selectFamily,
                    onShade = viewModel::selectShade,
                    onIntensity = viewModel::setIntensity,
                    onTitleLongPress = {
                        if (BuildConfig.DEBUG) {
                            renderer.showMatte = !renderer.showMatte
                            requestRender()
                        }
                    }
                )
            }
        }
    }
}

/** The GL view the dyed picture is drawn on; paused and resumed with the screen. */
@Composable
private fun HairSurface(surface: GLSurfaceView, renderer: HairColorRenderer) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { surface })
    DisposableEffect(lifecycleOwner, surface) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surface.onResume()
                Lifecycle.Event.ON_PAUSE -> surface.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderer.release()
        }
    }
}

/**
 * Front camera → hair segmenter → [onPicture], one frame at a time: a frame is only handed on
 * together with the mask computed from it.
 *
 * The camera is bound once this screen is resumed, i.e. after the screen it came from has been
 * disposed. Face Scan's camera releases with `unbindAll()` on dispose, which would otherwise take
 * this screen's camera with it.
 */
@Composable
private fun CameraSource(onPicture: (HairFrame) -> Unit, onStatus: (StudioStatus) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        val inFlight = AtomicReference<Bitmap?>()
        val tracker = HairTracker(patience = 4, onStatus = onStatus)
        var restart = true
        val segmenter = LiveHairSegmenter(context.applicationContext, softConfidence = true) { mask ->
            val bitmap = inFlight.getAndSet(null) ?: return@LiveHairSegmenter
            onPicture(tracker.frame(bitmap, mask, restart))
            restart = false
        }
        if (!segmenter.isReady) onStatus(StudioStatus.UNAVAILABLE)

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(executor) { proxy ->
            try {
                val raw = proxy.toBitmap()
                val upright = rotateAndMirror(raw, proxy.imageInfo.rotationDegrees, mirror = true)
                if (upright !== raw) raw.recycle()
                when {
                    // No detector on this device: still show the camera, just undyed.
                    !segmenter.isReady -> onPicture(HairFrame(upright, null, 0, 0, HairStats.Default))
                    !inFlight.compareAndSet(null, upright) -> upright.recycle()
                    !segmenter.detect(upright) -> {
                        inFlight.set(null)
                        upright.recycle()
                    }
                }
            } finally {
                proxy.close()
            }
        }

        var disposed = false
        var provider: ProcessCameraProvider? = null
        var bound = false
        fun bind(attempt: Int) {
            val cameraProvider = provider ?: return
            if (disposed || bound || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                bound = true
            }.onFailure {
                Log.w(TAG, "Camera bind failed (attempt $attempt): ${it.message}")
                // The previous screen may still be letting go of the camera.
                if (attempt < 3) main.postDelayed({ bind(attempt + 1) }, 200L)
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = runCatching { future.get() }.getOrNull()
            bind(0)
        }, ContextCompat.getMainExecutor(context))
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) bind(0) }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            main.removeCallbacksAndMessages(null)
            // Only this screen's use case: the screen being returned to may already have bound its own.
            runCatching { provider?.unbind(analysis) }
            analysis.clearAnalyzer()
            runCatching { segmenter.close() }
            executor.shutdown()
        }
    }
}

/** A still picture, segmented once. */
@Composable
private fun PhotoSource(uri: Uri, onPicture: (HairFrame) -> Unit, onStatus: (StudioStatus) -> Unit) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(uri) {
        val frame = withContext(Dispatchers.Default) {
            runCatching {
                val bitmap = ScanBitmapLoader.fromUri(context, uri).fitWithin(MAX_PHOTO_SIDE)
                val mask = StillImageHairSegmenter(context, softConfidence = true).use { it.segment(bitmap) }
                HairTracker(patience = 1, onStatus = onStatus).frame(bitmap, mask, restart = true)
            }.onFailure { Log.w(TAG, "Couldn't prepare photo", it) }.getOrNull()
        }
        if (frame == null) onStatus(StudioStatus.UNAVAILABLE) else onPicture(frame)
    }
}

/**
 * Turns a picture and its mask into a [HairFrame]: measures the hair's lightness (smoothed, so the
 * dye doesn't pump as the light changes) and reports whether there is hair to color at all.
 *
 * @param patience how many measurements in a row may find no hair before saying so. A video frame
 *   can miss; a photo gets one look.
 */
private class HairTracker(private val patience: Int, private val onStatus: (StudioStatus) -> Unit) {
    private var stats = HairStats.Default
    private var measured = false
    private var frames = 0
    private var misses = 0

    fun frame(bitmap: Bitmap, mask: HairMask?, restart: Boolean): HairFrame {
        if (mask == null) {
            onStatus(StudioStatus.UNAVAILABLE)
            return HairFrame(bitmap, null, 0, 0, stats, restart)
        }
        val alpha = mask.hairConfidence?.takeIf { it.size == mask.pixelCount }
            ?: ByteArray(mask.pixelCount) { if (mask.isHair(it)) 255.toByte() else 0 }
        if (frames++ % MEASURE_EVERY == 0) {
            val now = HairColorMath.hairStats(alpha, mask.width, mask.height, stride = max(1, mask.width / 96)) { x, y ->
                bitmap.getPixel(x * bitmap.width / mask.width, y * bitmap.height / mask.height)
            }
            if (now.coverage < MIN_COVERAGE) {
                misses++
            } else {
                misses = 0
                stats = if (measured) stats.towards(now, 0.25f) else now
                measured = true
            }
            onStatus(
                when {
                    misses >= patience -> StudioStatus.NO_HAIR
                    !measured -> StudioStatus.STARTING
                    bitmap.isDim() -> StudioStatus.LOW_LIGHT
                    else -> StudioStatus.READY
                }
            )
        }
        return HairFrame(bitmap, alpha, mask.width, mask.height, stats, restart)
    }

    private fun HairStats.towards(other: HairStats, t: Float) = HairStats(
        meanL + (other.meanL - meanL) * t,
        stdL + (other.stdL - stdL) * t,
        other.coverage
    )

    /** Average brightness over a coarse grid; dim rooms make every shade look muddy. */
    private fun Bitmap.isDim(): Boolean {
        var sum = 0f
        for (gy in 1..6) for (gx in 1..6) {
            val p = getPixel(gx * width / 7, gy * height / 7)
            sum += (0.299f * (p shr 16 and 0xFF) + 0.587f * (p shr 8 and 0xFF) + 0.114f * (p and 0xFF)) / 255f
        }
        return sum / 36f < 0.16f
    }

    private companion object {
        const val MEASURE_EVERY = 3
        const val MIN_COVERAGE = 0.01f
    }
}

/**
 * Moves the renderer's dye toward what the UI asks for. A new shade washes in from the old one
 * rather than snapping, and every change can be interrupted by the next tap.
 */
@Composable
private fun DyeDriver(renderer: HairColorRenderer, ui: ColorStudioUiState, comparing: Boolean, requestRender: () -> Unit) {
    val on = remember { Animatable(0f) }
    val compare = remember { Animatable(1f) }
    val intensity = remember { floatArrayOf(ui.intensity) }
    val push = remember {
        {
            renderer.amount = intensity[0] * on.value * compare.value
            requestRender()
        }
    }
    val spec = remember { tween<Float>(220, easing = EaseOutStrong) }

    LaunchedEffect(ui.dyeShade) {
        val to = ui.dyeShade.let { floatArrayOf(it.l, it.a, it.b, it.contrastGain, it.maxLift) }
        // Coming from the user's own color there is no old shade to blend from.
        val from = if (on.value == 0f) to else renderer.shade
        animate(0f, 1f, animationSpec = spec) { t, _ ->
            renderer.shade = FloatArray(to.size) { from[it] + (to[it] - from[it]) * t }
            requestRender()
        }
    }
    LaunchedEffect(ui.shade != null) {
        on.animateTo(if (ui.shade != null) 1f else 0f, spec) { push() }
    }
    LaunchedEffect(comparing) {
        // Quick to show the before, a touch slower to bring the color back.
        compare.animateTo(if (comparing) 0f else 1f, tween(if (comparing) 120 else 200, easing = EaseOutStrong)) { push() }
    }
    LaunchedEffect(ui.intensity) {
        intensity[0] = ui.intensity
        push()
    }
}

/**
 * Press and hold to see the hair without the color. For TalkBack, which can't hold, activating it
 * switches the before view on and off instead.
 */
@Composable
private fun CompareButton(enabled: Boolean, comparing: Boolean, onComparing: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = ButtonDefaults.MinHeight)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(onPress = {
                        onComparing(true)
                        tryAwaitRelease()
                        onComparing(false)
                    })
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (enabled) "Compare with your own color" else "Compare, pick a shade first"
                stateDescription = if (comparing) "Showing your own color" else "Showing the shade"
                if (enabled) onClick { onComparing(!comparing); true } else disabled()
            },
        shape = ButtonDefaults.outlinedShape,
        color = CameraScrim,
        contentColor = OnShell.copy(alpha = if (enabled) 1f else 0.45f),
        border = BorderStroke(1.5.dp, OnShell.copy(alpha = 0.35f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(ButtonDefaults.ContentPadding)) {
            Text("Compare", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** What's stopping the color from showing, and what to do about it. Nothing while all is well. */
@Composable
private fun StudioSign(status: StudioStatus, fromPhoto: Boolean) {
    val (title, subtitle) = when (status) {
        StudioStatus.NO_HAIR -> "Can't find your hair" to
            if (fromPhoto) "Try a photo that shows your hair clearly" else "Face the camera in good light"
        StudioStatus.LOW_LIGHT -> "It's dark in here" to "Shades look truer in brighter light"
        StudioStatus.UNAVAILABLE -> "Hair color can't run on this phone" to "The hair detector didn't start"
        StudioStatus.STARTING, StudioStatus.READY -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CameraScrim, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).border(2.dp, WarnAmberDark, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = WarnAmberDark, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = WarnAmberDark)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnShellMuted)
        }
    }
}

private fun Context.supportsGlEs3(): Boolean =
    (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion >= 0x30000

private fun Bitmap.fitWithin(maxSide: Int): Bitmap {
    val longest = max(width, height)
    if (longest <= maxSide) return this
    val scale = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true).also { recycle() }
}

private const val TAG = "ColorStudio"

/** Keeps a 12-megapixel photo from becoming a 48 MB texture; plenty for a phone screen. */
private const val MAX_PHOTO_SIDE = 1600
