package com.hairconsultant.app.data.repository

import android.util.Log
import com.hairconsultant.app.data.local.dao.ChatMessageDao
import com.hairconsultant.app.data.local.dao.ChatSummaryDao
import com.hairconsultant.app.data.local.dao.HaircutDao
import com.hairconsultant.app.data.local.entity.ChatMessageEntity
import com.hairconsultant.app.data.local.entity.ChatSummaryEntity
import com.hairconsultant.app.data.local.entity.HaircutEntity
import com.hairconsultant.app.data.remote.firebase.ChatHistoryRemoteRepository
import com.hairconsultant.app.data.remote.gemini.GeminiChatRepository
import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.ChatSender
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Per-user AI-chatbot conversation history, kept offline-first in Room and mirrored to Firestore
 * (see [ChatHistoryRemoteRepository]) the same way [ConsultationRepository] handles consultations.
 * [com.hairconsultant.app.ui.chatbot.ChatBotController] is the sole writer: it appends every
 * message as it's sent, and reloads a user's history from here whenever the signed-in user changes.
 */
interface ChatHistoryRepository {
    fun observeHistory(userId: String): Flow<List<ChatMessage>>
    suspend fun append(userId: String, message: ChatMessage)
    suspend fun clearHistory(userId: String)

    /**
     * Backfills Room from Firestore the first time a user's local history is empty (fresh
     * install, new device, or a Room wipe from a destructive migration) so chat history survives
     * more than just this install. A no-op once Room already has rows for [userId].
     */
    suspend fun restoreFromRemoteIfEmpty(userId: String)

    /**
     * What to actually send Gemini for this user's next reply: a bounded recent-messages window
     * plus a rolling summary standing in for everything older than it, rather than [fullHistory]
     * replayed verbatim — so the request size stops growing once a conversation passes the window,
     * no matter how long-lived it gets. Folds any newly-aged-out messages into the stored summary
     * (one summarization call) only when the window has actually advanced past what was already
     * summarized; otherwise reuses the summary already on disk.
     */
    suspend fun conversationContextForReply(userId: String, fullHistory: List<ChatMessage>): ConversationContext
}

/**
 * @param summary Durable facts folded out of everything older than [recentMessages]; null if the
 * conversation hasn't grown past the window yet, so there's nothing to summarize.
 * @param recentMessages The tail of the conversation to replay verbatim, in chronological order.
 */
data class ConversationContext(val summary: String?, val recentMessages: List<ChatMessage>)

class ChatHistoryRepositoryImpl(
    private val chatMessageDao: ChatMessageDao,
    private val chatSummaryDao: ChatSummaryDao,
    private val haircutDao: HaircutDao,
    private val remote: ChatHistoryRemoteRepository,
    private val geminiChatRepository: GeminiChatRepository
) : ChatHistoryRepository {

    override fun observeHistory(userId: String): Flow<List<ChatMessage>> =
        combine(chatMessageDao.observeHistory(userId), haircutDao.observeAll()) { messages, haircuts ->
            val haircutsById = haircuts.mapNotNull { it.toHaircutOrNull() }.associateBy { it.id }
            messages.map { it.toDomain(haircutsById) }
        }

    override suspend fun append(userId: String, message: ChatMessage) {
        chatMessageDao.insert(message.toEntity(userId))
        runCatching { remote.append(userId, message) }
            .onFailure { Log.w(TAG, "Couldn't save chat message ${message.id} to Firestore", it) }
    }

    override suspend fun clearHistory(userId: String) {
        chatMessageDao.clearHistory(userId)
        chatSummaryDao.clear(userId)
        runCatching { remote.clearHistory(userId) }
            .onFailure { Log.w(TAG, "Couldn't clear chat history in Firestore", it) }
    }

    override suspend fun restoreFromRemoteIfEmpty(userId: String) {
        if (chatMessageDao.countForUser(userId) > 0) return
        val remoteHistory = runCatching { remote.fetchHistory(userId) }
            .onFailure { Log.w(TAG, "Couldn't restore chat history from Firestore", it) }
            .getOrNull() ?: return
        remoteHistory.forEach { restored ->
            chatMessageDao.insert(restored.message.toEntity(userId, restored.haircutOptionIds))
        }
        // The summary itself isn't mirrored to Firestore (it's derived, regenerable from the raw
        // history above) — it'll be rebuilt from scratch the next time the window advances.
    }

    override suspend fun conversationContextForReply(userId: String, fullHistory: List<ChatMessage>): ConversationContext {
        val recentMessages = fullHistory.takeLast(RECENT_WINDOW_SIZE)
        val older = fullHistory.dropLast(RECENT_WINDOW_SIZE)
        if (older.isEmpty()) {
            // Conversation hasn't outgrown the window yet — nothing to summarize.
            return ConversationContext(summary = null, recentMessages = recentMessages)
        }

        val stored = chatSummaryDao.get(userId)
        // dropLast/drop clamp instead of throwing, so a summarizedThroughCount left over from a
        // cleared/shorter history just re-summarizes everything rather than crashing.
        val newlyAgedOut = older.drop(stored?.summarizedThroughCount ?: 0)
        if (newlyAgedOut.isEmpty()) {
            return ConversationContext(summary = stored?.summaryText, recentMessages = recentMessages)
        }

        val updatedSummary = geminiChatRepository.summarizeConversation(stored?.summaryText, newlyAgedOut).getOrNull()
        if (updatedSummary != null) {
            chatSummaryDao.upsert(
                ChatSummaryEntity(
                    userId = userId,
                    summaryText = updatedSummary,
                    summarizedThroughCount = older.size,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
        // On failure, keep the stale-but-still-useful stored summary rather than blocking this
        // reply on it — the un-folded messages just get retried on the next call.
        return ConversationContext(summary = updatedSummary ?: stored?.summaryText, recentMessages = recentMessages)
    }

    private companion object {
        const val TAG = "ChatHistoryRepository"

        /** How many of the most recent messages get replayed verbatim to Gemini on every reply. */
        const val RECENT_WINDOW_SIZE = 20
    }
}

private fun HaircutEntity.toHaircutOrNull() =
    runCatching {
        Haircut(
            id = id,
            name = name,
            imageUrl = imageUrl,
            length = HairLength.valueOf(length),
            texture = HairTexture.valueOf(texture),
            recommendedFaceShapes = recommendedFaceShapes.mapNotNull { runCatching { FaceShape.valueOf(it) }.getOrNull() },
            description = description
        )
    }.getOrNull()

private fun ChatMessageEntity.toDomain(haircutsById: Map<String, Haircut>) = ChatMessage(
    id = id,
    sender = runCatching { ChatSender.valueOf(sender) }.getOrDefault(ChatSender.BOT),
    text = text,
    haircutOptions = haircutOptionIds.mapNotNull { haircutsById[it] },
    quickReplies = quickReplies,
    timestampEpochMillis = timestampEpochMillis
)

private fun ChatMessage.toEntity(userId: String, haircutOptionIds: List<String> = haircutOptions.map { it.id }) =
    ChatMessageEntity(
        id = id,
        userId = userId,
        sender = sender.name,
        text = text,
        haircutOptionIds = haircutOptionIds,
        quickReplies = quickReplies,
        timestampEpochMillis = timestampEpochMillis
    )
