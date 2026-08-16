package com.hairconsultant.app.data.remote.api.dto

import kotlinx.serialization.Serializable

/** Wire format for the haircut catalog served by the backend API from PostgreSQL. */
@Serializable
data class HaircutDto(
    val id: String,
    val name: String,
    val imageUrl: String,
    val length: String,
    val texture: String,
    val recommendedFaceShapes: List<String>,
    val treatment: String,
    val description: String
)
