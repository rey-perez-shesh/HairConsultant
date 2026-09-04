package com.hairconsultant.app.domain.model

enum class Gender(val displayName: String) {
    FEMALE("Female"),
    MALE("Male"),
    NON_BINARY("Non-binary"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}

enum class HairLength(val displayName: String) {
    BALD("Bald"),
    SHORT("Short"),
    MEDIUM("Medium"),
    LONG("Long")
}

enum class HairTexture(val displayName: String) {
    STRAIGHT("Straight"),
    WAVY("Wavy"),
    CURLY("Curly")
}

enum class HairColor(val displayName: String) {
    BLACK("Black"),
    BROWN("Brown"),
    BLONDE("Blonde"),
    RED("Red"),
    GRAY("Gray"),
    OTHER("Other")
}

enum class FaceShape(val displayName: String) {
    OVAL("Oval"),
    ROUND("Round"),
    SQUARE("Square"),
    HEART("Heart"),
    DIAMOND("Diamond")
}

/** Who a catalog haircut is styled for, independent of the wearer's own [Gender]. */
enum class HaircutGenderStyle(val displayName: String) {
    MASCULINE("Masculine"),
    FEMININE("Feminine"),
    UNISEX("Unisex")
}

/** Home screen's default catalog filter for a signed-in user's [Gender]: their matching styles plus unisex. */
fun Gender.matchingHaircutStyles(): Set<HaircutGenderStyle> = when (this) {
    Gender.MALE -> setOf(HaircutGenderStyle.MASCULINE, HaircutGenderStyle.UNISEX)
    Gender.FEMALE -> setOf(HaircutGenderStyle.FEMININE, HaircutGenderStyle.UNISEX)
    Gender.NON_BINARY, Gender.PREFER_NOT_TO_SAY -> setOf(HaircutGenderStyle.UNISEX)
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
