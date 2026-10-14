package com.hairconsultant.app.ui.deepar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.ui.facescan.HairstyleArCatalog

/**
 * Launches the isolated [DeepArCurtainsActivity] for a catalog DeepAR effect.
 * DeepAR does not initialize reliably inside Compose Dialogs / SurfaceView holes.
 */
@Composable
fun DeepArCurtainsTestScreen(
    onClose: () -> Unit,
    haircut: Haircut? = null,
    effectAssetPath: String = haircut?.let { HairstyleArCatalog.deepArEffectPath(it) }
        ?: DeepArCurtainsSession.DEFAULT_EFFECT_ASSET_PATH,
    displayName: String = haircut?.name
        ?: effectAssetPath.substringAfterLast('/').removeSuffix(".deepar")
) {
    val context = LocalContext.current
    LaunchedEffect(effectAssetPath, displayName) {
        context.startActivity(
            DeepArCurtainsActivity.intent(context, effectAssetPath, displayName)
        )
        onClose()
    }
}
