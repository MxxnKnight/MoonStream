package com.moon.moonstream.backend

import android.content.Context

/**
 * The single swap point. The whole app reads [backend] and nothing else.
 *
 * Today it holds the [FakeMoonStreamBackend] (pretend data). When the real
 * Telegram implementation is ready, its module calls [install] once (after
 * its own login flow completes) and every screen transparently goes real —
 * no screen code changes, because screens only ever spoke to the
 * [MoonStreamBackend] contract.
 */
object BackendProvider {

    lateinit var backend: MoonStreamBackend
        private set

    private var installed = false

    /** Call once from MainActivity.onCreate, before the first frame. */
    fun init(context: Context) {
        if (installed) return
        backend = FakeMoonStreamBackend(context)
    }

    /**
     * Plugs in the real Telegram implementation. Called by the real
     * backend module after ITS login flow completes (phone -> code ->
     * password, session restored). After this call the fake is gone.
     */
    fun install(real: MoonStreamBackend) {
        backend = real
        installed = true
    }
}
