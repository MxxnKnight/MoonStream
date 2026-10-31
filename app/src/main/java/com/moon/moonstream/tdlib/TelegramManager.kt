package com.moon.moonstream.tdlib

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class TelegramManager private constructor(private val context: Context) {

    private var client: Client? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var apiId: Int = 0
    private var apiHash: String = ""
    private var isWaitingForParameters = false

    init {
        val prefs = context.getSharedPreferences("moonstream_tdlib", Context.MODE_PRIVATE)
        apiId = prefs.getInt("api_id", 0)
        apiHash = prefs.getString("api_hash", "") ?: ""
    }

    fun setApiCredentials(apiId: Int, apiHash: String) {
        this.apiId = apiId
        this.apiHash = apiHash
        context.getSharedPreferences("moonstream_tdlib", Context.MODE_PRIVATE)
            .edit()
            .putInt("api_id", apiId)
            .putString("api_hash", apiHash)
            .apply()

        if (isWaitingForParameters && apiId != 0 && apiHash.isNotBlank()) {
            sendParameters()
        }
    }

    fun start() {
        if (client != null) return

        try { client = Client.create({ update -> handleUpdate(update) }, { error -> Log.e("TelegramManager", "Update exception", error) }, { error -> Log.e("TelegramManager", "Default exception", error) }) } catch (t: Throwable) { Log.e("TelegramManager", "Failed to start TDLib client", t) }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                when (val state = update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        isWaitingForParameters = true
                        // Only send parameters if we already have api credentials
                        if (apiId != 0 && apiHash.isNotBlank()) {
                            sendParameters()
                        }
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        isWaitingForParameters = false
                        _authState.value = AuthState.WaitingPhoneNumber
                    }
                    is TdApi.AuthorizationStateWaitCode -> {
                        _authState.value = AuthState.WaitingCode
                    }
                    is TdApi.AuthorizationStateWaitPassword -> {
                        _authState.value = AuthState.WaitingPassword(state.passwordHint ?: "")
                    }
                    is TdApi.AuthorizationStateReady -> {
                        _authState.value = AuthState.Ready
                    }
                    is TdApi.AuthorizationStateLoggingOut -> {
                        _authState.value = AuthState.LoggedOut
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        _authState.value = AuthState.LoggedOut
                        client = null
                    }
                    else -> {}
                }
            }
        }
    }

    private fun sendParameters() {
        isWaitingForParameters = false
        val parameters = TdApi.SetTdlibParameters().apply {
            databaseDirectory = context.filesDir.absolutePath + "/tdlib"
            filesDirectory = context.filesDir.absolutePath + "/tdlib_files"
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = this@TelegramManager.apiId
            apiHash = this@TelegramManager.apiHash
            systemLanguageCode = "en"
            deviceModel = "Android"
            systemVersion = "Android"
            applicationVersion = "0.5.2"
        }
        client?.send(parameters, null)
    }

    fun setPhoneNumber(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null), null)
    }

    fun checkCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), null)
    }

    fun checkPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password), null)
    }

    fun logout() {
        client?.send(TdApi.LogOut(), null)
    }

    suspend fun <T : TdApi.Object> send(query: TdApi.Function<T>): Result<T> =
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) {
                cont.resume(Result.failure(IllegalStateException("TDLib client is not started")))
                return@suspendCancellableCoroutine
            }
            c.send(query) { result ->
                if (result is TdApi.Error) {
                    cont.resume(Result.failure(Exception("TDLib error ${result.code}: ${result.message}")))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(Result.success(result as T))
                }
            }
        }

    companion object {
        @Volatile
        private var INSTANCE: TelegramManager? = null

        fun getInstance(context: Context): TelegramManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelegramManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object WaitingPhoneNumber : AuthState()
    object WaitingCode : AuthState()
    data class WaitingPassword(val hint: String) : AuthState()
    object Ready : AuthState()
    object LoggedOut : AuthState()
}


