package com.moon.moonstream.tdlib

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class TelegramManager(private val context: Context) {

    private var client: Client? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var apiId: Int = 0
    private var apiHash: String = ""

    fun setApiCredentials(apiId: Int, apiHash: String) {
        this.apiId = apiId
        this.apiHash = apiHash
    }

    fun start() {
        if (client != null) return

        client = Client.create(
            { update -> handleUpdate(update) },
            { error -> Log.e("TelegramManager", "Update exception", error) },
            { error -> Log.e("TelegramManager", "Default exception", error) }
        )
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                when (val state = update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        sendParameters()
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
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
                    is TdApi.AuthorizationStateLoggingOut,
                    is TdApi.AuthorizationStateClosed -> {
                        _authState.value = AuthState.LoggedOut
                    }
                    else -> {
                        // ignore other states for now
                    }
                }
            }
        }
    }

    private fun sendParameters() {
        val parameters = TdApi.SetTdlibParameters().apply {
            databaseDirectory = context.filesDir.absolutePath + "/tdlib"
            useMessageDatabase = true
            useSecretChats = false
            apiId = this@TelegramManager.apiId
            apiHash = this@TelegramManager.apiHash
            systemLanguageCode = "en"
            deviceModel = "Android"
            systemVersion = "Android"
            applicationVersion = "1.0"
            enableStorageOptimizer = true
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

    fun close() {
        client?.close()
        client = null
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
