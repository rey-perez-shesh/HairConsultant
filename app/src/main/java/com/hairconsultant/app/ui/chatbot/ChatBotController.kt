package com.hairconsultant.app.ui.chatbot

import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.ChatSender
import com.hairconsultant.app.domain.model.Haircut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ChatBotUiState(
    val isOpen: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = ""
)

/**
 * Drives the floating AI-chatbot bottom sheet that follows the user on Home, Face Scan and
 * Image Upload. Each screen owns its own instance so the conversation can reference that
 * screen's scan/upload context (e.g. suggested haircuts once a face has been analyzed).
 */
class ChatBotController(private val onUserMessage: suspend (String) -> Unit = {}) {

    private val _state = MutableStateFlow(
        ChatBotUiState(
            messages = listOf(
                bot("Hi! I'm your AI hairstylist. Scan your face or upload a photo and I'll suggest cuts that fit you.")
            )
        )
    )
    val state: StateFlow<ChatBotUiState> = _state

    fun setOpen(open: Boolean) {
        _state.update { it.copy(isOpen = open) }
    }

    fun updateInput(text: String) {
        _state.update { it.copy(input = text) }
    }

    suspend fun sendCurrentInput() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(messages = it.messages + user(text), input = "") }
        onUserMessage(text)
    }

    /** A tap on one of a bot message's quick-reply chips behaves like typing + sending that reply. */
    suspend fun selectQuickReply(text: String) {
        _state.update { it.copy(messages = it.messages + user(text)) }
        onUserMessage(text)
    }

    fun pushBotMessage(text: String, haircutOptions: List<Haircut> = emptyList(), quickReplies: List<String> = emptyList()) {
        _state.update { it.copy(messages = it.messages + bot(text, haircutOptions, quickReplies)) }
    }

    fun pushUserMessage(text: String) {
        _state.update { it.copy(messages = it.messages + user(text)) }
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
}
