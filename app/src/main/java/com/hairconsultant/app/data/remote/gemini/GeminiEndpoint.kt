package com.hairconsultant.app.data.remote.gemini

import com.google.firebase.auth.FirebaseAuth
import com.hairconsultant.app.BuildConfig
import java.io.IOException
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Where Gemini REST calls go. With GEMINI_PROXY_URL set (the hairconsultant-admin `geminiProxy`
 * Cloud Function) requests carry the signed-in user's Firebase ID token and the API key stays on
 * the server. GEMINI_API_KEY (direct, key baked into the APK) is only a local-testing fallback.
 */
internal object GeminiEndpoint {

    private const val GOOGLE_BASE_URL = "https://generativelanguage.googleapis.com"

    const val NOT_CONFIGURED_MESSAGE =
        "Set GEMINI_PROXY_URL (or GEMINI_API_KEY for local testing) in local.properties to enable the AI consultant."

    val isConfigured: Boolean
        get() = BuildConfig.GEMINI_PROXY_URL.isNotBlank() || BuildConfig.GEMINI_API_KEY.isNotBlank()

    /** A POST of [jsonBody] to `v1beta/models/[modelAndMethod]`, e.g. "gemini-2.5-flash:generateContent". */
    suspend fun post(modelAndMethod: String, jsonBody: String): Request {
        val builder = Request.Builder().post(jsonBody.toRequestBody("application/json".toMediaType()))
        val proxyUrl = BuildConfig.GEMINI_PROXY_URL.trimEnd('/')
        if (proxyUrl.isNotBlank()) {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: throw IOException("Sign in to use the AI consultant.")
            builder.url("$proxyUrl/v1beta/models/$modelAndMethod")
                .addHeader("Authorization", "Bearer $token")
        } else {
            builder.url("$GOOGLE_BASE_URL/v1beta/models/$modelAndMethod")
                .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
        }
        return builder.build()
    }
}
