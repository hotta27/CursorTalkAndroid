package com.example.cursortalkandroid.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cursortalkandroid.data.ChatPreferences
import com.example.cursortalkandroid.data.ChatPreferencesStore
import com.example.cursortalkandroid.data.ChatHistoryStore
import com.example.cursortalkandroid.data.ChatRepository
import com.example.cursortalkandroid.data.EmptyChatHistoryStore
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import com.example.cursortalkandroid.data.model.SseEvent
import com.example.cursortalkandroid.data.retainRecentChatMessages
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val isLoadingHistory: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val serverUrl: String = ChatPreferences.DEFAULT_SERVER_URL,
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val preferences: ChatPreferencesStore,
    private val historyStore: ChatHistoryStore = EmptyChatHistoryStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()
    private val nextMessageId = AtomicLong(0)
    private var sessionId: String? = null
    private var historySaveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                sessionId = preferences.sessionId.first()
                applyMessages(historyStore.loadMessages(), loading = false)
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isLoadingHistory = false,
                        error = "保存した会話を読み込めませんでした。",
                    )
                }
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

    fun refreshHistory() {
        val snapshot = mutableState.value
        if (snapshot.isRefreshing || snapshot.isStreaming || snapshot.isLoadingHistory) return

        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val remoteMessages = repository.fetchHistory(snapshot.serverUrl, sessionId)
                val messages = remoteMessages ?: historyStore.loadMessages()
                applyMessages(messages, loading = false)
                if (remoteMessages != null) {
                    scheduleHistorySave(immediate = true)
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(error = "チャット履歴の更新に失敗しました。")
                }
            } finally {
                mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun sendMessage() {
        val snapshot = mutableState.value
        val text = snapshot.input.trim()
        if (text.isEmpty() || snapshot.isStreaming || snapshot.isLoadingHistory) return

        val userId = nextMessageId.incrementAndGet()
        val assistantId = nextMessageId.incrementAndGet()
        val userMessage = ChatMessage(
            id = userId,
            role = ChatRole.User,
            text = text,
        )
        val assistantMessage = ChatMessage(
            id = assistantId,
            role = ChatRole.Assistant,
            text = "",
        )
        mutableState.update {
            it.copy(
                messages = retainRecentChatMessages(it.messages + userMessage + assistantMessage),
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
                        is SseEvent.Delta -> appendAssistantText(assistantId, event.text)
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
                mutableState.update { it.copy(isStreaming = false) }
                scheduleHistorySave(immediate = true)
            }
        }
    }

    private fun applyMessages(messages: List<ChatMessage>, loading: Boolean) {
        val retained = retainRecentChatMessages(messages)
        nextMessageId.set(retained.maxOfOrNull(ChatMessage::id) ?: 0)
        mutableState.update {
            it.copy(
                messages = retained,
                isLoadingHistory = loading,
            )
        }
    }

    private fun appendAssistantText(messageId: Long, text: String) {
        mutableState.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id == messageId) message.copy(text = message.text + text) else message
                },
            )
        }
        scheduleHistorySave()
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(repository, preferences, historyStore) as T
        }
    }

    private companion object {
        const val HISTORY_SAVE_DEBOUNCE_MS = 250L
    }
}
