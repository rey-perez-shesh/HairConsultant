package com.hairconsultant.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hairconsultant.app.data.local.entity.ChatSummaryEntity

@Dao
interface ChatSummaryDao {
    @Query("SELECT * FROM chat_summaries WHERE userId = :userId")
    suspend fun get(userId: String): ChatSummaryEntity?

    @Upsert
    suspend fun upsert(summary: ChatSummaryEntity)

    @Query("DELETE FROM chat_summaries WHERE userId = :userId")
    suspend fun clear(userId: String)
}
