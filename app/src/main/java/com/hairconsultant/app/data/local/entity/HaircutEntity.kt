package com.hairconsultant.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.hairconsultant.app.data.local.Converters

/** Offline cache of the haircut catalog ("filters" in the PostgreSQL/Firestore backend). */
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
    val description: String
)
