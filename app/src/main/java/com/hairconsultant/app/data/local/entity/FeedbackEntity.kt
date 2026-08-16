package com.hairconsultant.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Queued satisfaction rating / written feedback, synced to the backend analytics store. */
@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val rating: Int,
    val comment: String,
    val isSynced: Boolean = false,
    val createdAtEpochMillis: Long
)
