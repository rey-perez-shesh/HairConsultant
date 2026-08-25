package com.hairconsultant.app.ui.facescan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.analysis.FaceAnalyzer
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.data.analysis.NoFaceDetectedException
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairColor
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

enum class FaceScanStage { IDLE, ANALYZING, CONFIRM_RESULT, ASK_FIX, ASK_LENGTH, ASK_TEXTURE, ASK_TREATMENT, SUGGESTIONS }

enum class ScanFixTarget { FACE_SHAPE, HAIR_LENGTH, HAIR_TEXTURE }

data class FaceScanUiState(
    val stage: FaceScanStage = FaceScanStage.IDLE,
    val scanResult: ScanResult? = null,
    val desiredLength: HairLength? = null,
    val desiredTexture: HairTexture? = null,
    val desiredTreatment: TreatmentPreference? = null,
    val fixTarget: ScanFixTarget? = null,
    val afterRescan: Boolean = false,
    val suggestions: List<Haircut> = emptyList(),
    val triedOnHaircut: Haircut? = null
)

class FaceScanViewModel(
    private val faceAnalyzer: FaceAnalyzer,
    private val haircutRepository: HaircutRepository,
    val landmarkStore: FaceLandmarkStore
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
            try {
                val result = faceAnalyzer.analyzeCameraFrame()
                _uiState.update { it.copy(stage = FaceScanStage.CONFIRM_RESULT, scanResult = result) }
                chatBot.pushBotMessage(
                    "I detected a ${result.faceShape.displayName} face shape" +
                        hairScanPhrase(result) +
                        ". Did I get it right?",
                    quickReplies = listOf("Yes, that's right", "No, let me fix it")
                )
            } catch (error: NoFaceDetectedException) {
                _uiState.update { it.copy(stage = FaceScanStage.IDLE) }
                chatBot.pushBotMessage(error.message ?: "I couldn't see a face. Line up with the outline and try again.")
            } catch (error: Exception) {
                _uiState.update { it.copy(stage = FaceScanStage.IDLE) }
                chatBot.pushBotMessage("Scan failed (${error.message}). Please try again.")
            }
        }
    }

    fun onQuickReply(reply: String) {
        viewModelScope.launch { chatBot.selectQuickReply(reply) }
    }

    private suspend fun handleFreeText(text: String) {
        when (_uiState.value.stage) {
            FaceScanStage.CONFIRM_RESULT -> onResultConfirmationReply(text)
            FaceScanStage.ASK_FIX -> onFixReply(text)
            FaceScanStage.ASK_LENGTH -> onLengthReply(text)
            FaceScanStage.ASK_TEXTURE -> onTextureReply(text)
            FaceScanStage.ASK_TREATMENT -> onTreatmentReply(text)
            else -> chatBot.pushBotMessage("Tap \"Scan\" to start a new face analysis.")
        }
    }

    private fun onResultConfirmationReply(text: String) {
        if (text.startsWith("No", ignoreCase = true)) {
            _uiState.update { it.copy(stage = FaceScanStage.ASK_FIX, fixTarget = null) }
            chatBot.pushBotMessage(
                "No problem — what would you like to fix?",
                quickReplies = fixMenuQuickReplies(includeTexture = _uiState.value.scanResult?.hairLength != HairLength.BALD)
            )
            return
        }
        if (FaceShape.entries.any { it.displayName.equals(text, ignoreCase = true) }) {
            _uiState.update {
                it.copy(scanResult = it.scanResult?.copy(faceShape = FaceShape.entries.first { s -> s.displayName.equals(text, ignoreCase = true) }))
            }
        }
        if (_uiState.value.afterRescan) {
            continueAfterConfirmedHair()
            return
        }
        if (_uiState.value.scanResult?.hairLength == HairLength.BALD) {
            continueAfterConfirmedHair()
            return
        }
        _uiState.update { it.copy(stage = FaceScanStage.ASK_LENGTH) }
        chatBot.pushBotMessage(
            "Great, thanks! What length are you going for?",
            quickReplies = HairLength.entries.map { it.displayName }
        )
    }

    private fun onFixReply(text: String) {
        when {
            text.equals(RESCAN_LABEL, ignoreCase = true) -> rescan()
            _uiState.value.fixTarget == null -> onFixCategorySelected(text)
            else -> onFixValueSelected(text)
        }
    }

    private fun onFixCategorySelected(text: String) {
        when {
            text.equals(FIX_FACE_SHAPE_LABEL, ignoreCase = true) -> {
                _uiState.update { it.copy(fixTarget = ScanFixTarget.FACE_SHAPE) }
                chatBot.pushBotMessage(
                    "Pick your face shape:",
                    quickReplies = FaceShape.entries.map { it.displayName }
                )
            }
            text.equals(FIX_HAIR_LENGTH_LABEL, ignoreCase = true) -> {
                _uiState.update { it.copy(fixTarget = ScanFixTarget.HAIR_LENGTH) }
                chatBot.pushBotMessage(
                    "Pick your hair length:",
                    quickReplies = HairLength.entries.map { it.displayName }
                )
            }
            text.equals(FIX_HAIR_TEXTURE_LABEL, ignoreCase = true) -> {
                _uiState.update { it.copy(fixTarget = ScanFixTarget.HAIR_TEXTURE) }
                chatBot.pushBotMessage(
                    "Pick your hair texture:",
                    quickReplies = HairTexture.entries.map { it.displayName }
                )
            }
            else -> chatBot.pushBotMessage(
                "Tap one of the options below, or choose Rescan to try again.",
                quickReplies = fixMenuQuickReplies()
            )
        }
    }

    private fun onFixValueSelected(text: String) {
        when (_uiState.value.fixTarget) {
            ScanFixTarget.FACE_SHAPE -> {
                val shape = FaceShape.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: run {
                    chatBot.pushBotMessage("Pick a face shape from the list.", quickReplies = FaceShape.entries.map { it.displayName })
                    return
                }
                _uiState.update {
                    it.copy(
                        scanResult = it.scanResult?.copy(faceShape = shape),
                        fixTarget = null,
                        stage = FaceScanStage.ASK_LENGTH
                    )
                }
                chatBot.pushBotMessage(
                    "Updated to ${shape.displayName}. What length are you going for?",
                    quickReplies = HairLength.entries.map { it.displayName }
                )
            }
            ScanFixTarget.HAIR_LENGTH -> {
                val length = HairLength.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: run {
                    chatBot.pushBotMessage("Pick a length from the list.", quickReplies = HairLength.entries.map { it.displayName })
                    return
                }
                _uiState.update {
                    it.copy(
                        desiredLength = length,
                        scanResult = it.scanResult?.copy(hairLength = length),
                        fixTarget = null
                    )
                }
                if (length == HairLength.BALD) {
                    continueAfterConfirmedHair()
                    return
                }
                _uiState.update { it.copy(stage = FaceScanStage.ASK_TEXTURE) }
                chatBot.pushBotMessage(
                    "Updated to ${length.displayName}. What's your hair texture?",
                    quickReplies = HairTexture.entries.map { it.displayName }
                )
            }
            ScanFixTarget.HAIR_TEXTURE -> {
                val texture = HairTexture.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: run {
                    chatBot.pushBotMessage("Pick a texture from the list.", quickReplies = HairTexture.entries.map { it.displayName })
                    return
                }
                _uiState.update {
                    it.copy(
                        desiredTexture = texture,
                        scanResult = it.scanResult?.copy(hairTexture = texture),
                        fixTarget = null,
                        stage = FaceScanStage.ASK_TREATMENT
                    )
                }
                chatBot.pushBotMessage(
                    "Updated to ${texture.displayName}. Any treatment planned, like rebonding or perming?",
                    quickReplies = TreatmentPreference.entries.map { it.displayName }
                )
            }
            null -> onFixCategorySelected(text)
        }
    }

    private fun rescan() {
        _uiState.update { FaceScanUiState(stage = FaceScanStage.IDLE, afterRescan = true) }
        chatBot.pushBotMessage("Rescanning — hold still...")
        startScan()
    }

    private fun continueAfterConfirmedHair() {
        val bald = _uiState.value.scanResult?.hairLength == HairLength.BALD ||
            _uiState.value.desiredLength == HairLength.BALD
        if (bald) {
            suggestWigs()
            return
        }
        askTreatment()
    }

    private fun suggestWigs() {
        _uiState.update {
            it.copy(
                stage = FaceScanStage.SUGGESTIONS,
                afterRescan = false,
                desiredLength = it.desiredLength ?: HairLength.BALD,
                desiredTexture = null
            )
        }
        viewModelScope.launch {
            val result = _uiState.value.scanResult ?: return@launch
            val matches = haircutRepository.observeClusters().first()
                .flatMap { it.haircuts }
                .filter { result.faceShape in it.recommendedFaceShapes }
            val suggestions = matches.ifEmpty {
                haircutRepository.observeClusters().first().flatMap { it.haircuts }
            }.take(6)
            _uiState.update { it.copy(suggestions = suggestions) }
            chatBot.pushBotMessage(
                "Since you don't have hair to style, you can try a wig. " +
                    "Here are looks that fit your ${result.faceShape.displayName} face — tap one to try it on.",
                haircutOptions = suggestions
            )
        }
    }

    private fun askTreatment() {
        _uiState.update {
            it.copy(
                stage = FaceScanStage.ASK_TREATMENT,
                afterRescan = false,
                desiredLength = it.desiredLength ?: it.scanResult?.hairLength,
                desiredTexture = it.desiredTexture ?: it.scanResult?.hairTexture
            )
        }
        chatBot.pushBotMessage(
            "Got it. Any treatment planned, like rebonding or perming?",
            quickReplies = TreatmentPreference.entries.map { it.displayName }
        )
    }

    private fun onLengthReply(text: String) {
        val length = HairLength.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: return
        _uiState.update {
            it.copy(
                desiredLength = length,
                scanResult = if (length == HairLength.BALD) it.scanResult?.copy(hairLength = HairLength.BALD) else it.scanResult
            )
        }
        if (length == HairLength.BALD) {
            continueAfterConfirmedHair()
            return
        }
        _uiState.update { it.copy(stage = FaceScanStage.ASK_TEXTURE) }
        val detected = _uiState.value.scanResult?.takeIf { it.hairTextureConfidence >= 0.5f }?.hairTexture
        val hint = detected?.let { " I detected ${it.displayName.lowercase()} hair." }.orEmpty()
        chatBot.pushBotMessage(
            "What's your hair texture?$hint",
            quickReplies = HairTexture.entries.map { it.displayName }
        )
    }

    private fun onTextureReply(text: String) {
        val texture = HairTexture.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: return
        _uiState.update {
            it.copy(
                desiredTexture = texture,
                scanResult = it.scanResult?.copy(hairTexture = texture)
            )
        }
        askTreatment()
    }

    private fun onTreatmentReply(text: String) {
        val treatment = TreatmentPreference.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: return
        _uiState.update { it.copy(desiredTreatment = treatment, stage = FaceScanStage.SUGGESTIONS) }
        viewModelScope.launch {
            val result = _uiState.value.scanResult ?: return@launch
            val length = _uiState.value.desiredLength ?: result.hairLength
            val texture = _uiState.value.desiredTexture ?: result.hairTexture
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
        chatBot.setOpen(false)
        _uiState.update { it.copy(triedOnHaircut = haircut) }
    }

    fun clearTryOn() {
        _uiState.update { it.copy(triedOnHaircut = null) }
    }
}

