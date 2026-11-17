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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hairconsultant.app.HairConsultantApp
import com.hairconsultant.app.R
import com.hairconsultant.app.domain.model.arFilterFavoriteId
import kotlinx.coroutines.launch

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

        effectAssetPath = intent.getStringExtra(EXTRA_EFFECT_ASSET).orEmpty()
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
            ?: effectAssetPath.substringAfterLast('/').removeSuffix(".deepar").ifBlank { "DeepAR" }
        if (effectAssetPath.isBlank()) {
            finish()
            return
        }

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

        addFavoriteButton(root, density, pad)

        setContentView(root)

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> startSession()
            else -> cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /** The heart that keeps this filter in Profile favorites, mirroring the close button. Absent when signed out or launched without a catalog style. */
    private fun addFavoriteButton(root: FrameLayout, density: Float, pad: Int) {
        val haircutId = intent.getStringExtra(EXTRA_HAIRCUT_ID) ?: return
        val container = (application as HairConsultantApp).container
        val uid = container.authRepository.currentUser.value?.uid ?: return
        val repository = container.consultationRepository
        val favoriteId = arFilterFavoriteId(uid, haircutId)
        var saved = false

        val heart = android.widget.ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (48 * density).toInt(),
                (48 * density).toInt()
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(0, pad, pad, 0)
            }
            setBackgroundColor(0xCCFFFFFF.toInt())
            setOnClickListener {
                val remove = saved
                lifecycleScope.launch {
                    if (remove) repository.removeSavedLook(favoriteId) else repository.saveArFilter(uid, haircutId)
                    Toast.makeText(
                        this@DeepArCurtainsActivity,
                        if (remove) "Removed from favorites" else "Saved to your Profile favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        root.addView(heart)

        // Read from storage, so the heart also reflects a filter removed in Profile.
        lifecycleScope.launch {
            repository.observeFavorites(uid).collect { favorites ->
                saved = favorites.any { it.id == favoriteId }
                heart.setImageResource(if (saved) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
                heart.contentDescription =
                    if (saved) "Remove $displayName from favorites" else "Save $displayName to favorites"
            }
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
        const val EXTRA_HAIRCUT_ID = "extra_haircut_id"

        /** [haircutId] is the catalog style being tried on; with it the screen offers the favorite heart. */
        fun intent(
            context: Context,
            effectAssetPath: String,
            displayName: String,
            haircutId: String? = null
        ): Intent = Intent(context, DeepArCurtainsActivity::class.java).apply {
            putExtra(EXTRA_EFFECT_ASSET, effectAssetPath)
            putExtra(EXTRA_DISPLAY_NAME, displayName)
            putExtra(EXTRA_HAIRCUT_ID, haircutId)
        }
    }
}
