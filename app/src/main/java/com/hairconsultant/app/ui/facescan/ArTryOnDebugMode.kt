package com.hairconsultant.app.ui.facescan

/**
 * Master AR debug switch for the hairstyle try-on preview.
 *
 * false — clean portrait preview (no landmarks, crown guides, or calibration HUD)
 * true  — show landmarks, crown anchor, tracking numbers, calibration guides
 *
 * Does not disable tracking or change hairstyle transforms.
 */
const val DEBUG_AR: Boolean = false

/**
 * Temporary AR try-on debug modes (used when diagnosing hair cover / occlusion).
 *
 * NORMAL — production-like: reconstructed hair cover + depth face occlusion + GLB
 * MASK — magenta hair mask only (verify segmentation coverage)
 * REMOVAL — reconstructed cover only (hide GLB) to inspect fill quality
 * FACE_OCCLUSION — show depth occluder as translucent cyan volume
 */
enum class ArTryOnDebugMode {
    NORMAL,
    MASK,
    REMOVAL,
    FACE_OCCLUSION
}

/** Switch this for on-device diagnosis. Keep [ArTryOnDebugMode.NORMAL] for the real try-on look. */
val AR_TRY_ON_DEBUG_MODE: ArTryOnDebugMode = ArTryOnDebugMode.NORMAL
