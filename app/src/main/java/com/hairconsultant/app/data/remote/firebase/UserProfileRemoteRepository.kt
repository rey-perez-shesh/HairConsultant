package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.TreatmentPreference
import com.hairconsultant.app.domain.model.User
import kotlinx.coroutines.tasks.await

/** Firestore-backed user profile document, keyed by the Firebase Auth uid. */
interface UserProfileRemoteRepository {
    suspend fun fetch(userId: String): User?
    suspend fun save(user: User)

    /** Stamps the profile's last-active time (shown in the admin dashboard's user list). */
    suspend fun markActive(userId: String)
}

class FirestoreUserProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : UserProfileRemoteRepository {

    private val collection get() = firestore.collection("users")

    override suspend fun fetch(userId: String): User? {
        val snapshot = collection.document(userId).get().await()
        if (!snapshot.exists()) return null
        return User(
            id = userId,
            email = snapshot.getString("email").orEmpty(),
            username = snapshot.getString("username").orEmpty(),
            birthdayEpochDay = snapshot.getLong("birthdayEpochDay") ?: 0L,
            gender = runCatching { Gender.valueOf(snapshot.getString("gender").orEmpty()) }
                .getOrDefault(Gender.PREFER_NOT_TO_SAY),
            photoUrl = snapshot.getString("photoUrl"),
            createdAtEpochMillis = snapshot.getLong("createdAtEpochMillis") ?: 0L,
            preferredHairLength = snapshot.getString("preferredHairLength")
                ?.let { runCatching { HairLength.valueOf(it) }.getOrNull() },
            preferredHairTexture = snapshot.getString("preferredHairTexture")
                ?.let { runCatching { HairTexture.valueOf(it) }.getOrNull() },
            preferredTreatment = snapshot.getString("preferredTreatment")
                ?.let { runCatching { TreatmentPreference.valueOf(it) }.getOrNull() }
        )
    }

    override suspend fun save(user: User) {
        collection.document(user.id).set(
            mapOf(
                "email" to user.email,
                "username" to user.username,
                "birthdayEpochDay" to user.birthdayEpochDay,
                "gender" to user.gender.name,
                "photoUrl" to user.photoUrl,
                "createdAtEpochMillis" to user.createdAtEpochMillis,
                "preferredHairLength" to user.preferredHairLength?.name,
                "preferredHairTexture" to user.preferredHairTexture?.name,
                "preferredTreatment" to user.preferredTreatment?.name
            ),
            // Merge so fields only the admin backend writes (e.g. "disabled") survive profile edits.
            SetOptions.merge()
        ).await()
    }

    override suspend fun markActive(userId: String) {
        collection.document(userId).update("lastActiveAtEpochMillis", System.currentTimeMillis()).await()
    }
}

/** In-memory stand-in used until a Firebase project is attached. */
class MockUserProfileRepository : UserProfileRemoteRepository {
    private val store = mutableMapOf<String, User>()

    override suspend fun fetch(userId: String): User? = store[userId]

    override suspend fun save(user: User) {
        store[user.id] = user
    }

    override suspend fun markActive(userId: String) = Unit
}
