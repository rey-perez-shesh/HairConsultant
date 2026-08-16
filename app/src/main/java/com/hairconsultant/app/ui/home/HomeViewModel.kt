package com.hairconsultant.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class HomeViewModel(private val haircutRepository: HaircutRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    val chatBot = ChatBotController(onUserMessage = { text -> respondToChat(text) })
    val chatState: StateFlow<ChatBotUiState> = chatBot.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ChatBotUiState()
    )

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

    private fun respondToChat(text: String) {
        val clusters = _uiState.value.clusters
        val suggestion = clusters.flatMap { it.haircuts }.shuffled().take(4)
        chatBot.pushBotMessage(
            "Here are a few styles you might like based on \"$text\". " +
                "For a personalized match, try the Face Scan or Image Upload tab so I can see your face shape.",
            haircutOptions = suggestion
        )
    }
}
