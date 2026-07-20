package com.example.cursortalkandroid.data.model

enum class ChatRole {
    User,
    Assistant,
}

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
)

data class Bookmark(
    val id: Long,
    val sourceMessageId: Long,
    val role: ChatRole,
    val text: String,
    val savedAt: Long,
)

sealed interface SseEvent {
    data class Meta(val sessionId: String) : SseEvent
    data class Delta(val text: String) : SseEvent
    data class Done(val sessionId: String) : SseEvent
    data class Error(val message: String) : SseEvent
}
