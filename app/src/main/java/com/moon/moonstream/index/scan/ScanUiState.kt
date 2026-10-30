package com.moon.moonstream.index.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-level scan state. A singleton object (not screen-local) so the scan
 * keeps its progress when the user navigates to Server / Library / Settings
 * — or when the app goes to the background. The Channels screen's scan
 * panel and per-channel progress bars observe these flows.
 */
object ScanUiState {

    enum class ChState { IDLE, SCANNING, DONE }

    data class ChProgress(
        val channelId: Long,
        val title: String,
        /** 0f..1f while scanning */
        val progress: Float,
        val state: ChState,
    )

    data class Totals(
        val indexed: Int,
        val existing: Int,
        val unmatched: Int,
        val checked: Int,
    )

    private val _channels = MutableStateFlow<Map<Long, ChProgress>>(emptyMap())
    val channels: StateFlow<Map<Long, ChProgress>> = _channels.asStateFlow()

    private val _totals = MutableStateFlow(Totals(0, 0, 0, 0))
    val totals: StateFlow<Totals> = _totals.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _panelVisible = MutableStateFlow(false)
    val panelVisible: StateFlow<Boolean> = _panelVisible.asStateFlow()

    private val _panelFinished = MutableStateFlow(false)
    val panelFinished: StateFlow<Boolean> = _panelFinished.asStateFlow()

    /** Called by ScanService when a run starts. */
    fun beginRun(targets: List<Pair<Long, String>>) {
        _totals.value = Totals(0, 0, 0, 0)
        _channels.value = targets.associate { (id, title) ->
            id to ChProgress(id, title, 0f, ChState.SCANNING)
        }
        _scanning.value = true
        _panelFinished.value = false
        _panelVisible.value = true
    }

    fun updateProgress(channelId: Long, progress: Float) {
        _channels.update { map ->
            map[channelId]?.let {
                map + (channelId to it.copy(progress = progress.coerceIn(0f, 1f)))
            } ?: map
        }
    }

    fun markDone(channelId: Long) {
        _channels.update { map ->
            map[channelId]?.let {
                map + (channelId to it.copy(progress = 1f, state = ChState.DONE))
            } ?: map
        }
    }

    fun addTotals(indexed: Int, existing: Int, unmatched: Int, checked: Int) {
        _totals.update {
            it.copy(
                indexed = it.indexed + indexed,
                existing = it.existing + existing,
                unmatched = it.unmatched + unmatched,
                checked = it.checked + checked,
            )
        }
    }

    /** Called by ScanService when the whole run finishes (or is cancelled). */
    fun finishRun() {
        _scanning.value = false
        _panelFinished.value = true
    }

    /** User dismissed the results panel. */
    fun dismissPanel() {
        _panelVisible.value = false
        _panelFinished.value = false
        _channels.value = emptyMap()
    }
}
