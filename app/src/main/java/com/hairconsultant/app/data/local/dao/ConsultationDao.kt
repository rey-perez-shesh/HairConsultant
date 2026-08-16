package com.hairconsultant.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hairconsultant.app.data.local.entity.ConsultationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Upsert
    suspend fun upsert(consultation: ConsultationEntity)

    @Query("SELECT * FROM consultations WHERE userId = :userId ORDER BY createdAtEpochMillis DESC")
    fun observeHistory(userId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE userId = :userId AND isFavorite = 1 ORDER BY createdAtEpochMillis DESC")
    fun observeFavorites(userId: String): Flow<List<ConsultationEntity>>

    @Query("UPDATE consultations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM consultations WHERE userId = :userId")
    suspend fun clearHistory(userId: String)

    @Query("DELETE FROM consultations WHERE userId = :userId AND isFavorite = 1")
    suspend fun clearFavorites(userId: String)
}
