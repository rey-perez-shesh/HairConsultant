package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.domain.model.Haircut

/** Formats catalog haircuts as grounding context for [GeminiChatRepository.reply]. */
fun List<Haircut>.describeForChatContext(): String =
    if (isEmpty()) {
        "No specific candidate haircuts yet."
    } else {
        joinToString(separator = "\n") { haircut ->
            "- \"${haircut.name}\" (${haircut.length.displayName}, ${haircut.texture.displayName} hair; " +
                "suits ${haircut.recommendedFaceShapes.joinToString(", ") { it.displayName }} face shapes): " +
                haircut.description
        }
    }
