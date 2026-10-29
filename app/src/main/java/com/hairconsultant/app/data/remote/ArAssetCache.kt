package com.hairconsultant.app.data.remote

import android.content.Context
import android.util.Log
import com.hairconsultant.app.domain.model.ArType
import com.hairconsultant.app.domain.model.Haircut
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * On-device copies of AR try-on assets uploaded from the admin dashboard (Firebase Storage
 * download URLs on [Haircut.arAssetUrl]). Each file is keyed by haircut id + [Haircut.arAssetVersion],
 * so a re-upload in the dashboard (which bumps the version) is fetched once and older copies of
 * the same style are dropped.
 */
object ArAssetCache {

    private const val TAG = "ArAssetCache"
    private const val DIR_NAME = "ar_assets"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val downloadLock = Mutex()

    /** Local file for [haircut]'s remote asset, downloading it first if needed; null on failure. */
    suspend fun localFile(context: Context, haircut: Haircut): File? {
        val url = haircut.arAssetUrl ?: return null
        val extension = when (haircut.arType) {
            ArType.DEEPAR -> "deepar"
            ArType.GLB -> "glb"
            ArType.AUTO, ArType.NONE -> return null
        }
        val safeId = haircut.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = File(context.filesDir, DIR_NAME)
        val target = File(dir, "${safeId}_v${haircut.arAssetVersion}.$extension")
        if (target.isFile && target.length() > 0) return target

        return withContext(Dispatchers.IO) {
            downloadLock.withLock {
                if (target.isFile && target.length() > 0) return@withLock target
                dir.mkdirs()
                dir.listFiles { file -> file.name.startsWith("${safeId}_v") }?.forEach { it.delete() }
                val partial = File(dir, "${target.name}.part")
                runCatching {
                    httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val body = checkNotNull(response.body) { "Empty body" }
                        body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                    }
                    check(partial.renameTo(target)) { "Couldn't move download into place" }
                    target
                }.onFailure {
                    partial.delete()
                    Log.w(TAG, "Couldn't download AR asset for ${haircut.id}", it)
                }.getOrNull()
            }
        }
    }
}
