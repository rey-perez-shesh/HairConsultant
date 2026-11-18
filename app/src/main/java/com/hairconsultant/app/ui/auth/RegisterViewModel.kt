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
    val isRegistered: Boolean = false,
    /**
     * Set when the account was created but its profile couldn't be saved to Firestore; shown
     * before leaving the screen. The profile is kept on this device and uploaded at next login.
     */
    val profileSyncWarning: String? = null
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
            var profileSyncWarning: String? = null
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
                ).onFailure { error ->
                    profileSyncWarning = "Your account was created, but your profile couldn't be saved to the " +
                        "cloud (${error.message}). It's kept on this phone and will be uploaded automatically " +
                        "the next time you log in here."
                }
                // Firebase signs the new user in automatically; sign back out so registration
                // lands on the login page instead of skipping straight into the app.
                authRepository.logout()
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRegistered = result.isSuccess,
                    profileSyncWarning = profileSyncWarning,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun validate(state: RegisterUiState): String? = when {
        state.email.isBlank() -> "Please enter your email."
        !isValidEmail(state.email) -> "Please enter a valid email address containing \"@\"."
        passwordRules(state.password).any { !it.met } ->
            passwordRules(state.password).first { !it.met }.error
        state.password != state.confirmPassword -> "Passwords do not match."
        state.birthdayEpochDay == null -> "Please select your birthday."
        state.birthdayEpochDay > LocalDate.now().toEpochDay() -> "Birthday cannot be in the future."
        state.gender == null -> "Please select your gender."
        else -> null
    }
}

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Something@domain.tld, ignoring surrounding spaces: the check Register and the password reset share. */
fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

/** One password requirement: [label] for the live checklist, [error] for the message on submit. */
data class PasswordRule(val label: String, val error: String, val met: Boolean)

/** The requirements in the order they're reported, so the checklist and the submit check can't disagree. */
fun passwordRules(password: String): List<PasswordRule> = listOf(
    PasswordRule("8+ characters", "Password must be at least 8 characters.", password.length >= 8),
    PasswordRule("Uppercase", "Password must contain at least one uppercase letter.", password.any { it.isUpperCase() }),
    PasswordRule("Lowercase", "Password must contain at least one lowercase letter.", password.any { it.isLowerCase() }),
    PasswordRule("Number", "Password must contain at least one number.", password.any { it.isDigit() }),
    PasswordRule("Symbol", "Password must contain at least one special character.", password.any { !it.isLetterOrDigit() })
)
