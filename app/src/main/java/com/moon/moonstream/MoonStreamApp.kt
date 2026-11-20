package com.moon.moonstream

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.moon.moonstream.server.ServedFiles

/**
 * App entry point. Owns the Coil image loader so poster caching is
 * bounded: 50 MB on disk by default (moon's 2026-11-20 — the app had
 * grown to 248 MB, posters with no cap among the causes), or no disk
 * cache at all with the "Lite posters" toggle.
 *
 * Coil builds the loader once per process from [newImageLoader], so a
 * Lite toggle flip needs an app restart to take effect — the toggle
 * says so in its description. The memory cache stays on either way:
 * it is RAM, not disk, and it keeps scrolling smooth.
 */
class MoonStreamApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Reload the list of files the addon server served, so the
        // sweep can still drop their Telegram copies after a restart.
        ServedFiles.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val lite = isLitePosters(this)
        return ImageLoader.Builder(this)
            .diskCache(
                if (lite) {
                    null
                } else {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve(POSTER_CACHE_DIR))
                        .maxSizeBytes(POSTER_DISK_CACHE_BYTES)
                        .build()
                },
            )
            .build()
    }

    companion object {
        private const val PREFS = "moonstream_prefs"

        /** SharedPreferences key for the Lite posters toggle. */
        const val KEY_LITE_POSTERS = "lite_posters"

        /** Coil's poster cache dir under cacheDir (its own default name). */
        const val POSTER_CACHE_DIR = "image_cache"

        /** Poster disk cache cap in MB (moon's 2026-11-20). */
        const val POSTER_DISK_CACHE_MB = 50L

        val POSTER_DISK_CACHE_BYTES: Long = POSTER_DISK_CACHE_MB * 1024L * 1024L

        fun isLitePosters(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_LITE_POSTERS, false)
    }
}
