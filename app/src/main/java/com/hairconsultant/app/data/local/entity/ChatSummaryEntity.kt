package com.hairconsultant.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One user's rolling summary of everything folded out of the recent-messages window sent to
 * Gemini (see [com.hairconsultant.app.data.repository.ChatHistoryRepositoryImpl.conversationContextForReply]).
 * [summarizedThroughCount] is how many of that user's chat messages (oldest-first) are already
 * folded into [summaryText], so only messages appended after it need summarizing next time.
 */
@Entity(tableName = "chat_summaries")
data class ChatSummaryEntity(
    @PrimaryKey val userId: String,
    val summaryText: String,
    val summarizedThroughCount: Int,
    val updatedAtEpochMillis: Long
)
