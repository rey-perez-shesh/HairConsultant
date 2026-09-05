package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.BuildConfig
import com.hairconsultant.app.data.HairKnowledgeBase
import com.hairconsultant.app.data.HairKnowledgeBase.KnowledgeChunk
import kotlin.math.sqrt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
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
 * The retrieval half of the AI consultant's retrieval-augmented generation: rather than handing
 * [com.hairconsultant.app.data.HairKnowledgeBase]'s whole reference text to Gemini on every turn,
 * this embeds each [HairKnowledgeBase.chunks] entry once, embeds the user's question, and returns
 * only the chunks whose meaning is closest to it — so [GeminiChatRepository] grounds its answer on
 * the few passages that are actually relevant.
 */
interface HairKnowledgeRetriever {
    suspend fun retrieve(query: String, topK: Int = DEFAULT_TOP_K): Result<List<String>>

    companion object {
        const val DEFAULT_TOP_K = 5
    }
}

/** Produces an embedding vector for a piece of text. Swappable so retrieval logic is testable without a network call. */
fun interface TextEmbedder {
    suspend fun embed(text: String): FloatArray
}

class GeminiHairKnowledgeRetriever(
    private val embedder: TextEmbedder = HttpGeminiTextEmbedder(),
    private val chunks: List<KnowledgeChunk> = HairKnowledgeBase.chunks
) : HairKnowledgeRetriever {

    private val mutex = Mutex()
    private var chunkEmbeddings: List<FloatArray>? = null

    override suspend fun retrieve(query: String, topK: Int): Result<List<String>> = runCatching {
        val embeddings = chunkEmbeddings ?: mutex.withLock {
            chunkEmbeddings ?: chunks.map { embedder.embed(it.text) }.also { chunkEmbeddings = it }
        }
        val queryEmbedding = embedder.embed(query)
        chunks.indices
            .sortedByDescending { cosineSimilarity(queryEmbedding, embeddings[it]) }
            .take(topK)
            .map { chunks[it].text }
    }

    internal companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            return if (normA == 0f || normB == 0f) 0f else dot / (sqrt(normA) * sqrt(normB))
        }
    }
}

/** Calls Gemini's text-embedding-004 model to turn text into a vector. */
class HttpGeminiTextEmbedder(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : TextEmbedder {

    override suspend fun embed(text: String): FloatArray {
        check(BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            "Set GEMINI_API_KEY in local.properties to enable the AI consultant."
        }
        val requestJson = buildJsonObject {
            put(
                "content",
                buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", text) }) } }
            )
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent")
            .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = httpClient.awaitBody(request)
        val values = Json.parseToJsonElement(responseBody).jsonObject["embedding"]
            ?.jsonObject?.get("values")?.jsonArray
            ?: error("Gemini embedding response had no values: $responseBody")
        return FloatArray(values.size) { values[it].jsonPrimitive.float }
    }
}
