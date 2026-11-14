package com.offlinehub.storage

import com.offlinehub.app
import com.offlinehub.data.Affinity
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Learns, entirely on this device, which content types/categories the user opens most.
 * Scores decay with a 14-day half-life. Disabling Smart Cache stops recording and makes
 * every weight neutral. Nothing here is ever uploaded.
 */
object SmartCache {
    private const val HALF_LIFE_DAYS = 14.0
    private const val DAY = 86_400_000.0

    private fun decayed(a: Affinity, now: Long): Double =
        a.score * 0.5.pow((now - a.updatedAt) / DAY / HALF_LIFE_DAYS)

    suspend fun record(type: String, category: String) {
        if (!app.settings.value.smartCache) return
        val now = System.currentTimeMillis()
        val existing = app.dao.affinities().associateBy { it.key }
        for (key in listOf("type:$type", "cat:$category")) {
            val old = existing[key]
            val score = (if (old != null) decayed(old, now) else 0.0) + 1.0
            app.dao.upsertAffinity(Affinity(key, score, now))
        }
    }

    /** Normalised 0..1 weights per key ("type:PODCAST", "cat:tech"); empty when disabled. */
    suspend fun weights(): Map<String, Double> {
        if (!app.settings.value.smartCache) return emptyMap()
        val now = System.currentTimeMillis()
        val all = app.dao.affinities().associate { it.key to decayed(it, now) }
        if (all.isEmpty()) return emptyMap()
        val maxType = all.filterKeys { it.startsWith("type:") }.values.maxOrNull() ?: 1.0
        val maxCat = all.filterKeys { it.startsWith("cat:") }.values.maxOrNull() ?: 1.0
        return all.mapValues { (k, v) ->
            val m = if (k.startsWith("type:")) maxType else maxCat
            if (m <= 0) 0.0 else (v / m).coerceIn(0.0, 1.0)
        }
    }

    fun weightOf(w: Map<String, Double>, key: String): Double = w[key] ?: 0.5

    /** Scales a base per-sync quota by how much the user cares about this content. */
    fun quota(base: Int, weight: Double): Int = max(1, (base * (0.5 + weight)).roundToInt())

    suspend fun reset() = app.dao.clearAffinity()
}
