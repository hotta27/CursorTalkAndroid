package com.example.cursortalkandroid.data

import androidx.test.platform.app.InstrumentationRegistry
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FileChatHistoryStoreTest {
    @Test
    fun savesAndRestoresMessages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FileChatHistoryStore(context)
        val messages = listOf(
            ChatMessage(1, ChatRole.User, "複数行の\n質問"),
            ChatMessage(2, ChatRole.Assistant, "**回答**"),
        )

        try {
            store.saveMessages(messages)
            assertEquals(messages, store.loadMessages())
        } finally {
            store.saveMessages(emptyList())
        }
    }

    @Test
    fun dropsOldestMessagesBeyondMaxLimit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FileChatHistoryStore(context)
        val messages = (1L..120L).map { id ->
            ChatMessage(id, ChatRole.User, "message-$id")
        }

        try {
            store.saveMessages(messages)
            val loaded = store.loadMessages()
            assertEquals(MAX_CHAT_HISTORY_MESSAGES, loaded.size)
            assertEquals(21L, loaded.first().id)
            assertEquals(120L, loaded.last().id)
        } finally {
            store.saveMessages(emptyList())
        }
    }
}
