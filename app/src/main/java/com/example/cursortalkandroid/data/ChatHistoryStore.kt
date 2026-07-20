package com.example.cursortalkandroid.data

import android.content.Context
import android.util.AtomicFile
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 端末に保持するチャット履歴の最大件数。超過分は古いメッセージから削除する。 */
const val MAX_CHAT_HISTORY_MESSAGES = 100

fun retainRecentChatMessages(
    messages: List<ChatMessage>,
    maxMessages: Int = MAX_CHAT_HISTORY_MESSAGES,
): List<ChatMessage> = if (messages.size <= maxMessages) {
    messages
} else {
    messages.takeLast(maxMessages)
}

interface ChatHistoryStore {
    suspend fun loadMessages(): List<ChatMessage>
    suspend fun saveMessages(messages: List<ChatMessage>)
}

object EmptyChatHistoryStore : ChatHistoryStore {
    override suspend fun loadMessages(): List<ChatMessage> = emptyList()
    override suspend fun saveMessages(messages: List<ChatMessage>) = Unit
}

class FileChatHistoryStore(
    context: Context,
) : ChatHistoryStore {
    private val file = AtomicFile(context.filesDir.resolve("chat_history.bin"))

    override suspend fun loadMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext emptyList()

        try {
            DataInputStream(BufferedInputStream(file.openRead())).use { input ->
                if (input.readInt() != FILE_MAGIC) throw IOException("Unsupported history format")
                val count = input.readInt()
                if (count !in 0..MAX_CHAT_HISTORY_MESSAGES) throw IOException("Invalid message count")

                List(count) {
                    val id = input.readLong()
                    val role = when (input.readByte().toInt()) {
                        0 -> ChatRole.User
                        1 -> ChatRole.Assistant
                        else -> throw IOException("Invalid chat role")
                    }
                    val textSize = input.readInt()
                    if (textSize !in 0..MAX_MESSAGE_BYTES) {
                        throw IOException("Invalid message size")
                    }
                    val textBytes = ByteArray(textSize)
                    input.readFully(textBytes)
                    ChatMessage(id, role, textBytes.toString(Charsets.UTF_8))
                }
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    override suspend fun saveMessages(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val retainedMessages = retainRecentChatMessages(messages)
        val outputStream = file.startWrite()
        try {
            val output = DataOutputStream(BufferedOutputStream(outputStream))
            output.writeInt(FILE_MAGIC)
            output.writeInt(retainedMessages.size)
            retainedMessages.forEach { message ->
                val textBytes = message.text.toByteArray(Charsets.UTF_8)
                if (textBytes.size > MAX_MESSAGE_BYTES) {
                    throw IOException("Message is too large to persist")
                }
                output.writeLong(message.id)
                output.writeByte(if (message.role == ChatRole.User) 0 else 1)
                output.writeInt(textBytes.size)
                output.write(textBytes)
            }
            output.flush()
            file.finishWrite(outputStream)
        } catch (exception: Exception) {
            file.failWrite(outputStream)
            throw exception
        }
    }

    private companion object {
        const val FILE_MAGIC = 0x43544131
        const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    }
}
