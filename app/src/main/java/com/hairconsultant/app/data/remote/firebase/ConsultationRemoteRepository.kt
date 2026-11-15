package com.hairconsultant.app.data.remote.firebase

import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import com.hairconsultant.app.BuildConfig
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.ScanResult
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed sync of consultation history, plus the user's saved looks
 * ([Consultation.isSavedLook]) so those follow the account to a reinstall or a new phone.
 */
interface ConsultationRemoteRepository {
    suspend fun save(consultation: Consultation)
    suspend fun setFavorite(consultationId: String, isFavorite: Boolean)
    suspend fun fetchHistory(userId: String): List<Consultation>

    suspend fun saveLook(saved: RemoteSavedLook)
    suspend fun deleteLook(lookId: String)
    suspend fun deleteLooks(userId: String)
    suspend fun fetchLooks(userId: String): List<RemoteSavedLook>
}

/**
 * A saved look as Firestore holds it: the catalog haircut travels as an id, since
 * [Consultation.selectedHaircut] needs a full [com.hairconsultant.app.domain.model.Haircut] that
 * only the caller, with the local catalog on hand, can look up.
 */
data class RemoteSavedLook(val look: Consultation, val haircutId: String?)

class FirestoreConsultationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ConsultationRemoteRepository {

    private val collection get() = firestore.collection("consultations")

