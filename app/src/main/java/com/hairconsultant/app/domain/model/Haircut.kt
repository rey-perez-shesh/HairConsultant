package com.hairconsultant.app.domain.model

/**
 * A candidate hairstyle the recommendation engine (backed by the "filters" table in
 * PostgreSQL / Firestore once wired up) can suggest to the user.
 */
data class Haircut(
    val id: String,
    val name: String,
    val imageUrl: String,
    val length: HairLength,
    val texture: HairTexture,
    val recommendedFaceShapes: List<FaceShape>,
    val treatment: TreatmentPreference = TreatmentPreference.NONE,
    val description: String = ""
)

data class HaircutCluster(
    val length: HairLength,
    val texture: HairTexture,
    val haircuts: List<Haircut>
) {
    val title: String get() = "${length.displayName} & ${texture.displayName}"
}
