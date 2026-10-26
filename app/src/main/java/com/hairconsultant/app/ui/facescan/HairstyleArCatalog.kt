package com.hairconsultant.app.ui.facescan

import android.content.Context
import com.hairconsultant.app.data.remote.ArAssetCache
import com.hairconsultant.app.domain.model.ArType
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.ui.deepar.DeepArCurtainsSession

/**
 * Extensible haircut-id → GLB / temporary 2D PNG mapping for AR try-on.
 *
 * Local offsets/scale are applied after the shared crown / tracking transform — they do not
 * change HeadPoseEstimator, MediaPipe, or global calibration constants.
 *
 * Temporary 2D PNGs use the same catalog IDs as GLB styles so a later swap
 * (`hairstyle.png` → `hairstyle.glb`) only needs a [AssetConfig] row — selection/UI stay the same.
 */
object HairstyleArCatalog {

    /**
     * When true, catalog styles with a mapped `.deepar` asset open DeepAR try-on
     * instead of SceneView GLB / PNG overlay.
     *
     * **Revert:** set to `false` and rebuild — those styles fall back to GLB/PNG.
     */
    const val USE_DEEPAR_TRY_ON: Boolean = true

    /** @deprecated Use [USE_DEEPAR_TRY_ON]. Kept so older call sites/comments still compile. */
    const val USE_DEEPAR_FOR_CURTAINS: Boolean = USE_DEEPAR_TRY_ON

    /**
     * Catalog display name (lowercase) → asset under `assets/effects/`.
     * Names must match [SampleData] haircut titles.
     */
    private val deeparByName: Map<String, String> = mapOf(
        "curtains" to "effects/curtains.deepar",
        "wavy fringe crop" to "effects/wavy_fringe_crop.deepar",
        "face framing layered cut" to "effects/face_framing_layered_cut.deepar",
        "french crop" to "effects/french_crop.deepar",
        "classic blunt bob" to "effects/blunt_bob.deepar",
        "curly quiff" to "effects/curly_quiff.deepar",
        "textured crop medium length" to "effects/textured_crop_medium_length.deepar",
        "shaggy wolfcut" to "effects/shaggy_wolfcut.deepar",
        "layered curly shag" to "effects/layered_curly_shag.deepar",
        "curtain bangs wavy lob" to "effects/curtain_bangs_wavy_lob.deepar"
    )

