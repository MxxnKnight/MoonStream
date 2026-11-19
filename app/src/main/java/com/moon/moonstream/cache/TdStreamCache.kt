package com.moon.moonstream.cache

import android.content.Context
import com.moon.moonstream.util.AppLog
import java.io.File

/**
 * On-disk home of the in-app player's byte window (`tdfile://` DataSource).
 *
 * The player never downloads a whole video: it keeps only the chunks around
 * the play position, and deletes every chunk it has already played
 * (`ui/player/LowStorageWindow.kt` decides what survives). This object owns
 * the folder those chunks live in, so the Settings screen can measure them
 * and clear them.
 *
 * Why `filesDir` and not `cacheDir`: `cacheDir` is what Android is free to
 * wipe under storage pressure, which would yank bytes out from under an
 * active playback, and it is also what the "App cache" meter counts.
 * These chunks are a separate, explicitly-cleared store.
 *
 * Only this app writes here — unlike `filesDir/tdlib_files`, which belongs
 * to TDLib and must only ever be deleted through the backend contract.
 */
object TdStreamCache {

    private const val ROOT = "tdstream"

    /** Playbacks currently holding a window; their folder is never cleared. */
    private val active = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Folders warmed for instant play but not yet played; swept the
     *  moment the Detail page closes (player close is the backstop),
     *  so warmed bytes never linger (see StreamWarmer). */
    private val warmed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun root(context: Context): File = File(context.filesDir, ROOT)

    /** Stable folder name for one Telegram message. */
    fun key(channelId: Long, messageId: Long): String = "${channelId}_$messageId"

    /** Folder for one file's chunks, created on demand. */
    fun dirFor(context: Context, channelId: Long, messageId: Long): File =
        File(root(context), key(channelId, messageId)).apply { mkdirs() }

    fun markActive(channelId: Long, messageId: Long) {
        active.add(key(channelId, messageId))
    }

    fun markInactive(channelId: Long, messageId: Long) {
        active.remove(key(channelId, messageId))
    }

    fun isActive(channelId: Long, messageId: Long): Boolean =
        active.contains(key(channelId, messageId))

    /** Remember a folder as warmed for instant play (swept by [clearWarmed]). */
    fun markWarmed(channelId: Long, messageId: Long) {
        warmed.add(key(channelId, messageId))
    }

    /** Forget a warmed folder: the file IS being played, so the
     *  Detail-exit sweep must leave its chunks alone. Called from
     *  playInApp/playContinue before navigation. */
    fun unmarkWarmed(channelId: Long, messageId: Long) {
        warmed.remove(key(channelId, messageId))
    }

    /** Total bytes of in-app player chunks on disk. */
    fun bytes(context: Context): Long = dirSize(root(context))

    /**
     * Deletes every chunk folder that no playback is using. Returns bytes
     * freed. Active folders are skipped so a clear can't break the video
     * being watched right now.
     */
    fun clear(context: Context): Long {
        val root = root(context)
        val before = dirSize(root)
        val inUse = synchronized(active) { active.toSet() }
        var folders = 0
        root.listFiles()?.forEach { dir ->
            if (!inUse.contains(dir.name)) {
                dir.deleteRecursively()
                folders++
            }
        }
        // Warmed keys for folders just deleted are stale; folders still
        // in use keep theirs (their own close handler clears them).
        synchronized(warmed) { warmed.retainAll(inUse) }
        val freed = (before - dirSize(root)).coerceAtLeast(0L)
        if (folders > 0) {
            AppLog.i(
                "CACHE",
                "player chunks: deleted $folders leftover folder(s), ${freed / (1024 * 1024)} MB freed" +
                    if (inUse.isEmpty()) "" else " (${inUse.size} playback folder(s) kept)",
            )
        }
        return freed
    }

    /**
     * Deletes warmed-only folders (instant-play leftovers) that no playback
     * is using. Called when a Detail page closes without playing, and on
     * player close as a backstop. A warmed folder the player just used is
     * unmarked before navigation, so it is never dropped here. Returns
     * bytes freed.
     */
    fun clearWarmed(context: Context): Long {
        val root = root(context)
        val inUse = synchronized(active) { active.toSet() }
        val todo = synchronized(warmed) { warmed.toSet() }
        var freed = 0L
        var folders = 0
        todo.forEach { k ->
            warmed.remove(k)
            if (inUse.contains(k)) return@forEach
            val dir = File(root, k)
            if (dir.exists()) {
                freed += dirSize(dir)
                dir.deleteRecursively()
                folders++
            }
        }
        if (folders > 0) {
            AppLog.i(
                "CACHE",
                "instant play: deleted $folders warmed folder(s), ${freed / 1024} KB freed",
            )
        }
        return freed
    }

    /** Deletes one file's chunks (called when its playback closes). */
    fun clearFile(context: Context, channelId: Long, messageId: Long): Long {
        warmed.remove(key(channelId, messageId))
        val dir = File(root(context), key(channelId, messageId))
        val before = dirSize(dir)
        dir.deleteRecursively()
        if (before > 0) {
            AppLog.i(
                "CACHE",
                "player chunks: deleted msg=$messageId, ${before / (1024 * 1024)} MB freed",
            )
        }
        return before
    }

    private fun dirSize(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
}
