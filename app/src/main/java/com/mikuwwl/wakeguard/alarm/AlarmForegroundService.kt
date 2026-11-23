package com.mikuwwl.wakeguard.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.mikuwwl.wakeguard.R
import com.mikuwwl.wakeguard.WakeGuardApplication
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AlarmIntents.ACTION_STOP) {
            stopPlayback()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val alarmId = intent?.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L) ?: -1L
        val eventId = intent?.getLongExtra(AlarmIntents.EXTRA_EVENT_ID, -1L) ?: -1L
        startForeground(NOTIFICATION_ID, buildNotification(alarmId, eventId), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (mediaPlayer == null) {
            acquireWakeLock()
            serviceScope.launch { startPlayback(alarmId) }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:wakeguard-alarm")
                .apply { setReferenceCounted(false); acquire(3 * 60 * 60 * 1000L) }
        } catch (error: Exception) {
            Log.w(TAG, "Could not acquire alarm wake lock", error)
        }
    }

    private suspend fun startPlayback(alarmId: Long) {
        val uriText = WakeGuardDatabase.get(this).alarmDao().getById(alarmId)?.ringtoneUri
        val uri = uriText?.let(android.net.Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            val player = MediaPlayer()
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            if (uri != null) player.setDataSource(this, uri) else player.setDataSource("/system/media/audio/alarms/Alarm_Classic.ogg")
            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
            Log.i(TAG, "Alarm playback started for alarm=$alarmId")
        } catch (error: Exception) {
            Log.e(TAG, "Alarm playback failed for alarm=$alarmId", error)
        }
    }

    private fun buildNotification(alarmId: Long, eventId: Long): Notification {
        val open = PendingIntent.getActivity(
            this,
            (eventId.takeIf { it > 0 } ?: alarmId).toInt(),
            Intent(this, MainActivity::class.java).apply {
                action = AlarmIntents.ACTION_SHOW_ALARM
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmIntents.EXTRA_EVENT_ID, eventId)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, WakeGuardApplication.ALARM_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder.setSmallIcon(R.drawable.ic_wakeguard)
            .setContentTitle("WakeGuard 正在响铃")
            .setContentText("打开应用完成起床验证")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
    }

    private fun stopPlayback() {
        try { mediaPlayer?.stop() } catch (_: IllegalStateException) { }
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        Log.i(TAG, "Alarm playback stopped")
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WakeGuardAlarm"
        private const val NOTIFICATION_ID = 4101

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmForegroundService::class.java).setAction(AlarmIntents.ACTION_STOP))
        }
    }
}
