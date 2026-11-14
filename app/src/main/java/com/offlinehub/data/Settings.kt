package com.offlinehub.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val onboarded: Boolean = false,
    val autoSync: Boolean = true,
    val syncIntervalHours: Int = 6,
    val wifiOnly: Boolean = true,
    val onlyCharging: Boolean = false,
    val minBatteryPct: Int = 20,
    val storageLimitMb: Long = 8192,
    val retentionDays: Int = 30,
    val autoCleanup: Boolean = true,
    val smartCache: Boolean = true,
    val theme: String = "system",
    // what to cache
    val cacheWorld: Boolean = true,
    val cacheTech: Boolean = true,
    val cacheGaming: Boolean = false,
    val cacheSports: Boolean = false,
    val cacheBooks: Boolean = true,
    val cachePodcasts: Boolean = true,
    val cacheAudiobooks: Boolean = false,
    val cacheMusic: Boolean = true,
    val cacheVideos: Boolean = false,
    val cacheWeather: Boolean = true,
    val newsAudio: Boolean = true,
    // per-sync quotas
    val maxNewsPerSync: Int = 60,
    val booksPerSync: Int = 2,
    val podcastEpisodesPerFeed: Int = 2,
    val audiobookChaptersPerSync: Int = 3,
    val musicPerSync: Int = 6,
    val videosPerSync: Int = 1,
    val videoMaxMb: Int = 400,
    // weather
    val weatherCity: String = "",
    val weatherLat: Double = Double.NaN,
    val weatherLon: Double = Double.NaN,
    // user-editable sources, one "name|url" per line
    val podcastFeeds: String = "Global News Podcast|https://podcasts.files.bbci.co.uk/p02nq0gn.rss\nNPR Up First|https://feeds.npr.org/510318/podcast.xml",
    val extraNewsFeeds: String = "",
    // state
    val lastSyncAt: Long = 0,
    val bookCursor: Int = 1,
    val audiobookOffset: Int = 0,
    val musicPage: Int = 1,
    val videoPage: Int = 1,
    val queuedSyncReason: String = ""
)

