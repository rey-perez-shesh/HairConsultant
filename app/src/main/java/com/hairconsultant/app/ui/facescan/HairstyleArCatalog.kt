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
        localOffsetY = -0.22f,
        localOffsetZ = 0.02f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /**
     * Curtains — Meshy layered curtain GLB; crown-align + tuck; no face cutout.
     */
    private val CURTAINS_FIT = AssetConfig(
        modelAsset = "wigs/Meshy_AI_layered_hair_3k_tris_curtain_texture.glb",
        localScaleMul = 0.68f,
        localOffsetY = -0.20f,
        localOffsetZ = 0.02f,
        alignCrownToMaxY = true,
        enableFaceCutout = false
    )

    /** Catalog IDs with a dedicated try-on GLB. */
    val DEDICATED_GLB_IDS: Set<String> = setOf(
        "SHORT_STRAIGHT_0", // Boy Cut
        "SHORT_STRAIGHT_1" // Curtains (was Buzz Cut)
    )

    private val byId: Map<String, AssetConfig> = mapOf(
        "SHORT_STRAIGHT_0" to BOY_CUT_FIT,
        "SHORT_STRAIGHT_1" to CURTAINS_FIT,
        "LONG_WAVY_1" to AssetConfig(
            modelAsset = "wigs/Meshy_AI_Long_Wavy_Hair_AR_texture.glb"
        )
    )

    private val byName: Map<String, AssetConfig> = mapOf(
        "boy cut" to BOY_CUT_FIT,
        "curtains" to CURTAINS_FIT,
        "long wavy hair" to byId.getValue("LONG_WAVY_1")
    )

    /** True when this card should open the real SceneView GLB AR try-on (not 2D photo-swap). */
    fun hasDedicatedGlb(haircut: Haircut): Boolean =
        haircut.id in DEDICATED_GLB_IDS ||
            haircut.name.trim().lowercase() in setOf("boy cut", "curtains")

    fun configFor(haircut: Haircut): AssetConfig? {
        byId[haircut.id]?.let { return it }
        byName[haircut.name.trim().lowercase()]?.let { return it }
        // Default try-on GLB is the boy cut — apply the same asset fit when no dedicated row.
        if (WIG_ASSET.contains("buzzcut", ignoreCase = true)) return BOY_CUT_FIT
        return null
    }

    fun modelAssetFor(haircut: Haircut): String =
        configFor(haircut)?.modelAsset ?: WIG_ASSET
}
