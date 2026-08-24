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
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The AI hairstylist's reasoning engine: a Gemini text model grounded on
 * [HairKnowledgeBase] so its hairstyle suggestions and answers come from real hairstyling
 * guidance rather than a hard-coded decision tree. [context] carries whatever the calling
 * screen already knows for certain (confirmed face shape/length/texture, the specific catalog
 * candidates being shown) so the model reasons over real, in-catalog options instead of
 * inventing styles that don't exist in the app.
 */
interface GeminiChatRepository {
    suspend fun reply(conversation: List<ChatMessage>, userMessage: String, context: String): Result<String>
}

class GeminiChatRepositoryImpl(
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
            val requestJson = buildJsonObject {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", SYSTEM_PROMPT) })
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

            val responseBody = httpClient.await(request)
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

    private suspend fun OkHttpClient.await(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) {
                        cont.resume(body)
                    } else {
                        cont.resumeWithException(IOException("Gemini request failed (${it.code}): $body"))
                    }
                }
            }
        })
    }

    private companion object {
        val SYSTEM_PROMPT: String = """
            You are the AI hair consultant inside the HairConsultant app. You help users pick a
            hairstyle and understand hair care, reasoning from the hairstyling knowledge below —
            never from generic guesses.
        """.trimIndent() + "\n\n" + HairKnowledgeBase.referenceText + "\n\n" + """
            Rules:
            - Only recommend hairstyles that appear in the "candidate haircuts" list given in the
              context, if one is given — never invent a style name that isn't listed there.
            - Ground every recommendation or answer in the reference knowledge above: name the
              specific mechanism (e.g. "adds width at the jaw", "needs extra moisture because
              coily hair is driest") rather than giving a generic compliment.
            - If the context doesn't yet include a confirmed face shape, hair texture, or length
              and the user's question depends on one, ask a short clarifying question instead of
              guessing.
            - Keep replies conversational and concise: 2-4 sentences, no headers or bullet lists.
        """.trimIndent()
    }
}
