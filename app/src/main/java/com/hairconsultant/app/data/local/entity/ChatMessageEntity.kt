package com.hairconsultant.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One AI-chatbot message, kept per-user so the conversation survives app restarts and logins. */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sender: String,
    val text: String,
    /** IDs into the haircuts table; hydrated back into [com.hairconsultant.app.domain.model.Haircut] on read. */
    val haircutOptionIds: List<String>,
    val quickReplies: List<String>,
    val timestampEpochMillis: Long
)
