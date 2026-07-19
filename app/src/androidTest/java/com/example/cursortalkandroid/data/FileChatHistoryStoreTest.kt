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
}
