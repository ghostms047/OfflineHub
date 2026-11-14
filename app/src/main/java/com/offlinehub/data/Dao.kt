package com.offlinehub.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

private const val LOCAL = "(localPath != '' OR mediaUrl = '')"

@Dao
interface HubDao {
    // ---- items
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(i: Item): Long

    @Update
    suspend fun update(i: Item)

    @Query("SELECT * FROM items WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): Item?

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Item?

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE type = :type ORDER BY publishedAt DESC, id DESC")
    fun observeType(type: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE type = :type AND $LOCAL ORDER BY publishedAt DESC, id DESC")
    fun observeLocalType(type: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE type = :type AND $LOCAL ORDER BY publishedAt DESC, id DESC")
    suspend fun localType(type: String): List<Item>

    @Query("SELECT * FROM items WHERE type = 'NEWS' AND (:cat = 'all' OR category = :cat) ORDER BY publishedAt DESC LIMIT 300")
    fun observeNews(cat: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE readLater = 1 ORDER BY lastAccessed DESC, publishedAt DESC")
    fun observeReadLater(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE type = :type AND seriesKey = :series ORDER BY seq ASC, publishedAt DESC")
    suspend fun series(type: String, series: String): List<Item>

    @Query("SELECT type, COUNT(*) AS cnt, COALESCE(SUM(sizeBytes),0) AS bytes FROM items WHERE $LOCAL GROUP BY type")
    fun observeStats(): Flow<List<TypeStat>>

    @Query("SELECT * FROM items WHERE $LOCAL AND (title LIKE '%' || :q || '%' OR subtitle LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%' OR source LIKE '%' || :q || '%') ORDER BY publishedAt DESC LIMIT 120")
    suspend fun search(q: String): List<Item>

    @Query("SELECT * FROM items WHERE dlState IN ('QUEUED','RUNNING') ORDER BY priority DESC, id ASC")
    suspend fun queuedDownloads(): List<Item>

    @Query("SELECT * FROM items WHERE dlState != 'NONE' AND dlState != 'DONE' ORDER BY priority DESC, id ASC")
    fun observeDownloads(): Flow<List<Item>>

    @Query("UPDATE items SET dlState = :state, dlError = :err WHERE id = :id")
    suspend fun setDlState(id: Long, state: String, err: String = "")

    @Query("UPDATE items SET dlBytes = :bytes, dlTotal = :total, dlState = 'RUNNING' WHERE id = :id")
    suspend fun setProgress(id: Long, bytes: Long, total: Long)

    @Query("UPDATE items SET localPath = :path, sizeBytes = :size, dlState = 'DONE', dlBytes = :size, dlTotal = :size, syncedAt = :now, dlError = '' WHERE id = :id")
    suspend fun setDownloaded(id: Long, path: String, size: Long, now: Long)

    @Query("UPDATE items SET localPath = '', sizeBytes = 0, dlState = 'NONE', dlBytes = 0, dlTotal = 0 WHERE id = :id")
    suspend fun clearLocal(id: Long)

    @Query("UPDATE items SET thumbPath = :path WHERE id = :id")
    suspend fun setThumb(id: Long, path: String)

    @Query("UPDATE items SET positionMs = :pos, durationMs = CASE WHEN :dur > 0 THEN :dur ELSE durationMs END WHERE id = :id")
    suspend fun setPosition(id: Long, pos: Long, dur: Long)

    @Query("UPDATE items SET lastAccessed = :now, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE items SET readLater = :v WHERE id = :id")
    suspend fun setReadLater(id: Long, v: Boolean)

    @Query("UPDATE items SET pinned = :v WHERE id = :id")
    suspend fun setPinned(id: Long, v: Boolean)

    @Query("UPDATE items SET priority = :p WHERE id = :id")
    suspend fun setPriority(id: Long, p: Int)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM items WHERE $LOCAL AND pinned = 0 AND readLater = 0 AND source != 'imported'")
    suspend fun evictable(): List<Item>

    @Query("SELECT * FROM items")
    suspend fun all(): List<Item>

    @Query("SELECT itemId FROM playlist_entries")
    suspend fun playlistItemIds(): List<Long>

    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM items WHERE type = :type AND $LOCAL")
    suspend fun bytesOfType(type: String): Long

    // ---- notes
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' ORDER BY updatedAt DESC LIMIT 50")
    suspend fun searchNotes(q: String): List<Note>

    @Upsert
    suspend fun upsertNote(n: Note): Long

    @Delete
    suspend fun deleteNote(n: Note)

    // ---- document bookmarks and notes
    @Query("SELECT * FROM bookmarks WHERE docId = :docId ORDER BY location")
    fun observeBookmarks(docId: Long): Flow<List<Bookmark>>

    @Insert
    suspend fun addBookmark(b: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT * FROM doc_notes WHERE docId = :docId ORDER BY location, createdAt")
    fun observeDocNotes(docId: Long): Flow<List<DocNote>>

    @Insert
    suspend fun addDocNote(n: DocNote)

    @Query("DELETE FROM doc_notes WHERE id = :id")
    suspend fun deleteDocNote(id: Long)

    // ---- playlists
    @Query("SELECT * FROM playlists ORDER BY name")
    fun observePlaylists(): Flow<List<Playlist>>

    @Insert
    suspend fun addPlaylist(p: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun clearPlaylistEntries(id: Long)

    @Insert
    suspend fun addPlaylistEntry(e: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE id = :id")
    suspend fun deletePlaylistEntry(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :p AND itemId = :i")
    suspend fun removeFromPlaylist(p: Long, i: Long)

    @Query("SELECT COALESCE(MAX(pos), -1) + 1 FROM playlist_entries WHERE playlistId = :id")
    suspend fun nextPlaylistPos(id: Long): Int

    @Query("SELECT e.* FROM playlist_entries e WHERE e.playlistId = :id ORDER BY e.pos")
    fun observePlaylistEntries(id: Long): Flow<List<PlaylistEntry>>

    @Query("SELECT i.* FROM items i JOIN playlist_entries e ON e.itemId = i.id WHERE e.playlistId = :id ORDER BY e.pos")
    suspend fun playlistItems(id: Long): List<Item>

    @Query("SELECT i.* FROM items i JOIN playlist_entries e ON e.itemId = i.id WHERE e.playlistId = :id ORDER BY e.pos")
    fun observePlaylistItems(id: Long): Flow<List<Item>>

    // ---- sync log
    @Insert
    suspend fun log(e: SyncLogEntry)

    @Query("SELECT * FROM sync_log ORDER BY id DESC LIMIT 40")
    fun observeLog(): Flow<List<SyncLogEntry>>

    @Query("DELETE FROM sync_log WHERE id NOT IN (SELECT id FROM sync_log ORDER BY id DESC LIMIT 200)")
    suspend fun trimLog()

    // ---- calendar
    @Query("SELECT * FROM events ORDER BY dayStart")
    fun observeEvents(): Flow<List<CalEvent>>

    @Insert
    suspend fun addEvent(e: CalEvent)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    // ---- smart cache
    @Query("SELECT * FROM affinity")
    suspend fun affinities(): List<Affinity>

    @Upsert
    suspend fun upsertAffinity(a: Affinity)

    @Query("DELETE FROM affinity")
    suspend fun clearAffinity()
}
