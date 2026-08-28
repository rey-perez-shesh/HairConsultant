package com.hairconsultant.app.ui.facescan

import com.hairconsultant.app.domain.model.Haircut

/**
 * Extensible haircut-id → GLB mapping for AR try-on.
 *
 * Local offsets/scale are applied after the shared crown / tracking transform — they do not
 * change HeadPoseEstimator, MediaPipe, or global calibration constants.
 */
object HairstyleArCatalog {

    data class AssetConfig(
        val modelAsset: String,
        /** Extra model-space nudge after automatic crown alignment (usually 0). */
        val localOffsetX: Float = 0f,
        val localOffsetY: Float = 0f,
        val localOffsetZ: Float = 0f,
        /** Multiplier on the existing head-width scale (1 = unchanged global scale math). */
        val localScaleMul: Float = 1f,
        /**
         * Degrees added to head yaw so the mesh front faces the camera.
         * Meshy buzzcut-style exports typically need 180; other exports may need 0.
         */
        val modelYawOffset: Float = 180f,
        /** Fixed mesh correction (degrees) applied with head pitch/yaw/roll — per asset only. */
        val localRotationX: Float = 0f,
        val localRotationY: Float = 0f,
        val localRotationZ: Float = 0f,
        /**
         * When true, shift so AABB maxY sits at the node origin (crown).
         * Needed for Meshy exports centered at (0,0,0); prepared crown-origin GLBs leave this false.
         */
        val alignCrownToMaxY: Boolean = true,
        /**
         * When false, skip the depth face occluder + Compose face punch-through.
         * Short/curtain Meshy shells must not get an artificial face-shaped hole;
         * the live PreviewView face already shows where SceneView has no hair.
         */
        val enableFaceCutout: Boolean = true
    )

