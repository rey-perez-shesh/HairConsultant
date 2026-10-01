package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.matchingHaircutStyles

/**
 * The chat context line telling the model which catalog styles suit the user's profile gender,
 * or null when gender shouldn't narrow anything (non-binary, prefer not to say, or unknown).
 * Without it the model sees each style's Masculine/Feminine/Unisex tag but not who it's talking
 * to, so it could suggest a women's cut to a man who never mentioned his gender in chat.
 */
fun Gender?.describeForChatContext(): String? = when (this) {
    Gender.MALE, Gender.FEMALE -> {
        val styles = matchingHaircutStyles().joinToString(" or ") { it.displayName }
        "User's gender (from their profile): $displayName. Recommend only $styles styles from the catalog " +
            "unless the user explicitly asks about a style tagged for another gender."
    }
    else -> null
}

/**
 * Formats catalog haircuts as grounding context for [GeminiChatRepository.reply]. Chat replies
 * are handed the *whole* catalog through this, never a first-N slice of it: the catalog is
 * ordered row by row (Short & Straight first), so any slice only ever showed the model the first
 * row or two, and the model is told it may only recommend listed styles.
 */
fun List<Haircut>.describeForChatContext(): String =
    if (isEmpty()) {
        "No haircuts available."
    } else {
        joinToString(separator = "\n") { haircut ->
            "- \"${haircut.name}\" (${haircut.length.displayName}, ${haircut.texture.displayName} hair; " +
                "${haircut.genderStyle.displayName}; " +
                "suits ${haircut.recommendedFaceShapes.joinToString(", ") { it.displayName }} face shapes): " +
                haircut.description
        }
    }

/**
 * The catalog styles [reply] actually names, in the order it first mentions them — so the
 * pictures shown under a chat reply are exactly the styles being talked about, rather than an
 * independently chosen slice of the catalog.
 *
 * Longer names are matched first and claim their span of the text, so "Undercut Crop with Length
 * on Top" isn't also counted as "Undercut". Matching is case-sensitive on whole words, since the
 * model is told to spell names exactly as listed — that keeps ordinary prose like "a classic
 * look" from matching the style named "Classic".
 */
fun haircutsNamedIn(reply: String, catalog: List<Haircut>, limit: Int = MAX_NAMED_HAIRCUTS): List<Haircut> {
    val claimed = BooleanArray(reply.length)
    val found = mutableListOf<Pair<Int, Haircut>>()
    catalog.distinctBy { it.name }.sortedByDescending { it.name.length }.forEach { haircut ->
        val pattern = Regex("(?<![\\w-])${Regex.escape(haircut.name)}(?![\\w-])")
        val match = pattern.findAll(reply).firstOrNull { m -> m.range.none { claimed[it] } } ?: return@forEach
        match.range.forEach { claimed[it] = true }
        found += match.range.first to haircut
    }
    return found.sortedBy { it.first }.map { it.second }.take(limit)
}

private const val MAX_NAMED_HAIRCUTS = 8
