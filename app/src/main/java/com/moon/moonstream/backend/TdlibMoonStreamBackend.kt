package com.moon.moonstream.backend

import android.content.Context
import android.util.Log
import com.moon.moonstream.tdlib.AuthState
import com.moon.moonstream.tdlib.TelegramManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Real Telegram backend powered by TDLib.
 * Bridges TelegramManager's AuthState into BackendAuthState,
 * and implements real channel listing, message fetching, and profile retrieval.
 */
class TdlibMoonStreamBackend(
    private val context: Context,
    private val telegramManager: TelegramManager
) : MoonStreamBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(BackendAuthState.LOGGED_OUT)
    override val authState: StateFlow<BackendAuthState> = _authState.asStateFlow()

    init {
        // Mirror TelegramManager's AuthState into BackendAuthState
        scope.launch {
            telegramManager.authState.collect { tdState ->
                _authState.value = when (tdState) {
                    is AuthState.Ready -> BackendAuthState.READY
                    else -> BackendAuthState.LOGGED_OUT
                }
            }
        }
    }

    override suspend fun listChannels(): List<MsChannel> {
        return try {
            // Fetch up to 500 chats
            val chatsResult = telegramManager.send(TdApi.GetChats(null, 500))
            if (chatsResult.isFailure) {
                Log.e("TdlibBackend", "listChannels GetChats failed", chatsResult.exceptionOrNull())
                return emptyList()
            }
            val chatIds = chatsResult.getOrThrow().chatIds

            val channels = mutableListOf<MsChannel>()
            for (chatId in chatIds) {
                val chatResult = telegramManager.send(TdApi.GetChat(chatId))
                if (chatResult.isFailure) continue
                val chat = chatResult.getOrThrow()
                // Include supergroups that are channels and regular channels
                val isChannel = when (val type = chat.type) {
                    is TdApi.ChatTypeSupergroup -> type.isChannel
                    else -> false
                }
                if (isChannel) {
                    channels.add(MsChannel(id = chat.id, title = chat.title))
                }
            }
            channels
        } catch (e: Exception) {
            Log.e("TdlibBackend", "listChannels exception", e)
            emptyList()
        }
    }

    override suspend fun fetchMessages(
        channelId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<MsMessage> {
        return try {
            val result = telegramManager.send(
                TdApi.GetChatHistory(channelId, fromMessageId, 0, limit, false)
            )
            if (result.isFailure) {
                Log.e("TdlibBackend", "fetchMessages failed", result.exceptionOrNull())
                return emptyList()
            }
            val messages = result.getOrThrow().messages ?: return emptyList()
            messages.mapNotNull { msg ->
                val content = msg.content
                when (content) {
                    is TdApi.MessageDocument -> {
                        val doc = content.document
                        MsMessage(
                            id = msg.id,
                            channelId = channelId,
                            fileName = doc.fileName.ifBlank { "file_${msg.id}" },
                            fileSize = doc.document?.size ?: 0L,
                            mimeType = doc.mimeType.ifBlank { null },
                            date = msg.date.toLong(),
                        )
                    }
                    is TdApi.MessageVideo -> {
                        val video = content.video
                        MsMessage(
                            id = msg.id,
                            channelId = channelId,
                            fileName = video.fileName.ifBlank { "video_${msg.id}.mp4" },
                            fileSize = video.video?.size ?: 0L,
                            mimeType = video.mimeType.ifBlank { "video/mp4" },
                            date = msg.date.toLong(),
                        )
                    }
                    else -> null // skip text, stickers, etc.
                }
            }
        } catch (e: Exception) {
            Log.e("TdlibBackend", "fetchMessages exception", e)
            emptyList()
        }
    }

    override suspend fun readFileRange(
        channelId: Long,
        messageId: Long,
        offset: Long,
        length: Int
    ): ByteArray {
        // TODO(Phase 5): download file via TDLib and read range
        return ByteArray(0)
    }

    override suspend fun getMe(): MsProfile {
        return try {
            val result = telegramManager.send(TdApi.GetMe())
            if (result.isFailure) {
                Log.e("TdlibBackend", "getMe failed", result.exceptionOrNull())
                return MsProfile(id = 0, firstName = "Telegram User", username = null, phone = "")
            }
            val user = result.getOrThrow()
            MsProfile(
                id = user.id,
                firstName = user.firstName,
                username = user.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" },
                phone = user.phoneNumber,
            )
        } catch (e: Exception) {
            Log.e("TdlibBackend", "getMe exception", e)
            MsProfile(id = 0, firstName = "Telegram User", username = null, phone = "")
        }
    }

    override suspend fun logout() {
        telegramManager.logout()
        _authState.value = BackendAuthState.LOGGED_OUT
    }

    private suspend fun getSavedMessagesChatId(): Long? {
        val me = getMe()
        if (me.id == 0L) return null
        val chatResult = telegramManager.send(TdApi.CreatePrivateChat(me.id, false))
        return if (chatResult.isSuccess) chatResult.getOrThrow().id else me.id
    }

    override suspend fun uploadBackup(bytes: ByteArray, fileName: String): Long? {
        val chatId = getSavedMessagesChatId() ?: return null
        val tempFile = java.io.File(context.cacheDir, "upload_" + fileName)
        return try {
            tempFile.writeBytes(bytes)
            val doc = TdApi.InputMessageDocument(
                TdApi.InputFileLocal(tempFile.absolutePath),
                null,
                true,
                TdApi.FormattedText("#MoonStreamBackup MoonStream library backup", emptyArray())
            )
            val sendMsg = TdApi.SendMessage(chatId, null, null, null, null, doc)
            val res = telegramManager.send(sendMsg)
            if (res.isSuccess) res.getOrThrow().id else null
        } catch (e: Exception) {
            Log.e("TdlibBackend", "uploadBackup failed", e)
            null
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun findLatestBackup(): BackupRef? {
        val chatId = getSavedMessagesChatId() ?: return null
        return try {
            val query = TdApi.SearchChatMessages(
                chatId,
                null,
                "#MoonStreamBackup",
                null,
                0L,
                0,
                10,
                null
            )
            val res = telegramManager.send(query)
            if (res.isFailure) return null
            val found = res.getOrThrow()
            val msg = found.messages?.firstOrNull { it.content is TdApi.MessageDocument } ?: return null
            val doc = (msg.content as TdApi.MessageDocument).document
            val fileId = doc.document?.id ?: return null
            BackupRef(messageId = msg.id, fileId = fileId, date = msg.date.toLong())
        } catch (e: Exception) {
            Log.e("TdlibBackend", "findLatestBackup failed", e)
            null
        }
    }

    override suspend fun downloadBackup(ref: BackupRef): ByteArray? {
        return try {
            val downloadQuery = TdApi.DownloadFile(ref.fileId, 1, 0L, 0L, true)
            val res = telegramManager.send(downloadQuery)
            if (res.isFailure) return null
            val file = res.getOrThrow()
            val localPath = file.local?.path
            if (!localPath.isNullOrBlank()) {
                val downloadedFile = java.io.File(localPath)
                if (downloadedFile.exists()) downloadedFile.readBytes() else null
            } else null
        } catch (e: Exception) {
            Log.e("TdlibBackend", "downloadBackup failed", e)
            null
        }
    }
}
