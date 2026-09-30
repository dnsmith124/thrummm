// SPDX-License-Identifier: GPL-3.0-or-later
package com.dnsmith.thrummm

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Buzzer {
    // USAGE_NOTIFICATION matters: the system drops vibrations from background
    // processes unless they're tagged as notification/alarm/ringtone usage.
    private val notificationAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val ringtoneAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // In vibrate mode the system plays its own fallback buzz ~500ms after the
    // notification posts, which would cut our pattern off. Android 12 won't let a
    // one-shot vibration interrupt a repeating one, so we end the pattern with a
    // repeating silent segment and cancel it ourselves once the window has passed.
    private const val MIN_HOLD_MS = 2000L
    private const val SILENT_TAIL_MS = 1000L

    /** Pause between repeats of the pattern while a call rings. */
    const val CALL_GAP_MS = 1000L
    private const val CALL_START_DELAY_MS = 500L

    private val handler = Handler(Looper.getMainLooper())
    private var pendingCancel: Runnable? = null
    private var loopTick: Runnable? = null

    @Volatile
    var looping = false
        private set

    fun vibrate(context: Context, timings: LongArray) {
        // A notification buzz would replace the call loop; let the call win.
        if (looping) return
        val vibrator = vibrator(context) ?: return

        // Timings alternate off/on starting with off and always have even length,
        // so the appended element is an off segment.
        val held = timings + SILENT_TAIL_MS
        val holdMs = maxOf(timings.sum(), MIN_HOLD_MS)

        clearPendingCancel()
        @Suppress("DEPRECATION") // vibrate(effect, VibrationAttributes) needs API 33
        vibrator.vibrate(VibrationEffect.createWaveform(held, held.size - 1), notificationAttrs)
        pendingCancel = Runnable { pendingCancel = null; vibrator.cancel() }
            .also { handler.postDelayed(it, holdMs) }
    }

    /** Repeats [timings] with a gap until [stopLoop]. */
    fun startLoop(context: Context, timings: LongArray) {
        val vibrator = vibrator(context) ?: return
        clearPendingCancel()
        loopTick?.let(handler::removeCallbacks)
        looping = true
        // Ends on an off segment (the gap); repeating from 0 restarts the pattern.
        val loop = timings + CALL_GAP_MS
        val effect = VibrationEffect.createWaveform(loop, 0)
        val cycleMs = loop.sum()

        // Telecom starts its own repeating ringtone vibration shortly after the call
        // starts ringing, and the newest repeating vibration wins. So start a little
        // late, then re-issue at every cycle boundary: seamless while we still own
        // the vibrator, and it reclaims it within one cycle if Telecom took over.
        loopTick = object : Runnable {
            override fun run() {
                if (!looping) return
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, ringtoneAttrs)
                handler.postDelayed(this, cycleMs)
            }
        }.also { handler.postDelayed(it, CALL_START_DELAY_MS) }
    }

    fun stopLoop(context: Context) {
        loopTick?.let(handler::removeCallbacks)
        loopTick = null
        if (!looping) return
        looping = false
        // Only cancels our own vibration: the service matches on this app's token.
        vibrator(context)?.cancel()
    }

    private fun clearPendingCancel() {
        pendingCancel?.let(handler::removeCallbacks)
        pendingCancel = null
    }

    private fun vibrator(context: Context): Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }
}
