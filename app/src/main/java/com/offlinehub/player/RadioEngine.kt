package com.offlinehub.player

import com.offlinehub.app
import com.offlinehub.data.Item
import com.offlinehub.data.Type

enum class RadioMode(val label: String) {
    MIXED("Mixed station"), RANDOM("Random"), MUSIC("Music"), TALK("Podcasts & audiobooks"), NEWS("News"), PLAYLIST("Playlist")
}

/**
 * Builds a never-ending queue from locally stored audio only, so it works without internet.
 * MIXED follows the pattern News → Music → Podcast → Music → Short news → Music.
 */
object RadioEngine {
    private val pattern = listOf("NEWS", "MUSIC", "TALK", "MUSIC", "SHORT", "MUSIC")
    private var step = 0
    private val recent = ArrayDeque<Long>()
    private var mode = RadioMode.MIXED
    private var playlistId = -1L

    private fun finished(i: Item) = i.durationMs > 0 && i.positionMs > 0.95 * i.durationMs

    private fun pick(pool: List<Item>): Item? {
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it.id !in recent }
        val src = if (fresh.isNotEmpty()) fresh else pool
        val choice = src.random()
        recent.addLast(choice.id)
        while (recent.size > 12) recent.removeFirst()
        return choice
    }

    private suspend fun talkPool(): List<Item> {
        val pods = app.dao.localType(Type.PODCAST).filter { !finished(it) }
        val books = app.dao.localType(Type.AUDIOBOOK).filter { !finished(it) }
        // audiobooks: only the next unfinished chapter of each book, so radio plays them in order
        val nextChapters = books.groupBy { it.seriesKey }.values.mapNotNull { it.minByOrNull { c -> c.seq } }
        return pods + nextChapters
    }

    suspend fun start(m: RadioMode, playlist: Long = -1): List<Item> {
        mode = m; playlistId = playlist; step = 0; recent.clear()
        return next(10)
    }

    suspend fun next(n: Int): List<Item> {
        val out = ArrayList<Item>()
        var guard = 0
        val music = app.dao.localType(Type.MUSIC)
        val newsAudio = app.dao.localType(Type.NEWS_AUDIO)
        val talk = talkPool()
        val playlistItems = if (mode == RadioMode.PLAYLIST) app.dao.playlistItems(playlistId) else emptyList()
        while (out.size < n && guard++ < n * 6) {
            val item: Item? = when (mode) {
                RadioMode.MIXED -> {
                    val slot = pattern[step % pattern.size]; step++
                    when (slot) {
                        "NEWS" -> pick(newsAudio.filter { it.category == "news" })
                        "SHORT" -> pick(newsAudio.filter { it.category == "short" })
                        "TALK" -> pick(talk)
                        else -> pick(music)
                    }
                }
                RadioMode.RANDOM -> pick(music + talk + newsAudio)
                RadioMode.MUSIC -> pick(music)
                RadioMode.TALK -> pick(talk)
                RadioMode.NEWS -> pick(newsAudio)
                RadioMode.PLAYLIST -> pick(playlistItems)
            }
            if (item != null) out += item
        }
        return out
    }
}