    override suspend fun save(consultation: Consultation) {
        collection.document(consultation.id).set(
            mapOf(
                "userId" to consultation.userId,
                "source" to consultation.source.name,
                "faceShape" to consultation.scanResult.faceShape.name,
                "hairLength" to consultation.scanResult.hairLength.name,
                "hairTexture" to consultation.scanResult.hairTexture.name,
                "selectedHaircutId" to consultation.selectedHaircut?.id,
                "sourceImageUrl" to consultation.sourceImageUrl,
                "resultImageUrl" to consultation.resultImageUrl,
                "isFavorite" to consultation.isFavorite,
                "createdAtEpochMillis" to consultation.createdAtEpochMillis,
                // Analysis quality details for the admin dashboard's classifier-health analytics.
                "hairColor" to consultation.scanResult.hairColor.name,
                "faceShapeConfidence" to consultation.scanResult.faceShapeConfidence.toDouble(),
                "hairLengthConfidence" to consultation.scanResult.hairLengthConfidence.toDouble(),
                "hairTextureConfidence" to consultation.scanResult.hairTextureConfidence.toDouble(),
                "verifierFaceShape" to consultation.scanResult.verifierFaceShape?.name,
                "sourcesAgreed" to consultation.scanResult.sourcesAgreed,
                "appVersion" to BuildConfig.VERSION_NAME,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        ).await()
    }

    override suspend fun setFavorite(consultationId: String, isFavorite: Boolean) {
        collection.document(consultationId).update("isFavorite", isFavorite).await()
    }

    override suspend fun fetchHistory(userId: String): List<Consultation> {
        val snapshot = collection.whereEqualTo("userId", userId).get().await()
        return snapshot.documents.map { doc ->
            Consultation(
                id = doc.id,
                userId = userId,
                source = runCatching { ConsultationSource.valueOf(doc.getString("source").orEmpty()) }
                    .getOrDefault(ConsultationSource.FACE_SCAN),
                scanResult = ScanResult(
                    faceShape = runCatching { FaceShape.valueOf(doc.getString("faceShape").orEmpty()) }
                        .getOrDefault(FaceShape.OVAL),
                    hairLength = runCatching { HairLength.valueOf(doc.getString("hairLength").orEmpty()) }
                        .getOrDefault(HairLength.MEDIUM),
                    hairTexture = runCatching { HairTexture.valueOf(doc.getString("hairTexture").orEmpty()) }
                        .getOrDefault(HairTexture.STRAIGHT)
                ),
                selectedHaircut = null,
                sourceImageUrl = doc.getString("sourceImageUrl"),
                resultImageUrl = doc.getString("resultImageUrl"),
                isFavorite = doc.getBoolean("isFavorite") ?: false,
                createdAtEpochMillis = doc.getLong("createdAtEpochMillis") ?: 0L
            )
        }
    }

    /**
     * Saved looks get a collection of their own rather than "consultations": there, each would
     * count as an extra scan in the admin dashboard's analytics.
     */
    private val looks get() = firestore.collection("savedLooks")

    override suspend fun saveLook(saved: RemoteSavedLook) {
        val look = saved.look
        looks.document(look.id).set(
            mapOf(
                "userId" to look.userId,
                "source" to look.source.name,
                "faceShape" to look.scanResult.faceShape.name,
                "hairLength" to look.scanResult.hairLength.name,
                "hairTexture" to look.scanResult.hairTexture.name,
                "haircutId" to saved.haircutId,
                "sourceImageUrl" to look.sourceImageUrl,
                "resultImageUrl" to look.resultImageUrl,
                "createdAtEpochMillis" to look.createdAtEpochMillis
            )
        ).await()
    }

    override suspend fun deleteLook(lookId: String) {
        looks.document(lookId).delete().await()
    }

    override suspend fun deleteLooks(userId: String) {
        val snapshot = looks.whereEqualTo("userId", userId).get().await()
        if (snapshot.isEmpty) return
        val batch = firestore.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    override suspend fun fetchLooks(userId: String): List<RemoteSavedLook> {
        val snapshot = looks.whereEqualTo("userId", userId).get().await()
        return snapshot.documents.map { doc ->
            RemoteSavedLook(
                look = Consultation(
                    id = doc.id,
                    userId = userId,
                    source = runCatching { ConsultationSource.valueOf(doc.getString("source").orEmpty()) }
                        .getOrDefault(ConsultationSource.IMAGE_UPLOAD),
                    scanResult = ScanResult(
                        faceShape = runCatching { FaceShape.valueOf(doc.getString("faceShape").orEmpty()) }
                            .getOrDefault(FaceShape.OVAL),
                        hairLength = runCatching { HairLength.valueOf(doc.getString("hairLength").orEmpty()) }
                            .getOrDefault(HairLength.MEDIUM),
                        hairTexture = runCatching { HairTexture.valueOf(doc.getString("hairTexture").orEmpty()) }
                            .getOrDefault(HairTexture.STRAIGHT)
                    ),
                    selectedHaircut = null,
                    sourceImageUrl = doc.getString("sourceImageUrl"),
                    resultImageUrl = doc.getString("resultImageUrl"),
                    isFavorite = true,
                    createdAtEpochMillis = doc.getLong("createdAtEpochMillis") ?: 0L
                ),
                haircutId = doc.getString("haircutId")
            )
        }
    }
}

/** In-memory stand-in used until a Firebase project is attached. */
class MockConsultationRemoteRepository : ConsultationRemoteRepository {
    private val store = mutableMapOf<String, Consultation>()

    override suspend fun save(consultation: Consultation) {
        store[consultation.id] = consultation
    }

    override suspend fun setFavorite(consultationId: String, isFavorite: Boolean) {
        store[consultationId]?.let { store[consultationId] = it.copy(isFavorite = isFavorite) }
    }

    override suspend fun fetchHistory(userId: String): List<Consultation> =
        store.values.filter { it.userId == userId }

    private val looks = mutableMapOf<String, RemoteSavedLook>()

    override suspend fun saveLook(saved: RemoteSavedLook) {
        looks[saved.look.id] = saved
    }

    override suspend fun deleteLook(lookId: String) {
        looks.remove(lookId)
    }

    override suspend fun deleteLooks(userId: String) {
        looks.values.removeAll { it.look.userId == userId }
    }

    override suspend fun fetchLooks(userId: String): List<RemoteSavedLook> =
        looks.values.filter { it.look.userId == userId }
}
