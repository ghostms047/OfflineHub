package com.offlinehub.storage

import android.media.MediaMetadataRetriever
import com.offlinehub.app
import com.offlinehub.data.Dl
import com.offlinehub.data.Item
import com.offlinehub.data.Type
import java.io.File
import kotlin.math.max

data class Usage(val byBucket: LinkedHashMap<String, Long>, val total: Long, val limit: Long)

object Storage {
    fun mediaDir(type: String): File = File(app.filesDir, "media/$type").apply { mkdirs() }
    fun thumbDir(): File = File(app.filesDir, "thumbs").apply { mkdirs() }
    fun indexDir(): File = File(app.filesDir, "index").apply { mkdirs() }
    fun mapsDir(): File = File(app.filesDir, "maps").apply { mkdirs() }
    fun booksDir(): File = mediaDir(Type.BOOK)

    fun limitBytes(): Long = app.settings.value.storageLimitMb * 1024L * 1024L

    fun dirSize(f: File): Long {
        if (!f.exists()) return 0
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { dirSize(it) } ?: 0
    }

    suspend fun usage(): Usage {
        val news = app.dao.bytesOfType(Type.NEWS)
        val media = File(app.filesDir, "media")
        fun sz(t: String) = dirSize(File(media, t))
        val videos = sz(Type.VIDEO)
        val music = sz(Type.MUSIC)
        val books = sz(Type.BOOK)
        val podcasts = sz(Type.PODCAST)
        val audiobooks = sz(Type.AUDIOBOOK)
        val other = sz(Type.NEWS_AUDIO) + dirSize(thumbDir()) + dirSize(indexDir()) + dirSize(mapsDir()) +
            dirSize(File(app.dataDir, "databases"))
        val m = linkedMapOf(
            "News" to news, "Videos" to videos, "Music" to music, "Books" to books,
            "Podcasts" to podcasts, "Audiobooks" to audiobooks, "Other" to other
        )
        return Usage(LinkedHashMap(m), m.values.sum(), limitBytes())
    }

    private fun lastUse(i: Item) = max(max(i.lastAccessed, i.syncedAt), i.publishedAt)

    private suspend fun protectedIds(extra: Long = -1): Set<Long> {
        val s = HashSet<Long>(app.dao.playlistItemIds())
        val cur = app.player.state.value.currentId
        if (cur > 0) s += cur
        if (extra > 0) s += extra
        return s
    }

    /** Higher = evict sooner. */
    fun evictionScore(i: Item, now: Long, w: Map<String, Double>): Double {
        val ageDays = (now - lastUse(i)) / 86_400_000.0
        val affinity = SmartCache.weightOf(w, "type:${i.type}")
        val sizeFactor = 1.0 + i.sizeBytes / (500.0 * 1024 * 1024)
        var s = (ageDays + 0.5) * (1.6 - affinity) * sizeFactor
        if (i.durationMs > 0 && i.positionMs > 0.95 * i.durationMs) s += 20  // finished
        if (i.accessCount > 0) s *= 0.7
        return s
    }

    private suspend fun evict(i: Item) {
        if (i.localPath.isNotEmpty()) File(i.localPath).delete()
        if (i.mediaUrl.isEmpty()) app.dao.delete(i.id) else app.dao.clearLocal(i.id)
    }

    /**
     * Frees low-priority content until [needBytes] more fits under the storage limit.
     * Returns false if it can't make room (everything left is pinned/protected).
     */
    suspend fun ensureSpace(needBytes: Long, protectId: Long = -1): Boolean {
        var used = usage().total
        val limit = limitBytes()
        if (used + needBytes <= limit) return true
        val now = System.currentTimeMillis()
        val w = SmartCache.weights()
        val protectedSet = protectedIds(protectId)
        val candidates = app.dao.evictable()
            .filter { it.id !in protectedSet && it.dlState != Dl.RUNNING }
            .sortedByDescending { evictionScore(it, now, w) }
        for (c in candidates) {
            if (used + needBytes <= limit) break
            used -= c.sizeBytes
            evict(c)
        }
        return usage().total + needBytes <= limit
    }

    /** Items the smart manager would remove first, with the highest eviction score first. */
    suspend fun suggestions(n: Int = 12): List<Item> {
        val now = System.currentTimeMillis()
        val w = SmartCache.weights()
        val prot = protectedIds()
        return app.dao.evictable()
            .filter { it.id !in prot && it.dlState != Dl.RUNNING && it.type != Type.NEWS && it.sizeBytes > 0 }
            .sortedByDescending { evictionScore(it, now, w) }
            .take(n)
    }

    suspend fun removeItem(i: Item) = evict(i)

    /** Clears every unpinned item of one kind (manual control). */
    suspend fun clearType(type: String): Int {
        val prot = protectedIds()
        var n = 0
        for (i in app.dao.evictable()) {
            if (i.type == type && i.id !in prot) { evict(i); n++ }
        }
        return n
    }

    /** Removes expired content and orphaned files. Returns bytes freed (approx). */
    suspend fun cleanup(retentionDays: Int = app.settings.value.retentionDays): Long {
        val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
        val prot = protectedIds()
        var freed = 0L
        for (i in app.dao.all()) {
            if (i.pinned || i.readLater || i.source == "imported" || i.id in prot) continue
            if (i.dlState == Dl.RUNNING || i.dlState == Dl.QUEUED || i.dlState == Dl.PAUSED) continue
            if (lastUse(i) < cutoff) {
                freed += i.sizeBytes
                if (i.localPath.isNotEmpty()) File(i.localPath).delete()
                app.dao.delete(i.id)
            }
        }
        freed += removeOrphans()
        return freed
    }

    /** Files in media folders that no row refers to (e.g. left over from a cancelled download). */
    private suspend fun removeOrphans(): Long {
        val known = HashSet<String>()
        for (i in app.dao.all()) {
            if (i.localPath.isNotEmpty()) known += i.localPath
            if (i.dlState == Dl.QUEUED || i.dlState == Dl.PAUSED || i.dlState == Dl.RUNNING) {
                // keep partial files of unfinished downloads
                known += DownloadFiles.partPath(i)
            }
            if (i.thumbPath.isNotEmpty()) known += i.thumbPath
        }
        var freed = 0L
        val roots = listOf(File(app.filesDir, "media"), thumbDir())
        for (r in roots) {
            r.walkTopDown().filter { it.isFile }.forEach { f ->
                if (f.absolutePath !in known) {
                    freed += f.length()
                    f.delete()
                }
            }
        }
        return freed
    }

    fun durationOf(path: String): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (_: Exception) { 0L }
}

object DownloadFiles {
    fun extOf(i: Item): String {
        val clean = i.mediaUrl.substringBefore('?').substringAfterLast('/')
        val ext = clean.substringAfterLast('.', "").lowercase()
        if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) return ext
        return when (i.type) {
            Type.BOOK -> "epub"
            Type.VIDEO -> "mp4"
            else -> "mp3"
        }
    }

    fun finalPath(i: Item): String = File(Storage.mediaDir(i.type), "${i.id}.${extOf(i)}").absolutePath
    fun partPath(i: Item): String = finalPath(i) + ".part"
}
