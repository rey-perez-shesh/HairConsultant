package com.hairconsultant.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.remote.gemini.GeminiChatRepository
import com.hairconsultant.app.data.remote.gemini.describeForChatContext
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutCluster
import com.hairconsultant.app.ui.chatbot.ChatBotController
import com.hairconsultant.app.ui.chatbot.ChatBotUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val clusters: List<HaircutCluster> = emptyList(),
    val isLoading: Boolean = true,
    val selectedHaircut: Haircut? = null
)

class HomeViewModel(
    private val haircutRepository: HaircutRepository,
    private val chatRepository: GeminiChatRepository,
    val chatBot: ChatBotController
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
                _uiState.update { it.copy(clusters = clusters) }
            }
        }
    }

    fun onHaircutSelected(haircut: Haircut) {
        _uiState.update { it.copy(selectedHaircut = haircut) }
    }

    fun dismissHaircutDetail() {
        _uiState.update { it.copy(selectedHaircut = null) }
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
