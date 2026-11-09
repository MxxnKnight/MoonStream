package com.moon.moonstream.backend

import android.content.Context
import android.util.Log
import com.moon.moonstream.tdlib.AuthState
import com.moon.moonstream.tdlib.TelegramManager
import com.moon.moonstream.util.AppLog
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
                            caption = content.caption?.text.orEmpty(),
                            editDate = msg.editDate.toLong(),
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
                            caption = content.caption?.text.orEmpty(),
                            editDate = msg.editDate.toLong(),
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

    // Cache (channelId, messageId) -> fileId so seeking requests don't repeatedly
    // fetch the same message. Keyed by BOTH ids — messageIds repeat across
    // channels, so a messageId-only key returns the wrong file.
    private val messageFileIdCache = java.util.concurrent.ConcurrentHashMap<Pair<Long, Long>, Int>()

    override suspend fun readFileRange(
        channelId: Long,
        messageId: Long,
        offset: Long,
        length: Int
    ): ByteArray {
        if (length <= 0) return ByteArray(0)
        return try {
            val fileId = messageFileIdCache.getOrPut(channelId to messageId) {
                val msgRes = telegramManager.send(TdApi.GetMessage(channelId, messageId))
                if (msgRes.isFailure) return ByteArray(0)
                val msg = msgRes.getOrThrow()
                when (val c = msg.content) {
                    is TdApi.MessageDocument -> c.document.document?.id ?: 0
                    is TdApi.MessageVideo -> c.video.video?.id ?: 0
                    else -> 0
                }
            }
            if (fileId == 0) return ByteArray(0)

            // Step 1: Trigger download of the required chunk range synchronously
            val dlRes = telegramManager.send(TdApi.DownloadFile(fileId, 32, offset, length.toLong(), true))
            val localFile = if (dlRes.isSuccess) dlRes.getOrThrow().local else null

            // Step 2: If file is locally available on disk, read directly with RandomAccessFile (fastest)
            val path = localFile?.path
            if (!path.isNullOrBlank()) {
                val f = java.io.File(path)
                if (f.exists() && f.length() > offset) {
                    java.io.RandomAccessFile(f, "r").use { raf ->
                        raf.seek(offset)
                        val bytesToRead = minOf(length.toLong(), f.length() - offset).toInt()
                        val buf = ByteArray(bytesToRead)
                        val read = raf.read(buf)
                        return if (read > 0) {
                            if (read == buf.size) buf else buf.copyOf(read)
                        } else ByteArray(0)
                    }
                }
            }

            // Step 3: Fallback to TDLib's internal file cache reader
            val readRes = telegramManager.send(TdApi.ReadFilePart(fileId, offset, length.toLong()))
            if (readRes.isSuccess) {
                readRes.getOrThrow().data ?: ByteArray(0)
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            Log.e("TdlibBackend", "readFileRange error for msg=${messageId} offset=${offset} length=${length}", e)
            ByteArray(0)
        }
    }

    /**
     * Drop TDLib's own downloaded copy of a message's file.
     *
     * The in-app player keeps its own 8 MB chunk window and deletes chunks as
     * they are watched, so without this TDLib's dir grows to the whole video
     * even though nothing in the app needs those bytes any more. Called by
     * TdByteWindow under its download mutex, and only after the chunks it
     * holds are complete — a later read simply re-downloads its range.
     *
     * [messageFileIdCache] only knows files this process has read, so after a
     * restart (exactly when Clear Cache runs) the id has to be looked up with
     * `GetMessage` first. Returning true on an unknown message would make
     * Clear Cache report success without deleting anything — that was the
     * leak. True now means either "deleted" or "the message holds no file";
     * a failed lookup or delete returns false so the caller can say so.
     */
    override suspend fun deleteCachedFile(channelId: Long, messageId: Long): Boolean {
        val key = channelId to messageId
        return try {
            var fileId = messageFileIdCache[key]
            if (fileId == null) {
                val lookup = lookupFileId(channelId, messageId)
                if (lookup.isFailure) {
                    AppLog.w(
                        "TDSTREAM",
                        "could not look up msg=$messageId: " +
                            "${lookup.exceptionOrNull()?.message}",
                    )
                    return false
                }
                fileId = lookup.getOrThrow()
                if (fileId == 0) {
                    AppLog.i("TDSTREAM", "msg=$messageId holds no video/file — nothing to delete")
                    return true
                }
                messageFileIdCache[key] = fileId
                AppLog.i("TDSTREAM", "resolved fileId=$fileId for msg=$messageId via GetMessage")
            }
            val res = telegramManager.send(TdApi.DeleteFile(fileId))
            if (!res.isSuccess) {
                AppLog.w("TDSTREAM", "DeleteFile failed for msg=$messageId: ${res.exceptionOrNull()?.message}")
            }
            res.isSuccess
        } catch (e: Exception) {
            AppLog.w("TDSTREAM", "DeleteFile threw for msg=$messageId: ${e.message}")
            false
        }
    }

    /**
     * Asks Telegram for one message and pulls out its file id.
     *
     * `Result.success(0)` means the message really has no downloadable file
     * (text, sticker, poll…); a `Result.failure` means the lookup itself did
     * not happen, which is a different answer and must not be read as "clean".
     * Content types match [readFileRange] on purpose: if streaming can use a
     * file here, this can delete it, and nothing else is touched.
     */
    private suspend fun lookupFileId(channelId: Long, messageId: Long): Result<Int> =
        telegramManager.send(TdApi.GetMessage(channelId, messageId)).map { msg ->
            when (val c = msg.content) {
                is TdApi.MessageDocument -> c.document.document?.id ?: 0
                is TdApi.MessageVideo -> c.video.video?.id ?: 0
                else -> 0
            }
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

    /**
     * Saved Messages is the private chat with yourself, so its id is our own
     * user id. (The old version also called createPrivateChat for this — one
     * useless request per backup operation.)
     */
    private suspend fun getSavedMessagesChatId(): Long? {
        val me = getMe()
        return if (me.id == 0L) null else me.id
    }

    /**
     * Uploads the backup document and WAITS until Telegram really has it
     * (moon's 2026-11-09).
     *
     * The old version deleted the temp file immediately after sendMessage()
     * returned — but sendMessage() returns the moment the message is queued;
     * TDLib is still reading the local file at that point. Deleting it killed
     * the upload, Telegram dropped the message, and the app still logged
     * "uploaded new backup msg N" while Saved Messages stayed empty.
     *
     * The file now lives in filesDir (not cacheDir, which the system can clear
     * mid-upload) and is only removed once the remote copy is complete.
     */
    /**
     * Dead-link probe: one GetMessage, and the error text is read instead of
     * swallowed. TDLib answers "Message not found" / "MESSAGE_ID_INVALID"
     * for a deleted message and something else for a failed request — the
     * two must not be confused, or a network hiccup marks the whole library
     * dead.
     */
    override suspend fun messageExists(channelId: Long, messageId: Long): Boolean? {
        return try {
            val res = telegramManager.send(TdApi.GetMessage(channelId, messageId))
            if (res.isSuccess) return true
            val text = res.exceptionOrNull()?.message?.lowercase().orEmpty()
            when {
                text.contains("not found") ||
                    text.contains("message_id_invalid") ||
                    text.contains("chat not found") ||
                    text.contains("channel_invalid") -> false
                else -> null
            }
        } catch (e: Exception) {
            Log.e("TdlibBackend", "messageExists failed", e)
            null
        }
    }

    override suspend fun uploadBackup(bytes: ByteArray, fileName: String): Long? {
        val chatId = getSavedMessagesChatId() ?: return null
        val dir = java.io.File(context.filesDir, "backup_upload").apply { mkdirs() }
        // Clear leftovers from an interrupted earlier run.
        dir.listFiles()?.forEach { if (it.name != fileName) it.delete() }
        val tempFile = java.io.File(dir, fileName)
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
            if (res.isFailure) {
                Log.e("TdlibBackend", "uploadBackup: sendMessage failed: ${res.exceptionOrNull()?.message}")
                tempFile.delete()
                return null
            }
            val msgId = res.getOrThrow().id
            val uploaded = awaitUploadComplete(chatId, msgId)
            if (uploaded) {
                tempFile.delete()
                msgId
            } else {
                // Keep the local file: the message may still land, and keeping
                // it is what lets a later attempt reuse the same bytes.
                Log.e("TdlibBackend", "uploadBackup: msg $msgId never finished uploading")
                com.moon.moonstream.util.AppLog.e(
                    "BACKUP",
                    "Telegram never finished receiving backup msg $msgId — is the connection ok?",
                )
                null
            }
        } catch (e: Exception) {
            Log.e("TdlibBackend", "uploadBackup failed", e)
            null
        }
    }

    /**
     * Polls the just-sent message until its document reports a completed
     * remote copy. Returns false if the message disappears (the upload was
     * cancelled) or the timeout passes.
     */
    private suspend fun awaitUploadComplete(
        chatId: Long,
        messageId: Long,
        timeoutMs: Long = 120_000L,
    ): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val res = try {
                telegramManager.send(TdApi.GetMessage(chatId, messageId))
            } catch (e: Exception) {
                null
            }
            if (res != null) {
                if (res.isFailure) {
                    Log.w("TdlibBackend", "awaitUploadComplete: message $messageId vanished")
                    return false
                }
                val content = res.getOrThrow().content
                if (content is TdApi.MessageDocument) {
                    val remote = content.document.document?.remote
                    if (remote != null && remote.isUploadingCompleted) return true
                }
            }
            kotlinx.coroutines.delay(400)
        }
        return false
    }

    override suspend fun getBackupMessage(messageId: Long): BackupRef? {
        val chatId = getSavedMessagesChatId() ?: return null
        return try {
            // Direct fetch by id (moon's 2026-11-07 fix): unlike the
            // text search, this works the moment the message is sent.
            val res = telegramManager.send(TdApi.GetMessage(chatId, messageId))
            if (res.isFailure) return null
            val msg = res.getOrThrow()
            if (msg.content !is TdApi.MessageDocument) return null
            val doc = (msg.content as TdApi.MessageDocument).document
            val fileId = doc.document?.id ?: return null
            // A message that exists but whose file Telegram hasn't fully
            // received is not a usable backup — the old check said "verified"
            // for it, which is how a broken upload looked successful.
            if (doc.document?.remote?.isUploadingCompleted != true) return null
            BackupRef(messageId = msg.id, fileId = fileId, date = msg.date.toLong())
        } catch (e: Exception) {
            Log.e("TdlibBackend", "getBackupMessage failed", e)
            null
        }
    }

    /**
     * Backup documents found by searching Saved Messages.
     *
     * Text search is the cheap way, but Telegram's search index lags behind
     * new messages — a backup uploaded seconds ago (or, before the upload fix,
     * one that never fully arrived) simply isn't there yet, which made
     * "restore" report "No backup found" while the file was sitting in Saved
     * Messages. [recentBackupDocs] reads the chat history directly as the
     * reliable fallback.
     */
    private suspend fun searchBackupDocs(limit: Int): List<BackupRef> {
        val chatId = getSavedMessagesChatId() ?: return emptyList()
        return try {
            val query = TdApi.SearchChatMessages(
                chatId,
                null,
                "#MoonStreamBackup",
                null,
                0L,
                0,
                limit,
                null
            )
            val res = telegramManager.send(query)
            if (res.isFailure) return emptyList()
            val found = res.getOrThrow()
            (found.messages ?: emptyArray())
                .filter { it.content is TdApi.MessageDocument }
                .mapNotNull { msg ->
                    val doc = (msg.content as TdApi.MessageDocument).document
                    val fileId = doc.document?.id ?: return@mapNotNull null
                    BackupRef(messageId = msg.id, fileId = fileId, date = msg.date.toLong())
                }
        } catch (e: Exception) {
            Log.e("TdlibBackend", "searchBackupDocs failed", e)
            emptyList()
        }
    }

    /**
     * Backup documents in the newest messages of Saved Messages, found
     * without any search: neither the chat nor the message index is involved.
     */
    private suspend fun recentBackupDocs(limit: Int = 60): List<BackupRef> {
        val chatId = getSavedMessagesChatId() ?: return emptyList()
        return try {
            // from_message_id = 0 means "start from the newest message".
            val res = telegramManager.send(TdApi.GetChatHistory(chatId, 0L, 0, limit, false))
            if (res.isFailure) {
                Log.e("TdlibBackend", "recentBackupDocs failed", res.exceptionOrNull())
                return emptyList()
            }
            (res.getOrThrow().messages ?: emptyArray()).mapNotNull { msg ->
                val content = msg.content as? TdApi.MessageDocument ?: return@mapNotNull null
                val doc = content.document
                val looksLikeBackup = doc.fileName.startsWith("MoonStream_backup") ||
                    content.caption?.text.orEmpty().contains("#MoonStreamBackup")
                if (!looksLikeBackup) return@mapNotNull null
                val fileId = doc.document?.id ?: return@mapNotNull null
                BackupRef(messageId = msg.id, fileId = fileId, date = msg.date.toLong())
            }
        } catch (e: Exception) {
            Log.e("TdlibBackend", "recentBackupDocs failed", e)
            emptyList()
        }
    }

    override suspend fun findLatestBackup(): BackupRef? {
        val fromSearch = searchBackupDocs(10)
        val fromHistory = recentBackupDocs()
        return (fromSearch + fromHistory).maxByOrNull { it.date }
    }

    override suspend fun findAllBackups(): List<BackupRef> {
        return (searchBackupDocs(100) + recentBackupDocs())
            .distinctBy { it.messageId }
            .sortedByDescending { it.date }
    }

    override suspend fun deleteBackup(ref: BackupRef): Boolean {
        val chatId = getSavedMessagesChatId() ?: return false
        return try {
            val res = telegramManager.send(
                TdApi.DeleteMessages(chatId, longArrayOf(ref.messageId), true),
            )
            res.isSuccess
        } catch (e: Exception) {
            Log.e("TdlibBackend", "deleteBackup failed", e)
            false
        }
    }

    override suspend fun downloadBackup(ref: BackupRef): ByteArray? {
        return try {
            // DownloadFile returns before the bytes are on disk, so wait for
            // "downloading completed" before reading — otherwise a restore
            // could read a half-written file (same async trap as the upload).
            telegramManager.send(TdApi.DownloadFile(ref.fileId, 1, 0L, 0L, true))
            var path: String? = null
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 120_000L) {
                val res = telegramManager.send(TdApi.GetFile(ref.fileId))
                val file = if (res.isSuccess) res.getOrThrow() else null
                if (file?.local?.isDownloadingCompleted == true) {
                    path = file.local?.path
                    break
                }
                kotlinx.coroutines.delay(300)
            }
            val localPath = path
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
