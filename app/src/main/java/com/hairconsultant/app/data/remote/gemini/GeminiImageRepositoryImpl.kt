package com.hairconsultant.app.data.remote.gemini

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Calls the Gemini image generation endpoint with the user's photo + the selected style's catalog
 * reference photo + a styling [prompt] built from the chatbot conversation, so it can render the
 * discussed look onto the uploaded photo with an actual visual anchor for what that style looks
 * like, not just its name. Every call is a no-op failure until [GeminiEndpoint] is configured
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
        if (!GeminiEndpoint.isConfigured) {
            return Result.failure(IllegalStateException(GeminiEndpoint.NOT_CONFIGURED_MESSAGE))
        }
        return runCatching {
            val source = readImageBytes(sourceImageUri) ?: error("Could not read the selected photo.")
            // Best-effort: catalog thumbnails are almost always local drawables and this rarely
            // fails, but if it does, generation still proceeds on the text description alone
            // rather than failing outright over a missing/unreachable reference image.
            val reference = referenceImageUri?.let { readImageBytes(it) }?.let(::flattenOntoWhite)

            // TEMPORARY diagnostic: what Gemini is actually sent; remove once try-on generation is fixed.
            prompt.chunked(3000).forEach { Log.d(TAG, "prompt: $it") }
            Log.d(
                TAG,
                "source=$sourceImageUri ${source.first.size}B ${source.second}; reference=$referenceImageUri " +
                    (reference?.let { "${it.first.size}B ${it.second}" } ?: "NOT LOADED")
            )
            File(appContext.cacheDir, "debug_source").writeBytes(source.first)
            reference?.let { File(appContext.cacheDir, "debug_reference.jpg").writeBytes(it.first) }

            var image = requestPreview(prompt, reference, source)
            // The model now and then hands the photo back with the hair untouched, mostly for
            // styles close to the person's current cut. One firmer retry, then show what we have.
            if (looksUnchanged(source.first, image)) {
                Log.d(TAG, "result matches the uploaded photo; retrying once")
                image = runCatching { requestPreview(prompt + UNCHANGED_RETRY_NOTE, reference, source) }
                    .getOrElse { if (it is CancellationException) throw it else image }
            }

            val file = File(appContext.cacheDir, "gemini_preview_${System.currentTimeMillis()}.jpg")
            file.writeBytes(image)
            Uri.fromFile(file)
        }
    }

    /** One generateContent call; returns the generated image's bytes. */
    private suspend fun requestPreview(
        prompt: String,
        reference: Pair<ByteArray, String>?,
        source: Pair<ByteArray, String>
    ): ByteArray {
        val requestJson = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", prompt) })
                            // Reference first, the user's photo last: the prompt numbers them in
                            // this order, and with the photo last the result keeps its portrait
                            // shape (it came back square when the square reference went last).
                            reference?.let { add(buildJsonObject { put("inlineData", it.toInlineDataJson()) }) }
                            add(buildJsonObject { put("inlineData", source.toInlineDataJson()) })
                        }
                    }
                )
            }
        }
        val request = GeminiEndpoint.post("gemini-2.5-flash-image:generateContent", requestJson.toString())
        return extractGeneratedImage(httpClient.await(request))
    }

    /**
     * True when [generated] is, to the eye, the same picture as [source]. A photo the two decode
     * differently (e.g. one rotated by its EXIF tag) reads as changed, which errs towards not
     * spending a second request.
     */
    private fun looksUnchanged(source: ByteArray, generated: ByteArray): Boolean {
        val before = comparisonPixels(source) ?: return false
        val after = comparisonPixels(generated) ?: return false
        return changedFraction(before, after) < UNCHANGED_MAX_CHANGED_FRACTION
    }

    /** [bytes] squashed to a small square, so two sizes of the same picture line up pixel for pixel. */
    private fun comparisonPixels(bytes: ByteArray): IntArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Decode at roughly twice the comparison size first: a straight bilinear shrink from full
        // size skips most pixels and would add noise that looks like change.
        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit(maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / (COMPARE_SIZE * 2)))
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val small = Bitmap.createScaledBitmap(bitmap, COMPARE_SIZE, COMPARE_SIZE, true)
        val pixels = IntArray(COMPARE_SIZE * COMPARE_SIZE)
        small.getPixels(pixels, 0, COMPARE_SIZE, 0, 0, COMPARE_SIZE, COMPARE_SIZE)
        if (small !== bitmap) small.recycle()
        bitmap.recycle()
        return pixels
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

    /**
     * Catalog thumbnails are dark hair cutouts on a transparent background. Sent as-is, the
     * transparency can read as black, so the hair vanishes into it and the model has no style to
     * copy. Flattening onto white and re-encoding as JPEG also makes the MIME type correct: local
     * drawable URIs have no extension, so a PNG was being labelled image/jpeg.
     */
    private fun flattenOntoWhite(image: Pair<ByteArray, String>): Pair<ByteArray, String> {
        val bitmap = BitmapFactory.decodeByteArray(image.first, 0, image.first.size) ?: return image
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        flat.recycle()
        return out.toByteArray() to "image/jpeg"
    }

    private fun Pair<ByteArray, String>.toInlineDataJson() = buildJsonObject {
        put("mimeType", second)
        put("data", Base64.encodeToString(first, Base64.NO_WRAP))
    }

    /** Pulls the first inline image part out of a Gemini generateContent response. */
    private fun extractGeneratedImage(responseBody: String): ByteArray {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val candidates = root["candidates"]?.jsonArray
            ?: error("Gemini response had no candidates: $responseBody")
        val parts = candidates.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: error("Gemini response had no content parts: $responseBody")
        // TEMPORARY diagnostic, see generateHaircutPreview.
        Log.d(
            TAG,
            "response: ${responseBody.length} chars, finish=${candidates.firstOrNull()?.jsonObject?.get("finishReason")}, " +
                "images=${parts.count { it.jsonObject["inlineData"] != null }}, " +
                "text=${parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }}"
        )
        val base64Data = parts.firstNotNullOfOrNull { part ->
            part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
        } ?: error("Gemini response had no image data: $responseBody")

        return Base64.decode(base64Data, Base64.NO_WRAP)
    }

    private companion object {
        const val TAG = "GeminiImage"

        /** Side of the square both pictures are shrunk to for [looksUnchanged]. */
        const val COMPARE_SIZE = 48

        /**
         * Below this share of changed pixels the result counts as the untouched photo.
         * shortcut: set from four previews of one photo (untouched 0.7%; restyled 3.3%, 4.0%,
         * 18.7%), so a subtle real restyle can cost one needless retry; re-measure if that shows.
         */
        const val UNCHANGED_MAX_CHANGED_FRACTION = 0.02f

        const val UNCHANGED_RETRY_NOTE = " The previous attempt returned the photo with the hair unchanged, which " +
            "is wrong. The hair must be visibly restyled to match the first image."
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

/**
 * The share of pixels whose brightness differs noticeably between two same-sized ARGB pixel
 * arrays. A restyled haircut moves dark hair over light skin or background (or the reverse), so
 * it shows up here, while re-encoding noise on an untouched photo doesn't.
 */
internal fun changedFraction(before: IntArray, after: IntArray): Float {
    if (before.isEmpty() || before.size != after.size) return 1f
    return before.indices.count { abs(brightness(before[it]) - brightness(after[it])) > NOTICEABLE_BRIGHTNESS_CHANGE }
        .toFloat() / before.size
}

private fun brightness(argb: Int) = ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF)) / 3

private const val NOTICEABLE_BRIGHTNESS_CHANGE = 40
