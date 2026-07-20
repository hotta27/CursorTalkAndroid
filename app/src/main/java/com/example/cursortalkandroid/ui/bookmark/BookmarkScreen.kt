package com.example.cursortalkandroid.ui.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cursortalkandroid.data.model.Bookmark
import com.example.cursortalkandroid.data.model.ChatRole
import com.example.cursortalkandroid.ui.chat.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    bookmarks: List<Bookmark>,
    onRemoveBookmark: (Long) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    onDismissError: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("ブックマーク") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            error?.let { message ->
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
                            Text(message)
                        }
                        TextButton(onClick = onDismissError) {
                            Text("閉じる")
                        }
                    }
                }
            }

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "ブックマークはまだありません。\nチャットの下の「☆ ブックマーク」から保存できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(bookmarks, key = Bookmark::id) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            onRemove = { onRemoveBookmark(bookmark.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.large,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (bookmark.role == ChatRole.User) "自分" else "アシスタント",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        SelectionContainer(modifier = Modifier.padding(top = 4.dp)) {
            MarkdownText(
                markdown = bookmark.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onRemove) {
                Text("削除")
            }
        }
    }
}
