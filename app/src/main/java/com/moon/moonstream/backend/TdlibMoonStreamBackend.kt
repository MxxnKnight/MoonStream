package com.moon.moonstream.backend

import android.content.Context
import com.moon.moonstream.tdlib.TelegramManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real Telegram backend.
 * For now it only handles login state.
 * listChannels / fetchMessages / etc. will be added later.
 */
class TdlibMoonStreamBackend(
    private val context: Context,
    private val telegramManager: TelegramManager
) : MoonStreamBackend {

    private val _authState = MutableStateFlow(BackendAuthState.READY)
    override val authState: StateFlow<BackendAuthState> = _authState.asStateFlow()

    override suspend fun listChannels(): List<MsChannel> {
        // TODO: Implement later
        return emptyList()
    }

    override suspend fun fetchMessages(
        channelId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<MsMessage> {
        // TODO: Implement later
        return emptyList()
    }

    override suspend fun readFileRange(
        channelId: Long,
        messageId: Long,
        offset: Long,
        length: Int
    ): ByteArray {
        // TODO: Implement later
        return ByteArray(0)
    }

    override suspend fun getMe(): MsProfile {
        // TODO: Implement later
        return MsProfile(
            id = 0,
            firstName = "Telegram User",
            username = null,
            phone = ""
        )
    }

    override suspend fun logout() {
        telegramManager.logout()
        _authState.value = BackendAuthState.LOGGED_OUT
    }
}
