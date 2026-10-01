package com.hairconsultant.app.ui.facescan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hairconsultant.app.data.analysis.FaceAnalyzer
import com.hairconsultant.app.data.analysis.FaceLandmarkStore
import com.hairconsultant.app.data.analysis.NoFaceDetectedException
import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.recommendation.HairstyleRecommender
import com.hairconsultant.app.data.remote.gemini.ExtractedPreferences
import com.hairconsultant.app.data.remote.gemini.GeminiChatRepository
import com.hairconsultant.app.data.remote.gemini.describeForChatContext
import com.hairconsultant.app.data.remote.gemini.haircutsNamedIn
import com.hairconsultant.app.data.repository.ConsultationRepository
import com.hairconsultant.app.data.repository.UserRepository
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.domain.model.ConsultationSource
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.TreatmentPreference
import com.hairconsultant.app.ui.chatbot.ChatBotController
import com.hairconsultant.app.ui.chatbot.ChatBotUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class FaceScanStage { IDLE, ANALYZING, CONFIRM_RESULT, ASK_FIX, CONSULTING, RECOMMENDING, SUGGESTIONS }

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
    val triedOnHaircut: Haircut? = null,
    val tryOnHairColor: HairColorPreset = HairColorPreset.NATURAL
)

class FaceScanViewModel(
    private val faceAnalyzer: FaceAnalyzer,
    private val recommender: HairstyleRecommender,
    val landmarkStore: FaceLandmarkStore,
    private val chatRepository: GeminiChatRepository,
    val chatBot: ChatBotController,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val consultationRepository: ConsultationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaceScanUiState())
    val uiState: StateFlow<FaceScanUiState> = _uiState

    val chatState: StateFlow<ChatBotUiState> = chatBot.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ChatBotUiState()
    )

    /** Claims the shared chatbot's next reply whenever Face Scan becomes the active screen. */
    fun activateChat() {
        chatBot.setHandler { text -> handleFreeText(text) }
    }

    private var consultationId = UUID.randomUUID().toString()

    /** When the current scan started; everything from here on is this consultation's conversation. */
    private var consultationStartedAt = 0L

    fun startScan() {
        if (_uiState.value.stage == FaceScanStage.ANALYZING) return
        _uiState.update { it.copy(stage = FaceScanStage.ANALYZING) }
        chatBot.setOpen(true)
        consultationStartedAt = System.currentTimeMillis()
        chatBot.pushBotMessage("Analyzing your face and hair, hold still...")
        viewModelScope.launch {
            try {
                val result = faceAnalyzer.analyzeCameraFrame()
                _uiState.update { it.copy(stage = FaceScanStage.CONFIRM_RESULT, scanResult = result) }
                chatBot.pushBotMessage(
                    "I detected a ${result.faceShape.displayName} face shape" +
                        hairScanPhrase(result) +
                        ". Want to see hairstyles that fit you right away, or talk it through with me first? " +
                        "If I got anything wrong, tap \"$FIX_SCAN_LABEL\".",
                    quickReplies = listOf(CONFIRM_LABEL, CONSULT_LABEL, FIX_SCAN_LABEL)
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
            FaceScanStage.CONSULTING, FaceScanStage.SUGGESTIONS -> onConsultingReply(text)
            FaceScanStage.RECOMMENDING -> chatBot.pushBotMessage("Still thinking about your matches — one moment.")
            else -> respondFreeform(text)
        }
    }

    /**
     * The consultation loop: the user talks freely with the AI consultant for as long as they
     * like, and every tap of [CONFIRM_LABEL] runs [showHairstyles]. Anything said after
     * suggestions are shown reopens the consultation, so new or changed parameters get a fresh
     * round of thinking the next time [CONFIRM_LABEL] is tapped.
     */
    private suspend fun onConsultingReply(text: String) {
        if (text.equals(CONFIRM_LABEL, ignoreCase = true)) {
            showHairstyles()
        } else {
            _uiState.update { it.copy(stage = FaceScanStage.CONSULTING) }
            respondFreeform(text)
        }
    }

    /**
     * Free-form AI consultant reply, grounded on the *whole* catalog so the consultant can
     * discuss any style the app has — not just the ones closest to the scan, which would leave it
     * blind to e.g. long styles when a short-haired user asks for long hair. During the
     * consultation no pictures are attached: catalog images only appear once [showHairstyles] has
     * worked out the user's top matches, and every reply carries the [CONFIRM_LABEL] chip so it's
     * always one tap away. Outside a consultation (before any scan), the pictures shown are
     * exactly the styles the reply names.
     */
    private suspend fun respondFreeform(text: String) {
        val catalog = recommender.catalog()
        val context = buildConsultationContext(catalog)
        val conversation = chatBot.buildReplyContext()
        val consulting = _uiState.value.stage == FaceScanStage.CONSULTING
        val quickReplies = if (consulting) listOf(CONFIRM_LABEL) else emptyList()
        chatRepository.reply(conversation.recentMessages, text, context, conversation.summary)
            .onSuccess { reply ->
                val pictures = if (consulting) emptyList() else haircutsNamedIn(reply, catalog)
                chatBot.pushBotMessage(reply, haircutOptions = pictures, quickReplies = quickReplies)
            }
            .onFailure { error ->
                chatBot.pushBotMessage(
                    "I couldn't reach the AI consultant right now (${error.message}).",
                    quickReplies = quickReplies
                )
            }
    }

    /** Everything the app already knows for certain about this consultation, for the AI consultant to reason over. */
    private suspend fun buildConsultationContext(catalog: List<Haircut>): String {
        val state = _uiState.value
        val scan = state.scanResult
        return buildString {
            scan?.let { append("Confirmed face shape: ${it.faceShape.displayName}. ") }
            (state.desiredLength ?: scan?.hairLength)?.let { append("Hair length: ${it.displayName}. ") }
            (state.desiredTexture ?: scan?.hairTexture)?.let { append("Hair texture: ${it.displayName}. ") }
            state.desiredTreatment?.takeIf { it != TreatmentPreference.NONE }
                ?.let { append("Planned treatment: ${it.displayName}. ") }
            val gender = profileGender()
            gender.describeForChatContext()?.let { append("\n").append(it) }
            if (scan != null) {
                append(
                    "\nNo hairstyle pictures are shown with your replies; the user sees their top matches as " +
                        "pictures when they tap \"$CONFIRM_LABEL\". If they add or change preferences, you can " +
                        "mention that tapping it will update their picks."
                )
                val closest = recommender.shortlist(scan, currentParameters(), gender, CLOSEST_MATCHES_HINT)
                append(
                    "\nClosest catalog matches to the confirmed scan so far (a starting point only — anything in " +
                        "the full catalog below is fair game if it fits what the user asks for): " +
                        closest.joinToString(", ") { "\"${it.name}\"" } + "."
                )
            }
            if (state.suggestions.isNotEmpty()) {
                append("\nCurrently suggested to the user: ${state.suggestions.joinToString(", ") { "\"${it.name}\"" }}.")
            }
            append("\nHairstyle catalog (every style the app has):\n")
            append(catalog.describeForChatContext())
        }
    }

    /**
     * Right after the scan the user chooses: see hairstyles right away, consult first, or fix the
     * scanned features. Typing anything else starts the consultation with that message.
     */
    private suspend fun onResultConfirmationReply(text: String) {
        val typedShape = FaceShape.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) }
        when {
            text.equals(FIX_SCAN_LABEL, ignoreCase = true) || text.startsWith("No", ignoreCase = true) -> {
                _uiState.update { it.copy(stage = FaceScanStage.ASK_FIX, fixTarget = null) }
                chatBot.pushBotMessage(
                    "No problem — what would you like to fix?",
                    quickReplies = fixMenuQuickReplies(includeTexture = _uiState.value.scanResult?.hairLength != HairLength.BALD)
                )
            }
            text.equals(CONFIRM_LABEL, ignoreCase = true) -> {
                enterConsulting()
                showHairstyles()
            }
            text.equals(CONSULT_LABEL, ignoreCase = true) -> startConsulting()
            typedShape != null -> {
                _uiState.update { it.copy(scanResult = it.scanResult?.copy(faceShape = typedShape)) }
                startConsulting()
            }
            else -> {
                enterConsulting()
                respondFreeform(text)
            }
        }
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
                chatBot.pushBotMessage("Pick your face shape:", quickReplies = FaceShape.entries.map { it.displayName })
            }
            text.equals(FIX_HAIR_LENGTH_LABEL, ignoreCase = true) -> {
                _uiState.update { it.copy(fixTarget = ScanFixTarget.HAIR_LENGTH) }
                chatBot.pushBotMessage("Pick your hair length:", quickReplies = HairLength.entries.map { it.displayName })
            }
            text.equals(FIX_HAIR_TEXTURE_LABEL, ignoreCase = true) -> {
                _uiState.update { it.copy(fixTarget = ScanFixTarget.HAIR_TEXTURE) }
                chatBot.pushBotMessage("Pick your hair texture:", quickReplies = HairTexture.entries.map { it.displayName })
            }
            else -> chatBot.pushBotMessage(
                "Tap one of the options below, or choose Rescan to try again.",
                quickReplies = fixMenuQuickReplies()
            )
        }
    }

    /** A fixed scan leads into the consultation, per "fix the scanned features and be consulted first". */
    private fun onFixValueSelected(text: String) {
        when (_uiState.value.fixTarget) {
            ScanFixTarget.FACE_SHAPE -> {
                val shape = FaceShape.entries.firstOrNull { it.displayName.equals(text, ignoreCase = true) } ?: run {
                    chatBot.pushBotMessage("Pick a face shape from the list.", quickReplies = FaceShape.entries.map { it.displayName })
                    return
                }
                _uiState.update { it.copy(scanResult = it.scanResult?.copy(faceShape = shape), fixTarget = null) }
                startConsulting()
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
                startConsulting()
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
                        fixTarget = null
                    )
                }
                startConsulting()
            }
            null -> onFixCategorySelected(text)
        }
    }

    fun rescan() {
        consultationId = UUID.randomUUID().toString()
        _uiState.update { FaceScanUiState(stage = FaceScanStage.IDLE, afterRescan = true) }
        chatBot.pushBotMessage("Rescanning — hold still...")
        startScan()
    }

    private fun isBald(): Boolean =
        _uiState.value.scanResult?.hairLength == HairLength.BALD || _uiState.value.desiredLength == HairLength.BALD

    /** Moves into the consultation stage, seeding the wanted length/texture from the confirmed scan. */
    private fun enterConsulting() {
        val bald = isBald()
        _uiState.update {
            it.copy(
                stage = FaceScanStage.CONSULTING,
                afterRescan = false,
                desiredLength = if (bald) HairLength.BALD else it.desiredLength ?: it.scanResult?.hairLength,
                desiredTexture = if (bald) null else it.desiredTexture ?: it.scanResult?.hairTexture
            )
        }
    }

    /** Opens the free-form consultation, which lasts until the user taps [CONFIRM_LABEL]. */
    private fun startConsulting() {
        enterConsulting()
        chatBot.pushBotMessage(
            if (isBald()) {
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
     * Show My Hairstyles: compiles this whole consultation (every message since the scan), then
     * [HairstyleRecommender] narrows the catalog to the user's stated parameters, reasons over it
     * with the hairstyling knowledge base, and picks the top matches — shown with the catalog's
     * own images. Runs again from scratch on every tap, so new parameters always get fresh picks.
     */
    private suspend fun showHairstyles() {
        val result = _uiState.value.scanResult ?: return
        if (_uiState.value.stage == FaceScanStage.RECOMMENDING) return
        _uiState.update { it.copy(stage = FaceScanStage.RECOMMENDING) }
        chatBot.pushBotMessage("Let me think through everything we discussed and find your best matches in the catalog...")
        try {
            val recommendation = recommender.recommend(
                scan = result,
                known = currentParameters(),
                gender = profileGender(),
                consultation = chatBot.messagesSince(consultationStartedAt).takeLast(MAX_CONSULTATION_MESSAGES),
                conversationSummary = chatBot.buildReplyContext().summary
            )
            val parameters = recommendation.parameters
            _uiState.update {
                it.copy(
                    stage = FaceScanStage.SUGGESTIONS,
                    suggestions = recommendation.haircuts,
                    desiredLength = parameters.length,
                    desiredTexture = parameters.texture,
                    desiredTreatment = parameters.treatment
                )
            }
            val intro = recommendation.explanation ?: if (parameters.length == HairLength.BALD) {
                "Since you don't have hair to style, you can try a wig. " +
                    "Here are looks that fit your ${result.faceShape.displayName} face — tap one to try it on."
            } else {
                "Based on your ${result.faceShape.displayName} face shape, here are some cuts I'd suggest. Tap one to try it on!"
            }
            chatBot.pushBotMessage(intro, haircutOptions = recommendation.haircuts)
            persistConsultation(selectedHaircut = null)
            persistPreferences()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update { it.copy(stage = FaceScanStage.CONSULTING) }
            chatBot.pushBotMessage(
                "I couldn't put your matches together (${error.message}). Tap \"$CONFIRM_LABEL\" to try again.",
                quickReplies = listOf(CONFIRM_LABEL)
            )
        }
    }

    private fun currentParameters(): ExtractedPreferences {
        val state = _uiState.value
        return ExtractedPreferences(
            length = state.desiredLength ?: state.scanResult?.hairLength,
            texture = state.desiredTexture ?: state.scanResult?.hairTexture,
            treatment = state.desiredTreatment
        )
    }

    private suspend fun profileGender(): Gender? {
        val uid = authRepository.currentUser.value?.uid ?: return null
        return userRepository.get(uid)?.gender
    }

    fun onHaircutTryOn(haircut: Haircut) {
        chatBot.setOpen(false)
        _uiState.update { it.copy(triedOnHaircut = haircut) }
        persistConsultation(selectedHaircut = haircut)
    }

    fun onTryOnHairColor(color: HairColorPreset) {
        _uiState.update { it.copy(tryOnHairColor = color) }
    }

    fun clearTryOn() {
        _uiState.update { it.copy(triedOnHaircut = null, tryOnHairColor = HairColorPreset.NATURAL) }
    }

    /** Saves this session as a consultation (Room now, Firestore in the background) once a scan has a result. */
    private fun persistConsultation(selectedHaircut: Haircut?) {
        val uid = authRepository.currentUser.value?.uid ?: return
        val result = _uiState.value.scanResult ?: return
        viewModelScope.launch {
            consultationRepository.save(
                Consultation(
                    id = consultationId,
                    userId = uid,
                    source = ConsultationSource.FACE_SCAN,
                    scanResult = result,
                    selectedHaircut = selectedHaircut,
                    sourceImageUrl = null,
                    resultImageUrl = null,
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
            val current = userRepository.get(uid) ?: return@launch
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
private const val CONSULT_LABEL = "Consult with me first"
private const val FIX_SCAN_LABEL = "Fix scan results"
/** How many scan-closest styles are pointed out to the consultant as a starting point. */
private const val CLOSEST_MATCHES_HINT = 8
/** Cap on how much of one consultation gets compiled for Show My Hairstyles. */
private const val MAX_CONSULTATION_MESSAGES = 80

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
