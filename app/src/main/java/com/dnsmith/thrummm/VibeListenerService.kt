package com.dnsmith.thrummm

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class VibeListenerService : NotificationListenerService() {

    private val lastBuzz = HashMap<String, Long>()
    private var callCallback: CallCallback? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_CALL_LOOP) updateCallWatch()
    }

    override fun onListenerConnected() {
        Prefs(this).registerListener(prefsListener)
        updateCallWatch()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        if (sbn.packageName == packageName) return
        if (sbn.packageName !in prefs.selectedPackages) return

        val flags = sbn.notification.flags
        if (sbn.isOngoing ||
            flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
            flags and Notification.FLAG_GROUP_SUMMARY != 0
        ) return

        val now = SystemClock.elapsedRealtime()
        synchronized(lastBuzz) {
            val prev = lastBuzz[sbn.key]
            if (prev != null && now - prev < DEBOUNCE_MS) return
            lastBuzz[sbn.key] = now
            lastBuzz.entries.removeAll { now - it.value > DEBOUNCE_MS * 10 }
        }

        if (isMuted()) return

        Log.d(TAG, "Buzzing for ${sbn.packageName} (${sbn.key})")
        Buzzer.vibrate(this, prefs.notificationTimings(sbn.packageName))
    }

    private fun isMuted(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return true
        val am = getSystemService(AudioManager::class.java)
        return am.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    /** Watches call state only while the loop is enabled and READ_PHONE_STATE is granted. */
    private fun updateCallWatch() {
        val want = Prefs(this).callLoop &&
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        if (want && callCallback == null) {
            callCallback = CallCallback().also { tm.registerTelephonyCallback(mainExecutor, it) }
        } else if (!want) {
            callCallback?.let(tm::unregisterTelephonyCallback)
            callCallback = null
            Buzzer.stopLoop(this)
        }
    }

    private inner class CallCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            if (state == TelephonyManager.CALL_STATE_RINGING && shouldVibrateForCall()) {
                Log.d(TAG, "Call ringing: starting loop")
                Buzzer.startLoop(this@VibeListenerService, Prefs(this@VibeListenerService).callTimings())
            } else if (state != TelephonyManager.CALL_STATE_RINGING) {
                Buzzer.stopLoop(this@VibeListenerService)
            }
        }
    }

    // Mirrors when the system itself would vibrate for a call: always in vibrate
    // mode, in ring mode only if "vibrate for calls" is on, never in silent or DND.
    private fun shouldVibrateForCall(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return false
        return when (getSystemService(AudioManager::class.java).ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> true
            AudioManager.RINGER_MODE_NORMAL ->
                Settings.System.getInt(contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 0) == 1
            else -> false
        }
    }

    override fun onListenerDisconnected() {
        Prefs(this).unregisterListener(prefsListener)
        callCallback?.let { getSystemService(TelephonyManager::class.java)?.unregisterTelephonyCallback(it) }
        callCallback = null
        Buzzer.stopLoop(this)
        requestRebind(ComponentName(this, VibeListenerService::class.java))
    }

    companion object {
        private const val TAG = "Thrummm"
        private const val DEBOUNCE_MS = 2000L
    }
}
