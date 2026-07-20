package com.example.cursortalkandroid.data

import android.content.Context
import android.util.AtomicFile
import com.example.cursortalkandroid.data.model.Bookmark
import com.example.cursortalkandroid.data.model.ChatRole
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookmarkStore {
    suspend fun loadBookmarks(): List<Bookmark>

    /** Persists bookmarks and returns the list actually retained on disk. */
    suspend fun saveBookmarks(bookmarks: List<Bookmark>): List<Bookmark>

    companion object {
        const val MAX_BOOKMARKS = 500
    }
}

object EmptyBookmarkStore : BookmarkStore {
    override suspend fun loadBookmarks(): List<Bookmark> = emptyList()
    override suspend fun saveBookmarks(bookmarks: List<Bookmark>): List<Bookmark> =
        bookmarks.takeLast(BookmarkStore.MAX_BOOKMARKS)
}

class FileBookmarkStore(
    context: Context,
) : BookmarkStore {
    private val file = AtomicFile(context.filesDir.resolve("bookmarks.bin"))

    override suspend fun loadBookmarks(): List<Bookmark> = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext emptyList()

        try {
            DataInputStream(BufferedInputStream(file.openRead())).use { input ->
                if (input.readInt() != FILE_MAGIC) throw IOException("Unsupported bookmark format")
                val count = input.readInt()
                if (count !in 0..BookmarkStore.MAX_BOOKMARKS) {
                    throw IOException("Invalid bookmark count")
                }

                List(count) {
                    val id = input.readLong()
                    val sourceMessageId = input.readLong()
                    val role = when (input.readByte().toInt()) {
                        0 -> ChatRole.User
                        1 -> ChatRole.Assistant
                        else -> throw IOException("Invalid chat role")
                    }
                    val savedAt = input.readLong()
                    val textSize = input.readInt()
                    if (textSize !in 0..MAX_MESSAGE_BYTES) {
                        throw IOException("Invalid bookmark size")
                    }
                    val textBytes = ByteArray(textSize)
                    input.readFully(textBytes)
                    Bookmark(
                        id = id,
                        sourceMessageId = sourceMessageId,
                        role = role,
                        text = textBytes.toString(Charsets.UTF_8),
                        savedAt = savedAt,
                    )
                }
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    override suspend fun saveBookmarks(bookmarks: List<Bookmark>): List<Bookmark> =
        withContext(Dispatchers.IO) {
            val retained = bookmarks.takeLast(BookmarkStore.MAX_BOOKMARKS)
            val outputStream = file.startWrite()
            try {
                val output = DataOutputStream(BufferedOutputStream(outputStream))
                output.writeInt(FILE_MAGIC)
                output.writeInt(retained.size)
                retained.forEach { bookmark ->
                    val textBytes = bookmark.text.toByteArray(Charsets.UTF_8)
                    if (textBytes.size > MAX_MESSAGE_BYTES) {
                        throw IOException("Bookmark is too large to persist")
                    }
                    output.writeLong(bookmark.id)
                    output.writeLong(bookmark.sourceMessageId)
                    output.writeByte(if (bookmark.role == ChatRole.User) 0 else 1)
                    output.writeLong(bookmark.savedAt)
                    output.writeInt(textBytes.size)
                    output.write(textBytes)
                }
                output.flush()
                file.finishWrite(outputStream)
                retained
            } catch (exception: Exception) {
                file.failWrite(outputStream)
                throw exception
            }
        }

    private companion object {
        const val FILE_MAGIC = 0x43544231
        const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    }
}
