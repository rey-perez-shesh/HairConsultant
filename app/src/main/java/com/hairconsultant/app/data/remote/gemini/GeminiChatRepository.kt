package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.BuildConfig
import com.hairconsultant.app.data.HairKnowledgeBase
import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.ChatSender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.TreatmentPreference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The AI hairstylist's reasoning engine: a Gemini text model grounded on passages retrieved from
 * [HairKnowledgeBase] by [retriever] (retrieval-augmented generation) so its hairstyle suggestions
 * and answers come from real hairstyling guidance rather than a hard-coded decision tree.
 * [context] carries whatever the calling screen already knows for certain (confirmed face
 * shape/length/texture, the specific catalog candidates being shown) so the model reasons over
 * real, in-catalog options instead of inventing styles that don't exist in the app.
 */
interface GeminiChatRepository {
    /**
     * [conversation] should be just the recent-messages window, not necessarily the user's whole
     * history — [conversationSummary], when non-null, carries the durable facts folded out of
     * whatever came before that window (see [summarizeConversation]), so the model still has
     * long-term context without every older turn being replayed verbatim on every call.
     */
    suspend fun reply(
        conversation: List<ChatMessage>,
        userMessage: String,
        context: String,
        conversationSummary: String?
    ): Result<String>

    /**
     * Condenses [newMessages] into [previousSummary] (or starts a fresh one if null), producing a
     * compact paragraph of durable facts — confirmed face shape/length/texture, treatments
     * discussed, styles liked/disliked, stated preferences — that [reply] can pass back in as
     * [conversationSummary] once those messages age out of the recent window.
     */
    suspend fun summarizeConversation(previousSummary: String?, newMessages: List<ChatMessage>): Result<String>

    /**
     * Reads [conversation] and extracts any hair length/texture/treatment preference the user
     * actually *stated* while chatting freely — e.g. "I'd love something curly and low
     * maintenance" — understanding negation properly ("I don't want curly" is not the same as
     * wanting straight) rather than naive keyword containment. Each field is null when the user
     * never actually specified it, so callers fall back to whatever they already know (the
     * confirmed scan, or a quick-reply fix) instead of guessing.
     */
    suspend fun extractPreferences(conversation: List<ChatMessage>): Result<ExtractedPreferences>

    /**
     * The "thinking" step behind Show My Hairstyles: reasons (with Gemini's extended thinking
     * enabled) over the whole [consultation], [conversationSummary], the confirmed [userProfile],
     * and hairstyling knowledge retrieved for this specific conversation, then picks the
     * [pickCount] best-fitting entries from [candidates] — already narrowed by the user's stated
     * parameters — ranked best first. Only ids from [candidates] are ever returned.
     */
    suspend fun rankHaircuts(
        consultation: List<ChatMessage>,
        conversationSummary: String?,
        userProfile: String,
        candidates: List<Haircut>,
        pickCount: Int
    ): Result<RankedHaircuts>
}

/** See [GeminiChatRepository.extractPreferences]. */
data class ExtractedPreferences(
    val length: HairLength? = null,
    val texture: HairTexture? = null,
    val treatment: TreatmentPreference? = null
)

/** See [GeminiChatRepository.rankHaircuts]: catalog ids best-first, plus the consultant's explanation of the picks. */
data class RankedHaircuts(val haircutIds: List<String>, val explanation: String)

