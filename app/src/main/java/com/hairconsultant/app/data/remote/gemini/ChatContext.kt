package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairColor
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.confidentSkinTone
import com.hairconsultant.app.domain.model.confidentUndertone
import com.hairconsultant.app.domain.model.matchingHaircutStyles
import com.hairconsultant.app.ui.haircolor.HairShades

/** Below this the scan's hair colour isn't stated as a reading. */
private const val HAIR_COLOR_SURE = 0.5f

/**
 * The scan's hair colour and skin tone as chat context, for hair-colour advice, or null when the
 * scan read neither. An unsure undertone comes with the questions to settle it, and colour advice
 * names the Color Studio's shades so the user can try a suggestion straight away.
 */
fun ScanResult.describeColoringForChatContext(): String? {
    val hair = hairColor.takeIf { it != HairColor.OTHER && hairColorConfidence >= HAIR_COLOR_SURE }
    val tone = skinTone
    if (hair == null && tone == null) return null
    return buildString {
        hair?.let { append("Current hair color (from the scan): ${it.displayName}. ") }
        when {
            tone == null -> Unit
            confidentSkinTone == null ->
                append("Skin tone: not read reliably (poor light); if hair color comes up, ask the user to describe their skin tone. ")
            else -> {
                append("Skin tone (estimated from the scan's camera): ${tone.displayName}")
                val sureUndertone = confidentUndertone
                if (sureUndertone != null) {
                    append(", ${sureUndertone.displayName.lowercase()} undertone. ")
                } else {
                    append(skinUndertone?.let { ", undertone unclear (leaning ${it.displayName.lowercase()}). " } ?: ". ")
                    append(
                        "Before recommending hair colors that depend on undertone, confirm it: ask whether gold or " +
                            "silver jewelry flatters them more, or whether the veins on their wrist look greenish (warm) " +
                            "or bluish (cool). "
                    )
                }
            }
        }
        append(
            "When suggesting hair colors, prefer the app's Color Studio shades so the user can try them on: " +
                HairShades.all.joinToString(", ") { it.name } + "."
        )
    }
}

/**
 * Which results the scan isn't sure of, as a sentence pointing at [fixLabel], or null when it's
 * sure of all of them. The face-shape model splits its belief over four shapes, so it gets a
 * lower bar than the hair readings.
 */
fun ScanResult.unsureSentence(fixLabel: String): String? {
    val unsure = buildList {
        if (faceShapeConfidence < FACE_SHAPE_SURE) add("face shape")
        if (hairLength != HairLength.BALD) {
            if (hairLengthConfidence < HAIR_SURE) add("hair length")
            if (hairTextureConfidence < HAIR_SURE) add("hair texture")
        }
    }
    if (unsure.isEmpty()) return null
    val items = if (unsure.size == 1) unsure[0] else unsure.dropLast(1).joinToString(", ") + " and " + unsure.last()
    return "I'm not sure about your $items, so check ${if (unsure.size == 1) "it" else "them"} with \"$fixLabel\"."
}

private const val FACE_SHAPE_SURE = 0.4f
private const val HAIR_SURE = 0.5f

/** The chat's sentence about the skin right after a scan, or null when it wasn't read reliably. */
fun ScanResult.skinScanSentence(): String? {
    val tone = confidentSkinTone ?: return null
    val undertone = confidentUndertone
    return "Your skin reads as ${tone.displayName.lowercase()}" +
        (undertone?.let { " with a ${it.displayName.lowercase()} undertone" } ?: "") +
        ", which I'll use if you ask about hair color."
}

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

/**
 * Whether a typed chat message is the user asking to be shown hairstyles ("show me the new
 * haircuts"), which should run the same matching as the "Show My Hairstyles" chip, so the picks
 * reach the try-on strip too rather than only being described in a chat reply.
 *
 * Plural "styles"/"cuts" only: "show me how to style it" is a question, not a request for picks.
 * shortcut: English keyword rule, so other phrasings just get a normal reply with the chip
 * offered; have the model classify the message if that misses too often.
 */
fun asksToSeeHairstyles(text: String): Boolean = SHOW_VERB.containsMatchIn(text) && STYLES_NOUN.containsMatchIn(text)

private val SHOW_VERB = Regex("\\b(show|see|view|give|suggest|recommend|display|list)\\b", RegexOption.IGNORE_CASE)
private val STYLES_NOUN = Regex(
    "\\b(hairstyles?|haircuts?|styles|cuts|matches|options|suggestions|recommendations|picks)\\b",
    RegexOption.IGNORE_CASE
)
