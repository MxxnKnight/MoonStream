package com.moon.moonstream.index.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The on-phone library database. Lazily built; call [get] from a background
 * thread (Room forbids main-thread queries).
 */
@Database(
    entities = [
        MediaItemEntity::class,
        StreamFileEntity::class,
        SubtitleFileEntity::class,
        ScanCursorEntity::class,
        UnmatchedFileEntity::class,
        ChannelConfigEntity::class,
        WatchProgressEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
abstract class MsDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun streamDao(): StreamDao
    abstract fun subtitleDao(): SubtitleDao
    abstract fun scanCursorDao(): ScanCursorDao
    abstract fun unmatchedDao(): UnmatchedDao
    abstract fun channelConfigDao(): ChannelConfigDao
    abstract fun watchProgressDao(): WatchProgressDao

    companion object {
        @Volatile
        private var instance: MsDatabase? = null

        /**
         * v2 -> v3: adds media_items.stremioId (the Stremio-facing tt/tg ID).
         * Non-destructive — the library survives; missing values are
         * backfilled at startup (see AppUiState.init).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN stremioId TEXT")
            }
        }

        /**
         * v3 -> v4: adds media_items.isCustom (user-created entries).
         * Non-destructive — defaults to 0 (false) for existing rows.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4 -> v5: adds stream_files.episodeEnd (multi-episode files like "E7-8").
         * Nullable — existing rows get null (single episode).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stream_files ADD COLUMN episodeEnd INTEGER")
            }
        }

        /**
         * v5 -> v6: adds watch_progress (continue watching).
         * New table — nothing to migrate.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS watch_progress (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "mediaId INTEGER NOT NULL, " +
                        "streamFileId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "episodeLabel TEXT, " +
                        "posterUrl TEXT, " +
                        "playedAt INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v6 -> v7: adds watch_progress.positionMs (in-player Resume position).
         * Defaults to 0 (no resume) for existing rows.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_progress ADD COLUMN positionMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN kitsuId TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN anilistId TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_progress ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN backdropUrl TEXT")
            }
        }

        /** v12: per-channel "manual" flag (never auto-match). */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE channel_config ADD COLUMN manualOnly INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v13: per-file Telegram edit stamp, so a fixed caption can be picked
         * up by a quick scan instead of a full rescan (moon's 2026-11-09).
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stream_files ADD COLUMN editDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subtitle_files ADD COLUMN editDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE unmatched_files ADD COLUMN editDate INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v14: caption on unmatched files, so manual channels can show
         * the caption (the real title) instead of the raw filename
         * (moon's 2026-11-09).
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE unmatched_files ADD COLUMN caption TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v15: genres on media items (comma-separated, e.g. "Action,Drama"),
         * for the Watch Stats Top Genres section (moon's 2026-11-10).
         * Nullable — titles indexed before this change have no genres
         * until they are re-indexed; the stats screen only counts rows
         * that have them.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN genres TEXT")
            }
        }

        /**
         * v16: originalLanguage (ISO code, e.g. "ml", "en", "ko") and
         * country (ISO code, e.g. "IN", "US", "KR") on media items,
         * for the Watch Stats By language / By country sections and for
         * identifying Asian dramas (moon's 2026-11-11). Nullable —
         * titles indexed before this change have none until re-indexed.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN originalLanguage TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN country TEXT")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_progress ADD COLUMN finishedAt INTEGER")
            }
        }

        fun get(context: Context): MsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MsDatabase::class.java,
                    "moonstream.db",
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    // Pre-release app: unknown schema jumps still wipe and
                    // rescan. Never ship a destructive migration past v1.0
                    // without a real one.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
