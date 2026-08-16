package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** Real implementation backed by Firebase Authentication. Requires google-services.json. */
class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser.toAuthUser())
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser.toAuthUser()
        }
    }

    override suspend fun login(email: String, password: String): Result<AuthUser> = runCatching {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        result.user.toAuthUser() ?: error("Login succeeded but no user was returned")
    }

    override suspend fun register(email: String, password: String): Result<AuthUser> = runCatching {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        result.user.toAuthUser() ?: error("Registration succeeded but no user was returned")
    }

    override fun logout() {
        firebaseAuth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser?.toAuthUser(): AuthUser? =
        this?.let { AuthUser(uid = it.uid, email = it.email.orEmpty()) }
}
