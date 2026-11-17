package com.hairconsultant.app.data.local

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The camera frame of each account's latest successful Face Scan, kept on the device so Image
 * Upload can offer it as a photo. One folder per account, so nobody else signing in on the phone
 * sees it.
 */
class ScanPhotoStore(private val dir: File) {
    private val saves = MutableStateFlow(0)

    /** The newest scan photo for [uid], read again after every [save]. */
    fun observe(uid: String): Flow<File?> = saves.map { latest(uid) }.flowOn(Dispatchers.IO)

    fun latest(uid: String): File? = photos(uid).maxByOrNull(::takenAt)

    suspend fun save(uid: String, frame: Bitmap) {
        withContext(Dispatchers.IO) {
            nextFile(uid).outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        }
        saves.update { it + 1 }
    }

    /**
     * A new file per scan, so Coil and Image Upload never take a new scan for the old one. Older
     * scans are deleted except the newest, which Image Upload may still be showing.
     */
    internal fun nextFile(uid: String): File {
        photos(uid).sortedBy(::takenAt).dropLast(1).forEach(File::delete)
        return File(File(dir, uid).apply { mkdirs() }, "${System.currentTimeMillis()}.jpg")
    }

    private fun photos(uid: String): List<File> =
        File(dir, uid).listFiles { file -> file.extension == "jpg" }.orEmpty().toList()

    private fun takenAt(file: File): Long = file.nameWithoutExtension.toLongOrNull() ?: 0L

    private companion object {
        const val JPEG_QUALITY = 92
    }
}
