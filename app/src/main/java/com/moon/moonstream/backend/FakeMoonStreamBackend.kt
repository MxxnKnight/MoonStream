package com.moon.moonstream.backend

import android.content.Context
import com.moon.moonstream.ui.model.fakeChannels
import com.moon.moonstream.ui.model.fakeTitles
import com.moon.moonstream.ui.state.AppUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The pretend Telegram. Implements [MoonStreamBackend] with the app's fake
 * data so every screen works end-to-end with zero network.
 *
 * ROLE (v0.7.0+): this class is a @Preview / development aid ONLY. No
 * production screen reads from it — Library, Detail and Channels all read
 * the Room database built by the real scanner. At startup
 * [BackendProvider.init] still installs it as the placeholder backend, but
 * the real [TdlibMoonStreamBackend] replaces it via
 * [BackendProvider.install] the moment TDLib reports Ready.
 *
 * The ONLY fake-specific entry point is [fakeLogin] (any credentials
 * accepted — it just flips the state). The real implementation will have
 * its own login flow and will never call this.
 */
class FakeMoonStreamBackend(context: Context) : MoonStreamBackend {

    private val appContext = context.applicationContext

    private val _authState = MutableStateFlow(
        if (AppUiState.loggedIn) BackendAuthState.READY else BackendAuthState.LOGGED_OUT,
    )
    override val authState: StateFlow<BackendAuthState> = _authState.asStateFlow()

    /** Fake login: any input accepted. Persists like the old fake flow did. */
    fun fakeLogin() {
        AppUiState.setLoggedIn(appContext)
        AppUiState.addLog("AUTH", "Telegram login accepted (fake)")
        _authState.value = BackendAuthState.READY
    }

    override suspend fun listChannels(): List<MsChannel> =
        fakeChannels.mapIndexed { i, c -> MsChannel(id = 1000L + i, title = c.name) }

    override suspend fun fetchMessages(
        channelId: Long,
        fromMessageId: Long,
        limit: Int,
    ): List<MsMessage> {
        // Pretend every title's streams live in the requested channel, newest
        // first. Here fromMessageId is a 1-based position cursor (the real
        // implementation will use actual Telegram message ids).
        val streams = fakeTitles.flatMap { t ->
            t.allStreams().map { s -> t to s }
        }
        val start = fromMessageId.coerceIn(0, streams.size.toLong()).toInt()
        return streams
            .drop(start)
            .take(limit.coerceIn(1, 100))
            .mapIndexed { i, (t, s) ->
                MsMessage(
                    id = (start + i + 1).toLong(),
                    channelId = channelId,
                    fileName = s.fileName,
                    fileSize = parseSizeMb(s.size) * 1024L * 1024L,
                    mimeType = if (s.fileName.endsWith(".mkv", true)) "video/x-matroska" else null,
                    date = 1_730_000_000L - (start + i) * 3600L,
                )
            }
    }

    override suspend fun readFileRange(
        channelId: Long,
        messageId: Long,
        offset: Long,
        length: Int,
    ): ByteArray {
        // Fake bytes for the future streaming server (TODO Phase 4/5: real).
        return ByteArray(length.coerceIn(1, 1024 * 1024))
    }

    override suspend fun getMe(): MsProfile = MsProfile(
        id = 123456789L,
        firstName = "moon",
        username = "moon",
        phone = "+910000000000",
    )

    override suspend fun logout() {
        AppUiState.setLoggedOut(appContext)
        AppUiState.addLog("AUTH", "Logged out (fake)")
        _authState.value = BackendAuthState.LOGGED_OUT
    }

    override suspend fun uploadBackup(bytes: ByteArray, fileName: String): Long? = 1L

    override suspend fun findLatestBackup(): BackupRef? = null

    override suspend fun downloadBackup(ref: BackupRef): ByteArray? = null

    private fun parseSizeMb(text: String): Long {
        // "2.2 GB" / "750 MB" -> MB
        val parts = text.trim().split(" ")
        val num = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        return when (parts.getOrNull(1)?.uppercase()) {
            "GB", "G" -> (num * 1024).toLong()
            "KB", "K" -> 0L
            else -> num.toLong()
        }
    }
}
