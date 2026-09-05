package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.BuildConfig
import com.hairconsultant.app.data.HairKnowledgeBase
import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.ChatSender
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
    suspend fun reply(conversation: List<ChatMessage>, userMessage: String, context: String): Result<String>
}

class GeminiChatRepositoryImpl(
    private val retriever: HairKnowledgeRetriever = GeminiHairKnowledgeRetriever(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : GeminiChatRepository {

    override suspend fun reply(conversation: List<ChatMessage>, userMessage: String, context: String): Result<String> {
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
                            add(buildJsonObject { put("text", buildSystemPrompt(knowledge)) })
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

    private fun buildSystemPrompt(retrievedKnowledge: List<String>): String =
        INSTRUCTIONS + "\n\n" + retrievedKnowledge.joinToString(separator = "\n\n") + "\n\n" + RULES

    private companion object {
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
              that list, since the app displays that exact candidate list as image cards right
              under your reply and any other name will visibly mismatch the pictures shown.
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
