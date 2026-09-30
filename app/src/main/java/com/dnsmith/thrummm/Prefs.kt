// SPDX-License-Identifier: GPL-3.0-or-later
package com.dnsmith.thrummm

import android.content.Context
import android.content.SharedPreferences

enum class Preset(val label: String, val shortName: String, val timings: LongArray?) {
    // The system's stock notification pattern, kept for side-by-side comparison.
    ORIGINAL("Original (100 / 150 / 100)", "Original", longArrayOf(0, 100, 150, 100)),
    LONG("Long (500 / 200 / 500)", "Long", longArrayOf(0, 500, 200, 500)),
    VERY_LONG("Very long (800 / 250 / 800)", "Very long", longArrayOf(0, 800, 250, 800)),
    TRIPLE("Triple (600 / 200 ×3)", "Triple", longArrayOf(0, 600, 200, 600, 200, 600)),
    CUSTOM("Custom", "Custom", null),
}

enum class AppTheme(val label: String) { AMBIENT("Ambient"), LIGHT("Light"), DARK("Dark") }

/** A preset, or custom on/off/pulses values (only used when [preset] is CUSTOM). */
data class PatternSpec(
    val preset: Preset,
    val onMs: Long = 600,
    val offMs: Long = 250,
    val pulses: Int = 2,
) {
    /** Waveform timings (off, on, off, on, ...). */
    fun timings(): LongArray = preset.timings ?: Prefs.buildCustom(onMs, offMs, pulses)

    fun label(): String =
        if (preset == Preset.CUSTOM) "Custom ($onMs / $offMs ×$pulses)" else preset.label

    fun shortLabel(): String =
        if (preset == Preset.CUSTOM) "Custom $onMs/$offMs ×$pulses" else preset.shortName

    fun encode() = "${preset.name}:$onMs:$offMs:$pulses"

    companion object {
        fun decode(s: String?): PatternSpec? {
            val parts = s?.split(':') ?: return null
            if (parts.size != 4) return null
            return runCatching {
                PatternSpec(Preset.valueOf(parts[0]), parts[1].toLong(), parts[2].toLong(), parts[3].toInt())
            }.getOrNull()
        }
    }
}

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val appPatterns: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_patterns", Context.MODE_PRIVATE)

    var selectedPackages: Set<String>
        get() = sp.getStringSet(KEY_PACKAGES, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_PACKAGES, value).apply()

    /** The default pattern, used for everything unless overridden below. */
    var defaultPattern: PatternSpec
        get() = PatternSpec(
            preset = runCatching { Preset.valueOf(sp.getString(KEY_PRESET, null)!!) }.getOrDefault(Preset.LONG),
            onMs = sp.getLong(KEY_ON, 600),
            offMs = sp.getLong(KEY_OFF, 250),
            pulses = sp.getInt(KEY_PULSES, 2),
        )
        set(value) = sp.edit()
            .putString(KEY_PRESET, value.preset.name)
            .putLong(KEY_ON, value.onMs)
            .putLong(KEY_OFF, value.offMs)
            .putInt(KEY_PULSES, value.pulses)
            .apply()

    var callLoop: Boolean
        get() = sp.getBoolean(KEY_CALL_LOOP, false)
        set(value) = sp.edit().putBoolean(KEY_CALL_LOOP, value).apply()

    var separateCallPattern: Boolean
        get() = sp.getBoolean(KEY_SEPARATE_CALL, false)
        set(value) = sp.edit().putBoolean(KEY_SEPARATE_CALL, value).apply()

    /** Falls back to the default pattern until one has been saved. */
    var callPattern: PatternSpec
        get() = PatternSpec.decode(sp.getString(KEY_CALL_PATTERN, null)) ?: defaultPattern
        set(value) = sp.edit().putString(KEY_CALL_PATTERN, value.encode()).apply()

    var theme: AppTheme
        get() = runCatching { AppTheme.valueOf(sp.getString(KEY_THEME, null)!!) }.getOrDefault(AppTheme.AMBIENT)
        set(value) = sp.edit().putString(KEY_THEME, value.name).apply()

    var matchSideHome: Boolean
        get() = sp.getBoolean(KEY_MATCH_SIDEHOME, true)
        set(value) = sp.edit().putBoolean(KEY_MATCH_SIDEHOME, value).apply()

    var perAppPatterns: Boolean
        get() = sp.getBoolean(KEY_PER_APP, false)
        set(value) = sp.edit().putBoolean(KEY_PER_APP, value).apply()

    /** null means "use the default pattern". */
    fun appPattern(pkg: String): PatternSpec? = PatternSpec.decode(appPatterns.getString(pkg, null))

    fun setAppPattern(pkg: String, spec: PatternSpec?) {
        appPatterns.edit().apply { if (spec == null) remove(pkg) else putString(pkg, spec.encode()) }.apply()
    }

    fun notificationTimings(pkg: String): LongArray =
        ((if (perAppPatterns) appPattern(pkg) else null) ?: defaultPattern).timings()

    fun callTimings(): LongArray = (if (separateCallPattern) callPattern else defaultPattern).timings()

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        private const val KEY_PACKAGES = "packages"
        private const val KEY_PRESET = "preset"
        private const val KEY_ON = "custom_on"
        private const val KEY_OFF = "custom_off"
        private const val KEY_PULSES = "custom_pulses"
        private const val KEY_SEPARATE_CALL = "separate_call_pattern"
        private const val KEY_CALL_PATTERN = "call_pattern"
        private const val KEY_PER_APP = "per_app_patterns"
        private const val KEY_THEME = "theme"
        private const val KEY_MATCH_SIDEHOME = "match_sidehome"
        const val KEY_CALL_LOOP = "call_loop"

        fun buildCustom(onMs: Long, offMs: Long, pulses: Int): LongArray {
            val out = mutableListOf(0L)
            repeat(pulses.coerceIn(1, 10)) { i ->
                if (i > 0) out += offMs.coerceIn(0, 5000)
                out += onMs.coerceIn(10, 5000)
            }
            return out.toLongArray()
        }
    }
}
