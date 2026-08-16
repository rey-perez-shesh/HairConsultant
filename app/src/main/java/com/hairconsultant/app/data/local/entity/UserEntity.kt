package com.hairconsultant.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Offline cache of the signed-in user's profile, mirrored from Firestore. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val username: String,
    val birthdayEpochDay: Long,
    val gender: String,
    val photoUrl: String?,
    val createdAtEpochMillis: Long
)
