package com.clipboardsync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ServerApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val textMediaType = "text/plain; charset=utf-8".toMediaType()

    fun testConnection(serverUrl: String): String? {
        return try {
            val url = "${serverUrl.trimEnd('/')}/clipboard"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) null
                else "HTTP ${resp.code}"
            }
        } catch (e: Exception) {
            e.message ?: "Error desconocido"
        }
    }

    fun sendClipboard(serverUrl: String, text: String): Boolean {
        return try {
            val url = "${serverUrl.trimEnd('/')}/clipboard"
            val body = text.toRequestBody(textMediaType)
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    fun fetchClipboard(serverUrl: String): String? {
        return try {
            val url = "${serverUrl.trimEnd('/')}/clipboard"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
        } catch (e: Exception) {
            null
        }
    }
}
