package com.hairconsultant.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved face-scan / image-upload session, kept for the Profile screen's History tab. */
@Entity(tableName = "consultations")
data class ConsultationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val source: String,
    val faceShape: String,
    val hairLength: String,
    val hairTexture: String,
    val selectedHaircutId: String?,
    val sourceImageUrl: String?,
    val resultImageUrl: String?,
    val isFavorite: Boolean,
    val createdAtEpochMillis: Long
)
