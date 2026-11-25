package com.mikuwwl.wakeguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mikuwwl.wakeguard.alarm.WakeGuardDiagnostics

class WakeGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WakeGuardDiagnostics.record(this, "PROCESS_STARTED")
        WakeGuardDiagnostics.recordLatestProcessExit(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "WakeGuard alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Ongoing alarm playback and wake-up verification"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object { const val ALARM_CHANNEL_ID = "wakeguard_alarm" }
}
