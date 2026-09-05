package com.hairconsultant.app.data.remote.gemini

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request

/** Runs [request] on [this] client and returns its body, shared by every Gemini REST call. */
internal suspend fun OkHttpClient.awaitBody(request: Request): String = suspendCancellableCoroutine { cont ->
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
