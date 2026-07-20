package com.example.cursortalkandroid.data

import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.SseEvent
import com.example.cursortalkandroid.data.remote.CursorTalkClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun streamMessage(
        baseUrl: String,
        message: String,
        sessionId: String?,
    ): Flow<SseEvent>

    /**
     * サーバーからチャット履歴を取得する。
     *
     * 戻り値が `null` のときはリモートAPI未実装のため、呼び出し側でローカル履歴へフォールバックする。
     */
    suspend fun fetchHistory(
        baseUrl: String,
        sessionId: String?,
    ): List<ChatMessage>?
}

class DefaultChatRepository(
    private val client: CursorTalkClient,
) : ChatRepository {
    override fun streamMessage(
        baseUrl: String,
        message: String,
        sessionId: String?,
    ): Flow<SseEvent> = client.streamMessage(baseUrl, message, sessionId)

    override suspend fun fetchHistory(
        baseUrl: String,
        sessionId: String?,
    ): List<ChatMessage>? {
        // 仮実装: 履歴取得APIが未提供のため、通信レイテンシだけ再現して null を返す。
        // 本物の GET /chat/history などが用意できたら、ここでパース結果を返す。
        delay(MOCK_HISTORY_FETCH_DELAY_MS)
        return null
    }

    private companion object {
        const val MOCK_HISTORY_FETCH_DELAY_MS = 600L
    }
}
