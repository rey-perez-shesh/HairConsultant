package com.hairconsultant.app.domain.model

/** One saved face-scan / image-upload session, kept in history and available for favoriting. */
data class Consultation(
    val id: String,
    val userId: String,
    val source: ConsultationSource,
    val scanResult: ScanResult,
    val selectedHaircut: Haircut?,
    val sourceImageUrl: String?,
    val resultImageUrl: String?,
    val isFavorite: Boolean = false,
    val createdAtEpochMillis: Long = 0L
)

data class ChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
    val haircutOptions: List<Haircut> = emptyList(),
    /** Tappable quick-reply labels (e.g. face-shape confirmation, length/treatment choices). */
    val quickReplies: List<String> = emptyList(),
    val timestampEpochMillis: Long = 0L
)

/** 1-5 satisfaction rating plus optional free-form feedback, sent to the backend for analytics. */
data class FeedbackReport(
    val id: String,
    val userId: String,
    val rating: Int,
    val comment: String,
    val createdAtEpochMillis: Long = 0L
)
