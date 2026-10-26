package com.hairconsultant.app.domain.model

/**
 * A candidate hairstyle the recommendation engine can suggest to the user. The catalog is managed
 * from the admin dashboard and read from Firestore's "haircuts" collection (cached in Room);
 * [com.hairconsultant.app.data.SampleData] is only the offline fallback for a fresh install.
 */
data class Haircut(
    val id: String,
    val name: String,
    val imageUrl: String,
    val length: HairLength,
    val texture: HairTexture,
    val recommendedFaceShapes: List<FaceShape>,
    val genderStyle: HaircutGenderStyle = HaircutGenderStyle.UNISEX,
    val treatment: TreatmentPreference = TreatmentPreference.NONE,
    val description: String = "",
    val sortOrder: Int = 0,
    /** How this style is tried on; [ArType.AUTO] keeps the APK-bundled mapping in HairstyleArCatalog. */
    val arType: ArType = ArType.AUTO,
    /** Firebase Storage download URL of a dashboard-uploaded `.deepar` / `.glb` asset. */
    val arAssetUrl: String? = null,
    /** Bumped by the dashboard on every asset upload so the on-device cache re-downloads it. */
    val arAssetVersion: Int = 0,
    /** Per-asset fit for remote GLBs (same meaning as HairstyleArCatalog.AssetConfig). */
    val glbFit: GlbFit = GlbFit()
)

/** Try-on source chosen per style in the admin dashboard. */
enum class ArType {
    /** Use whatever HairstyleArCatalog maps from the APK's bundled assets (or none). */
    AUTO,
    DEEPAR,
    GLB,
    /** Hide the try-on button for this style. */
    NONE
}

data class GlbFit(
    val scaleMul: Float = 0.7f,
    val offsetX: Float = 0f,
    val offsetY: Float = -0.03f,
    val offsetZ: Float = 0.02f,
    val yawOffset: Float = 0f
)

data class HaircutCluster(
    val length: HairLength,
    val texture: HairTexture,
    val haircuts: List<Haircut>
) {
    val title: String get() = "${length.displayName} & ${texture.displayName}"
}
