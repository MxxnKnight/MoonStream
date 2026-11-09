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
    version = 12,
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

        fun get(context: Context): MsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MsDatabase::class.java,
                    "moonstream.db",
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    // Pre-release app: unknown schema jumps still wipe and
                    // rescan. Never ship a destructive migration past v1.0
                    // without a real one.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
