package com.hairconsultant.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.repository.ConsultationRepository
import com.hairconsultant.app.data.repository.FeedbackRepository
import com.hairconsultant.app.data.repository.UserRepository
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class ProfileFormState(
    val tab: ProfileTab,
    val rating: Int,
    val comment: String,
    val settingsOpen: Boolean,
    val feedbackSubmitted: Boolean
)

enum class ProfileTab { HISTORY, FAVORITES }

data class ProfileUiState(
    val user: User? = null,
    val selectedTab: ProfileTab = ProfileTab.HISTORY,
    val history: List<Consultation> = emptyList(),
    val favorites: List<Consultation> = emptyList(),
    val rating: Int = 0,
    val feedbackComment: String = "",
    val isSettingsOpen: Boolean = false,
    val feedbackSubmitted: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val consultationRepository: ConsultationRepository,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ProfileTab.HISTORY)
    private val _rating = MutableStateFlow(0)
    private val _feedbackComment = MutableStateFlow("")
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _feedbackSubmitted = MutableStateFlow(false)

    private val userId: String? get() = authRepository.currentUser.value?.uid

    private val formState: StateFlow<ProfileFormState> = combine(
        _selectedTab, _rating, _feedbackComment, _isSettingsOpen, _feedbackSubmitted
    ) { tab, rating, comment, settingsOpen, submitted ->
        ProfileFormState(tab, rating, comment, settingsOpen, submitted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileFormState(ProfileTab.HISTORY, 0, "", false, false))

    private val userFlow = authRepository.currentUser.flatMapLatest { user ->
        user?.let { userRepository.observe(it.uid) } ?: flowOf(null)
    }
    private val historyFlow = authRepository.currentUser.flatMapLatest { user ->
        user?.let { consultationRepository.observeHistory(it.uid) } ?: flowOf(emptyList())
    }
    private val favoritesFlow = authRepository.currentUser.flatMapLatest { user ->
        user?.let { consultationRepository.observeFavorites(it.uid) } ?: flowOf(emptyList())
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        userFlow, historyFlow, favoritesFlow, formState
    ) { user, history, favorites, form ->
        ProfileUiState(
            user = user,
            history = history,
            favorites = favorites,
            selectedTab = form.tab,
            rating = form.rating,
            feedbackComment = form.comment,
            isSettingsOpen = form.settingsOpen,
            feedbackSubmitted = form.feedbackSubmitted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun onTabSelected(tab: ProfileTab) = _selectedTab.update { tab }
    fun onRatingChanged(rating: Int) = _rating.update { rating }
    fun onFeedbackCommentChanged(text: String) = _feedbackComment.update { text }
    fun setSettingsOpen(open: Boolean) = _isSettingsOpen.update { open }

    fun toggleFavorite(consultation: Consultation) {
        viewModelScope.launch {
            consultationRepository.setFavorite(consultation.id, !consultation.isFavorite)
        }
    }

    fun submitFeedback() {
        val uid = userId ?: return
        viewModelScope.launch {
            feedbackRepository.submit(uid, _rating.value, _feedbackComment.value)
            _feedbackSubmitted.update { true }
            _rating.update { 0 }
            _feedbackComment.update { "" }
        }
    }

    fun clearHistory() {
        val uid = userId ?: return
        viewModelScope.launch { consultationRepository.clearHistory(uid) }
    }

    fun clearFavorites() {
        val uid = userId ?: return
        viewModelScope.launch { consultationRepository.clearFavorites(uid) }
    }

    fun logout() {
        authRepository.logout()
    }
}
