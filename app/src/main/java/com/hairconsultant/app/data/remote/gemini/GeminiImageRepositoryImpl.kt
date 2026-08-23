package com.hairconsultant.app.data.remote.gemini

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.hairconsultant.app.BuildConfig
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
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Calls the Gemini image generation endpoint with the user's photo + a styling [prompt] built
 * from the chatbot conversation so it can render the discussed look onto the uploaded photo.
 * Every call is a no-op failure until [BuildConfig.GEMINI_API_KEY] is set in local.properties.
 */
class GeminiImageRepositoryImpl(
    private val appContext: Context,
    // Image generation routinely takes 20-60s (far past OkHttp's 10s defaults), so this client
    // needs its own generous timeouts rather than sharing NetworkModule's backend-API client.
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
) : GeminiImageRepository {

    override suspend fun generateHaircutPreview(sourceImageUri: Uri, prompt: String): Result<Uri> {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Set GEMINI_API_KEY in local.properties to enable AI generation."))
        }
        return runCatching {
            val imageBytes = appContext.contentResolver.openInputStream(sourceImageUri)?.use { it.readBytes() }
                ?: error("Could not read the selected photo.")
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val mimeType = appContext.contentResolver.getType(sourceImageUri) ?: "image/jpeg"

            val requestJson = buildJsonObject {
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            putJsonArray("parts") {
                                add(buildJsonObject { put("text", prompt) })
                                add(
                                    buildJsonObject {
                                        put(
                                            "inlineData",
                                            buildJsonObject {
                                                put("mimeType", mimeType)
                                                put("data", base64Image)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent")
                .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = httpClient.await(request)
            saveGeneratedImage(responseBody)
        }
    }

    /** Pulls the first inline image part out of a Gemini generateContent response and writes it to cache. */
    private fun saveGeneratedImage(responseBody: String): Uri {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val candidates = root["candidates"]?.jsonArray
            ?: error("Gemini response had no candidates: $responseBody")
        val parts = candidates.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: error("Gemini response had no content parts: $responseBody")
        val base64Data = parts.firstNotNullOfOrNull { part ->
            part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
        } ?: error("Gemini response had no image data: $responseBody")

        val imageBytes = Base64.decode(base64Data, Base64.NO_WRAP)
        val file = File(appContext.cacheDir, "gemini_preview_${System.currentTimeMillis()}.jpg")
        file.writeBytes(imageBytes)
        return Uri.fromFile(file)
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
}
