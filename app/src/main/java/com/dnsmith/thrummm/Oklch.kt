// SPDX-License-Identifier: GPL-3.0-or-later
package com.dnsmith.thrummm

import android.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A colour in OKLCH: perceptual lightness 0..1, chroma, hue in degrees. */
data class Lch(val l: Double, val c: Double, val h: Double) {

    fun toArgb(alpha: Float = 1f): Int {
        val hr = Math.toRadians(h)
        val a = c * cos(hr)
        val b = c * sin(hr)
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b
        val lc = l_ * l_ * l_
        val mc = m_ * m_ * m_
        val sc = s_ * s_ * s_
        val r = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc
        val g = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc
        val bl = -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc
        return Color.argb((alpha * 255).toInt(), encode(r), encode(g), encode(bl))
    }

    fun withL(newL: Double) = copy(l = newL.coerceIn(0.0, 1.0))

    companion object {
        fun fromHex(hex: String): Lch = fromArgb(Color.parseColor(hex))

        fun fromArgb(argb: Int): Lch {
            val r = decode(Color.red(argb))
            val g = decode(Color.green(argb))
            val b = decode(Color.blue(argb))
            val l_ = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m_ = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s_ = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val l = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
            val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
            val bb = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
            return Lch(l, sqrt(a * a + bb * bb), (Math.toDegrees(atan2(bb, a)) + 360) % 360)
        }

        /** Interpolates lightness and chroma linearly, hue along the shorter arc. */
        fun mix(x: Lch, y: Lch, t: Double): Lch {
            var dh = y.h - x.h
            if (dh > 180) dh -= 360
            if (dh < -180) dh += 360
            return Lch(x.l + (y.l - x.l) * t, x.c + (y.c - x.c) * t, (x.h + dh * t + 360) % 360)
        }

        private fun decode(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        private fun encode(linear: Double): Int {
            val c = linear.coerceIn(0.0, 1.0)
            val s = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
            return (s * 255 + 0.5).toInt().coerceIn(0, 255)
        }
    }
}