class SettingsStore(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Settings> = _flow
    val value: Settings get() = _flow.value

    fun update(f: (Settings) -> Settings) {
        synchronized(this) {
            val n = f(_flow.value)
            save(n)
            _flow.value = n
        }
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            onboarded = p.getBoolean("onboarded", d.onboarded),
            autoSync = p.getBoolean("autoSync", d.autoSync),
            syncIntervalHours = p.getInt("syncIntervalHours", d.syncIntervalHours),
            wifiOnly = p.getBoolean("wifiOnly", d.wifiOnly),
            onlyCharging = p.getBoolean("onlyCharging", d.onlyCharging),
            minBatteryPct = p.getInt("minBatteryPct", d.minBatteryPct),
            storageLimitMb = p.getLong("storageLimitMb", d.storageLimitMb),
            retentionDays = p.getInt("retentionDays", d.retentionDays),
            autoCleanup = p.getBoolean("autoCleanup", d.autoCleanup),
            smartCache = p.getBoolean("smartCache", d.smartCache),
            theme = p.getString("theme", d.theme) ?: d.theme,
            cacheWorld = p.getBoolean("cacheWorld", d.cacheWorld),
            cacheTech = p.getBoolean("cacheTech", d.cacheTech),
            cacheGaming = p.getBoolean("cacheGaming", d.cacheGaming),
            cacheSports = p.getBoolean("cacheSports", d.cacheSports),
            cacheBooks = p.getBoolean("cacheBooks", d.cacheBooks),
            cachePodcasts = p.getBoolean("cachePodcasts", d.cachePodcasts),
            cacheAudiobooks = p.getBoolean("cacheAudiobooks", d.cacheAudiobooks),
            cacheMusic = p.getBoolean("cacheMusic", d.cacheMusic),
            cacheVideos = p.getBoolean("cacheVideos", d.cacheVideos),
            cacheWeather = p.getBoolean("cacheWeather", d.cacheWeather),
            newsAudio = p.getBoolean("newsAudio", d.newsAudio),
            maxNewsPerSync = p.getInt("maxNewsPerSync", d.maxNewsPerSync),
            booksPerSync = p.getInt("booksPerSync", d.booksPerSync),
            podcastEpisodesPerFeed = p.getInt("podcastEpisodesPerFeed", d.podcastEpisodesPerFeed),
            audiobookChaptersPerSync = p.getInt("audiobookChaptersPerSync", d.audiobookChaptersPerSync),
            musicPerSync = p.getInt("musicPerSync", d.musicPerSync),
            videosPerSync = p.getInt("videosPerSync", d.videosPerSync),
            videoMaxMb = p.getInt("videoMaxMb", d.videoMaxMb),
            weatherCity = p.getString("weatherCity", d.weatherCity) ?: "",
            weatherLat = Double.fromBits(p.getLong("weatherLat", Double.NaN.toBits())),
            weatherLon = Double.fromBits(p.getLong("weatherLon", Double.NaN.toBits())),
            podcastFeeds = p.getString("podcastFeeds", d.podcastFeeds) ?: d.podcastFeeds,
            extraNewsFeeds = p.getString("extraNewsFeeds", d.extraNewsFeeds) ?: "",
            lastSyncAt = p.getLong("lastSyncAt", 0),
            bookCursor = p.getInt("bookCursor", 1),
            audiobookOffset = p.getInt("audiobookOffset", 0),
            musicPage = p.getInt("musicPage", 1),
            videoPage = p.getInt("videoPage", 1)
        )
    }

    private fun save(s: Settings) {
        p.edit()
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("autoSync", s.autoSync)
            .putInt("syncIntervalHours", s.syncIntervalHours)
            .putBoolean("wifiOnly", s.wifiOnly)
            .putBoolean("onlyCharging", s.onlyCharging)
            .putInt("minBatteryPct", s.minBatteryPct)
            .putLong("storageLimitMb", s.storageLimitMb)
            .putInt("retentionDays", s.retentionDays)
            .putBoolean("autoCleanup", s.autoCleanup)
            .putBoolean("smartCache", s.smartCache)
            .putString("theme", s.theme)
            .putBoolean("cacheWorld", s.cacheWorld)
            .putBoolean("cacheTech", s.cacheTech)
            .putBoolean("cacheGaming", s.cacheGaming)
            .putBoolean("cacheSports", s.cacheSports)
            .putBoolean("cacheBooks", s.cacheBooks)
            .putBoolean("cachePodcasts", s.cachePodcasts)
            .putBoolean("cacheAudiobooks", s.cacheAudiobooks)
            .putBoolean("cacheMusic", s.cacheMusic)
            .putBoolean("cacheVideos", s.cacheVideos)
            .putBoolean("cacheWeather", s.cacheWeather)
            .putBoolean("newsAudio", s.newsAudio)
            .putInt("maxNewsPerSync", s.maxNewsPerSync)
            .putInt("booksPerSync", s.booksPerSync)
            .putInt("podcastEpisodesPerFeed", s.podcastEpisodesPerFeed)
            .putInt("audiobookChaptersPerSync", s.audiobookChaptersPerSync)
            .putInt("musicPerSync", s.musicPerSync)
            .putInt("videosPerSync", s.videosPerSync)
            .putInt("videoMaxMb", s.videoMaxMb)
            .putString("weatherCity", s.weatherCity)
            .putLong("weatherLat", s.weatherLat.toBits())
            .putLong("weatherLon", s.weatherLon.toBits())
            .putString("podcastFeeds", s.podcastFeeds)
            .putString("extraNewsFeeds", s.extraNewsFeeds)
            .putLong("lastSyncAt", s.lastSyncAt)
            .putInt("bookCursor", s.bookCursor)
            .putInt("audiobookOffset", s.audiobookOffset)
            .putInt("musicPage", s.musicPage)
            .putInt("videoPage", s.videoPage)
            .apply()
    }
}
