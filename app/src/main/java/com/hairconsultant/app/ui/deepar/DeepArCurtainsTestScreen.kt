package com.hairconsultant.app.ui.deepar

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.ui.facescan.HairstyleArCatalog

/**
 * Launches [DeepArCurtainsActivity] for a dashboard-uploaded DeepAR effect.
 * DeepAR does not initialize reliably inside Compose Dialogs / SurfaceView holes.
 */
@Composable
fun DeepArCurtainsTestScreen(
    onClose: () -> Unit,
    haircut: Haircut? = null,
    effectAssetPath: String? = null,
    displayName: String = haircut?.name
        ?: effectAssetPath?.substringAfterLast('/')?.removeSuffix(".deepar")
        ?: "DeepAR"
) {
    val context = LocalContext.current
    LaunchedEffect(haircut?.id, effectAssetPath, displayName) {
        val effect = when {
            !effectAssetPath.isNullOrBlank() -> effectAssetPath
            haircut != null -> HairstyleArCatalog.resolveDeepArEffect(context, haircut)
            else -> null
        }
        if (effect != null) {
            context.startActivity(
                DeepArCurtainsActivity.intent(context, effect, displayName)
            )
        } else {
            Toast.makeText(
                context,
                "Couldn't download this try-on — check your connection",
                Toast.LENGTH_SHORT
            ).show()
        }
        onClose()
    }
}
