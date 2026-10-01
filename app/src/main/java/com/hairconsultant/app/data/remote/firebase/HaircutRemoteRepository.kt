package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.hairconsultant.app.domain.model.Haircut
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed mirror of the haircut catalog. [reconcile] is called on every
 * [com.hairconsultant.app.data.repository.HaircutRepositoryImpl.refresh] and makes the "haircuts"
 * collection exactly match [com.hairconsultant.app.data.SampleData] — the catalog in code is the
 * single source of truth, and Firestore is kept as a synced mirror/backup of it rather than an
 * independently-editable copy that can drift out of sync.
 */
interface HaircutRemoteRepository {
    /** Upserts every entry in [haircuts] and deletes any existing document not in it. */
    suspend fun reconcile(haircuts: List<Haircut>)
}

class FirestoreHaircutRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : HaircutRemoteRepository {

    private val collection get() = firestore.collection("haircuts")

    // A single Firestore batch caps out at 500 writes; fine for this catalog's size (~90 entries
    // plus a handful of deletes), but would need chunking if the catalog grew far beyond that.
    override suspend fun reconcile(haircuts: List<Haircut>) {
        val existingIds = collection.get().await().documents.map { it.id }.toSet()
        val currentIds = haircuts.map { it.id }.toSet()
        val batch = firestore.batch()
        haircuts.forEach { haircut -> batch.set(collection.document(haircut.id), haircut.toFirestoreMap()) }
        (existingIds - currentIds).forEach { staleId -> batch.delete(collection.document(staleId)) }
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

    override suspend fun reconcile(haircuts: List<Haircut>) {
        store.clear()
        haircuts.forEach { store[it.id] = it }
    }
}
