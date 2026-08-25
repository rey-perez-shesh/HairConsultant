package com.hairconsultant.app.ui.imageupload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.analysis.FaceAnalyzer
import com.hairconsultant.app.data.analysis.NoFaceDetectedException
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.remote.firebase.MediaStorageRepository
import com.hairconsultant.app.data.remote.gemini.GeminiChatRepository
import com.hairconsultant.app.data.remote.gemini.GeminiImageRepository
import com.hairconsultant.app.data.remote.gemini.describeForChatContext
import com.hairconsultant.app.data.repository.ConsultationRepository
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.data.repository.UserRepository
import com.hairconsultant.app.domain.model.ChatSender
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
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
import java.util.UUID

enum class ImageUploadStage { IDLE, ANALYZING, CONFIRM_RESULT, ASK_FIX, CONSULTING, SUGGESTIONS }

enum class UploadFixTarget { FACE_SHAPE, HAIR_LENGTH, HAIR_TEXTURE }

data class ImageUploadUiState(
    val stage: ImageUploadStage = ImageUploadStage.IDLE,
    val sourceImageUri: Uri? = null,
    val scanResult: ScanResult? = null,
    val desiredLength: HairLength? = null,
    val desiredTexture: HairTexture? = null,
    val desiredTreatment: TreatmentPreference? = null,
    val fixTarget: UploadFixTarget? = null,
    val afterRescan: Boolean = false,
    val suggestions: List<Haircut> = emptyList(),
    val selectedHaircut: Haircut? = null,
    val isGenerating: Boolean = false,
    val generatedImageUri: Uri? = null,
    val generationError: String? = null,
    val remoteSourceImageUrl: String? = null,
    val remoteResultImageUrl: String? = null
)

