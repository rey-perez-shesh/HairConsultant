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

/**
 * A saved look from the Color Studio: "look-color-<shadeId>-<uuid>".
 * shortcut: the shade rides in the id because a consultation has no field for it; add a column
 * once a color look needs more than its shade's name.
 */
const val COLOR_LOOK_ID_PREFIX = SAVED_LOOK_ID_PREFIX + "color-"

/** The shade id of a color look, or null for any other consultation. Shade ids never contain '-'. */
val Consultation.colorLookShadeId: String?
    get() = id.takeIf { it.startsWith(COLOR_LOOK_ID_PREFIX) }?.removePrefix(COLOR_LOOK_ID_PREFIX)?.substringBefore('-')

/**
 * A favorited AR try-on filter: "look-ar-<userId>-<haircutId>", one per user and style, so
 * hearting twice can't duplicate it. It has no picture; the style is its selected haircut.
 * shortcut: rides the saved-looks rows (and their Firestore collection) so it needs no table,
 * which would wipe local data under the destructive migration; give it one if it grows fields.
 */
const val AR_FILTER_ID_PREFIX = SAVED_LOOK_ID_PREFIX + "ar-"

fun arFilterFavoriteId(userId: String, haircutId: String): String = "$AR_FILTER_ID_PREFIX$userId-$haircutId"

val Consultation.isArFilter: Boolean get() = id.startsWith(AR_FILTER_ID_PREFIX)

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