private fun fixMenuQuickReplies(includeTexture: Boolean = true): List<String> = buildList {
    add(FIX_FACE_SHAPE_LABEL)
    add(FIX_HAIR_LENGTH_LABEL)
    if (includeTexture) add(FIX_HAIR_TEXTURE_LABEL)
    add(RESCAN_LABEL)
}

private const val FIX_FACE_SHAPE_LABEL = "Face shape"
private const val FIX_HAIR_LENGTH_LABEL = "Hair length"
private const val FIX_HAIR_TEXTURE_LABEL = "Hair texture"
private const val RESCAN_LABEL = "Rescan"

private fun hairScanPhrase(result: ScanResult): String {
    if (result.hairLength == HairLength.BALD) {
        return " and I don't see much hair (bald)"
    }
    val details = buildList {
        if (result.hairLengthConfidence >= 0.5f) {
            add("${result.hairLength.displayName.lowercase()} hair")
        }
        if (result.hairTextureConfidence >= 0.5f) {
            add("${result.hairTexture.displayName.lowercase()} texture")
        }
        if (result.hairColorConfidence >= 0.5f && result.hairColor != HairColor.OTHER) {
            add("${result.hairColor.displayName.lowercase()} color")
        }
    }
    return if (details.isEmpty()) "" else " and ${details.joinToString(", ")}"
}
