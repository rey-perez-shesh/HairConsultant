package com.hairconsultant.app.data

import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.TreatmentPreference

/**
 * Placeholder haircut catalog used until the real "filters" table (PostgreSQL, mirrored into
 * Firestore/backend API) is populated. [com.hairconsultant.app.data.repository.HaircutRepository]
 * seeds Room with this the first time it runs so the clustered Home screen has something to show.
 */
object SampleData {

    private val namesByTexture = mapOf(
        HairTexture.STRAIGHT to listOf("Sleek Bob", "Classic Crop", "Blunt Cut", "Straight Shag", "Layered Sleek"),
        HairTexture.WAVY to listOf("Beach Waves", "Wavy Lob", "Textured Waves", "Soft Shag", "Wavy Layers"),
        HairTexture.CURLY to listOf("Curly Afro", "Defined Curls", "Curly Bob", "Spiral Layers", "Curly Fringe"),
        HairTexture.COILY to listOf("Coily Crop", "Twist Out", "Coily Bob", "Natural Coils", "Coily Fade")
    )

    val allHaircuts: List<Haircut> by lazy {
        HairLength.entries.flatMap { length ->
            HairTexture.entries.flatMap { texture ->
                namesByTexture.getValue(texture).mapIndexed { index, name ->
                    val id = "${length.name}_${texture.name}_$index"
                    Haircut(
                        id = id,
                        name = "$name ${length.displayName}",
                        imageUrl = "https://picsum.photos/seed/$id/400/520",
                        length = length,
                        texture = texture,
                        recommendedFaceShapes = FaceShape.entries.filterIndexed { i, _ -> (i + index) % 2 == 0 },
                        treatment = TreatmentPreference.NONE,
                        description = "A ${texture.displayName.lowercase()}-friendly " +
                            "${length.displayName.lowercase()} style."
                    )
                }
            }
        }
    }

    fun forCluster(length: HairLength, texture: HairTexture): List<Haircut> =
        allHaircuts.filter { it.length == length && it.texture == texture }
}