class GeminiChatRepositoryImpl(
    private val retriever: HairKnowledgeRetriever = GeminiHairKnowledgeRetriever(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : GeminiChatRepository {

    override suspend fun reply(
        conversation: List<ChatMessage>,
        userMessage: String,
        context: String,
        conversationSummary: String?
    ): Result<String> {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Set GEMINI_API_KEY in local.properties to enable the AI consultant."))
        }
        return runCatching {
            // Retrieval-augmented grounding: fetch just the knowledge passages relevant to this
            // question rather than the whole knowledge base; if retrieval itself fails (e.g. no
            // network for the embedding call), fall back to the full reference text so the chat
            // still answers, just less precisely targeted.
            val knowledge = retriever.retrieve(userMessage)
                .getOrElse { HairKnowledgeBase.chunks.map { it.text } }
            val requestJson = buildJsonObject {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", buildSystemPrompt(knowledge, conversationSummary)) })
                        }
                    }
                )
                putJsonArray("contents") {
                    // Prior turns give the model conversation memory; Gemini expects strictly
                    // "user"/"model" roles, which map 1:1 onto our ChatSender enum.
                    conversation.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", if (message.sender == ChatSender.USER) "user" else "model")
                                putJsonArray("parts") { add(buildJsonObject { put("text", message.text) }) }
                            }
                        )
                    }
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                add(
                                    buildJsonObject {
                                        put("text", "Context the app already knows for certain:\n$context\n\nUser: $userMessage")
                                    }
                                )
                            }
                        }
                    )
                }
                // Chat replies should feel snappy; extended thinking roughly doubles latency and
                // token cost here for no real gain on a short, conversational hairstyling answer.
                put(
                    "generationConfig",
                    buildJsonObject {
                        put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) })
                    }
                )
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = httpClient.awaitBody(request)
            extractText(responseBody)
        }
    }

    override suspend fun summarizeConversation(previousSummary: String?, newMessages: List<ChatMessage>): Result<String> {
        if (newMessages.isEmpty()) return Result.success(previousSummary.orEmpty())
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Set GEMINI_API_KEY in local.properties to enable the AI consultant."))
        }
        return runCatching {
            val transcript = newMessages.joinToString(separator = "\n") { "${it.sender.name}: ${it.text}" }
            val requestJson = buildJsonObject {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        putJsonArray("parts") { add(buildJsonObject { put("text", SUMMARIZE_INSTRUCTIONS) }) }
                    }
                )
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                add(
                                    buildJsonObject {
                                        put(
                                            "text",
                                            "Previous summary: ${previousSummary?.takeIf { it.isNotBlank() } ?: "(none yet)"}" +
                                                "\n\nNew messages to fold in:\n$transcript"
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
                put(
                    "generationConfig",
                    buildJsonObject { put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) }) }
                )
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = httpClient.awaitBody(request)
            extractText(responseBody)
        }
    }

    override suspend fun extractPreferences(conversation: List<ChatMessage>): Result<ExtractedPreferences> {
        if (conversation.none { it.sender == ChatSender.USER }) return Result.success(ExtractedPreferences())
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Set GEMINI_API_KEY in local.properties to enable the AI consultant."))
        }
        return runCatching {
            val transcript = conversation.joinToString(separator = "\n") { "${it.sender.name}: ${it.text}" }
            val requestJson = buildJsonObject {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        putJsonArray("parts") { add(buildJsonObject { put("text", EXTRACT_PREFERENCES_INSTRUCTIONS) }) }
                    }
                )
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") { add(buildJsonObject { put("text", transcript) }) }
                        }
                    )
                }
                put(
                    "generationConfig",
                    buildJsonObject {
                        put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) })
                        // Guarantees a parseable JSON body back instead of prose wrapped around it.
                        put("responseMimeType", "application/json")
                    }
                )
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = httpClient.awaitBody(request)
            parseExtractedPreferences(extractText(responseBody))
        }
    }

    override suspend fun rankHaircuts(
        consultation: List<ChatMessage>,
        conversationSummary: String?,
        userProfile: String,
        candidates: List<Haircut>,
        pickCount: Int
    ): Result<RankedHaircuts> {
        if (candidates.isEmpty()) return Result.failure(IllegalArgumentException("No candidate haircuts to rank."))
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Set GEMINI_API_KEY in local.properties to enable the AI consultant."))
        }
        return runCatching {
            val userSaid = consultation.filter { it.sender == ChatSender.USER }.joinToString(separator = " ") { it.text }
            // Retrieve knowledge for what this user actually discussed (not a canned sentence), so
            // the passages reasoned over match their face shape, texture, treatments and lifestyle.
            val knowledge = retriever.retrieve("$userProfile $userSaid".takeLast(MAX_RETRIEVAL_QUERY_CHARS), RANK_KNOWLEDGE_TOP_K)
                .getOrElse { HairKnowledgeBase.chunks.map { it.text } }
            val transcript = consultation.joinToString(separator = "\n") { "${it.sender.name}: ${it.text}" }
            val catalog = candidates.joinToString(separator = "\n") { haircut ->
                "- id=${haircut.id} | \"${haircut.name}\" | ${haircut.length.displayName}, ${haircut.texture.displayName} | " +
                    "${haircut.genderStyle.displayName} | suits ${haircut.recommendedFaceShapes.joinToString(", ") { it.displayName }} " +
                    "face shapes | ${haircut.description}"
            }
            val requestJson = buildJsonObject {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(
                                buildJsonObject {
                                    put(
                                        "text",
                                        RANK_INSTRUCTIONS.replace("{count}", pickCount.toString()) +
                                            "\n\nHairstyling knowledge:\n" + knowledge.joinToString(separator = "\n\n")
                                    )
                                }
                            )
                        }
                    }
                )
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                add(
                                    buildJsonObject {
                                        put(
                                            "text",
                                            buildString {
                                                append("User profile:\n$userProfile\n\n")
                                                if (!conversationSummary.isNullOrBlank()) {
                                                    append("Summary of earlier conversations (background only):\n$conversationSummary\n\n")
                                                }
                                                append("This consultation:\n$transcript\n\n")
                                                append("Catalog candidates (already narrowed to the user's stated parameters):\n$catalog")
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
                put(
                    "generationConfig",
                    buildJsonObject {
                        // Unlike chat replies, this is the one step where deliberate reasoning is
                        // the point: weighing face shape, stated parameters and knowledge together.
                        put("thinkingConfig", buildJsonObject { put("thinkingBudget", RANK_THINKING_BUDGET) })
                        put("responseMimeType", "application/json")
                    }
                )
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // Thinking takes noticeably longer than a plain chat reply.
            val responseBody = httpClient.newBuilder().readTimeout(90, TimeUnit.SECONDS).build().awaitBody(request)
            parseRankedHaircuts(extractText(responseBody))
        }
    }

    private fun parseRankedHaircuts(json: String): RankedHaircuts {
        val obj = Json.parseToJsonElement(json).jsonObject
        val ids = obj["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }.orEmpty()
        val explanation = obj["message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (ids.isEmpty() || explanation.isEmpty()) error("Gemini ranking was missing ids or message: $json")
        return RankedHaircuts(ids, explanation)
    }

    private fun parseExtractedPreferences(json: String): ExtractedPreferences {
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return ExtractedPreferences()
        fun field(name: String): String? = obj[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return ExtractedPreferences(
            length = field("length")?.let { runCatching { HairLength.valueOf(it.uppercase()) }.getOrNull() },
            texture = field("texture")?.let { runCatching { HairTexture.valueOf(it.uppercase()) }.getOrNull() },
            treatment = field("treatment")?.let { runCatching { TreatmentPreference.valueOf(it.uppercase()) }.getOrNull() }
        )
    }

    private fun extractText(responseBody: String): String {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val candidates = root["candidates"]?.jsonArray
            ?: error("Gemini response had no candidates: $responseBody")
        val parts = candidates.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: error("Gemini response had no content parts: $responseBody")
        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString(separator = "").trim()
        if (text.isEmpty()) error("Gemini response had no text: $responseBody")
        return text
    }

    private fun buildSystemPrompt(retrievedKnowledge: List<String>, conversationSummary: String?): String = buildString {
        append(INSTRUCTIONS)
        if (!conversationSummary.isNullOrBlank()) {
            append("\n\nSummary of this user's earlier conversation (older turns already folded out of the ")
            append("message history below, so rely on this for anything from before the recent messages):\n")
            append(conversationSummary)
        }
        append("\n\n")
        append(retrievedKnowledge.joinToString(separator = "\n\n"))
        append("\n\n")
        append(RULES)
    }

    private companion object {
        const val RANK_THINKING_BUDGET = 2048
        const val RANK_KNOWLEDGE_TOP_K = 6
        const val MAX_RETRIEVAL_QUERY_CHARS = 2000

        val RANK_INSTRUCTIONS: String = """
            You are the AI hair consultant inside the HairConsultant app. The user just asked to see
            their hairstyles. Think carefully about everything below, then pick the {count} catalog
            candidates that best fit this user, ranked best first.

            How to decide:
            1. Respect the parameters the user stated in this consultation (length, texture,
               treatment, masculine/feminine look, maintenance, lifestyle, styles they liked or
               ruled out). A style that contradicts something they explicitly said must rank below
               every style that doesn't.
            2. Use the hairstyling knowledge to judge fit for their confirmed face shape and hair
               type — name real mechanisms, not generic compliments.
            3. Only pick ids that appear in the candidate list, each at most once.

            Output strict JSON only, exactly this shape:
            {"ids": ["<id>", ...], "message": "<reply to the user>"}
            "ids" holds exactly {count} ids (fewer only if fewer candidates exist).
            "message" is shown in the chat above the {count} style pictures: one short opening
            sentence tying the picks to what the user asked for, then one sentence per pick in the
            same order, naming each style exactly as written in the candidate list and saying why
            it fits. Plain text, no markdown, no bullet characters.
        """.trimIndent()

        val EXTRACT_PREFERENCES_INSTRUCTIONS: String = """
            You read a conversation between a user and an AI hair consultant and extract ONLY the
            hair length/texture/treatment preferences the user explicitly stated for their next
            hairstyle. Output strict JSON, nothing else, in exactly this shape:
            {"length": "SHORT" | "MEDIUM" | "LONG" | "BALD" | null,
             "texture": "STRAIGHT" | "WAVY" | "CURLY" | null,
             "treatment": "NONE" | "REBOND" | "PERM" | null}

            Rules:
            - Use null for any field the user never actually stated a preference for. Never guess
              or infer one from unrelated details.
            - Pay close attention to negation: "I don't want curly" means texture is NOT curly —
              it does NOT mean the user wants straight. If the user only says what they don't want
              without saying what they do want, output null for that field.
            - "REBOND" means chemical hair straightening/rebonding, "PERM" means a chemical perm
              (adding curl/wave). Only output "NONE" if the user explicitly says they don't want
              any treatment; otherwise, if treatment was never brought up, output null.
            - Only extract from what the USER said, never from the assistant's own questions or
              suggested options.
            - Output ONLY the JSON object — no markdown fences, no explanation.
        """.trimIndent()

        val SUMMARIZE_INSTRUCTIONS: String = """
            You maintain a compact running summary of an ongoing conversation between a user and
            an AI hair consultant. Update the previous summary to fold in the new messages below —
            don't start over or restate what's unchanged. Keep only durable facts: confirmed face
            shape, hair length/texture, treatments discussed, styles the user liked, disliked, or
            ruled out, and any stated preferences or constraints (budget, lifestyle, maintenance).
            Drop pleasantries and small talk. Write it as a short paragraph, not a list. Output
            only the updated summary text, nothing else.
        """.trimIndent()

        val INSTRUCTIONS: String = """
            You are the AI hair consultant inside the HairConsultant app. You help users pick a
            hairstyle and understand hair care, reasoning from the hairstyling knowledge below —
            never from generic guesses.
        """.trimIndent()

        val RULES: String = """
            Rules:
            - Only recommend hairstyles that appear in the "candidate haircuts" list given in the
              context, if one is given — never invent a style name that isn't listed there, and
              never rename, shorten, or combine one into a name that doesn't appear verbatim in
              that list, since the app only has pictures of styles in that list and any other name
              won't match anything the user can see or try on.
            - When you do recommend a specific style, spell its name exactly as it appears in the
              candidate list (matching case and punctuation) so it's recognizable against the
              cards shown beneath your reply.
            - Ground every recommendation or answer in the reference knowledge above: name the
              specific mechanism (e.g. "adds width at the jaw", "needs extra moisture because
              tightly-curled hair is driest") rather than giving a generic compliment.
            - If the context doesn't yet include a confirmed face shape, hair texture, or length
              and the user's question depends on one, ask a short clarifying question instead of
              guessing.
            - If none of the knowledge above actually answers the question, say so plainly instead
              of guessing.
            - Keep replies conversational and concise: 2-4 sentences, no headers or bullet lists.
        """.trimIndent()
    }
}
