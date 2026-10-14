package com.hairconsultant.app.ui.deepar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Full-screen DeepAR Activity for catalog hairstyle `.deepar` effects.
 *
 * DeepAR’s Android SDK expects a real Activity + SurfaceView (not a Compose Dialog).
 * Pass [EXTRA_EFFECT_ASSET] (e.g. `effects/french_crop.deepar`) and optional [EXTRA_DISPLAY_NAME].
 */
class DeepArCurtainsActivity : ComponentActivity() {

    private var session: DeepArCurtainsSession? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusView: TextView
    private lateinit var effectAssetPath: String
    private lateinit var displayName: String

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSession() else {
            statusView.text = "Camera permission is required for DeepAR."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        effectAssetPath = intent.getStringExtra(EXTRA_EFFECT_ASSET)
            ?: DeepArCurtainsSession.DEFAULT_EFFECT_ASSET_PATH
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
            ?: effectAssetPath.substringAfterLast('/').removeSuffix(".deepar")

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(surfaceView)

        statusView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
            }
            setBackgroundColor(0xE6111820.toInt())
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding(pad, pad, pad, pad)
            text = "DeepAR — $displayName\nStarting…"
        }
        root.addView(statusView)

        val close = android.widget.ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (48 * density).toInt(),
                (48 * density).toInt()
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                setMargins(pad, pad, 0, 0)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0xCCFFFFFF.toInt())
            setOnClickListener {
                // Free camera before finish so Face Scan can rebind without lag/blackout.
                setResult(RESULT_OK)
                releaseSession()
                finish()
            }
            contentDescription = getString(android.R.string.cancel)
        }
        root.addView(close)

        setContentView(root)

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> startSession()
            else -> cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startSession() {
        if (session != null) return
        val s = DeepArCurtainsSession(
            context = this,
            lifecycleOwner = this,
            effectAssetPath = effectAssetPath,
            displayName = displayName,
            onStatus = { msg -> runOnUiThread { statusView.text = "DeepAR — $displayName\n$msg" } },
            onError = { msg ->
                runOnUiThread {
                    statusView.text = "DeepAR — $displayName\nERROR: $msg"
                    Log.e(TAG, msg)
                }
            }
        )
        session = s
        surfaceView.post { s.start(surfaceView) }
    }

    override fun onPause() {
        // Free the camera as early as possible when leaving DeepAR.
        session?.releaseCamera()
        super.onPause()
    }

    override fun onStop() {
        releaseSession()
        super.onStop()
    }

    override fun onDestroy() {
        releaseSession()
        super.onDestroy()
    }

    private fun releaseSession() {
        session?.release()
        session = null
    }

    companion object {
        private const val TAG = "DeepArCurtainsActivity"
        const val EXTRA_EFFECT_ASSET = "extra_effect_asset"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"

        fun intent(
            context: Context,
            effectAssetPath: String,
            displayName: String
        ): Intent = Intent(context, DeepArCurtainsActivity::class.java).apply {
            putExtra(EXTRA_EFFECT_ASSET, effectAssetPath)
            putExtra(EXTRA_DISPLAY_NAME, displayName)
        }
    }
}
