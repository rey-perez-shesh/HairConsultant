package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutGenderStyle
import com.hairconsultant.app.domain.model.TreatmentPreference
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed haircut catalog. [seedIfEmpty] migrates the curated, face-shape-justified
 * catalog ([com.hairconsultant.app.data.SampleData]) into the "haircuts" collection the first
 * time the app runs against an empty Firestore project; every later launch just reads whatever
 * is currently in Firestore, so the catalog can be edited from the console without an app update.
 */
interface HaircutRemoteRepository {
    suspend fun fetchAll(): List<Haircut>
    suspend fun seedIfEmpty(haircuts: List<Haircut>)
    /** Merge-update specific catalog rows (e.g. Meshy GLB name/thumbnail patches) without wiping the catalog. */
    suspend fun upsertHaircuts(haircuts: List<Haircut>)
}

class FirestoreHaircutRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : HaircutRemoteRepository {

    private val collection get() = firestore.collection("haircuts")

    override suspend fun fetchAll(): List<Haircut> {
        val snapshot = collection.get().await()
        return snapshot.documents.map { doc ->
            Haircut(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                imageUrl = doc.getString("imageUrl").orEmpty(),
                length = runCatching { HairLength.valueOf(doc.getString("length").orEmpty()) }
                    .getOrDefault(HairLength.MEDIUM),
                texture = runCatching { HairTexture.valueOf(doc.getString("texture").orEmpty()) }
                    .getOrDefault(HairTexture.STRAIGHT),
                recommendedFaceShapes = (doc.get("recommendedFaceShapes") as? List<*>)
                    ?.mapNotNull { shape -> (shape as? String)?.let { runCatching { FaceShape.valueOf(it) }.getOrNull() } }
                    .orEmpty(),
                genderStyle = doc.getString("genderStyle")
                    ?.let { runCatching { HaircutGenderStyle.valueOf(it) }.getOrNull() } ?: HaircutGenderStyle.UNISEX,
                treatment = doc.getString("treatment")
                    ?.let { runCatching { TreatmentPreference.valueOf(it) }.getOrNull() } ?: TreatmentPreference.NONE,
                description = doc.getString("description").orEmpty()
            )
        }
    }

    override suspend fun seedIfEmpty(haircuts: List<Haircut>) {
        val existing = collection.limit(1).get().await()
        if (!existing.isEmpty) return
        val batch = firestore.batch()
        haircuts.forEach { haircut ->
            batch.set(collection.document(haircut.id), haircut.toFirestoreMap())
        }
        batch.commit().await()
    }

    override suspend fun upsertHaircuts(haircuts: List<Haircut>) {
        if (haircuts.isEmpty()) return
        val batch = firestore.batch()
        haircuts.forEach { haircut ->
            batch.set(
                collection.document(haircut.id),
                haircut.toFirestoreMap(),
                SetOptions.merge()
            )
        }
        batch.commit().await()
    }
}

private fun Haircut.toFirestoreMap(): Map<String, Any> = mapOf(
    "name" to name,
    "imageUrl" to imageUrl,
    "length" to length.name,
    "texture" to texture.name,
    "recommendedFaceShapes" to recommendedFaceShapes.map { it.name },
    "genderStyle" to genderStyle.name,
    "treatment" to treatment.name,
    "description" to description
)

/** In-memory stand-in used until a Firebase project is attached. */
class MockHaircutRemoteRepository : HaircutRemoteRepository {
    private val store = mutableMapOf<String, Haircut>()

    override suspend fun fetchAll(): List<Haircut> = store.values.toList()

    override suspend fun seedIfEmpty(haircuts: List<Haircut>) {
        if (store.isNotEmpty()) return
        haircuts.forEach { store[it.id] = it }
    }

    override suspend fun upsertHaircuts(haircuts: List<Haircut>) {
        haircuts.forEach { store[it.id] = it }
    }
}
