package com.example.cursortalkandroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.ui.bookmark.BookmarkScreen
import com.example.cursortalkandroid.ui.chat.ChatInputBar
import com.example.cursortalkandroid.ui.chat.ChatScreen
import com.example.cursortalkandroid.ui.chat.ChatUiState

private enum class AppTab(val label: String, val icon: String) {
    Bookmarks("ブックマーク", "🔖"),
    Chat("チャット", "💬"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CursorTalkApp(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissError: () -> Unit,
    onSaveServerUrl: (String) -> Boolean,
    onToggleBookmark: (ChatMessage) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Chat) }
    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                if (selectedTab == AppTab.Chat) {
                    ChatInputBar(
                        input = state.input,
                        isStreaming = state.isStreaming || state.isLoadingHistory,
                        onInputChange = onInputChange,
                        onSend = onSend,
                    )
                }
                if (!imeVisible) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Text(tab.icon) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                AppTab.Bookmarks -> BookmarkScreen(
                    bookmarks = state.bookmarks,
                    onRemoveBookmark = onRemoveBookmark,
                    error = state.error,
                    onDismissError = onDismissError,
                )

                AppTab.Chat -> ChatScreen(
                    state = state,
                    onDismissError = onDismissError,
                    onSaveServerUrl = onSaveServerUrl,
                    onToggleBookmark = onToggleBookmark,
                )
            }
        }
    }
}
