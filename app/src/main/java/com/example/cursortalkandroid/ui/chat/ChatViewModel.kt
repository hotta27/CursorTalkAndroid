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
    val error: String? = null,
    val serverUrl: String = ChatPreferences.DEFAULT_SERVER_URL,
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val preferences: ChatPreferencesStore,
    private val historyStore: ChatHistoryStore = EmptyChatHistoryStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()
    private val nextMessageId = AtomicLong(0)
    private var sessionId: String? = null
    private var historySaveJob: Job? = null
    private var lastAssistantDeltaAtMs: Long? = null

    init {
        viewModelScope.launch {
            try {
                sessionId = preferences.sessionId.first()
                val messages = historyStore.loadMessages()
                nextMessageId.set(messages.maxOfOrNull(ChatMessage::id) ?: 0)
                mutableState.update {
                    it.copy(messages = messages, isLoadingHistory = false)
                }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(repository, preferences, historyStore) as T
        }
    }

    private companion object {
        const val HISTORY_SAVE_DEBOUNCE_MS = 250L
        const val BUBBLE_SPLIT_GAP_MS = 3_000L
    }
}
