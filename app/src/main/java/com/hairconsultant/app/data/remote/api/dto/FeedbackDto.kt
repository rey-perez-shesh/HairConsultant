package com.hairconsultant.app.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackDto(
    val userId: String,
    val rating: Int,
    val comment: String,
    val createdAtEpochMillis: Long
)

/** Structured analytics/consultation events forwarded to PostgreSQL for research purposes. */
@Serializable
data class AnalyticsEventDto(
    val userId: String,
    val eventType: String,
    val faceShape: String? = null,
    val hairLength: String? = null,
    val hairTexture: String? = null,
    val selectedHaircutId: String? = null,
    val createdAtEpochMillis: Long
)
