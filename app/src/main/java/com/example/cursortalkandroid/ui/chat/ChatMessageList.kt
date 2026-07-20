package com.example.cursortalkandroid.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    bookmarkedSourceIds: Set<Long> = emptySet(),
    onToggleBookmark: (ChatMessage) -> Unit = {},
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "メッセージを送信して会話を始めましょう。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    // 起動時・履歴復元時は先頭からアニメーションせず、最新メッセージ位置へ即ジャンプする
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0),
    )
    val latestText = messages.lastOrNull()?.text.orEmpty()
    LaunchedEffect(messages.size, latestText) {
        listState.scrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        items(messages, key = ChatMessage::id) { message ->
            val isActivelyStreaming = isStreaming &&
                message.role == ChatRole.Assistant &&
                message == messages.last()
            ChatBubble(
                message = message,
                showTyping = isActivelyStreaming && message.text.isEmpty(),
                canBookmark = !isActivelyStreaming && message.text.isNotBlank(),
                isBookmarked = message.id in bookmarkedSourceIds,
                onToggleBookmark = { onToggleBookmark(message) },
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    showTyping: Boolean,
    canBookmark: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
    val isUser = message.role == ChatRole.User
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape: Shape = MaterialTheme.shapes.large

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.86f)
                .background(bubbleColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when {
                showTyping -> Text("…", color = contentColor)
                else -> SelectionContainer {
                    if (isUser) {
                        Text(message.text, color = contentColor)
                    } else {
                        MarkdownText(message.text, color = contentColor)
                    }
                }
            }
        }
        if (canBookmark) {
            TextButton(onClick = onToggleBookmark) {
                Text(if (isBookmarked) "★ 保存済み" else "☆ ブックマーク")
            }
        }
    }
}
