package com.example.cursortalkandroid.data

import com.example.cursortalkandroid.data.model.SseEvent
import com.example.cursortalkandroid.data.remote.CursorTalkClient
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun streamMessage(
        baseUrl: String,
        message: String,
        sessionId: String?,
    ): Flow<SseEvent>
}

class DefaultChatRepository(
    private val client: CursorTalkClient,
) : ChatRepository {
    override fun streamMessage(
        baseUrl: String,
        message: String,
        sessionId: String?,
    ): Flow<SseEvent> = client.streamMessage(baseUrl, message, sessionId)
}
