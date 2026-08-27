package com.hairconsultant.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.remote.gemini.GeminiChatRepository
import com.hairconsultant.app.data.remote.gemini.describeForChatContext
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.data.repository.UserRepository
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutCluster
import com.hairconsultant.app.domain.model.matchingHaircutStyles
import com.hairconsultant.app.ui.chatbot.ChatBotController
import com.hairconsultant.app.ui.chatbot.ChatBotUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    /** Every cluster as loaded from the catalog, before the filter row below is applied. */
    val allClusters: List<HaircutCluster> = emptyList(),
    /** What's actually shown: [allClusters] narrowed by the four filters below. */
    val clusters: List<HaircutCluster> = emptyList(),
    val isLoading: Boolean = true,
    val selectedHaircut: Haircut? = null,
    val cameraTryOnHaircut: Haircut? = null,
    /** Defaults to the signed-in user's profile gender once loaded, so Home opens already personalized. */
    val genderFilter: Gender? = null,
    val faceShapeFilter: FaceShape? = null,
    val hairLengthFilter: HairLength? = null,
    val hairTextureFilter: HairTexture? = null
)

class HomeViewModel(
    private val haircutRepository: HaircutRepository,
    private val chatRepository: GeminiChatRepository,
    val chatBot: ChatBotController,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    val chatState: StateFlow<ChatBotUiState> = chatBot.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ChatBotUiState()
    )

    /** Claims the shared chatbot's next reply whenever Home becomes the active screen. */
    fun activateChat() {
        chatBot.setHandler { text -> respondToChat(text) }
    }

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            haircutRepository.refresh()
            _uiState.update { it.copy(isLoading = false) }
        }
        viewModelScope.launch {
            haircutRepository.observeClusters().collect { clusters ->
                _uiState.update { it.copy(allClusters = clusters, clusters = applyFilters(clusters, it)) }
            }
        }
        viewModelScope.launch {
            val uid = authRepository.currentUser.value?.uid ?: return@launch
            val gender = userRepository.observe(uid).first()?.gender ?: return@launch
            setGenderFilter(gender)
        }
    }

    /** The dropdown row's four filters — pass `null` to mean "All" for that category. */
    fun setGenderFilter(gender: Gender?) = updateFilters { it.copy(genderFilter = gender) }
    fun setFaceShapeFilter(faceShape: FaceShape?) = updateFilters { it.copy(faceShapeFilter = faceShape) }
    fun setHairLengthFilter(length: HairLength?) = updateFilters { it.copy(hairLengthFilter = length) }
    fun setHairTextureFilter(texture: HairTexture?) = updateFilters { it.copy(hairTextureFilter = texture) }

    private inline fun updateFilters(transform: (HomeUiState) -> HomeUiState) {
        _uiState.update { current ->
            val updated = transform(current)
            updated.copy(clusters = applyFilters(updated.allClusters, updated))
        }
    }

    /** Narrows the full catalog to [HomeUiState]'s current filter selections; empty clusters drop out. */
    private fun applyFilters(clusters: List<HaircutCluster>, filters: HomeUiState): List<HaircutCluster> {
        val allowedStyles = filters.genderFilter
            ?.takeIf { it != Gender.NON_BINARY && it != Gender.PREFER_NOT_TO_SAY }
            ?.matchingHaircutStyles()
        return clusters
            .filter { filters.hairLengthFilter == null || it.length == filters.hairLengthFilter }
            .filter { filters.hairTextureFilter == null || it.texture == filters.hairTextureFilter }
            .map { cluster ->
                cluster.copy(
                    haircuts = cluster.haircuts.filter { haircut ->
                        (allowedStyles == null || haircut.genderStyle in allowedStyles) &&
                            (filters.faceShapeFilter == null || filters.faceShapeFilter in haircut.recommendedFaceShapes)
                    }
                )
            }
            .filter { it.haircuts.isNotEmpty() }
    }

    /** Tapping a catalog card (or a chat-suggested style) shows a quick-look preview first. */
    fun onHaircutSelected(haircut: Haircut) {
        _uiState.update { it.copy(selectedHaircut = haircut) }
    }

    fun dismissHaircutDetail() {
        _uiState.update { it.copy(selectedHaircut = null) }
    }

    /** The preview's "Try It On" button: closes the preview and opens the live camera filter. */
    fun onTryOnClicked(haircut: Haircut) {
        _uiState.update { it.copy(selectedHaircut = null, cameraTryOnHaircut = haircut) }
    }

    fun closeCameraTryOn() {
        _uiState.update { it.copy(cameraTryOnHaircut = null) }
    }

    /** Routes free-form chat through the AI consultant, grounded on whatever catalog entries match. */
    private suspend fun respondToChat(text: String) {
        val catalog = _uiState.value.clusters.flatMap { it.haircuts }
        val matched = matchHaircuts(catalog, text)
        val candidatesForContext = matched.ifEmpty { catalog }
        val candidatesForGallery = matched.ifEmpty { catalog.shuffled() }.take(4)
        val context = "Candidate haircuts from the catalog:\n${candidatesForContext.describeForChatContext()}"
        chatRepository.reply(chatBot.state.value.messages, text, context)
            .onSuccess { reply -> chatBot.pushBotMessage(reply, haircutOptions = candidatesForGallery) }
            .onFailure { error ->
                chatBot.pushBotMessage(
                    "I couldn't reach the AI consultant right now (${error.message}). " +
                        "Try the Face Scan or Image Upload tab for a personalized match in the meantime.",
                    haircutOptions = candidatesForGallery
                )
            }
    }
}

private fun matchHaircuts(catalog: List<Haircut>, text: String): List<Haircut> {
    val lower = text.lowercase()
    return catalog.filter { haircut ->
        lower.contains(haircut.length.displayName.lowercase()) ||
            lower.contains(haircut.texture.displayName.lowercase()) ||
            haircut.recommendedFaceShapes.any { lower.contains(it.displayName.lowercase()) }
    }
}
