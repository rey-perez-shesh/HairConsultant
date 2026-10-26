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

/** Firestore-backed sync of consultation history, so it survives a reinstall/new device. */
interface ConsultationRemoteRepository {
    suspend fun save(consultation: Consultation)
    suspend fun setFavorite(consultationId: String, isFavorite: Boolean)
    suspend fun fetchHistory(userId: String): List<Consultation>
}

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
}
