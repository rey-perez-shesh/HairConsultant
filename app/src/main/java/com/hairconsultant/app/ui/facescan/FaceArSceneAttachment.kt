package com.hairconsultant.app.ui.facescan

import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.sceneview.SceneView

/**
 * Attaches [SceneView] as the top layer in the camera [FrameLayout].
 * CameraX [PreviewView] always stays visible underneath.
 */
class FaceArSceneAttachment {
    private var frameLayout: FrameLayout? = null

    var sceneView: SceneView? = null
        private set

    fun bindFrameLayout(root: FrameLayout) {
        frameLayout = root
        sceneView?.let { attach(it) }
    }

    fun setSceneView(view: SceneView?) {
        sceneView?.let { detach(it) }
        sceneView = view
        view?.let { attach(it) }
    }

    private fun attach(view: SceneView) {
        val root = frameLayout ?: return
        if (view.parent === root) return
        (view.parent as? ViewGroup)?.removeView(view)
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        SceneViewTransparency.configure(view)
    }

    private fun detach(view: SceneView) {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
