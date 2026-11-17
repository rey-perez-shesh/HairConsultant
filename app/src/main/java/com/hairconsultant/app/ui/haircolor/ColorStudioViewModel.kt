package com.hairconsultant.app.ui.haircolor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.remote.firebase.MediaStorageRepository
import com.hairconsultant.app.data.repository.ConsultationRepository
import com.hairconsultant.app.domain.model.COLOR_LOOK_ID_PREFIX
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class StudioMessage(val text: String) {
    SAVED_TO_LOOKS("Saved to your looks"),
    SAVED_TO_GALLERY("Saved to your gallery"),
    PICK_SHADE("Pick a shade first"),
    SCAN_FIRST("Scan your face once, then you can save looks"),
    FAILED("Couldn't save this look")
}

data class ColorStudioUiState(
    val family: ShadeFamily = ShadeFamily.NATURAL,
    /** The shade being worn, or null for the user's own color. */
    val shade: HairShade? = null,
    /** The last shade worn: what the hair keeps showing while it fades back to the user's own color. */
    val dyeShade: HairShade = HairShades.all.first(),
    val intensity: Float = 0.85f,
    val saving: Boolean = false,
    val message: StudioMessage? = null
)

class ColorStudioViewModel(
    private val authRepository: AuthRepository,
    private val consultationRepository: ConsultationRepository,
    private val mediaStorageRepository: MediaStorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColorStudioUiState())
    val uiState: StateFlow<ColorStudioUiState> = _uiState.asStateFlow()

    fun selectFamily(family: ShadeFamily) = _uiState.update { it.copy(family = family) }

    fun selectShade(shade: HairShade?) =
        _uiState.update { it.copy(shade = shade, dyeShade = shade ?: it.dyeShade) }

    fun setIntensity(value: Float) = _uiState.update { it.copy(intensity = value.coerceIn(0f, 1f)) }

    fun say(message: StudioMessage) = _uiState.update { it.copy(message = message) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    /** Takes ownership of [picture] (the dyed frame) and recycles it when done. */
    fun saveLook(context: Context, picture: Bitmap, shade: HairShade, fromPhoto: Boolean) {
        if (_uiState.value.saving) {
            picture.recycle()
            return
        }
        val appContext = context.applicationContext
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val message = runCatching { withContext(Dispatchers.IO) { store(appContext, picture, shade, fromPhoto) } }
                .onFailure { Log.w(TAG, "Couldn't save color look", it) }
                .getOrDefault(StudioMessage.FAILED)
            picture.recycle()
            _uiState.update { it.copy(saving = false, message = message) }
        }
    }

    private suspend fun store(context: Context, picture: Bitmap, shade: HairShade, fromPhoto: Boolean): StudioMessage {
        val uid = authRepository.currentUser.value?.uid ?: return StudioMessage.FAILED
        // A look is stored as a consultation, which can't exist without a scan result; the user's
        // latest one describes the same head. Without any, the picture goes to the gallery instead.
        val lastScan = consultationRepository.observeHistory(uid).first().firstOrNull()?.scanResult
        if (lastScan == null) {
            // Below Android 10 the gallery needs a storage permission the app doesn't ask for.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return StudioMessage.SCAN_FIRST
            saveToGallery(context, picture, shade)
            return StudioMessage.SAVED_TO_GALLERY
        }
        val file = File(context.cacheDir, "color_look_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        val local = Uri.fromFile(file)
        val url = runCatching { mediaStorageRepository.uploadTryOnResult(uid, local) }.getOrElse { local.toString() }
        consultationRepository.save(
            Consultation(
                id = "$COLOR_LOOK_ID_PREFIX${shade.id}-${UUID.randomUUID()}",
                userId = uid,
                source = if (fromPhoto) ConsultationSource.IMAGE_UPLOAD else ConsultationSource.FACE_SCAN,
                scanResult = lastScan,
                selectedHaircut = null,
                sourceImageUrl = null,
                resultImageUrl = url,
                isFavorite = true,
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
        return StudioMessage.SAVED_TO_LOOKS
    }

    private fun saveToGallery(context: Context, picture: Bitmap, shade: HairShade) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "HairConsultant_${shade.id}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/HairConsultant")
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Gallery refused the picture")
        resolver.openOutputStream(uri)?.use { picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            ?: error("Couldn't write to the gallery")
    }

    private companion object {
        const val TAG = "ColorStudio"
        const val JPEG_QUALITY = 92
    }
}
