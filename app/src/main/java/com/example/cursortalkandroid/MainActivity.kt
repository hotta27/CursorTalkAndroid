package com.example.cursortalkandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cursortalkandroid.data.ChatPreferences
import com.example.cursortalkandroid.data.DefaultChatRepository
import com.example.cursortalkandroid.data.FileChatHistoryStore
import com.example.cursortalkandroid.data.remote.CursorTalkClient
import com.example.cursortalkandroid.ui.chat.ChatScreen
import com.example.cursortalkandroid.ui.chat.ChatViewModel
import com.example.cursortalkandroid.ui.theme.CursorTalkAndroidTheme

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(
            repository = DefaultChatRepository(CursorTalkClient()),
            preferences = ChatPreferences(applicationContext),
            historyStore = FileChatHistoryStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CursorTalkAndroidTheme {
                val state = chatViewModel.state.collectAsStateWithLifecycle()
                ChatScreen(
                    state = state.value,
                    onInputChange = chatViewModel::updateInput,
                    onSend = chatViewModel::sendMessage,
                    onDismissError = chatViewModel::dismissError,
                    onSaveServerUrl = chatViewModel::saveServerUrl,
                )
            }
        }
    }
}