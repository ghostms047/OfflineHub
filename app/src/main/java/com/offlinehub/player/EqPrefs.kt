package com.offlinehub.player

import android.content.Context

/** Equalizer settings shared between the UI and the playback service (no cross-process state needed). */
class EqPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("equalizer", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) { p.edit().putBoolean("enabled", v).apply() }

    var preset: String
        get() = p.getString("preset", "Flat") ?: "Flat"
        set(v) { p.edit().putString("preset", v).apply() }

    fun levels(): List<Int> =
        (p.getString("levels", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }

    fun setLevels(l: List<Int>) { p.edit().putString("levels", l.joinToString(",")).apply() }

    companion object {
        val presets = listOf("Flat", "Bass boost", "Treble boost", "Vocal", "Rock")

        /** Builds band levels (millibels) for a preset given the device's band count and range. */
        fun presetLevels(name: String, bands: Int, minLevel: Int, maxLevel: Int): List<Int> {
            if (bands <= 0) return emptyList()
            val span = (maxLevel - minLevel) / 2
            return (0 until bands).map { b ->
                val pos = if (bands == 1) 0.0 else b.toDouble() / (bands - 1)
                val f = when (name) {
                    "Bass boost" -> 0.8 - pos * 1.2
                    "Treble boost" -> -0.8 + pos * 1.6
                    "Vocal" -> if (pos in 0.3..0.8) 0.6 else -0.2
                    "Rock" -> if (pos < 0.25 || pos > 0.75) 0.6 else -0.2
                    else -> 0.0
                }
                (f * span * 0.6).toInt().coerceIn(minLevel, maxLevel)
            }
        }
    }
}
