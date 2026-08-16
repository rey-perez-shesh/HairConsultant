package com.hairconsultant.app.data.remote.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Uploads consultation photos and generated try-on results. Backed by Firebase Storage. */
interface MediaStorageRepository {
    suspend fun uploadConsultationPhoto(userId: String, localUri: Uri): String
    suspend fun uploadTryOnResult(userId: String, localUri: Uri): String
}

class FirebaseMediaStorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : MediaStorageRepository {

    override suspend fun uploadConsultationPhoto(userId: String, localUri: Uri): String =
        upload("users/$userId/consultations/${UUID.randomUUID()}.jpg", localUri)

    override suspend fun uploadTryOnResult(userId: String, localUri: Uri): String =
        upload("users/$userId/tryon/${UUID.randomUUID()}.jpg", localUri)

    private suspend fun upload(path: String, localUri: Uri): String {
        val ref = storage.reference.child(path)
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }
}

/** Returns the local URI unchanged so the UI has something to render before Storage is wired up. */
class MockMediaStorageRepository : MediaStorageRepository {
    override suspend fun uploadConsultationPhoto(userId: String, localUri: Uri): String = localUri.toString()
    override suspend fun uploadTryOnResult(userId: String, localUri: Uri): String = localUri.toString()
}
