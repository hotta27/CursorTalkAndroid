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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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

        override suspend fun saveBookmarks(bookmarks: List<Bookmark>) {
            savedBookmarks = bookmarks
        }
    }
}
