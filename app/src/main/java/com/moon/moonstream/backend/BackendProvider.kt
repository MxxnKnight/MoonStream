package com.moon.moonstream.backend

import android.content.Context
import com.moon.moonstream.tdlib.TelegramManager

/**
 * The single swap point. The whole app reads [backend] and nothing else.
 *
 * Starts with [TdlibMoonStreamBackend] backed by the real TelegramManager.
 * Auth state is observed from TelegramManager — if not logged in the app
 * shows the login screen; if logged in it shows the main screens.
 *
 * [FakeMoonStreamBackend] is still available for UI testing/preview.
 */
object BackendProvider {

    lateinit var backend: MoonStreamBackend
        private set

    private var initialized = false

    /** Call once from Application.onCreate or MainActivity.onCreate, before the first frame. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val telegramManager = TelegramManager.getInstance(context)
        backend = TdlibMoonStreamBackend(context, telegramManager)
    }

    /**
     * Plugs in a custom backend implementation (e.g. for testing).
     * After this call, [backend] is replaced.
     */
    fun install(custom: MoonStreamBackend) {
        backend = custom
    }
}
