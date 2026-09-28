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
import java.net.URLConnection
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Calls the Gemini image generation endpoint with the user's photo + the selected style's catalog
 * reference photo + a styling [prompt] built from the chatbot conversation, so it can render the
 * discussed look onto the uploaded photo with an actual visual anchor for what that style looks
 * like, not just its name. Every call is a no-op failure until [BuildConfig.GEMINI_API_KEY] is set
 * in local.properties.
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

    override suspend fun generateHaircutPreview(
        sourceImageUri: Uri,
        referenceImageUri: Uri?,
        prompt: String
    ): Result<Uri> {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Set GEMINI_API_KEY in local.properties to enable AI generation."))
        }
        return runCatching {
            val source = readImageBytes(sourceImageUri) ?: error("Could not read the selected photo.")
            // Best-effort: catalog thumbnails are almost always local drawables and this rarely
            // fails, but if it does, generation still proceeds on the text description alone
            // rather than failing outright over a missing/unreachable reference image.
            val reference = referenceImageUri?.let { readImageBytes(it) }

            val requestJson = buildJsonObject {
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            putJsonArray("parts") {
                                add(buildJsonObject { put("text", prompt) })
                                add(buildJsonObject { put("inlineData", source.toInlineDataJson()) })
                                reference?.let { add(buildJsonObject { put("inlineData", it.toInlineDataJson()) }) }
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

    /** Reads [uri]'s bytes + MIME type, whether it's a local drawable/content/file URI or a remote http(s) one. */
    private suspend fun readImageBytes(uri: Uri): Pair<ByteArray, String>? = runCatching {
        when (uri.scheme?.lowercase()) {
            "http", "https" -> httpClient.awaitBytesWithType(Request.Builder().url(uri.toString()).build())
            else -> {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not open a stream for $uri")
                val mimeType = appContext.contentResolver.getType(uri)
                    ?: URLConnection.guessContentTypeFromName(uri.toString())
                    ?: "image/jpeg"
                bytes to mimeType
            }
        }
    }.getOrNull()

    private fun Pair<ByteArray, String>.toInlineDataJson() = buildJsonObject {
        put("mimeType", second)
        put("data", Base64.encodeToString(first, Base64.NO_WRAP))
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

    /** Same as [await], but for a plain binary GET (a remote catalog thumbnail) — bytes + Content-Type. */
    private suspend fun OkHttpClient.awaitBytesWithType(request: Request): Pair<ByteArray, String> =
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            cont.resumeWithException(IOException("Fetch failed (${it.code})"))
                            return
                        }
                        val bytes = it.body?.bytes() ?: ByteArray(0)
                        val mimeType = it.header("Content-Type")?.substringBefore(";")?.trim() ?: "image/jpeg"
                        cont.resume(bytes to mimeType)
                    }
                }
            })
        }
}
