package com.example.cursortalkandroid.data

import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryLimitsTest {
    @Test
    fun retainRecentChatMessagesDropsOldestFirst() {
        val messages = (1L..105L).map { id ->
            ChatMessage(id, if (id % 2L == 0L) ChatRole.Assistant else ChatRole.User, "m$id")
        }

        val retained = retainRecentChatMessages(messages)

        assertEquals(MAX_CHAT_HISTORY_MESSAGES, retained.size)
        assertEquals(6L, retained.first().id)
        assertEquals(105L, retained.last().id)
    }

    @Test
    fun retainRecentChatMessagesKeepsShortListsUnchanged() {
        val messages = listOf(
            ChatMessage(1, ChatRole.User, "質問"),
            ChatMessage(2, ChatRole.Assistant, "回答"),
        )

        assertEquals(messages, retainRecentChatMessages(messages))
    }
}
