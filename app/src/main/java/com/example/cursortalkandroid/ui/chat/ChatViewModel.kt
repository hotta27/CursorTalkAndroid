package com.example.cursortalkandroid.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cursortalkandroid.data.BookmarkStore
import com.example.cursortalkandroid.data.ChatPreferences
import com.example.cursortalkandroid.data.ChatPreferencesStore
import com.example.cursortalkandroid.data.ChatHistoryStore
import com.example.cursortalkandroid.data.ChatRepository
import com.example.cursortalkandroid.data.EmptyBookmarkStore
import com.example.cursortalkandroid.data.EmptyChatHistoryStore
import com.example.cursortalkandroid.data.model.Bookmark
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import com.example.cursortalkandroid.data.model.SseEvent
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val isLoadingHistory: Boolean = true,
    val error: String? = null,
    val serverUrl: String = ChatPreferences.DEFAULT_SERVER_URL,
) {
    val bookmarkedSourceIds: Set<Long>
        get() = bookmarks.mapTo(mutableSetOf(), Bookmark::sourceMessageId)
}

class ChatViewModel(
    private val repository: ChatRepository,
    private val preferences: ChatPreferencesStore,
    private val historyStore: ChatHistoryStore = EmptyChatHistoryStore,
    private val bookmarkStore: BookmarkStore = EmptyBookmarkStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()
    private val nextMessageId = AtomicLong(0)
    private val nextBookmarkId = AtomicLong(0)
    private var sessionId: String? = null
    private var historySaveJob: Job? = null
    private var lastAssistantDeltaAtMs: Long? = null
    private val bookmarkPersistMutex = Mutex()
    private var lastPersistedBookmarks: List<Bookmark> = emptyList()

    init {
        viewModelScope.launch {
            var messages = emptyList<ChatMessage>()
            var bookmarks = emptyList<Bookmark>()
            var historyError: String? = null
            var bookmarkError: String? = null

            try {
                sessionId = preferences.sessionId.first()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // sessionId が取れなくても履歴・ブックマークの読み込みは続行する
            }

            try {
                messages = historyStore.loadMessages()
                nextMessageId.set(messages.maxOfOrNull(ChatMessage::id) ?: 0)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                historyError = "保存した会話を読み込めませんでした。"
            }

            try {
                bookmarks = bookmarkStore.loadBookmarks()
                nextBookmarkId.set(bookmarks.maxOfOrNull(Bookmark::id) ?: 0)
                lastPersistedBookmarks = bookmarks
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                bookmarkError = "保存したブックマークを読み込めませんでした。"
            }

            mutableState.update {
                it.copy(
                    messages = messages,
                    bookmarks = bookmarks,
                    isLoadingHistory = false,
                    error = historyError ?: bookmarkError,
                )
            }
        }
        viewModelScope.launch {
            preferences.serverUrl.collect { url ->
                mutableState.update { it.copy(serverUrl = url) }
            }
        }
    }

    fun updateInput(value: String) {
        mutableState.update { it.copy(input = value) }
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    fun toggleBookmark(message: ChatMessage) {
        val snapshot = mutableState.value
        val source = snapshot.messages.firstOrNull { it.id == message.id } ?: message
        if (source.text.isBlank() || isActivelyStreamingMessage(snapshot, source.id)) return

        val current = snapshot.bookmarks
        val existing = current.firstOrNull { it.sourceMessageId == source.id }
        val updated = if (existing != null) {
            current - existing
        } else {
            (current + Bookmark(
                id = nextBookmarkId.incrementAndGet(),
                sourceMessageId = source.id,
                role = source.role,
                text = source.text,
                savedAt = System.currentTimeMillis(),
            )).takeLast(BookmarkStore.MAX_BOOKMARKS)
        }
        mutableState.update { it.copy(bookmarks = updated) }
        persistBookmarks()
    }

    fun removeBookmark(bookmarkId: Long) {
        val updated = mutableState.value.bookmarks.filterNot { it.id == bookmarkId }
        mutableState.update { it.copy(bookmarks = updated) }
        persistBookmarks()
    }

    private fun isActivelyStreamingMessage(state: ChatUiState, messageId: Long): Boolean {
        if (!state.isStreaming) return false
        val last = state.messages.lastOrNull() ?: return false
        return last.id == messageId && last.role == ChatRole.Assistant
    }

    private fun persistBookmarks() {
        viewModelScope.launch {
            bookmarkPersistMutex.withLock {
                val toSave = mutableState.value.bookmarks
                try {
                    val retained = bookmarkStore.saveBookmarks(toSave)
                    lastPersistedBookmarks = retained
                    mutableState.update { current ->
                        if (current.bookmarks == toSave) {
                            current.copy(bookmarks = retained)
                        } else {
                            current
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    mutableState.update { current ->
                        if (current.bookmarks == toSave) {
                            current.copy(
                                bookmarks = lastPersistedBookmarks,
                                error = "ブックマークを端末へ保存できませんでした。",
                            )
                        } else {
                            current.copy(error = "ブックマークを端末へ保存できませんでした。")
                        }
                    }
                }
            }
        }
    }

    fun saveServerUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim().trimEnd('/')
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || parsed.scheme !in setOf("http", "https")) {
            mutableState.update { it.copy(error = "http または https の正しいURLを入力してください。") }
            return false
        }

        mutableState.update { it.copy(serverUrl = url, error = null) }
        sessionId = null
        viewModelScope.launch {
            preferences.saveServerUrl(url)
            preferences.clearSessionId()
        }
        return true
    }

    fun sendMessage() {
        val snapshot = mutableState.value
        val text = snapshot.input.trim()
        if (text.isEmpty() || snapshot.isStreaming || snapshot.isLoadingHistory) return

        val userId = nextMessageId.incrementAndGet()
        val typingPlaceholderId = nextMessageId.incrementAndGet()
        val userMessage = ChatMessage(
            id = userId,
            role = ChatRole.User,
            text = text,
        )
        val typingPlaceholder = ChatMessage(
            id = typingPlaceholderId,
            role = ChatRole.Assistant,
            text = "",
        )
        lastAssistantDeltaAtMs = null
        mutableState.update {
            it.copy(
                messages = it.messages + userMessage + typingPlaceholder,
                input = "",
                isStreaming = true,
                error = null,
            )
        }
        scheduleHistorySave()

        viewModelScope.launch {
            try {
                repository.streamMessage(snapshot.serverUrl, text, sessionId).collect { event ->
                    when (event) {
                        is SseEvent.Meta -> storeSessionId(event.sessionId)
                        is SseEvent.Delta -> appendAssistantDelta(event.text)
                        is SseEvent.Done -> storeSessionId(event.sessionId)
                        is SseEvent.Error -> mutableState.update {
                            it.copy(error = event.message)
                        }
                    }
                }
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(error = exception.message ?: "応答の取得に失敗しました。")
                }
            } finally {
                lastAssistantDeltaAtMs = null
                mutableState.update { current ->
                    current.copy(
                        messages = current.messages.withoutTrailingEmptyAssistant(),
                        isStreaming = false,
                    )
                }
                scheduleHistorySave(immediate = true)
            }
        }
    }

    private fun appendAssistantDelta(text: String) {
        if (text.isEmpty()) return
        val now = nowMillis()
        mutableState.update { current ->
            val messages = current.messages.toMutableList()
            val last = messages.lastOrNull()
            val continueSameBubble = last != null &&
                last.role == ChatRole.Assistant &&
                (
                    last.text.isEmpty() ||
                        lastAssistantDeltaAtMs?.let { now - it < BUBBLE_SPLIT_GAP_MS } == true
                    )
            if (continueSameBubble) {
                messages[messages.lastIndex] = last.copy(text = last.text + text)
            } else {
                messages += ChatMessage(
                    id = nextMessageId.incrementAndGet(),
                    role = ChatRole.Assistant,
                    text = text,
                )
            }
            current.copy(messages = messages)
        }
        lastAssistantDeltaAtMs = now
        scheduleHistorySave()
    }

    private fun List<ChatMessage>.withoutTrailingEmptyAssistant(): List<ChatMessage> {
        val last = lastOrNull() ?: return this
        return if (last.role == ChatRole.Assistant && last.text.isEmpty()) dropLast(1) else this
    }

    private fun storeSessionId(value: String) {
        sessionId = value
        viewModelScope.launch { preferences.saveSessionId(value) }
    }

    private fun scheduleHistorySave(immediate: Boolean = false) {
        historySaveJob?.cancel()
        historySaveJob = viewModelScope.launch {
            if (!immediate) delay(HISTORY_SAVE_DEBOUNCE_MS)
            try {
                historyStore.saveMessages(mutableState.value.messages)
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(error = "会話を端末へ保存できませんでした。")
                }
            }
        }
    }

    class Factory(
        private val repository: ChatRepository,
        private val preferences: ChatPreferencesStore,
        private val historyStore: ChatHistoryStore,
        private val bookmarkStore: BookmarkStore = EmptyBookmarkStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(repository, preferences, historyStore, bookmarkStore) as T
        }
    }

    private companion object {
        const val HISTORY_SAVE_DEBOUNCE_MS = 250L
        const val BUBBLE_SPLIT_GAP_MS = 3_000L
    }
}
