package com.offlinehub.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Type {
    const val NEWS = "NEWS"
    const val BOOK = "BOOK"
    const val MUSIC = "MUSIC"
    const val VIDEO = "VIDEO"
    const val PODCAST = "PODCAST"
    const val AUDIOBOOK = "AUDIOBOOK"
    const val NEWS_AUDIO = "NEWS_AUDIO"
    val audio = listOf(MUSIC, PODCAST, AUDIOBOOK, NEWS_AUDIO)
}

object Dl {
    const val NONE = "NONE"
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

/**
 * One row for every piece of content. Text content (news) lives entirely in [summary];
 * media/books have a [mediaUrl] and, once downloaded, a [localPath].
 */
@Entity(tableName = "items", indices = [Index("type"), Index("category"), Index(value = ["url"], unique = true)])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val category: String = "general",
    val title: String,
    val subtitle: String = "",
    val summary: String = "",
    val url: String,
    val mediaUrl: String = "",
    val thumbUrl: String = "",
    val thumbPath: String = "",
    val localPath: String = "",
    val sizeBytes: Long = 0,
    val source: String = "",
    val license: String = "",
    val seriesKey: String = "",
    val seq: Int = 0,
    val publishedAt: Long = 0,
    val syncedAt: Long = 0,
    val lastAccessed: Long = 0,
    val accessCount: Int = 0,
    val readLater: Boolean = false,
    val pinned: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val dlState: String = Dl.NONE,
    val dlBytes: Long = 0,
    val dlTotal: Long = 0,
    val priority: Int = 0,
    val dlError: String = ""
) {
    /** Usable without internet. */
    val isLocal: Boolean get() = mediaUrl.isEmpty() || localPath.isNotEmpty()
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long
)

@Entity(tableName = "bookmarks", indices = [Index("docId")])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val location: Int,
    val label: String,
    val createdAt: Long
)

@Entity(tableName = "doc_notes", indices = [Index("docId")])
data class DocNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val location: Int,
    val text: String,
    val createdAt: Long
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_entries", indices = [Index("playlistId")])
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val itemId: Long,
    val pos: Int
)

@Entity(tableName = "sync_log")
data class SyncLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val message: String
)

@Entity(tableName = "events")
data class CalEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dayStart: Long,
    val notes: String = ""
)

@Entity(tableName = "affinity")
data class Affinity(
    @PrimaryKey val key: String,
    val score: Double,
    val updatedAt: Long
)

data class TypeStat(val type: String, val cnt: Int, val bytes: Long)
