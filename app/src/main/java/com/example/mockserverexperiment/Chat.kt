package com.example.mockserverexperiment

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class Chat(private val baseUrl: HttpUrl) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()
    private var message: String? = null
    private var count = 0
    var headers: Map<String, String> = mapOf()

    fun loadPath(path1: String, path2: String): Pair<Int, String?> {
        val request = Request.Builder()
            .url(baseUrl.newBuilder()
                .addPathSegment(path1)
                .addPathSegment(path2)
                .build()).build()

        val response = okHttpClient.newCall(request).execute()

        return Pair(response.code, if (response.isSuccessful) response.body?.string() else null)
    }

    fun load() {
        val request = Request.Builder()
            .url(baseUrl.newBuilder()
                .addPathSegment("chat")
                .addPathSegment("send").build())
            .header("Authorization", "xxx").post("{}".toRequestBody())
            .header("Content-Type", "application/json; charset=utf-8").build()
        readBody(request)
    }

    fun loadMore() {
        count++
        val path = if (count > 1) "$count" else ""
        val request = Request.Builder()
            .url(baseUrl.newBuilder()
                .addPathSegment("chat")
                .addPathSegment("messages")
                .addPathSegment(path).build())
            .header("Authorization", "xxx").post("{}".toRequestBody())
            .header("Content-Type", "application/json; charset=utf-8").build()

        readBody(request)
    }

    private fun readBody(request: Request) {
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            if (message != null) {
                message += "\n" + response.body?.string()
            } else {
                message = response.body?.string()
            }
        } else {
            message = response.message
        }

        headers = response.headers.associateBy ({ it.first }, { it.second })
    }

    fun messages(): String? {
        return message
    }
}
