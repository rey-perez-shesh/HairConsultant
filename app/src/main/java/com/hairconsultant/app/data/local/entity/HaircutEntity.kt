package com.hairconsultant.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.hairconsultant.app.data.local.Converters

/** Offline cache of the Firestore "haircuts" catalog (managed from the admin dashboard). */
@Entity(tableName = "haircuts")
@TypeConverters(Converters::class)
data class HaircutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String,
    val length: String,
    val texture: String,
    val recommendedFaceShapes: List<String>,
    val genderStyle: String,
    val treatment: String,
    val description: String,
    val sortOrder: Int = 0,
    val arType: String = "AUTO",
    val arAssetUrl: String? = null,
    val arAssetVersion: Int = 0,
    val glbScaleMul: Float = 0.7f,
    val glbOffsetX: Float = 0f,
    val glbOffsetY: Float = -0.03f,
    val glbOffsetZ: Float = 0.02f,
    val glbYawOffset: Float = 0f
)
