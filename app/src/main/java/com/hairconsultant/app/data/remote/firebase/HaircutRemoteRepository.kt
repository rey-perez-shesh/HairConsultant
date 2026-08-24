package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
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
            batch.set(
                collection.document(haircut.id),
                mapOf(
                    "name" to haircut.name,
                    "imageUrl" to haircut.imageUrl,
                    "length" to haircut.length.name,
                    "texture" to haircut.texture.name,
                    "recommendedFaceShapes" to haircut.recommendedFaceShapes.map { it.name },
                    "treatment" to haircut.treatment.name,
                    "description" to haircut.description
                )
            )
        }
        batch.commit().await()
    }
}

/** In-memory stand-in used until a Firebase project is attached. */
class MockHaircutRemoteRepository : HaircutRemoteRepository {
    private val store = mutableMapOf<String, Haircut>()

    override suspend fun fetchAll(): List<Haircut> = store.values.toList()

    override suspend fun seedIfEmpty(haircuts: List<Haircut>) {
        if (store.isNotEmpty()) return
        haircuts.forEach { store[it.id] = it }
    }
}
