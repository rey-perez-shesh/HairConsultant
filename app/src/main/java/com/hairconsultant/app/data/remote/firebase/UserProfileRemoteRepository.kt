package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.User
import kotlinx.coroutines.tasks.await

/** Firestore-backed user profile document, keyed by the Firebase Auth uid. */
interface UserProfileRemoteRepository {
    suspend fun fetch(userId: String): User?
    suspend fun save(user: User)
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
            createdAtEpochMillis = snapshot.getLong("createdAtEpochMillis") ?: 0L
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
                "createdAtEpochMillis" to user.createdAtEpochMillis
            )
        ).await()
    }
}

/** In-memory stand-in used until a Firebase project is attached. */
class MockUserProfileRepository : UserProfileRemoteRepository {
    private val store = mutableMapOf<String, User>()

    override suspend fun fetch(userId: String): User? = store[userId]

    override suspend fun save(user: User) {
        store[user.id] = user
    }
}
