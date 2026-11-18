package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CancellationException
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

    override suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (e: FirebaseAuthInvalidUserException) {
        // Projects without email-enumeration protection report unknown emails; answer the same as a send.
        Result.success(Unit)
    } catch (e: FirebaseNetworkException) {
        Result.failure(Exception("No connection. Check your internet and try again.", e))
    } catch (e: FirebaseTooManyRequestsException) {
        Result.failure(Exception("Too many attempts. Wait a few minutes and try again.", e))
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Result.failure(Exception("That email address isn't valid.", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(Exception("Couldn't send the email right now. Try again later.", e))
    }

    override fun logout() {
        firebaseAuth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser?.toAuthUser(): AuthUser? =
        this?.let { AuthUser(uid = it.uid, email = it.email.orEmpty()) }
}
