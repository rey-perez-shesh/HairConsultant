package com.hairconsultant.app.data.analysis

/**
 * Per-pixel hair replacement weight (0 = keep camera, 255 = full soft cover).
 * Built from MediaPipe hair segmentation + face-safe refinement — never a face mask.
 */
data class SoftHairMask(
    val alpha: ByteArray,
    val width: Int,
    val height: Int
) {
    fun alphaAt(index: Int): Int =
        if (index in alpha.indices) alpha[index].toInt() and 0xFF else 0
}
