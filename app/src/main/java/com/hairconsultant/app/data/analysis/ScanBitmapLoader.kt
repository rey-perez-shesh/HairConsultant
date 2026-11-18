package com.hairconsultant.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.hypot
import kotlin.math.max

internal object ScanBitmapLoader {
    fun fromUri(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            return ensureArgb(decoded)
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open image")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Could not decode image")
        val rotation = when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        val oriented = rotateAndMirror(bitmap, rotation, mirror = false)
        return if (oriented !== bitmap) {
            bitmap.recycle()
            ensureArgb(oriented)
        } else {
            ensureArgb(bitmap)
        }
    }

    /**
     * The lens's focal length over the photo's long side, from the EXIF 35 mm-equivalent focal
     * length; null when the photo doesn't record one (screenshots, most shared photos).
     */
    fun focalToLongSide(context: Context, uri: Uri, width: Int, height: Int): Float? {
        val equivalent = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
            }
        }.getOrNull() ?: 0
        if (equivalent <= 0 || width <= 0 || height <= 0) return null
        // 35 mm equivalents match the diagonal of a 36x24 mm frame, 43.27 mm. A cropped photo reads
        // too short, so the result is kept within ordinary phone lenses (about 17–50 mm).
        return (equivalent / 43.27f * hypot(width.toFloat(), height.toFloat()) / max(width, height)).coerceIn(0.5f, 1.45f)
    }

    private fun ensureArgb(bitmap: Bitmap): Bitmap =
        if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
}
