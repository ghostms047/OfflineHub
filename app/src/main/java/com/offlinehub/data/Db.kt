package com.offlinehub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Item::class, Note::class, Bookmark::class, DocNote::class, Playlist::class,
        PlaylistEntry::class, SyncLogEntry::class, CalEvent::class, Affinity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): HubDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "offlinehub.db")
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}
