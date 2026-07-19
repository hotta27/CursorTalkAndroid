package com.example.cursortalkandroid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.chatDataStore by preferencesDataStore(name = "chat_settings")

interface ChatPreferencesStore {
    val serverUrl: Flow<String>
    val sessionId: Flow<String?>

    suspend fun saveServerUrl(url: String)
    suspend fun saveSessionId(sessionId: String)
    suspend fun clearSessionId()
}

class ChatPreferences(
    private val context: Context,
) : ChatPreferencesStore {
    private val preferences = context.chatDataStore.data.catch { exception ->
        if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
        else throw exception
    }

    override val serverUrl: Flow<String> = preferences.map {
        it[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    override val sessionId: Flow<String?> = preferences.map { it[SESSION_ID] }

    override suspend fun saveServerUrl(url: String) {
        context.chatDataStore.edit { it[SERVER_URL] = url }
    }

    override suspend fun saveSessionId(sessionId: String) {
        context.chatDataStore.edit { it[SESSION_ID] = sessionId }
    }

    override suspend fun clearSessionId() {
        context.chatDataStore.edit { it.remove(SESSION_ID) }
    }

    companion object {
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000"
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SESSION_ID = stringPreferencesKey("session_id")
    }
}
