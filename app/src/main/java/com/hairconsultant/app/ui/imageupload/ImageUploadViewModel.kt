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
     *
     * Grounds on [computeSuggestions] rather than the (pre-confirmation, still-empty)
     * [ImageUploadUiState.suggestions] directly — otherwise every question asked during
     * CONSULTING, which is exactly the phase the user is invited into to talk about styles, would
     * ground on an empty candidate list and the AI consultant would truthfully — but
     * unhelpfully — report it has no candidates from the app.
     */
    private suspend fun respondFreeform(text: String) {
        val candidates = _uiState.value.suggestions.ifEmpty { computeSuggestions() }
        val context = buildConsultationContext(candidates)
        val conversation = chatBot.buildReplyContext()
        chatRepository.reply(conversation.recentMessages, text, context, conversation.summary)
            .onSuccess { reply -> chatBot.pushBotMessage(reply) }
            .onFailure { error -> chatBot.pushBotMessage("I couldn't reach the AI consultant right now (${error.message}).") }
    }

    /** Everything the app already knows for certain about this consultation, for the AI consultant to reason over. */
    private fun buildConsultationContext(candidates: List<Haircut>): String {
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
            val suggestions = computeSuggestions()
            _uiState.update { it.copy(suggestions = suggestions) }
            val conversation = chatBot.buildReplyContext()
            val intro = chatRepository.reply(
                conversation.recentMessages,
                "The user just confirmed they're happy with the consultation. Recommend hairstyles from the " +
                    "candidates now, weaving in everything relevant from our conversation, and explain why each fits.",
                buildConsultationContext(suggestions),
                conversation.summary
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

    /**
     * Best-matching catalog haircuts for whatever's currently known (confirmed scan + any
     * length/texture chosen while chatting) — used both to lock in [ImageUploadUiState.suggestions]
     * once the consultation is confirmed, and by [respondFreeform] to ground the AI consultant
     * *before* confirmation too, so it's never left reasoning over an empty candidate list.
     */
    private suspend fun computeSuggestions(): List<Haircut> {
        val result = _uiState.value.scanResult
            ?: return haircutRepository.observeClusters().first().flatMap { it.haircuts }.take(MAX_SUGGESTIONS)
        val bald = result.hairLength == HairLength.BALD || _uiState.value.desiredLength == HairLength.BALD
        return if (bald) {
            haircutRepository.observeClusters().first().flatMap { it.haircuts }
                .filter { result.faceShape in it.recommendedFaceShapes }
                .ifEmpty { haircutRepository.observeClusters().first().flatMap { it.haircuts } }
                .take(MAX_SUGGESTIONS)
        } else {
            val length = _uiState.value.desiredLength ?: result.hairLength
            val texture = _uiState.value.desiredTexture ?: result.hairTexture
            haircutRepository.observeMatching(result.faceShape, length, texture).first()
                .ifEmpty { haircutRepository.observeClusters().first().flatMap { it.haircuts }.take(MAX_SUGGESTIONS) }
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
            val referenceUri = runCatching { Uri.parse(haircut.imageUrl) }.getOrNull()
            val result = geminiImageRepository.generateHaircutPreview(sourceUri, referenceUri, prompt)
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
     * confirmed, the length/texture/treatment they chose while chatting, and everything else they
     * typed or tapped, so the generated preview reflects what was actually discussed. Explicitly
     * tells Gemini which of the two attached images is which, since [GeminiImageRepository.
     * generateHaircutPreview] now sends the catalog's own reference photo for [haircut] alongside
     * the user's source photo, rather than leaving the style's look to be guessed from its name.
     *
     * Reuses [ChatBotController.buildReplyContext] (the same bounded recent-window + rolling
     * summary built for chat replies) instead of re-truncating [chatBot]'s full history here —
     * so, unlike the old oldest-first `.take(600)`, the *most recent* thing the user asked for
     * survives, no matter how long the persisted conversation has grown across sessions.
     */
    private suspend fun buildStylePrompt(haircut: Haircut): String {
        val state = _uiState.value
        val conversation = chatBot.buildReplyContext()
        val recentUserAsks = conversation.recentMessages
            .filter { it.sender == ChatSender.USER }
            .takeLast(MAX_STYLE_PROMPT_USER_TURNS)
            .joinToString(separator = "; ") { it.text }
        return buildString {
            append("The first attached image is a photo of the person to restyle. The second attached image is ")
            append("a reference photo of the exact \"${haircut.name}\" hairstyle (")
            append("${haircut.length.displayName.lowercase()} length, ${haircut.texture.displayName.lowercase()} texture) — ")
            append("apply that hairstyle's shape and cut to the person in the first image, ")
            append("keeping their face, skin tone, and background unchanged.")
            if (haircut.description.isNotBlank()) {
                append(" Style detail: ${haircut.description}")
            }
            state.scanResult?.let { append(" Their face shape is ${it.faceShape.displayName.lowercase()}.") }
            (state.desiredTreatment ?: haircut.treatment).takeIf { it != TreatmentPreference.NONE }?.let {
                append(" Include a ${it.displayName.lowercase()} treatment look.")
            }
            if (!conversation.summary.isNullOrBlank()) {
                append(" Earlier in the conversation: ${conversation.summary}")
            }
            if (recentUserAsks.isNotBlank()) {
                append(" Recent requests from the user: $recentUserAsks.")
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
/** How many of the most recent user chat turns get quoted into the image-generation prompt. */
private const val MAX_STYLE_PROMPT_USER_TURNS = 6
/** How many catalog haircuts get surfaced as candidates, pre- or post-confirmation. */
private const val MAX_SUGGESTIONS = 6

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
