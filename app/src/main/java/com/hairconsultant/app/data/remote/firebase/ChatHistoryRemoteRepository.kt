package com.hairconsultant.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.ChatSender
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed sync of the AI chatbot's conversation, so a user's chat history survives a
 * reinstall/new device the same way [ConsultationRemoteRepository] does for consultation history.
 * Room ([com.hairconsultant.app.data.local.dao.ChatMessageDao]) is the source of truth this app
 * reads from; this is a mirror written to on every message and pulled back only to repopulate an
 * empty local cache (see [com.hairconsultant.app.data.repository.ChatHistoryRepositoryImpl]).
 */
interface ChatHistoryRemoteRepository {
    suspend fun append(userId: String, message: ChatMessage)
    suspend fun fetchHistory(userId: String): List<RemoteChatMessage>
    suspend fun clearHistory(userId: String)
}

/**
 * A restored message plus the catalog haircut ids it was showing, since [ChatMessage.haircutOptions]
 * needs full [com.hairconsultant.app.domain.model.Haircut] objects that only the caller (which has
 * the local haircut catalog on hand) can re-hydrate from these ids.
 */
data class RemoteChatMessage(val message: ChatMessage, val haircutOptionIds: List<String>)

class FirestoreChatHistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChatHistoryRemoteRepository {

    private val collection get() = firestore.collection("chatMessages")

    override suspend fun append(userId: String, message: ChatMessage) {
        collection.document(message.id).set(
            mapOf(
                "userId" to userId,
                "sender" to message.sender.name,
                "text" to message.text,
                "haircutOptionIds" to message.haircutOptions.map { it.id },
                "quickReplies" to message.quickReplies,
                "timestampEpochMillis" to message.timestampEpochMillis
            )
        ).await()
    }

    override suspend fun fetchHistory(userId: String): List<RemoteChatMessage> {
        val snapshot = collection.whereEqualTo("userId", userId).get().await()
        return snapshot.documents.map { doc ->
            RemoteChatMessage(
                message = ChatMessage(
                    id = doc.id,
                    sender = runCatching { ChatSender.valueOf(doc.getString("sender").orEmpty()) }
                        .getOrDefault(ChatSender.BOT),
                    text = doc.getString("text").orEmpty(),
                    quickReplies = (doc.get("quickReplies") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                    timestampEpochMillis = doc.getLong("timestampEpochMillis") ?: 0L
                ),
                haircutOptionIds = (doc.get("haircutOptionIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            )
        }.sortedBy { it.message.timestampEpochMillis }
    }

    override suspend fun clearHistory(userId: String) {
        val snapshot = collection.whereEqualTo("userId", userId).get().await()
        if (snapshot.isEmpty) return
        val batch = firestore.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}

/** In-memory stand-in used until a Firebase project is attached. */
class MockChatHistoryRemoteRepository : ChatHistoryRemoteRepository {
    private val store = mutableMapOf<String, ChatMessage>()
    private val ownerByMessageId = mutableMapOf<String, String>()

    override suspend fun append(userId: String, message: ChatMessage) {
        store[message.id] = message
        ownerByMessageId[message.id] = userId
    }

    override suspend fun fetchHistory(userId: String): List<RemoteChatMessage> =
        store.values.filter { ownerByMessageId[it.id] == userId }
            .sortedBy { it.timestampEpochMillis }
            .map { RemoteChatMessage(message = it, haircutOptionIds = it.haircutOptions.map { h -> h.id }) }

    override suspend fun clearHistory(userId: String) {
        val ids = ownerByMessageId.filterValues { it == userId }.keys
        ids.forEach { store.remove(it); ownerByMessageId.remove(it) }
    }
}
