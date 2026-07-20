package com.example.cursortalkandroid.data.remote

import com.example.cursortalkandroid.data.model.SseEvent
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CursorTalkClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    fun streamMessage(
        baseUrl: String,
        message: String,
        sessionId: String?,
    ): Flow<SseEvent> = channelFlow {
        val body = buildJsonBody(message, sessionId)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${baseUrl.trim().trimEnd('/')}/chat/stream")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()
        val call = client.newCall(request)

        try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = response.body.string().take(500)
                        throw IOException("サーバーエラー (${response.code})${detail.toDetail()}")
                    }

                    val parser = SseParser()
                    val source = response.body.source()
                    while (currentCoroutineContext().isActive && !source.exhausted()) {
                        ensureActive()
                        val line = source.readUtf8Line() ?: break
                        parser.feed("$line\n").forEach { send(it) }
                    }
                    if (currentCoroutineContext().isActive) {
                        parser.finish().forEach { send(it) }
                    }
                }
            }
        } catch (exception: IOException) {
            // cancel() による切断は購読終了であり、ユーザー向けエラーにしない
            ensureActive()
            if (!call.isCanceled()) throw exception
        } finally {
            call.cancel()
        }
    }

    private fun buildJsonBody(message: String, sessionId: String?): String = buildString {
        append("{\"message\":")
        append(message.toJsonString())
        if (!sessionId.isNullOrBlank()) {
            append(",\"sessionId\":")
            append(sessionId.toJsonString())
        }
        append('}')
    }

    private fun String.toJsonString(): String = buildString {
        append('"')
        this@toJsonString.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u%04x".format(char.code))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }

    private fun String.toDetail(): String = trim()
        .takeIf(String::isNotEmpty)
        ?.let { ": $it" }
        .orEmpty()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
