package com.hairconsultant.app.ui.facescan

import android.graphics.PixelFormat
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.View as FilamentView
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position

/**
 * SceneView extends [SurfaceView] + Filament. Transparent overlay only works when:
 * 1. Camera preview is also a [SurfaceView] (PreviewView PERFORMANCE mode).
 * 2. Filament clears with alpha=0 and [FilamentView.BlendMode.TRANSLUCENT].
 * 3. [SurfaceHolder] uses [PixelFormat.TRANSLUCENT] and [setZOrderMediaOverlay].
 *
 * SurfaceView-over-TextureView (PreviewView COMPATIBLE) always shows black behind GL content.
 */
object SceneViewTransparency {
    private const val TAG = "SceneViewTransparency"

    fun configure(sceneView: SceneView, cameraZ: Float = 1f) {
        sceneView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val surfaceView = sceneView as SurfaceView
        // Overlay above camera SurfaceView; do NOT use setZOrderOnTop(true) — that punches a
        // solid hole through the whole window and hides PreviewView on many devices.
        surfaceView.setZOrderOnTop(false)
        surfaceView.setZOrderMediaOverlay(true)
        surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                holder.setFormat(PixelFormat.TRANSLUCENT)
                applyFilamentTransparency(sceneView, cameraZ)
                Log.d(TAG, "surfaceCreated — translucency applied")
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                holder.setFormat(PixelFormat.TRANSLUCENT)
                applyFilamentTransparency(sceneView, cameraZ)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })

        applyFilamentTransparency(sceneView, cameraZ)
    }

    private fun applyFilamentTransparency(sceneView: SceneView, cameraZ: Float) {
        runCatching {
            sceneView.uiHelper.isOpaque = false
            sceneView.view.blendMode = FilamentView.BlendMode.TRANSLUCENT
            sceneView.view.setFrontFaceWindingInverted(true)
            sceneView.scene.skybox = null
            sceneView.indirectLight = sceneView.environment.indirectLight
            sceneView.mainLightNode?.apply {
                intensity = 100_000f
                isShadowCaster = false
            }
            sceneView.renderer.clearOptions = sceneView.renderer.clearOptions.apply {
                clear = true
                clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            }
            sceneView.cameraNode.position = Position(0f, 0f, cameraZ)
        }.onFailure { e ->
            Log.w(TAG, "Filament transparency setup failed", e)
        }
    }
}
