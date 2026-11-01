package com.moon.moonstream.index.db

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Process-wide, always-warm copy of the library list. Before this, every visit
 * to the Library tab built a brand-new Room flow starting from an empty list,
 * so the grid filled in halfway through the tab-switch animation (jank).
 * Now the data is already there when the screen appears.
 */
object LibraryCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var flow: StateFlow<List<MediaItemEntity>>? = null

    fun items(context: Context): StateFlow<List<MediaItemEntity>> =
        flow ?: synchronized(this) {
            flow ?: MsDatabase.get(context.applicationContext).mediaDao().observeAll()
                .stateIn(scope, SharingStarted.Eagerly, emptyList())
                .also { flow = it }
        }
}
