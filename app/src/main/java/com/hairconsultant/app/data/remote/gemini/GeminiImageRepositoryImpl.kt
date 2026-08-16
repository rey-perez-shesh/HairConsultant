package com.hairconsultant.app.data.remote.gemini

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.hairconsultant.app.BuildConfig
import com.hairconsultant.app.domain.model.Haircut
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Calls the Gemini image generation endpoint with the user's photo + the selected haircut's
 * reference image so it can render the style onto the uploaded photo. This is real wiring, but
 * every call is a no-op failure until [BuildConfig.GEMINI_API_KEY] is set in local.properties.
 */
class GeminiImageRepositoryImpl(
    private val appContext: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) : GeminiImageRepository {

    override suspend fun generateHaircutPreview(sourceImageUri: Uri, haircut: Haircut): Result<Uri> {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Set GEMINI_API_KEY in local.properties to enable AI generation."))
        }
        return runCatching {
            val imageBytes = appContext.contentResolver.openInputStream(sourceImageUri)?.use { it.readBytes() }
                ?: error("Could not read the selected photo.")
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val requestJson = buildJsonObject {
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            putJsonArray("parts") {
                                add(
                                    buildJsonObject {
                                        put(
                                            "text",
                                            "Apply this haircut style (\"${haircut.name}\", " +
                                                "${haircut.length.displayName.lowercase()}, " +
                                                "${haircut.texture.displayName.lowercase()}) to the person in the photo, " +
                                                "keeping their face and background unchanged."
                                        )
                                    }
                                )
                                add(
                                    buildJsonObject {
                                        put(
                                            "inlineData",
                                            buildJsonObject {
                                                put("mimeType", "image/jpeg")
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
            // TODO: parse the inlineData image part out of the Gemini response, write it to the
            // app's cache dir, and return a file:// / content:// Uri pointing at it.
            error("Response parsing not implemented yet: $responseBody")
        }
    }

    private suspend fun OkHttpClient.await(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { cont.resume(it.body?.string().orEmpty()) }
            }
        })
    }
}
