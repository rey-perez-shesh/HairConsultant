package com.hairconsultant.app.ui.deepar

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import ai.deepar.ar.DeepAR
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX [Preview.SurfaceProvider] that feeds frames into DeepAR via an external GL texture.
 * Adapted from DeepAR's official Android quickstart (isolated curtains test only).
 */
class DeepArSurfaceProvider(
    private val context: Context,
    private val deepAR: DeepAR
) : Preview.SurfaceProvider {

    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var nativeGLTextureHandle: Int = 0
    private var orientation: Int = 0
    private var mirror: Boolean = true
    private val notifyDeepAr = AtomicBoolean(true)
    private val stopped = AtomicBoolean(false)

    fun setMirror(mirror: Boolean) {
        this.mirror = mirror
        if (surfaceTexture == null || surface == null) return
        // Pause briefly when flipping cameras so DeepAR doesn't mirror the wrong stream.
        notifyDeepAr.set(false)
        Timer().schedule(object : TimerTask() {
            override fun run() {
                notifyDeepAr.set(true)
            }
        }, 1000)
    }

    fun stop() {
        stopped.set(true)
        runCatching { surface?.release() }
        runCatching { surfaceTexture?.release() }
        surface = null
        surfaceTexture = null
        nativeGLTextureHandle = 0
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        if (stopped.get()) {
            request.willNotProvideSurface()
            return
        }
        Log.d(TAG, "Surface requested ${request.resolution}")

        if (nativeGLTextureHandle == 0) {
            nativeGLTextureHandle = deepAR.externalGlTexture
            Log.d(TAG, "external GL texture=$nativeGLTextureHandle")
        }
        if (nativeGLTextureHandle == 0) {
            Log.e(TAG, "DeepAR external GL texture unavailable")
            request.willNotProvideSurface()
            return
        }

        val resolution = request.resolution
        var texture = surfaceTexture
        if (texture == null) {
            texture = SurfaceTexture(nativeGLTextureHandle).also { surfaceTexture = it }
            texture.setOnFrameAvailableListener {
                if (stopped.get()) return@setOnFrameAvailableListener
                runCatching {
                    texture.updateTexImage()
                    if (notifyDeepAr.get()) {
                        deepAR.receiveFrameExternalTexture(
                            resolution.width,
                            resolution.height,
                            orientation,
                            mirror,
                            nativeGLTextureHandle
                        )
                    }
                }.onFailure { Log.w(TAG, "External texture frame failed: ${it.message}") }
            }
        }
        texture.setDefaultBufferSize(resolution.width, resolution.height)

        if (surface == null) {
            surface = Surface(texture)
        }

        request.setTransformationInfoListener(ContextCompat.getMainExecutor(context)) { info ->
            orientation = info.rotationDegrees
        }

        val provided = surface ?: run {
            request.willNotProvideSurface()
            return
        }
        request.provideSurface(provided, ContextCompat.getMainExecutor(context)) { result ->
            Log.i(TAG, "Surface result=${result.resultCode}")
        }
    }

    companion object {
        private const val TAG = "DeepArSurfaceProvider"
    }
}
