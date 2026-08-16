package com.hairconsultant.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hairconsultant.app.data.local.entity.HaircutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HaircutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(haircuts: List<HaircutEntity>)

    @Query("SELECT * FROM haircuts")
    fun observeAll(): Flow<List<HaircutEntity>>

    @Query("SELECT * FROM haircuts WHERE length = :length AND texture = :texture")
    fun observeByCluster(length: String, texture: String): Flow<List<HaircutEntity>>

    @Query("DELETE FROM haircuts")
    suspend fun clear()
}
