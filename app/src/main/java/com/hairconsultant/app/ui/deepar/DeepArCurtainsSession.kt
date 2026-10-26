package com.hairconsultant.app.ui.deepar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import ai.deepar.ar.ARErrorType
import ai.deepar.ar.AREventListener
import ai.deepar.ar.CameraResolutionPreset
import ai.deepar.ar.DeepAR
import ai.deepar.ar.DeepARImageFormat
import com.hairconsultant.app.BuildConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolated DeepAR session for a catalog hairstyle `.deepar` effect.
 *
 * Matches DeepAR’s official Android quickstart order:
 * 1) Create DeepAR + setLicenseKey + initialize (Activity context)
 * 2) Bind CameraX ImageAnalysis immediately
 * 3) setRenderSurface only from [surfaceChanged] (never before initialize returns)
 * 4) switchEffect in [initialized]
 *
 * Prefer launching via [DeepArCurtainsActivity] — Compose Dialogs break SurfaceView/DeepAR.
 */
class DeepArCurtainsSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    /** Path under `assets/` e.g. `effects/curtains.deepar`. */
    private val effectAssetPath: String,
    private val displayName: String = effectAssetPath.substringAfterLast('/'),
    private val onStatus: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) : AREventListener, SurfaceHolder.Callback {

    private var deepAR: DeepAR? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var buffers: Array<ByteBuffer>? = null
    private var currentBuffer: Int = 0
    private val released = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private var surfaceAttached: SurfaceView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initWatchdog: Runnable? = null

    fun start(surfaceView: SurfaceView) {
        if (released.get()) return
        if (deepAR != null) return

        surfaceAttached = surfaceView
        if (BuildConfig.DEEPAR_LICENSE_KEY.isBlank()) {
            onError("DEEPAR_LICENSE_KEY missing in local.properties")
            return
        }
        if (!effectAssetExists()) {
            onError(if (isDownloadedEffect) "Missing $effectAssetPath" else "Missing assets/$effectAssetPath")
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onError("DeepAR needs an Activity context")
            return
        }

        onStatus("Creating DeepAR engine…")
        Log.i(TAG, "initialize keyLen=${BuildConfig.DEEPAR_LICENSE_KEY.length}")

        val ar = DeepAR(activity)
        deepAR = ar
        ar.setLicenseKey(BuildConfig.DEEPAR_LICENSE_KEY)
        ar.initialize(activity, this)
        onStatus("DeepAR initializing…")

        // Official quickstart binds camera right after initialize (before initialized()).
        setupCamera()

        surfaceView.holder.addCallback(this)
        // Force surfaceChanged — DeepAR quickstart does GONE → VISIBLE for this reason.
        surfaceView.visibility = View.GONE
        surfaceView.visibility = View.VISIBLE

        initWatchdog = Runnable {
            if (!released.get() && !initialized.get()) {
                onError(
                    "DeepAR still initializing. Confirm license App ID is " +
                        "com.hairconsultant.app, uninstall/reinstall after key change, " +
                        "and check Logcat tag DeepArCurtains."
                )
            }
        }
        mainHandler.postDelayed(initWatchdog!!, 12_000L)
    }

    /**
     * Free CameraX immediately so Face Scan can reopen the camera without waiting
     * for the heavier DeepAR engine teardown.
     */
    fun releaseCamera() {
        runCatching { cameraProvider?.unbindAll() }
            .onFailure { Log.w(TAG, "Camera unbind failed: ${it.message}") }
        cameraProvider = null
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        buffers = null
        Log.i(TAG, "DeepAR camera freed")
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        initWatchdog?.let { mainHandler.removeCallbacks(it) }
        initWatchdog = null

        // Camera first — Face Scan needs it as soon as this Activity finishes.
        releaseCamera()

        surfaceAttached?.holder?.removeCallback(this)
        surfaceAttached = null

        val ar = deepAR
        deepAR = null
        if (ar != null) {
            runCatching { ar.setRenderSurface(null, 0, 0) }
            runCatching { ar.switchEffect("effect", null as String?) }
            runCatching { ar.setAREventListener(null) }
            runCatching { ar.release() }
                .onFailure { Log.w(TAG, "DeepAR release failed: ${it.message}") }
        }
        initialized.set(false)
        onStatus("DeepAR released")
        Log.i(TAG, "DeepAR curtains session released")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated valid=${holder.surface.isValid}")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged ${width}x$height")
        if (width <= 0 || height <= 0) return
        val ar = deepAR ?: return
        runCatching {
            ar.setRenderSurface(holder.surface, width, height)
            Log.i(TAG, "Render surface set ${width}x$height")
            if (!initialized.get()) onStatus("Surface ready — waiting for DeepAR…")
        }.onFailure {
            Log.e(TAG, "setRenderSurface failed: ${it.message}")
            onError("Render surface failed: ${it.message}")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        deepAR?.setRenderSurface(null, 0, 0)
    }

    override fun initialized() {
        if (released.get()) return
        initialized.set(true)
        initWatchdog?.let { mainHandler.removeCallbacks(it) }
        deepAR?.switchEffect("effect", effectUri)
        onStatus("$displayName loaded — move your head")
        Log.i(TAG, "DeepAR initialized; effect=$effectUri")
    }

    override fun error(errorType: ARErrorType?, message: String?) {
        val text = message ?: errorType?.name ?: "Unknown DeepAR error"
        Log.e(TAG, "DeepAR error type=$errorType msg=$text")
        onError(text)
    }

    override fun effectSwitched(slot: String?) {
        Log.i(TAG, "Effect switched: $slot")
        if (!released.get()) onStatus("Effect live — move your head")
    }

    override fun faceVisibilityChanged(faceVisible: Boolean) {
        Log.d(TAG, "faceVisible=$faceVisible")
    }

    override fun imageVisibilityChanged(gameObjectName: String?, imageVisible: Boolean) = Unit
    override fun frameAvailable(image: Image?) = Unit
    override fun shutdownFinished() = Unit
    override fun screenshotTaken(bitmap: Bitmap?) = Unit
    override fun videoRecordingStarted() = Unit
    override fun videoRecordingFinished() = Unit
    override fun videoRecordingFailed() = Unit
    override fun videoRecordingPrepared() = Unit

    private fun setupCamera() {
        if (released.get()) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (released.get()) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                bindAnalysis(provider)
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider failed", e)
                onError("Camera failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindAnalysis(provider: ProcessCameraProvider) {
        if (released.get()) return
        val preset = CameraResolutionPreset.P1280x720
        val portrait = isPortrait()
        val width = if (portrait) preset.height else preset.width
        val height = if (portrait) preset.width else preset.height

        ensureBuffers(width * height * 4)

        // Official quickstart feeds frames on the main executor.
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetResolution(Size(width, height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), ::analyzeFrame)

        val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, imageAnalysis)
        Log.i(TAG, "CameraX ImageAnalysis bound ${width}x$height")
        onStatus("Camera bound — waiting for DeepAR…")
    }

    private fun analyzeFrame(image: ImageProxy) {
        try {
            if (released.get()) return
            val ar = deepAR ?: return
            // Official quickstart feeds frames before initialized(); DeepAR ignores until ready.
            val plane = image.planes[0]
            val src = plane.buffer
            src.rewind()
            val needed = src.remaining().coerceAtLeast(image.width * image.height * 4)
            ensureBuffers(needed)
            val bufs = buffers ?: return
            val dest = bufs[currentBuffer]
            dest.clear()
            if (dest.capacity() < src.remaining()) {
                Log.w(TAG, "Buffer too small dest=${dest.capacity()} src=${src.remaining()}")
                return
            }
            dest.put(src)
            dest.position(0)
            ar.receiveFrame(
                dest,
                image.width,
                image.height,
                image.imageInfo.rotationDegrees,
                /* mirror */ true,
                DeepARImageFormat.RGBA_8888,
                plane.pixelStride
            )
            currentBuffer = (currentBuffer + 1) % BUFFER_COUNT
        } catch (e: Exception) {
            // Expected briefly before initialized() on some SDK builds.
            if (initialized.get()) Log.w(TAG, "Frame feed failed: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun ensureBuffers(minCapacity: Int) {
        val existing = buffers
        if (existing != null && existing[0].capacity() >= minCapacity) return
        buffers = Array(BUFFER_COUNT) {
            ByteBuffer.allocateDirect(minCapacity).apply {
                order(ByteOrder.nativeOrder())
                position(0)
            }
        }
        currentBuffer = 0
    }

    private fun isPortrait(): Boolean {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val rotation = wm.defaultDisplay.rotation
        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
    }

    /** Dashboard-uploaded effects arrive as absolute paths into ArAssetCache, not `assets/` paths. */
    private val isDownloadedEffect: Boolean
        get() = effectAssetPath.startsWith("/")

    private fun effectAssetExists(): Boolean =
        if (isDownloadedEffect) {
            File(effectAssetPath).isFile
        } else {
            runCatching {
                context.assets.open(effectAssetPath).use { true }
            }.getOrDefault(false)
        }

    private val effectUri: String
        get() = if (isDownloadedEffect) effectAssetPath else "file:///android_asset/$effectAssetPath"

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    companion object {
        private const val TAG = "DeepArCurtains"
        private const val BUFFER_COUNT = 2
        const val DEFAULT_EFFECT_ASSET_PATH = "effects/curtains.deepar"
    }
}
