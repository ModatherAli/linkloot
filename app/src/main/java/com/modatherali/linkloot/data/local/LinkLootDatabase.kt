package com.modatherali.linkloot.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntryEntity::class,
        DiscoveredMediaEntity::class,
        DownloadTaskEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LinkLootDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
}
