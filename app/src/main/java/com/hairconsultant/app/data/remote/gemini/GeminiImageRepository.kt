package com.hairconsultant.app.data.remote.gemini

import android.net.Uri

/**
 * Generates a photo-realistic preview of a hairstyle applied to the user's uploaded photo.
 * [prompt] is built by the caller from the chatbot conversation (confirmed face shape, desired
 * length/texture/treatment, and whatever else the user said) so the render reflects what was
 * actually discussed rather than just a haircut's static fields.
 * Backed by the Gemini image generation API; the API key is injected via BuildConfig from
 * local.properties (see app/build.gradle.kts) so it never gets committed.
 */
interface GeminiImageRepository {
    suspend fun generateHaircutPreview(sourceImageUri: Uri, prompt: String): Result<Uri>
}
