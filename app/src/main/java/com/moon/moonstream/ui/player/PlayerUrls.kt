package com.moon.moonstream.ui.player

import com.moon.moonstream.ui.state.AppUiState

/**
 * Stream URLs for the in-app player.
 * Same phone as the server → localhost. Never the WiFi IP: it can go stale
 * and the connection just hangs (moon's 0.9.5
 * ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT).
 * Copy Link keeps the LAN URL for Stremio / other devices.
 */
object PlayerUrls {
    fun stream(mediaId: Long, fileId: Long): String =
        "http://127.0.0.1:${AppUiState.port}/stream/file/$mediaId/$fileId"

    fun sub(subId: Long, delayMs: Long = 0L): String =
        "http://127.0.0.1:${AppUiState.port}/sub/$subId" +
            (if (delayMs != 0L) "?delayMs=$delayMs" else "")
}
