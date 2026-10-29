// TdlibMoonStreamBackend.kt
package com.moon.moonstream.backend

import android.content.Context
import com.moon.moonstream.tdlib.AuthState
import com.moon.moonstream.tdlib.TdlibClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi

class TdlibMoonStreamBackend(
    private val context: Context,
    private val client: TdlibClient
) : MoonStreamBackend {

    private val _authState = MutableStateFlow(BackendAuthState.LOGGED_OUT)
    override val authState: StateFlow<BackendAuthState> = _authState.asStateFlow()

    init {
        // Map TDLib auth states → your contract
        // You would collect client.authState in a coroutine and update _authState
        // When AuthState.Ready → BackendAuthState.READY
        // When LoggedOut → LOGGED_OUT
    }

    // Call this once after successful login / session restore
    fun markReady() {
        _authState.value = BackendAuthState.READY
    }

    override suspend fun listChannels(): List<MsChannel> {
        // Implementation sketch:
        // 1. Get chats via GetChats
        // 2. For each, GetChat → filter for channels/supergroups
        // 3. Return MsChannel(id, title)
        return emptyList() // TODO: real implementation
    }

    override suspend fun fetchMessages(
        channelId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<MsMessage> {
        // Use GetChatHistory, filter messages that have document / video / audio
        // Map to MsMessage (fileName, fileSize, mimeType, date)
        return emptyList()
    }

    override suspend fun readFileRange(
        channelId: Long,
        messageId: Long,
        offset: Long,
        length: Int
    ): ByteArray {
        // Download file via DownloadFile + read local path with RandomAccessFile
        // or use the streaming helpers TDLib provides
        return ByteArray(0)
    }

    override suspend fun getMe(): MsProfile {
        val me = /* client.getMe() */ null
        return MsProfile(
            id = me?.id ?: 0,
            firstName = me?.firstName ?: "",
            username = me?.usernames?.activeUsernames?.firstOrNull(),
            phone = me?.phoneNumber ?: ""
        )
    }

    override suspend fun logout() {
        client.logout()
        _authState.value = BackendAuthState.LOGGED_OUT
    }
}
