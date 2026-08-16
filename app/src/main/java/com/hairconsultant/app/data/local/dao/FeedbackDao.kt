package com.hairconsultant.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hairconsultant.app.data.local.entity.FeedbackEntity

@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: FeedbackEntity)

    @Query("SELECT * FROM feedback WHERE isSynced = 0")
    suspend fun getUnsynced(): List<FeedbackEntity>

    @Query("UPDATE feedback SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
