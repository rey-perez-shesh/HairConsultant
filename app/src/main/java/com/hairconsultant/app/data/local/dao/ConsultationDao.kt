package com.hairconsultant.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hairconsultant.app.data.local.entity.ConsultationEntity
import com.hairconsultant.app.domain.model.SAVED_LOOK_ID_PREFIX
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Upsert
    suspend fun upsert(consultation: ConsultationEntity)

    /** Sessions only: saved looks live in the same table but belong to Favorites, not History. */
    @Query(
        "SELECT * FROM consultations WHERE userId = :userId AND id NOT LIKE '$SAVED_LOOK_ID_PREFIX%' " +
            "ORDER BY createdAtEpochMillis DESC"
    )
    fun observeHistory(userId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE userId = :userId AND isFavorite = 1 ORDER BY createdAtEpochMillis DESC")
    fun observeFavorites(userId: String): Flow<List<ConsultationEntity>>

    @Query("UPDATE consultations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("SELECT * FROM consultations WHERE userId = :userId AND id LIKE '$SAVED_LOOK_ID_PREFIX%'")
    suspend fun savedLooks(userId: String): List<ConsultationEntity>

    @Query("DELETE FROM consultations WHERE id = :id")
    suspend fun delete(id: String)

    /** Leaves saved looks alone: the user kept those on purpose, and they aren't shown in History. */
    @Query("DELETE FROM consultations WHERE userId = :userId AND id NOT LIKE '$SAVED_LOOK_ID_PREFIX%'")
    suspend fun clearHistory(userId: String)

    @Query("DELETE FROM consultations WHERE userId = :userId AND isFavorite = 1")
    suspend fun clearFavorites(userId: String)
}