    /**
     * Boy Cut — full volume but snug on the scalp (not a floating helmet).
     * Centered Meshy shell: crown-align maxY, then tuck downward.
     */
    private val BOY_CUT_FIT = AssetConfig(
        modelAsset = "wigs/Meshy_AI__buzzcut_texture.glb",
        localScaleMul = 0.64f,
        localOffsetY = -0.18f,
        localOffsetZ = 0.02f,
        modelYawOffset = 180f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /** Stable catalog IDs (SampleData index — name lookup is authoritative for AR mapping). */
    private const val ID_BOY_CUT = "SHORT_STRAIGHT_0"
    private const val ID_CURTAINS = "SHORT_STRAIGHT_1"
    private const val ID_MODERN_POMPADOUR = "SHORT_STRAIGHT_7"
    private const val ID_FRENCH_CROP = "SHORT_STRAIGHT_9"
    private const val ID_LONG_WAVY = "LONG_WAVY_1"
    private const val ID_SHAGGY_WOLFCUT = "LONG_WAVY_7"

    /**
     * Curtains — Meshy curtain GLB; crown-align + tuck; no face cutout.
     * modelYawOffset=0 (this export faces camera without the buzzcut +180° flip).
     */
    private val CURTAINS_FIT = AssetConfig(
        modelAsset = "wigs/Meshy_AI_curtains.glb",
        localScaleMul = 0.68f,
        localOffsetY = -0.08f,
        localOffsetZ = 0.02f,
        modelYawOffset = 0f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /**
     * French Crop — short Meshy crop; Boy Cut scale baseline, raised crown anchor.
     * modelYawOffset=0 (same facing convention as Curtains GLB).
     */
    private val FRENCH_CROP_FIT = AssetConfig(
        modelAsset = "wigs/Meshy_AI_french crop.glb",
        localScaleMul = 0.64f,
        localOffsetY = 0.10f,
        localOffsetZ = 0.02f,
        modelYawOffset = 0f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /**
     * Modern Pompadour — centered Meshy shell (maxY ~0.92); crown-align + slight tuck.
     */
    private val MODERN_POMPADOUR_FIT = AssetConfig(
        modelAsset = "wigs/Meshy_AI_modern_pompadour.glb",
        localScaleMul = 0.66f,
        localOffsetY = -0.06f,
        localOffsetZ = 0.02f,
        modelYawOffset = 0f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /**
     * Long Wavy — tall centered shell (maxY ~0.95); crown-align, modest tuck for scalp contact.
     */
    private val LONG_WAVY_FIT = AssetConfig(
        modelAsset = "wigs/Meshy_AI_long_wavy.glb",
        localScaleMul = 0.88f,
        localOffsetY = -0.03f,
        localOffsetZ = 0.02f,
        modelYawOffset = 0f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /**
     * Shaggy Wolfcut — medium centered shell (maxY ~0.91); independent fit from other styles.
     */
    private val SHAGGY_WOLFCUT_FIT = AssetConfig(
        modelAsset = "wigs/Meshy_AI_shaggy_wolfcut.glb",
        localScaleMul = 0.84f,
        localOffsetY = -0.02f,
        localOffsetZ = 0.02f,
        modelYawOffset = 0f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /** Catalog IDs with a dedicated try-on GLB. */
    val DEDICATED_GLB_IDS: Set<String> = setOf(
        ID_BOY_CUT,
        ID_CURTAINS,
        ID_FRENCH_CROP,
        ID_MODERN_POMPADOUR,
        ID_LONG_WAVY,
        ID_SHAGGY_WOLFCUT
    )

    private val byId: Map<String, AssetConfig> = mapOf(
        ID_BOY_CUT to BOY_CUT_FIT,
        ID_CURTAINS to CURTAINS_FIT,
        ID_FRENCH_CROP to FRENCH_CROP_FIT,
        ID_MODERN_POMPADOUR to MODERN_POMPADOUR_FIT,
        ID_LONG_WAVY to LONG_WAVY_FIT,
        ID_SHAGGY_WOLFCUT to SHAGGY_WOLFCUT_FIT
    )

    /** Name → GLB config (checked before id so stale Firestore ids cannot swap assets). */
    private val byName: Map<String, AssetConfig> = mapOf(
        "boy cut" to BOY_CUT_FIT,
        "curtains" to CURTAINS_FIT,
        "french crop" to FRENCH_CROP_FIT,
        "modern pompadour" to MODERN_POMPADOUR_FIT,
        "long wavy hair" to LONG_WAVY_FIT,
        "shaggy wolfcut" to SHAGGY_WOLFCUT_FIT
    )

    /** True when this card should open the real SceneView GLB AR try-on (not 2D photo-swap). */
    fun hasDedicatedGlb(haircut: Haircut): Boolean {
        val nameKey = haircut.name.trim().lowercase()
        if (nameKey in byName.keys && nameKey in DEDICATED_STYLE_NAMES) return true
        return haircut.id in DEDICATED_GLB_IDS
    }

    /**
     * Resolve AR asset config for the selected catalog card.
     * Name is checked first so "Curtains" / "French Crop" always load the correct GLB.
     */
    fun configFor(haircut: Haircut): AssetConfig? {
        val nameKey = haircut.name.trim().lowercase()
        if (nameKey in DEDICATED_STYLE_NAMES) {
            byName[nameKey]?.let { return it }
        }
        byId[haircut.id]?.let { return it }
        byName[nameKey]?.let { return it }
        // Default try-on GLB is the boy cut — apply the same asset fit when no dedicated row.
        if (WIG_ASSET.contains("buzzcut", ignoreCase = true)) return BOY_CUT_FIT
        return null
    }

    fun modelAssetFor(haircut: Haircut): String =
        configFor(haircut)?.modelAsset ?: WIG_ASSET

    private val DEDICATED_STYLE_NAMES = setOf(
        "boy cut",
        "curtains",
        "french crop",
        "modern pompadour",
        "long wavy hair",
        "shaggy wolfcut"
    )
}
