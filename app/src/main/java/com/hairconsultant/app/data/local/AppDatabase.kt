package com.hairconsultant.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hairconsultant.app.data.local.dao.ConsultationDao
import com.hairconsultant.app.data.local.dao.FeedbackDao
import com.hairconsultant.app.data.local.dao.HaircutDao
import com.hairconsultant.app.data.local.dao.UserDao
import com.hairconsultant.app.data.local.entity.ConsultationEntity
import com.hairconsultant.app.data.local.entity.FeedbackEntity
import com.hairconsultant.app.data.local.entity.HaircutEntity
import com.hairconsultant.app.data.local.entity.UserEntity

/**
 * Local/offline store. Acts as a cache in front of Firestore (profile, consultations) and the
 * backend-API-fronted PostgreSQL catalog (haircuts), so the UI keeps working without a network.
 */
@Database(
    entities = [UserEntity::class, HaircutEntity::class, ConsultationEntity::class, FeedbackEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun haircutDao(): HaircutDao
    abstract fun consultationDao(): ConsultationDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        const val DATABASE_NAME = "hair_consultant.db"
    }
}
