package com.hairconsultant.app.data.remote.firebase

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val email: String)

/**
 * Login/registration contract. [FirebaseAuthRepository] is the real Firebase Auth-backed
 * implementation; until a Firebase project (google-services.json) is wired in, [AppContainer]
 * hands out [MockAuthRepository] so the UI flow can be built and demoed today.
 */
interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    suspend fun login(email: String, password: String): Result<AuthUser>

    suspend fun register(email: String, password: String): Result<AuthUser>

    fun logout()
}
