package com.example.cursortalkandroid.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cursortalkandroid.data.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissError: () -> Unit,
    onSaveServerUrl: (String) -> Boolean,
    modifier: Modifier = Modifier,
    onToggleBookmark: (ChatMessage) -> Unit = {},
) {
    var showSettings by remember { mutableStateOf(false) }
    var serverUrlDraft by remember(state.serverUrl, showSettings) {
        mutableStateOf(state.serverUrl)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("チャット") },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Text("接続設定")
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                input = state.input,
                isStreaming = state.isStreaming || state.isLoadingHistory,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SelectionContainer {
                            Text(error)
                        }
                        TextButton(onClick = onDismissError) {
                            Text("閉じる")
                        }
                    }
                }
            }
            ChatMessageList(
                messages = state.messages,
                isStreaming = state.isStreaming,
                modifier = Modifier.weight(1f),
                bookmarkedSourceIds = state.bookmarkedSourceIds,
                onToggleBookmark = onToggleBookmark,
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("CursorTalk接続先") },
            text = {
                Column {
                    Text("エミュレータからPCへ接続する場合は 10.0.2.2 を使用します。")
                    OutlinedTextField(
                        value = serverUrlDraft,
                        onValueChange = { serverUrlDraft = it },
                        label = { Text("サーバーURL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onSaveServerUrl(serverUrlDraft)) showSettings = false
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}
