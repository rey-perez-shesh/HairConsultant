package com.hairconsultant.app.ui.facescan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.analysis.FaceAnalyzer
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.TreatmentPreference
import com.hairconsultant.app.ui.chatbot.ChatBotController
import com.hairconsultant.app.ui.chatbot.ChatBotUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FaceScanStage { IDLE, ANALYZING, CONFIRM_RESULT, ASK_LENGTH, ASK_TREATMENT, SUGGESTIONS }

data class FaceScanUiState(
    val stage: FaceScanStage = FaceScanStage.IDLE,
    val scanResult: ScanResult? = null,
    val desiredLength: HairLength? = null,
    val desiredTreatment: TreatmentPreference? = null,
    val suggestions: List<Haircut> = emptyList(),
    val triedOnHaircut: Haircut? = null
)

class FaceScanViewModel(
    private val faceAnalyzer: FaceAnalyzer,
    private val haircutRepository: HaircutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaceScanUiState())
    val uiState: StateFlow<FaceScanUiState> = _uiState

    val chatBot = ChatBotController(onUserMessage = { text -> handleFreeText(text) })
    val chatState: StateFlow<ChatBotUiState> = chatBot.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ChatBotUiState()
    )

    fun startScan() {
        if (_uiState.value.stage == FaceScanStage.ANALYZING) return
        _uiState.update { it.copy(stage = FaceScanStage.ANALYZING) }
        chatBot.setOpen(true)
        chatBot.pushBotMessage("Analyzing your face and hair, hold still...")
        viewModelScope.launch {
            val result = faceAnalyzer.analyzeCameraFrame()
            _uiState.update { it.copy(stage = FaceScanStage.CONFIRM_RESULT, scanResult = result) }
            chatBot.pushBotMessage(
                "I detected a ${result.faceShape.displayName} face shape, " +
                    "${result.hairLength.displayName.lowercase()} hair, and " +
                    "${result.hairTexture.displayName.lowercase()} texture. Did I get that right?",
                quickReplies = listOf("Yes, that's right", "No, let me fix it")
            )
        }
    }

    fun onQuickReply(reply: String) {
        viewModelScope.launch { chatBot.selectQuickReply(reply) }
    }

    private suspend fun handleFreeText(text: String) {
        when (_uiState.value.stage) {
            FaceScanStage.CONFIRM_RESULT -> onResultConfirmationReply(text)
            FaceScanStage.ASK_LENGTH -> onLengthReply(text)
            FaceScanStage.ASK_TREATMENT -> onTreatmentReply(text)
            else -> chatBot.pushBotMessage("Tap \"Scan\" to start a new face analysis.")
        }
    }

    private fun onResultConfirmationReply(text: String) {
        if (text.startsWith("No", ignoreCase = true)) {
            chatBot.pushBotMessage(
                "No problem — what's your actual face shape?",
                quickReplies = FaceShape.entries.map { it.displayName }
            )
            return
        }
        if (FaceShape.entries.any { it.displayName == text }) {
            _uiState.update {
                it.copy(scanResult = it.scanResult?.copy(faceShape = FaceShape.entries.first { s -> s.displayName == text }))
            }
        }
        _uiState.update { it.copy(stage = FaceScanStage.ASK_LENGTH) }
        chatBot.pushBotMessage(
            "Great, thanks! What length are you going for?",
            quickReplies = HairLength.entries.map { it.displayName }
        )
    }

    private fun onLengthReply(text: String) {
        val length = HairLength.entries.firstOrNull { it.displayName == text } ?: return
        _uiState.update { it.copy(desiredLength = length, stage = FaceScanStage.ASK_TREATMENT) }
        chatBot.pushBotMessage(
            "Got it. Any treatment planned, like rebonding or perming?",
            quickReplies = TreatmentPreference.entries.map { it.displayName }
        )
    }

    private fun onTreatmentReply(text: String) {
        val treatment = TreatmentPreference.entries.firstOrNull { it.displayName == text } ?: return
        _uiState.update { it.copy(desiredTreatment = treatment, stage = FaceScanStage.SUGGESTIONS) }
        viewModelScope.launch {
            val result = _uiState.value.scanResult ?: return@launch
            val length = _uiState.value.desiredLength ?: result.hairLength
            val texture = result.hairTexture
            val matches = haircutRepository.observeMatching(result.faceShape, length, texture).first()
            val suggestions = matches.ifEmpty { haircutRepository.observeClusters().first().flatMap { it.haircuts }.take(6) }
            _uiState.update { it.copy(suggestions = suggestions) }
            chatBot.pushBotMessage(
                "Based on your ${result.faceShape.displayName} face shape, here are some cuts I'd suggest. " +
                    "Tap one to try it on!",
                haircutOptions = suggestions
            )
        }
    }

    fun onHaircutTryOn(haircut: Haircut) {
        _uiState.update { it.copy(triedOnHaircut = haircut) }
    }

    fun clearTryOn() {
        _uiState.update { it.copy(triedOnHaircut = null) }
    }
}
