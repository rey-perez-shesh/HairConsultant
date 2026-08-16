package com.hairconsultant.app.data.remote.firebase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** In-memory stand-in for [FirebaseAuthRepository], used until a Firebase project is attached. */
class MockAuthRepository : AuthRepository {

    private val registeredUsers = mutableMapOf<String, String>() // email -> password
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    override suspend fun login(email: String, password: String): Result<AuthUser> {
        delay(400) // simulate network round trip
        val storedPassword = registeredUsers[email]
        return if (storedPassword != null && storedPassword == password) {
            val user = AuthUser(uid = email, email = email)
            _currentUser.value = user
            Result.success(user)
        } else {
            Result.failure(IllegalArgumentException("Incorrect email or password."))
        }
    }

    override suspend fun register(email: String, password: String): Result<AuthUser> {
        delay(400)
        if (registeredUsers.containsKey(email)) {
            return Result.failure(IllegalStateException("An account already exists for this email."))
        }
        registeredUsers[email] = password
        val user = AuthUser(uid = UUID.randomUUID().toString(), email = email)
        _currentUser.value = user
        return Result.success(user)
    }

    override fun logout() {
        _currentUser.value = null
    }
}
