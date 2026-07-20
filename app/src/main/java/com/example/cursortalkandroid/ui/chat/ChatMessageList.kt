package com.example.cursortalkandroid.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import com.example.cursortalkandroid.ui.theme.AssistantSelectionBackground
import com.example.cursortalkandroid.ui.theme.AssistantSelectionHandle
import com.example.cursortalkandroid.ui.theme.UserChatBubble
import com.example.cursortalkandroid.ui.theme.UserChatOnBubble
import com.example.cursortalkandroid.ui.theme.UserSelectionBackground
import com.example.cursortalkandroid.ui.theme.UserSelectionHandle

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
    var latestBubbleHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(messages.size, latestText, latestBubbleHeightPx) {
        scrollToRevealLatestMessageBottom(listState, messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        items(messages, key = ChatMessage::id) { message ->
            val isLatest = message.id == messages.last().id
            val isActivelyStreaming = isStreaming &&
                message.role == ChatRole.Assistant &&
                isLatest
            ChatBubble(
                message = message,
                showTyping = isActivelyStreaming && message.text.isEmpty(),
                canBookmark = !isActivelyStreaming && message.text.isNotBlank(),
                isBookmarked = message.id in bookmarkedSourceIds,
                onToggleBookmark = { onToggleBookmark(message) },
                modifier = if (isLatest) {
                    Modifier.onSizeChanged { latestBubbleHeightPx = it.height }
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * Keeps the bottom of the latest message visible.
 * [LazyListState.scrollToItem] alone only aligns the item's top, so a bubble
 * taller than the viewport would still clip its growing bottom edge.
 */
private suspend fun scrollToRevealLatestMessageBottom(
    listState: LazyListState,
    lastIndex: Int,
) {
    if (lastIndex < 0) return

    // Wait one frame so LazyColumn has measured the updated bubble height.
    withFrameNanos { }

    val layoutInfo = listState.layoutInfo
    val lastItem = layoutInfo.visibleItemsInfo.find { it.index == lastIndex }
    if (lastItem == null) {
        listState.scrollToItem(lastIndex)
        withFrameNanos { }
    }

    val settledInfo = listState.layoutInfo
    val settledItem = settledInfo.visibleItemsInfo.find { it.index == lastIndex } ?: return
    val overflow = (settledItem.offset + settledItem.size) - settledInfo.viewportEndOffset
    if (overflow > 0) {
        listState.scrollBy(overflow.toFloat())
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    showTyping: Boolean,
    canBookmark: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ChatRole.User
    // primary と分離した専用色にし、選択ハイライトが同化しないようにする
    val bubbleColor = if (isUser) {
        UserChatBubble
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        UserChatOnBubble
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape: Shape = MaterialTheme.shapes.large
    val selectionColors = remember(isUser) {
        if (isUser) {
            TextSelectionColors(
                handleColor = UserSelectionHandle,
                backgroundColor = UserSelectionBackground,
            )
        } else {
            TextSelectionColors(
                handleColor = AssistantSelectionHandle,
                backgroundColor = AssistantSelectionBackground,
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
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
                else -> CompositionLocalProvider(
                    LocalTextSelectionColors provides selectionColors,
                ) {
                    SelectionContainer {
                        if (isUser) {
                            Text(message.text, color = contentColor)
                        } else {
                            MarkdownText(message.text, color = contentColor)
                        }
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
