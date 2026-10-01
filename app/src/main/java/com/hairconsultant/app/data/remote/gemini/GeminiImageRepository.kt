package com.hairconsultant.app.data.remote.gemini

import android.net.Uri

/**
 * Generates a photo-realistic preview of a hairstyle applied to the user's uploaded photo.
 * [prompt] is built by the caller from the chatbot conversation (confirmed face shape, desired
 * length/texture/treatment, and whatever else the user said) so the render reflects what was
 * actually discussed rather than just a haircut's static fields. [referenceImageUri] is the
 * catalog's own reference photo for the selected style (its `Haircut.imageUrl`) — sent alongside
 * the source photo so Gemini has an actual visual anchor for what the named style looks like
 * instead of guessing from the name/length/texture alone; fetching it is best-effort, so a missing
 * or unreachable reference image degrades to name-only guidance rather than failing generation.
 * Backed by the Gemini image generation API; the API key is injected via BuildConfig from
 * local.properties (see app/build.gradle.kts) so it never gets committed.
 */
interface GeminiImageRepository {
    suspend fun generateHaircutPreview(sourceImageUri: Uri, referenceImageUri: Uri?, prompt: String): Result<Uri>
}