    /** Catalog id → DeepAR asset (backup when name lookup misses). */
    private val deeparById: Map<String, String> = mapOf(
        ID_CURTAINS to "effects/curtains.deepar",
        "SHORT_WAVY_4" to "effects/wavy_fringe_crop.deepar",
        "MEDIUM_STRAIGHT_4" to "effects/face_framing_layered_cut.deepar",
        ID_FRENCH_CROP to "effects/french_crop.deepar",
        "SHORT_STRAIGHT_6" to "effects/blunt_bob.deepar",
        "MEDIUM_CURLY_5" to "effects/curly_quiff.deepar",
        "MEDIUM_STRAIGHT_6" to "effects/textured_crop_medium_length.deepar",
        ID_SHAGGY_WOLFCUT to "effects/shaggy_wolfcut.deepar",
        "MEDIUM_CURLY_1" to "effects/layered_curly_shag.deepar",
        "MEDIUM_WAVY_0" to "effects/curtain_bangs_wavy_lob.deepar"
    )

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
     * Temporary 2D PNG overlay fit (same ID / selection path as GLB).
     * Offsets are fractions of measured head width/height; scale multiplies head-width fit.
     */
    data class PngAssetConfig(
        /** Drawable resource name under res/drawable (e.g. thumb_curtains or overlay_curtains). */
        val drawableName: String,
        /** Horizontal nudge as a fraction of head width (+ = screen right). */
        val localOffsetX: Float = 0f,
        /** Vertical nudge as a fraction of head height (+ = down). */
        val localOffsetY: Float = -0.15f,
        /** Multiplier on head-width-based PNG scale. */
        val localScaleMul: Float = 2.2f,
        /**
         * Vertical anchor inside the PNG: 0 = top edge on crown, 0.5 = image center on crown.
         */
        val anchorY: Float = 0.18f
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
        localOffsetY = -0.03f,
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

    /**
     * The dashboard-uploaded asset type for [haircut], or null when the style uses the bundled
     * mapping below ([ArType.AUTO], or an explicit type that has no uploaded file yet).
     */
    fun remoteArType(haircut: Haircut): ArType? = when (haircut.arType) {
        ArType.DEEPAR, ArType.GLB -> haircut.arType.takeIf { haircut.arAssetUrl != null }
        ArType.AUTO, ArType.NONE -> null
    }

    private fun isTryOnDisabled(haircut: Haircut): Boolean = haircut.arType == ArType.NONE

    /** True when the GLB comes from the dashboard (downloaded through [ArAssetCache]), not `assets/`. */
    fun isRemoteGlb(haircut: Haircut): Boolean = remoteArType(haircut) == ArType.GLB

    /**
     * DeepAR effect to launch for [haircut]: a downloaded dashboard upload (absolute file path) or
     * a bundled `assets/` path. Null only when a dashboard upload couldn't be downloaded.
     */
    suspend fun resolveDeepArEffect(context: Context, haircut: Haircut): String? =
        if (remoteArType(haircut) == ArType.DEEPAR) {
            ArAssetCache.localFile(context, haircut)?.absolutePath
        } else {
            deepArEffectPath(haircut) ?: DeepArCurtainsSession.DEFAULT_EFFECT_ASSET_PATH
        }

    /** True when this card should open the real SceneView GLB AR try-on (not 2D photo-swap). */
    fun hasDedicatedGlb(haircut: Haircut): Boolean {
        if (isTryOnDisabled(haircut)) return false
        remoteArType(haircut)?.let { return it == ArType.GLB }
        // DeepAR try-on takes priority while the flag is on.
        if (shouldUseDeepAr(haircut)) return false
        val nameKey = haircut.name.trim().lowercase()
        if (nameKey in byName.keys && nameKey in DEDICATED_STYLE_NAMES) return true
        return haircut.id in DEDICATED_GLB_IDS
    }

    /** Curtains catalog card (SampleData id / name). */
    fun isCurtainsHaircut(haircut: Haircut): Boolean {
        val nameKey = haircut.name.trim().lowercase()
        return haircut.id == ID_CURTAINS || nameKey == "curtains"
    }

    /** Asset path under `assets/` for DeepAR, or null if this style has no effect. */
    fun deepArEffectPath(haircut: Haircut): String? {
        val nameKey = haircut.name.trim().lowercase()
        deeparByName[nameKey]?.let { return it }
        return deeparById[haircut.id]
    }

    /**
     * Live DeepAR try-on when [USE_DEEPAR_TRY_ON] is on and this haircut has a mapped `.deepar`.
     */
    fun shouldUseDeepAr(haircut: Haircut): Boolean {
        if (!USE_DEEPAR_TRY_ON || isTryOnDisabled(haircut)) return false
        remoteArType(haircut)?.let { return it == ArType.DEEPAR }
        return deepArEffectPath(haircut) != null
    }

    /** @deprecated Prefer [shouldUseDeepAr]. */
    fun shouldUseDeepArCurtains(haircut: Haircut): Boolean = shouldUseDeepAr(haircut)

    /**
     * Temporary 2D PNG try-on when no dedicated GLB is mapped.
     * Uses catalog [Haircut.imageUrl] drawable (`thumb_*`) or a dedicated `overlay_*` if present.
     */
    fun hasPngTryOn(haircut: Haircut): Boolean {
        if (isTryOnDisabled(haircut) || remoteArType(haircut) != null) return false
        if (shouldUseDeepAr(haircut)) return false
        if (hasDedicatedGlb(haircut)) return false
        return drawableNameForPng(haircut) != null
    }

    /** Home / Face Scan: open live try-on when GLB, DeepAR, or temporary 2D PNG is available. */
    fun canTryOn(haircut: Haircut): Boolean =
        shouldUseDeepAr(haircut) || hasDedicatedGlb(haircut) || hasPngTryOn(haircut)

    /**
     * Resolve temporary 2D PNG fit for the selected catalog card.
     * Same IDs as GLB — replace with [AssetConfig] later without changing selection.
     */
    fun pngConfigFor(haircut: Haircut): PngAssetConfig? {
        if (hasDedicatedGlb(haircut)) return null
        val drawableName = drawableNameForPng(haircut) ?: return null
        val override = pngFitById[haircut.id] ?: pngFitByName[haircut.name.trim().lowercase()]
        return (override ?: DEFAULT_PNG_FIT).copy(drawableName = drawableName)
    }

    /** Resolve drawable id for a PNG config; prefers `overlay_*` when that resource exists. */
    fun resolvePngDrawableRes(context: Context, config: PngAssetConfig): Int {
        val overlayName = config.drawableName
            .removePrefix("thumb_")
            .let { "overlay_$it" }
        val overlayId = context.resources.getIdentifier(overlayName, "drawable", context.packageName)
        if (overlayId != 0) return overlayId
        val id = context.resources.getIdentifier(config.drawableName, "drawable", context.packageName)
        return id
    }

    /**
     * Resolve AR asset config for the selected catalog card.
     * Name is checked first so "Curtains" / "French Crop" always load the correct GLB.
     */
    fun configFor(haircut: Haircut): AssetConfig? {
        if (isRemoteGlb(haircut)) {
            val fit = haircut.glbFit
            return AssetConfig(
                // Not an `assets/` path: a stable label for logs/tint; the file comes from ArAssetCache.
                modelAsset = "remote/${haircut.id}_v${haircut.arAssetVersion}.glb",
                localScaleMul = fit.scaleMul,
                localOffsetX = fit.offsetX,
                localOffsetY = fit.offsetY,
                localOffsetZ = fit.offsetZ,
                modelYawOffset = fit.yawOffset,
                alignCrownToMaxY = true,
                enableFaceCutout = false
            )
        }
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

    private fun drawableNameForPng(haircut: Haircut): String? {
        pngDrawableById[haircut.id]?.let { return it }
        return drawableNameFromImageUrl(haircut.imageUrl)
    }

    private fun drawableNameFromImageUrl(imageUrl: String): String? {
        val marker = "/drawable/"
        val i = imageUrl.indexOf(marker)
        if (i < 0) return null
        val name = imageUrl.substring(i + marker.length).substringBefore('?').substringBefore('#')
        return name.takeIf { it.isNotBlank() && !it.startsWith("http") }
    }

    private val DEDICATED_STYLE_NAMES = setOf(
        "boy cut",
        "curtains",
        "french crop",
        "modern pompadour",
        "long wavy hair",
        "shaggy wolfcut"
    )

    private val DEFAULT_PNG_FIT = PngAssetConfig(drawableName = "")

    /** Optional per-style 2D fit overrides (IDs match SampleData / future GLB rows). */
    private val pngFitById: Map<String, PngAssetConfig> = mapOf(
        "SHORT_STRAIGHT_2" to PngAssetConfig("", localOffsetY = -0.12f, localScaleMul = 2.1f),
        "SHORT_STRAIGHT_3" to PngAssetConfig("", localOffsetY = -0.10f, localScaleMul = 2.3f),
        "SHORT_STRAIGHT_6" to PngAssetConfig("", localOffsetY = -0.08f, localScaleMul = 2.0f, anchorY = 0.22f),
        "SHORT_STRAIGHT_8" to PngAssetConfig("", localOffsetY = -0.14f, localScaleMul = 2.15f),
        "LONG_STRAIGHT_0" to PngAssetConfig("", localOffsetY = -0.05f, localScaleMul = 2.6f, anchorY = 0.12f),
        "LONG_STRAIGHT_1" to PngAssetConfig("", localOffsetY = -0.05f, localScaleMul = 2.55f, anchorY = 0.12f),
        "LONG_CURLY_0" to PngAssetConfig("", localOffsetY = -0.04f, localScaleMul = 2.7f, anchorY = 0.12f),
        "MEDIUM_WAVY_3" to PngAssetConfig("", localOffsetY = -0.08f, localScaleMul = 2.4f)
    )

    private val pngFitByName: Map<String, PngAssetConfig> = emptyMap()

    /** Explicit drawable name overrides when imageUrl is remote / missing. */
    private val pngDrawableById: Map<String, String> = emptyMap()
}
