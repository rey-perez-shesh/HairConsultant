package com.hairconsultant.app.data.remote.gemini

import android.net.Uri
import com.hairconsultant.app.domain.model.Haircut

/**
 * Generates a photo-realistic preview of [haircut] applied to the user's uploaded photo.
 * Backed by the Gemini image generation API; the API key is injected via BuildConfig from
 * local.properties (see app/build.gradle.kts) so it never gets committed.
 */
interface GeminiImageRepository {
    suspend fun generateHaircutPreview(sourceImageUri: Uri, haircut: Haircut): Result<Uri>
}
