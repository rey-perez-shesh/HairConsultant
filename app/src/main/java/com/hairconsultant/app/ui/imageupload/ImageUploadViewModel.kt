package com.hairconsultant.app.ui.imageupload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.analysis.FaceAnalyzer
import com.hairconsultant.app.data.remote.gemini.GeminiImageRepository
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
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

enum class ImageUploadStage { IDLE, ANALYZING, CONFIRM_RESULT, ASK_LENGTH, ASK_TREATMENT, SUGGESTIONS }

data class ImageUploadUiState(
    val stage: ImageUploadStage = ImageUploadStage.IDLE,
    val sourceImageUri: Uri? = null,
    val scanResult: ScanResult? = null,
    val desiredLength: HairLength? = null,
    val suggestions: List<Haircut> = emptyList(),
    val selectedHaircut: Haircut? = null,
    val isGenerating: Boolean = false,
    val generatedImageUri: Uri? = null,
    val generationError: String? = null
)

class ImageUploadViewModel(
    private val faceAnalyzer: FaceAnalyzer,
    private val haircutRepository: HaircutRepository,
    private val geminiImageRepository: GeminiImageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageUploadUiState())
    val uiState: StateFlow<ImageUploadUiState> = _uiState

    val chatBot = ChatBotController(onUserMessage = { text -> handleFreeText(text) })
    val chatState: StateFlow<ChatBotUiState> = chatBot.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ChatBotUiState()
    )

    fun onImagePicked(uri: Uri) {
        _uiState.update {
            ImageUploadUiState(sourceImageUri = uri, stage = ImageUploadStage.ANALYZING)
        }
        chatBot.setOpen(true)
        chatBot.pushBotMessage("Got your photo! Analyzing your face shape and hair now...")
        viewModelScope.launch {
            val result = faceAnalyzer.analyzeImage(uri)
            _uiState.update { it.copy(stage = ImageUploadStage.CONFIRM_RESULT, scanResult = result) }
            chatBot.pushBotMessage(
                "I see a ${result.faceShape.displayName} face shape with " +
                    "${result.hairLength.displayName.lowercase()}, ${result.hairTexture.displayName.lowercase()} hair. " +
                    "Is that correct?",
                quickReplies = listOf("Yes, that's right", "No, let me fix it")
            )
        }
    }

    fun onQuickReply(reply: String) {
        viewModelScope.launch { chatBot.selectQuickReply(reply) }
    }

    private fun handleFreeText(text: String) {
        when (_uiState.value.stage) {
            ImageUploadStage.CONFIRM_RESULT -> onResultConfirmationReply(text)
            ImageUploadStage.ASK_LENGTH -> onLengthReply(text)
            ImageUploadStage.ASK_TREATMENT -> onTreatmentReply(text)
            else -> chatBot.pushBotMessage("Upload a photo to get started.")
        }
    }

    private fun onResultConfirmationReply(text: String) {
        if (text.startsWith("No", ignoreCase = true)) {
            chatBot.pushBotMessage("What's your actual face shape?", quickReplies = FaceShape.entries.map { it.displayName })
            return
        }
        FaceShape.entries.firstOrNull { it.displayName == text }?.let { shape ->
            _uiState.update { it.copy(scanResult = it.scanResult?.copy(faceShape = shape)) }
        }
        _uiState.update { it.copy(stage = ImageUploadStage.ASK_LENGTH) }
        chatBot.pushBotMessage("What length would you like to try?", quickReplies = HairLength.entries.map { it.displayName })
    }

    private fun onLengthReply(text: String) {
        val length = HairLength.entries.firstOrNull { it.displayName == text } ?: return
        _uiState.update { it.copy(desiredLength = length, stage = ImageUploadStage.ASK_TREATMENT) }
        chatBot.pushBotMessage(
            "Planning any treatment, like rebonding or perming?",
            quickReplies = TreatmentPreference.entries.map { it.displayName }
        )
    }

    private fun onTreatmentReply(text: String) {
        if (TreatmentPreference.entries.none { it.displayName == text }) return
        _uiState.update { it.copy(stage = ImageUploadStage.SUGGESTIONS) }
        viewModelScope.launch {
            val result = _uiState.value.scanResult ?: return@launch
            val length = _uiState.value.desiredLength ?: result.hairLength
            val matches = haircutRepository.observeMatching(result.faceShape, length, result.hairTexture).first()
            val suggestions = matches.ifEmpty { haircutRepository.observeClusters().first().flatMap { it.haircuts }.take(6) }
            _uiState.update { it.copy(suggestions = suggestions) }
            chatBot.pushBotMessage(
                "Here are cuts that fit your ${result.faceShape.displayName} face shape. " +
                    "Pick one and I'll generate a preview on your photo.",
                haircutOptions = suggestions
            )
        }
    }

    fun onHaircutSelected(haircut: Haircut) {
        val sourceUri = _uiState.value.sourceImageUri ?: return
        _uiState.update {
            it.copy(selectedHaircut = haircut, isGenerating = true, generatedImageUri = null, generationError = null)
        }
        chatBot.pushBotMessage("Generating \"${haircut.name}\" on your photo...")
        viewModelScope.launch {
            val result = geminiImageRepository.generateHaircutPreview(sourceUri, haircut)
            result.onSuccess { generatedUri ->
                _uiState.update { it.copy(isGenerating = false, generatedImageUri = generatedUri) }
                chatBot.pushBotMessage("Here's your new look!")
            }.onFailure { error ->
                _uiState.update { it.copy(isGenerating = false, generationError = error.message) }
                chatBot.pushBotMessage(
                    "I couldn't generate that preview yet (${error.message}). " +
                        "Showing the style's reference photo instead."
                )
            }
        }
    }
}
