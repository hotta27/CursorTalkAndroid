package com.example.cursortalkandroid.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
                    onInputChange = {},
                    onSend = {},
                    onDismissError = {},
                    onSaveServerUrl = { true },
                )
            }
        }

        composeRule.onNodeWithText("質問").assertIsDisplayed()
        composeRule.onNodeWithText("…").assertIsDisplayed()
        composeRule.onNodeWithText("接続エラー").assertIsDisplayed()
        composeRule.onNodeWithText("送信").assertIsNotEnabled()
    }

    @Test
    fun displaysEmptyState() {
        composeRule.setContent {
            CursorTalkAndroidTheme {
                ChatScreen(
                    state = ChatUiState(),
                    onInputChange = {},
                    onSend = {},
                    onDismissError = {},
                    onSaveServerUrl = { true },
                )
            }
        }

        composeRule.onNodeWithText("メッセージを送信して会話を始めましょう。")
            .assertIsDisplayed()
    }
}
