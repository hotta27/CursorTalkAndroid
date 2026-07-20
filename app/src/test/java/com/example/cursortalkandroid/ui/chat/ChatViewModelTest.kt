package com.example.cursortalkandroid.ui.chat

import com.example.cursortalkandroid.MainDispatcherRule
import com.example.cursortalkandroid.data.BookmarkStore
import com.example.cursortalkandroid.data.ChatPreferences
import com.example.cursortalkandroid.data.ChatPreferencesStore
import com.example.cursortalkandroid.data.ChatHistoryStore
import com.example.cursortalkandroid.data.ChatRepository
import com.example.cursortalkandroid.data.model.Bookmark
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import com.example.cursortalkandroid.data.model.SseEvent
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sendAppendsDeltasAndPersistsSession() = runTest(mainDispatcherRule.testDispatcher) {
        val preferences = FakePreferences()
        val repository = FakeRepository(
            events = listOf(
                SseEvent.Meta("session-1"),
                SseEvent.Delta("こん"),
                SseEvent.Delta("にちは"),
                SseEvent.Done("session-1"),
            ),
        )
        val viewModel = ChatViewModel(repository, preferences)
        advanceUntilIdle()

        viewModel.updateInput("  質問  ")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.messages.size)
        assertEquals(ChatRole.User, state.messages[0].role)
        assertEquals("質問", state.messages[0].text)
        assertEquals("こんにちは", state.messages[1].text)
        assertFalse(state.isStreaming)
        assertNull(state.error)
        assertEquals("session-1", preferences.savedSessionId)
        assertEquals(1, repository.requestCount)
    }

    @Test
    fun streamErrorRestoresInputAndPreventsDuplicateSend() =
        runTest(mainDispatcherRule.testDispatcher) {
            val preferences = FakePreferences()
            val repository = FakeRepository(
                events = listOf(SseEvent.Error("サーバー失敗")),
            )
            val viewModel = ChatViewModel(repository, preferences)
            advanceUntilIdle()

            viewModel.updateInput("質問")
            viewModel.sendMessage()
            viewModel.sendMessage()
            advanceUntilIdle()

            assertEquals(1, repository.requestCount)
            assertEquals("サーバー失敗", viewModel.state.value.error)
            assertFalse(viewModel.state.value.isStreaming)
        }

    @Test
    fun validatesAndStoresServerUrlWhileClearingSession() =
        runTest(mainDispatcherRule.testDispatcher) {
            val preferences = FakePreferences(initialSessionId = "old")
            val viewModel = ChatViewModel(FakeRepository(emptyList()), preferences)
            advanceUntilIdle()

            assertFalse(viewModel.saveServerUrl("not a url"))
            assertTrue(viewModel.saveServerUrl("https://example.com/"))
            advanceUntilIdle()

            assertEquals("https://example.com", viewModel.state.value.serverUrl)
            assertTrue(preferences.sessionWasCleared)
        }

    @Test
    fun restoresAndUpdatesPersistedConversation() =
        runTest(mainDispatcherRule.testDispatcher) {
            val initialMessages = listOf(
                ChatMessage(10, ChatRole.User, "以前の質問"),
                ChatMessage(11, ChatRole.Assistant, "以前の回答"),
            )
            val historyStore = FakeHistoryStore(initialMessages)
            val viewModel = ChatViewModel(
                repository = FakeRepository(listOf(SseEvent.Delta("新しい回答"))),
                preferences = FakePreferences(),
                historyStore = historyStore,
            )
            advanceUntilIdle()

            assertEquals(initialMessages, viewModel.state.value.messages)
            viewModel.updateInput("新しい質問")
            viewModel.sendMessage()
            advanceUntilIdle()

            assertEquals(4, historyStore.savedMessages.size)
            assertEquals("新しい回答", historyStore.savedMessages.last().text)
        }

    @Test
    fun loadsBookmarksEvenWhenHistoryLoadFails() =
        runTest(mainDispatcherRule.testDispatcher) {
            val bookmarks = listOf(
                Bookmark(1, 10, ChatRole.Assistant, "残したいメモ", 100L),
            )
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                historyStore = object : ChatHistoryStore {
                    override suspend fun loadMessages(): List<ChatMessage> {
                        throw IOException("history corrupt")
                    }

                    override suspend fun saveMessages(messages: List<ChatMessage>) = Unit
                },
                bookmarkStore = FakeBookmarkStore(bookmarks),
            )
            advanceUntilIdle()

            assertEquals(bookmarks, viewModel.state.value.bookmarks)
            assertTrue(viewModel.state.value.messages.isEmpty())
            assertEquals("保存した会話を読み込めませんでした。", viewModel.state.value.error)
        }

    @Test
    fun loadsHistoryEvenWhenBookmarkLoadFails() =
        runTest(mainDispatcherRule.testDispatcher) {
            val messages = listOf(ChatMessage(10, ChatRole.User, "以前の質問"))
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                historyStore = FakeHistoryStore(messages),
                bookmarkStore = object : BookmarkStore {
                    override suspend fun loadBookmarks(): List<Bookmark> {
                        throw IOException("bookmarks corrupt")
                    }

                    override suspend fun saveBookmarks(bookmarks: List<Bookmark>): List<Bookmark> =
                        bookmarks
                },
            )
            advanceUntilIdle()

            assertEquals(messages, viewModel.state.value.messages)
            assertTrue(viewModel.state.value.bookmarks.isEmpty())
            assertEquals("保存したブックマークを読み込めませんでした。", viewModel.state.value.error)
        }

    @Test
    fun toggleBookmarkAddsThenRemovesAndPersists() =
        runTest(mainDispatcherRule.testDispatcher) {
            val bookmarkStore = FakeBookmarkStore()
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                bookmarkStore = bookmarkStore,
            )
            advanceUntilIdle()

            val message = ChatMessage(5, ChatRole.Assistant, "保存したい回答")

            viewModel.toggleBookmark(message)
            advanceUntilIdle()

            var state = viewModel.state.value
            assertEquals(1, state.bookmarks.size)
            assertEquals("保存したい回答", state.bookmarks.first().text)
            assertEquals(5L, state.bookmarks.first().sourceMessageId)
            assertTrue(5L in state.bookmarkedSourceIds)
            assertEquals(1, bookmarkStore.savedBookmarks.size)

            viewModel.toggleBookmark(message)
            advanceUntilIdle()

            state = viewModel.state.value
            assertTrue(state.bookmarks.isEmpty())
            assertFalse(5L in state.bookmarkedSourceIds)
            assertTrue(bookmarkStore.savedBookmarks.isEmpty())
        }

    @Test
    fun toggleBookmarkIgnoresBlankMessages() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                bookmarkStore = FakeBookmarkStore(),
            )
            advanceUntilIdle()

            viewModel.toggleBookmark(ChatMessage(1, ChatRole.Assistant, "   "))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.bookmarks.isEmpty())
        }

    @Test
    fun restoresPersistedBookmarksAndRemovesById() =
        runTest(mainDispatcherRule.testDispatcher) {
            val initial = listOf(
                Bookmark(1, 10, ChatRole.User, "質問メモ", 100L),
                Bookmark(2, 11, ChatRole.Assistant, "回答メモ", 200L),
            )
            val bookmarkStore = FakeBookmarkStore(initial)
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                bookmarkStore = bookmarkStore,
            )
            advanceUntilIdle()

            assertEquals(initial, viewModel.state.value.bookmarks)

            viewModel.removeBookmark(1)
            advanceUntilIdle()

            assertEquals(listOf(initial[1]), viewModel.state.value.bookmarks)
            assertEquals(listOf(initial[1]), bookmarkStore.savedBookmarks)
        }

    @Test
    fun toggleBookmarkRollsBackUiWhenPersistFails() =
        runTest(mainDispatcherRule.testDispatcher) {
            val bookmarkStore = FailingBookmarkStore()
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                bookmarkStore = bookmarkStore,
            )
            advanceUntilIdle()

            viewModel.toggleBookmark(ChatMessage(5, ChatRole.Assistant, "保存したい回答"))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.bookmarks.isEmpty())
            assertEquals("ブックマークを端末へ保存できませんでした。", state.error)
        }

    @Test
    fun rapidBookmarkTogglePersistsLatestState() =
        runTest(mainDispatcherRule.testDispatcher) {
            val firstSaveStarted = CompletableDeferred<Unit>()
            val allowFirstSave = CompletableDeferred<Unit>()
            val bookmarkStore = ControllableBookmarkStore(firstSaveStarted, allowFirstSave)
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                bookmarkStore = bookmarkStore,
            )
            advanceUntilIdle()

            val message = ChatMessage(5, ChatRole.Assistant, "保存したい回答")
            viewModel.toggleBookmark(message)

            val waiter = launch { firstSaveStarted.await() }
            advanceUntilIdle()
            assertTrue(waiter.isCompleted)

            viewModel.toggleBookmark(message)
            allowFirstSave.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.bookmarks.isEmpty())
            assertTrue(bookmarkStore.savedBookmarks.isEmpty())
        }

    @Test
    fun toggleBookmarkIgnoresActivelyStreamingAssistantMessage() =
        runTest(mainDispatcherRule.testDispatcher) {
            val continueStream = CompletableDeferred<Unit>()
            val bookmarkStore = FakeBookmarkStore()
            val viewModel = ChatViewModel(
                repository = object : ChatRepository {
                    override fun streamMessage(
                        baseUrl: String,
                        message: String,
                        sessionId: String?,
                    ): Flow<SseEvent> = flow {
                        emit(SseEvent.Delta("途中"))
                        continueStream.await()
                        emit(SseEvent.Delta("完了"))
                        emit(SseEvent.Done("session-1"))
                    }
                },
                preferences = FakePreferences(),
                bookmarkStore = bookmarkStore,
            )
            advanceUntilIdle()

            viewModel.updateInput("質問")
            viewModel.sendMessage()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isStreaming)
            val streamingMessage = viewModel.state.value.messages.last()
            assertEquals("途中", streamingMessage.text)

            viewModel.toggleBookmark(streamingMessage)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.bookmarks.isEmpty())
            assertTrue(bookmarkStore.savedBookmarks.isEmpty())

            continueStream.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isStreaming)
            viewModel.toggleBookmark(viewModel.state.value.messages.last())
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.bookmarks.size)
            assertEquals("途中完了", viewModel.state.value.bookmarks.first().text)
        }

    @Test
    fun toggleBookmarkCapsToMaxAndKeepsUiInSync() =
        runTest(mainDispatcherRule.testDispatcher) {
            val initial = List(BookmarkStore.MAX_BOOKMARKS) { index ->
                Bookmark(
                    id = index.toLong() + 1,
                    sourceMessageId = index.toLong() + 1000,
                    role = ChatRole.Assistant,
                    text = "bookmark-$index",
                    savedAt = index.toLong(),
                )
            }
            val bookmarkStore = FakeBookmarkStore(initial)
            val viewModel = ChatViewModel(
                repository = FakeRepository(emptyList()),
                preferences = FakePreferences(),
                bookmarkStore = bookmarkStore,
            )
            advanceUntilIdle()

            viewModel.toggleBookmark(ChatMessage(9999, ChatRole.User, "新しいメモ"))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(BookmarkStore.MAX_BOOKMARKS, state.bookmarks.size)
            assertEquals("新しいメモ", state.bookmarks.last().text)
            assertEquals(initial.first().id + 1, state.bookmarks.first().id)
            assertEquals(state.bookmarks, bookmarkStore.savedBookmarks)
        }

    private class FakeRepository(
        private val events: List<SseEvent>,
    ) : ChatRepository {
        var requestCount = 0

        override fun streamMessage(
            baseUrl: String,
            message: String,
            sessionId: String?,
        ): Flow<SseEvent> = flow {
            requestCount++
            events.forEach { emit(it) }
        }
    }

    private class FakePreferences(
        initialSessionId: String? = null,
    ) : ChatPreferencesStore {
        override val serverUrl = MutableStateFlow(ChatPreferences.DEFAULT_SERVER_URL)
        override val sessionId = MutableStateFlow(initialSessionId)
        var savedSessionId: String? = null
        var sessionWasCleared = false

        override suspend fun saveServerUrl(url: String) {
            serverUrl.value = url
        }

        override suspend fun saveSessionId(sessionId: String) {
            savedSessionId = sessionId
            this.sessionId.value = sessionId
        }

        override suspend fun clearSessionId() {
            sessionWasCleared = true
            sessionId.value = null
        }
    }

    private class FakeHistoryStore(
        private val initialMessages: List<ChatMessage>,
    ) : ChatHistoryStore {
        var savedMessages: List<ChatMessage> = emptyList()

        override suspend fun loadMessages(): List<ChatMessage> = initialMessages

        override suspend fun saveMessages(messages: List<ChatMessage>) {
            savedMessages = messages
        }
    }

    private class FakeBookmarkStore(
        private val initialBookmarks: List<Bookmark> = emptyList(),
    ) : BookmarkStore {
        var savedBookmarks: List<Bookmark> = initialBookmarks

        override suspend fun loadBookmarks(): List<Bookmark> = initialBookmarks

        override suspend fun saveBookmarks(bookmarks: List<Bookmark>): List<Bookmark> {
            savedBookmarks = bookmarks.takeLast(BookmarkStore.MAX_BOOKMARKS)
            return savedBookmarks
        }
    }

    private class FailingBookmarkStore : BookmarkStore {
        override suspend fun loadBookmarks(): List<Bookmark> = emptyList()

        override suspend fun saveBookmarks(bookmarks: List<Bookmark>): List<Bookmark> {
            throw IOException("disk full")
        }
    }

    private class ControllableBookmarkStore(
        private val firstSaveStarted: CompletableDeferred<Unit>,
        private val allowFirstSave: CompletableDeferred<Unit>,
    ) : BookmarkStore {
        var savedBookmarks: List<Bookmark> = emptyList()
        private var saveCount = 0

        override suspend fun loadBookmarks(): List<Bookmark> = emptyList()

        override suspend fun saveBookmarks(bookmarks: List<Bookmark>): List<Bookmark> {
            saveCount++
            if (saveCount == 1) {
                firstSaveStarted.complete(Unit)
                allowFirstSave.await()
            }
            savedBookmarks = bookmarks.takeLast(BookmarkStore.MAX_BOOKMARKS)
            return savedBookmarks
        }
    }
}
