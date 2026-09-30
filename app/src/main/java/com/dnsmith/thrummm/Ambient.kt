// SPDX-License-Identifier: GPL-3.0-or-later
package com.dnsmith.thrummm

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A time-of-day "sky" palette in the spirit of the Side suite's launcher: one mood
 * colour picked from where the sun is, with ink, accent and surfaces derived from it.
 */
class Ambient private constructor(val mood: Lch) {

    /** Dark ink on light skies, light ink on dark ones. */
    val fgDark = mood.l > 0.55
    val fg: Int = if (fgDark) 0xFF080604.toInt() else 0xFFFBF8F2.toInt()
    val accent: Int = Lch(if (fgDark) 0.52 else 0.80, 0.16, mood.h).toArgb()
    val sheet: Int = mood.withL(mood.l + 0.06).toArgb()

    /** Whether [other] would look noticeably different (worth re-theming for). */
    fun differsFrom(other: Ambient): Boolean {
        var dh = kotlin.math.abs(mood.h - other.mood.h) % 360
        if (dh > 180) dh = 360 - dh
        return fgDark != other.fgDark || kotlin.math.abs(mood.l - other.mood.l) > 0.03 ||
            kotlin.math.abs(mood.c - other.mood.c) > 0.02 || (mood.c > 0.02 && dh > 8)
    }

    fun fg(alpha: Float): Int = withAlpha(fg, alpha)
    fun accent(alpha: Float): Int = withAlpha(accent, alpha)

    /** Flat sky, two soft drifting fields and a vignette, sized to the screen. */
    fun background(widthPx: Int, heightPx: Int): Drawable {
        val diag = hypot(widthPx.toDouble(), heightPx.toDouble()).toFloat()
        fun field(lch: Lch, alpha: Float, cx: Float, cy: Float, radius: Float) = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(lch.toArgb(alpha), lch.toArgb(0f))
            setGradientCenter(cx, cy)
            gradientRadius = radius
        }
        val vignette = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            val edge = (0.18 + (1 - mood.l.coerceIn(0.0, 1.0)) * 0.3).toFloat()
            colors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(Color.BLACK, edge))
            setGradientCenter(0.5f, 0.45f)
            gradientRadius = diag * 0.62f
        }
        return LayerDrawable(
            arrayOf(
                ColorDrawable(mood.withL(mood.l - 0.05).toArgb()),
                field(mood.withL(mood.l + 0.07), 0.55f, 0.3f, 0.2f, diag * 0.55f),
                field(Lch(mood.l + 0.02, mood.c * 1.2, (mood.h + 35) % 360), 0.35f, 0.85f, 0.75f, diag * 0.5f),
                vignette,
            )
        )
    }

    companion object {
        private val NIGHT = Lch.fromHex("#131F4A")
        private val DAWN = Lch.fromHex("#E293B3")
        private val DAY = Lch.fromHex("#A4C8EA")
        private val DUSK = Lch.fromHex("#EF8746")
        private const val LATITUDE = 60.0

        // SideHome's pinned "Full light" / "Full dark" moods.
        private val LIGHT = Lch.fromHex("#F6F9FF")
        private val DARK = Lch.fromHex("#0D0F15")
        const val SIDEHOME_PACKAGE = "fi.palonkorpi.sidehome"

        /** The palette the settings screen should use right now. */
        fun forSettings(context: Context, prefs: Prefs): Ambient {
            if (prefs.matchSideHome && isSideHomeInstalled(context)) {
                sideHomeMood(context)?.let { return Ambient(it) }
            }
            return when (prefs.theme) {
                AppTheme.AMBIENT -> now()
                AppTheme.LIGHT -> Ambient(LIGHT)
                AppTheme.DARK -> Ambient(DARK)
            }
        }

        fun isSideHomeInstalled(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(SIDEHOME_PACKAGE, 0) }.isSuccess

        /**
         * SideHome keeps its theme in private storage, but its wallpaper sync paints the
         * system wallpaper to match the current theme, and wallpaper colours are readable
         * without any permission. Null when there's nothing to read (e.g. a live wallpaper).
         */
        private fun sideHomeMood(context: Context): Lch? {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
            return Lch.fromArgb(colors.primaryColor.toArgb())
        }

        fun now(): Ambient {
            val t = LocalDateTime.now()
            return Ambient(moodFor(t.hour + t.minute / 60.0, t.dayOfYear))
        }

        private fun moodFor(hour: Double, dayOfYear: Int): Lch {
            val alt = sunAltitude(hour, dayOfYear)
            var mood = Lch.mix(NIGHT, DAY, smoothStep(-6.0, 7.0, alt))
            // Warm orange just above the horizon, rose just below it.
            mood = Lch.mix(mood, DUSK, 0.6 * exp(-((alt - 4) / 6).let { it * it }))
            mood = Lch.mix(mood, DAWN, 0.55 * exp(-((alt + 3) / 5).let { it * it }))
            return mood.copy(c = mood.c * 1.12)
        }

        /** Rough solar altitude in degrees, using local clock time as solar time. */
        private fun sunAltitude(hour: Double, dayOfYear: Int): Double {
            val decl = Math.toRadians(23.44 * sin(Math.toRadians(360.0 / 365 * (dayOfYear - 81))))
            val hourAngle = Math.toRadians(15 * (hour - 12))
            val lat = Math.toRadians(LATITUDE)
            return Math.toDegrees(asin(sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(hourAngle)))
        }

        private fun smoothStep(a: Double, b: Double, x: Double): Double {
            val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
            return t * t * (3 - 2 * t)
        }

        private fun withAlpha(color: Int, alpha: Float) =
            Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
