package com.moon.moonstream.index.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-phone library database. Lazily built; call [get] from a background
 * thread (Room forbids main-thread queries).
 */
@Database(
    entities = [
        MediaItemEntity::class,
        StreamFileEntity::class,
        ScanCursorEntity::class,
        UnmatchedFileEntity::class,
        ChannelConfigEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MsDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun streamDao(): StreamDao
    abstract fun scanCursorDao(): ScanCursorDao
    abstract fun unmatchedDao(): UnmatchedDao
    abstract fun channelConfigDao(): ChannelConfigDao

    companion object {
        @Volatile
        private var instance: MsDatabase? = null

        fun get(context: Context): MsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MsDatabase::class.java,
                    "moonstream.db",
                ).build().also { instance = it }
            }
    }
}
