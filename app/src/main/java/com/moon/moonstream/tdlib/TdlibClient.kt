// TdlibClient.kt
package com.moon.moonstream.tdlib

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thin wrapper around TDLib Client.
 * Handles authorization states and provides suspend helpers.
 */
class TdlibClient(private val context: Context) {

    private val client = Client.create(
        { update -> handleUpdate(update) },
        { error -> Log.e(TAG, "TDLib error: $error") },
        { exception -> Log.e(TAG, "TDLib exception", exception) }
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<TdApi.Object>>()

    private val _authState = MutableStateFlow<AuthState>(AuthState.WaitingTdlibParameters)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // You must obtain these from https://my.telegram.org
    // NEVER commit real values. Prefer BuildConfig or encrypted SharedPreferences.
    private var apiId: Int = 0
    private var apiHash: String = ""

    fun setCredentials(apiId: Int, apiHash: String) {
        this.apiId = apiId
        this.apiHash = apiHash
    }

    init {
        // Start the TDLib event loop
        scope.launch {
            while (isActive) {
                val response = client.receive(1.0) // 1 second timeout
                if (response != null) {
                    val id = response.id
                    if (id != 0L) {
                        pending.remove(id)?.complete(response)
                    }
                }
            }
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                when (val state = update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        _authState.value = AuthState.WaitingTdlibParameters
                        sendParameters()
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        _authState.value = AuthState.WaitingPhoneNumber
                    }
                    is TdApi.AuthorizationStateWaitCode -> {
                        _authState.value = AuthState.WaitingCode(state.codeInfo)
                    }
                    is TdApi.AuthorizationStateWaitPassword -> {
                        _authState.value = AuthState.WaitingPassword(state.passwordHint ?: "")
                    }
                    is TdApi.AuthorizationStateReady -> {
                        _authState.value = AuthState.Ready
                    }
                    is TdApi.AuthorizationStateLoggingOut,
                    is TdApi.AuthorizationStateClosing,
                    is TdApi.AuthorizationStateClosed -> {
                        _authState.value = AuthState.LoggedOut
                    }
                    else -> { /* other intermediate states */ }
                }
            }
            // You can also handle UpdateNewMessage, UpdateFile, etc. later
        }
    }

    private fun sendParameters() {
        val params = TdApi.SetTdlibParameters().apply {
            databaseDirectory = context.filesDir.absolutePath + "/tdlib"
            filesDirectory = context.filesDir.absolutePath + "/tdlib_files"
            useMessageDatabase = true
            useSecretChats = false
            apiId = this@TdlibClient.apiId
            apiHash = this@TdlibClient.apiHash
            systemLanguageCode = "en"
            deviceModel = "Android"
            systemVersion = "Android"
            applicationVersion = "0.5.2"
            enableStorageOptimizer = true
        }
        send(params)
    }

    // ---------- Public auth API ----------

    suspend fun setPhoneNumber(phone: String): Result<Unit> = runCatching {
        send(TdApi.SetAuthenticationPhoneNumber(phone, null)).await()
    }

    suspend fun checkCode(code: String): Result<Unit> = runCatching {
        send(TdApi.CheckAuthenticationCode(code)).await()
    }

    suspend fun checkPassword(password: String): Result<Unit> = runCatching {
        send(TdApi.CheckAuthenticationPassword(password)).await()
    }

    suspend fun logout() {
        send(TdApi.LogOut()).await()
    }

    // ---------- Generic send helper ----------

    private fun send(query: TdApi.Function): CompletableDeferred<TdApi.Object> {
        val deferred = CompletableDeferred<TdApi.Object>()
        val id = requestId.getAndIncrement()
        pending[id] = deferred
        client.send(query, id) // TDLib Java binding style
        return deferred
    }

    // Convenience for the ones that return a specific type
    private suspend fun <T : TdApi.Object> sendTyped(query: TdApi.Function): T {
        @Suppress("UNCHECKED_CAST")
        return send(query).await() as T
    }

    fun close() {
        scope.cancel()
        client.close()
    }

    companion object {
        private const val TAG = "TdlibClient"
    }
}

sealed class AuthState {
    object WaitingTdlibParameters : AuthState()
    object WaitingPhoneNumber : AuthState()
    data class WaitingCode(val codeInfo: TdApi.AuthenticationCodeInfo) : AuthState()
    data class WaitingPassword(val hint: String) : AuthState()
    object Ready : AuthState()
    object LoggedOut : AuthState()
}