class ImageUploadViewModel(
    private val faceAnalyzer: FaceAnalyzer,
    private val haircutRepository: HaircutRepository,
    private val geminiImageRepository: GeminiImageRepository,
    private val chatRepository: GeminiChatRepository,
    val chatBot: ChatBotController,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val consultationRepository: ConsultationRepository,
    private val mediaStorageRepository: MediaStorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageUploadUiState())
    val uiState: StateFlow<ImageUploadUiState> = _uiState

    val chatState: StateFlow<ChatBotUiState> = chatBot.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ChatBotUiState()
    )

    /** Claims the shared chatbot's next reply whenever Image Upload becomes the active screen. */
    fun activateChat() {
        chatBot.setHandler { text -> handleFreeText(text) }
    }

    private var consultationId = UUID.randomUUID().toString()

    fun onImagePicked(uri: Uri) {
        consultationId = UUID.randomUUID().toString()
        _uiState.update {
            ImageUploadUiState(sourceImageUri = uri, stage = ImageUploadStage.ANALYZING)
        }
        analyzePhoto(uri)
    }

    private fun analyzePhoto(uri: Uri) {
        chatBot.setOpen(true)
        chatBot.pushBotMessage("Got your photo! Analyzing your face shape and hair now...")
        viewModelScope.launch {
            try {
                val result = faceAnalyzer.analyzeImage(uri)
                _uiState.update { it.copy(stage = ImageUploadStage.CONFIRM_RESULT, scanResult = result) }
                chatBot.pushBotMessage(
                    "I see a ${result.faceShape.displayName} face shape" +
                        hairScanPhrase(result) +
                        ". Did I get it right?",
                    quickReplies = listOf("Yes, that's right", "No, let me fix it")
                )
            } catch (error: NoFaceDetectedException) {
                _uiState.update { it.copy(stage = ImageUploadStage.IDLE) }
                chatBot.pushBotMessage(error.message ?: "I couldn't find a face in that photo. Try another one.")
            } catch (error: Exception) {
                _uiState.update { it.copy(stage = ImageUploadStage.IDLE) }
                chatBot.pushBotMessage("Could not analyze that photo (${error.message}). Please try another.")
            }
        }
    }

    fun onQuickReply(reply: String) {
        viewModelScope.launch { chatBot.selectQuickReply(reply) }
    }

    private suspend fun handleFreeText(text: String) {
        when (_uiState.value.stage) {
            ImageUploadStage.CONFIRM_RESULT -> onResultConfirmationReply(text)
            ImageUploadStage.ASK_FIX -> onFixReply(text)
            ImageUploadStage.CONSULTING -> onConsultingReply(text)
            else -> respondFreeform(text)
        }
    }

    /**
     * Once the face-shape/length/texture scan is confirmed, the user talks freely with the AI
     * consultant — no more rigid quick-reply questions — until they tap [CONFIRM_LABEL], at which
     * point [confirmConsultation] collects everything discussed and generates real suggestions.
     */
    private suspend fun onConsultingReply(text: String) {
        if (text.equals(CONFIRM_LABEL, ignoreCase = true)) {
            confirmConsultation()
        } else {
            respondFreeform(text)
        }
    }

    /**
     * Anything outside the guided scan/fix flow (before a photo is uploaded, or once
     * suggestions are already shown) goes to the AI consultant instead of a canned reply, so
     * users can ask real questions ("is rebonding safe for wavy hair?", "what's low-maintenance
     * for the gym?") and get an answer reasoned from [com.hairconsultant.app.data.HairKnowledgeBase].
     */
    private suspend fun respondFreeform(text: String) {
        val context = buildConsultationContext()
        chatRepository.reply(chatBot.state.value.messages, text, context)
            .onSuccess { reply -> chatBot.pushBotMessage(reply) }
            .onFailure { error -> chatBot.pushBotMessage("I couldn't reach the AI consultant right now (${error.message}).") }
    }

    /** Everything the app already knows for certain about this consultation, for the AI consultant to reason over. */
    private fun buildConsultationContext(candidates: List<Haircut> = _uiState.value.suggestions): String {
        val state = _uiState.value
        return buildString {
            state.scanResult?.let { append("Confirmed face shape: ${it.faceShape.displayName}. ") }
            (state.desiredLength ?: state.scanResult?.hairLength)?.let { append("Hair length: ${it.displayName}. ") }
            (state.desiredTexture ?: state.scanResult?.hairTexture)?.let { append("Hair texture: ${it.displayName}. ") }
            state.desiredTreatment?.takeIf { it != TreatmentPreference.NONE }
                ?.let { append("Planned treatment: ${it.displayName}. ") }
            append("\nCandidate haircuts:\n")
            append(candidates.describeForChatContext())
        }
    }

    private fun onResultConfirmationReply(text: String) {
        if (text.startsWith("No", ignoreCase = true)) {
            _uiState.update { it.copy(stage = ImageUploadStage.ASK_FIX, fixTarget = null) }
            chatBot.pushBotMessage(
                "No problem — what would you like to fix?",
                quickReplies = fixMenuQuickReplies(includeTexture = _uiState.value.scanResult?.hairLength != HairLength.BALD)
            )
            return
        }
        FaceShape.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) }?.let { shape ->
            _uiState.update { it.copy(scanResult = it.scanResult?.copy(faceShape = shape)) }
        }
        continueAfterConfirmedHair()
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
                _uiState.update { it.copy(fixTarget = UploadFixTarget.FACE_SHAPE) }
                chatBot.pushBotMessage("Pick your face shape:", quickReplies = FaceShape.entries.map { it.displayName })
            }
            text.equals(FIX_HAIR_LENGTH_LABEL, ignoreCase = true) -> {
                _uiState.update { it.copy(fixTarget = UploadFixTarget.HAIR_LENGTH) }
                chatBot.pushBotMessage("Pick your hair length:", quickReplies = HairLength.entries.map { it.displayName })
            }
            text.equals(FIX_HAIR_TEXTURE_LABEL, ignoreCase = true) -> {
                _uiState.update { it.copy(fixTarget = UploadFixTarget.HAIR_TEXTURE) }
                chatBot.pushBotMessage("Pick your hair texture:", quickReplies = HairTexture.entries.map { it.displayName })
            }
            else -> chatBot.pushBotMessage(
                "Tap one of the options below, or choose Rescan to analyze the photo again.",
                quickReplies = fixMenuQuickReplies()
            )
        }
    }

    private fun onFixValueSelected(text: String) {
        when (_uiState.value.fixTarget) {
            UploadFixTarget.FACE_SHAPE -> {
                val shape = FaceShape.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: run {
                    chatBot.pushBotMessage("Pick a face shape from the list.", quickReplies = FaceShape.entries.map { it.displayName })
                    return
                }
                _uiState.update { it.copy(scanResult = it.scanResult?.copy(faceShape = shape), fixTarget = null) }
                startConsulting(bald = _uiState.value.scanResult?.hairLength == HairLength.BALD)
            }
            UploadFixTarget.HAIR_LENGTH -> {
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
                continueAfterConfirmedHair()
            }
            UploadFixTarget.HAIR_TEXTURE -> {
                val texture = HairTexture.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: run {
                    chatBot.pushBotMessage("Pick a texture from the list.", quickReplies = HairTexture.entries.map { it.displayName })
                    return
                }
                _uiState.update {
                    it.copy(
                        desiredTexture = texture,
                        scanResult = it.scanResult?.copy(hairTexture = texture),
                        fixTarget = null
                    )
                }
                startConsulting(bald = false)
            }
            null -> onFixCategorySelected(text)
        }
    }

    fun rescan() {
        val uri = _uiState.value.sourceImageUri
        if (uri == null) {
            chatBot.pushBotMessage("Upload a photo first, then I can rescan it.")
            return
        }
        consultationId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                stage = ImageUploadStage.ANALYZING,
                scanResult = null,
                desiredLength = null,
                desiredTexture = null,
                desiredTreatment = null,
                fixTarget = null,
                afterRescan = true,
                suggestions = emptyList(),
                selectedHaircut = null,
                isGenerating = false,
                generatedImageUri = null,
                generationError = null,
                remoteResultImageUrl = null
            )
        }
        analyzePhoto(uri)
    }

    private fun continueAfterConfirmedHair() {
        val bald = _uiState.value.scanResult?.hairLength == HairLength.BALD ||
            _uiState.value.desiredLength == HairLength.BALD
        startConsulting(bald)
    }

    /**
     * Opens the free-form consultation: the confirmed scan is in, so from here the user just
     * talks with the AI consultant (length, texture, treatments, lifestyle, anything) until they
     * tap [CONFIRM_LABEL]. No more values get locked in via quick-reply here.
     */
    private fun startConsulting(bald: Boolean) {
        _uiState.update {
            it.copy(
                stage = ImageUploadStage.CONSULTING,
                afterRescan = false,
                desiredLength = if (bald) HairLength.BALD else it.desiredLength ?: it.scanResult?.hairLength,
                desiredTexture = if (bald) null else it.desiredTexture ?: it.scanResult?.hairTexture
            )
        }
        chatBot.pushBotMessage(
            if (bald) {
                "Since you don't have hair to style right now, tell me about the wig look you want — " +
                    "style, vibe, anything at all. Tap \"$CONFIRM_LABEL\" whenever you're ready to see options."
            } else {
                "Tell me about the cut you want — length, texture, any treatments, your lifestyle, whatever's " +
                    "on your mind. Tap \"$CONFIRM_LABEL\" when you're ready to see your matches."
            },
            quickReplies = listOf(CONFIRM_LABEL)
        )
    }

    /**
     * The commit step: collects everything discussed in the consultation (the full chat history
     * is threaded through [GeminiChatRepository.reply] automatically) and generates real
     * suggestions from the catalog, with the AI consultant explaining why each fits.
     */
    fun confirmConsultation() {
        if (_uiState.value.stage != ImageUploadStage.CONSULTING) return
        _uiState.update { it.copy(stage = ImageUploadStage.SUGGESTIONS) }
        viewModelScope.launch {
            val result = _uiState.value.scanResult ?: return@launch
            val bald = result.hairLength == HairLength.BALD || _uiState.value.desiredLength == HairLength.BALD
            val suggestions = if (bald) {
                haircutRepository.observeClusters().first().flatMap { it.haircuts }
                    .filter { result.faceShape in it.recommendedFaceShapes }
                    .ifEmpty { haircutRepository.observeClusters().first().flatMap { it.haircuts } }
                    .take(6)
            } else {
                val length = _uiState.value.desiredLength ?: result.hairLength
                val texture = _uiState.value.desiredTexture ?: result.hairTexture
                haircutRepository.observeMatching(result.faceShape, length, texture).first()
                    .ifEmpty { haircutRepository.observeClusters().first().flatMap { it.haircuts }.take(6) }
            }
            _uiState.update { it.copy(suggestions = suggestions) }
            val intro = chatRepository.reply(
                chatBot.state.value.messages,
                "The user just confirmed they're happy with the consultation. Recommend hairstyles from the " +
                    "candidates now, weaving in everything relevant from our conversation, and explain why each fits.",
                buildConsultationContext(suggestions)
            ).getOrElse {
                if (bald) {
                    "Since you don't have hair to style, you can try a wig. " +
                        "Here are looks that fit your ${result.faceShape.displayName} face — pick one and I'll generate a preview."
                } else {
                    "Here are cuts that fit your ${result.faceShape.displayName} face shape. " +
                        "Pick one and I'll generate a preview on your photo."
                }
            }
            chatBot.pushBotMessage(intro, haircutOptions = suggestions)
            persistConsultation(selectedHaircut = null)
            persistPreferences()
        }
    }

    fun onHaircutSelected(haircut: Haircut) {
        val sourceUri = _uiState.value.sourceImageUri ?: return
        _uiState.update {
            it.copy(selectedHaircut = haircut, isGenerating = true, generatedImageUri = null, generationError = null)
        }
        chatBot.pushBotMessage("Generating \"${haircut.name}\" on your photo...")
        viewModelScope.launch {
            val prompt = buildStylePrompt(haircut)
            val result = geminiImageRepository.generateHaircutPreview(sourceUri, prompt)
            result.onSuccess { generatedUri ->
                _uiState.update { it.copy(isGenerating = false, generatedImageUri = generatedUri) }
                chatBot.pushBotMessage("Here's your new look!")
                uploadResultAndPersist(haircut, generatedUri)
            }.onFailure { error ->
                _uiState.update { it.copy(isGenerating = false, generationError = error.message) }
                chatBot.pushBotMessage(
                    "I couldn't generate that preview yet (${error.message}). " +
                        "Showing the style's reference photo instead."
                )
                persistConsultation(selectedHaircut = haircut)
            }
        }
    }

    /**
     * Builds the Gemini image prompt from the chatbot conversation: the face shape the user
     * confirmed, the length/texture/treatment they chose while chatting, and everything else
     * they typed or tapped, so the generated preview reflects what was actually discussed.
     */
    private fun buildStylePrompt(haircut: Haircut): String {
        val state = _uiState.value
        val conversationContext = chatBot.state.value.messages
            .filter { it.sender == ChatSender.USER }
            .joinToString(separator = "; ") { it.text }
            .take(600)
        return buildString {
            append("Apply the \"${haircut.name}\" hairstyle (${haircut.length.displayName.lowercase()} length, ")
            append("${haircut.texture.displayName.lowercase()} texture) to the person in the photo, ")
            append("keeping their face, skin tone, and background unchanged.")
            state.scanResult?.let { append(" Their face shape is ${it.faceShape.displayName.lowercase()}.") }
            (state.desiredTreatment ?: haircut.treatment).takeIf { it != TreatmentPreference.NONE }?.let {
                append(" Include a ${it.displayName.lowercase()} treatment look.")
            }
            if (conversationContext.isNotBlank()) {
                append(" Context from the conversation with the user: $conversationContext.")
            }
        }
    }

    /** Uploads the generated try-on preview to Storage, then saves the consultation with its URL. */
    private fun uploadResultAndPersist(haircut: Haircut, generatedUri: Uri) {
        val uid = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch {
            val resultUrl = runCatching { mediaStorageRepository.uploadTryOnResult(uid, generatedUri) }
                .getOrElse { generatedUri.toString() }
            _uiState.update { it.copy(remoteResultImageUrl = resultUrl) }
            persistConsultation(selectedHaircut = haircut)
        }
    }

    /** Saves this session as a consultation (Room now, Firestore in the background) once a scan has a result. */
    private fun persistConsultation(selectedHaircut: Haircut?) {
        val uid = authRepository.currentUser.value?.uid ?: return
        val state = _uiState.value
        val result = state.scanResult ?: return
        viewModelScope.launch {
            val sourceUrl = state.remoteSourceImageUrl ?: state.sourceImageUri?.let { uri ->
                runCatching { mediaStorageRepository.uploadConsultationPhoto(uid, uri) }
                    .getOrElse { uri.toString() }
                    .also { url -> _uiState.update { it.copy(remoteSourceImageUrl = url) } }
            }
            consultationRepository.save(
                Consultation(
                    id = consultationId,
                    userId = uid,
                    source = ConsultationSource.IMAGE_UPLOAD,
                    scanResult = result,
                    selectedHaircut = selectedHaircut,
                    sourceImageUrl = sourceUrl,
                    resultImageUrl = _uiState.value.remoteResultImageUrl,
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    /** Remembers the confirmed length/texture/treatment on the user's profile for future personalization. */
    private fun persistPreferences() {
        val uid = authRepository.currentUser.value?.uid ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val current = userRepository.observe(uid).first() ?: return@launch
            userRepository.save(
                current.copy(
                    preferredHairLength = state.desiredLength ?: current.preferredHairLength,
                    preferredHairTexture = state.desiredTexture ?: current.preferredHairTexture,
                    preferredTreatment = state.desiredTreatment ?: current.preferredTreatment
                )
            )
        }
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
private const val CONFIRM_LABEL = "Show My Hairstyles"

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
