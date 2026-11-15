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
) {
    /** One generated preview the user kept to compare, rather than a whole scan/upload session. */
    val isSavedLook: Boolean get() = id.startsWith(SAVED_LOOK_ID_PREFIX)
}

/**
 * Ids starting with this are saved looks: a single generated preview stored as a favorited
 * consultation of its own, so Favorites can hold several looks from one photo side by side.
 * shortcut: told apart by id prefix so saved looks need no table or column of their own; give
 * them one if they ever carry fields a consultation doesn't.
 */
const val SAVED_LOOK_ID_PREFIX = "look-"

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
