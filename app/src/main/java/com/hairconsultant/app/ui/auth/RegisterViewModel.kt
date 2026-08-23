package com.hairconsultant.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.repository.UserRepository
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val birthdayEpochDay: Long? = null,
    val gender: Gender? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistered: Boolean = false
)

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    fun onBirthdaySelected(epochDay: Long) = _uiState.update { it.copy(birthdayEpochDay = epochDay, errorMessage = null) }
    fun onGenderSelected(gender: Gender) = _uiState.update { it.copy(gender = gender, errorMessage = null) }

    fun register() {
        val state = _uiState.value
        val validationError = validate(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.register(state.email.trim(), state.password)
            result.onSuccess { authUser ->
                userRepository.save(
                    User(
                        id = authUser.uid,
                        email = authUser.email,
                        username = authUser.email.substringBefore("@"),
                        birthdayEpochDay = state.birthdayEpochDay ?: 0L,
                        gender = state.gender ?: Gender.PREFER_NOT_TO_SAY,
                        createdAtEpochMillis = System.currentTimeMillis()
                    )
                )
                // Firebase signs the new user in automatically; sign back out so registration
                // lands on the login page instead of skipping straight into the app.
                authRepository.logout()
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRegistered = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun validate(state: RegisterUiState): String? = when {
        state.email.isBlank() -> "Please enter your email."
        !EMAIL_REGEX.matches(state.email.trim()) -> "Please enter a valid email address containing \"@\"."
        state.password.length < 8 -> "Password must be at least 8 characters."
        !state.password.any { it.isUpperCase() } -> "Password must contain at least one uppercase letter."
        !state.password.any { it.isLowerCase() } -> "Password must contain at least one lowercase letter."
        !state.password.any { it.isDigit() } -> "Password must contain at least one number."
        state.password.none { !it.isLetterOrDigit() } -> "Password must contain at least one special character."
        state.password != state.confirmPassword -> "Passwords do not match."
        state.birthdayEpochDay == null -> "Please select your birthday."
        state.birthdayEpochDay > LocalDate.now().toEpochDay() -> "Birthday cannot be in the future."
        state.gender == null -> "Please select your gender."
        else -> null
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
