package com.example.cursortalkandroid.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.cursortalkandroid.data.model.ChatMessage
import com.example.cursortalkandroid.data.model.ChatRole
import com.example.cursortalkandroid.ui.theme.CursorTalkAndroidTheme
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysMessagesStreamingStateAndError() {
        composeRule.setContent {
            CursorTalkAndroidTheme {
                Column {
                    ChatScreen(
                        state = ChatUiState(
                            messages = listOf(
                                ChatMessage(1, ChatRole.User, "質問"),
                                ChatMessage(2, ChatRole.Assistant, ""),
                            ),
                            input = "入力中",
                            isStreaming = true,
                            error = "接続エラー",
                        ),
                        onDismissError = {},
                        onSaveServerUrl = { true },
                    )
                    ChatInputBar(
                        input = "入力中",
                        isStreaming = true,
                        onInputChange = {},
                        onSend = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("質問").assertIsDisplayed()
        composeRule.onNodeWithText("…").assertIsDisplayed()
        composeRule.onNodeWithText("接続エラー").assertIsDisplayed()
        composeRule.onNodeWithText("送信").assertIsNotEnabled()
        composeRule.onAllNodesWithText("☆ ブックマーク").assertCountEquals(0)
    }

    @Test
    fun hidesBookmarkWhileAssistantReplyIsStreaming() {
        composeRule.setContent {
            CursorTalkAndroidTheme {
                ChatScreen(
                    state = ChatUiState(
                        messages = listOf(
                            ChatMessage(1, ChatRole.User, "質問"),
                            ChatMessage(2, ChatRole.Assistant, "途中までの回答"),
                        ),
                        isStreaming = true,
                    ),
                    onDismissError = {},
                    onSaveServerUrl = { true },
                )
            }
        }

        composeRule.onNodeWithText("途中までの回答").assertIsDisplayed()
        composeRule.onAllNodesWithText("☆ ブックマーク").assertCountEquals(0)
    }

    @Test
    fun displaysEmptyState() {
        composeRule.setContent {
            CursorTalkAndroidTheme {
                ChatScreen(
                    state = ChatUiState(),
                    onDismissError = {},
                    onSaveServerUrl = { true },
                )
            }
        }

        composeRule.onNodeWithText("メッセージを送信して会話を始めましょう。")
            .assertIsDisplayed()
    }
}
