package com.hairconsultant.app.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    /** The forgot-password pop-up, or null while it's closed. */
    val reset: ResetPasswordState? = null
)

/** Asks for the email until [sentTo] is set, then shows "Check your email" for that address. */
data class ResetPasswordState(
    val email: String,
    val isSending: Boolean = false,
    val error: String? = null,
    val sentTo: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun togglePasswordVisibility() = _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun openPasswordReset() = _uiState.update { it.copy(reset = ResetPasswordState(email = it.email.trim())) }
    fun onResetEmailChange(value: String) = updateReset { it.copy(email = value, error = null) }
    fun closePasswordReset() = _uiState.update { it.copy(reset = null) }

    fun sendPasswordReset() {
        val reset = _uiState.value.reset ?: return
        if (reset.isSending) return
        val email = reset.email.trim()
        val invalid = when {
            email.isEmpty() -> "Enter the email you signed up with."
            !isValidEmail(email) -> "That email address isn't valid."
            else -> null
        }
        if (invalid != null) {
            updateReset { it.copy(error = invalid) }
            return
        }
        updateReset { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            updateReset {
                it.copy(isSending = false, sentTo = email.takeIf { result.isSuccess }, error = result.exceptionOrNull()?.message)
            }
        }
    }

    /** No-op once the pop-up is closed, so a send finishing after Cancel doesn't reopen it. */
    private fun updateReset(change: (ResetPasswordState) -> ResetPasswordState) =
        _uiState.update { state -> state.copy(reset = state.reset?.let(change)) }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter both email and password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(state.email.trim(), state.password)
            result.getOrNull()?.let { user -> syncProfile(user.uid) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Downloads the profile before Home opens (so the gender default is ready), and re-uploads it
     * if a past registration's Firestore write failed and only this device has it. Bounded so a
     * slow connection never holds up login; a failure here just leaves the local copy in use.
     */
    private suspend fun syncProfile(userId: String) {
        val synced = withTimeoutOrNull(PROFILE_SYNC_TIMEOUT_MILLIS) {
            runCatching { userRepository.refreshFromRemote(userId) }
                .onFailure { Log.w(TAG, "Profile sync after login failed", it) }
        }
        if (synced == null) Log.w(TAG, "Profile sync after login timed out")
    }

    private companion object {
        const val TAG = "LoginViewModel"
        const val PROFILE_SYNC_TIMEOUT_MILLIS = 10_000L
    }
}
