package com.hairconsultant.app.domain.model

enum class Gender(val displayName: String) {
    FEMALE("Female"),
    MALE("Male"),
    NON_BINARY("Non-binary"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}

enum class HairLength(val displayName: String) {
    SHORT("Short"),
    MEDIUM("Medium"),
    LONG("Long")
}

enum class HairTexture(val displayName: String) {
    STRAIGHT("Straight"),
    WAVY("Wavy"),
    CURLY("Curly"),
    COILY("Coily")
}

enum class FaceShape(val displayName: String) {
    OVAL("Oval"),
    ROUND("Round"),
    SQUARE("Square"),
    HEART("Heart"),
    LONG("Long"),
    DIAMOND("Diamond"),
    TRIANGLE("Triangle")
}

/** Optional chemical treatment the user wants their next style to involve. */
enum class TreatmentPreference(val displayName: String) {
    NONE("No treatment"),
    REBOND("Rebonding"),
    PERM("Perming")
}

enum class ConsultationSource {
    FACE_SCAN,
    IMAGE_UPLOAD
}

enum class ChatSender {
    USER,
    BOT
}
