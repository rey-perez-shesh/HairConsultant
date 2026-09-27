package com.hairconsultant.app.ui.chatbot

import com.hairconsultant.app.data.remote.firebase.AuthRepository
import com.hairconsultant.app.data.repository.ChatHistoryRepository
import com.hairconsultant.app.data.repository.ConversationContext
import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.ChatSender
import com.hairconsultant.app.domain.model.Haircut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatBotUiState(
    val isOpen: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = ""
)

/**
 * Drives the single floating AI-chatbot bottom sheet shared across Home, Face Scan and Image
 * Upload (one instance, owned by [com.hairconsultant.app.di.AppContainer], injected into every
 * screen's ViewModel) so it's the same ongoing conversation no matter which tab it's opened
 * from. Each screen's ViewModel calls [setHandler] when it becomes active so its own free-text
 * logic (which can reference that screen's scan/upload context) handles the next reply.
 *
 * The conversation itself is per-user: every message is persisted to [chatHistoryRepository]
 * (Room now, Firestore in the background) tagged with the signed-in user's id, and [state] is
 * reloaded from that history whenever [authRepository]'s current user changes — so logging out
 * and a different user logging in on the same device swaps to *their* history instead of leaking
 * the previous session's conversation, and a returning user's chat picks up where they left off
 * after the app is killed and reopened.
 */
class ChatBotController(
    private val authRepository: AuthRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val controllerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private var onUserMessage: suspend (String) -> Unit = {}

    /** The user id new messages get persisted under; null while signed out (persistence is skipped). */
    private var activeUserId: String? = null

    private val _state = MutableStateFlow(ChatBotUiState(messages = listOf(bot(GREETING))))
    val state: StateFlow<ChatBotUiState> = _state

    init {
        controllerScope.launch {
            authRepository.currentUser.collectLatest { user -> loadHistoryFor(user?.uid) }
        }
    }

    private suspend fun loadHistoryFor(userId: String?) {
        activeUserId = userId
        if (userId == null) {
            _state.update { ChatBotUiState(messages = listOf(bot(GREETING))) }
            return
        }
        runCatching { chatHistoryRepository.restoreFromRemoteIfEmpty(userId) }
        val history = chatHistoryRepository.observeHistory(userId).first()
        _state.update { ChatBotUiState(messages = history.ifEmpty { listOf(bot(GREETING)) }) }
    }

    fun setHandler(handler: suspend (String) -> Unit) {
        onUserMessage = handler
    }

    /**
     * What the caller should hand [com.hairconsultant.app.data.remote.gemini.GeminiChatRepository.reply]
     * for the next turn: a bounded recent-messages window plus a rolling summary of everything
     * older than it, rather than the full (potentially long-lived) conversation — see
     * [ChatHistoryRepository.conversationContextForReply]. Falls back to the raw in-memory
     * messages, unsummarized, while signed out (nothing is persisted to key a summary off then).
     */
    suspend fun buildReplyContext(): ConversationContext {
        val userId = activeUserId
        val history = _state.value.messages
        return if (userId != null) {
            chatHistoryRepository.conversationContextForReply(userId, history)
        } else {
            ConversationContext(summary = null, recentMessages = history)
        }
    }

    fun setOpen(open: Boolean) {
        _state.update { it.copy(isOpen = open) }
    }

    fun updateInput(text: String) {
        _state.update { it.copy(input = text) }
    }

    suspend fun sendCurrentInput() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        val message = user(text)
        _state.update { it.copy(messages = it.messages + message, input = "") }
        persist(message)
        onUserMessage(text)
    }

    /** A tap on one of a bot message's quick-reply chips behaves like typing + sending that reply. */
    suspend fun selectQuickReply(text: String) {
        val message = user(text)
        _state.update { it.copy(messages = it.messages + message) }
        persist(message)
        onUserMessage(text)
    }

    fun pushBotMessage(text: String, haircutOptions: List<Haircut> = emptyList(), quickReplies: List<String> = emptyList()) {
        val message = bot(text, haircutOptions, quickReplies)
        _state.update { it.copy(messages = it.messages + message) }
        persist(message)
    }

    fun pushUserMessage(text: String) {
        val message = user(text)
        _state.update { it.copy(messages = it.messages + message) }
        persist(message)
    }

    /** Fire-and-forget: never block the UI-thread state update on a Room write/Firestore round trip. */
    private fun persist(message: ChatMessage) {
        val userId = activeUserId ?: return
        controllerScope.launch { runCatching { chatHistoryRepository.append(userId, message) } }
    }

    private fun user(text: String) = ChatMessage(
        id = UUID.randomUUID().toString(),
        sender = ChatSender.USER,
        text = text,
        timestampEpochMillis = System.currentTimeMillis()
    )

    private fun bot(text: String, haircutOptions: List<Haircut> = emptyList(), quickReplies: List<String> = emptyList()) = ChatMessage(
        id = UUID.randomUUID().toString(),
        sender = ChatSender.BOT,
        text = text,
        haircutOptions = haircutOptions,
        quickReplies = quickReplies,
        timestampEpochMillis = System.currentTimeMillis()
    )

    private companion object {
        const val GREETING = "Hi! I'm your AI hairstylist. Scan your face or upload a photo and I'll suggest cuts that fit you."
    }
}
